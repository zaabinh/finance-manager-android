package com.example.fintrack.app.analytics.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivityAnalyticsBinding;
import com.example.fintrack.app.analytics.model.AnalyticsCategoryItem;
import com.example.fintrack.app.analytics.model.AnalyticsData;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.analytics.provider.AnalyticsProvider;
import com.example.fintrack.app.budget.ui.BudgetListActivity;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsActivity extends AppCompatActivity {

    private ActivityAnalyticsBinding binding;

    // Biến trạng thái
    private int currentStep = 1;
    private AnalyticsData analyticsData;
    private String currentMonth;
    private AnalyticsCategoryItem selectedCategory;
    private AnalyticsProvider provider;

    private static final String STATE_STEP = "step";
    private static final String STATE_MONTH = "month";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAnalyticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        provider = FintrackApplication.container(this).analyticsProvider();
        if (provider == null) {
            // Not connected (real flavors): no charts or figures, only an explicit state under the header.
            binding.scrollAnalytics.setVisibility(View.GONE);
            binding.layoutNotConnected.setVisibility(View.VISIBLE);
            return;
        }
        currentMonth = getResources().getStringArray(R.array.analytics_demo_months)[0];
        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(STATE_STEP, 1);
            currentMonth = savedInstanceState.getString(STATE_MONTH, currentMonth);
        }
        loadAnalyticsData(currentMonth);
        setupListeners();
        updateStepUI();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, currentStep);
        outState.putString(STATE_MONTH, currentMonth);
    }

    private void loadAnalyticsData(String month) {
        currentMonth = month;
        binding.tvSelectedMonth.setText(month);
        analyticsData = provider.getAnalyticsDataForMonth(month);

        binding.tvSlide1NetDiff.setText(analyticsData.getNetDifference());
        binding.tvSlide1Income.setText(analyticsData.getTotalIncome());
        binding.tvSlide1Expense.setText(analyticsData.getTotalExpense());
        binding.tvSlide1Note.setText(analyticsData.getOverviewNote());

        setupSlide2CategoryPills();

        binding.tvSlide3PeakHeader.setText(getString(R.string.analytics_peak_week_header, analyticsData.getPeakWeekNumber()));
        binding.tvSlide3PeakSub.setText(getString(R.string.analytics_peak_week_sub, analyticsData.getPeakWeekAmount(), analyticsData.getPeakWeekSharePercentage()));

        binding.tvSlide4CategoryName.setText(getString(R.string.analytics_budget_category, analyticsData.getBudgetCategoryName()));
        binding.tvSlide4BudgetPercent.setText(getString(R.string.analytics_budget_percent, analyticsData.getBudgetUsedPercentage()));
        binding.tvSlide4Spent.setText(getString(R.string.analytics_budget_spent, analyticsData.getBudgetSpentFormatted()));
        binding.tvSlide4Remaining.setText(getString(R.string.analytics_budget_remaining, analyticsData.getBudgetRemainingFormatted()));
        binding.tvSlide4AlertNote.setText(analyticsData.getBudgetNote());

        binding.tvSlide5TipMessage.setText(analyticsData.getTipMessage());
        binding.tvSlide5SuggestionAction.setText(getString(R.string.analytics_tip_suggestion, analyticsData.getTipSuggestionAction()));
    }

    private void setupSlide2CategoryPills() {
        binding.containerSlide2Pills.removeAllViews();
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
            binding.containerSlide2Pills.addView(pill);
        }
    }

    private void updateSlide2SelectedCategoryUI(AnalyticsCategoryItem item) {
        if (item == null) return;
        if (item.getIconResId() != 0) {
            binding.ivSlide2CategoryIcon.setImageResource(item.getIconResId());
        }
        binding.tvSlide2CategoryPercent.setText(item.getPercentage() + "%");
        binding.tvSlide2CategoryDetail.setText(item.getName() + " · " + item.getAmountFormatted());
        binding.pbSlide2Progress.setProgress(item.getPercentage());
    }

    private void updateStepUI() {
        binding.tvStoryStepCounter.setText(getString(R.string.analytics_step_counter, currentStep));

        View[] segs = {binding.progressSeg1, binding.progressSeg2, binding.progressSeg3, binding.progressSeg4, binding.progressSeg5};
        for (int i = 0; i < segs.length; i++) {
            if (i < currentStep) {
                segs[i].setBackgroundResource(R.drawable.bg_story_progress_active);
            } else {
                segs[i].setBackgroundResource(R.drawable.bg_story_progress_inactive);
            }
        }

        binding.layoutSlide1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutSlide2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutSlide3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        binding.layoutSlide4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        binding.layoutSlide5.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);

        binding.btnPrevStep.setEnabled(currentStep > 1);
        binding.btnPrevStep.setAlpha(currentStep > 1 ? 1.0f : 0.4f);

        if (currentStep == 5) {
            binding.btnNextStep.setText(R.string.btn_review);
        } else {
            binding.btnNextStep.setText(R.string.btn_next);
        }
    }

    private void setupListeners() {
        binding.btnSelectMonth.setOnClickListener(v -> {
            String[] months = getResources().getStringArray(R.array.analytics_demo_months);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.select_report_month)
                    .setItems(months, (dialog, which) -> {
                        loadAnalyticsData(months[which]);
                        currentStep = 1;
                        updateStepUI();
                    })
                    .show();
        });

        binding.btnPrevStep.setOnClickListener(v -> {
            if (currentStep > 1) {
                currentStep--;
                updateStepUI();
            }
        });

        binding.btnNextStep.setOnClickListener(v -> {
            if (currentStep < 5) {
                currentStep++;
                updateStepUI();
            } else {
                currentStep = 1;
                updateStepUI();
            }
        });

        // Điều hướng trực tiếp đến Màn hình danh sách Ngân sách
        binding.btnSlide5Suggestion.setOnClickListener(v -> {
            Intent intent = new Intent(this, BudgetListActivity.class);
            startActivity(intent);
        });
    }
}
