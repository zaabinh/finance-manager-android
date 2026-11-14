package com.example.fintrack.app.category.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.databinding.ItemColorSwatchBinding;
import java.util.List;

/** Round colour swatches; the selected one shows a check and ring. Users never see hex codes. */
public class ColorSwatchAdapter extends RecyclerView.Adapter<ColorSwatchAdapter.Holder> {
    public interface Listener { void onPick(String hex); }

    private final List<String> colors;
    private final CharSequence[] names;
    private final Listener listener;
    private String selected;

    public ColorSwatchAdapter(List<String> colors, CharSequence[] names, String selected, Listener listener) {
        this.colors = colors; this.names = names; this.selected = selected; this.listener = listener;
    }

    public void setSelected(String hex) { selected = hex; notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemColorSwatchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        String hex = colors.get(position);
        boolean on = hex.equalsIgnoreCase(selected);
        h.color.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
        h.check.setVisibility(on ? View.VISIBLE : View.GONE);
        h.ring.setVisibility(on ? View.VISIBLE : View.GONE);
        h.touch.setContentDescription(position < names.length ? names[position] : hex);
        h.touch.setSelected(on);
        h.touch.setOnClickListener(v -> { selected = hex; notifyDataSetChanged(); listener.onPick(hex); });
    }

    @Override public int getItemCount() { return colors.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View touch, color, ring, check;
        Holder(ItemColorSwatchBinding b) {
            super(b.getRoot());
            touch = b.flSwatch; color = b.viewSwatchColor; ring = b.viewSwatchRing; check = b.ivSwatchCheck;
        }
    }
}
