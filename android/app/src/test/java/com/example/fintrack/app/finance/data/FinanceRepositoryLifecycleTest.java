package com.example.fintrack.app.finance.data;

import static org.junit.Assert.*;

import com.example.fintrack.app.budget.remote.BudgetApiService;
import com.example.fintrack.app.category.remote.CategoryApiService;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;
import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.transaction.remote.TransactionApiService;
import com.example.fintrack.app.wallet.remote.WalletApiService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Exercises real Retrofit calls and repository delivery without a backend or Android filesystem. */
public class FinanceRepositoryLifecycleTest {
    private static final FinanceBook.Query QUERY = new FinanceBook.Query(null, null, null, null, null, null, null);
    private static final AppExecutors DIRECT = new AppExecutors(Runnable::run, Runnable::run, Runnable::run, Runnable::run);

    private static final class QueueExecutor implements Executor {
        final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
        @Override public void execute(Runnable task) { pending.add(task); }
        void drain() { Runnable task; while ((task = pending.poll()) != null) task.run(); }
    }
    private static FinanceRepository repository(Interceptor transport) {
        Retrofit retrofit = new Retrofit.Builder().baseUrl("https://fixture.invalid/")
                .client(new OkHttpClient.Builder().addInterceptor(transport).build())
                .addConverterFactory(GsonConverterFactory.create()).build();
        FinanceApiBundle api = new FinanceApiBundle(retrofit.create(WalletApiService.class),
                retrofit.create(CategoryApiService.class), retrofit.create(BudgetApiService.class),
                retrofit.create(TransactionApiService.class));
        return new FinanceRepository(api, null);
    }
    private static Response json(Interceptor.Chain chain, String body) {
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("fixture")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }
    @Test public void cancelReadCancelsTheActiveRetrofitTransportAndSuppressesDelivery() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        AtomicReference<okhttp3.Call> transport = new AtomicReference<>();
        FinanceRepository repository = repository(chain -> {
            transport.set(chain.call());
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("fixture timed out");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); throw new IOException(error);
            }
            return json(chain, "{\"items\":[],\"page\":0,\"totalPages\":1}");
        });
        QueueExecutor worker = new QueueExecutor(), delivery = new QueueExecutor();
        List<Resource<FinanceBook.Page>> received = new ArrayList<>();
        RequestHandle request = repository.transactionsAsync(QUERY, 0,
                new AppExecutors(worker, worker, worker, delivery), received::add);
        Thread running = new Thread(() -> { try { worker.drain(); } finally { finished.countDown(); } });
        running.start();
        try {
            assertTrue("Retrofit call started", entered.await(5, TimeUnit.SECONDS));
            request.cancel();
            request.cancel();
            assertTrue("actual OkHttp transport canceled", transport.get().isCanceled());
        } finally {
            release.countDown();
            assertTrue("worker finished", finished.await(5, TimeUnit.SECONDS));
            running.join(1000);
        }
        delivery.drain();
        assertTrue(received.isEmpty());
    }
    @Test public void cancelSuppressesAResponseAlreadyQueuedForMainThreadDelivery() {
        FinanceRepository repository = repository(chain -> json(chain, "{\"items\":[],\"page\":0,\"totalPages\":1}"));
        QueueExecutor delivery = new QueueExecutor();
        List<Resource<FinanceBook.Page>> received = new ArrayList<>();
        RequestHandle request = repository.transactionsAsync(QUERY, 0,
                new AppExecutors(Runnable::run, Runnable::run, Runnable::run, delivery), received::add);
        assertEquals(1, delivery.pending.size());
        request.cancel();
        delivery.drain();
        assertTrue(received.isEmpty());
    }
    @Test public void remoteMutationReceiptUsesTheServerWalletAndCategoryIds() {
        FinanceRepository repository = repository(chain -> json(chain, chain.request().url().encodedPath().endsWith("/wallets")
                ? "{\"walletId\":\"server-wallet\"}" : "{\"categoryId\":\"server-category\"}"));
        FinanceBook previous = new FinanceBook(FinanceBook.empty());
        List<Resource<MutationReceipt>> results = new ArrayList<>();
        repository.mutateAsync(FinanceAction.wallet("Cash", 100, WalletType.CASH), previous, () -> true, DIRECT, results::add);
        repository.mutateAsync(FinanceAction.category("Custom", TransactionType.EXPENSE), previous, () -> true, DIRECT, results::add);
        assertEquals(2, results.size());
        assertEquals(Resource.Status.SUCCESS, results.get(0).getStatus());
        assertEquals("server-wallet", results.get(0).getData().createdEntityId);
        assertEquals("server-category", results.get(1).getData().createdEntityId);
        assertNull(results.get(0).getData().snapshot);
        assertNull(results.get(1).getData().snapshot);
    }
    @Test public void localCreationReturnsTheIdOfTheEntityActuallyAdded() {
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        String wallet = FinanceAction.wallet("Cash", 100, WalletType.CASH).localResult.apply(book);
        String category = FinanceAction.category("Custom", TransactionType.EXPENSE).localResult.apply(book);
        assertNotNull(wallet); assertNotNull(category);
        assertEquals("Cash", book.wallet(wallet).name);
        assertEquals("Custom", book.category(category).name);
    }
}
