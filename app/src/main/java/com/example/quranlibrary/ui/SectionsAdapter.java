package com.example.quranlibrary.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quranlibrary.R;
import com.example.quranlibrary.data.db.Section;

/**
 * Adapter لعرض قائمة الأقسام.
 * يستخدم ListAdapter + DiffUtil لتحديثات سلسة بدون notifyDataSetChanged.
 */
public class SectionsAdapter extends ListAdapter<Section, SectionsAdapter.SectionViewHolder> {

    public interface OnSectionClickListener {
        void onSectionClick(Section section);
    }

    private final OnSectionClickListener listener;

    public SectionsAdapter(OnSectionClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Section> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Section>() {
                @Override
                public boolean areItemsTheSame(@NonNull Section oldItem, @NonNull Section newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Section oldItem, @NonNull Section newItem) {
                    return oldItem.name.equals(newItem.name)
                            && oldItem.sortOrder == newItem.sortOrder
                            && oldItem.iconKey.equals(newItem.iconKey);
                }
            };

    @NonNull
    @Override
    public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_section, parent, false);
        return new SectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
        Section section = getItem(position);
        holder.bind(section, listener);
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.section_name);
        }

        void bind(Section section, OnSectionClickListener listener) {
            nameText.setText(section.name);
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onSectionClick(section);
            });
        }
    }
}
