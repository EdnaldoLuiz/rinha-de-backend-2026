package com.expedit.rinha2026.parser;

import com.expedit.rinha2026.domain.FraudRequestFields;

public final class BytePayloadParser {
    private static final byte[] KEY_TRANSACTION = ascii("\"transaction\"");
    private static final byte[] KEY_AMOUNT = ascii("\"amount\"");
    private static final byte[] KEY_INSTALLMENTS = ascii("\"installments\"");
    private static final byte[] KEY_REQUESTED_AT = ascii("\"requested_at\"");
    private static final byte[] KEY_CUSTOMER = ascii("\"customer\"");
    private static final byte[] KEY_AVG_AMOUNT = ascii("\"avg_amount\"");
    private static final byte[] KEY_TX_COUNT_24H = ascii("\"tx_count_24h\"");
    private static final byte[] KEY_KNOWN_MERCHANTS = ascii("\"known_merchants\"");
    private static final byte[] KEY_MERCHANT = ascii("\"merchant\"");
    private static final byte[] KEY_ID = ascii("\"id\"");
    private static final byte[] KEY_MCC = ascii("\"mcc\"");
    private static final byte[] KEY_TERMINAL = ascii("\"terminal\"");
    private static final byte[] KEY_IS_ONLINE = ascii("\"is_online\"");
    private static final byte[] KEY_CARD_PRESENT = ascii("\"card_present\"");
    private static final byte[] KEY_KM_FROM_HOME = ascii("\"km_from_home\"");
    private static final byte[] KEY_LAST_TRANSACTION = ascii("\"last_transaction\"");
    private static final byte[] KEY_TIMESTAMP = ascii("\"timestamp\"");
    private static final byte[] KEY_KM_FROM_CURRENT = ascii("\"km_from_current\"");
    private static final byte[] VALUE_TRUE = ascii("true");
    private static final byte[] VALUE_FALSE = ascii("false");
    private static final byte[] VALUE_NULL = ascii("null");

    public FraudRequestFields parse(byte[] json, int length) {
        FraudRequestFields req = new FraudRequestFields();

        int transactionIdx = findRequired(json, length, KEY_TRANSACTION, 0);
        req.amount = parseDoubleByKey(json, length, KEY_AMOUNT, transactionIdx);
        req.installments = parseIntByKey(json, length, KEY_INSTALLMENTS, transactionIdx);
        parseTimestampByKey(json, length, KEY_REQUESTED_AT, transactionIdx, req, true);

        int customerIdx = findRequired(json, length, KEY_CUSTOMER, 0);
        req.customerAvgAmount = parseDoubleByKey(json, length, KEY_AVG_AMOUNT, customerIdx);
        req.customerTxCount24h = parseIntByKey(json, length, KEY_TX_COUNT_24H, customerIdx);
        parseKnownMerchants(json, length, customerIdx, req);

        int merchantIdx = findRequired(json, length, KEY_MERCHANT, 0);
        req.merchantId = parseMerchantIdByKey(json, length, KEY_ID, merchantIdx);
        req.merchantMcc = parseQuotedIntByKey(json, length, KEY_MCC, merchantIdx);
        req.merchantAvgAmount = parseDoubleByKey(json, length, KEY_AVG_AMOUNT, merchantIdx);

        int terminalIdx = findRequired(json, length, KEY_TERMINAL, 0);
        req.terminalIsOnline = parseBooleanByKey(json, length, KEY_IS_ONLINE, terminalIdx);
        req.terminalCardPresent = parseBooleanByKey(json, length, KEY_CARD_PRESENT, terminalIdx);
        req.terminalKmFromHome = parseDoubleByKey(json, length, KEY_KM_FROM_HOME, terminalIdx);

        parseLastTransaction(json, length, req);

        return req;
    }

    private void parseKnownMerchants(byte[] json, int length, int customerIdx, FraudRequestFields req) {
        int keyIdx = findOptional(json, length, KEY_KNOWN_MERCHANTS, customerIdx);
        if (keyIdx < 0) {
            return;
        }

        int valueStart = findValueStart(json, length, keyIdx);
        if (valueStart >= length || json[valueStart] != '[') {
            throw invalidPayload();
        }

        int idx = valueStart + 1;
        while (idx < length) {
            idx = skipWs(json, length, idx);
            if (idx >= length) {
                break;
            }

            byte c = json[idx];
            if (c == ']') {
                return;
            }
            if (c == ',') {
                idx++;
                continue;
            }
            if (c != '"') {
                throw invalidPayload();
            }

            int tokenStart = idx + 1;
            int tokenEnd = findClosingQuote(json, length, tokenStart);
            if (req.knownMerchantCount < req.knownMerchantIds.length) {
                req.knownMerchantIds[req.knownMerchantCount++] =
                    parseMerchantIdFromRange(json, tokenStart, tokenEnd);
            }
            idx = tokenEnd + 1;
        }
    }

