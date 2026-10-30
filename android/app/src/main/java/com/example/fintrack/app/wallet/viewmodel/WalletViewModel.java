package com.example.fintrack.app.wallet.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.fintrack.app.wallet.model.TransactionItem;
import com.example.fintrack.app.wallet.model.WalletItem;
import com.example.fintrack.app.wallet.repository.WalletRepository;

import java.util.List;

// ViewModel chịu trách nhiệm quản lý dữ liệu liên quan đến ví,
// đóng vai trò cầu nối giữa Repository (dữ liệu) và View (Activity/Fragment)
public class WalletViewModel extends ViewModel {

    // Đối tượng Repository dùng để truy xuất và thao tác dữ liệu (Database / Mock data)
    private final WalletRepository repository;

    // Constructor: Khởi tạo ViewModel và lấy instance của Repository
    public WalletViewModel() {
        this.repository = WalletRepository.getInstance();
    }

    // Lấy danh sách tất cả các ví dưới dạng LiveData
    // Giúp UI (Activity) có thể tự động cập nhật (observe) mỗi khi dữ liệu thay đổi
    public LiveData<List<WalletItem>> getWallets() {
        return repository.getWallets();
    }

    // Tìm và trả về thông tin chi tiết của một ví cụ thể dựa vào ID
    public WalletItem getWalletById(String id) {
        return repository.getWalletById(id);
    }

    // Thêm mới một ví vào hệ thống thông qua Repository
    public void addWallet(WalletItem wallet) {
        repository.addWallet(wallet);
    }

    // Cập nhật thông tin của một ví đã tồn tại
    public void updateWallet(WalletItem wallet) {
        repository.updateWallet(wallet);
    }

    // Xoá một ví khỏi hệ thống dựa vào ID của ví đó
    public void deleteWallet(String walletId) {
        repository.deleteWallet(walletId);
    }

    // Lấy danh sách các giao dịch gần đây của một ví cụ thể dựa vào walletId
    public List<TransactionItem> getRecentTransactions(String walletId) {
        return repository.getRecentTransactionsForWallet(walletId);
    }
}