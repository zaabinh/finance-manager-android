package com.example.fintrack.app.core.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Sheet/Container (Figma 629:2895) option picker for wallets and categories: choose one, then confirm. */
public final class PickerSheet {
    private PickerSheet() { }

    public static final class Option {
        public final String id, label;
        @DrawableRes public final int icon;
        /** #RRGGBB tint for the icon, or null for the default secondary colour. */
        public final String colorHex;
        public Option(String id, String label, int icon) { this(id, label, icon, null); }
        public Option(String id, String label, int icon, String colorHex) {
            this.id = id; this.label = label; this.icon = icon; this.colorHex = colorHex;
        }
    }

    /** Active wallets, minus excludeId (e.g. the other side of a transfer). */
    public static List<Option> wallets(List<FinanceBook.Wallet> all, String excludeId) {
        List<Option> out = new ArrayList<>();
        for (FinanceBook.Wallet w : all)
            if (w.archivedAt == null && !w.id.equals(excludeId)) out.add(new Option(w.id, w.name, R.drawable.ic_wallet));
        return out;
    }

    /** Active categories of exactly the given type. */
    public static List<Option> categories(List<FinanceBook.Category> all, FinanceBook.Type type) {
        List<Option> out = new ArrayList<>();
        for (FinanceBook.Category c : all)
            if (c.archivedAt == null && c.type == type) out.add(new Option(c.id, c.name, CategoryIcons.resource(c.iconKey), CategoryIcons.color(c.colorHex, c.type)));
        return out;
    }

    /** The current selection only counts when it is one of the offered options. */
    public static String initialSelection(List<Option> options, String selectedId) {
        for (Option o : options) if (o.id.equals(selectedId)) return selectedId;
        return null;
    }

    /** addNewText 0 hides the add-new row; addNew runs after the sheet is dismissed. */
    public static BottomSheetDialog show(AppCompatActivity host, @StringRes int title, List<Option> options, String selectedId,
            @StringRes int addNewText, Runnable addNew, Consumer<String> confirmed) {
        View content = LayoutInflater.from(host).inflate(R.layout.sheet_picker, null);
        ((TextView) content.findViewById(R.id.tvPickerTitle)).setText(title);
        View confirm = content.findViewById(R.id.btnPickerConfirm);
        String[] selection = {initialSelection(options, selectedId)};
        confirm.setEnabled(selection[0] != null);
        RecyclerView list = content.findViewById(R.id.rvPickerOptions);
        list.setAdapter(new Adapter(options, selection, () -> confirm.setEnabled(selection[0] != null)));
        // Cap the list so the confirm button stays on screen; the list scrolls inside.
        int max = host.getResources().getDisplayMetrics().heightPixels / 2;
        int rows = options.size() * host.getResources().getDimensionPixelSize(R.dimen.selector_min_height);
        if (rows > max) list.getLayoutParams().height = max;
        BottomSheetDialog dialog = SheetDialog.show(host, content);
        content.findViewById(R.id.btnPickerClose).setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> { dialog.dismiss(); if (selection[0] != null) confirmed.accept(selection[0]); });
        TextView add = content.findViewById(R.id.btnPickerAddNew);
        if (addNewText != 0 && addNew != null) {
            add.setText(addNewText); add.setVisibility(View.VISIBLE);
            add.setOnClickListener(v -> { dialog.dismiss(); addNew.run(); });
        }
        return dialog;
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<Option> options; private final String[] selection; private final Runnable changed;
        Adapter(List<Option> options, String[] selection, Runnable changed) {
            this.options = options; this.selection = selection; this.changed = changed;
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker_option, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            Option o = options.get(position);
            h.label.setText(o.label); h.icon.setImageResource(o.icon);
            if (o.colorHex != null) h.icon.setImageTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(o.colorHex)));
            h.itemView.setSelected(o.id.equals(selection[0]));
            h.itemView.setOnClickListener(v -> { selection[0] = o.id; notifyDataSetChanged(); changed.run(); });
        }
        @Override public int getItemCount() { return options.size(); }
        static final class Holder extends RecyclerView.ViewHolder {
            final TextView label; final ImageView icon;
            Holder(View v) { super(v); label = v.findViewById(R.id.tvOptionLabel); icon = v.findViewById(R.id.ivOptionIcon); }
        }
    }
}
