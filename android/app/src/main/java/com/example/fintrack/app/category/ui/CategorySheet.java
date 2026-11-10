package com.example.fintrack.app.category.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.IconPickerAdapter;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.core.ui.SheetDialog;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/** Sheet/Category (Figma 718:11596, Add): quick category creation. The caller decides what saving means. */
public final class CategorySheet {
    public interface Listener { void onSave(CategorySheet sheet, String name, FinanceBook.Type type, String iconKey, String colorHex); }

    private final BottomSheetDialog dialog;
    private final MaterialButton confirm;
    private FinanceBook.Type type;
    private final CategoryAppearance appearance;

    private CategorySheet(AppCompatActivity host, FinanceBook.Type initial, Listener listener) {
        type = initial == FinanceBook.Type.INCOME ? FinanceBook.Type.INCOME : FinanceBook.Type.EXPENSE;
        View content = LayoutInflater.from(host).inflate(R.layout.sheet_category, null);
        ((TextView) content.findViewById(R.id.tvSheetTitle)).setText(R.string.sheet_category_title);
        content.findViewById(R.id.btnTypeTransfer).setVisibility(View.GONE);
        EditText name = content.findViewById(R.id.etCategoryName);
        MaterialButton expense = content.findViewById(R.id.btnTypeExpense), income = content.findViewById(R.id.btnTypeIncome);
        appearance = new CategoryAppearance(host, content.findViewById(R.id.rvIconPicker),
                content.findViewById(R.id.rvColorPicker), type, null, null);
        Runnable showType = () -> {
            expense.setChecked(type == FinanceBook.Type.EXPENSE); income.setChecked(type == FinanceBook.Type.INCOME);
        };
        showType.run();
        expense.setOnClickListener(v -> { type = FinanceBook.Type.EXPENSE; showType.run(); appearance.setType(type); });
        income.setOnClickListener(v -> { type = FinanceBook.Type.INCOME; showType.run(); appearance.setType(type); });
        confirm = content.findViewById(R.id.btnSheetConfirm);
        confirm.setText(R.string.sheet_category_title);
        dialog = SheetDialog.show(host, content);
        content.findViewById(R.id.btnSheetClose).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnSheetCancel).setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty() || value.codePointCount(0, value.length()) > 100) {
                name.setError(host.getString(R.string.category_name_invalid)); return;
            }
            listener.onSave(this, value, type, appearance.iconKey(), appearance.colorHex());
        });
    }

    public static CategorySheet show(AppCompatActivity host, FinanceBook.Type type, Listener listener) {
        return new CategorySheet(host, type, listener);
    }

    public void loading(boolean busy) { FinanceUi.progress(confirm, busy); }
    public void dismiss() { dialog.dismiss(); }
}
