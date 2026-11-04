package com.example.fintrack.app.category.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.category.model.CategoryItem;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

/**
 * CategoryAdapter: Adapter dùng để chuyển đổi danh sách các danh mục (CategoryItem)
 * thành các dòng hiển thị trên danh sách (RecyclerView) trong màn hình quản lý danh mục.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    /**
     * Interface lắng nghe các sự kiện tương tác của người dùng trên mỗi dòng danh mục:
     * - onCategoryClick: Bấm vào dòng danh mục để xem/sửa chi tiết.
     * - onStatusToggle: Bật/tắt công tắc trạng thái hoạt động ngay trên danh sách.
     */
    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryItem category);
        void onStatusToggle(CategoryItem category, boolean isChecked);
    }

    private final List<CategoryItem> categoryList = new ArrayList<>(); // Danh sách hiển thị hiện tại
    private final OnCategoryClickListener listener;                   // Biến lắng nghe sự kiện bấm nút

    // Constructor nhận vào listener từ Activity
    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    /**
     * Cập nhật danh sách mới cho Adapter và làm mới lại giao diện hiển thị
     */
    public void setCategories(List<CategoryItem> categories) {
        categoryList.clear();
        if (categories != null) {
            categoryList.addAll(categories);
        }
        notifyDataSetChanged(); // Báo RecyclerView vẽ lại giao diện
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Gắn giao diện XML của từng dòng (item_category) vào ViewHolder
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Đổ dữ liệu của từng danh mục vào các thành phần giao diện tương ứng tại vị trí (position)
        holder.bind(categoryList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return categoryList.size(); // Trả về tổng số lượng danh mục có trong danh sách
    }

    /**
     * Lớp ViewHolder: Ánh xạ và quản lý các thành phần giao diện của một dòng danh mục đơn lẻ
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout flIconBg;         // Nền bao quanh icon danh mục
        private final ImageView ivCategoryIcon;     // Hình ảnh icon đại diện
        private final TextView tvCategoryName;      // Tên danh mục
        private final TextView tvCategoryStatus;    // Nhãn chữ hiển thị trạng thái (Đang hoạt động / Đã vô hiệu hóa)
        private final SwitchMaterial switchStatus;  // Công tắc bật/tắt trạng thái

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các ID từ file layout item_category.xml
            flIconBg = itemView.findViewById(R.id.flIconBg);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryStatus = itemView.findViewById(R.id.tvCategoryStatus);
            switchStatus = itemView.findViewById(R.id.switchStatus);
        }

        /**
         * Gán dữ liệu của một đối tượng CategoryItem lên các thành phần giao diện của dòng
         */
        public void bind(CategoryItem category, OnCategoryClickListener listener) {
            Context context = itemView.getContext();
            tvCategoryName.setText(category.getName()); // Hiển thị tên danh mục

            // Hiển thị trạng thái hoạt động bằng chữ và màu sắc tương ứng
            if (category.isActive()) {
                tvCategoryStatus.setText("Đang hoạt động");
                tvCategoryStatus.setTextColor(ContextCompat.getColor(context, R.color.status_success));
            } else {
                tvCategoryStatus.setText("Đã vô hiệu hóa");
                tvCategoryStatus.setTextColor(ContextCompat.getColor(context, R.color.neutral_text_400));
            }

            // Gỡ sự kiện lắng nghe cũ trước khi đổi trạng thái Switch để tránh lỗi gọi lặp vô ý
            switchStatus.setOnCheckedChangeListener(null);
            switchStatus.setChecked(category.isActive());

            // Tùy chỉnh màu sắc nền icon tùy thuộc vào loại Thu nhập (INCOME) hay Chi tiêu (EXPENSE)
            if ("INCOME".equalsIgnoreCase(category.getType())) {
                flIconBg.setBackgroundResource(R.drawable.bg_income_card);
                ivCategoryIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_success));
            } else {
                flIconBg.setBackgroundResource(R.drawable.bg_expense_card);
                ivCategoryIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_error));
            }

            // Hiển thị hình ảnh icon nếu có
            if (category.getIconResId() != 0) {
                ivCategoryIcon.setImageResource(category.getIconResId());
            }

            // Lắng nghe sự kiện khi người dùng gạt công tắc bật/tắt trạng thái
            switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onStatusToggle(category, isChecked);
                }
            });

            // Lắng nghe sự kiện khi người dùng bấm vào toàn bộ dòng danh mục (để sửa)
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}