package com.example.fintrack.app.category.ui;

import android.content.Context;
import android.graphics.Color;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.category.adapter.ColorSwatchAdapter;
import com.example.fintrack.app.category.adapter.IconPickerAdapter;
import com.example.fintrack.app.category.model.CategoryColorChoice;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.finance.domain.FinanceBook;

/**
 * Icon grid + colour swatches shared by Sheet/Category and the category screens. The icon grid previews
 * the selected colour; the colour follows the type default until a swatch is picked.
 */
final class CategoryAppearance {
    private final CategoryColorChoice color;
    private final IconPickerAdapter icons;
    private final ColorSwatchAdapter swatches;
    private String iconKey;

    /** iconKey may be null (first icon); manualColor is a stored or previously picked hex, null = follow the type. */
    CategoryAppearance(Context context, RecyclerView iconList, RecyclerView colorList, FinanceBook.Type type,
            String iconKey, String manualColor) {
        this.iconKey = iconKey == null ? CategoryIcons.KEYS.get(0) : iconKey;
        color = new CategoryColorChoice(type, manualColor);
        icons = new IconPickerAdapter(CategoryIcons.RESOURCES, CategoryIcons.resource(this.iconKey),
                resource -> this.iconKey = CategoryIcons.key(resource));
        swatches = new ColorSwatchAdapter(CategoryIcons.COLORS, context.getResources().getStringArray(R.array.category_color_names),
                color.selected(), hex -> { color.pick(hex); refresh(); });
        iconList.setAdapter(icons);
        colorList.setAdapter(swatches);
        refresh();
    }

    void setType(FinanceBook.Type type) { color.setType(type); refresh(); }
    String iconKey() { return iconKey; }
    /** Hex to save: always a swatch value or the stored one. */
    String colorHex() { return color.selected(); }
    /** Picked colour to keep across rotation (null = follow the type). */
    String manualColor() { return color.manual(); }

    private void refresh() {
        swatches.setSelected(color.selected());
        icons.setTint(Color.parseColor(color.selected()));
    }
}
