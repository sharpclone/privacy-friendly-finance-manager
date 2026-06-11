package org.secuso.privacyfriendlyfinance.csv;

public class CsvDefinitions {
    public static final char CSV_FIELD_DELIMITER_CHAR = ';';
    public static final String COLUMN_NAME_NOTE = "note";
    public static final String COLUMN_NAME_AMOUNT = "amount";
    public static final String COLUMN_NAME_DATE = "date";
    public static final String COLUMN_NAME_CATEGORY = "category";
    public static final String COLUMN_NAME_ACCOUNT = "account";
    public static final String COLUMN_NAME_CURRENCY = "currency";
    public static final String[] CSV_HEADER_TRANSACTIONSSTRINGS = {COLUMN_NAME_DATE, COLUMN_NAME_AMOUNT, COLUMN_NAME_NOTE, COLUMN_NAME_CATEGORY, COLUMN_NAME_ACCOUNT, COLUMN_NAME_CURRENCY};

    /** Comment-line marker (starts with '#', so older importers skip it) holding the app default currency. */
    public static final String META_DEFAULT_CURRENCY = "#defaultCurrency";
}
