package com.example.fintrack.app.category.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ItemCategoryBinding;
import com.example.fintrack.app.category.model.CategoryIcons;
import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CategoryAdapter: Adapter quản lý danh sách Category trong màn hình quản lý danh mục.
 * Sử dụng trực tiếp canonical domain model Category và DiffUtil để tối ưu hiệu năng.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
        void onStatusToggle(Category category, boolean isChecked);
    }

    private final List<Category> categoryList = new ArrayList<>();
    private final OnCategoryClickListener listener;

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<Category> categories) {
        List<Category> next = categories != null ? categories : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return categoryList.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return categoryList.get(oldItemPosition).id.equals(next.get(newItemPosition).id);
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Category oldC = categoryList.get(oldItemPosition);
                Category newC = next.get(newItemPosition);
                return Objects.equals(oldC.name, newC.name)
                        && oldC.type == newC.type
                        && Objects.equals(oldC.iconKey, newC.iconKey)
                        && Objects.equals(oldC.colorHex, newC.colorHex)
                        && Objects.equals(oldC.archivedAt, newC.archivedAt);
            }
        });
        categoryList.clear();
        categoryList.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(categoryList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View flIconBg;
        private final ImageView ivCategoryIcon;
        private final TextView tvCategoryName;
        private final TextView tvCategoryStatus;
        private final SwitchMaterial switchStatus;

        public ViewHolder(@NonNull ItemCategoryBinding binding) {
            super(binding.getRoot());
            flIconBg = binding.flIconBg;
            ivCategoryIcon = binding.ivCategoryIcon;
            tvCategoryName = binding.tvCategoryName;
            tvCategoryStatus = binding.tvCategoryStatus;
            switchStatus = binding.switchStatus;
        }

        public void bind(Category category, OnCategoryClickListener listener) {
            Context context = itemView.getContext();
            tvCategoryName.setText(category.name);

            boolean active = category.archivedAt == null;
            if (active) {
                tvCategoryStatus.setText(R.string.status_active);
                tvCategoryStatus.setTextColor(ContextCompat.getColor(context, R.color.status_success));
            } else {
                tvCategoryStatus.setText(R.string.status_disabled);
                tvCategoryStatus.setTextColor(ContextCompat.getColor(context, R.color.neutral_text_400));
            }

            switchStatus.setOnCheckedChangeListener(null);
            switchStatus.setChecked(active);

            if (category.type == TransactionType.INCOME) {
                flIconBg.setBackgroundResource(R.drawable.bg_income_card);
            } else {
                flIconBg.setBackgroundResource(R.drawable.bg_expense_card);
            }

            CategoryIcons.paint(ivCategoryIcon, category);

            switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onStatusToggle(category, isChecked);
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
