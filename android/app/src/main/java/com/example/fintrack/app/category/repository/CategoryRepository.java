package com.example.fintrack.app.category.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.R;
import com.example.fintrack.app.category.model.CategoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * CategoryRepository: Đóng vai trò là nguồn quản lý dữ liệu (Data Source) cho danh mục.
 * Sử dụng mô hình Singleton (chỉ có 1 instance duy nhất trong toàn bộ ứng dụng)
 * để lưu trữ, thêm, sửa, xóa danh mục và cung cấp dữ liệu dạng LiveData cho ViewModel.
 */
public class CategoryRepository {

    private static CategoryRepository instance; // Biến giữ thể hiện duy nhất của Repository

    // LiveData chứa danh sách danh mục, giúp tự động báo cáo thay đổi cho UI
    private final MutableLiveData<List<CategoryItem>> categoriesLiveData = new MutableLiveData<>();

    // Danh sách gốc lưu trữ các đối tượng CategoryItem trong bộ nhớ tạm
    private final List<CategoryItem> categoryList = new ArrayList<>();

    // Constructor riêng tư (Singleton) -> Khởi tạo dữ liệu mẫu khi vừa tạo Repository
    private CategoryRepository() {
        initDefaultData();
    }

    /**
     * Phương thức lấy thể hiện duy nhất (Singleton) của CategoryRepository
     */
    public static synchronized CategoryRepository getInstance() {
        if (instance == null) {
            instance = new CategoryRepository();
        }
        return instance;
    }

    /**
     * Khởi tạo dữ liệu mẫu mặc định (Các danh mục thu nhập và chi tiêu ban đầu)
     */
    private void initDefaultData() {
        if (categoryList.isEmpty()) {
            // Các danh mục Thu nhập (INCOME)
            categoryList.add(new CategoryItem("c1", "Lương tháng", "INCOME", R.drawable.ic_briefcase, "Lương hàng tháng", true));
            categoryList.add(new CategoryItem("c2", "Thưởng", "INCOME", R.drawable.ic_gift, "Thưởng dự án, lễ tết", false));

            // Các danh mục Chi tiêu (EXPENSE)
            categoryList.add(new CategoryItem("c3", "Ăn uống & Cafe", "EXPENSE", R.drawable.ic_coffee, "Cà phê, ăn trưa, nhà hàng", true));
            categoryList.add(new CategoryItem("c4", "Di chuyển", "EXPENSE", R.drawable.ic_car, "Xăng xe, Grab, bảo dưỡng", true));
            categoryList.add(new CategoryItem("c5", "Giải trí", "EXPENSE", R.drawable.ic_ticket, "Xem phim, ca nhạc", false));
            categoryList.add(new CategoryItem("c6", "Mua sắm", "EXPENSE", R.drawable.ic_shopping_bag, "Quần áo, đồ dùng, mỹ phẩm, ...", true));
            categoryList.add(new CategoryItem("c7", "Hóa đơn", "EXPENSE", R.drawable.ic_receipt, "Điện, nước, internet", true));

            // Cập nhật danh sách mẫu lên LiveData để các thành phần khác quan sát
            categoriesLiveData.setValue(new ArrayList<>(categoryList));
        }
    }

    /**
     * Cung cấp danh sách danh mục dưới dạng LiveData
     */
    public LiveData<List<CategoryItem>> getCategories() {
        return categoriesLiveData;
    }

    /**
     * Tìm và trả về một danh mục cụ thể dựa theo mã ID của nó
     */
    public CategoryItem getCategoryById(String id) {
        for (CategoryItem c : categoryList) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        // Nếu không tìm thấy, trả về phần tử đầu tiên (hoặc null nếu danh sách trống)
        return !categoryList.isEmpty() ? categoryList.get(0) : null;
    }

    /**
     * Thêm một danh mục mới vào danh sách và cập nhật lại LiveData
     */
    public void addCategory(CategoryItem category) {
        categoryList.add(category);
        categoriesLiveData.setValue(new ArrayList<>(categoryList));
    }

    /**
     * Cập nhật thông tin của một danh mục đã tồn tại trong danh sách
     */
    public void updateCategory(CategoryItem updatedCategory) {
        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryList.get(i).getId().equals(updatedCategory.getId())) {
                categoryList.set(i, updatedCategory); // Thay thế bằng thông tin mới
                break;
            }
        }
        categoriesLiveData.setValue(new ArrayList<>(categoryList));
    }

    /**
     * Xóa một danh mục khỏi danh sách dựa theo mã ID
     */
    public void deleteCategory(String categoryId) {
        categoryList.removeIf(c -> c.getId().equals(categoryId));
        categoriesLiveData.setValue(new ArrayList<>(categoryList));
    }

    /**
     * Thay đổi trạng thái hoạt động (Bật/Tắt) của một danh mục theo ID
     */
    public void toggleCategoryStatus(String categoryId, boolean isActive) {
        for (CategoryItem c : categoryList) {
            if (c.getId().equals(categoryId)) {
                c.setActive(isActive); // Cập nhật trạng thái mới
                break;
            }
        }
        categoriesLiveData.setValue(new ArrayList<>(categoryList));
    }
}