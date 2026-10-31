package com.example.fintrack.ui.preview;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.example.fintrack.R;

/**
 * Debug-only visual QA: renders layouts to PNG at several widths / font scales.
 * tools:* attributes are stripped at build time, so sample data is re-applied from copies of the
 * layout sources in androidTest/assets/layouts (synced by scripts/sync-preview-layouts.sh).
 * Output: /sdcard/Android/data/&lt;appId&gt;/files/renders/*.png
 */
@RunWith(AndroidJUnit4.class)
public class LayoutRenderTest {

    private static final String TOOLS_NS = "http://schemas.android.com/tools";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Content area of a 360x800 Figma frame: minus 44dp status bar and 48dp navigation bar. */
    private static final int SCREEN_HEIGHT_DP = 708;

    private Instrumentation instrumentation;
    private Context targetContext;
    private File outDir;

    @Test
    public void renderAll() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        targetContext = instrumentation.getTargetContext();
        outDir = new File(targetContext.getExternalFilesDir(null), "renders");
        //noinspection ResultOfMethodCallIgnored
        outDir.mkdirs();
        String only = InstrumentationRegistry.getArguments().getString("only");
        for (Shot shot : PreviewShots.all()) {
            if (only != null && !shot.name.startsWith(only)) continue;
            for (int width : shot.widthsDp) render(shot, width, 1.0f);
            for (float scale : shot.fontScales) render(shot, 360, scale);
        }
    }

    private void render(Shot shot, int widthDp, float fontScale) throws Exception {
        Throwable[] error = new Throwable[1];
        instrumentation.runOnMainSync(() -> {
            try {
                renderOnMain(shot, widthDp, fontScale);
            } catch (Throwable t) {
                error[0] = t;
            }
        });
        if (error[0] != null) throw new AssertionError("Render failed: " + shot.name, error[0]);
    }

    private void renderOnMain(Shot shot, int widthDp, float fontScale) throws Exception {
        Configuration config = new Configuration(targetContext.getResources().getConfiguration());
        config.fontScale = fontScale;
        config.screenWidthDp = widthDp;
        config.screenHeightDp = SCREEN_HEIGHT_DP;
        Context themed = new ContextThemeWrapper(targetContext.createConfigurationContext(config), R.style.Theme_Fintrack);

        int layoutId = themed.getResources().getIdentifier(shot.layout, "layout", targetContext.getPackageName());
        if (layoutId == 0) throw new IllegalArgumentException("Unknown layout " + shot.layout);
        View root = LayoutInflater.from(themed).inflate(layoutId, null, false);
        applyTools(themed, root, shot.layout, true);
        if (shot.state != null) shot.state.accept(root);

        float density = themed.getResources().getDisplayMetrics().density;
        int widthPx = Math.round(widthDp * density);
        int heightSpec = shot.screen
                ? View.MeasureSpec.makeMeasureSpec(Math.round(SCREEN_HEIGHT_DP * density), View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        root.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), heightSpec);
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(root.getMeasuredWidth(), Math.max(1, root.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        TypedValue bg = new TypedValue();
        themed.getTheme().resolveAttribute(android.R.attr.windowBackground, bg, true);
        if (bg.type >= TypedValue.TYPE_FIRST_COLOR_INT && bg.type <= TypedValue.TYPE_LAST_COLOR_INT) canvas.drawColor(bg.data);
        root.draw(canvas);

        String suffix = fontScale == 1.0f ? widthDp + "dp" : widthDp + "dp_font" + Math.round(fontScale * 100);
        try (FileOutputStream out = new FileOutputStream(new File(outDir, shot.name + "__" + suffix + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** Re-applies tools:text / tools:visibility / tools:src / tools:checked / tools:listitem from the layout source. */
    private void applyTools(Context ctx, View scope, String layout, boolean isRootOfFile) throws Exception {
        XmlPullParser parser = openSource(layout);
        int depth = 0;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.END_TAG) depth--;
            if (event != XmlPullParser.START_TAG) continue;
            depth++;
            String idAttr = parser.getAttributeValue(ANDROID_NS, "id");
            View target = null;
            if (idAttr != null) {
                int id = resId(ctx, idAttr, "id");
                target = id == 0 ? null : (scope.getId() == id ? scope : scope.findViewById(id));
            } else if (depth == 1 && isRootOfFile) {
                target = scope;
            }
            if ("include".equals(parser.getName())) {
                String included = parser.getAttributeValue(null, "layout");
                View includeScope = target;
                if (includeScope == null) continue; // shortcut: includes need an android:id to receive sample data
                applyAttrs(ctx, includeScope, parser);
                applyTools(ctx, includeScope, included.substring(included.indexOf('/') + 1), false);
                continue;
            }
            if (target != null) applyAttrs(ctx, target, parser);
        }
    }

    private void applyAttrs(Context ctx, View view, XmlPullParser parser) throws Exception {
        String text = parser.getAttributeValue(TOOLS_NS, "text");
        if (text != null && view instanceof TextView) ((TextView) view).setText(resolveText(ctx, text));
        String hint = parser.getAttributeValue(TOOLS_NS, "hint");
        if (hint != null && view instanceof TextView) ((TextView) view).setHint(resolveText(ctx, hint));
        String visibility = parser.getAttributeValue(TOOLS_NS, "visibility");
        if (visibility != null) {
            view.setVisibility("visible".equals(visibility) ? View.VISIBLE : "invisible".equals(visibility) ? View.INVISIBLE : View.GONE);
        }
        String src = parser.getAttributeValue(TOOLS_NS, "src");
        if (src != null && view instanceof ImageView) ((ImageView) view).setImageResource(resId(ctx, src, "drawable"));
        String checked = parser.getAttributeValue(TOOLS_NS, "checked");
        if (checked != null && view instanceof CompoundButton) ((CompoundButton) view).setChecked(Boolean.parseBoolean(checked));
        String listItem = parser.getAttributeValue(TOOLS_NS, "listitem");
        if (listItem != null && view instanceof RecyclerView) {
            String count = parser.getAttributeValue(TOOLS_NS, "itemCount");
            bindPreviewList(ctx, (RecyclerView) view, listItem.substring(listItem.indexOf('/') + 1), count == null ? 5 : Integer.parseInt(count));
        }
    }

    private void bindPreviewList(Context ctx, RecyclerView list, String itemLayout, int count) {
        list.setLayoutManager(new LinearLayoutManager(ctx));
        int layoutId = resId(ctx, "@layout/" + itemLayout, "layout");
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View item = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
                try {
                    applyTools(parent.getContext(), item, itemLayout, true);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return new RecyclerView.ViewHolder(item) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) { }

            @Override
            public int getItemCount() {
                return count;
            }
        });
    }

    private XmlPullParser openSource(String layout) throws Exception {
        InputStream in = instrumentation.getContext().getAssets().open("layouts/" + layout + ".xml");
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(in, "UTF-8");
        return parser;
    }

    private static CharSequence resolveText(Context ctx, String value) {
        if (value.startsWith("@string/")) return ctx.getString(resId(ctx, value, "string"));
        return value.replace("\\n", "\n");
    }

    private static int resId(Context ctx, String ref, String defType) {
        String name = ref.substring(ref.indexOf('/') + 1);
        String type = ref.startsWith("@") && ref.contains("/") ? ref.substring(ref.startsWith("@+") ? 2 : 1, ref.indexOf('/')) : defType;
        return ctx.getResources().getIdentifier(name, type, ctx.getPackageName());
    }

    /** One render target. Screens are measured at the 708dp content height; components wrap. */
    public static final class Shot {
        final String name;
        final String layout;
        final boolean screen;
        final Consumer<View> state;
        int[] widthsDp = {320, 360, 412};
        float[] fontScales = {1.3f, 1.5f};

        Shot(String name, String layout, boolean screen, Consumer<View> state) {
            this.name = name;
            this.layout = layout;
            this.screen = screen;
            this.state = state;
        }

        Shot widths(int... dps) {
            widthsDp = dps;
            return this;
        }

        Shot fonts(float... scales) {
            fontScales = scales;
            return this;
        }
    }

    /** Helper for state shots: show/hide views by id name. */
    static Consumer<View> show(String[] visible, String[] gone) {
        return root -> {
            Context ctx = root.getContext();
            for (String id : visible) setVisibility(ctx, root, id, View.VISIBLE);
            for (String id : gone) setVisibility(ctx, root, id, View.GONE);
        };
    }

    private static void setVisibility(Context ctx, View root, String idName, int visibility) {
        View v = root.findViewById(resId(ctx, "@id/" + idName, "id"));
        if (v == null) throw new IllegalArgumentException("No view @id/" + idName);
        v.setVisibility(visibility);
    }

    static List<Shot> list(Shot... shots) {
        List<Shot> out = new ArrayList<>();
        for (Shot s : shots) out.add(s);
        return out;
    }

}
