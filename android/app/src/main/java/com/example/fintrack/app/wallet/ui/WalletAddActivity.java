package com.example.fintrack.app.wallet.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.wallet.model.WalletItem;
import com.example.fintrack.app.wallet.viewmodel.WalletViewModel;

import java.util.UUID;

/**
 * Màn hình WalletAddActivity: Dùng để người dùng tạo mới một chiếc ví
 * (Ví dụ: Tiền mặt, Ngân hàng, Ví điện tử, Tiết kiệm).
 */
public class WalletAddActivity extends AppCompatActivity {

    // --- 1. Khai báo các thành phần giao diện (UI) và biến dữ liệu ---
    private ImageView btnBack;                  // Nút quay lại màn hình trước
    private EditText etWalletName;              // Ô nhập tên ví
    private TextView pillCash, pillBank, pillEWallet, pillSavings; // Các nút chọn loại ví (dạng pill)
    private RelativeLayout btnSelectProvider;   // Khu vực bấm để chọn ngân hàng/nhà cung cấp
    private TextView tvSelectedProvider;        // Hiển thị tên ngân hàng/nhà cung cấp đã chọn
    private EditText etBalance;                 // Ô nhập số dư ban đầu
    private EditText etNote;                    // Ô nhập ghi chú
    private Button btnCreateWallet;             // Nút xác nhận tạo ví
    private Button btnCancel;                   // Nút hủy bỏ

    private WalletViewModel walletViewModel;    // ViewModel để xử lý dữ liệu (lưu ví vào DB/Room)
    private String selectedType = "Ngân hàng";  // Loại ví mặc định ban đầu
    private String selectedProvider = "Techcombank"; // Ngân hàng mặc định ban đầu

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_add); // Gắn giao diện XML vào Activity này

        // Khởi tạo ViewModel để tương tác dữ liệu
        walletViewModel = new ViewModelProvider(this).get(WalletViewModel.class);

        initViews();                // Ánh xạ các thành phần giao diện từ XML sang Java
        setupTypePillListeners();   // Lắng nghe sự kiện chọn loại ví
        setupListeners();           // Lắng nghe sự kiện bấm các nút khác (Lưu, Hủy, Chọn ngân hàng...)
    }

    /**
     * Ánh xạ các ID từ file XML vào các biến trong code Java
     */
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etWalletName = findViewById(R.id.etWalletName);
        pillCash = findViewById(R.id.pillCash);
        pillBank = findViewById(R.id.pillBank);
        pillEWallet = findViewById(R.id.pillEWallet);
        pillSavings = findViewById(R.id.pillSavings);
        btnSelectProvider = findViewById(R.id.btnSelectProvider);
        tvSelectedProvider = findViewById(R.id.tvSelectedProvider);
        etBalance = findViewById(R.id.etBalance);
        etNote = findViewById(R.id.etNote);
        btnCreateWallet = findViewById(R.id.btnCreateWallet);
        btnCancel = findViewById(R.id.btnCancel);
    }

    /**
     * Thiết lập sự kiện bấm cho các nút chọn loại ví (Tiền mặt, Ngân hàng, Ví điện tử, Tiết kiệm)
     */
    private void setupTypePillListeners() {
        pillCash.setOnClickListener(v -> updatePillSelection("Tiền mặt", pillCash));
        pillBank.setOnClickListener(v -> updatePillSelection("Ngân hàng", pillBank));
        pillEWallet.setOnClickListener(v -> updatePillSelection("Ví điện tử", pillEWallet));
        pillSavings.setOnClickListener(v -> updatePillSelection("Tiết kiệm", pillSavings));
    }

    /**
     * Cập nhật giao diện khi người dùng chọn một loại ví mới:
     * - Lưu lại tên loại ví được chọn.
     * - Đổi màu nút được chọn thành sáng lên (đã chọn).
     * - Đổi các nút còn lại về trạng thái bình thường (chưa chọn).
     */
    private void updatePillSelection(String type, TextView selectedPill) {
        selectedType = type;
        resetPillStyles(); // Đưa tất cả về trạng thái chưa chọn trước
        selectedPill.setBackgroundResource(R.drawable.bg_pill_selected); // Đổi nền nút được chọn
        selectedPill.setTextColor(getColor(R.color.white));              // Đổi chữ thành màu trắng
    }

    /**
     * Reset toàn bộ các nút chọn loại ví về trạng thái chưa chọn (màu nền mặc định, chữ xám)
     */
    private void resetPillStyles() {
        TextView[] pills = {pillCash, pillBank, pillEWallet, pillSavings};
        for (TextView pill : pills) {
            pill.setBackgroundResource(R.drawable.bg_pill_unselected);
            pill.setTextColor(getColor(R.color.text_secondary));
        }
    }

    /**
     * Thiết lập sự kiện bấm nút cho các thành phần chức năng chính
     */
    private void setupListeners() {
        // Nút quay lại hoặc hủy bỏ thì đóng màn hình
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        // Mở hộp thoại (Dialog) cho phép người dùng chọn Ngân hàng hoặc Nhà cung cấp ví
        btnSelectProvider.setOnClickListener(v -> {
            String[] providers = {"Techcombank", "Vietcombank", "MB Bank", "VPBank", "Momo", "ZaloPay"};
            new AlertDialog.Builder(this)
                    .setTitle("Chọn ngân hàng / nhà cung cấp")
                    .setItems(providers, (dialog, which) -> {
                        selectedProvider = providers[which]; // Lấy tên nhà cung cấp được bấm
                        tvSelectedProvider.setText(selectedProvider); // Hiển thị lên giao diện
                        tvSelectedProvider.setTextColor(getColor(R.color.text_primary));
                    })
                    .show();
        });

        // Xử lý logic khi người dùng bấm nút "Tạo ví"
        btnCreateWallet.setOnClickListener(v -> {
            // 1. Kiểm tra xem người dùng đã nhập tên ví chưa
            String name = etWalletName.getText().toString().trim();
            if (name.isEmpty()) {
                etWalletName.setError("Vui lòng nhập tên ví"); // Báo lỗi nếu để trống
                return;
            }

            // 2. Lấy số dư, chuyển đổi từ kiểu chữ (String) sang số (double)
            double balance = 0;
            String balanceStr = etBalance.getText().toString().trim();
            if (!balanceStr.isEmpty()) {
                try {
                    balance = Double.parseDouble(balanceStr);
                } catch (NumberFormatException ignored) {
                    // Nếu nhập sai định dạng số thì giữ nguyên balance = 0
                }
            }

            // 3. Lấy ghi chú (nếu có)
            String note = etNote.getText().toString().trim();

            // 4. Tạo đối tượng WalletItem mới chứa toàn bộ thông tin người dùng vừa nhập
            WalletItem wallet = new WalletItem(
                    UUID.randomUUID().toString(), // Tạo ID ngẫu nhiên duy nhất cho ví
                    name,                         // Tên ví
                    selectedType,                 // Loại ví (Tiền mặt, Ngân hàng,...)
                    selectedProvider,             // Nhà cung cấp (Techcombank, Momo,...)
                    balance,                      // Số dư ban đầu
                    "VND",                        // Đơn vị tiền tệ mặc định
                    note,                         // Ghi chú
                    true                          // Trạng thái hoạt động
            );

            // 5. Gọi ViewModel để lưu ví vào hệ thống
            walletViewModel.addWallet(wallet);

            // 6. Thông báo thành công và đóng màn hình
            Toast.makeText(this, "Tạo ví mới thành công!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}