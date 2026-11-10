package com.example.fintrack.app.category.model;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.ImageView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import java.util.Arrays;
import java.util.List;

/**
 * Category icon/colour catalogue shared with the backend: {@link #KEYS} must equal
 * CategoryService.ICON_KEYS (the server rejects other icon keys). colorHex is stored as #RRGGBB.
 */
public final class CategoryIcons {
    private CategoryIcons() {}
    public static final List<String> KEYS = Arrays.asList("coffee","car","ticket","shopping_bag","receipt","heart",
            "briefcase","gift","school","more_horiz","image_placeholder");
    public static final List<Integer> RESOURCES = Arrays.asList(R.drawable.ic_coffee,R.drawable.ic_car,
            R.drawable.ic_ticket,R.drawable.ic_shopping_bag,R.drawable.ic_receipt,R.drawable.ic_heart,
            R.drawable.ic_briefcase,R.drawable.ic_gift,R.drawable.ic_school,R.drawable.ic_more_horiz,
            R.drawable.ic_image_placeholder);

    /** Default colours: expense = semantic_error red, income = semantic_success green. */
    public static final String EXPENSE_COLOR = "#B73E36";
    public static final String INCOME_COLOR = "#287B47";
    /** Swatches offered by the colour picker (first two are the type defaults). */
    public static final List<String> COLORS = Arrays.asList(EXPENSE_COLOR, INCOME_COLOR,
            "#9B5A0B", "#2E68B5", "#7A4FB3", "#1F7A7A", "#C2410C", "#5D6C62");

    public static String key(int resource) {
        int index = RESOURCES.indexOf(resource);
        return index < 0 ? null : KEYS.get(index);
    }
    public static int resource(String key) {
        int index = KEYS.indexOf(key);
        return index < 0 ? R.drawable.ic_category : RESOURCES.get(index);
    }
    public static String defaultColor(FinanceBook.Type type) {
        return type == FinanceBook.Type.INCOME ? INCOME_COLOR : EXPENSE_COLOR;
    }
    /** Stored colour if valid, otherwise the type default. */
    public static String color(String colorHex, FinanceBook.Type type) {
        return colorHex != null && colorHex.matches("#[0-9a-fA-F]{6}") ? colorHex : defaultColor(type);
    }
    /** Draws the category's chosen icon in its chosen (or default) colour. */
    public static void paint(ImageView view, FinanceBook.Category category) {
        if (category == null) { view.setImageResource(R.drawable.ic_category); view.setImageTintList(null); return; }
        view.setImageResource(resource(category.iconKey));
        view.setImageTintList(ColorStateList.valueOf(Color.parseColor(color(category.colorHex, category.type))));
    }
}
