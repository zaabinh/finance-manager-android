package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.example.fintrack.app.core.ui.MoneyFormat;
import com.example.fintrack.app.core.ui.MoneyInput;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.setup.model.PendingWallet;
import com.example.fintrack.app.setup.model.PendingWalletList;
import com.example.fintrack.app.wallet.ui.WalletSheet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.FirstSetupRequest;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.remote.UpdateProfileRequest;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình thiết lập ví tiền/tài khoản (SetupWalletActivity) trong luồng cài đặt
public class SetupWalletActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;          // Nút Quay lại
    private TextView tvAddWallet;      // Thẻ/nút bấm để thêm ví mới
    private Button btnCompleteSetup;   // Nút hoàn tất cài đặt

    // Lưu trữ thông tin nhận được từ các màn hình trước (với giá trị mặc định phòng trường hợp không có dữ liệu)
    private String displayName = "Minh Anh";
    private String reminderTime = "20:00";
    private UserViewModel userViewModel;

    public static final String EXTRA_PENDING_WALLETS = "com.example.fintrack.EXTRA_PENDING_WALLETS";
    private static final String STATE_PENDING = "pendingWallets";
    // Wallets collected in this step; kept across rotation and passed on via EXTRA_PENDING_WALLETS.
    private PendingWalletList pending = new PendingWalletList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wallet); // Gắn giao diện XML vào Activity

        // Lấy dữ liệu (Tên và Giờ nhắc nhở) truyền từ các màn hình trước sang
        if (getIntent() != null) {
            if (getIntent().hasExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME)) {
                displayName = getIntent().getStringExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME);
            }
            if (getIntent().hasExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME)) {
                reminderTime = getIntent().getStringExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME);
            }
        }

        if (savedInstanceState != null && savedInstanceState.getSerializable(STATE_PENDING) instanceof PendingWalletList) {
            pending = (PendingWalletList) savedInstanceState.getSerializable(STATE_PENDING);
        } else if (getIntent().getSerializableExtra(EXTRA_PENDING_WALLETS) instanceof PendingWalletList) {
            pending = (PendingWalletList) getIntent().getSerializableExtra(EXTRA_PENDING_WALLETS);
        }
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm cho các nút
        observeFirstSetup();
        renderWallets();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putSerializable(STATE_PENDING, pending);
    }

    // Cập nhật thẻ Tiền mặt và danh sách ví chờ lưu
    private void renderWallets() {
        TextView balance = findViewById(R.id.tvCashBalance);
        TextView desc = findViewById(R.id.tvCashDesc);
        balance.setText(pending.hasCash() ? FinanceUi.money(pending.cashBalance()) : getString(R.string.setup_cash_set_balance));
        desc.setText(pending.hasCash() ? R.string.setup_cash_confirmed : R.string.wallet_default_desc);
        LinearLayout list = findViewById(R.id.llPendingWallets);
        list.removeAllViews();
        for (PendingWallet wallet : pending.others()) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_pending_wallet, list, false);
            ((TextView) row.findViewById(R.id.tvPendingName)).setText(wallet.name);
            ((TextView) row.findViewById(R.id.tvPendingDesc)).setText(
                    getString(R.string.setup_wallet_pending_desc, getString(typeLabel(wallet.walletType))));
            ((TextView) row.findViewById(R.id.tvPendingBalance)).setText(FinanceUi.money(wallet.openingBalance));
            list.addView(row);
        }
    }

    private static int typeLabel(String type) {
        if (FinanceBook.WalletType.BANK.name().equals(type)) return R.string.wallet_type_bank;
        if (FinanceBook.WalletType.CASH.name().equals(type)) return R.string.wallet_type_cash;
        return R.string.wallet_type_savings;
    }

    // Hộp thoại nhập số dư Tiền mặt; xác nhận thì thêm vào danh sách ví chờ lưu
    private void showCashDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_money_input, null);
        EditText input = content.findViewById(R.id.etDialogAmount);
        MoneyInput.attach(input, content.findViewById(R.id.chipGroupAmountSuggestions));
        if (pending.hasCash() && pending.cashBalance() > 0) input.setText(MoneyFormat.group(pending.cashBalance()));
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setup_cash_dialog_title).setView(content)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String digits = MoneyFormat.digits(input.getText());
                    pending.setCash(digits.isEmpty() ? 0 : Long.parseLong(digits));
                    renderWallets();
                }).show();
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvAddWallet = findViewById(R.id.tvAddWallet);
        btnCompleteSetup = findViewById(R.id.btnCompleteSetup);
    }

    // Thiết lập xử lý sự kiện bấm cho các nút
    private void setupListeners() {
        // Nút Back: Đóng màn hình hiện tại để quay lại màn hình thông báo
        ivBack.setOnClickListener(v -> finish());

        // Bấm vào nút Thêm ví: Mở màn hình Thêm ví mới
        tvAddWallet.setOnClickListener(v -> WalletSheet.show(this, (sheet, wallet) -> {
            // Chỉ lưu vào danh sách chờ, chưa gửi lên server
            pending.add(wallet);
            renderWallets();
            sheet.dismiss();
        }));
        findViewById(R.id.cardCashWallet).setOnClickListener(v -> showCashDialog());

        // Nút Hoàn tất: gửi toàn bộ input của 3 bước trong một request
        btnCompleteSetup.setOnClickListener(v -> userViewModel.completeFirstSetup(buildRequest()));
    }

    // shortcut: chưa gửi ví; `pending.all()` giữ danh sách ví (Tiền mặt + ví thêm), bổ sung trường wallets khi có wallet API
    private FirstSetupRequest buildRequest() {
        Intent in = getIntent();
        boolean notifEnabled = in.getBooleanExtra(SetupNotificationActivity.EXTRA_NOTIF_ENABLED, true);
        boolean budgetAlert = in.getBooleanExtra(SetupNotificationActivity.EXTRA_BUDGET_ALERT, true);
        return new FirstSetupRequest(
                new UpdateProfileRequest(displayName, in.getStringExtra(SetupBasicInfoActivity.EXTRA_TIME_ZONE)),
                new UpdateNotificationSettingsRequest(
                        notifEnabled,
                        in.getBooleanExtra(SetupNotificationActivity.EXTRA_DAILY_REMINDER, true),
                        reminderTime,
                        budgetAlert,
                        in.getIntExtra(SetupNotificationActivity.EXTRA_BUDGET_THRESHOLD, 80),
                        budgetAlert,
                        notifEnabled));
    }

    private void observeFirstSetup() {
        userViewModel.getFirstSetupResult().observe(this, resource -> {
            if (resource == null) return;
            btnCompleteSetup.setEnabled(resource.getStatus() != Resource.Status.LOADING);
            if (resource.getStatus() == Resource.Status.SUCCESS) {
                Intent intent = new Intent(SetupWalletActivity.this, SetupCompleteActivity.class);
                intent.putExtras(getIntent());
                intent.putExtra(EXTRA_PENDING_WALLETS, pending);
                // Đã lưu lên server: không cho Back quay lại các bước setup
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                Toast.makeText(this, resource.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}