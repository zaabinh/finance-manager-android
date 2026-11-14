package com.example.fintrack.app.category.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.CategoryAdapter;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.category.model.CategoryItem;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ActivityCategoryListBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryListActivity extends FinanceActivity {
    private ActivityCategoryListBinding binding;
    private CategoryAdapter adapter;
    private TransactionType type = TransactionType.EXPENSE;
    @Override protected View contentView() { binding = ActivityCategoryListBinding.inflate(getLayoutInflater()); return binding.getRoot(); }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.CATEGORIES; }
    @Override protected void bind(Bundle saved) {
        if (saved != null) type = TransactionType.valueOf(saved.getString("categoryType","EXPENSE"));
        adapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override public void onCategoryClick(CategoryItem category) {
                startActivity(new Intent(CategoryListActivity.this,CategoryEditActivity.class).putExtra("CATEGORY_ID",category.getId()));
            }
            @Override public void onStatusToggle(CategoryItem category,boolean active) {
                if (!model.busy()) model.change(FinanceAction.categoryStatus(category.getId(),active),false);
            }
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this)); binding.rvCategories.setAdapter(adapter);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddCategory.setOnClickListener(v -> startActivity(
                new Intent(this,CategoryAddActivity.class).putExtra("category_type",type.name())));
        binding.tabSpend.setOnClickListener(v -> { type=TransactionType.EXPENSE; render(); });
        binding.tabIncome.setOnClickListener(v -> { type=TransactionType.INCOME; render(); });
        binding.etSearchCategory.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
    @Override protected void render() {
        if (adapter == null || book == null) return;
        String query=binding.etSearchCategory.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<CategoryItem> items=new ArrayList<>();
        for (Category c:book.categories()) {
            if(c.type==type && c.name.toLowerCase(Locale.ROOT).contains(query)) {
                CategoryItem item = new CategoryItem(c.id,c.name,c.type.name(),CategoryIcons.resource(c.iconKey),"",c.archivedAt==null);
                item.setColorHex(c.colorHex);
                items.add(item);
            }
        }
        adapter.setCategories(items);
        FinanceUi.visible(binding.layoutEmptyCategories, items.isEmpty()); FinanceUi.visible(binding.rvCategories, !items.isEmpty());
        binding.tabSpend.setBackgroundResource(type==TransactionType.EXPENSE?R.drawable.bg_pill_selected:android.R.color.transparent);
        binding.tabIncome.setBackgroundResource(type==TransactionType.INCOME?R.drawable.bg_pill_selected:android.R.color.transparent);
        binding.tabSpend.setTextColor(getColor(type==TransactionType.EXPENSE?R.color.white:R.color.text_secondary));
        binding.tabIncome.setTextColor(getColor(type==TransactionType.INCOME?R.color.white:R.color.text_secondary));
    }
    @Override protected void completed() { model.refresh(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("categoryType",type.name()); super.onSaveInstanceState(state);
    }
}
