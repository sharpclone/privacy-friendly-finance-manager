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

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.joda.time.LocalDate;
import org.secuso.privacyfriendlyfinance.R;
import org.secuso.privacyfriendlyfinance.domain.FinanceDatabase;
import org.secuso.privacyfriendlyfinance.domain.model.Account;
import org.secuso.privacyfriendlyfinance.domain.model.Category;
import org.secuso.privacyfriendlyfinance.domain.model.Transaction;
import org.secuso.privacyfriendlyfinance.helpers.BalanceHistory;
import org.secuso.privacyfriendlyfinance.helpers.CurrencyHelper;
import org.secuso.privacyfriendlyfinance.helpers.RateResolver;
import org.secuso.privacyfriendlyfinance.helpers.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows the running-balance history of an account or category as a line chart or candlestick
 * (TradingView-style, OHLC of the running balance) with selectable day/week/month intervals and
 * summary statistics underneath.
 */
public class ChartActivity extends AppCompatActivity {
    public static final String EXTRA_ENTITY_TYPE = "org.secuso.privacyfriendlyfinance.CHART_TYPE";
    public static final String EXTRA_ENTITY_ID = "org.secuso.privacyfriendlyfinance.CHART_ID";
    public static final String EXTRA_TITLE = "org.secuso.privacyfriendlyfinance.CHART_TITLE";
    public static final String TYPE_ACCOUNT = "account";
    public static final String TYPE_CATEGORY = "category";

    private static final LocalDate EPOCH = new LocalDate(1970, 1, 1);

    private LineChart lineChart;
    private CandleStickChart candleChart;
    private TextView statsView;

    private boolean isCategory;
    private long entityId;
    private String currencyCode;
    private String defaultCurrency;
    private double conversionFactor = 1.0;

    private enum Mode {TOTAL, PER_PERIOD}

