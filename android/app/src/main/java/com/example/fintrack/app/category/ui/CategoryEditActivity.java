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

/**
 * Màn hình CategoryEditActivity: Dùng để xem, chỉnh sửa thông tin hoặc xóa
 * một danh mục thu/chi đã có sẵn trong ứng dụng.
 */
public class CategoryEditActivity extends AppCompatActivity {

    // --- 1. Khai báo các thành phần giao diện (UI) và biến dữ liệu ---
    private ImageView btnBack;                  // Nút quay lại màn hình trước
    private EditText etCategoryName;              // Ô nhập/sửa tên danh mục
    private RelativeLayout btnSelectCategoryType; // Khu vực bấm để chọn loại (Thu nhập / Chi tiêu)
    private TextView tvSelectedCategoryType;      // Hiển thị loại danh mục hiện tại
    private RecyclerView rvIconPicker;          // Danh sách hiển thị các icon để người dùng chọn
    private EditText etDescription;             // Ô nhập/sửa mô tả danh mục
    private SwitchMaterial switchStatus;        // Nút gạt bật/tắt trạng thái hoạt động của danh mục
    private TextView tvStatusLabel;             // Nhãn hiển thị chữ trạng thái (Đang hoạt động / Đã vô hiệu hóa)
    private Button btnSaveChanges;              // Nút lưu các thay đổi
    private Button btnDeleteCategory;           // Nút xóa danh mục

    private CategoryViewModel categoryViewModel;// ViewModel để xử lý dữ liệu danh mục với CSDL/Room
    private String categoryId;                  // Mã ID của danh mục cần sửa
    private CategoryItem currentCategory;       // Đối tượng chứa thông tin chi tiết của danh mục hiện tại
    private String selectedType = "EXPENSE";    // Loại danh mục mặc định (Chi tiêu)
    private int selectedIconResId = R.drawable.ic_shopping_bag; // ID icon mặc định

    // Danh sách các icon có sẵn để người dùng lựa chọn
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
        setContentView(R.layout.activity_category_edit); // Gắn giao diện XML vào Activity này

