package com.example.fintrack.app.setup.ui;

import com.example.fintrack.app.core.network.ErrorMapper;

import com.example.fintrack.app.finance.domain.WalletType;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.example.fintrack.app.core.ui.MoneyFormat;
import com.example.fintrack.app.core.ui.MoneyInput;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.setup.model.PendingWallet;
import com.example.fintrack.app.setup.model.PendingWalletList;
import com.example.fintrack.app.wallet.ui.WalletSheet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.databinding.DialogMoneyInputBinding;
import com.example.fintrack.databinding.ItemPendingWalletBinding;
import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySetupWalletBinding;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.FirstSetupRequest;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.remote.UpdateProfileRequest;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình thiết lập ví tiền/tài khoản (SetupWalletActivity) trong luồng cài đặt
public class SetupWalletActivity extends AppCompatActivity {

    private ActivitySetupWalletBinding binding;

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;          // Nút Quay lại
    private TextView tvAddWallet;      // Thẻ/nút bấm để thêm ví mới
    private Button btnCompleteSetup;   // Nút hoàn tất cài đặt

    // Lưu trữ thông tin nhận được từ các màn hình trước (với giá trị mặc định phòng trường hợp không có dữ liệu)
    private String displayName;
    private String reminderTime = "20:00";
    private UserViewModel userViewModel;

    public static final String EXTRA_PENDING_WALLETS = "com.example.fintrack.EXTRA_PENDING_WALLETS";
    private static final String STATE_PENDING = "pendingWallets";
    // Wallets collected in this step; kept across rotation and passed on via EXTRA_PENDING_WALLETS.
    private PendingWalletList pending = new PendingWalletList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupWalletBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // Gắn giao diện XML vào Activity

        displayName = getString(R.string.setup_default_display_name);
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
        userViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);
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
        TextView balance = binding.tvCashBalance;
        TextView desc = binding.tvCashDesc;
        balance.setText(pending.hasCash() ? FinanceUi.money(pending.cashBalance()) : getString(R.string.setup_cash_set_balance));
        desc.setText(pending.hasCash() ? R.string.setup_cash_confirmed : R.string.wallet_default_desc);
        LinearLayout list = binding.llPendingWallets;
        list.removeAllViews();
        for (PendingWallet wallet : pending.others()) {
            ItemPendingWalletBinding row = ItemPendingWalletBinding.inflate(getLayoutInflater(), list, false);
            row.tvPendingName.setText(wallet.name);
            row.tvPendingDesc.setText(
                    getString(R.string.setup_wallet_pending_desc, getString(typeLabel(wallet.walletType))));
            row.tvPendingBalance.setText(FinanceUi.money(wallet.openingBalance));
            list.addView(row.getRoot());
        }
    }

    private static int typeLabel(String type) {
        if (WalletType.BANK.name().equals(type)) return R.string.wallet_type_bank;
        if (WalletType.CASH.name().equals(type)) return R.string.wallet_type_cash;
        return R.string.wallet_type_savings;
    }

    // Hộp thoại nhập số dư Tiền mặt; xác nhận thì thêm vào danh sách ví chờ lưu
    private void showCashDialog() {
        DialogMoneyInputBinding content = DialogMoneyInputBinding.inflate(getLayoutInflater());
        EditText input = content.etDialogAmount;
        // The <include> in dialog_money_input has no id, so the chip group is not on the binding.
        MoneyInput.attach(input, content.getRoot().findViewById(R.id.chipGroupAmountSuggestions));
        if (pending.hasCash() && pending.cashBalance() > 0) input.setText(MoneyFormat.group(pending.cashBalance()));
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setup_cash_dialog_title).setView(content.getRoot())
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String digits = MoneyFormat.digits(input.getText());
                    pending.setCash(digits.isEmpty() ? 0 : Long.parseLong(digits));
                    renderWallets();
                }).show();
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        ivBack = binding.ivBack;
        tvAddWallet = binding.tvAddWallet;
        btnCompleteSetup = binding.btnCompleteSetup;
    }

    // Thiết lập xử lý sự kiện bấm cho các nút
    private void setupListeners() {
        // Nút Back: Đóng màn hình hiện tại để quay lại màn hình thông báo
        ivBack.setOnClickListener(v -> finish());

        // Bấm vào nút Thêm ví: Mở màn hình Thêm ví mới
        tvAddWallet.setOnClickListener(v -> WalletSheet.show(this, (sheet, wallet) -> {
            // Vào danh sách chờ; được gửi lên server khi bấm Hoàn tất
            pending.add(wallet);
            renderWallets();
            sheet.dismiss();
        }));
        binding.cardCashWallet.setOnClickListener(v -> showCashDialog());

        // Nút Hoàn tất: gửi toàn bộ input của 3 bước trong một request
        btnCompleteSetup.setOnClickListener(v -> userViewModel.completeFirstSetup(buildRequest()));
    }

    // Ví đã xác nhận (Tiền mặt + ví thêm) được gửi kèm; server tạo chúng cùng lúc hoàn tất thiết lập.
    private FirstSetupRequest buildRequest() {
        java.util.List<com.example.fintrack.app.wallet.remote.WalletRequest> wallets = new java.util.ArrayList<>();
        for (com.example.fintrack.app.setup.model.PendingWallet wallet : pending.all())
            wallets.add(new com.example.fintrack.app.wallet.remote.WalletRequest(wallet.name, wallet.openingBalance,
                    com.example.fintrack.app.finance.domain.WalletType.valueOf(wallet.walletType),
                    wallet.includeInAssets, wallet.sortOrder));
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
                        notifEnabled),
                wallets);
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
                Toast.makeText(this, ErrorMapper.text(this, resource.getError()), Toast.LENGTH_LONG).show();
            }
        });
    }
}