    private List<Transaction> transactions = new ArrayList<>();
    private BalanceHistory.Interval interval = BalanceHistory.Interval.DAY;
    private boolean showCandles = false;
    private Mode mode = Mode.PER_PERIOD;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            getSupportActionBar().setTitle(title != null ? title : getString(R.string.chart_title));
        }

        String type = getIntent().getStringExtra(EXTRA_ENTITY_TYPE);
        isCategory = TYPE_CATEGORY.equals(type);
        entityId = getIntent().getLongExtra(EXTRA_ENTITY_ID, -1);
        // The chart is always rendered in the default currency; other currencies are converted
        // using rates derived from the data (see RateResolver).
        defaultCurrency = SharedPreferencesManager.get(this).getDefaultCurrencyCode();
        currencyCode = defaultCurrency;

        lineChart = findViewById(R.id.chart_line);
        candleChart = findViewById(R.id.chart_candle);
        statsView = findViewById(R.id.text_stats);

        setupChartCosmetics();
        setupControls();
        loadTransactions();
    }

    private void setupControls() {
        Spinner intervalSpinner = findViewById(R.id.spinner_interval);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.chart_interval_day),
                        getString(R.string.chart_interval_week),
                        getString(R.string.chart_interval_month)});
        intervalSpinner.setAdapter(adapter);
        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 1: interval = BalanceHistory.Interval.WEEK; break;
                    case 2: interval = BalanceHistory.Interval.MONTH; break;
                    default: interval = BalanceHistory.Interval.DAY; break;
                }
                render();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        RadioGroup typeGroup = findViewById(R.id.group_chart_type);
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            showCandles = checkedId == R.id.radio_candle;
            lineChart.setVisibility(showCandles ? View.GONE : View.VISIBLE);
            candleChart.setVisibility(showCandles ? View.VISIBLE : View.GONE);
        });

        Spinner modeSpinner = findViewById(R.id.spinner_mode);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.chart_mode_period),
                        getString(R.string.chart_mode_total)});
        modeSpinner.setAdapter(modeAdapter);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mode = position == 1 ? Mode.TOTAL : Mode.PER_PERIOD;
                render();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupChartCosmetics() {
        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return EPOCH.plusDays((int) value).toString("yy-MM-dd");
            }
        });

        candleChart.getDescription().setEnabled(false);
        candleChart.getAxisRight().setEnabled(false);
        candleChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    private void loadTransactions() {
        FinanceDatabase db = FinanceDatabase.getInstance(getApplication());
        new Thread(() -> {
            final List<Transaction> loaded = isCategory
                    ? db.transactionDao().getForCategoryAscSync(entityId)
                    : db.transactionDao().getForAccountAscSync(entityId);

            // Build currency lookups and derive conversion rates to the default currency from
            // the whole data set (transfer in/out pairs and stored conversions).
            SharedPreferencesManager prefs = SharedPreferencesManager.get(this);
            Map<Long, String> accountCurrency = new HashMap<>();
            for (Account account : db.accountDao().getAllSynchron()) {
                if (account != null && account.getId() != null) {
                    accountCurrency.put(account.getId(), prefs.getAccountCurrencyCode(account.getId()));
                }
            }
            Map<Long, String> categoryCurrency = new HashMap<>();
            for (Category category : db.categoryDao().getAllSynchron()) {
                if (category != null && category.getId() != null) {
                    String code = category.getCurrencyCode();
                    if (code == null || code.trim().isEmpty()) code = defaultCurrency;
                    categoryCurrency.put(category.getId(), code);
                }
            }

            RateResolver resolver = new RateResolver(db.transactionDao().getAllSynchron(),
                    accountCurrency, categoryCurrency, defaultCurrency);
            String entityCurrency = isCategory
                    ? categoryCurrency.get(entityId)
                    : accountCurrency.get(entityId);
            final double factor = resolver.factorToDefault(entityCurrency);

            runOnUiThread(() -> {
                transactions = loaded;
                conversionFactor = factor;
                render();
            });
        }).start();
    }

    private void render() {
        if (transactions == null || transactions.isEmpty()) {
            lineChart.clear();
            candleChart.clear();
            statsView.setText(R.string.chart_no_data);
            return;
        }

        BalanceHistory history = new BalanceHistory(transactions, interval, isCategory, conversionFactor);
        if (mode == Mode.TOTAL) {
            renderTotalLine(history);
            renderTotalCandles(history);
        } else {
            renderPeriodLine(history);
            renderPeriodCandles(history);
        }
        renderStats(history);
    }

    // --- cumulative (running balance) ---

    private void renderTotalLine(BalanceHistory history) {
        List<Entry> entries = new ArrayList<>();
        for (BalanceHistory.LinePoint point : history.getLine()) {
            entries.add(new Entry((float) point.epochDay, point.balance / 100f));
        }
        lineChart.getXAxis().setValueFormatter(dateFormatter());
        lineChart.setMarker(new ChartMarkerView(this,
                x -> EPOCH.plusDays((int) x).toString("yy-MM-dd"), currencyCode));
        lineChart.setData(new LineData(styleLine(entries, getString(R.string.chart_balance_label))));
        lineChart.invalidate();
    }

    private void renderTotalCandles(BalanceHistory history) {
        List<CandleEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int i = 0;
        for (BalanceHistory.Candle candle : history.getCandles()) {
            entries.add(new CandleEntry(i, candle.high / 100f, candle.low / 100f,
                    candle.open / 100f, candle.close / 100f));
            labels.add(candle.label);
            i++;
        }
        setCandleData(entries, labels);
    }

    // --- per-period (income / expense / net within each interval) ---

    private void renderPeriodLine(BalanceHistory history) {
        List<Entry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int i = 0;
        for (BalanceHistory.PeriodFlow period : history.getPeriods()) {
            entries.add(new Entry(i, period.net / 100f));
            labels.add(period.label);
            i++;
        }
        lineChart.getXAxis().setValueFormatter(indexFormatter(labels));
        lineChart.setMarker(new ChartMarkerView(this, indexLabelProvider(labels), currencyCode));
        LineDataSet set = styleLine(entries, getString(R.string.chart_flow_label));
        set.setDrawCircles(true);
        set.setCircleColor(getResources().getColor(R.color.colorPrimary));
        lineChart.setData(new LineData(set));
        lineChart.invalidate();
    }

    private void renderPeriodCandles(BalanceHistory history) {
        // One candle per period: open=0 baseline, high=income, low=expenses, close=net.
        List<CandleEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int i = 0;
        for (BalanceHistory.PeriodFlow period : history.getPeriods()) {
            entries.add(new CandleEntry(i,
                    period.income / 100f,
                    period.expense / 100f,
                    0f,
                    period.net / 100f));
            labels.add(period.label);
            i++;
        }
        setCandleData(entries, labels);
    }

    // --- shared helpers ---

    private LineDataSet styleLine(List<Entry> entries, String label) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(getResources().getColor(R.color.colorPrimary));
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillColor(getResources().getColor(R.color.colorPrimary));
        set.setFillAlpha(40);
        return set;
    }

    private void setCandleData(List<CandleEntry> entries, final List<String> labels) {
        CandleDataSet set = new CandleDataSet(entries, getString(R.string.chart_balance_label));
        set.setDrawValues(false);
        set.setShadowColor(Color.DKGRAY);
        set.setShadowWidth(0.8f);
        set.setDecreasingColor(getResources().getColor(R.color.red));
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setIncreasingColor(getResources().getColor(R.color.green));
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setNeutralColor(Color.GRAY);
        candleChart.getXAxis().setValueFormatter(indexFormatter(labels));
        candleChart.setMarker(new ChartMarkerView(this, indexLabelProvider(labels), currencyCode));
        candleChart.setData(new CandleData(set));
        candleChart.invalidate();
    }

    private ValueFormatter dateFormatter() {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return EPOCH.plusDays((int) value).toString("yy-MM-dd");
            }
        };
    }

    private ValueFormatter indexFormatter(final List<String> labels) {
        return new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index < 0 || index >= labels.size()) return "";
                return labels.get(index);
            }
        };
    }

    private ChartMarkerView.LabelProvider indexLabelProvider(final List<String> labels) {
        return x -> {
            int index = (int) x;
            if (index < 0 || index >= labels.size()) return "";
            return labels.get(index);
        };
    }

    private void renderStats(BalanceHistory history) {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.chart_stats_current,
                CurrencyHelper.convertToCurrencyString(this, history.getCurrentBalance(), currencyCode))).append('\n');
        sb.append(getString(R.string.chart_stats_min_max,
                CurrencyHelper.convertToCurrencyString(this, history.getMinBalance(), currencyCode),
                CurrencyHelper.convertToCurrencyString(this, history.getMaxBalance(), currencyCode))).append('\n');
        sb.append(getString(R.string.chart_stats_income,
                CurrencyHelper.convertToCurrencyString(this, history.getTotalIncome(), currencyCode))).append('\n');
        sb.append(getString(R.string.chart_stats_expenses,
                CurrencyHelper.convertToCurrencyString(this, history.getTotalExpenses(), currencyCode))).append('\n');
        sb.append(getString(R.string.chart_stats_net,
                CurrencyHelper.convertToCurrencyString(this, history.getNetChange(), currencyCode))).append('\n');
        sb.append(getString(R.string.chart_stats_count, history.getTransactionCount()));
        statsView.setText(sb.toString());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
