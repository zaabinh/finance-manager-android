package com.example.fintrack.app.home.ui;

import com.example.fintrack.app.finance.ui.FinanceErrors;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.MonthlyBudget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.analytics.ui.AnalyticsActivity;
import com.example.fintrack.app.core.ui.SessionExpiry;
import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.home.remote.DashboardResponse;
import com.example.fintrack.app.home.viewmodel.DashboardViewModel;
import com.example.fintrack.app.wallet.ui.WalletAddActivity;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.notification.ui.NotificationActivity;
import com.example.fintrack.app.transaction.ui.TransactionImportActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình chính của ứng dụng (HomeDashboardActivity) - Hiển thị tổng quan số dư, ngân sách và các thao tác nhanh
public class HomeDashboardActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI Views)
    private TextView tvUserName;
    private TextView tvTotalBalance;
    private TextView tvAvailableBalance;
    private TextView tvSavingsBalance;
    private ImageView btnToggleBalanceVisibility;

    private View btnActionRecord;
    private View btnActionAddWallet;
    private View btnActionScan;
    private View btnActionReport;

    private TextView tvBudgetStatusBadge;
    private TextView tvBudgetUsage;
    private ProgressBar pbBudgetProgress;
    private TextView tvBudgetRemaining;
    private View homeBudgetLink;

    // Khai báo quản lý phiên đăng nhập và các ViewModel xử lý dữ liệu
    private SessionStore sessionStore;
    private UserViewModel userViewModel;
    private FinanceViewModel financeModel;
    private DashboardViewModel dashboardModel;
    private SessionSnapshot origin; // phiên đăng nhập lúc mở màn hình

    // Biến lưu trạng thái hiển thị số dư và các giá trị tiền tệ (tính theo đơn vị cents)
    private boolean isBalanceHidden = false;
    private long totalAssetsCents;
    private long availableCents;
    private long savingsCents;
    // Whether the current month has a budget; decides where the budget card's link goes
    private boolean hasBudget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        // Lấy SessionStore và ghi nhớ phiên đăng nhập hiện tại
        sessionStore = FintrackApplication.container(this).sessionStore();
        origin = sessionStore.current();

        // Khởi tạo các ViewModel để kết nối dữ liệu
        userViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);
        financeModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory().forFinance(
                com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS, null)).get(FinanceViewModel.class);

        dashboardModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(DashboardViewModel.class);

        // Gọi các hàm thiết lập giao diện, lắng nghe dữ liệu và sự kiện bấm nút
        initViews();
        setupObservers();
        setupListeners();
    }

    // Ánh xạ các View từ file XML sang biến Java
    private void initViews() {
        tvUserName = findViewById(R.id.tvUserName);
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvAvailableBalance = findViewById(R.id.tvAvailableBalance);
        tvSavingsBalance = findViewById(R.id.tvSavingsBalance);
        btnToggleBalanceVisibility = findViewById(R.id.btnToggleBalanceVisibility);

        btnActionRecord = findViewById(R.id.btnActionRecord);
        btnActionAddWallet = findViewById(R.id.btnActionAddWallet);
        btnActionScan = findViewById(R.id.btnActionScan);
        btnActionReport = findViewById(R.id.btnActionReport);

        tvBudgetStatusBadge = findViewById(R.id.tvBudgetStatusBadge);
        tvBudgetUsage = findViewById(R.id.tvBudgetUsage);
        pbBudgetProgress = findViewById(R.id.pbBudgetProgress);
        tvBudgetRemaining = findViewById(R.id.tvBudgetRemaining);
        homeBudgetLink = findViewById(R.id.homeBudgetLink);
    }

    // Lắng nghe dữ liệu thay đổi từ ViewModel (LiveData)
    private void setupObservers() {
        // Hiển thị tên người dùng từ Local Session trước (nếu có)
        String name = origin.name;
        if (name != null && !name.isEmpty()) {
            tvUserName.setText(getString(R.string.greeting_format, name));
        }

        // Lắng nghe kết quả lấy thông tin profile từ server (UserViewModel đã lưu vào Session)
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String fetchedName = resource.getData().getDisplayName();
                if (fetchedName != null && !fetchedName.isEmpty()) {
                    tvUserName.setText(getString(R.string.greeting_format, fetchedName));
                }
            }
        });

        // Real backend: one GET /api/v1/dashboard request for the whole Home summary; its aggregates are shown as sent.
        dashboardModel.summary().observe(this, resource -> {
            // The session that asked has ended (e.g. the server rejected the refresh token): leave for Login.
            if (resource.getStatus() == Resource.Status.ERROR && SessionExpiry.redirectIfEnded(this, origin)) return;
            if (resource.getStatus() == Resource.Status.ERROR) showLoadError(ErrorMapper.text(this, resource.getError()));
            if (resource.getStatus() != Resource.Status.SUCCESS) return;
            DashboardResponse body = resource.getData();
            DashboardResponse.Budget budget = body.budget;
            showSummary(body.assets.totalMinor, body.assets.availableMinor, body.assets.savingsMinor,
                    budget.limitMinor, budget.spentMinor, budget.progressPercent,
                    "EXCEEDED".equals(budget.worstStatus) || (budget.limitMinor > 0 && budget.spentMinor >= budget.limitMinor));
        });

        // Mock flavor has no dashboard endpoint: derive the same summary from the local ledger.
        financeModel.state().observe(this, resource -> {
            if (resource.getStatus() == Resource.Status.ERROR && SessionExpiry.redirectIfEnded(this, origin)) return;
            if (resource.getStatus() == Resource.Status.ERROR) {
                showLoadError(getString(FinanceUi.error(FinanceErrors.problem(resource.getError()))));
            }
            if (resource.getData() == null) return;
            FinanceBook book = resource.getData();
            MonthlyBudget currentBudget = book.monthlyBudgetFor(book.monthAt(System.currentTimeMillis()));
            long total = book.assets(false), savings = book.assets(true);
            long spent = currentBudget == null ? book.budgetSpent(book.monthAt(System.currentTimeMillis())) : book.monthlySpent(currentBudget);
            long limit = currentBudget == null ? 0 : book.monthlyTotal(currentBudget);
            int percent = limit <= 0 ? 0 : java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                    .divide(java.math.BigInteger.valueOf(limit)).min(java.math.BigInteger.valueOf(100)).intValue();
            showSummary(total, Math.subtractExact(total, savings), savings, limit, spent, percent, limit > 0 && spent >= limit);
        });
    }

    private void showLoadError(String message) {
        com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, view -> refreshSummary()).show();
    }

    private void refreshSummary() {
        if (BuildConfig.USE_MOCK) financeModel.refresh();
        else dashboardModel.refresh();
    }

    private void showSummary(long total, long available, long savings, long limit, long spent, int percent, boolean exceeded) {
        totalAssetsCents = total;
        availableCents = available;
        savingsCents = savings;
        updateBalanceDisplay();
        hasBudget = limit > 0;
        if (homeBudgetLink instanceof TextView) {
            ((TextView) homeBudgetLink).setText(hasBudget ? R.string.see_details : R.string.home_create_budget);
        }
        if (tvBudgetUsage == null || tvBudgetRemaining == null || pbBudgetProgress == null) return;
        if (hasBudget) {
            // Đã thiết lập hạn mức ngân sách
            pbBudgetProgress.setProgress(percent);
            tvBudgetUsage.setText(getString(R.string.budget_usage_format, FinanceUi.money(spent), FinanceUi.money(limit)));
            // Badge: any category over its limit counts (dashboard worstStatus), not only the total.
            tvBudgetStatusBadge.setText(exceeded ? R.string.budget_status_exceeded : R.string.budget_status_within);
            tvBudgetStatusBadge.setBackgroundResource(exceeded ? R.drawable.bg_badge_over_budget : R.drawable.bg_badge_in_budget);
            tvBudgetStatusBadge.setTextColor(getColor(exceeded ? R.color.status_error : R.color.status_success));
            tvBudgetRemaining.setText(spent >= limit
                    ? getString(R.string.budget_exceeded_detail, FinanceUi.money(spent - limit), percent)
                    : getString(R.string.budget_remaining_detail, FinanceUi.money(limit - spent), percent));
        } else {
            // Chưa có ngân sách tháng này: báo cho người dùng và mời tạo (link bên dưới mở form tạo ngân sách)
            tvBudgetStatusBadge.setText(R.string.budget_status_unset);
            tvBudgetUsage.setText(getString(R.string.budget_spent_only, FinanceUi.money(spent)));
            tvBudgetRemaining.setText(R.string.budget_unset_note);
            pbBudgetProgress.setProgress(0);
        }
    }

    // Cập nhật trạng thái ẩn/hiện số tiền trên giao diện
    private void updateBalanceDisplay() {
        if (isBalanceHidden) {
            // Nếu đang ẩn: hiển thị dấu chấm tròn bảo mật
            tvTotalBalance.setText(R.string.hidden_balance);
            tvAvailableBalance.setText(R.string.hidden_amount);
            tvSavingsBalance.setText(R.string.hidden_amount);
            btnToggleBalanceVisibility.setImageResource(R.drawable.ic_eye_close);
        } else {
            // Nếu đang hiện: hiển thị số tiền thực tế đã được định dạng
            tvTotalBalance.setText(FinanceUi.money(totalAssetsCents));
            tvAvailableBalance.setText(FinanceUi.money(availableCents));
            tvSavingsBalance.setText(FinanceUi.money(savingsCents));
            btnToggleBalanceVisibility.setImageResource(R.drawable.ic_eye);
        }
    }

    // Thiết lập các sự kiện click (tương tác người dùng)
    private void setupListeners() {
        // Nút ẩn/hiện số dư
        btnToggleBalanceVisibility.setOnClickListener(v -> {
            isBalanceHidden = !isBalanceHidden;
            updateBalanceDisplay();
        });

        // Nút chuyển đến màn hình thông báo
        View flNotification = findViewById(R.id.flNotification);
        if (flNotification != null) {
            flNotification.setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));
        }

        // Các nút thao tác nhanh (Quick Actions)
        if (btnActionRecord != null) {
            btnActionRecord.setOnClickListener(v -> startActivity(new Intent(this, TransactionFormActivity.class)));
        }

        if (btnActionAddWallet != null) {
            btnActionAddWallet.setOnClickListener(v -> startActivity(new Intent(this, WalletAddActivity.class)));
        }

        if (btnActionScan != null) {
            btnActionScan.setOnClickListener(v -> startActivity(new Intent(this, TransactionImportActivity.class)));
        }

        if (btnActionReport != null) {
            btnActionReport.setOnClickListener(v -> startActivity(new Intent(this, AnalyticsActivity.class)));
        }

        // Đường dẫn bấm vào xem danh sách/chi tiết ngân sách
        if (homeBudgetLink != null) {
            // No budget this month: the link invites the user to create one instead of opening an empty list.
            homeBudgetLink.setOnClickListener(v -> startActivity(new Intent(this,
                    hasBudget ? BudgetListActivity.class : com.example.fintrack.app.budget.ui.MonthlyBudgetFormActivity.class)));
        }

        // Khởi tạo thanh điều hướng dưới màn hình (Bottom Navigation)
        FinanceUi.navigation(this, R.id.navHome);
    }

    // Kiểm tra lại trạng thái đăng nhập và làm mới dữ liệu mỗi khi màn hình quay lại hiển thị (Resume)
    @Override
    protected void onResume() {
        super.onResume();
        // Nếu phiên lúc mở màn hình không còn là phiên hiện tại (đăng xuất, đổi tài khoản, đăng nhập lại) thì đá về màn hình Login
        if (sessionStore != null && SessionExpiry.redirectIfEnded(this, origin)) return;
        // Gọi tải lại thông tin profile và dữ liệu tài chính mới nhất
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
        refreshSummary();
    }
}
