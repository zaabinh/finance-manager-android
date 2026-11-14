package com.example.fintrack.app.category.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.CategoryAdapter;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.category.model.CategoryItem;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryListActivity extends FinanceActivity {
    private CategoryAdapter adapter;
    private EditText search;
    private TransactionType type = TransactionType.EXPENSE;
    @Override protected int layout() { return R.layout.activity_category_list; }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.CATEGORIES; }
    @Override protected void bind(Bundle saved) {
        if (saved != null) type = TransactionType.valueOf(saved.getString("categoryType","EXPENSE"));
        search = findViewById(R.id.etSearchCategory);
        RecyclerView list = findViewById(R.id.rvCategories);
        adapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override public void onCategoryClick(CategoryItem category) {
                startActivity(new Intent(CategoryListActivity.this,CategoryEditActivity.class).putExtra("CATEGORY_ID",category.getId()));
            }
            @Override public void onStatusToggle(CategoryItem category,boolean active) {
                if (!model.busy()) model.change(FinanceAction.categoryStatus(category.getId(),active),false);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddCategory).setOnClickListener(v -> startActivity(
                new Intent(this,CategoryAddActivity.class).putExtra("category_type",type.name())));
        findViewById(R.id.tabSpend).setOnClickListener(v -> { type=TransactionType.EXPENSE; render(); });
        findViewById(R.id.tabIncome).setOnClickListener(v -> { type=TransactionType.INCOME; render(); });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
    @Override protected void render() {
        if (adapter == null || book == null) return;
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<CategoryItem> items=new ArrayList<>();
        for (Category c:book.categories()) {
            if(c.type==type && c.name.toLowerCase(Locale.ROOT).contains(query)) {
                CategoryItem item = new CategoryItem(c.id,c.name,c.type.name(),CategoryIcons.resource(c.iconKey),"",c.archivedAt==null);
                item.setColorHex(c.colorHex);
                items.add(item);
            }
        }
        adapter.setCategories(items);
        visible(R.id.layoutEmptyCategories,items.isEmpty()); visible(R.id.rvCategories,!items.isEmpty());
        findViewById(R.id.tabSpend).setBackgroundResource(type==TransactionType.EXPENSE?R.drawable.bg_pill_selected:android.R.color.transparent);
        findViewById(R.id.tabIncome).setBackgroundResource(type==TransactionType.INCOME?R.drawable.bg_pill_selected:android.R.color.transparent);
        ((android.widget.TextView)findViewById(R.id.tabSpend)).setTextColor(getColor(type==TransactionType.EXPENSE?R.color.white:R.color.text_secondary));
        ((android.widget.TextView)findViewById(R.id.tabIncome)).setTextColor(getColor(type==TransactionType.INCOME?R.color.white:R.color.text_secondary));
    }
    @Override protected void completed() { model.refresh(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("categoryType",type.name()); super.onSaveInstanceState(state);
    }
}
