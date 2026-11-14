package com.example.fintrack.app.finance.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.util.AtomicFile;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.Wallet;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MockFinanceStoreTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private File file() { return new File(context().getCacheDir(),"finance-test-"+UUID.randomUUID()+".json"); }
    private byte[] seed(File file) throws Exception {
        FinanceSnapshot.Builder state=FinanceBook.empty();state.wallets.add(new Wallet("cash","Cash",1000));
        byte[] bytes=new FinanceFileCodec().encode(state.build()).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output=new FileOutputStream(file)) { output.write(bytes); }
        return bytes;
    }
    @Test public void partialWriteFailureKeepsPreviousBytesAndReadableState() throws Exception {
        File file=file();byte[] before=seed(file);
        AtomicFile broken=new AtomicFile(file) {
            @Override public void finishWrite(FileOutputStream output) {
                throw new IllegalStateException("simulated commit failure after bytes were written");
            }
        };
        try {
            MockFinanceStore store=new MockFinanceStore(broken);
            assertEquals(FinanceProblem.STORAGE,assertThrows(FinanceFailure.class,()->store.mutate(book->book.addWallet("New",100))).problem);
            assertArrayEquals(before,new AtomicFile(file).readFully());
            assertEquals(1,new MockFinanceStore(new AtomicFile(file)).read().snapshot().wallets.size());
        } finally { new AtomicFile(file).delete(); }
    }
    @Test public void invalidMutationDoesNotStartWriting() throws Exception {
        File file=file();byte[] before=seed(file);
        try {
            MockFinanceStore store=new MockFinanceStore(new AtomicFile(file));
            assertThrows(FinanceFailure.class,()->store.mutate(book->book.addWallet("Invalid",-1)));
            assertArrayEquals(before,new AtomicFile(file).readFully());
        } finally { new AtomicFile(file).delete(); }
    }
    @Test public void corruptFileIsNotReplacedWithDemo() throws Exception {
        File file=file();byte[] bytes="{broken".getBytes(StandardCharsets.UTF_8);
        try {
            try (FileOutputStream output=new FileOutputStream(file)) { output.write(bytes); }
            assertEquals(FinanceProblem.STORAGE,assertThrows(FinanceFailure.class,()->new MockFinanceStore(new AtomicFile(file)).read()).problem);
            assertArrayEquals(bytes,new AtomicFile(file).readFully());
        } finally { new AtomicFile(file).delete(); }
    }
    @Test public void ownerFilesRemainIndependent() {
        String first="test-owner-a-"+UUID.randomUUID(),second="test-owner-b-"+UUID.randomUUID();
        MockFinanceStore a=new MockFinanceStore(context(),first),b=new MockFinanceStore(context(),second);
        a.mutate(book->book.addWallet("Only A",100));
        assertTrue(a.read().snapshot().wallets.stream().anyMatch(w->"Only A".equals(w.name)));
        assertFalse(b.read().snapshot().wallets.stream().anyMatch(w->"Only A".equals(w.name)));
    }
}
