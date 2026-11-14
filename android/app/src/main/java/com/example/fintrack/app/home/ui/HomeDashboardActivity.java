package com.example.fintrack.app.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.BuildConfig;
import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.analytics.ui.AnalyticsActivity;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.budget.ui.MonthlyBudgetFormActivity;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.core.ui.SessionExpiry;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceErrors;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.home.domain.HomeSummary;
import com.example.fintrack.app.home.viewmodel.DashboardViewModel;
import com.example.fintrack.app.notification.model.NotificationItem;
import com.example.fintrack.app.notification.provider.NotificationProvider;
import com.example.fintrack.app.notification.ui.NotificationActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.transaction.ui.TransactionImportActivity;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.example.fintrack.app.wallet.ui.WalletAddActivity;
import com.example.fintrack.databinding.ActivityHomeDashboardBinding;

// Màn hình chính của ứng dụng (HomeDashboardActivity) - Hiển thị tổng quan số dư, ngân sách và các thao tác nhanh
public class HomeDashboardActivity extends AppCompatActivity {
    private static final String STATE_BALANCE_HIDDEN = "balanceHidden";

    private ActivityHomeDashboardBinding binding;

    private SessionStore sessionStore;
    private UserViewModel userViewModel;
    private FinanceViewModel financeModel;
    private DashboardViewModel dashboardModel;
    private SessionSnapshot origin; // phiên đăng nhập lúc mở màn hình

    private boolean isBalanceHidden = false;
    // Last summary rendered; null until the server (or the mock ledger) has answered, so no figure shows before that.
    private HomeSummary summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (savedInstanceState != null) isBalanceHidden = savedInstanceState.getBoolean(STATE_BALANCE_HIDDEN);

        sessionStore = FintrackApplication.container(this).sessionStore();
        origin = sessionStore.current();

        userViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);
        financeModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory().forFinance(
                com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS, null)).get(FinanceViewModel.class);
        dashboardModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(DashboardViewModel.class);

        setupObservers();
        setupListeners();
        updateBalanceDisplay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_BALANCE_HIDDEN, isBalanceHidden);
    }

    // Lắng nghe dữ liệu thay đổi từ ViewModel (LiveData)
    private void setupObservers() {
        // Hiển thị tên người dùng từ Local Session trước (nếu có)
        String name = origin.name;
        if (name != null && !name.isEmpty()) {
            binding.tvUserName.setText(getString(R.string.greeting_format, name));
        }

        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String fetchedName = resource.getData().getDisplayName();
                if (fetchedName != null && !fetchedName.isEmpty()) {
                    binding.tvUserName.setText(getString(R.string.greeting_format, fetchedName));
                }
            }
        });

        // Real backend: one GET /api/v1/dashboard request for the whole Home summary; its aggregates are shown as sent.
        dashboardModel.summary().observe(this, resource -> {
            // The session that asked has ended (e.g. the server rejected the refresh token): leave for Login.
            if (resource.getStatus() == Resource.Status.ERROR && SessionExpiry.redirectIfEnded(this, origin)) return;
            if (resource.getStatus() == Resource.Status.ERROR) showLoadError(ErrorMapper.text(this, resource.getError()));
            if (resource.getStatus() != Resource.Status.SUCCESS) return;
            showSummary(HomeSummary.fromDashboard(resource.getData()));
        });

        // Mock flavor has no dashboard endpoint: derive the same summary from the local ledger.
        financeModel.state().observe(this, resource -> {
            if (resource.getStatus() == Resource.Status.ERROR && SessionExpiry.redirectIfEnded(this, origin)) return;
            if (resource.getStatus() == Resource.Status.ERROR) {
                showLoadError(getString(FinanceUi.error(FinanceErrors.problem(resource.getError()))));
            }
            if (resource.getData() == null) return;
            FinanceBook book = resource.getData();
            showSummary(HomeSummary.fromBook(book, System.currentTimeMillis()));
        });
    }

    private void showLoadError(String message) {
        com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, view -> refreshSummary()).show();
    }

    private void refreshSummary() {
        if (BuildConfig.USE_MOCK) financeModel.refresh();
        else dashboardModel.refresh();
    }

    private void showSummary(HomeSummary next) {
        summary = next;
        updateBalanceDisplay();
        binding.homeBudgetLink.setText(summary.hasBudget() ? R.string.see_details : R.string.home_create_budget);
        binding.tvBudgetStatusBadge.setVisibility(View.VISIBLE);
        if (summary.hasBudget()) {
            boolean exceeded = summary.budgetExceeded;
            binding.pbBudgetProgress.setProgress(summary.budgetPercent);
            binding.tvBudgetUsage.setText(getString(R.string.budget_usage_format,
                    FinanceUi.money(summary.budgetSpent), FinanceUi.money(summary.budgetLimit)));
            // Badge: any category over its limit counts (dashboard worstStatus), not only the total.
            binding.tvBudgetStatusBadge.setText(exceeded ? R.string.budget_status_exceeded : R.string.budget_status_within);
            binding.tvBudgetStatusBadge.setBackgroundResource(exceeded ? R.drawable.bg_badge_over_budget : R.drawable.bg_badge_in_budget);
            binding.tvBudgetStatusBadge.setTextColor(getColor(exceeded ? R.color.status_error : R.color.status_success));
            long remaining = summary.budgetRemaining();
            binding.tvBudgetRemaining.setText(remaining <= 0
                    ? getString(R.string.budget_exceeded_detail, FinanceUi.money(-remaining), summary.budgetPercent)
                    : getString(R.string.budget_remaining_detail, FinanceUi.money(remaining), summary.budgetPercent));
        } else {
            // Chưa có ngân sách tháng này: báo cho người dùng và mời tạo (link bên dưới mở form tạo ngân sách)
            binding.tvBudgetStatusBadge.setText(R.string.budget_status_unset);
            binding.tvBudgetUsage.setText(getString(R.string.budget_spent_only, FinanceUi.money(summary.budgetSpent)));
            binding.tvBudgetRemaining.setText(R.string.budget_unset_note);
            binding.pbBudgetProgress.setProgress(0);
        }
    }

    // Cập nhật trạng thái ẩn/hiện số tiền trên giao diện
    private void updateBalanceDisplay() {
        if (isBalanceHidden) {
            binding.tvTotalBalance.setText(R.string.hidden_balance);
            binding.tvAvailableBalance.setText(R.string.hidden_amount);
            binding.tvSavingsBalance.setText(R.string.hidden_amount);
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.ic_eye_close);
        } else {
            // No data yet: a dash, never a sample figure.
            boolean loaded = summary != null;
            String none = getString(R.string.home_amount_placeholder);
            binding.tvTotalBalance.setText(loaded ? FinanceUi.money(summary.totalAssets) : none);
            binding.tvAvailableBalance.setText(loaded ? FinanceUi.money(summary.available) : none);
            binding.tvSavingsBalance.setText(loaded ? FinanceUi.money(summary.savings) : none);
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.ic_eye);
        }
    }

    private void setupListeners() {
        binding.btnToggleBalanceVisibility.setOnClickListener(v -> {
            isBalanceHidden = !isBalanceHidden;
            updateBalanceDisplay();
        });
        binding.flNotification.setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));
        binding.btnActionRecord.setOnClickListener(v -> startActivity(new Intent(this, TransactionFormActivity.class)));
        binding.btnActionAddWallet.setOnClickListener(v -> startActivity(new Intent(this, WalletAddActivity.class)));
        binding.btnActionScan.setOnClickListener(v -> startActivity(new Intent(this, TransactionImportActivity.class)));
        binding.btnActionReport.setOnClickListener(v -> startActivity(new Intent(this, AnalyticsActivity.class)));
        // No budget this month: the link invites the user to create one instead of opening an empty list.
        binding.homeBudgetLink.setOnClickListener(v -> startActivity(new Intent(this,
                summary != null && summary.hasBudget() ? BudgetListActivity.class : MonthlyBudgetFormActivity.class)));

        FinanceUi.navigation(this, R.id.navHome);
    }

    // The bell dot reflects a real source only: unread demo notifications in mock, never in real flavors.
    private void updateNotificationDot() {
        NotificationProvider provider = FintrackApplication.container(this).notificationProvider();
        boolean unread = false;
        if (provider != null) for (NotificationItem item : provider.notifications()) unread |= !item.isRead();
        binding.viewNotificationDot.setVisibility(unread ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Phiên lúc mở màn hình không còn là phiên hiện tại: đá về màn hình Login
        if (sessionStore != null && SessionExpiry.redirectIfEnded(this, origin)) return;
        updateNotificationDot();
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
        refreshSummary();
    }
}
