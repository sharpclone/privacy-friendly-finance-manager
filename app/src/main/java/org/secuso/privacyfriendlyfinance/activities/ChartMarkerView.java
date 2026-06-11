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

package org.secuso.privacyfriendlyfinance.activities;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import org.secuso.privacyfriendlyfinance.R;
import org.secuso.privacyfriendlyfinance.helpers.CurrencyHelper;

/**
 * Small info box shown when a chart point is tapped: renders the value (in the chart currency) and
 * the date/period label of the tapped point.
 */
public class ChartMarkerView extends MarkerView {
    public interface LabelProvider {
        String labelFor(float x);
    }

    private final TextView textView;
    private final LabelProvider labelProvider;
    private final String currencyCode;
    private final Context context;

    public ChartMarkerView(Context context, LabelProvider labelProvider, String currencyCode) {
        super(context, R.layout.marker_chart);
        this.context = context;
        this.labelProvider = labelProvider;
        this.currencyCode = currencyCode;
        this.textView = findViewById(R.id.marker_text);
    }

    @Override
    public void refreshContent(Entry entry, Highlight highlight) {
        long cents = Math.round(entry.getY() * 100);
        String value = CurrencyHelper.convertToCurrencyString(context, cents, currencyCode);
        String label = labelProvider != null ? labelProvider.labelFor(entry.getX()) : null;
        textView.setText(label == null || label.isEmpty() ? value : label + "\n" + value);
        super.refreshContent(entry, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}
