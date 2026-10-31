package com.example.fintrack.app.finance.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.home.ui.HomeDashboardActivity;
import com.example.fintrack.app.settings.ui.SettingActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.function.Consumer;

public final class FinanceUi {
    private FinanceUi() { }
    public static String money(long amount) { return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(amount) + "\u00a0đ"; }
    public static String date(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy, HH:mm", new Locale("vi", "VN"));
        format.setTimeZone(FinanceBook.ZONE); return format.format(timestamp);
    }
    public static String monthLabel(String month) { return month.substring(5) + "/" + month.substring(0, 4); }
    public static int typeName(FinanceBook.Type type) {
        return type == FinanceBook.Type.EXPENSE ? R.string.transaction_type_expense
                : type == FinanceBook.Type.INCOME ? R.string.transaction_type_income : R.string.transaction_type_transfer;
    }
    public static int error(FinanceBook.Problem problem) {
        switch (problem) {
            case AMOUNT: return R.string.finance_amount_error;
            case CATEGORY: return R.string.finance_category_error;
            case WALLET: return R.string.finance_wallet_error;
            case SAME_WALLET: return R.string.transfer_same_wallet_error;
            case DUPLICATE_BUDGET: return R.string.finance_duplicate_budget;
            case NOT_FOUND: return R.string.finance_not_found;
            case NAME: return R.string.finance_name_error;
            case DATE: return R.string.finance_date_error;
            case NETWORK: return R.string.error_connection;
            case UNAUTHORIZED: return R.string.error_unauthorized;
            case CONFLICT: return R.string.finance_conflict;
            case VALIDATION: return R.string.error_bad_request;
            case SERVER: return R.string.error_server;
            default: return R.string.finance_storage_error;
        }
    }
    public static void insets(View content) {
        // These screens use fitsSystemWindows for bars; IME needs explicit handling on edge-to-edge targets.
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, 0, 0, Math.max(0, ime.bottom - bars.bottom));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }
    public static void navigation(FinanceActivity activity, int selected) {
        int[] ids = {R.id.navTabHome, R.id.navTabHistory, R.id.navTabKhoan, R.id.navTabSettings, R.id.btnNavAdd};
        Class<?>[] targets = {HomeDashboardActivity.class, TransactionListActivity.class, BudgetListActivity.class,
                SettingActivity.class, TransactionFormActivity.class};
        for (int i = 0; i < ids.length; i++) {
            View view = activity.findViewById(ids[i]);
            view.setSelected(ids[i] == selected);
            final Class<?> target = targets[i];
            view.setOnClickListener(v -> {
                if (target != activity.getClass()) activity.startActivity(new Intent(activity, target)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            });
        }
    }
    public static void month(FinanceActivity activity, String month, Consumer<String> selected) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER);
        NumberPicker year = new NumberPicker(activity), number = new NumberPicker(activity);
        year.setMinValue(1970); year.setMaxValue(9999); year.setValue(Integer.parseInt(month.substring(0, 4)));
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
        Calendar date = Calendar.getInstance(FinanceBook.ZONE); date.setTimeInMillis(initial);
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
        EditText balance = new EditText(activity); balance.setHint(R.string.finance_opening_balance);
        balance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        android.widget.Spinner type = new android.widget.Spinner(activity);
        type.setAdapter(new android.widget.ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{activity.getString(R.string.wallet_cash), activity.getString(R.string.finance_wallet_bank), activity.getString(R.string.finance_wallet_savings)}));
        type.setContentDescription(activity.getString(R.string.finance_wallet_type));
        box.addView(name); box.addView(balance); box.addView(type);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.finance_add_wallet)
                .setView(box).setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.picker_confirm, null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(button -> {
            String label = name.getText().toString().trim();
            if (label.isEmpty() || label.length() > 100) { name.setError(activity.getString(R.string.finance_name_error)); return; }
            long opening;
            try { opening = balance.getText().toString().isEmpty() || "0".equals(balance.getText().toString())
                    ? 0 : FinanceBook.parseAmount(balance.getText().toString()); }
            catch (FinanceBook.Failure failure) { balance.setError(activity.getString(R.string.finance_amount_error)); return; }
            FinanceBook.WalletType walletType = FinanceBook.WalletType.values()[type.getSelectedItemPosition()];
            activity.model.change(FinanceAction.wallet(label, opening, walletType), false); dialog.dismiss();
        })); dialog.show();
    }
    public static void addCategory(FinanceActivity activity, FinanceBook.Type type) {
        EditText name = new EditText(activity); name.setHint(R.string.finance_category_name);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.picker_add_category)
                .setView(name).setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.picker_confirm, null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(button -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty() || value.length() > 100) { name.setError(activity.getString(R.string.finance_name_error)); return; }
            activity.model.change(FinanceAction.category(value, type), false); dialog.dismiss();
        })); dialog.show();
    }
    public static void wallet(FinanceActivity activity, Consumer<String> selected) {
        if (activity.book == null || activity.model.busy()) return;
        java.util.List<FinanceBook.Wallet> wallets = activity.book.state().wallets;
        String[] labels = new String[wallets.size() + 1];
        for (int i = 0; i < wallets.size(); i++) labels[i] = wallets.get(i).name + " · " + money(activity.book.balance(wallets.get(i).id));
        labels[wallets.size()] = activity.getString(R.string.finance_add_wallet);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.picker_wallet_title).setItems(labels, (dialog, which) -> {
            if (which == wallets.size()) addWallet(activity); else selected.accept(wallets.get(which).id);
        }).show();
    }
    public static void category(FinanceActivity activity, FinanceBook.Type type, Consumer<String> selected) {
        if (activity.book == null || activity.model.busy()) return;
        java.util.List<FinanceBook.Category> categories = new java.util.ArrayList<>();
        for (FinanceBook.Category item : activity.book.state().categories) if (item.type == type) categories.add(item);
        String[] labels = new String[categories.size() + 1];
        for (int i = 0; i < categories.size(); i++) labels[i] = categories.get(i).name;
        labels[categories.size()] = activity.getString(R.string.picker_add_category);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.picker_category_title).setItems(labels, (dialog, which) -> {
            if (which == categories.size()) addCategory(activity, type); else selected.accept(categories.get(which).id);
        }).show();
    }
}
