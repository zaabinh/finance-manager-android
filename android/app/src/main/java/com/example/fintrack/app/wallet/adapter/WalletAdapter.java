package com.example.fintrack.app.wallet.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.wallet.model.WalletItem;

import java.util.ArrayList;
import java.util.List;

// Adapter chịu trách nhiệm hiển thị danh sách các ví (WalletItem) lên RecyclerView
public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.ViewHolder> {

    // Khai báo Interface dùng để lắng nghe sự kiện khi người dùng bấm vào một ví trong danh sách
    public interface OnWalletClickListener {
        void onWalletClick(WalletItem wallet);
    }

    private final List<WalletItem> walletList = new ArrayList<>(); // Danh sách lưu trữ các ví
    private final OnWalletClickListener listener;                 // Biến lưu sự kiện click

    // Constructor nhận vào listener để xử lý sự kiện khi người dùng bấm vào item ví
    public WalletAdapter(OnWalletClickListener listener) {
        this.listener = listener;
    }

    // Cập nhật danh sách ví mới cho Adapter và yêu cầu vẽ lại giao diện
    public void setWallets(List<WalletItem> wallets) {
        walletList.clear();
        if (wallets != null) {
            walletList.addAll(wallets);
        }
        notifyDataSetChanged(); // Cập nhật lại RecyclerView
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Khởi tạo giao diện cho từng dòng (item) từ file XML `item_wallet`
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallet, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Lấy thông tin ví tại vị trí `position` và truyền vào ViewHolder kèm theo listener
        WalletItem wallet = walletList.get(position);
        holder.bind(wallet, listener);
    }

    @Override
    public int getItemCount() {
        // Trả về tổng số lượng ví có trong danh sách
        return walletList.size();
    }

    // Lớp ViewHolder giúp lưu giữ tham chiếu đến các View con bên trong item để tối ưu hiệu năng
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvWalletName;      // Tên ví
        private final TextView tvWalletSubtitle;  // Mô tả phụ (Ghi chú hoặc Loại ví)
        private final TextView tvWalletBalance;   // Số dư trong ví

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các thành phần giao diện từ file XML
            tvWalletName = itemView.findViewById(R.id.tvWalletName);
            tvWalletSubtitle = itemView.findViewById(R.id.tvWalletSubtitle);
            tvWalletBalance = itemView.findViewById(R.id.tvWalletBalance);
        }

        // Đổ dữ liệu từ đối tượng WalletItem vào các View và gán sự kiện bấm
        public void bind(WalletItem wallet, OnWalletClickListener listener) {
            tvWalletName.setText(wallet.getName());

            // Hiển thị ghi chú (note) nếu có, nếu không thì hiển thị loại ví (type) làm phụ đề
            tvWalletSubtitle.setText(wallet.getNote() != null && !wallet.getNote().isEmpty() ? wallet.getNote() : wallet.getType());

            // Hiển thị số dư đã được định dạng chuẩn tiền tệ
            tvWalletBalance.setText(wallet.getFormattedBalance());

            // Xử lý sự kiện khi người dùng bấm vào toàn bộ khung của một ví
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onWalletClick(wallet); // Gọi callback trả về ví tương ứng
                }
            });
        }
    }
}