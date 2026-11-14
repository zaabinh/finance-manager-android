package com.example.fintrack.app.core.di;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.transaction.viewmodel.TransactionListViewModel;
import com.example.fintrack.app.transaction.viewmodel.TransactionReceiptViewModel;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

/** Builds every ViewModel from the container's dependencies. */
public final class AppViewModelFactory implements ViewModelProvider.Factory {
    private final AppContainer container;
    private final FinanceRepository.Scope financeScope;
    private final String financeDetailId;

    public AppViewModelFactory(AppContainer container) {
        this(container, FinanceRepository.Scope.BUDGETS, null);
    }
    private AppViewModelFactory(AppContainer container, FinanceRepository.Scope scope, String detailId) {
        this.container = container; this.financeScope = scope; this.financeDetailId = detailId;
    }
    public ViewModelProvider.Factory forFinance(FinanceRepository.Scope scope, String detailId) {
        return new AppViewModelFactory(container, scope, detailId);
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass == AuthViewModel.class)
            return (T) new AuthViewModel(container.authRepository(), container.userRepository(), container.sessionStore());
        if (modelClass == UserViewModel.class) return (T) new UserViewModel(container.userRepository(), container.sessionStore());
        if (modelClass == FinanceViewModel.class)
            return (T) new FinanceViewModel(container.sessionStore(), container::financeRepository, container.executors(), financeScope, financeDetailId);
        if (modelClass == TransactionListViewModel.class)
            return (T) new TransactionListViewModel(container.sessionStore(), container::financeRepository, container.executors());
        if (modelClass == TransactionReceiptViewModel.class)
            return (T) new TransactionReceiptViewModel(container.appContext(), container.executors());
        throw new IllegalArgumentException("Unknown ViewModel: " + modelClass.getName());
    }
}
