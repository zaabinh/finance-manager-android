package com.example.fintrack.app.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.analytics.ui.AnalyticsActivity;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.home.remote.DashboardApiService;
import com.example.fintrack.app.home.remote.DashboardResponse;
import com.example.fintrack.app.wallet.ui.WalletAddActivity;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.notification.ui.NotificationActivity;
import com.example.fintrack.app.transaction.ui.TransactionImportActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

import java.util.Objects;

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
    private SessionManager sessionManager;
    private UserViewModel userViewModel;
    private FinanceViewModel financeModel;
    private String financeOwner;

    // Biến lưu trạng thái hiển thị số dư và các giá trị tiền tệ (tính theo đơn vị cents)
    private boolean isBalanceHidden = false;
    private long totalAssetsCents;
    private long availableCents;
    private long savingsCents;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        // Khởi tạo SessionManager và lấy ID người dùng hiện tại
        sessionManager = SessionManager.getInstance(getApplicationContext());
        financeOwner = sessionManager.getUserId();

        // Khởi tạo các ViewModel để kết nối dữ liệu
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        financeModel = new ViewModelProvider(this).get(FinanceViewModel.class);

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
        String name = sessionManager.getUserName();
        if (name != null && !name.isEmpty()) {
            tvUserName.setText(getString(R.string.greeting_format, name));
        }

        // Lắng nghe kết quả lấy thông tin profile từ server
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String fetchedName = resource.getData().getDisplayName();
                if (fetchedName != null && !fetchedName.isEmpty()) {
                    tvUserName.setText(getString(R.string.greeting_format, fetchedName));
                    // Cập nhật lại thông tin vào bộ nhớ tạm (Session)
                    sessionManager.saveUserProfile(
                            resource.getData().getId(),
                            resource.getData().getEmail(),
                            fetchedName
                    );
                }
            }
        });

        // Mock flavor has no dashboard endpoint: derive the same summary from the local ledger.
        financeModel.state().observe(this, resource -> {
            if (resource.getStatus() == Resource.Status.ERROR) {
                showLoadError(getString(FinanceUi.error(FinanceBook.Problem.valueOf(resource.getMessage()))));
            }
            if (resource.getData() == null) return;
            FinanceBook book = resource.getData();
            FinanceBook.MonthlyBudget currentBudget = book.monthlyBudgetFor(book.monthAt(System.currentTimeMillis()));
            long total = book.assets(false), savings = book.assets(true);
            long spent = currentBudget == null ? book.budgetSpent(book.monthAt(System.currentTimeMillis())) : book.monthlySpent(currentBudget);
            long limit = currentBudget == null ? 0 : book.monthlyTotal(currentBudget);
            int percent = limit <= 0 ? 0 : java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                    .divide(java.math.BigInteger.valueOf(limit)).min(java.math.BigInteger.valueOf(100)).intValue();
            showSummary(total, Math.subtractExact(total, savings), savings, limit, spent, percent, limit > 0 && spent >= limit);
        });
    }

    /** Real backend: one GET /api/v1/dashboard request for the whole Home summary. */
    private void loadDashboard() {
        com.example.fintrack.app.finance.data.FinanceRepository.client(this, financeOwner)
                .create(DashboardApiService.class).dashboard().enqueue(new retrofit2.Callback<DashboardResponse>() {
            @Override public void onResponse(@NonNull retrofit2.Call<DashboardResponse> call, @NonNull retrofit2.Response<DashboardResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                DashboardResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.assets == null || body.budget == null) {
                    showLoadError(ErrorMapper.getHttpErrorMessage(HomeDashboardActivity.this, response));
                    return;
                }
                DashboardResponse.Budget budget = body.budget;
                showSummary(body.assets.totalMinor, body.assets.availableMinor, body.assets.savingsMinor,
                        budget.limitMinor, budget.spentMinor, budget.progressPercent,
                        "EXCEEDED".equals(budget.worstStatus) || (budget.limitMinor > 0 && budget.spentMinor >= budget.limitMinor));
            }
            @Override public void onFailure(@NonNull retrofit2.Call<DashboardResponse> call, @NonNull Throwable error) {
                if (!isFinishing() && !isDestroyed()) showLoadError(ErrorMapper.getErrorMessage(HomeDashboardActivity.this, error));
            }
        });
    }

    private void showLoadError(String message) {
        com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, view -> refreshSummary()).show();
    }

    private void refreshSummary() {
        if (BuildConfig.USE_MOCK) financeModel.refresh();
        else loadDashboard();
    }

    private void showSummary(long total, long available, long savings, long limit, long spent, int percent, boolean exceeded) {
        totalAssetsCents = total;
        availableCents = available;
        savingsCents = savings;
        updateBalanceDisplay();
            if (tvBudgetUsage != null && tvBudgetRemaining != null && pbBudgetProgress != null) {
                if (limit > 0) {
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
                    // Chưa thiết lập hạn mức ngân sách
                    tvBudgetStatusBadge.setText(R.string.budget_status_unset);
                    tvBudgetUsage.setText(getString(R.string.budget_spent_only, FinanceUi.money(spent)));
                    tvBudgetRemaining.setText(R.string.budget_unset_note);
                    pbBudgetProgress.setProgress(0);
                }
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
            homeBudgetLink.setOnClickListener(v -> startActivity(new Intent(this, BudgetListActivity.class)));
        }

        // Khởi tạo thanh điều hướng dưới màn hình (Bottom Navigation)
        FinanceUi.navigation(this, R.id.navHome);
    }

    // Kiểm tra lại trạng thái đăng nhập và làm mới dữ liệu mỗi khi màn hình quay lại hiển thị (Resume)
    @Override
    protected void onResume() {
        super.onResume();
        // Nếu chưa đăng nhập hoặc đổi tài khoản thì đá về màn hình Login
        if (sessionManager != null && (!sessionManager.isLoggedIn() || !Objects.equals(financeOwner, sessionManager.getUserId()))) {
            startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }
        // Gọi tải lại thông tin profile và dữ liệu tài chính mới nhất
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
        refreshSummary();
    }
}
