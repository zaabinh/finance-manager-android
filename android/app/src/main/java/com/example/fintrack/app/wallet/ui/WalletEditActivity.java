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

// Activity này đảm nhận chức năng chỉnh sửa thông tin của một ví đã tồn tại hoặc xoá ví đó
public class WalletEditActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI Views)
    private ImageView btnBack;
    private EditText etWalletName;
    private TextView pillCash; // Nút chọn loại "Tiền mặt"
    private TextView pillBank; // Nút chọn loại "Ngân hàng"
    private TextView pillEWallet; // Nút chọn loại "Ví điện tử"
    private TextView pillSavings; // Nút chọn loại "Tiết kiệm"
    private RelativeLayout btnSelectProvider; // Vùng bấm để chọn tên Ngân hàng/Ví
    private TextView tvSelectedProvider;
    private EditText etBalance;
    private EditText etNote;
    private Button btnSaveChanges;
    private Button btnDeleteWallet;

    // Khai báo ViewModel để thao tác với dữ liệu
    private WalletViewModel walletViewModel;
    private String walletId;
    private WalletItem currentWallet;

    // Giá trị mặc định ban đầu cho loại ví và nhà cung cấp
    private String selectedType = "Ngân hàng";
    private String selectedProvider = "Techcombank";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_edit);

        // Nhận ID của ví cần sửa từ Intent (do màn hình trước truyền sang)
        walletId = getIntent().getStringExtra("WALLET_ID");

        // Khởi tạo ViewModel
        walletViewModel = new ViewModelProvider(this).get(WalletViewModel.class);

        // Gọi lần lượt các hàm cài đặt giao diện và nạp dữ liệu
        initViews();
        loadWalletData();
        setupTypePillListeners();
        setupListeners();
    }

    // Ánh xạ các biến với các view trong file layout XML thông qua ID
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
        btnSaveChanges = findViewById(R.id.btnSaveChanges);
        btnDeleteWallet = findViewById(R.id.btnDeleteWallet);
    }

    // Lấy dữ liệu của ví hiện tại từ ViewModel và hiển thị lên các ô nhập liệu
    private void loadWalletData() {
        // Lấy thông tin ví theo ID, nếu không có ID thì lấy tạm "w1" (dùng cho mục đích test)
        currentWallet = walletViewModel.getWalletById(walletId != null ? walletId : "w1");

        if (currentWallet != null) {
            etWalletName.setText(currentWallet.getName());

            // Lấy loại ví và nhà cung cấp, nếu null thì gán giá trị mặc định
            selectedType = currentWallet.getType() != null ? currentWallet.getType() : "Ngân hàng";
            selectedProvider = currentWallet.getProvider() != null ? currentWallet.getProvider() : "Techcombank";

            tvSelectedProvider.setText(selectedProvider);
            // Ép kiểu double sang long để hiển thị số tròn (không có số thập phân), rồi chuyển thành String
            etBalance.setText(String.valueOf((long) currentWallet.getBalance()));
            etNote.setText(currentWallet.getNote());

            // Cập nhật trạng thái màu sắc cho các nút chọn loại ví (pill) tương ứng với dữ liệu
            if ("Tiền mặt".equalsIgnoreCase(selectedType)) updatePillSelection("Tiền mặt", pillCash);
            else if ("Ví điện tử".equalsIgnoreCase(selectedType)) updatePillSelection("Ví điện tử", pillEWallet);
            else if ("Tiết kiệm".equalsIgnoreCase(selectedType)) updatePillSelection("Tiết kiệm", pillSavings);
            else updatePillSelection("Ngân hàng", pillBank);
        }
    }

    // Cài đặt sự kiện khi người dùng bấm vào các nút chọn loại ví
    private void setupTypePillListeners() {
        pillCash.setOnClickListener(v -> updatePillSelection("Tiền mặt", pillCash));
        pillBank.setOnClickListener(v -> updatePillSelection("Ngân hàng", pillBank));
        pillEWallet.setOnClickListener(v -> updatePillSelection("Ví điện tử", pillEWallet));
        pillSavings.setOnClickListener(v -> updatePillSelection("Tiết kiệm", pillSavings));
    }

    // Xử lý đổi màu nút (pill) khi được chọn
    private void updatePillSelection(String type, TextView selectedPill) {
        selectedType = type; // Lưu lại loại ví đang chọn
        resetPillStyles();   // Reset tất cả các nút về màu xám (chưa chọn)

        // Đổi màu nền và màu chữ cho nút vừa được bấm
        selectedPill.setBackgroundResource(R.drawable.bg_pill_selected);
        selectedPill.setTextColor(getColor(R.color.white));
    }

    // Đưa tất cả các nút loại ví về trạng thái mặc định (màu nhạt/xám)
    private void resetPillStyles() {
        TextView[] pills = {pillCash, pillBank, pillEWallet, pillSavings};
        for (TextView pill : pills) {
            pill.setBackgroundResource(R.drawable.bg_pill_unselected);
            pill.setTextColor(getColor(R.color.text_secondary));
        }
    }

    // Cài đặt sự kiện cho các nút chức năng chính (Lưu, Xoá, Chọn ngân hàng, Quay lại)
    private void setupListeners() {
        // Nút quay lại
        btnBack.setOnClickListener(v -> finish());

        // Mở hộp thoại chọn Ngân hàng / Ví điện tử
        btnSelectProvider.setOnClickListener(v -> {
            String[] providers = {"Techcombank", "Vietcombank", "MB Bank", "VPBank", "Momo", "ZaloPay"};
            new AlertDialog.Builder(this)
                    .setTitle("Chọn ngân hàng / nhà cung cấp")
                    // Hiển thị danh sách, khi chọn sẽ cập nhật biến selectedProvider và text trên giao diện
                    .setItems(providers, (dialog, which) -> {
                        selectedProvider = providers[which];
                        tvSelectedProvider.setText(selectedProvider);
                    })
                    .show();
        });

        // Nút Lưu thay đổi
        btnSaveChanges.setOnClickListener(v -> {
            String name = etWalletName.getText().toString().trim();

            // Kiểm tra tính hợp lệ: Tên ví không được để trống
            if (name.isEmpty()) {
                etWalletName.setError("Vui lòng nhập tên ví");
                return; // Dừng hàm, không lưu tiếp
            }

            double balance = 0;
            // Xoá dấu chấm (nếu có) trong số tiền người dùng nhập để chuyển đổi về số chuẩn
            String balanceStr = etBalance.getText().toString().trim().replace(".", "");
            if (!balanceStr.isEmpty()) {
                try {
                    // Cố gắng chuyển chuỗi thành số thực (double)
                    balance = Double.parseDouble(balanceStr);
                } catch (NumberFormatException ignored) {} // Bỏ qua lỗi nếu người dùng nhập ký tự không phải số
            }

            // Cập nhật dữ liệu mới vào đối tượng currentWallet
            if (currentWallet != null) {
                currentWallet.setName(name);
                currentWallet.setType(selectedType);
                currentWallet.setProvider(selectedProvider);
                currentWallet.setBalance(balance);
                currentWallet.setNote(etNote.getText().toString().trim());

                // Gọi ViewModel để lưu thông tin cập nhật vào Database
                walletViewModel.updateWallet(currentWallet);
            }

            Toast.makeText(this, "Đã lưu thay đổi!", Toast.LENGTH_SHORT).show();
            finish(); // Đóng Activity và quay về màn hình trước
        });

        // Nút Xoá ví
        btnDeleteWallet.setOnClickListener(v -> {
            // Hiện hộp thoại xác nhận (tránh việc người dùng bấm nhầm)
            new AlertDialog.Builder(this)
                    .setTitle("Xoá ví")
                    .setMessage("Bạn có chắc chắn muốn xoá ví này?")
                    .setPositiveButton("Xoá", (dialog, which) -> {
                        if (currentWallet != null) {
                            walletViewModel.deleteWallet(currentWallet.getId());
                        }
                        Toast.makeText(this, "Đã xoá ví!", Toast.LENGTH_SHORT).show();
                        finish(); // Đóng Activity sau khi xoá xong
                    })
                    .setNegativeButton("Huỷ", (dialog, which) -> dialog.dismiss()) // Tắt hộp thoại nếu chọn huỷ
                    .show();
        });
    }
}