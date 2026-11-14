package com.example.fintrack.app.analytics.model;

import java.io.Serializable;
import java.util.List;

// Lớp mô hình dữ liệu (Model) chứa toàn bộ thông tin phân tích tài chính cho một tháng
// Implement Serializable để có thể truyền đối tượng này qua lại giữa các Activity/Fragment nếu cần
public class AnalyticsData implements Serializable {

    // --- Dữ liệu cho Slide 1: Tổng quan thu chi ---
    private String monthName; // Tên tháng (Ví dụ: "Tháng 10")
    private String netDifference; // Số dư chênh lệch (Ví dụ: "+5.000.000 đ")
    private String totalIncome; // Tổng thu nhập (Ví dụ: "15 triệu")
    private String totalExpense; // Tổng chi tiêu (Ví dụ: "10 triệu")
    private String overviewNote; // Ghi chú tổng quan

    // --- Dữ liệu cho Slide 2: Chi tiết danh mục chi tiêu ---
    private List<AnalyticsCategoryItem> categoryBreakdown; // Danh sách các khoản chi theo danh mục

    // --- Dữ liệu cho Slide 3: Tuần chi tiêu cao nhất ---
    private int peakWeekNumber; // Số thứ tự tuần cao nhất (Ví dụ: 3)
    private String peakWeekAmount; // Tổng tiền chi trong tuần đó (Ví dụ: "3.400.000 đ")
    private int peakWeekSharePercentage; // Tỷ lệ phần trăm chiếm trong tổng chi tháng (Ví dụ: 34)

    // --- Dữ liệu cho Slide 4: Cảnh báo ngân sách ---
    private String budgetCategoryName; // Tên danh mục cần cảnh báo (Ví dụ: "Ăn uống")
    private int budgetUsedPercentage; // Tỷ lệ ngân sách đã dùng (Ví dụ: 80)
    private String budgetSpentFormatted; // Số tiền đã chi (Ví dụ: "3.200.000 đ")
    private String budgetRemainingFormatted; // Số tiền còn lại (Ví dụ: "800.000 đ")
    private String budgetNote; // Ghi chú/Cảnh báo ngân sách

    // --- Dữ liệu cho Slide 5: Lời khuyên tài chính ---
    private String tipMessage; // Nội dung lời khuyên
    private String tipSuggestionAction; // Tên hành động gợi ý kèm theo

    // Hàm khởi tạo mặc định (Constructor không tham số)
    public AnalyticsData() {}

    // --- Các hàm Getter và Setter để truy xuất và cập nhật dữ liệu ---

    public String getMonthName() {
        return monthName;
    }

    public void setMonthName(String monthName) {
        this.monthName = monthName;
    }

    public String getNetDifference() {
        return netDifference;
    }

    public void setNetDifference(String netDifference) {
        this.netDifference = netDifference;
    }

    public String getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(String totalIncome) {
        this.totalIncome = totalIncome;
    }

    public String getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(String totalExpense) {
        this.totalExpense = totalExpense;
    }

    public String getOverviewNote() {
        return overviewNote;
    }

    public void setOverviewNote(String overviewNote) {
        this.overviewNote = overviewNote;
    }

    public List<AnalyticsCategoryItem> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    public void setCategoryBreakdown(List<AnalyticsCategoryItem> categoryBreakdown) {
        this.categoryBreakdown = categoryBreakdown;
    }

    public int getPeakWeekNumber() {
        return peakWeekNumber;
    }

    public void setPeakWeekNumber(int peakWeekNumber) {
        this.peakWeekNumber = peakWeekNumber;
    }

    public String getPeakWeekAmount() {
        return peakWeekAmount;
    }

    public void setPeakWeekAmount(String peakWeekAmount) {
        this.peakWeekAmount = peakWeekAmount;
    }

    public int getPeakWeekSharePercentage() {
        return peakWeekSharePercentage;
    }

    public void setPeakWeekSharePercentage(int peakWeekSharePercentage) {
        this.peakWeekSharePercentage = peakWeekSharePercentage;
    }

    public String getBudgetCategoryName() {
        return budgetCategoryName;
    }

    public void setBudgetCategoryName(String budgetCategoryName) {
        this.budgetCategoryName = budgetCategoryName;
    }

    public int getBudgetUsedPercentage() {
        return budgetUsedPercentage;
    }

    public void setBudgetUsedPercentage(int budgetUsedPercentage) {
        this.budgetUsedPercentage = budgetUsedPercentage;
    }

    public String getBudgetSpentFormatted() {
        return budgetSpentFormatted;
    }

    public void setBudgetSpentFormatted(String budgetSpentFormatted) {
        this.budgetSpentFormatted = budgetSpentFormatted;
    }

    public String getBudgetRemainingFormatted() {
        return budgetRemainingFormatted;
    }

    public void setBudgetRemainingFormatted(String budgetRemainingFormatted) {
        this.budgetRemainingFormatted = budgetRemainingFormatted;
    }

    public String getBudgetNote() {
        return budgetNote;
    }

    public void setBudgetNote(String budgetNote) {
        this.budgetNote = budgetNote;
    }

    public String getTipMessage() {
        return tipMessage;
    }

    public void setTipMessage(String tipMessage) {
        this.tipMessage = tipMessage;
    }

    public String getTipSuggestionAction() {
        return tipSuggestionAction;
    }

    public void setTipSuggestionAction(String tipSuggestionAction) {
        this.tipSuggestionAction = tipSuggestionAction;
    }
}
