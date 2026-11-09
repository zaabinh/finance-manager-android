package com.example.fintrack.app.core.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import com.example.fintrack.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

/** Attaches thousands grouping and optional suggestion chips to an amount EditText. */
public final class MoneyInput {
    private MoneyInput() { }

    public static void attach(EditText input) { attach(input, null); }

    /** @param chips optional group shown only while there are suggestions (its scroll parent is toggled too). */
    public static void attach(EditText input, ChipGroup chips) {
        input.addTextChangedListener(new TextWatcher() {
            private boolean busy;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (busy) return;
                busy = true;
                try {
                    String grouped = MoneyFormat.group(s);
                    if (!grouped.contentEquals(s)) {
                        int digitsBefore = MoneyFormat.digitsBefore(s, input.getSelectionStart());
                        s.replace(0, s.length(), grouped);
                        input.setSelection(MoneyFormat.indexAfterDigits(grouped, digitsBefore));
                    }
                    if (chips != null) showSuggestions(input, chips, MoneyFormat.suggestions(grouped));
                } finally { busy = false; }
            }
        });
        if (chips != null) showSuggestions(input, chips, MoneyFormat.suggestions(input.getText()));
    }

    private static void showSuggestions(EditText input, ChipGroup group, List<Long> values) {
        group.removeAllViews();
        for (long value : values) {
            String text = MoneyFormat.group(value);
            Chip chip = new Chip(group.getContext());
            chip.setText(text);
            chip.setContentDescription(group.getContext().getString(R.string.amount_suggestion_description, text));
            chip.setOnClickListener(v -> { input.setText(text); input.setSelection(input.length()); });
            group.addView(chip);
        }
        int visibility = values.isEmpty() ? View.GONE : View.VISIBLE;
        ViewParent parent = group.getParent();
        if (parent instanceof android.widget.HorizontalScrollView) ((View) parent).setVisibility(visibility);
        else group.setVisibility(visibility);
    }
}
