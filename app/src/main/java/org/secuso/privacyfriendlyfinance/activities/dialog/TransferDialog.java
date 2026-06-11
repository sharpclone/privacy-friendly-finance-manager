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

package org.secuso.privacyfriendlyfinance.activities.dialog;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import org.joda.time.LocalDate;
import org.secuso.privacyfriendlyfinance.R;
import org.secuso.privacyfriendlyfinance.activities.helper.CurrencyInputFilter;
import org.secuso.privacyfriendlyfinance.domain.FinanceDatabase;
import org.secuso.privacyfriendlyfinance.domain.model.Account;
import org.secuso.privacyfriendlyfinance.domain.model.Transaction;
import org.secuso.privacyfriendlyfinance.helpers.CurrencyHelper;
import org.secuso.privacyfriendlyfinance.helpers.SharedPreferencesManager;

import java.util.List;

/**
 * Dialog to create a transfer between two accounts. A transfer is stored as two linked
 * {@link Transaction} rows (one outgoing, one incoming) that share the same transferId.
 *
 * <p>When the two accounts use different currencies the default workflow asks for the amount
 * taken from the source account and the amount received into the destination account, so the
 * user never has to look up an exchange rate. Entering a rate is offered as an alternative.</p>
 */
public class TransferDialog extends AppCompatDialogFragment {

    private Spinner spinnerFrom;
    private Spinner spinnerTo;
    private TextView labelSent;
    private EditText editSent;
    private LinearLayout layoutReceived;
    private TextView labelReceived;
    private EditText editReceived;
    private CheckBox checkUseRate;
    private LinearLayout layoutRate;
    private TextView labelRate;
    private EditText editRate;
    private TextView dateView;
    private EditText editName;

    private List<Account> accounts;
    private LocalDate date = LocalDate.now();
    private AlertDialog dialog;

