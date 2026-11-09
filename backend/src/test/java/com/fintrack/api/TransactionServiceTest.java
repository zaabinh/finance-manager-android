package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.response.transaction.TransactionResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.TransactionRepository;
import com.fintrack.api.repository.TransactionRepository.Fields;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.transaction.TransactionService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionServiceTest {
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final FinanceSupport finance = mock(FinanceSupport.class);
    private final TransactionService service = new TransactionService(repository, finance);
    private final UUID user = UUID.randomUUID(), key = UUID.randomUUID(), id = UUID.randomUUID();
    private final UUID walletA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID walletB = UUID.fromString("f0000000-0000-0000-0000-00000000000b");
    private final UUID expenseCategory = UUID.randomUUID(), incomeCategory = UUID.randomUUID();
    private final TransactionResponse current = mock(TransactionResponse.class);

    @BeforeEach void references() {
        when(finance.wallet(eq(user), any(), anyBoolean())).thenAnswer(call -> new FinanceSupport.WalletRef(call.getArgument(1), 0, true));
        when(finance.category(user, expenseCategory, true)).thenReturn(new FinanceSupport.CategoryRef(expenseCategory, "EXPENSE", true));
        when(finance.category(user, incomeCategory, true)).thenReturn(new FinanceSupport.CategoryRef(incomeCategory, "INCOME", true));
        when(repository.byRequest(user, key)).thenReturn(Optional.empty());
        when(repository.create(eq(user), any(), any(), eq(key), anyString())).thenReturn(true);
        when(repository.detail(eq(user), any())).thenReturn(current);
    }

    @Test void createLocksWalletBeforeReadingBalanceAndPostsSignedEntry() {
        when(finance.balance(user, walletA)).thenReturn(100_000L);
        var result = service.create(user, expense(walletA, 100_000));
        assertThat(result.created()).isTrue();
        var order = inOrder(finance, repository);
        order.verify(finance).wallet(user, walletA, true);
        order.verify(repository).byRequest(user, key);
        order.verify(finance).category(user, expenseCategory, true);
        order.verify(finance).balance(user, walletA);
        var fields = ArgumentCaptor.forClass(Fields.class);
        order.verify(repository).create(eq(user), any(), fields.capture(), eq(key), anyString());
        assertThat(fields.getValue().delta()).isEqualTo(-100_000); // exactly to zero is allowed
        assertThat(fields.getValue().note()).isNull();
    }

    @Test void createRejectsInsufficientFundsWithoutWriting() {
        when(finance.balance(user, walletA)).thenReturn(69_999L);
        assertCode(() -> service.create(user, expense(walletA, 70_000)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    @Test void incomeOverflowIsRejected() {
        when(finance.balance(user, walletA)).thenReturn(Long.MAX_VALUE - 1);
        assertCode(() -> service.create(user, income(walletA, 2)), ErrorCode.FINANCE_AMOUNT_OVERFLOW);
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    @Test void createRequiresActiveSameTypeReferences() {
        when(finance.wallet(user, walletA, true)).thenReturn(new FinanceSupport.WalletRef(walletA, 0, false));
        assertCode(() -> service.create(user, expense(walletA, 1)), ErrorCode.WALLET_ARCHIVED);
        when(finance.category(user, expenseCategory, true)).thenReturn(new FinanceSupport.CategoryRef(expenseCategory, "EXPENSE", false));
        assertCode(() -> service.create(user, expense(walletB, 1)), ErrorCode.CATEGORY_ARCHIVED);
        TransactionRequest mismatch = expense(walletB, 1); mismatch.categoryId = incomeCategory;
        assertCode(() -> service.create(user, mismatch), ErrorCode.VALIDATION_ERROR);
        when(finance.wallet(user, walletB, true)).thenThrow(FinanceSupport.notFound());
        assertCode(() -> service.create(user, expense(walletB, 1)), ErrorCode.NOT_FOUND);
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    @Test void sameKeyAndPayloadReplaysCurrentRecordEvenAfterWalletArchive() {
        when(finance.balance(user, walletA)).thenReturn(1_000_000L);
        service.create(user, expense(walletA, 95_000));
        String fingerprint = capturedFingerprint();
        when(repository.byRequest(user, key)).thenReturn(Optional.of(new TransactionRepository.Replay(id, fingerprint)));
        when(finance.wallet(user, walletA, true)).thenReturn(new FinanceSupport.WalletRef(walletA, 0, false));
        // Equivalent offset and "" vs null note produce the same canonical payload.
        TransactionRequest retry = expense(walletA, 95_000); retry.occurredAt = "2026-11-07T15:45:00+07:00"; retry.note = "";
        var replay = service.create(user, retry);
        assertThat(replay.created()).isFalse();
        assertThat(replay.body()).isSameAs(current);
        verify(repository).detail(user, id);
        verify(repository, times(1)).create(any(), any(), any(), any(), any());
    }

    @Test void sameKeyDifferentPayloadConflicts() {
        when(finance.balance(user, walletA)).thenReturn(1_000_000L);
        service.create(user, expense(walletA, 95_000));
        String fingerprint = capturedFingerprint();
        when(repository.byRequest(user, key)).thenReturn(Optional.of(new TransactionRepository.Replay(id, fingerprint)));
        assertCode(() -> service.create(user, expense(walletA, 95_001)), ErrorCode.TRANSACTION_REQUEST_CONFLICT);
        TransactionRequest note = expense(walletA, 95_000); note.note = "x";
        assertCode(() -> service.create(user, note), ErrorCode.TRANSACTION_REQUEST_CONFLICT);
        when(repository.byRequest(user, key)).thenReturn(Optional.of(new TransactionRepository.Replay(id, null)));
        assertCode(() -> service.create(user, expense(walletA, 95_000)), ErrorCode.TRANSACTION_REQUEST_CONFLICT);
    }

    @Test void keyInsertedConcurrentlyFallsBackToReplayCheck() {
        when(finance.balance(user, walletA)).thenReturn(1_000_000L);
        when(repository.create(eq(user), any(), any(), eq(key), anyString())).thenReturn(false);
        when(repository.byRequest(user, key)).thenReturn(Optional.empty(),
                Optional.of(new TransactionRepository.Replay(id, "different")));
        assertCode(() -> service.create(user, expense(walletA, 1)), ErrorCode.TRANSACTION_REQUEST_CONFLICT);
    }

    @Test void validationRejectsBeforeAnyLock() {
        for (long amount : new long[]{0, -1, FinanceSupport.MAX_MONEY + 1})
            assertCode(() -> service.create(user, expense(walletA, amount)), ErrorCode.VALIDATION_ERROR);
        TransactionRequest transfer = expense(walletA, 1); transfer.transactionType = "TRANSFER"; transfer.destinationWalletId = walletB;
        assertCode(() -> service.create(user, transfer), ErrorCode.VALIDATION_ERROR); // category must be null
        TransactionRequest noDestination = transfer(walletA, null, 1);
        assertCode(() -> service.create(user, noDestination), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.create(user, transfer(walletA, walletA, 1)), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.create(user, transfer(null, walletB, 1)), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.create(user, transfer(walletA, walletB, 0)), ErrorCode.VALIDATION_ERROR);
        TransactionRequest other = expense(walletA, 1); other.transactionType = "ADJUSTMENT";
        assertCode(() -> service.create(user, other), ErrorCode.TRANSACTION_TYPE_UNSUPPORTED);
        TransactionRequest destination = expense(walletA, 1); destination.destinationWalletId = walletB;
        assertCode(() -> service.create(user, destination), ErrorCode.VALIDATION_ERROR);
        TransactionRequest local = expense(walletA, 1); local.occurredAt = "2026-11-07T08:45:00";
        assertCode(() -> service.create(user, local), ErrorCode.BAD_REQUEST);
        TransactionRequest date = expense(walletA, 1); date.occurredAt = "2026-11-07";
        assertCode(() -> service.create(user, date), ErrorCode.BAD_REQUEST);
        TransactionRequest missing = expense(walletA, 1); missing.occurredAt = null;
        assertCode(() -> service.create(user, missing), ErrorCode.VALIDATION_ERROR);
        TransactionRequest note = expense(walletA, 1); note.note = "a".repeat(2001);
        assertCode(() -> service.create(user, note), ErrorCode.VALIDATION_ERROR);
        TransactionRequest payee = expense(walletA, 1); payee.payee = "a".repeat(161);
        assertCode(() -> service.create(user, payee), ErrorCode.VALIDATION_ERROR);
        TransactionRequest noKey = expense(walletA, 1); noKey.clientRequestId = null;
        assertCode(() -> service.create(user, noKey), ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(finance);
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    @Test void actualJsonRejectsFractionalAmountAndReadOnlyFields() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String body : new String[]{"{\"amountMinor\":1.5}", "{\"amountMinor\":\"100\"}", "{\"status\":\"POSTED\"}",
                "{\"transactionId\":\"" + id + "\"}", "{\"walletId\":\"not-a-uuid\"}"})
            assertThatThrownBy(() -> json.readValue(body, TransactionRequest.class));
        assertThat(json.readValue("{\"amountMinor\":999999999999,\"destinationWalletId\":null}", TransactionRequest.class).amountMinor)
                .isEqualTo(999_999_999_999L);
    }

    @Test void updateMovingExpenseRefundsOldWalletAndChargesNewInSortedLockOrder() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 50_000, walletB, true));
        when(finance.balance(user, walletA)).thenReturn(80_000L);
        when(finance.balance(user, walletB)).thenReturn(0L);
        service.update(user, id, expense(walletA, 80_000));
        var order = inOrder(repository, finance);
        order.verify(repository).lock(user, id);
        order.verify(finance).wallet(user, walletA, true); // walletA sorts before walletB
        order.verify(finance).wallet(user, walletB, true);
        order.verify(finance).category(user, expenseCategory, true);
        order.verify(repository).update(eq(user), eq(id), any());
    }

    @Test void updateRejectsNewWalletShortfallAndIncomeReductionBelowSpent() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 50_000, walletB, true));
        when(finance.balance(user, walletA)).thenReturn(79_999L);
        when(finance.balance(user, walletB)).thenReturn(0L);
        assertCode(() -> service.update(user, id, expense(walletA, 80_000)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "INCOME", 100_000, walletA, true));
        when(finance.balance(user, walletA)).thenReturn(40_000L); // 60k already spent from this income
        assertCode(() -> service.update(user, id, income(walletA, 50_000)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        when(finance.balance(user, walletA)).thenReturn(40_000L);
        service.update(user, id, income(walletA, 60_000)); // candidate exactly 0
        verify(repository, times(1)).update(any(), any(), any());
    }

    @Test void updateRejectsVoidedArchivedTargetAndForeignTransaction() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 1, walletA, false));
        assertCode(() -> service.update(user, id, expense(walletA, 1)), ErrorCode.TRANSACTION_VOIDED);
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 1, walletA, true));
        when(finance.wallet(user, walletB, true)).thenReturn(new FinanceSupport.WalletRef(walletB, 0, false));
        assertCode(() -> service.update(user, id, expense(walletB, 1)), ErrorCode.WALLET_ARCHIVED);
        when(repository.lock(user, id)).thenThrow(FinanceSupport.notFound());
        assertCode(() -> service.update(user, id, expense(walletA, 1)), ErrorCode.NOT_FOUND);
        verify(repository, never()).update(any(), any(), any());
    }

    @Test void deleteExpenseRefundsOnceAndRepeatedDeleteIsNoOp() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 100_000, walletA, true));
        when(finance.balance(user, walletA)).thenReturn(0L);
        when(finance.wallet(user, walletA, true)).thenReturn(new FinanceSupport.WalletRef(walletA, 0, false)); // archived ok
        service.delete(user, id);
        var order = inOrder(repository, finance);
        order.verify(repository).lock(user, id);
        order.verify(finance).wallet(user, walletA, true);
        order.verify(finance).balance(user, walletA);
        order.verify(repository).voidPosted(user, id);
        clearInvocations(finance);
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 100_000, walletA, false));
        service.delete(user, id);
        verify(repository, times(1)).voidPosted(user, id);
        verifyNoInteractions(finance);
    }

    @Test void deletingSpentIncomeIsRejectedWithoutVoiding() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "INCOME", 100_000, walletA, true));
        when(finance.balance(user, walletA)).thenReturn(40_000L);
        assertCode(() -> service.delete(user, id), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        verify(repository, never()).voidPosted(any(), any());
    }

    @Test void listConvertsInclusiveLocalDatesToHalfOpenUtcAndValidatesQuery() {
        when(finance.zone(user)).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(repository.page(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(new TransactionRepository.Page(List.of(), 101));
        var page = service.list(user, 3, 50, "2026-11-01", "2026-11-30", null, null, "EXPENSE", "  50%_off ", null);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(3);
        verify(repository).page(user, Instant.parse("2026-10-31T17:00:00Z"), Instant.parse("2026-11-30T17:00:00Z"),
                null, null, "EXPENSE", "50%_off", "occurredAt,desc", 50, 150L);
        for (Runnable bad : List.<Runnable>of(
                () -> service.list(user, -1, 50, null, null, null, null, null, null, null),
                () -> service.list(user, 0, 201, null, null, null, null, null, null, null),
                () -> service.list(user, 0, 0, null, null, null, null, null, null, null),
                () -> service.list(user, 0, 50, "2026-11-02", "2026-11-01", null, null, null, null, null),
                () -> service.list(user, 0, 50, "2026-2-01", null, null, null, null, null, null),
                () -> service.list(user, 0, 50, "9999-01-01", null, null, null, null, null, null),
                () -> service.list(user, 0, 50, null, null, null, null, "ADJUSTMENT", null, null),
                () -> service.list(user, 0, 50, null, null, null, null, null, "a".repeat(201), null),
                () -> service.list(user, 0, 50, null, null, null, null, null, null, "note,asc")))
            assertCode(bad, ErrorCode.BAD_REQUEST);
        UUID foreign = UUID.randomUUID();
        when(finance.wallet(user, foreign, false)).thenThrow(FinanceSupport.notFound());
        assertCode(() -> service.list(user, 0, 50, null, null, foreign, null, null, null, null), ErrorCode.NOT_FOUND);
        verify(repository, times(1)).page(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test void listAcceptsTransferTypeAndWalletFilter() {
        when(finance.zone(user)).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(repository.page(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(new TransactionRepository.Page(List.of(), 0));
        service.list(user, 0, 50, null, null, walletB, null, "TRANSFER", null, null);
        verify(finance).wallet(user, walletB, false);
        verify(repository).page(user, null, null, walletB, null, "TRANSFER", null, "occurredAt,desc", 50, 0L);
    }

    @Test void transferLocksBothWalletsInOrderAndPostsTwoEntries() {
        when(finance.balance(user, walletA)).thenReturn(0L);
        when(finance.balance(user, walletB)).thenReturn(100_000L);
        var result = service.create(user, transfer(walletB, walletA, 100_000)); // source sorts after destination
        assertThat(result.created()).isTrue();
        var order = inOrder(finance, repository);
        order.verify(finance).wallet(user, walletA, true);
        order.verify(finance).wallet(user, walletB, true);
        order.verify(repository).byRequest(user, key);
        var fields = ArgumentCaptor.forClass(Fields.class);
        order.verify(repository).create(eq(user), any(), fields.capture(), eq(key), anyString());
        assertThat(fields.getValue().deltas()).containsExactlyInAnyOrderEntriesOf(Map.of(walletB, -100_000L, walletA, 100_000L));
        assertThat(fields.getValue().categoryId()).isNull();
        verify(finance, never()).category(any(), any(), anyBoolean());
    }

    @Test void transferRejectsSourceShortfallArchivedWalletsAndForeignDestination() {
        when(finance.balance(user, walletA)).thenReturn(99_999L);
        when(finance.balance(user, walletB)).thenReturn(0L);
        assertCode(() -> service.create(user, transfer(walletA, walletB, 100_000)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        when(finance.balance(user, walletA)).thenReturn(1_000_000L);
        when(finance.wallet(user, walletB, true)).thenReturn(new FinanceSupport.WalletRef(walletB, 0, false));
        assertCode(() -> service.create(user, transfer(walletA, walletB, 1)), ErrorCode.WALLET_ARCHIVED);
        when(finance.wallet(user, walletB, true)).thenThrow(FinanceSupport.notFound());
        assertCode(() -> service.create(user, transfer(walletA, walletB, 1)), ErrorCode.NOT_FOUND);
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    @Test void transferFingerprintIncludesDestinationAndExpenseFingerprintIsUnchanged() {
        UUID walletC = UUID.randomUUID();
        when(finance.balance(eq(user), any())).thenReturn(1_000_000L);
        service.create(user, transfer(walletA, walletB, 5_000));
        String fingerprint = capturedFingerprint();
        when(repository.byRequest(user, key)).thenReturn(Optional.of(new TransactionRepository.Replay(id, fingerprint)));
        assertThat(service.create(user, transfer(walletA, walletB, 5_000)).created()).isFalse();
        assertCode(() -> service.create(user, transfer(walletA, walletC, 5_000)), ErrorCode.TRANSACTION_REQUEST_CONFLICT);
        assertCode(() -> service.create(user, transfer(walletB, walletA, 5_000)), ErrorCode.TRANSACTION_REQUEST_CONFLICT);
    }

    @Test void expenseFingerprintKeepsTheOriginalCanonicalForm() throws Exception {
        // Fingerprints stored before transfers existed must still match: v1|type|amount|category|wallet|occurredAt|note|payee.
        when(finance.balance(user, walletA)).thenReturn(1_000_000L);
        service.create(user, expense(walletA, 95_000));
        String canonical = "v1";
        for (String part : new String[]{"EXPENSE", "95000", expenseCategory.toString(), walletA.toString(), "2026-11-07T08:45:00Z", "", ""})
            canonical += "|" + part.length() + ":" + part;
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(capturedFingerprint()).isEqualTo(expected);
    }

    @Test void updateTransferChangingWalletsLocksAllInvolvedAndChecksEachCandidate() {
        UUID walletC = UUID.fromString("80000000-0000-0000-0000-00000000000c");
        // old: A -> B 50k. new: C -> A 80k. A: +50k (refund) +80k; B: -50k; C: -80k.
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "TRANSFER", 50_000, walletA, true, walletB));
        when(finance.balance(user, walletA)).thenReturn(0L);
        when(finance.balance(user, walletB)).thenReturn(50_000L);
        when(finance.balance(user, walletC)).thenReturn(80_000L);
        service.update(user, id, transfer(walletC, walletA, 80_000));
        var order = inOrder(repository, finance);
        order.verify(repository).lock(user, id);
        order.verify(finance).wallet(user, walletA, true);
        order.verify(finance).wallet(user, walletC, true);
        order.verify(finance).wallet(user, walletB, true);
        order.verify(repository).update(eq(user), eq(id), any());
        // Destination B of the old transfer would go negative when it gives the 50k back.
        when(finance.balance(user, walletB)).thenReturn(49_999L);
        assertCode(() -> service.update(user, id, transfer(walletC, walletA, 80_000)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        when(finance.balance(user, walletB)).thenReturn(50_000L);
        when(finance.balance(user, walletC)).thenReturn(79_999L);
        assertCode(() -> service.update(user, id, transfer(walletC, walletA, 80_000)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        verify(repository, times(1)).update(any(), any(), any());
    }

    @Test void updateExpenseIntoTransferRequiresActiveDestination() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "EXPENSE", 10_000, walletA, true));
        when(finance.balance(eq(user), any())).thenReturn(0L);
        when(finance.wallet(user, walletB, true)).thenReturn(new FinanceSupport.WalletRef(walletB, 0, false));
        assertCode(() -> service.update(user, id, transfer(walletA, walletB, 10_000)), ErrorCode.WALLET_ARCHIVED);
        verify(repository, never()).update(any(), any(), any());
    }

    @Test void deleteTransferReversesOnceAndRequiresDestinationFunds() {
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "TRANSFER", 100_000, walletB, true, walletA));
        when(finance.balance(user, walletA)).thenReturn(99_999L); // destination already spent part of it
        when(finance.balance(user, walletB)).thenReturn(0L);
        assertCode(() -> service.delete(user, id), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        verify(repository, never()).voidPosted(any(), any());
        when(finance.balance(user, walletA)).thenReturn(100_000L); // exactly to zero is allowed
        when(finance.wallet(user, walletB, true)).thenReturn(new FinanceSupport.WalletRef(walletB, 0, false)); // archived ok
        service.delete(user, id);
        var order = inOrder(repository, finance);
        order.verify(finance).wallet(user, walletA, true);
        order.verify(finance).wallet(user, walletB, true);
        order.verify(repository).voidPosted(user, id);
        clearInvocations(finance);
        when(repository.lock(user, id)).thenReturn(new TransactionRepository.Row(id, "TRANSFER", 100_000, walletB, false, walletA));
        service.delete(user, id);
        verify(repository, times(1)).voidPosted(user, id);
        verifyNoInteractions(finance);
    }

    private String capturedFingerprint() {
        var fingerprint = ArgumentCaptor.forClass(String.class);
        verify(repository).create(eq(user), any(), any(), eq(key), fingerprint.capture());
        return fingerprint.getValue();
    }
    private TransactionRequest expense(UUID wallet, long amount) { return request("EXPENSE", expenseCategory, wallet, amount); }
    private TransactionRequest income(UUID wallet, long amount) { return request("INCOME", incomeCategory, wallet, amount); }
    private TransactionRequest transfer(UUID source, UUID destination, long amount) {
        TransactionRequest request = request("TRANSFER", null, source, amount);
        request.destinationWalletId = destination;
        return request;
    }
    private TransactionRequest request(String type, UUID category, UUID wallet, long amount) {
        TransactionRequest request = new TransactionRequest();
        request.transactionType = type; request.amountMinor = amount; request.categoryId = category; request.walletId = wallet;
        request.occurredAt = "2026-11-07T08:45:00Z"; request.clientRequestId = key;
        return request;
    }
    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