    private void parseLastTransaction(byte[] json, int length, FraudRequestFields req) {
        int keyIdx = findOptional(json, length, KEY_LAST_TRANSACTION, 0);
        if (keyIdx < 0) {
            req.hasLastTransaction = false;
            return;
        }

        int valueStart = findValueStart(json, length, keyIdx);
        if (startsWith(json, length, valueStart, VALUE_NULL)) {
            req.hasLastTransaction = false;
            return;
        }

        req.hasLastTransaction = true;
        parseTimestampByKey(json, length, KEY_TIMESTAMP, keyIdx, req, false);
        req.lastKmFromCurrent = parseDoubleByKey(json, length, KEY_KM_FROM_CURRENT, keyIdx);
    }

    private double parseDoubleByKey(byte[] json, int length, byte[] key, int fromIndex) {
        int keyIdx = findRequired(json, length, key, fromIndex);
        int start = findValueStart(json, length, keyIdx);
        int end = findValueEnd(json, length, start);
        return parseDoubleFromRange(json, start, end);
    }

    private int parseIntByKey(byte[] json, int length, byte[] key, int fromIndex) {
        int keyIdx = findRequired(json, length, key, fromIndex);
        int start = findValueStart(json, length, keyIdx);
        int end = findValueEnd(json, length, start);
        return parseIntFromRange(json, start, end);
    }

    private int parseQuotedIntByKey(byte[] json, int length, byte[] key, int fromIndex) {
        int keyIdx = findRequired(json, length, key, fromIndex);
        int start = findQuotedValueStart(json, length, keyIdx);
        int end = findClosingQuote(json, length, start);
        return parseIntFromRange(json, start, end);
    }

    private int parseMerchantIdByKey(byte[] json, int length, byte[] key, int fromIndex) {
        int keyIdx = findRequired(json, length, key, fromIndex);
        int start = findQuotedValueStart(json, length, keyIdx);
        int end = findClosingQuote(json, length, start);
        return parseMerchantIdFromRange(json, start, end);
    }

    private boolean parseBooleanByKey(byte[] json, int length, byte[] key, int fromIndex) {
        int keyIdx = findRequired(json, length, key, fromIndex);
        int start = findValueStart(json, length, keyIdx);

        if (startsWith(json, length, start, VALUE_TRUE)) {
            return true;
        }
        if (startsWith(json, length, start, VALUE_FALSE)) {
            return false;
        }
        throw invalidPayload();
    }

    private void parseTimestampByKey(
        byte[] json,
        int length,
        byte[] key,
        int fromIndex,
        FraudRequestFields req,
        boolean requested
    ) {
        int keyIdx = findRequired(json, length, key, fromIndex);
        int start = findQuotedValueStart(json, length, keyIdx);
        int end = findClosingQuote(json, length, start);

        if (end - start < 19) {
            throw invalidPayload();
        }

        int year = parseFourDigits(json, start);
        int month = parseTwoDigits(json, start + 5);
        int day = parseTwoDigits(json, start + 8);
        int hour = parseTwoDigits(json, start + 11);
        int minute = parseTwoDigits(json, start + 14);
        int second = parseTwoDigits(json, start + 17);

        if (requested) {
            req.requestedYear = year;
            req.requestedMonth = month;
            req.requestedDay = day;
            req.requestedHour = hour;
            req.requestedMinute = minute;
            req.requestedSecond = second;
        } else {
            req.lastYear = year;
            req.lastMonth = month;
            req.lastDay = day;
            req.lastHour = hour;
            req.lastMinute = minute;
            req.lastSecond = second;
        }
    }

    private int findRequired(byte[] json, int length, byte[] key, int fromIndex) {
        int idx = findOptional(json, length, key, fromIndex);
        if (idx < 0) {
            throw invalidPayload();
        }
        return idx;
    }

    private int findOptional(byte[] json, int length, byte[] key, int fromIndex) {
        int max = length - key.length;

        outer:
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            if (json[i] != key[0]) {
                continue;
            }
            for (int j = 1; j < key.length; j++) {
                if (json[i + j] != key[j]) {
                    continue outer;
                }
            }
            return i;
        }

