package com.example.fintrack.app.category.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.CategoryAdapter;
import com.example.fintrack.app.category.model.CategoryItem;
import com.example.fintrack.app.category.viewmodel.CategoryViewModel;

import java.util.ArrayList;
import java.util.List;

public class CategoryListActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnAddCategory;
    private TextView tabSpend;
    private TextView tabIncome;
    private EditText etSearchCategory;
    private RecyclerView rvCategories;

    private CategoryViewModel categoryViewModel;
    private CategoryAdapter categoryAdapter;
    private String currentTab = "EXPENSE"; // EXPENSE or INCOME
    private List<CategoryItem> fullList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_list);

        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        initViews();
        setupRecyclerView();
        setupObservers();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnAddCategory = findViewById(R.id.btnAddCategory);
        tabSpend = findViewById(R.id.tabSpend);
        tabIncome = findViewById(R.id.tabIncome);
        etSearchCategory = findViewById(R.id.etSearchCategory);
        rvCategories = findViewById(R.id.rvCategories);
    }

    private void setupRecyclerView() {
        categoryAdapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onCategoryClick(CategoryItem category) {
                Intent intent = new Intent(CategoryListActivity.this, CategoryEditActivity.class);
                intent.putExtra("CATEGORY_ID", category.getId());
                startActivity(intent);
            }

            @Override
            public void onStatusToggle(CategoryItem category, boolean isChecked) {
                categoryViewModel.toggleStatus(category.getId(), isChecked);
            }
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(categoryAdapter);
    }

    private void setupObservers() {
        categoryViewModel.getCategories().observe(this, categories -> {
            if (categories != null) {
                fullList = categories;
                filterCategories();
            }
        });
    }

    private void filterCategories() {
        String query = etSearchCategory.getText().toString().trim().toLowerCase();
        List<CategoryItem> filtered = new ArrayList<>();
        for (CategoryItem c : fullList) {
            if (currentTab.equalsIgnoreCase(c.getType())) {
                if (query.isEmpty() || c.getName().toLowerCase().contains(query)) {
                    filtered.add(c);
                }
            }
        }
        categoryAdapter.setCategories(filtered);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnAddCategory.setOnClickListener(v -> {
            Intent intent = new Intent(CategoryListActivity.this, CategoryAddActivity.class);
            startActivity(intent);
        });

        tabSpend.setOnClickListener(v -> {
            currentTab = "EXPENSE";
            tabSpend.setBackgroundResource(R.drawable.bg_pill_selected);
            tabSpend.setTextColor(getColor(R.color.white));
            tabIncome.setBackgroundResource(android.R.color.transparent);
            tabIncome.setTextColor(getColor(R.color.text_secondary));
            filterCategories();
        });

        tabIncome.setOnClickListener(v -> {
            currentTab = "INCOME";
            tabIncome.setBackgroundResource(R.drawable.bg_pill_selected);
            tabIncome.setTextColor(getColor(R.color.white));
            tabSpend.setBackgroundResource(android.R.color.transparent);
            tabSpend.setTextColor(getColor(R.color.text_secondary));
            filterCategories();
        });

        etSearchCategory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCategories();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
}
