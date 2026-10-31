package com.example.fintrack.app.analytics.provider;

import com.example.fintrack.R;
import com.example.fintrack.app.analytics.model.AnalyticsCategoryItem;
import com.example.fintrack.app.analytics.model.AnalyticsData;

import java.util.ArrayList;
import java.util.List;

// Lớp đóng vai trò cung cấp dữ liệu giả lập (Mock Data) cho phần phân tích tài chính (Analytics)
public class AnalyticsDataProvider {

    // Hàm nhận vào tên tháng và trả về một đối tượng chứa toàn bộ dữ liệu phân tích của tháng đó
    public static AnalyticsData getAnalyticsDataForMonth(String monthName) {
        AnalyticsData data = new AnalyticsData();

        // Thiết lập tên tháng (nếu null thì mặc định là "Tháng 10")
        data.setMonthName(monthName != null ? monthName : "Tháng 10");

        // Dữ liệu cho Slide 1: Tổng quan thu chi
        data.setNetDifference("+5.000.000 đ");
        data.setTotalIncome("15 triệu");
        data.setTotalExpense("10 triệu");
        data.setOverviewNote("Bạn thu nhiều hơn chi trong kỳ. Đây chưa phải số tiền tiết kiệm thực tế.");

        // Dữ liệu cho Slide 2: Danh sách các danh mục chi tiêu (Category Breakdown)
        List<AnalyticsCategoryItem> categories = new ArrayList<>();
        // Mỗi mục gồm: ID, Tên danh mục, Tỷ lệ phần trăm, Số tiền định dạng, Icon tương ứng
        categories.add(new AnalyticsCategoryItem("c1", "Nhà ở", 40, "4.000.000 đ", R.drawable.ic_home));
        categories.add(new AnalyticsCategoryItem("c2", "Ăn uống", 25, "2.500.000 đ", R.drawable.ic_coffee));
        categories.add(new AnalyticsCategoryItem("c3", "Mua sắm", 20, "2.000.000 đ", R.drawable.ic_shopping_bag));
        categories.add(new AnalyticsCategoryItem("c4", "Di chuyển", 15, "1.500.000 đ", R.drawable.ic_car));
        data.setCategoryBreakdown(categories); // Gắn danh sách danh mục vào đối tượng dữ liệu chung

        // Dữ liệu cho Slide 3: Tuần chi tiêu cao nhất trong tháng
        data.setPeakWeekNumber(3); // Tuần thứ 3
        data.setPeakWeekAmount("3.400.000 đ"); // Số tiền chi trong tuần đó
        data.setPeakWeekSharePercentage(34); // Chiếm 34% tổng chi tiêu tháng

        // Dữ liệu cho Slide 4: Cảnh báo ngân sách của một danh mục cụ thể
        data.setBudgetCategoryName("Ăn uống");
        data.setBudgetUsedPercentage(80); // Đã dùng 80% ngân sách
        data.setBudgetSpentFormatted("3.200.000 đ"); // Số tiền đã chi
        data.setBudgetRemainingFormatted("800.000 đ"); // Số tiền còn lại
        data.setBudgetNote("Bạn đã sử dụng 80% ngân sách Ăn uống. Hãy kiểm tra các khoản chi sắp tới.");

        // Dữ liệu cho Slide 5: Lời khuyên tài chính và hành động gợi ý
        data.setTipMessage("Tháng tới, hãy bắt đầu bằng việc xem lại hạn mức Ăn uống và các khoản chi dự kiến.");
        data.setTipSuggestionAction("Xem Khoản Ăn uống");

        // Trả về đối tượng dữ liệu hoàn chỉnh để hiển thị lên giao diện Activity
        return data;
    }
}