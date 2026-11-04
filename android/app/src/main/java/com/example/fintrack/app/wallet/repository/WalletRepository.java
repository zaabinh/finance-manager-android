package com.example.fintrack.app.wallet.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.R;
import com.example.fintrack.app.wallet.model.TransactionItem;
import com.example.fintrack.app.wallet.model.WalletItem;

import java.util.ArrayList;
import java.util.List;

// Lớp Repository chịu trách nhiệm quản lý nguồn dữ liệu (Data Source) của Ví
// Sử dụng mô hình Singleton để đảm bảo toàn ứng dụng chỉ dùng chung một đối tượng Repository duy nhất
public class WalletRepository {
    private static WalletRepository instance;

    // LiveData chứa danh sách các ví, giúp tự động thông báo (notify) cho UI khi dữ liệu thay đổi
    private final MutableLiveData<List<WalletItem>> walletsLiveData = new MutableLiveData<>();

    // Danh sách lưu trữ thực tế trong bộ nhớ (In-memory list)
    private final List<WalletItem> walletList = new ArrayList<>();

    // Constructor ở dạng private để ngăn chặn việc khởi tạo trực tiếp bằng từ khóa `new` từ bên ngoài
    private WalletRepository() {
        initDefaultData(); // Khởi tạo dữ liệu mẫu mặc định khi tạo Repository
    }

    // Phương thức tĩnh (Static) để truy xuất thể hiện (instance) duy nhất của Repository
    public static synchronized WalletRepository getInstance() {
        if (instance == null) {
            instance = new WalletRepository();
        }
        return instance;
    }

    // Khởi tạo một số dữ liệu ví mẫu ban đầu (dùng khi mới mở app hoặc danh sách đang trống)
    private void initDefaultData() {
        if (walletList.isEmpty()) {
            walletList.add(new WalletItem("w1", "Techcombank", "Ngân hàng", "Techcombank", 15736000, "VND", "Tài khoản thanh toán", true));
            walletList.add(new WalletItem("w2", "Tiền mặt", "Tiền mặt", "Mặt", 1100000, "VND", "Ví cá nhân", true));
            walletList.add(new WalletItem("w3", "Momo", "Ví điện tử", "Momo", 2500000, "VND", "Thanh toán online", true));

            // Đẩy danh sách mẫu vào LiveData để các Activity/Fragment đăng ký lắng nghe (observe) có thể nhận được
            walletsLiveData.setValue(new ArrayList<>(walletList));
        }
    }

    // Trả về danh sách ví dưới dạng LiveData để UI theo dõi sự thay đổi
    public LiveData<List<WalletItem>> getWallets() {
        return walletsLiveData;
    }

    // Tìm và trả về thông tin chi tiết của một ví dựa theo ID
    public WalletItem getWalletById(String id) {
        for (WalletItem w : walletList) {
            if (w.getId().equals(id)) {
                return w;
            }
        }
        // Nếu không tìm thấy, trả về ví đầu tiên trong danh sách (nếu có), ngược lại trả về null
        return !walletList.isEmpty() ? walletList.get(0) : null;
    }

    // Thêm một ví mới vào danh sách và cập nhật lại LiveData
    public void addWallet(WalletItem wallet) {
        walletList.add(wallet);
        walletsLiveData.setValue(new ArrayList<>(walletList)); // Bắn dữ liệu mới đi để UI cập nhật
    }

    // Cập nhật thông tin của một ví đã tồn tại trong danh sách
    public void updateWallet(WalletItem updatedWallet) {
        for (int i = 0; i < walletList.size(); i++) {
            // Tìm ví có ID trùng khớp để thay thế bằng thông tin mới
            if (walletList.get(i).getId().equals(updatedWallet.getId())) {
                walletList.set(i, updatedWallet);
                break;
            }
        }
        walletsLiveData.setValue(new ArrayList<>(walletList)); // Cập nhật lại LiveData
    }

    // Xoá một ví khỏi danh sách dựa vào ID của nó
    public void deleteWallet(String walletId) {
        // Xoá phần tử khỏi danh sách nếu ID khớp
        walletList.removeIf(w -> w.getId().equals(walletId));
        walletsLiveData.setValue(new ArrayList<>(walletList)); // Cập nhật lại LiveData
    }

    // Lấy danh sách giao dịch gần đây của một ví cụ thể (đang trả về danh sách giao dịch mẫu/mock data)
    public List<TransactionItem> getRecentTransactionsForWallet(String walletId) {
        List<TransactionItem> list = new ArrayList<>();
        list.add(new TransactionItem("t1", "Highlands Coffee", "Techcombank • Hôm qua, 15:45", 95000, false, R.drawable.ic_coffee));
        list.add(new TransactionItem("t2", "Chuyển tiền lương", "Techcombank • 25/10/2026", 25000000, true, R.drawable.ic_briefcase));
        list.add(new TransactionItem("t3", "Siêu thị WinMart", "Techcombank • 24/10/2026", 450000, false, R.drawable.ic_shopping_bag));
        return list;
    }
}