    public static void showTransferDialog(FragmentManager fragmentManager) {
        new TransferDialog().show(fragmentManager, "TransferDialog");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.transfer_title);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_transfer, null, false);
        builder.setView(view);

        spinnerFrom = view.findViewById(R.id.spinner_from);
        spinnerTo = view.findViewById(R.id.spinner_to);
        labelSent = view.findViewById(R.id.label_sent);
        editSent = view.findViewById(R.id.edit_sent);
        layoutReceived = view.findViewById(R.id.layout_received);
        labelReceived = view.findViewById(R.id.label_received);
        editReceived = view.findViewById(R.id.edit_received);
        checkUseRate = view.findViewById(R.id.check_use_rate);
        layoutRate = view.findViewById(R.id.layout_rate);
        labelRate = view.findViewById(R.id.label_rate);
        editRate = view.findViewById(R.id.edit_rate);
        dateView = view.findViewById(R.id.transfer_date);
        editName = view.findViewById(R.id.edit_transfer_name);

        editSent.setFilters(new InputFilter[]{new CurrencyInputFilter()});
        editReceived.setFilters(new InputFilter[]{new CurrencyInputFilter()});

        dateView.setText(date.toString());
        dateView.setOnClickListener(v -> openDatePicker());

        AdapterView.OnItemSelectedListener selectionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                refreshCurrencyUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spinnerFrom.setOnItemSelectedListener(selectionListener);
        spinnerTo.setOnItemSelectedListener(selectionListener);

        checkUseRate.setOnCheckedChangeListener((b, checked) -> refreshCurrencyUi());

        // Keep the received amount in sync while the user types a rate.
        TextWatcher rateWatcher = new SimpleTextWatcher(this::updateReceivedFromRate);
        editRate.addTextChangedListener(rateWatcher);
        editSent.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (checkUseRate.isChecked()) updateReceivedFromRate();
        }));

        loadAccounts();

        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.submit, null); // overridden below to avoid auto-dismiss on validation error

        dialog = builder.create();
        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit()));
        return dialog;
    }

    private void loadAccounts() {
        FinanceDatabase.getInstance(getActivity().getApplication()).accountDao().getAll()
                .observe(this, accountList -> {
                    accounts = accountList;
                    ArrayAdapter<Account> fromAdapter = new ArrayAdapter<>(getActivity(),
                            R.layout.support_simple_spinner_dropdown_item, accountList);
                    ArrayAdapter<Account> toAdapter = new ArrayAdapter<>(getActivity(),
                            R.layout.support_simple_spinner_dropdown_item, accountList);
                    spinnerFrom.setAdapter(fromAdapter);
                    spinnerTo.setAdapter(toAdapter);
                    if (accountList.size() > 1) {
                        spinnerTo.setSelection(1);
                    }
                    refreshCurrencyUi();
                });
    }

    private Account selectedFrom() {
        if (accounts == null || spinnerFrom.getSelectedItemPosition() < 0) return null;
        return (Account) spinnerFrom.getSelectedItem();
    }

    private Account selectedTo() {
        if (accounts == null || spinnerTo.getSelectedItemPosition() < 0) return null;
        return (Account) spinnerTo.getSelectedItem();
    }

    private String currencyOf(Account account) {
        if (account == null || account.getId() == null) {
            return SharedPreferencesManager.get(getActivity()).getDefaultCurrencyCode();
        }
        return SharedPreferencesManager.get(getActivity()).getAccountCurrencyCode(account.getId());
    }

    private void refreshCurrencyUi() {
        Account from = selectedFrom();
        Account to = selectedTo();
        if (from == null || to == null) return;

        String fromCur = currencyOf(from);
        String toCur = currencyOf(to);
        labelSent.setText(getString(R.string.transfer_amount_sent, from.toString(), fromCur));

        boolean differentCurrency = !fromCur.equalsIgnoreCase(toCur);
        checkUseRate.setVisibility(differentCurrency ? View.VISIBLE : View.GONE);

        if (!differentCurrency) {
            layoutReceived.setVisibility(View.GONE);
            layoutRate.setVisibility(View.GONE);
            return;
        }

        labelReceived.setText(getString(R.string.transfer_amount_received, to.toString(), toCur));
        labelRate.setText(getString(R.string.transfer_rate_label, fromCur, toCur));

        boolean useRate = checkUseRate.isChecked();
        layoutReceived.setVisibility(useRate ? View.GONE : View.VISIBLE);
        layoutRate.setVisibility(useRate ? View.VISIBLE : View.GONE);
        if (useRate) updateReceivedFromRate();
    }

    private void updateReceivedFromRate() {
        Long sent = CurrencyHelper.convertToLong(editSent.getText().toString());
        Double rate = parseDouble(editRate.getText().toString());
        if (sent == null || rate == null) return;
        long received = Math.round(Math.abs(sent) * rate);
        editReceived.setText(CurrencyHelper.convertToString(received));
    }

    private void openDatePicker() {
        new DatePickerDialog(getContext(),
                (picker, year, month, dayOfMonth) -> {
                    date = new LocalDate(year, month + 1, dayOfMonth);
                    dateView.setText(date.toString());
                },
                date.getYear(), date.getMonthOfYear() - 1, date.getDayOfMonth()).show();
    }

    private void submit() {
        Account from = selectedFrom();
        Account to = selectedTo();
        if (from == null || to == null || from.getId() == null || to.getId() == null) {
            Toast.makeText(getActivity(), R.string.transfer_error_no_accounts, Toast.LENGTH_SHORT).show();
            return;
        }
        if (from.getId().equals(to.getId())) {
            Toast.makeText(getActivity(), R.string.transfer_error_same_account, Toast.LENGTH_SHORT).show();
            return;
        }

        Long sentRaw = CurrencyHelper.convertToLong(editSent.getText().toString());
        if (sentRaw == null || sentRaw == 0) {
            Toast.makeText(getActivity(), R.string.transfer_error_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        long sent = Math.abs(sentRaw);

        String fromCur = currencyOf(from);
        String toCur = currencyOf(to);
        long received;
        if (fromCur.equalsIgnoreCase(toCur)) {
            received = sent;
        } else if (checkUseRate.isChecked()) {
            Double rate = parseDouble(editRate.getText().toString());
            if (rate == null || rate <= 0) {
                Toast.makeText(getActivity(), R.string.transfer_error_amount, Toast.LENGTH_SHORT).show();
                return;
            }
            received = Math.round(sent * rate);
        } else {
            Long receivedRaw = CurrencyHelper.convertToLong(editReceived.getText().toString());
            if (receivedRaw == null || receivedRaw == 0) {
                Toast.makeText(getActivity(), R.string.transfer_error_amount, Toast.LENGTH_SHORT).show();
                return;
            }
            received = Math.abs(receivedRaw);
        }

        String name = editName.getText().toString().trim();
        if (name.isEmpty()) {
            name = getString(R.string.transfer_default_name);
        }

        String defaultCurrency = SharedPreferencesManager.get(getActivity()).getDefaultCurrencyCode();
        long transferGroupId = System.currentTimeMillis();

        Transaction outgoing = new Transaction(name, -sent, date, from.getId());
        outgoing.setTransferId(transferGroupId);
        outgoing.setCategoryAmount(-sent);
        if (defaultCurrency.equalsIgnoreCase(fromCur)) outgoing.setDefaultAmount(-sent);

        Transaction incoming = new Transaction(name, received, date, to.getId());
        incoming.setTransferId(transferGroupId);
        incoming.setCategoryAmount(received);
        if (defaultCurrency.equalsIgnoreCase(toCur)) incoming.setDefaultAmount(received);

        FinanceDatabase db = FinanceDatabase.getInstance(getActivity().getApplication());
        new Thread(() -> {
            db.transactionDao().insert(outgoing);
            db.transactionDao().insert(incoming);
        }).start();

        Toast.makeText(getActivity(), R.string.transfer_created, Toast.LENGTH_SHORT).show();
        dialog.dismiss();
    }

    private static Double parseDouble(String text) {
        if (text == null) return null;
        try {
            return Double.parseDouble(text.replace(',', '.').trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChanged.run();
        }
    }
}
