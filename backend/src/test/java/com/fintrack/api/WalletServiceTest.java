package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.dto.request.wallet.WalletRequest;
import com.fintrack.api.dto.request.wallet.WalletStatusRequest;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.WalletRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.wallet.WalletService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class WalletServiceTest {
    private final WalletRepository repository = mock(WalletRepository.class);
    private final FinanceSupport finance = mock(FinanceSupport.class);
    private final WalletService service = new WalletService(repository, finance);
    private final UUID user = UUID.randomUUID(), wallet = UUID.randomUUID();

    @Test void createNormalizesNameAndAppliesDefaults() {
        WalletRequest request = request(null);
        request.name = "  Vi\u0301  ";
        service.create(user, request);
        verify(repository).create(eq(user), any(UUID.class), eq("Ví"), eq("CASH"), eq(0L), eq(true), eq(0));
    }

    @Test void openingEditUsesBalanceReadAfterOwnerLockAndPreservesLedgerContribution() {
        when(finance.wallet(user, wallet, true)).thenReturn(new FinanceSupport.WalletRef(wallet, 100_000, true));
        when(finance.balance(user, wallet)).thenReturn(40_000L);
        service.update(user, wallet, request(80_000L));
        var order = inOrder(finance, repository);
        order.verify(finance).wallet(user, wallet, true);
        order.verify(finance).balance(user, wallet);
        order.verify(repository).update(user, wallet, "Wallet", "CASH", 80_000, true, 0);
    }

    @Test void rejectsOpeningThatWouldMakeCurrentBalanceNegativeWithoutWriting() {
        when(finance.wallet(user, wallet, true)).thenReturn(new FinanceSupport.WalletRef(wallet, 80_000, true));
        when(finance.balance(user, wallet)).thenReturn(20_000L);
        assertCode(() -> service.update(user, wallet, request(50_000L)), ErrorCode.WALLET_INSUFFICIENT_FUNDS);
        verifyNoInteractions(repository);
    }

    @Test void openingMayBecomeZeroWhenThereAreNoExpenses() {
        when(finance.wallet(user, wallet, true)).thenReturn(new FinanceSupport.WalletRef(wallet, 80_000, true));
        when(finance.balance(user, wallet)).thenReturn(80_000L);
        service.update(user, wallet, request(0L));
        verify(repository).update(user, wallet, "Wallet", "CASH", 0, true, 0);
    }

    @Test void rejectsArchivedWalletBeforeReadingBalance() {
        when(finance.wallet(user, wallet, true)).thenReturn(new FinanceSupport.WalletRef(wallet, 0, false));
        assertCode(() -> service.update(user, wallet, request(0L)), ErrorCode.WALLET_ARCHIVED);
        verify(finance, never()).balance(any(), any());
        verifyNoInteractions(repository);
    }

    @Test void foreignWalletLookupStopsMutation() {
        when(finance.wallet(user, wallet, true)).thenThrow(FinanceSupport.notFound());
        assertCode(() -> service.archive(user, wallet), ErrorCode.NOT_FOUND);
        verifyNoInteractions(repository);
    }

    @Test void archiveLocksAndChangesLifecycleOnly() {
        service.archive(user, wallet);
        var order = inOrder(finance, repository);
        order.verify(finance).wallet(user, wallet, true);
        order.verify(repository).status(user, wallet, false);
        verifyNoMoreInteractions(repository, finance);
    }

    @Test void concurrentDuplicateAndRestoreConflictHaveContractCode() {
        doThrow(new DuplicateKeyException("unique active name")).when(repository)
                .create(eq(user), any(), anyString(), anyString(), anyLong(), anyBoolean(), anyInt());
        assertCode(() -> service.create(user, request(0L)), ErrorCode.WALLET_NAME_CONFLICT);
        doThrow(new DuplicateKeyException("unique active name")).when(repository).status(user, wallet, true);
        WalletStatusRequest status = new WalletStatusRequest(); status.active = true;
        assertCode(() -> service.status(user, wallet, status), ErrorCode.WALLET_NAME_CONFLICT);
    }

    @Test void rejectsMissingStatusAndInvalidFields() {
        assertCode(() -> service.status(user, wallet, new WalletStatusRequest()), ErrorCode.VALIDATION_ERROR);
        for (long value : new long[]{-1, FinanceSupport.MAX_MONEY + 1}) {
            assertCode(() -> service.create(user, request(value)), ErrorCode.VALIDATION_ERROR);
        }
        WalletRequest unsupported = request(0L); unsupported.walletType = "E_WALLET";
        assertCode(() -> service.create(user, unsupported), ErrorCode.VALIDATION_ERROR);
        WalletRequest name = request(0L); name.name = "a".repeat(101);
        assertCode(() -> service.create(user, name), ErrorCode.VALIDATION_ERROR);
        WalletRequest order = request(0L); order.sortOrder = -1;
        assertCode(() -> service.create(user, order), ErrorCode.VALIDATION_ERROR);
        when(finance.wallet(user, wallet, true)).thenReturn(new FinanceSupport.WalletRef(wallet, 0, true));
        assertCode(() -> service.update(user, wallet, request(null)), ErrorCode.VALIDATION_ERROR);
    }

    @Test void actualJsonRejectsFractionalMoneyStringsOverflowAndReadOnlyFields() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String value : new String[]{"1.5", "\"100\"", "9223372036854775808"}) {
            assertThatThrownBy(() -> json.readValue("{\"openingBalanceMinor\":" + value + "}", WalletRequest.class));
        }
        assertThatThrownBy(() -> json.readValue("{\"balanceMinor\":100}", WalletRequest.class));
        assertThatThrownBy(() -> json.readValue("{\"sortOrder\":0.5}", WalletRequest.class));
        assertThatThrownBy(() -> json.readValue("{\"includeInAssets\":\"true\"}", WalletRequest.class));
        assertThatThrownBy(() -> json.readValue("{\"active\":1}", WalletStatusRequest.class));
        assertThat(json.readValue("{\"openingBalanceMinor\":999999999999}", WalletRequest.class).openingBalanceMinor)
                .isEqualTo(999_999_999_999L);
    }

    @Test void invalidListStatusCannotReachRepository() {
        assertCode(() -> service.list(user, "UNKNOWN"), ErrorCode.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    private static WalletRequest request(Long opening) {
        WalletRequest request = new WalletRequest();
        request.name = "Wallet"; request.walletType = "CASH"; request.openingBalanceMinor = opening;
        return request;
    }
    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
