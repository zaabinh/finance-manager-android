package com.example.fintrack.app.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.settings.ui.SettingActivity;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.budget.ui.BudgetListActivity;

// Màn hình chính của ứng dụng (HomeDashboardActivity)
public class HomeDashboardActivity extends AppCompatActivity {

    private ImageView navSetting;
    private TextView tvUserName;
    private SessionManager sessionManager;
    private UserViewModel userViewModel;
    private FinanceViewModel financeModel;
    private String financeOwner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        financeOwner = sessionManager.getUserId();
        for (int id : new int[]{R.id.tvTotalBalance, R.id.tvAvailableBalance, R.id.tvSavingsBalance,
                R.id.tvIncomeVal, R.id.tvExpenseVal, R.id.tvRemainingVal}) ((TextView) findViewById(id)).setText(R.string.finance_loading_value);
        ((android.widget.LinearLayout) findViewById(R.id.homeWallets)).removeAllViews();
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        financeModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        findViewById(R.id.fabAddTransaction).setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)));
        findViewById(R.id.navReport).setOnClickListener(view -> startActivity(new Intent(this, TransactionListActivity.class)));
        findViewById(R.id.homeBudgetLink).setOnClickListener(view -> startActivity(new Intent(this, BudgetListActivity.class)));
        financeModel.state().observe(this, resource -> {
            if (resource.getStatus() == Resource.Status.ERROR) {
                com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                        FinanceUi.error(FinanceBook.Problem.valueOf(resource.getMessage())), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry, view -> financeModel.refresh()).show();
            }
            if (resource.getData() == null) return;
            FinanceBook book = resource.getData();
            String month = FinanceBook.month(System.currentTimeMillis());
            ((TextView) findViewById(R.id.tvTotalBalance)).setText(FinanceUi.money(book.assets(false)));
            ((TextView) findViewById(R.id.tvAvailableBalance)).setText(FinanceUi.money(Math.subtractExact(book.assets(false), book.assets(true))));
            ((TextView) findViewById(R.id.tvSavingsBalance)).setText(FinanceUi.money(book.assets(true)));
            ((TextView) findViewById(R.id.tvIncomeVal)).setText(FinanceUi.money(book.total(FinanceBook.Type.INCOME, month)));
            ((TextView) findViewById(R.id.tvExpenseVal)).setText(FinanceUi.money(book.total(FinanceBook.Type.EXPENSE, month)));
            ((TextView) findViewById(R.id.tvRemainingVal)).setText(FinanceUi.money(Math.subtractExact(book.total(FinanceBook.Type.INCOME, month), book.total(FinanceBook.Type.EXPENSE, month))));
            android.widget.LinearLayout wallets = findViewById(R.id.homeWallets); wallets.removeAllViews();
            if (book.state().wallets.isEmpty()) {
                TextView empty = new TextView(this); empty.setText(R.string.finance_wallet_empty); wallets.addView(empty);
            }
            for (FinanceBook.Wallet wallet : book.state().wallets) {
                android.view.View card = getLayoutInflater().inflate(R.layout.item_finance_wallet_summary, wallets, false);
                ((TextView) card.findViewById(R.id.tvFinanceWalletName)).setText(wallet.name);
                ((TextView) card.findViewById(R.id.tvFinanceWalletBalance)).setText(FinanceUi.money(book.balance(wallet.id)));
                wallets.addView(card);
            }
        });

        navSetting = findViewById(R.id.navSetting);
        tvUserName = findViewById(R.id.tvUserName);

        // Hiển thị tên từ SessionManager trước
        String name = sessionManager.getUserName();
        if (name != null && !name.isEmpty()) {
            tvUserName.setText(name);
        }

        // Quan sát dữ liệu profile từ API/Mock để đồng bộ mới nhất
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String fetchedName = resource.getData().getName();
                if (fetchedName != null && !fetchedName.isEmpty()) {
                    tvUserName.setText(fetchedName);
                    sessionManager.saveUserProfile(
                            resource.getData().getUserId(),
                            resource.getData().getEmail(),
                            fetchedName
                    );
                }
            }
        });

        // Tải profile khi mở màn hình
        userViewModel.fetchProfile();

        navSetting.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeDashboardActivity.this,
                    SettingActivity.class
            );
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null && (!sessionManager.isLoggedIn() || !java.util.Objects.equals(financeOwner, sessionManager.getUserId()))) {
            startActivity(new Intent(this, com.example.fintrack.app.auth.ui.LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish(); return;
        }
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
        if (financeModel != null) financeModel.refresh();
    }
}
