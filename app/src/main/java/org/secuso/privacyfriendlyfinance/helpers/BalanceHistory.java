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

package org.secuso.privacyfriendlyfinance.helpers;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.secuso.privacyfriendlyfinance.domain.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes a running-balance history from a list of transactions, producing both a continuous
 * line series and per-interval OHLC candles (TradingView-style) plus summary statistics.
 *
 * <p>All monetary values are kept in cents (long), exactly as stored in {@link Transaction}.</p>
 */
public class BalanceHistory {

    public enum Interval {DAY, WEEK, MONTH}

    /** One point of the running-balance line. x is the epoch day, y the balance in cents. */
    public static class LinePoint {
        public final long epochDay;
        public final long balance;

        LinePoint(long epochDay, long balance) {
            this.epochDay = epochDay;
            this.balance = balance;
        }
    }

    /** One OHLC candle for an interval. Values are running balances in cents. */
    public static class Candle {
        public final String label;
        public final long open;
        public final long high;
        public final long low;
        public final long close;

        Candle(String label, long open, long high, long low, long close) {
            this.label = label;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }

    /** Per-interval flow (not cumulative): income, expenses and their net for one period. */
    public static class PeriodFlow {
        public final String label;
        public final long income;
        public final long expense; // <= 0
        public final long net;

        PeriodFlow(String label, long income, long expense) {
            this.label = label;
            this.income = income;
            this.expense = expense;
            this.net = income + expense;
        }
    }

    private final List<LinePoint> line = new ArrayList<>();
    private final List<Candle> candles = new ArrayList<>();
    private final List<PeriodFlow> periods = new ArrayList<>();
    private long currentBalance = 0;
    private long minBalance = 0;
    private long maxBalance = 0;
    private long totalIncome = 0;
    private long totalExpenses = 0;
    private int transactionCount = 0;

    public BalanceHistory(List<Transaction> transactions, Interval interval, boolean useCategoryAmount) {
        this(transactions, interval, useCategoryAmount, 1.0);
    }

    /**
     * @param transactions  must be ordered ascending by date
     * @param interval      candle granularity
     * @param useCategoryAmount when true the value in the category currency
     *                          (COALESCE(categoryAmount, amount)) is used instead of amount
     * @param conversionFactor each value is multiplied by this factor (e.g. to express the whole
     *                          series in the default currency); use 1.0 for no conversion
     */
    public BalanceHistory(List<Transaction> transactions, Interval interval, boolean useCategoryAmount,
                          double conversionFactor) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        final LocalDate epoch = new LocalDate(1970, 1, 1);

        long running = 0;
        String currentKey = null;
        long candleOpen = 0;
        long candleHigh = 0;
        long candleLow = 0;
        long periodIncome = 0;
        long periodExpense = 0;
        boolean first = true;

        for (Transaction t : transactions) {
            long value = Math.round(valueOf(t, useCategoryAmount) * conversionFactor);
            transactionCount++;
            if (value >= 0) totalIncome += value;
            else totalExpenses += value;

            String key = periodKey(t.getDate(), interval);
            if (!key.equals(currentKey)) {
                // close the previous candle / period
                if (currentKey != null) {
                    candles.add(new Candle(currentKey, candleOpen, candleHigh, candleLow, running));
                    periods.add(new PeriodFlow(currentKey, periodIncome, periodExpense));
                }
                currentKey = key;
                candleOpen = running; // open of new period = close of previous
                candleHigh = running;
                candleLow = running;
                periodIncome = 0;
                periodExpense = 0;
            }

            if (value >= 0) periodIncome += value;
            else periodExpense += value;

            running += value;
            if (running > candleHigh) candleHigh = running;
            if (running < candleLow) candleLow = running;

            if (first) {
                minBalance = running;
                maxBalance = running;
                first = false;
            } else {
                if (running < minBalance) minBalance = running;
                if (running > maxBalance) maxBalance = running;
            }

            long epochDay = Days.daysBetween(epoch, t.getDate()).getDays();
            line.add(new LinePoint(epochDay, running));
        }

        // close the final candle / period
        if (currentKey != null) {
            candles.add(new Candle(currentKey, candleOpen, candleHigh, candleLow, running));
            periods.add(new PeriodFlow(currentKey, periodIncome, periodExpense));
        }
        currentBalance = running;
    }

    public List<PeriodFlow> getPeriods() {
        return periods;
    }

    private static long valueOf(Transaction t, boolean useCategoryAmount) {
        if (useCategoryAmount && t.getCategoryAmount() != null) {
            return t.getCategoryAmount();
        }
        return t.getAmount();
    }

    private static String periodKey(LocalDate date, Interval interval) {
        switch (interval) {
            case WEEK:
                return String.format("%04d-W%02d", date.getWeekyear(), date.getWeekOfWeekyear());
            case MONTH:
                return String.format("%04d-%02d", date.getYear(), date.getMonthOfYear());
            case DAY:
            default:
                return date.toString();
        }
    }

    public List<LinePoint> getLine() {
        return line;
    }

    public List<Candle> getCandles() {
        return candles;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public long getMinBalance() {
        return minBalance;
    }

    public long getMaxBalance() {
        return maxBalance;
    }

    public long getTotalIncome() {
        return totalIncome;
    }

    public long getTotalExpenses() {
        return totalExpenses;
    }

    public long getNetChange() {
        return totalIncome + totalExpenses;
    }

    public int getTransactionCount() {
        return transactionCount;
    }
}
