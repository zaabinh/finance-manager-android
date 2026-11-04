package com.example.fintrack.app.category.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.fintrack.app.category.model.CategoryItem;
import com.example.fintrack.app.category.repository.CategoryRepository;

import java.util.List;

/**
 * CategoryViewModel: Đóng vai trò là cầu nối trung gian giữa giao diện (UI)
 * và kho dữ liệu (Repository), giúp quản lý trạng thái và xử lý các thao tác
 * thêm, sửa, xóa, lấy dữ liệu của danh mục thu/chi.
 */
public class CategoryViewModel extends ViewModel {

    // Kho lưu trữ dữ liệu chính của danh mục
    private final CategoryRepository repository;

    // Constructor: Khởi tạo Repository khi ViewModel được tạo
    public CategoryViewModel() {
        this.repository = CategoryRepository.getInstance();
    }

    /**
     * Lấy danh sách toàn bộ danh mục dưới dạng LiveData
     * (giúp giao diện tự động cập nhật lại mỗi khi dữ liệu có thay đổi).
     */
    public LiveData<List<CategoryItem>> getCategories() {
        return repository.getCategories();
    }

    /**
     * Lấy thông tin chi tiết của một danh mục cụ thể dựa vào mã ID
     */
    public CategoryItem getCategoryById(String id) {
        return repository.getCategoryById(id);
    }

    /**
     * Thêm một danh mục mới vào hệ thống
     */
    public void addCategory(CategoryItem category) {
        repository.addCategory(category);
    }

    /**
     * Cập nhật thông tin mới cho một danh mục đã có sẵn
     */
    public void updateCategory(CategoryItem category) {
        repository.updateCategory(category);
    }

    /**
     * Xóa một danh mục khỏi hệ thống dựa theo mã ID của nó
     */
    public void deleteCategory(String categoryId) {
        repository.deleteCategory(categoryId);
    }

    /**
     * Bật hoặc tắt trạng thái hoạt động (Đang hoạt động / Đã vô hiệu hóa) của danh mục
     */
    public void toggleStatus(String categoryId, boolean isActive) {
        repository.toggleCategoryStatus(categoryId, isActive);
    }
}