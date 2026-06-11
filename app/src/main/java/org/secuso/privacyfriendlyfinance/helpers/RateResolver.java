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

import org.secuso.privacyfriendlyfinance.domain.model.Transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives, purely from existing data, a factor to convert any amount into the app's default
 * currency. Exchange rates are inferred from:
 * <ul>
 *   <li>transfer pairs &mdash; "what came out" (source) vs "what came in" (destination),</li>
 *   <li>the stored {@code defaultAmount} of a transaction (its value already in default currency),</li>
 *   <li>a transaction's {@code categoryAmount} vs {@code amount} (account vs category currency).</li>
 * </ul>
 * Currencies are connected as a graph and the factor to the default currency is propagated with a
 * breadth-first search, so currencies linked only indirectly (A&rarr;B&rarr;default) are still resolved.
 */
public class RateResolver {
    private final Map<String, Double> factorToDefault = new HashMap<>();

    public RateResolver(List<Transaction> allTransactions,
                        Map<Long, String> accountCurrencyById,
                        Map<Long, String> categoryCurrencyById,
                        String defaultCurrency) {
        // adjacency: from -> (to -> rate, where 1 unit of `from` == rate units of `to`)
        Map<String, Map<String, Double>> edges = new HashMap<>();

        // 1) transfer pairs
        Map<Long, List<Transaction>> byTransfer = new HashMap<>();
        for (Transaction t : allTransactions) {
            if (t.getTransferId() != null) {
                List<Transaction> group = byTransfer.get(t.getTransferId());
                if (group == null) {
                    group = new ArrayList<>();
                    byTransfer.put(t.getTransferId(), group);
                }
                group.add(t);
            }
        }
        for (List<Transaction> pair : byTransfer.values()) {
            if (pair.size() != 2) continue;
            Transaction a = pair.get(0);
            Transaction b = pair.get(1);
            Transaction out = a.getAmount() < 0 ? a : b;
            Transaction in = a.getAmount() < 0 ? b : a;
            String currencyOut = accountCurrencyById.get(out.getAccountId());
            String currencyIn = accountCurrencyById.get(in.getAccountId());
            double sent = Math.abs(out.getAmount());
            double received = Math.abs(in.getAmount());
            if (currencyOut == null || currencyIn == null || sent == 0 || received == 0) continue;
            addEdge(edges, currencyOut, currencyIn, received / sent);
        }

        // 2) stored defaultAmount
        for (Transaction t : allTransactions) {
            if (t.getDefaultAmount() != null && t.getAmount() != 0) {
                String currency = accountCurrencyById.get(t.getAccountId());
                if (currency != null) {
                    addEdge(edges, currency, defaultCurrency, (double) t.getDefaultAmount() / t.getAmount());
                }
            }
        }

        // 3) categoryAmount vs amount
        for (Transaction t : allTransactions) {
            if (t.getCategoryId() != null && t.getCategoryAmount() != null && t.getAmount() != 0) {
                String accountCurrency = accountCurrencyById.get(t.getAccountId());
                String categoryCurrency = categoryCurrencyById.get(t.getCategoryId());
                if (accountCurrency != null && categoryCurrency != null) {
                    addEdge(edges, accountCurrency, categoryCurrency,
                            (double) t.getCategoryAmount() / t.getAmount());
                }
            }
        }

        // BFS from the default currency
        factorToDefault.put(defaultCurrency, 1.0);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(defaultCurrency);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            double currentFactor = factorToDefault.get(current);
            Map<String, Double> neighbours = edges.get(current);
            if (neighbours == null) continue;
            for (Map.Entry<String, Double> entry : neighbours.entrySet()) {
                String neighbour = entry.getKey();
                double rate = entry.getValue(); // 1 current == rate neighbour
                if (rate == 0) continue;
                if (!factorToDefault.containsKey(neighbour)) {
                    // 1 neighbour == (1/rate) current == (currentFactor / rate) default
                    factorToDefault.put(neighbour, currentFactor / rate);
                    queue.add(neighbour);
                }
            }
        }
    }

    private static void addEdge(Map<String, Map<String, Double>> edges, String from, String to, double rate) {
        if (from.equalsIgnoreCase(to) || rate <= 0 || Double.isInfinite(rate) || Double.isNaN(rate)) return;
        putEdge(edges, from, to, rate);
        putEdge(edges, to, from, 1.0 / rate);
    }

    private static void putEdge(Map<String, Map<String, Double>> edges, String from, String to, double rate) {
        Map<String, Double> neighbours = edges.get(from);
        if (neighbours == null) {
            neighbours = new HashMap<>();
            edges.put(from, neighbours);
        }
        // keep the first observed rate for stability
        if (!neighbours.containsKey(to)) {
            neighbours.put(to, rate);
        }
    }

    /**
     * Factor to multiply an amount expressed in {@code currency} by to obtain the default-currency
     * amount. Returns 1.0 when no conversion path could be derived from the data.
     */
    public double factorToDefault(String currency) {
        if (currency == null) return 1.0;
        Double factor = factorToDefault.get(currency);
        return factor != null ? factor : 1.0;
    }

    public boolean hasRateFor(String currency) {
        return currency != null && factorToDefault.containsKey(currency);
    }
}
