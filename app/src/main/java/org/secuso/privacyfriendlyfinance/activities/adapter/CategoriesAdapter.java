/*
 Privacy Friendly Finance Manager is licensed under the GPLv3.
 Copyright (C) 2019 Leonard Otto, Felix Hofmann

 This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 General Public License as published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with this program.
 If not, see http://www.gnu.org/licenses/.

 Additionally icons from Google Design Material Icons are used that are licensed under Apache
 License Version 2.0.
 */

package org.secuso.privacyfriendlyfinance.activities.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import org.secuso.privacyfriendlyfinance.R;
import org.secuso.privacyfriendlyfinance.activities.BaseActivity;
import org.secuso.privacyfriendlyfinance.domain.model.Category;
import org.secuso.privacyfriendlyfinance.helpers.CurrencyHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for category lists. Supports hold-to-select multi-selection with a grouped
 * (per-currency) sum of the selected categories' current-month balances.
 *
 * @author Felix Hofmann
 * @author Leonard Otto
 */
public class CategoriesAdapter extends EntityListAdapter<CategoryWrapper, CategoryViewHolder> {
    public interface CategoryUiListener {
        void onOpen(CategoryWrapper wrapper);
        void onSelectionChanged(int selectedCount, String groupedSummary);
    }

    private final Set<Long> selectedIds = new HashSet<>();
    private boolean selectionMode = false;
    private CategoryUiListener listener;

    public CategoriesAdapter(BaseActivity context, LiveData<List<CategoryWrapper>> data) {
        super(context, data);
    }

    public void setListener(CategoryUiListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        View viewItem = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_category, parent, false);
        return new CategoryViewHolder(viewItem, context);
    }

    @Override
    public void onBindViewHolder(@NonNull final CategoryViewHolder holder, int index) {
        final CategoryWrapper wrapper = getItem(index);
        Category category = wrapper.getCategory();

        holder.itemView.setOnClickListener(v -> {
            int position = holder.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            CategoryWrapper clicked = getItem(position);
            if (selectionMode) {
                toggleSelection(clicked.getId());
            } else if (listener != null) {
                listener.onOpen(clicked);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            int position = holder.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return false;
            CategoryWrapper clicked = getItem(position);
            selectionMode = true;
            toggleSelection(clicked.getId());
            return true;
        });

        holder.setCategoryName(category.getName());
        holder.setCategoryColor(category.getColor());
        holder.setBudget(wrapper.getCategory().getBudget(), wrapper.getCurrencyCode());
        holder.setSelectedState(selectedIds.contains(category.getId()));
        wrapper.getBalance().observe(context, new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long balance) {
                holder.setBalance(balance, wrapper.getCurrencyCode());
                if (selectionMode && selectedIds.contains(wrapper.getId())) {
                    notifySelectionChanged();
                }
            }
        });
    }

    private void toggleSelection(Long id) {
        if (id == null) return;
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (selectedIds.isEmpty()) {
            selectionMode = false;
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(selectedIds.size(), getGroupedSummary());
        }
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void clearSelection() {
        selectedIds.clear();
        selectionMode = false;
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    /**
     * Groups the selected categories' current-month balances by their currency and renders a
     * "120.00 EUR + 50.00 USD" style summary. No cross-currency conversion is invented.
     */
    public String getGroupedSummary() {
        Map<String, Long> totalsByCurrency = new LinkedHashMap<>();
        for (CategoryWrapper wrapper : getCurrentList()) {
            if (!selectedIds.contains(wrapper.getId())) continue;
            String currency = wrapper.getCurrencyCode();
            Long balance = wrapper.getBalance().getValue();
            if (balance == null) balance = 0L;
            long old = totalsByCurrency.containsKey(currency) ? totalsByCurrency.get(currency) : 0L;
            totalsByCurrency.put(currency, old + balance);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : totalsByCurrency.entrySet()) {
            parts.add(CurrencyHelper.convertToCurrencyString(context, entry.getValue(), entry.getKey()));
        }
        return String.join(" + ", parts);
    }
}
