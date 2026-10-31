package com.example.fintrack.app.wallet.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.wallet.model.TransactionItem;

import java.util.ArrayList;
import java.util.List;

// Adapter chịu trách nhiệm chuyển đổi danh sách các giao dịch (TransactionItem) thành các dòng giao diện (ViewHolder) để hiển thị lên RecyclerView
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    // Danh sách lưu trữ các giao dịch hiển thị trên RecyclerView
    private final List<TransactionItem> list = new ArrayList<>();

    // Phương thức cập nhật dữ liệu mới cho Adapter và làm mới giao diện
    public void setTransactions(List<TransactionItem> transactions) {
        list.clear();
        if (transactions != null) {
            list.addAll(transactions);
        }
        notifyDataSetChanged(); // Báo cho RecyclerView vẽ lại giao diện với dữ liệu mới
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Khởi tạo giao diện cho từng dòng (item) từ file XML `item_recent_transaction`
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Gắn dữ liệu của giao dịch tại vị trí `position` vào ViewHolder tương ứng
        holder.bind(list.get(position));
    }

    @Override
    public int getItemCount() {
        // Trả về tổng số lượng giao dịch có trong danh sách
        return list.size();
    }

    // Lớp ViewHolder giúp lưu giữ tham chiếu đến các View con trong từng dòng để tránh việc gọi findViewById nhiều lần (tối ưu hiệu năng)
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivTxnIcon;      // Biểu tượng giao dịch
        private final TextView tvTxnTitle;      // Tên giao dịch (ví dụ: Highlands Coffee)
        private final TextView tvTxnSubtitle;   // Thông tin chi tiết phụ (ví dụ: Ngân hàng, thời gian)
        private final TextView tvTxnAmount;     // Số tiền giao dịch

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các thành phần giao diện từ file XML của item
            ivTxnIcon = itemView.findViewById(R.id.ivTxnIcon);
            tvTxnTitle = itemView.findViewById(R.id.tvTxnTitle);
            tvTxnSubtitle = itemView.findViewById(R.id.tvTxnSubtitle);
            tvTxnAmount = itemView.findViewById(R.id.tvTxnAmount);
        }

        // Đổ dữ liệu từ đối tượng TransactionItem vào các View và xử lý màu sắc hiển thị
        public void bind(TransactionItem item) {
            Context context = itemView.getContext();
            tvTxnTitle.setText(item.getTitle());
            tvTxnSubtitle.setText(item.getSubtitle());
            tvTxnAmount.setText(item.getFormattedAmount());

            // Nếu có icon, hiển thị icon tương ứng
            if (item.getIconResId() != 0) {
                ivTxnIcon.setImageResource(item.getIconResId());
            }

            // Phân biệt màu sắc dựa vào loại giao dịch: Thu nhập (Xanh) hay Chi tiêu (Đỏ)
            if (item.isIncome()) {
                // Tiền vào (Thu nhập): Dùng màu thành công (thường là màu xanh lá)
                tvTxnAmount.setTextColor(ContextCompat.getColor(context, R.color.status_success));
                ivTxnIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_success));
            } else {
                // Tiền ra (Chi tiêu): Dùng màu lỗi/cảnh báo (thường là màu đỏ)
                tvTxnAmount.setTextColor(ContextCompat.getColor(context, R.color.status_error));
                ivTxnIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_error));
            }
        }
    }
}