package com.example.fintrack.app.category.model;

import com.example.fintrack.R;
import java.util.Arrays;
import java.util.List;

/** Stable server keys; unknown keys still render with the generic category icon. */
public final class CategoryIcons {
    private CategoryIcons() {}
    private static final String[] KEYS = {"coffee","car","ticket","shopping_bag","receipt","heart",
            "briefcase","gift","school","more_horiz","image_placeholder"};
    public static final List<Integer> RESOURCES = Arrays.asList(R.drawable.ic_coffee,R.drawable.ic_car,
            R.drawable.ic_ticket,R.drawable.ic_shopping_bag,R.drawable.ic_receipt,R.drawable.ic_heart,
            R.drawable.ic_briefcase,R.drawable.ic_gift,R.drawable.ic_school,R.drawable.ic_more_horiz,
            R.drawable.ic_image_placeholder);
    public static String key(int resource) {
        int index = RESOURCES.indexOf(resource);
        return index < 0 ? null : KEYS[index];
    }
    public static int resource(String key) {
        for (int i=0;i<KEYS.length;i++) if (KEYS[i].equals(key)) return RESOURCES.get(i);
        return R.drawable.ic_category;
    }
}
