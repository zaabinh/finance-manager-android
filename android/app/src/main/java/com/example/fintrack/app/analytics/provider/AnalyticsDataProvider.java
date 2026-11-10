package com.example.fintrack.app.analytics.provider;

import com.example.fintrack.R;
import com.example.fintrack.app.analytics.model.AnalyticsCategoryItem;
import com.example.fintrack.app.analytics.model.AnalyticsData;

import java.util.ArrayList;
import java.util.List;

/**
 * Cung cấp dữ liệu phân tích tài chính (Analytics Data) minh họa nhất quán cho từng tháng (5 slides)
 */
public class AnalyticsDataProvider {

    public static AnalyticsData getAnalyticsDataForMonth(String monthName) {
        AnalyticsData data = new AnalyticsData();
        String month = monthName != null ? monthName : "Tháng 10";
        data.setMonthName(month);

        List<AnalyticsCategoryItem> categories = new ArrayList<>();

        if ("Tháng 9".equals(month)) {
            data.setNetDifference("+2.000.000 đ");
            data.setTotalIncome("14 triệu");
            data.setTotalExpense("12 triệu");
            data.setOverviewNote("Thu nhập nhỉnh hơn chi tiêu trong Tháng 9. Hãy duy trì thói quen tích lũy.");

            categories.add(new AnalyticsCategoryItem("c1", "Ăn uống", 35, "4.200.000 đ", R.drawable.ic_coffee));
            categories.add(new AnalyticsCategoryItem("c2", "Mua sắm", 30, "3.600.000 đ", R.drawable.ic_shopping_bag));
            categories.add(new AnalyticsCategoryItem("c3", "Nhà ở", 20, "2.400.000 đ", R.drawable.ic_home));
            categories.add(new AnalyticsCategoryItem("c4", "Di chuyển", 15, "1.800.000 đ", R.drawable.ic_car));

            data.setPeakWeekNumber(2);
            data.setPeakWeekAmount("3.800.000 đ");
            data.setPeakWeekSharePercentage(32);

            data.setBudgetCategoryName("Mua sắm");
            data.setBudgetUsedPercentage(92);
            data.setBudgetSpentFormatted("3.600.000 đ");
            data.setBudgetRemainingFormatted("300.000 đ");
            data.setBudgetNote("Bạn đã dùng 92% ngân sách Mua sắm trong Tháng 9. Hãy cẩn trọng các khoản chi phát sinh.");

            data.setTipMessage("Tháng tiếp theo, hãy đặt hạn mức Mua sắm hợp lý hơn để tránh thâm hụt.");
            data.setTipSuggestionAction("Xem Ngân sách Mua sắm");

        } else if ("Tháng 8".equals(month)) {
            data.setNetDifference("-1.000.000 đ");
            data.setTotalIncome("13 triệu");
            data.setTotalExpense("14 triệu");
            data.setOverviewNote("Chi tiêu vượt quá thu nhập trong Tháng 8. Bạn nên rà soát lại các khoản chi lớn.");

            categories.add(new AnalyticsCategoryItem("c1", "Mua sắm", 45, "6.300.000 đ", R.drawable.ic_shopping_bag));
            categories.add(new AnalyticsCategoryItem("c2", "Ăn uống", 25, "3.500.000 đ", R.drawable.ic_coffee));
            categories.add(new AnalyticsCategoryItem("c3", "Di chuyển", 15, "2.100.000 đ", R.drawable.ic_car));
            categories.add(new AnalyticsCategoryItem("c4", "Hóa đơn", 15, "2.100.000 đ", R.drawable.ic_receipt));

            data.setPeakWeekNumber(1);
            data.setPeakWeekAmount("4.500.000 đ");
            data.setPeakWeekSharePercentage(36);

            data.setBudgetCategoryName("Mua sắm");
            data.setBudgetUsedPercentage(115);
            data.setBudgetSpentFormatted("6.300.000 đ");
            data.setBudgetRemainingFormatted("Bội chi 800.000 đ");
            data.setBudgetNote("Khoản Mua sắm đã vượt 15% hạn mức ngân sách đặt ra.");

            data.setTipMessage("Hãy điều chỉnh cắt giảm bớt các khoản mua sắm không thiết yếu.");
            data.setTipSuggestionAction("Xem Ngân sách Mua sắm");

        } else if ("Tháng 7".equals(month)) {
            data.setNetDifference("+3.500.000 đ");
            data.setTotalIncome("15 triệu");
            data.setTotalExpense("11.5 triệu");
            data.setOverviewNote("Quản lý tài chính cân bằng tốt trong Tháng 7.");

            categories.add(new AnalyticsCategoryItem("c1", "Nhà ở", 35, "4.025.000 đ", R.drawable.ic_home));
            categories.add(new AnalyticsCategoryItem("c2", "Ăn uống", 30, "3.450.000 đ", R.drawable.ic_coffee));
            categories.add(new AnalyticsCategoryItem("c3", "Giải trí", 20, "2.300.000 đ", R.drawable.ic_ticket));
            categories.add(new AnalyticsCategoryItem("c4", "Hóa đơn", 15, "1.725.000 đ", R.drawable.ic_receipt));

            data.setPeakWeekNumber(4);
            data.setPeakWeekAmount("3.100.000 đ");
            data.setPeakWeekSharePercentage(27);

            data.setBudgetCategoryName("Hóa đơn");
            data.setBudgetUsedPercentage(75);
            data.setBudgetSpentFormatted("1.725.000 đ");
            data.setBudgetRemainingFormatted("575.000 đ");
            data.setBudgetNote("Tất cả ngân sách Tháng 7 đều được kiểm soát an toàn dưới hạn mức.");

            data.setTipMessage("Tiếp tục phát huy thói quen quản lý chi tiêu kỷ luật này.");
            data.setTipSuggestionAction("Xem Danh sách Ngân sách");

        } else {
            // Mặc định: Tháng 10
            data.setNetDifference("+5.000.000 đ");
            data.setTotalIncome("15 triệu");
            data.setTotalExpense("10 triệu");
            data.setOverviewNote("Bạn thu nhiều hơn chi trong kỳ. Đây chưa phải số tiền tiết kiệm thực tế.");

            categories.add(new AnalyticsCategoryItem("c1", "Nhà ở", 40, "4.000.000 đ", R.drawable.ic_home));
            categories.add(new AnalyticsCategoryItem("c2", "Ăn uống", 25, "2.500.000 đ", R.drawable.ic_coffee));
            categories.add(new AnalyticsCategoryItem("c3", "Mua sắm", 20, "2.000.000 đ", R.drawable.ic_shopping_bag));
            categories.add(new AnalyticsCategoryItem("c4", "Di chuyển", 15, "1.500.000 đ", R.drawable.ic_car));

            data.setPeakWeekNumber(3);
            data.setPeakWeekAmount("3.400.000 đ");
            data.setPeakWeekSharePercentage(34);

            data.setBudgetCategoryName("Ăn uống");
            data.setBudgetUsedPercentage(80);
            data.setBudgetSpentFormatted("3.200.000 đ");
            data.setBudgetRemainingFormatted("800.000 đ");
            data.setBudgetNote("Bạn đã sử dụng 80% ngân sách Ăn uống. Hãy kiểm tra các khoản chi sắp tới.");

            data.setTipMessage("Tháng tới, hãy bắt đầu bằng việc xem lại hạn mức Ăn uống và các khoản chi dự kiến.");
            data.setTipSuggestionAction("Xem Ngân sách Ăn uống");
        }

        data.setCategoryBreakdown(categories);
        return data;
    }
}
