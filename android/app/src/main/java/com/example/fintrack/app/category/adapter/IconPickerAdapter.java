package com.example.fintrack.app.category.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;

import java.util.List;

/**
 * IconPickerAdapter: Adapter dùng để hiển thị danh sách các icon dạng lưới (Grid),
 * giúp người dùng bấm chọn biểu tượng ưng ý cho danh mục.
 */
public class IconPickerAdapter extends RecyclerView.Adapter<IconPickerAdapter.ViewHolder> {

    /**
     * Interface lắng nghe sự kiện khi người dùng bấm chọn một icon cụ thể
     */
    public interface OnIconSelectListener {
        void onIconSelect(int iconResId); // Trả về ID tài nguyên của icon được chọn
    }

    private final List<Integer> iconList;     // Danh sách chứa ID các icon (ví dụ: R.drawable.ic_coffee, ...)
    private int selectedIconResId;            // ID của icon đang được chọn hiện tại
    private final OnIconSelectListener listener; // Biến lắng nghe sự kiện chọn icon

    // Constructor khởi tạo Adapter
    public IconPickerAdapter(List<Integer> iconList, int selectedIconResId, OnIconSelectListener listener) {
        this.iconList = iconList;
        this.selectedIconResId = selectedIconResId;
        this.listener = listener;
    }

    /**
     * Cập nhật lại icon đang được chọn từ bên ngoài và vẽ lại giao diện
     */
    public void setSelectedIconResId(int selectedIconResId) {
        this.selectedIconResId = selectedIconResId;
        notifyDataSetChanged(); // Làm mới lại RecyclerView
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Gắn giao diện XML của từng ô icon (item_icon_picker) vào ViewHolder
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int iconRes = iconList.get(position); // Lấy ID icon tại vị trí hiện tại

        // Gắn dữ liệu và xử lý khi người dùng bấm chọn icon này
        holder.bind(iconRes, iconRes == selectedIconResId, v -> {
            selectedIconResId = iconRes; // Lưu lại icon mới được bấm
            notifyDataSetChanged();      // Vẽ lại giao diện để đổi màu viền/nền icon được chọn

            // Gửi sự kiện về cho Activity biết icon nào vừa được chọn
            if (listener != null) {
                listener.onIconSelect(iconRes);
            }
        });
    }

    @Override
    public int getItemCount() {
        return iconList != null ? iconList.size() : 0; // Trả về tổng số lượng icon trong danh sách
    }

    /**
     * Lớp ViewHolder: Ánh xạ và quản lý các thành phần giao diện của một ô icon đơn lẻ
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout flIconContainer; // Khung chứa nền của icon
        private final ImageView ivPickerIcon;      // Hình ảnh icon

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các ID từ file layout item_icon_picker.xml
            flIconContainer = itemView.findViewById(R.id.flIconContainer);
            ivPickerIcon = itemView.findViewById(R.id.ivPickerIcon);
        }

        /**
         * Gắn hình ảnh icon và thay đổi giao diện (màu nền, màu icon)
         * tùy theo việc ô này có đang được chọn (isSelected) hay không.
         */
        public void bind(int iconRes, boolean isSelected, View.OnClickListener clickListener) {
            ivPickerIcon.setImageResource(iconRes); // Hiển thị hình ảnh icon

            if (isSelected) {
                // Nếu ĐƯỢC CHỌN: Đổi sang màu nền và màu icon nổi bật (Primary)
                flIconContainer.setBackgroundResource(R.drawable.bg_currency_box);
                ivPickerIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.color_primary));
            } else {
                // Nếu KHÔNG ĐƯỢC CHỌN: Hiển thị giao diện bình thường / phụ
                flIconContainer.setBackgroundResource(R.drawable.bg_expense_card);
                ivPickerIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.status_error));
            }

            // Lắng nghe sự kiện khi người dùng bấm vào ô icon này
            itemView.setOnClickListener(clickListener);
        }
    }
}