        // Lấy ID danh mục được truyền từ màn hình danh sách sang
        categoryId = getIntent().getStringExtra("CATEGORY_ID");
        // Khởi tạo ViewModel
        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        initViews();          // Ánh xạ các thành phần giao diện từ XML
        loadCategoryData();   // Tải dữ liệu cũ của danh mục lên giao diện
        setupListeners();     // Thiết lập các sự kiện bấm nút, gạt switch,...
    }

    /**
     * Ánh xạ các ID từ file XML vào các biến trong code Java
     */
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etCategoryName = findViewById(R.id.etCategoryName);
        btnSelectCategoryType = findViewById(R.id.btnSelectCategoryType);
        tvSelectedCategoryType = findViewById(R.id.tvSelectedCategoryType);
        rvIconPicker = findViewById(R.id.rvIconPicker);
        etDescription = findViewById(R.id.etDescription);
        switchStatus = findViewById(R.id.switchStatus);
        tvStatusLabel = findViewById(R.id.tvStatusLabel);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);
        btnDeleteCategory = findViewById(R.id.btnDeleteCategory);
    }

    /**
     * Tải thông tin của danh mục dựa trên ID và điền sẵn lên các trường giao diện
     */
    private void loadCategoryData() {
        // Lấy thông tin danh mục từ ViewModel (nếu ID null thì lấy mặc định là "c3")
        currentCategory = categoryViewModel.getCategoryById(categoryId != null ? categoryId : "c3");

        if (currentCategory != null) {
            etCategoryName.setText(currentCategory.getName()); // Điền tên cũ

            // Lấy loại danh mục (Thu hay Chi)
            selectedType = currentCategory.getType() != null ? currentCategory.getType() : "EXPENSE";
            tvSelectedCategoryType.setText("INCOME".equalsIgnoreCase(selectedType) ? "Thu nhập" : "Chi tiêu");

            // Lấy icon cũ
            selectedIconResId = currentCategory.getIconResId() != 0 ? currentCategory.getIconResId() : R.drawable.ic_shopping_bag;

            etDescription.setText(currentCategory.getDescription()); // Điền mô tả cũ

            // Thiết lập trạng thái công tắc (Bật/Tắt)
            switchStatus.setChecked(currentCategory.isActive());
            tvStatusLabel.setText(currentCategory.isActive() ? "Đang hoạt động" : "Đã vô hiệu hóa");
        }

        setupIconPicker(); // Khởi tạo bảng chọn icon
    }

    /**
     * Thiết lập danh sách chọn Icon (hiển thị dạng lưới 5 cột)
     */
    private void setupIconPicker() {
        IconPickerAdapter adapter = new IconPickerAdapter(availableIcons, selectedIconResId, iconResId -> {
            selectedIconResId = iconResId; // Lưu lại icon mới khi người dùng bấm chọn
        });
        rvIconPicker.setLayoutManager(new GridLayoutManager(this, 5)); // Hiển thị 5 cột
        rvIconPicker.setAdapter(adapter);
    }

    /**
     * Thiết lập sự kiện bấm nút và thao tác cho các thành phần trên màn hình
     */
    private void setupListeners() {
        // Nút quay lại đóng màn hình
        btnBack.setOnClickListener(v -> finish());

        // Mở hộp thoại (Dialog) chọn loại danh mục: Chi tiêu hoặc Thu nhập
        btnSelectCategoryType.setOnClickListener(v -> {
            String[] types = {"Chi tiêu", "Thu nhập"};
            new AlertDialog.Builder(this)
                    .setTitle("Chọn loại danh mục")
                    .setItems(types, (dialog, which) -> {
                        if (which == 0) {
                            selectedType = "EXPENSE";
                            tvSelectedCategoryType.setText("Chi tiêu");
                        } else {
                            selectedType = "INCOME";
                            tvSelectedCategoryType.setText("Thu nhập");
                        }
                    })
                    .show();
        });

        // Lắng nghe sự kiện bật/tắt công tắc trạng thái để đổi chữ nhãn tương ứng
        switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                tvStatusLabel.setText("Đang hoạt động");
            } else {
                tvStatusLabel.setText("Đã vô hiệu hóa");
            }
        });

        // Xử lý khi bấm nút "Lưu thay đổi"
        btnSaveChanges.setOnClickListener(v -> {
            // 1. Kiểm tra xem người dùng đã nhập tên danh mục chưa
            String name = etCategoryName.getText().toString().trim();
            if (name.isEmpty()) {
                etCategoryName.setError("Vui lòng nhập tên danh mục"); // Báo lỗi nếu để trống
                return;
            }

            // 2. Cập nhật các thông tin mới vào đối tượng danh mục hiện tại
            if (currentCategory != null) {
                currentCategory.setName(name);
                currentCategory.setType(selectedType);
                currentCategory.setIconResId(selectedIconResId);
                currentCategory.setDescription(etDescription.getText().toString().trim());
                currentCategory.setActive(switchStatus.isChecked());

                // 3. Gọi ViewModel để cập nhật vào cơ sở dữ liệu
                categoryViewModel.updateCategory(currentCategory);
            }

            // 4. Thông báo và đóng màn hình
            Toast.makeText(this, "Đã lưu thay đổi danh mục!", Toast.LENGTH_SHORT).show();
            finish();
        });

        // Xử lý khi bấm nút "Xóa danh mục"
        btnDeleteCategory.setOnClickListener(v -> {
            // Hiện hộp thoại cảnh báo xác nhận trước khi xóa
            new AlertDialog.Builder(this)
                    .setTitle("Xóa danh mục")
                    .setMessage("Bạn có chắc chắn muốn xóa danh mục này?")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        if (currentCategory != null) {
                            // Gọi ViewModel thực hiện xóa theo ID
                            categoryViewModel.deleteCategory(currentCategory.getId());
                        }
                        Toast.makeText(this, "Đã xóa danh mục!", Toast.LENGTH_SHORT).show();
                        finish(); // Đóng màn hình sau khi xóa xong
                    })
                    .setNegativeButton("Huỷ", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }
}