package com.example.fintrack.app.category.ui;

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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.IconPickerAdapter;
import com.example.fintrack.app.category.model.CategoryItem;
import com.example.fintrack.app.category.viewmodel.CategoryViewModel;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Màn hình dùng để thêm mới một danh mục thu/chi
public class CategoryAddActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (Views) có trong file XML
    private ImageView btnBack;
    private EditText etCategoryName;
    private RelativeLayout btnSelectCategoryType;
    private TextView tvSelectedCategoryType;
    private RecyclerView rvIconPicker;
    private EditText etDescription;
    private SwitchMaterial switchStatus;
    private TextView tvStatusLabel;
    private Button btnCreateCategory;
    private Button btnCancel;

    // Khai báo ViewModel để xử lý dữ liệu và các biến lưu trạng thái mặc định
    private CategoryViewModel categoryViewModel;
    private String selectedType = "EXPENSE"; // Mặc định là loại Chi tiêu ("EXPENSE")
    private int selectedIconResId = R.drawable.ic_coffee; // Icon mặc định ban đầu

    // Danh sách chứa các ID hình ảnh (icon) để người dùng lựa chọn
    private final List<Integer> availableIcons = Arrays.asList(
            R.drawable.ic_coffee,
            R.drawable.ic_car,
            R.drawable.ic_ticket,
            R.drawable.ic_shopping_bag,
            R.drawable.ic_receipt,
            R.drawable.ic_heart,
            R.drawable.ic_briefcase,
            R.drawable.ic_gift,
            R.drawable.ic_school,
            R.drawable.ic_more_horiz,
            R.drawable.ic_image_placeholder
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_add); // Liên kết với giao diện XML

        // Khởi tạo ViewModel để kết nối với tầng dữ liệu (database/repository)
        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        initViews();       // Ánh xạ các thành phần giao diện từ XML sang Java
        setupIconPicker(); // Cài đặt danh sách chọn icon hiển thị dạng lưới
        setupListeners();  // Lắng nghe các sự kiện bấm nút, chọn lựa của người dùng
    }

    // Hàm ánh xạ ID từ file XML vào các biến trong code Java
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etCategoryName = findViewById(R.id.etCategoryName);
        btnSelectCategoryType = findViewById(R.id.btnSelectCategoryType);
        tvSelectedCategoryType = findViewById(R.id.tvSelectedCategoryType);
        rvIconPicker = findViewById(R.id.rvIconPicker);
        etDescription = findViewById(R.id.etDescription);
        switchStatus = findViewById(R.id.switchStatus);
        tvStatusLabel = findViewById(R.id.tvStatusLabel);
        btnCreateCategory = findViewById(R.id.btnCreateCategory);
        btnCancel = findViewById(R.id.btnCancel);
    }

    // Cấu hình RecyclerView để hiển thị danh sách icon dạng lưới (5 cột mỗi hàng)
    private void setupIconPicker() {
        IconPickerAdapter adapter = new IconPickerAdapter(availableIcons, selectedIconResId, iconResId -> {
            selectedIconResId = iconResId; // Cập nhật lại icon khi người dùng bấm chọn icon khác
        });
        rvIconPicker.setLayoutManager(new GridLayoutManager(this, 5)); // Hiển thị lưới 5 cột
        rvIconPicker.setAdapter(adapter);
    }

    // Hàm chứa tất cả sự kiện tương tác của người dùng trên màn hình
    private void setupListeners() {
        // Nút quay lại và nút hủy: Đóng màn hình hiện tại và quay về màn hình trước
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        // Sự kiện khi bấm vào ô chọn loại danh mục (Thu nhập hay Chi tiêu)
        btnSelectCategoryType.setOnClickListener(v -> {
            String[] types = {"Chi tiêu", "Thu nhập"};
            new AlertDialog.Builder(this)
                    .setTitle("Chọn loại danh mục")
                    .setItems(types, (dialog, which) -> {
                        if (which == 0) {
                            selectedType = "EXPENSE"; // Lưu mã loại là Chi tiêu
                            tvSelectedCategoryType.setText("Chi tiêu");
                        } else {
                            selectedType = "INCOME";  // Lưu mã loại là Thu nhập
                            tvSelectedCategoryType.setText("Thu nhập");
                        }
                        tvSelectedCategoryType.setTextColor(getColor(R.color.text_primary));
                    })
                    .show();
        });

        // Sự kiện gạt công tắc (Switch) bật/tắt trạng thái hoạt động của danh mục
        switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                tvStatusLabel.setText("Đang hoạt động");
            } else {
                tvStatusLabel.setText("Đã vô hiệu hóa");
            }
        });

        // Sự kiện khi bấm nút "Tạo danh mục"
        btnCreateCategory.setOnClickListener(v -> {
            // Lấy tên danh mục và xóa khoảng trắng thừa ở đầu/cuối
            String name = etCategoryName.getText().toString().trim();

            // Kiểm tra xem người dùng đã nhập tên chưa, nếu chưa thì báo lỗi
            if (name.isEmpty()) {
                etCategoryName.setError("Vui lòng nhập tên danh mục");
                return;
            }

            // Lấy thông tin mô tả và trạng thái công tắc
            String desc = etDescription.getText().toString().trim();
            boolean isActive = switchStatus.isChecked();

            // Tạo đối tượng CategoryItem mới với ID tự động sinh ngẫu nhiên (UUID)
            CategoryItem category = new CategoryItem(
                    UUID.randomUUID().toString(), // Mã định danh ngẫu nhiên duy nhất
                    name,                         // Tên danh mục
                    selectedType,                 // Loại (EXPENSE / INCOME)
                    selectedIconResId,            // ID của icon đã chọn
                    desc,                         // Mô tả thêm
                    isActive                      // Trạng thái hoạt động
            );

            // Gửi dữ liệu lên ViewModel để lưu vào cơ sở dữ liệu
            categoryViewModel.addCategory(category);

            // Thông báo thành công và đóng màn hình
            Toast.makeText(this, "Tạo danh mục mới thành công!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}