package com.example.fintrack.app.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.analytics.ui.AnalyticsActivity;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.budget.ui.BudgetDetailActivity;
import com.example.fintrack.app.budget.ui.MonthlyBudgetFormActivity;
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
    private View btnActionBudget;
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
    // Ngân sách tháng hiện tại (null nếu chưa tạo) để nút tắt "Ngân sách" mở đúng màn hình
    private String currentMonthlyBudgetId;

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
        btnActionBudget = findViewById(R.id.btnActionBudget);
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

        // Lắng nghe dữ liệu tài chính (Số dư, Ngân sách)
        financeModel.state().observe(this, resource -> {
            // Xử lý khi có lỗi tải dữ liệu tài chính
            if (resource.getStatus() == Resource.Status.ERROR) {
                com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                                FinanceUi.error(FinanceBook.Problem.valueOf(resource.getMessage())), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry, view -> financeModel.refresh()).show();
            }
            if (resource.getData() == null) return;
            FinanceBook book = resource.getData();
            String month = book.monthAt(System.currentTimeMillis());

            // Tính toán tài sản tổng, tiền tiết kiệm và tiền khả dụng
            totalAssetsCents = book.assets(false);
            savingsCents = book.assets(true);
            availableCents = Math.subtractExact(totalAssetsCents, savingsCents);

            // Cập nhật lên giao diện hiển thị số dư
            updateBalanceDisplay();

            // Ngân sách tháng hiện tại: tổng (dẫn xuất) và đã chi lấy từ server (mock tính cùng công thức)
            FinanceBook.MonthlyBudget currentBudget = book.monthlyBudgetFor(month);
            currentMonthlyBudgetId = currentBudget == null ? null : currentBudget.id;
            long spent = currentBudget == null ? book.budgetSpent(month) : book.monthlySpent(currentBudget);
            long limit = currentBudget == null ? 0 : book.monthlyTotal(currentBudget);

            if (tvBudgetUsage != null && tvBudgetRemaining != null && pbBudgetProgress != null) {
                if (limit > 0) {
                    // Đã thiết lập hạn mức ngân sách
                    int percent = java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                            .divide(java.math.BigInteger.valueOf(limit)).min(java.math.BigInteger.valueOf(100)).intValue();
                    pbBudgetProgress.setProgress(percent);
                    tvBudgetUsage.setText(getString(R.string.budget_usage_format, FinanceUi.money(spent), FinanceUi.money(limit)));

                    if (spent >= limit) {
                        // Trường hợp chi tiêu vượt quá hạn mức
                        tvBudgetStatusBadge.setText(R.string.budget_status_exceeded);
                        tvBudgetStatusBadge.setBackgroundResource(R.drawable.bg_badge_over_budget);
                        tvBudgetStatusBadge.setTextColor(getColor(R.color.status_error));
                        tvBudgetRemaining.setText(getString(R.string.budget_exceeded_detail, FinanceUi.money(spent - limit), percent));
                    } else {
                        // Trường hợp chi tiêu trong hạn mức cho phép
                        tvBudgetStatusBadge.setText(R.string.budget_status_within);
                        tvBudgetStatusBadge.setBackgroundResource(R.drawable.bg_badge_in_budget);
                        tvBudgetStatusBadge.setTextColor(getColor(R.color.status_success));
                        tvBudgetRemaining.setText(getString(R.string.budget_remaining_detail, FinanceUi.money(limit - spent), percent));
                    }
                } else {
                    // Chưa thiết lập hạn mức ngân sách
                    tvBudgetStatusBadge.setText(R.string.budget_status_unset);
                    tvBudgetUsage.setText(getString(R.string.budget_spent_only, FinanceUi.money(spent)));
                    tvBudgetRemaining.setText(R.string.budget_unset_note);
                    pbBudgetProgress.setProgress(0);
                }
            }
        });
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

        if (btnActionBudget != null) {
            btnActionBudget.setOnClickListener(v -> startActivity(currentMonthlyBudgetId == null
                    ? new Intent(this, MonthlyBudgetFormActivity.class)
                    : new Intent(this, BudgetDetailActivity.class).putExtra(BudgetDetailActivity.EXTRA_MONTHLY_ID, currentMonthlyBudgetId)));
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
        if (financeModel != null) {
            financeModel.refresh();
        }
    }
}
