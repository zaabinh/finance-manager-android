package com.example.fintrack.app.analytics.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.app.analytics.model.AnalyticsCategoryItem;
import com.example.fintrack.app.analytics.model.AnalyticsData;
import com.example.fintrack.app.analytics.provider.AnalyticsDataProvider;
import com.example.fintrack.app.budget.ui.BudgetListActivity;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (Views)
    private ImageView btnBack;
    private RelativeLayout btnSelectMonth;
    private TextView tvSelectedMonth;

    // Các thanh tiến trình phân đoạn ở đầu màn hình (story-style)
    private View progressSeg1;
    private View progressSeg2;
    private View progressSeg3;
    private View progressSeg4;
    private View progressSeg5;

    private TextView tvStoryStepCounter;

    // Các Layout chứa nội dung của từng Slide (1 đến 5)
    private LinearLayout layoutSlide1;
    private LinearLayout layoutSlide2;
    private LinearLayout layoutSlide3;
    private LinearLayout layoutSlide4;
    private LinearLayout layoutSlide5;

    // Giao diện Slide 1: Tổng quan thu chi
    private TextView tvSlide1NetDiff;
    private TextView tvSlide1Income;
    private TextView tvSlide1Expense;
    private TextView tvSlide1Note;

    // Giao diện Slide 2: Chi tiết theo danh mục
    private ImageView ivSlide2CategoryIcon;
    private TextView tvSlide2CategoryPercent;
    private TextView tvSlide2CategoryDetail;
    private ProgressBar pbSlide2Progress;
    private LinearLayout containerSlide2Pills;

    // Giao diện Slide 3: Tuần chi tiêu cao nhất
    private TextView tvSlide3PeakHeader;
    private TextView tvSlide3PeakSub;

    // Giao diện Slide 4: Cảnh báo ngân sách
    private TextView tvSlide4CategoryName;
    private TextView tvSlide4BudgetPercent;
    private TextView tvSlide4Spent;
    private TextView tvSlide4Remaining;
    private TextView tvSlide4AlertNote;

    // Giao diện Slide 5: Lời khuyên và gợi ý
    private TextView tvSlide5TipMessage;
    private LinearLayout btnSlide5Suggestion;
    private TextView tvSlide5SuggestionAction;

    // Các nút điều hướng qua lại giữa các slide
    private Button btnPrevStep;
    private Button btnNextStep;

    // Biến trạng thái
    private int currentStep = 1;
    private AnalyticsData analyticsData;
    private String currentMonth = "Tháng 10";
    private AnalyticsCategoryItem selectedCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        initViews();
        loadAnalyticsData(currentMonth);
        setupListeners();
        updateStepUI();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnSelectMonth = findViewById(R.id.btnSelectMonth);
        tvSelectedMonth = findViewById(R.id.tvSelectedMonth);

        progressSeg1 = findViewById(R.id.progressSeg1);
        progressSeg2 = findViewById(R.id.progressSeg2);
        progressSeg3 = findViewById(R.id.progressSeg3);
        progressSeg4 = findViewById(R.id.progressSeg4);
        progressSeg5 = findViewById(R.id.progressSeg5);

        tvStoryStepCounter = findViewById(R.id.tvStoryStepCounter);

        layoutSlide1 = findViewById(R.id.layoutSlide1);
        layoutSlide2 = findViewById(R.id.layoutSlide2);
        layoutSlide3 = findViewById(R.id.layoutSlide3);
        layoutSlide4 = findViewById(R.id.layoutSlide4);
        layoutSlide5 = findViewById(R.id.layoutSlide5);

        tvSlide1NetDiff = findViewById(R.id.tvSlide1NetDiff);
        tvSlide1Income = findViewById(R.id.tvSlide1Income);
        tvSlide1Expense = findViewById(R.id.tvSlide1Expense);
        tvSlide1Note = findViewById(R.id.tvSlide1Note);

        ivSlide2CategoryIcon = findViewById(R.id.ivSlide2CategoryIcon);
        tvSlide2CategoryPercent = findViewById(R.id.tvSlide2CategoryPercent);
        tvSlide2CategoryDetail = findViewById(R.id.tvSlide2CategoryDetail);
        pbSlide2Progress = findViewById(R.id.pbSlide2Progress);
        containerSlide2Pills = findViewById(R.id.containerSlide2Pills);

        tvSlide3PeakHeader = findViewById(R.id.tvSlide3PeakHeader);
        tvSlide3PeakSub = findViewById(R.id.tvSlide3PeakSub);

        tvSlide4CategoryName = findViewById(R.id.tvSlide4CategoryName);
        tvSlide4BudgetPercent = findViewById(R.id.tvSlide4BudgetPercent);
        tvSlide4Spent = findViewById(R.id.tvSlide4Spent);
        tvSlide4Remaining = findViewById(R.id.tvSlide4Remaining);
        tvSlide4AlertNote = findViewById(R.id.tvSlide4AlertNote);

        tvSlide5TipMessage = findViewById(R.id.tvSlide5TipMessage);
        btnSlide5Suggestion = findViewById(R.id.btnSlide5Suggestion);
        tvSlide5SuggestionAction = findViewById(R.id.tvSlide5SuggestionAction);

        btnPrevStep = findViewById(R.id.btnPrevStep);
        btnNextStep = findViewById(R.id.btnNextStep);
    }

    private void loadAnalyticsData(String month) {
        currentMonth = month;
        tvSelectedMonth.setText(month);
        analyticsData = AnalyticsDataProvider.getAnalyticsDataForMonth(month);

        tvSlide1NetDiff.setText(analyticsData.getNetDifference());
        tvSlide1Income.setText(analyticsData.getTotalIncome());
        tvSlide1Expense.setText(analyticsData.getTotalExpense());
        tvSlide1Note.setText(analyticsData.getOverviewNote());

        setupSlide2CategoryPills();

        tvSlide3PeakHeader.setText("Tuần " + analyticsData.getPeakWeekNumber() + " chi tiêu cao nhất");
        tvSlide3PeakSub.setText(analyticsData.getPeakWeekAmount() + " · chiếm " + analyticsData.getPeakWeekSharePercentage() + "% chi tiêu tháng.");

        tvSlide4CategoryName.setText("Khoản " + analyticsData.getBudgetCategoryName());
        tvSlide4BudgetPercent.setText(analyticsData.getBudgetUsedPercentage() + "% ngân sách");
        tvSlide4Spent.setText("Đã chi " + analyticsData.getBudgetSpentFormatted());
        tvSlide4Remaining.setText("Còn " + analyticsData.getBudgetRemainingFormatted());
        tvSlide4AlertNote.setText(analyticsData.getBudgetNote());

        tvSlide5TipMessage.setText(analyticsData.getTipMessage());
        tvSlide5SuggestionAction.setText("Gợi ý: " + analyticsData.getTipSuggestionAction());
    }

    private void setupSlide2CategoryPills() {
        containerSlide2Pills.removeAllViews();
        List<AnalyticsCategoryItem> categories = analyticsData.getCategoryBreakdown();
        if (categories == null || categories.isEmpty()) return;

        List<TextView> pillViews = new ArrayList<>();
        selectedCategory = categories.get(0);
        updateSlide2SelectedCategoryUI(selectedCategory);

        for (AnalyticsCategoryItem item : categories) {
            TextView pill = new TextView(this);
            pill.setText(item.getName());
            pill.setTextSize(13);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(32, 16, 32, 16);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 16, 0);
            pill.setLayoutParams(lp);

            if (item.getId().equals(selectedCategory.getId())) {
                pill.setBackgroundResource(R.drawable.bg_story_pill_selected);
                pill.setTextColor(getColor(R.color.text_primary));
            } else {
                pill.setBackgroundResource(R.drawable.bg_story_pill_unselected);
                pill.setTextColor(getColor(R.color.white));
            }

            pill.setOnClickListener(v -> {
                selectedCategory = item;
                updateSlide2SelectedCategoryUI(item);
                for (int i = 0; i < categories.size(); i++) {
                    TextView p = pillViews.get(i);
                    if (categories.get(i).getId().equals(item.getId())) {
                        p.setBackgroundResource(R.drawable.bg_story_pill_selected);
                        p.setTextColor(getColor(R.color.text_primary));
                    } else {
                        p.setBackgroundResource(R.drawable.bg_story_pill_unselected);
                        p.setTextColor(getColor(R.color.white));
                    }
                }
            });

            pillViews.add(pill);
            containerSlide2Pills.addView(pill);
        }
    }

    private void updateSlide2SelectedCategoryUI(AnalyticsCategoryItem item) {
        if (item == null) return;
        if (item.getIconResId() != 0) {
            ivSlide2CategoryIcon.setImageResource(item.getIconResId());
        }
        tvSlide2CategoryPercent.setText(item.getPercentage() + "%");
        tvSlide2CategoryDetail.setText(item.getName() + " · " + item.getAmountFormatted());
        pbSlide2Progress.setProgress(item.getPercentage());
    }

    private void updateStepUI() {
        tvStoryStepCounter.setText("PHÂN TÍCH " + currentStep + " / 5 · MINH HỌA");

        View[] segs = {progressSeg1, progressSeg2, progressSeg3, progressSeg4, progressSeg5};
        for (int i = 0; i < segs.length; i++) {
            if (i < currentStep) {
                segs[i].setBackgroundResource(R.drawable.bg_story_progress_active);
            } else {
                segs[i].setBackgroundResource(R.drawable.bg_story_progress_inactive);
            }
        }

        layoutSlide1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        layoutSlide2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        layoutSlide3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        layoutSlide4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        layoutSlide5.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);

        btnPrevStep.setEnabled(currentStep > 1);
        btnPrevStep.setAlpha(currentStep > 1 ? 1.0f : 0.4f);

        if (currentStep == 5) {
            btnNextStep.setText(R.string.btn_review);
        } else {
            btnNextStep.setText(R.string.btn_next);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSelectMonth.setOnClickListener(v -> {
            String[] months = {"Tháng 10", "Tháng 9", "Tháng 8", "Tháng 7"};
            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_report_month)
                    .setItems(months, (dialog, which) -> {
                        loadAnalyticsData(months[which]);
                        currentStep = 1;
                        updateStepUI();
                    })
                    .show();
        });

        btnPrevStep.setOnClickListener(v -> {
            if (currentStep > 1) {
                currentStep--;
                updateStepUI();
            }
        });

        btnNextStep.setOnClickListener(v -> {
            if (currentStep < 5) {
                currentStep++;
                updateStepUI();
            } else {
                currentStep = 1;
                updateStepUI();
            }
        });

        // Điều hướng trực tiếp đến Màn hình danh sách Ngân sách
        btnSlide5Suggestion.setOnClickListener(v -> {
            Intent intent = new Intent(this, BudgetListActivity.class);
            intent.putExtra("month", currentMonth);
            startActivity(intent);
        });
    }
}
