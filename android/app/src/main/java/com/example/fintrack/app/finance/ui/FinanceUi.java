package com.example.fintrack.app.finance.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fintrack.R;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.home.ui.HomeDashboardActivity;
import com.example.fintrack.app.settings.ui.SettingActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

/** Shared lifecycle/error handling. All disk work stays in the repository executor. */
public final class FinanceUi {
    private FinanceUi() { }
    /** VISIBLE when on, GONE otherwise. */
    public static void visible(View view, boolean on) { view.setVisibility(on ? View.VISIBLE : View.GONE); }
    public static String money(long amount) {
        return String.format(Locale.ROOT, "%,d đ", amount).replace(',', '.');
    }
    /** Shared button loading state: disabled with a leading circular progress indicator; enabled and clean otherwise. */
    public static void progress(android.widget.Button button, boolean loading) {
        if (button == null) return;
        int color = button.getCurrentTextColor();
        button.setEnabled(!loading);
        button.setClickable(!loading);
        android.content.Context context = button.getContext();
        android.graphics.drawable.Drawable drawable = null;
        if (loading) {
            com.google.android.material.progressindicator.CircularProgressIndicatorSpec spec =
                    new com.google.android.material.progressindicator.CircularProgressIndicatorSpec(context, null, 0,
                            com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
            spec.indicatorColors = new int[] {color};
            drawable = com.google.android.material.progressindicator.IndeterminateDrawable.createCircularDrawable(context, spec);
        }
        if (button instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton material = (com.google.android.material.button.MaterialButton) button;
            if (loading) {
                material.setIconTint(null);
                material.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
            }
            material.setIcon(drawable);
        } else {
            if (drawable != null) drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            button.setCompoundDrawablesRelative(drawable, null, null, null);
            button.setCompoundDrawablePadding(loading ? context.getResources().getDimensionPixelSize(R.dimen.spacing_8) : 0);
        }
    }
    public static String date(long millis) {
        return date(millis, "Asia/Ho_Chi_Minh");
    }
    public static String date(long millis, String timeZone) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
        return sdf.format(new Date(millis));
    }
    public static int typeName(TransactionType type) {
        if (type == null) return R.string.transaction_type_expense;
        switch (type) {
            case INCOME: return R.string.transaction_type_income;
            case TRANSFER: return R.string.transaction_type_transfer;
            default: return R.string.transaction_type_expense;
        }
    }
    public static CharSequence monthLabel(Context context, String yyyyMm) {
        try {
            String[] parts = yyyyMm.split("-");
            return context.getString(R.string.budget_month_label, Integer.parseInt(parts[1]), parts[0]);
        } catch (Exception e) {
            return yyyyMm;
        }
    }
    public static int error(FinanceProblem problem) {
        switch (problem) {
            case NETWORK: return R.string.error_connection;
            case UNAUTHORIZED: return R.string.error_unauthorized;
            case CONFLICT: return R.string.finance_conflict;
            case VALIDATION: return R.string.error_bad_request;
            case SERVER: return R.string.error_server;
            case INSUFFICIENT_FUNDS: return R.string.finance_insufficient_funds;
            case VOIDED: return R.string.finance_transaction_voided;
            case ARCHIVED: return R.string.finance_archived;
            case NOT_FOUND: return R.string.finance_not_found;
            case AMOUNT: return R.string.finance_amount_error;
            case NAME: return R.string.finance_name_error;
            case CATEGORY: return R.string.finance_category_error;
            case WALLET: return R.string.finance_wallet_error;
            default: return R.string.finance_storage_error;
        }
    }
    public static void insets(View content) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            androidx.core.graphics.Insets ime = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            androidx.core.graphics.Insets bars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, 0, 0, Math.max(0, ime.bottom - bars.bottom));
            return windowInsets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(content);
    }
    public static void navigation(android.app.Activity activity, int selected) {
        int[] ids = {R.id.navHome, R.id.navReport, R.id.navBudget, R.id.navSetting};
        int[] containerIds = {R.id.containerNavHome, R.id.containerNavReport, R.id.containerNavBudget, R.id.containerNavSetting};
        int[] labelIds = {R.id.lblNavHome, R.id.lblNavReport, R.id.lblNavBudget, R.id.lblNavSetting};
        Class<?>[] targets = {HomeDashboardActivity.class, TransactionListActivity.class, BudgetListActivity.class, SettingActivity.class};
        
        for (int i = 0; i < ids.length; i++) {
            View view = activity.findViewById(ids[i]);
            TextView label = activity.findViewById(labelIds[i]);
            View container = activity.findViewById(containerIds[i]);
            
            boolean isActive = (ids[i] == selected);
            
            if (view instanceof ImageView) {
                ImageView imageView = (ImageView) view;
                if (isActive) {
                    androidx.core.widget.ImageViewCompat.setImageTintList(imageView, 
                            android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(activity, R.color.color_primary)));
                    imageView.setSelected(true);
                    imageView.setAlpha(1.0f);
                } else {
                    androidx.core.widget.ImageViewCompat.setImageTintList(imageView, 
                            android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(activity, R.color.text_secondary)));
                    imageView.setSelected(false);
                    imageView.setAlpha(0.7f);
                }
            }
            
            if (label != null) {
                if (isActive) {
                    label.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.color_primary));
                    label.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    label.setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.text_secondary));
                    label.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
            
            final Class<?> target = targets[i];
            View clickTarget = container != null ? container : view;
            if (clickTarget != null) {
                clickTarget.setOnClickListener(v -> {
                    if (target != activity.getClass()) {
                        activity.startActivity(new Intent(activity, target)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    }
                });
            }
        }

        View fabAdd = activity.findViewById(R.id.fabAddTransaction);
        if (fabAdd != null) {
            com.example.fintrack.app.component.FabSpeedDialHelper fabHelper = new com.example.fintrack.app.component.FabSpeedDialHelper(
                    activity.getWindow().getDecorView().getRootView(),
                    new com.example.fintrack.app.component.FabSpeedDialHelper.OnSpeedDialActionListener() {
                        @Override
                        public void onManualInputClick() {
                            activity.startActivity(new Intent(activity, TransactionFormActivity.class));
                        }

                        @Override
                        public void onOcrScanClick() {
                            activity.startActivity(new Intent(activity, com.example.fintrack.app.transaction.ui.TransactionImportActivity.class));
                        }
                    }
            );
            fabAdd.setOnClickListener(v -> fabHelper.showSpeedDial());
        }
    }
    public static void month(FinanceActivity activity, String month, Consumer<String> selected) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER);
        NumberPicker year = new NumberPicker(activity), number = new NumberPicker(activity);
        year.setMinValue(1); year.setMaxValue(9998); year.setValue(Integer.parseInt(month.substring(0, 4)));
        number.setMinValue(1); number.setMaxValue(12); number.setValue(Integer.parseInt(month.substring(5)));
        year.setContentDescription(activity.getString(R.string.finance_year));
        number.setContentDescription(activity.getString(R.string.budget_period_label));
        row.addView(number); row.addView(year);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.transaction_change_month).setView(row)
                .setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.picker_confirm, (dialog, which) -> {
                    row.clearFocus();
                    selected.accept(String.format(Locale.ROOT, "%04d-%02d", year.getValue(), number.getValue()));
                }).show();
    }
    public static void dateTime(FinanceActivity activity, long initial, Consumer<Long> selected) {
        Calendar date = Calendar.getInstance(TimeZone.getTimeZone(activity.book == null ? "Asia/Ho_Chi_Minh" : activity.book.timeZone())); date.setTimeInMillis(initial);
        new DatePickerDialog(activity, (picker, year, month, day) -> {
            date.set(year, month, day);
            new TimePickerDialog(activity, (clock, hour, minute) -> {
                date.set(Calendar.HOUR_OF_DAY, hour); date.set(Calendar.MINUTE, minute);
                date.set(Calendar.SECOND, 0); date.set(Calendar.MILLISECOND, 0); selected.accept(date.getTimeInMillis());
            }, date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE), true).show();
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }
    public static void addWallet(FinanceActivity activity) {
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL);
        int pad = activity.getResources().getDimensionPixelSize(R.dimen.spacing_24); box.setPadding(pad, 0, pad, 0);
        EditText name = new EditText(activity); name.setHint(R.string.finance_wallet_name);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.finance_add_wallet).setView(box)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_create_wallet, (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) activity.model.change(FinanceAction.wallet(value, 0, WalletType.CASH), true);
                }).show();
    }
    public static void addCategory(FinanceActivity activity, TransactionType type) {
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL);
        int pad = activity.getResources().getDimensionPixelSize(R.dimen.spacing_24); box.setPadding(pad, 0, pad, 0);
        EditText name = new EditText(activity); name.setHint(R.string.category_name_hint);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        box.addView(name);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.category_add_title).setView(box)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_create_category, (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) activity.model.change(FinanceAction.category(value, type), true);
                }).show();
    }
    public static void wallet(FinanceActivity activity, Consumer<String> selected) {
        wallet(activity, true, selected);
    }
    public static void wallet(FinanceActivity activity, boolean activeOnly, Consumer<String> selected) {
        if (activity.book == null) {
            Toast.makeText(activity, R.string.finance_wallet_empty, Toast.LENGTH_SHORT).show(); return;
        }
        java.util.List<Wallet> list = new java.util.ArrayList<>();
        for (Wallet item : activity.book.wallets()) if (!activeOnly || item.archivedAt == null) list.add(item);
        CharSequence[] names = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) names[i] = list.get(i).name;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.label_wallet).setItems(names, (dialog, which) ->
                selected.accept(list.get(which).id))
                .setNeutralButton(R.string.finance_add_wallet, (dialog, which) -> activity.startActivity(new Intent(activity,
                        com.example.fintrack.app.wallet.ui.WalletAddActivity.class))).show();
    }
    public static void category(FinanceActivity activity, TransactionType type, Consumer<String> selected) {
        category(activity, type, true, selected);
    }
    public static void category(FinanceActivity activity, TransactionType type, boolean activeOnly, Consumer<String> selected) {
        if (activity.book == null) {
            Toast.makeText(activity, R.string.finance_category_empty, Toast.LENGTH_SHORT).show(); return;
        }
        java.util.List<Category> all = activity.book.categories();
        java.util.List<Category> filtered = new java.util.ArrayList<>();
        for (Category c : all) if (c.type == type && (!activeOnly || c.archivedAt == null)) filtered.add(c);
        CharSequence[] names = new CharSequence[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) names[i] = filtered.get(i).name;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.label_category).setItems(names, (dialog, which) ->
                selected.accept(filtered.get(which).id))
                .setNeutralButton(R.string.category_add_title, (dialog, which) -> activity.startActivity(new Intent(activity,
                        com.example.fintrack.app.category.ui.CategoryAddActivity.class).putExtra(com.example.fintrack.app.category.ui.CategoryAddActivity.EXTRA_CATEGORY_TYPE, type.name()))).show();
    }
}
