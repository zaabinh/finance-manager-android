package com.example.fintrack.app.category.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ActivityCategoryAddBinding;
import com.example.fintrack.databinding.ActivityCategoryEditBinding;
import com.example.fintrack.databinding.FormCategoryFieldsBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

/** Uses the same repository, session checks and command lifecycle as finance pickers. */
abstract class CategoryFormActivity extends FinanceActivity {
    private static final String STATE_CATEGORY_TYPE = "categoryType", STATE_ICON_KEY = "iconKey",
            STATE_MANUAL_COLOR = "manualColor", STATE_CATEGORY_LOADED = "categoryLoaded";
    // The add and edit layouts are different binding classes; the shared views are held once here.
    private Toolbar toolbar;
    private EditText name, order;
    private SwitchMaterial status;
    private TextView statusLabel;
    private Button save, cancel, delete;
    private RecyclerView icons, colors;
    private MaterialButton expense, income;
    private CategoryAppearance appearance;
    private String id, iconKey, manualColor, colorHex;
    private int sortOrder;
    private TransactionType type = TransactionType.EXPENSE;
    private boolean initialized, restoredForm, bindingStatus;
    protected abstract boolean editing();
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.CATEGORIES; }

    @Override protected View contentView() {
        View root;
        FormCategoryFieldsBinding fields;
        if (editing()) {
            ActivityCategoryEditBinding b = ActivityCategoryEditBinding.inflate(getLayoutInflater());
            root = b.getRoot(); toolbar = b.toolbarForm; order = b.etCategorySortOrder; status = b.switchStatus; statusLabel = b.tvStatusLabel;
            save = b.btnSaveChanges; delete = b.btnDeleteCategory;
            fields = b.fields;
        } else {
            ActivityCategoryAddBinding b = ActivityCategoryAddBinding.inflate(getLayoutInflater());
            root = b.getRoot(); toolbar = b.toolbarForm; order = b.etCategorySortOrder; status = b.switchStatus; statusLabel = b.tvStatusLabel;
            save = b.btnCreateCategory; cancel = b.btnCancel;
            fields = b.fields;
        }
        name = fields.etCategoryName;
        icons = fields.rvIconPicker; colors = fields.rvColorPicker;
        expense = fields.typeSegmented.btnTypeExpense; income = fields.typeSegmented.btnTypeIncome;
        fields.typeSegmented.btnTypeTransfer.setVisibility(View.GONE);
        return root;
    }

    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(CategoryEditActivity.EXTRA_CATEGORY_ID);
        if (editing() && (id == null || id.isEmpty())) { finish(); return; }
        if ("INCOME".equals(getIntent().getStringExtra(CategoryAddActivity.EXTRA_CATEGORY_TYPE))) type = TransactionType.INCOME;
        if (saved != null) {
            type = TransactionType.valueOf(saved.getString(STATE_CATEGORY_TYPE, "EXPENSE"));
            iconKey = saved.getString(STATE_ICON_KEY); manualColor = saved.getString(STATE_MANUAL_COLOR);
            restoredForm = saved.getBoolean(STATE_CATEGORY_LOADED);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        if (!editing()) {
            cancel.setOnClickListener(v -> finish());
            // Lifecycle is changed explicitly after creation, not through unsupported POST fields.
            ((View)status.getParent()).setVisibility(View.GONE);
        } else {
            delete.setOnClickListener(v ->
                    confirmDelete(() -> model.change(FinanceAction.categoryArchive(id), true)));
        }
        status.setOnCheckedChangeListener((button, active) -> {
            if (!bindingStatus && initialized && !model.saveLoading())
                model.change(FinanceAction.categoryStatus(id, active), false);
        });
        expense.setOnClickListener(v -> { type = TransactionType.EXPENSE; showType(); appearance.setType(type); });
        income.setOnClickListener(v -> { type = TransactionType.INCOME; showType(); appearance.setType(type); });
        save.setOnClickListener(v -> {
            if (!initialized || model.saveLoading()) return;
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
        expense.setChecked(type == TransactionType.EXPENSE);
        income.setChecked(type == TransactionType.INCOME);
    }
    private void showIcons(String manual) {
        appearance = new CategoryAppearance(this, icons, colors, type, iconKey, manual);
    }
    @Override protected void render() {
        if (name == null) return;
        Category current = editing() ? book.category(id) : null;
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
        statusLabel.setText(getString(active ? R.string.status_active : R.string.status_disabled));
        name.setEnabled(active);
        order.setEnabled(active);
        expense.setEnabled(active); income.setEnabled(active);
        FinanceUi.visible(icons, active);
        FinanceUi.visible(colors, active);
        save.setEnabled(active && !model.saveLoading());
        if(editing()) delete.setEnabled(active && !model.saveLoading());
    }
    @Override protected void loading(boolean busy) {
        if (save != null) FinanceUi.progress(save, busy);
        if (save != null) save.setEnabled(!busy && initialized && (!editing() || book != null && book.category(id) != null && book.category(id).archivedAt == null));
        if (status != null) status.setEnabled(!busy && initialized);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_CATEGORY_TYPE,type.name()); state.putString(STATE_ICON_KEY,appearance.iconKey()); state.putString(STATE_MANUAL_COLOR,appearance.manualColor());
        state.putBoolean(STATE_CATEGORY_LOADED, initialized);
        super.onSaveInstanceState(state);
    }
}
