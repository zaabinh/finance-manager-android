package com.example.fintrack.app.category.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.IconPickerAdapter;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.switchmaterial.SwitchMaterial;

/** Uses the same repository, session checks and command lifecycle as finance pickers. */
abstract class CategoryFormActivity extends FinanceActivity {
    private EditText name, order;
    private SwitchMaterial status;
    private Button save;
    private RecyclerView icons;
    private CategoryAppearance appearance;
    private String id, iconKey, manualColor, colorHex;
    private int sortOrder;
    private FinanceBook.Type type = FinanceBook.Type.EXPENSE;
    private boolean initialized, restoredForm, bindingStatus;
    protected abstract boolean editing();
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.CATEGORIES; }

    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra("CATEGORY_ID");
        if (editing() && (id == null || id.isEmpty())) { finish(); return; }
        if ("INCOME".equals(getIntent().getStringExtra("category_type"))) type = FinanceBook.Type.INCOME;
        if (saved != null) {
            type = FinanceBook.Type.valueOf(saved.getString("categoryType", "EXPENSE"));
            iconKey = saved.getString("iconKey"); manualColor = saved.getString("manualColor");
            restoredForm = saved.getBoolean("categoryLoaded");
        }
        name = findViewById(R.id.etCategoryName);
        order = findViewById(R.id.etCategorySortOrder);
        status = findViewById(R.id.switchStatus);
        icons = findViewById(R.id.rvIconPicker);
        save = findViewById(editing() ? R.id.btnSaveChanges : R.id.btnCreateCategory);
        toolbar(R.id.toolbarForm);
        findViewById(R.id.btnTypeTransfer).setVisibility(View.GONE);
        if (!editing()) {
            findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
            // Lifecycle is changed explicitly after creation, not through unsupported POST fields.
            ((View)status.getParent()).setVisibility(View.GONE);
        } else {
            findViewById(R.id.btnDeleteCategory).setOnClickListener(v ->
                    confirmDelete(() -> model.change(FinanceAction.categoryArchive(id), true)));
        }
        status.setOnCheckedChangeListener((button, active) -> {
            if (!bindingStatus && initialized && !model.busy())
                model.change(FinanceAction.categoryStatus(id, active), false);
        });
        findViewById(R.id.btnTypeExpense).setOnClickListener(v -> { type = FinanceBook.Type.EXPENSE; showType(); appearance.setType(type); });
        findViewById(R.id.btnTypeIncome).setOnClickListener(v -> { type = FinanceBook.Type.INCOME; showType(); appearance.setType(type); });
        save.setOnClickListener(v -> {
            if (!initialized || model.busy()) return;
            String value = name.getText().toString().trim();
            if (value.isEmpty() || value.codePointCount(0,value.length()) > 100) {
                name.setError(getString(R.string.category_name_invalid)); return;
            }
            try { sortOrder = order.getText().toString().trim().isEmpty() ? 0 : Integer.parseInt(order.getText().toString().trim()); }
            catch (NumberFormatException exception) { order.setError(getString(R.string.category_sort_invalid)); return; }
            if (sortOrder < 0) { order.setError(getString(R.string.category_sort_invalid)); return; }
            colorHex = appearance.colorHex(); iconKey = appearance.iconKey();
            model.change(editing() ? FinanceAction.categoryUpdate(id,value,type,iconKey,colorHex,sortOrder)
                    : FinanceAction.category(value,type,iconKey,colorHex,sortOrder),true);
        });
        showType(); showIcons(manualColor);
        save.setEnabled(false);
    }

    private void showType() {
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.btnTypeExpense)).setChecked(type == FinanceBook.Type.EXPENSE);
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.btnTypeIncome)).setChecked(type == FinanceBook.Type.INCOME);
    }
    private void showIcons(String manual) {
        appearance = new CategoryAppearance(this, icons, findViewById(R.id.rvColorPicker), type, iconKey, manual);
    }
    @Override protected void render() {
        if (name == null) return;
        FinanceBook.Category current = editing() ? book.category(id) : null;
        if (editing() && current == null) { save.setEnabled(false); return; }
        if (!initialized) {
            if (current != null) {
                sortOrder = current.sortOrder;
                if (!restoredForm) { name.setText(current.name); type = current.type; iconKey = current.iconKey; manualColor = current.colorHex; }
            }
            if (!restoredForm) order.setText(String.valueOf(sortOrder));
            initialized = true; showType(); if (!restoredForm) showIcons(manualColor);
        }
        boolean active = current == null || current.archivedAt == null;
        bindingStatus = true;
        status.setChecked(active);
        bindingStatus = false;
        text(R.id.tvStatusLabel,getString(active ? R.string.status_active : R.string.status_disabled));
        name.setEnabled(active);
        order.setEnabled(active);
        findViewById(R.id.btnTypeExpense).setEnabled(active); findViewById(R.id.btnTypeIncome).setEnabled(active);
        icons.setVisibility(active ? View.VISIBLE : View.GONE);
        findViewById(R.id.rvColorPicker).setVisibility(active ? View.VISIBLE : View.GONE);
        save.setEnabled(active && !model.busy());
        if(editing()) findViewById(R.id.btnDeleteCategory).setEnabled(active && !model.busy());
    }
    @Override protected void loading(boolean busy) {
        if (save != null) FinanceUi.progress(save, busy);
        if (save != null) save.setEnabled(!busy && initialized && (!editing() || book != null && book.category(id) != null && book.category(id).archivedAt == null));
        if (status != null) status.setEnabled(!busy && initialized);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("categoryType",type.name()); state.putString("iconKey",appearance.iconKey()); state.putString("manualColor",appearance.manualColor());
        state.putBoolean("categoryLoaded", initialized);
        super.onSaveInstanceState(state);
    }
}