        return -1;
    }

    private int findValueStart(byte[] json, int length, int keyIdx) {
        int colon = -1;
        for (int i = keyIdx; i < length; i++) {
            if (json[i] == ':') {
                colon = i;
                break;
            }
        }
        if (colon < 0) {
            throw invalidPayload();
        }
        return skipWs(json, length, colon + 1);
    }

    private int findQuotedValueStart(byte[] json, int length, int keyIdx) {
        int valueStart = findValueStart(json, length, keyIdx);
        if (valueStart >= length || json[valueStart] != '"') {
            throw invalidPayload();
        }
        return valueStart + 1;
    }

    private int findClosingQuote(byte[] json, int length, int start) {
        for (int i = start; i < length; i++) {
            if (json[i] == '"') {
                return i;
            }
        }
        throw invalidPayload();
    }

    private int findValueEnd(byte[] json, int length, int start) {
        int i = start;
        while (i < length) {
            byte c = json[i];
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                break;
            }
            i++;
        }
        return i;
    }

    private int skipWs(byte[] json, int length, int index) {
        int i = index;
        while (i < length) {
            byte c = json[i];
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return i;
            }
            i++;
        }
        return i;
    }

    private boolean startsWith(byte[] json, int length, int start, byte[] value) {
        if (start < 0 || start + value.length > length) {
            return false;
        }
        for (int i = 0; i < value.length; i++) {
            if (json[start + i] != value[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String value) {
        byte[] out = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            out[i] = (byte) value.charAt(i);
        }
        return out;
    }

    private int parseTwoDigits(byte[] json, int start) {
        return (parseDigit(json[start]) * 10) + parseDigit(json[start + 1]);
    }

    private int parseFourDigits(byte[] json, int start) {
        return (parseDigit(json[start]) * 1000)
            + (parseDigit(json[start + 1]) * 100)
            + (parseDigit(json[start + 2]) * 10)
            + parseDigit(json[start + 3]);
    }

    private int parseDigit(byte b) {
        if (b < '0' || b > '9') {
            throw invalidPayload();
        }
        return b - '0';
    }

    private int parseMerchantIdFromRange(byte[] json, int start, int end) {
        int separator = -1;
        for (int i = start; i < end; i++) {
            if (json[i] == '-') {
                separator = i;
                break;
            }
        }
        if (separator < 0 || separator + 1 >= end) {
            return 0;
        }
        return parseIntFromRange(json, separator + 1, end);
    }

    private int parseIntFromRange(byte[] json, int start, int end) {
        int i = start;
        boolean negative = false;

        if (i < end && (json[i] == '+' || json[i] == '-')) {
            negative = json[i] == '-';
            i++;
        }

        int value = 0;
        while (i < end) {
            byte c = json[i];
            if (c < '0' || c > '9') {
                throw invalidPayload();
            }
            value = (value * 10) + (c - '0');
            i++;
        }

        return negative ? -value : value;
    }

    private double parseDoubleFromRange(byte[] json, int start, int end) {
        int i = start;
        boolean negative = false;

        if (i < end && (json[i] == '+' || json[i] == '-')) {
            negative = json[i] == '-';
            i++;
        }

        double value = 0d;
        while (i < end) {
            byte c = json[i];
            if (c < '0' || c > '9') {
                break;
            }
            value = (value * 10d) + (c - '0');
            i++;
        }

        if (i < end && json[i] == '.') {
            i++;
            double scale = 1d;
            while (i < end) {
                byte c = json[i];
                if (c < '0' || c > '9') {
                    break;
                }
                scale *= 10d;
                value += (c - '0') / scale;
                i++;
            }
        }

        if (i < end && (json[i] == 'e' || json[i] == 'E')) {
            i++;
            boolean negativeExp = false;
            if (i < end && (json[i] == '+' || json[i] == '-')) {
                negativeExp = json[i] == '-';
                i++;
            }

            int exponent = 0;
            while (i < end) {
                byte c = json[i];
                if (c < '0' || c > '9') {
                    break;
                }
                exponent = (exponent * 10) + (c - '0');
                i++;
            }

            value *= Math.pow(10d, negativeExp ? -exponent : exponent);
        }

        if (i != end) {
            throw invalidPayload();
        }

        return negative ? -value : value;
    }

    private IllegalArgumentException invalidPayload() {
        return new IllegalArgumentException("invalid payload");
    }
}
