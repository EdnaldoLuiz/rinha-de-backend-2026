package com.expedit.rinha2026.parser;

import com.expedit.rinha2026.domain.FraudRequestFields;

public final class PayloadParser {
    private static final String KEY_TRANSACTION = "\"transaction\"";
    private static final String KEY_CUSTOMER = "\"customer\"";
    private static final String KEY_MERCHANT = "\"merchant\"";
    private static final String KEY_TERMINAL = "\"terminal\"";
    private static final String KEY_LAST_TRANSACTION = "\"last_transaction\"";

    private static final String KEY_AMOUNT = "\"amount\"";
    private static final String KEY_INSTALLMENTS = "\"installments\"";
    private static final String KEY_REQUESTED_AT = "\"requested_at\"";
    private static final String KEY_AVG_AMOUNT = "\"avg_amount\"";
    private static final String KEY_TX_COUNT_24H = "\"tx_count_24h\"";
    private static final String KEY_KNOWN_MERCHANTS = "\"known_merchants\"";
    private static final String KEY_ID = "\"id\"";
    private static final String KEY_MCC = "\"mcc\"";
    private static final String KEY_IS_ONLINE = "\"is_online\"";
    private static final String KEY_CARD_PRESENT = "\"card_present\"";
    private static final String KEY_KM_FROM_HOME = "\"km_from_home\"";
    private static final String KEY_TIMESTAMP = "\"timestamp\"";
    private static final String KEY_KM_FROM_CURRENT = "\"km_from_current\"";

    public FraudRequestFields parse(String json) {
        FraudRequestFields req = new FraudRequestFields();

        int transactionIdx = findRequiredKey(json, KEY_TRANSACTION, 0);
        req.amount = parseDoubleByKey(json, KEY_AMOUNT, transactionIdx);
        req.installments = parseIntByKey(json, KEY_INSTALLMENTS, transactionIdx);
        parseTimestampByKey(json, KEY_REQUESTED_AT, transactionIdx, req, true);

        int customerIdx = findRequiredKey(json, KEY_CUSTOMER, 0);
        req.customerAvgAmount = parseDoubleByKey(json, KEY_AVG_AMOUNT, customerIdx);
        req.customerTxCount24h = parseIntByKey(json, KEY_TX_COUNT_24H, customerIdx);
        parseKnownMerchants(json, customerIdx, req);

        int merchantIdx = findRequiredKey(json, KEY_MERCHANT, 0);
        req.merchantId = parseMerchantIdByKey(json, KEY_ID, merchantIdx);
        req.merchantMcc = parseQuotedIntByKey(json, KEY_MCC, merchantIdx);
        req.merchantAvgAmount = parseDoubleByKey(json, KEY_AVG_AMOUNT, merchantIdx);

        int terminalIdx = findRequiredKey(json, KEY_TERMINAL, 0);
        req.terminalIsOnline = parseBooleanByKey(json, KEY_IS_ONLINE, terminalIdx);
        req.terminalCardPresent = parseBooleanByKey(json, KEY_CARD_PRESENT, terminalIdx);
        req.terminalKmFromHome = parseDoubleByKey(json, KEY_KM_FROM_HOME, terminalIdx);

        parseLastTransaction(json, req);

        return req;
    }

    private void parseKnownMerchants(String json, int customerIdx, FraudRequestFields req) {
        int keyIdx = findOptionalKey(json, KEY_KNOWN_MERCHANTS, customerIdx);
        if (keyIdx < 0) {
            return;
        }

        int valueStart = findValueStart(json, keyIdx);
        if (valueStart >= json.length() || json.charAt(valueStart) != '[') {
            throw invalidPayload("known_merchants must be an array");
        }

        int idx = valueStart + 1;
        while (idx < json.length()) {
            idx = skipWs(json, idx);
            if (idx >= json.length()) {
                break;
            }

            char c = json.charAt(idx);
            if (c == ']') {
                return;
            }
            if (c == ',') {
                idx++;
                continue;
            }
            if (c != '"') {
                throw invalidPayload("known_merchants must contain strings");
            }

            int tokenStart = idx + 1;
            int tokenEnd = findClosingQuote(json, tokenStart);
            if (req.knownMerchantCount < req.knownMerchantIds.length) {
                req.knownMerchantIds[req.knownMerchantCount++] = parseMerchantIdFromRange(json, tokenStart, tokenEnd);
            }
            idx = tokenEnd + 1;
        }
    }

    private void parseLastTransaction(String json, FraudRequestFields req) {
        int keyIdx = findOptionalKey(json, KEY_LAST_TRANSACTION, 0);
        if (keyIdx < 0) {
            req.hasLastTransaction = false;
            return;
        }

        int valueStart = findValueStart(json, keyIdx);
        if (json.startsWith("null", valueStart)) {
            req.hasLastTransaction = false;
            return;
        }

        req.hasLastTransaction = true;
        parseTimestampByKey(json, KEY_TIMESTAMP, keyIdx, req, false);
        req.lastKmFromCurrent = parseDoubleByKey(json, KEY_KM_FROM_CURRENT, keyIdx);
    }

    private double parseDoubleByKey(String json, String quotedKey, int fromIndex) {
        int keyIdx = findRequiredKey(json, quotedKey, fromIndex);
        int start = findValueStart(json, keyIdx);
        int end = findValueEnd(json, start);
        return parseDoubleFromRange(json, start, end);
    }

    private int parseIntByKey(String json, String quotedKey, int fromIndex) {
        int keyIdx = findRequiredKey(json, quotedKey, fromIndex);
        int start = findValueStart(json, keyIdx);
        int end = findValueEnd(json, start);
        return parseIntFromRange(json, start, end);
    }

    private int parseQuotedIntByKey(String json, String quotedKey, int fromIndex) {
        int keyIdx = findRequiredKey(json, quotedKey, fromIndex);
        int tokenStart = findQuotedValueStart(json, keyIdx);
        int tokenEnd = findClosingQuote(json, tokenStart);
        return parseIntFromRange(json, tokenStart, tokenEnd);
    }

    private int parseMerchantIdByKey(String json, String quotedKey, int fromIndex) {
        int keyIdx = findRequiredKey(json, quotedKey, fromIndex);
        int tokenStart = findQuotedValueStart(json, keyIdx);
        int tokenEnd = findClosingQuote(json, tokenStart);
        return parseMerchantIdFromRange(json, tokenStart, tokenEnd);
    }

    private boolean parseBooleanByKey(String json, String quotedKey, int fromIndex) {
        int keyIdx = findRequiredKey(json, quotedKey, fromIndex);
        int start = findValueStart(json, keyIdx);
        if (json.startsWith("true", start)) {
            return true;
        }
        if (json.startsWith("false", start)) {
            return false;
        }
        throw invalidPayload("invalid boolean for key " + quotedKey);
    }

    private void parseTimestampByKey(String json, String quotedKey, int fromIndex, FraudRequestFields req, boolean requestedTimestamp) {
        int keyIdx = findRequiredKey(json, quotedKey, fromIndex);
        int start = findQuotedValueStart(json, keyIdx);
        int end = findClosingQuote(json, start);
        int length = end - start;
        if (length < 19) {
            throw invalidPayload("timestamp too short");
        }

        if (json.charAt(start + 4) != '-'
            || json.charAt(start + 7) != '-'
            || json.charAt(start + 10) != 'T'
            || json.charAt(start + 13) != ':'
            || json.charAt(start + 16) != ':') {
            throw invalidPayload("invalid timestamp format");
        }

        int year = parseFourDigits(json, start);
        int month = parseTwoDigits(json, start + 5);
        int day = parseTwoDigits(json, start + 8);
        int hour = parseTwoDigits(json, start + 11);
        int minute = parseTwoDigits(json, start + 14);
        int second = parseTwoDigits(json, start + 17);

        if (requestedTimestamp) {
            req.requestedYear = year;
            req.requestedMonth = month;
            req.requestedDay = day;
            req.requestedHour = hour;
            req.requestedMinute = minute;
            req.requestedSecond = second;
            return;
        }

        req.lastYear = year;
        req.lastMonth = month;
        req.lastDay = day;
        req.lastHour = hour;
        req.lastMinute = minute;
        req.lastSecond = second;
    }

    private int parseTwoDigits(String json, int start) {
        return (parseDigit(json, start) * 10) + parseDigit(json, start + 1);
    }

    private int parseFourDigits(String json, int start) {
        return (parseDigit(json, start) * 1000)
            + (parseDigit(json, start + 1) * 100)
            + (parseDigit(json, start + 2) * 10)
            + parseDigit(json, start + 3);
    }

    private int parseDigit(String json, int index) {
        if (index < 0 || index >= json.length()) {
            throw invalidPayload("unexpected end while parsing digit");
        }
        char c = json.charAt(index);
        if (c < '0' || c > '9') {
            throw invalidPayload("invalid digit in numeric field");
        }
        return c - '0';
    }

    private int parseMerchantIdFromRange(String json, int start, int end) {
        int separator = -1;
        for (int i = start; i < end; i++) {
            if (json.charAt(i) == '-') {
                separator = i;
                break;
            }
        }
        if (separator < 0 || separator + 1 >= end) {
            return 0;
        }
        return parseIntFromRange(json, separator + 1, end);
    }

    private int parseIntFromRange(String json, int start, int end) {
        if (start >= end) {
            throw invalidPayload("empty integer token");
        }

        int i = start;
        boolean negative = false;
        char sign = json.charAt(i);
        if (sign == '+' || sign == '-') {
            negative = sign == '-';
            i++;
        }

        if (i >= end) {
            throw invalidPayload("invalid integer token");
        }

        int value = 0;
        boolean hasDigits = false;
        while (i < end) {
            char c = json.charAt(i);
            if (c < '0' || c > '9') {
                throw invalidPayload("invalid integer token");
            }
            hasDigits = true;
            int digit = c - '0';
            if (value > (Integer.MAX_VALUE - digit) / 10) {
                throw invalidPayload("integer overflow");
            }
            value = (value * 10) + digit;
            i++;
        }

        if (!hasDigits) {
            throw invalidPayload("invalid integer token");
        }

        return negative ? -value : value;
    }

    private double parseDoubleFromRange(String json, int start, int end) {
        if (start >= end) {
            throw invalidPayload("empty decimal token");
        }

        int i = start;
        boolean negative = false;

        char first = json.charAt(i);
        if (first == '+' || first == '-') {
            negative = (first == '-');
            i++;
        }

        if (i >= end) {
            throw invalidPayload("invalid decimal token");
        }

        double value = 0d;
        boolean hasIntegralDigits = false;
        while (i < end) {
            char c = json.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            hasIntegralDigits = true;
            value = (value * 10d) + (c - '0');
            i++;
        }

        if (i < end && json.charAt(i) == '.') {
            i++;
            double fractionScale = 1d;
            boolean hasFractionDigits = false;
            while (i < end) {
                char c = json.charAt(i);
                if (c < '0' || c > '9') {
                    break;
                }
                hasFractionDigits = true;
                fractionScale *= 10d;
                value += (c - '0') / fractionScale;
                i++;
            }
            if (!hasIntegralDigits && !hasFractionDigits) {
                throw invalidPayload("invalid decimal token");
            }
        } else if (!hasIntegralDigits) {
            throw invalidPayload("invalid decimal token");
        }

        if (i < end && (json.charAt(i) == 'e' || json.charAt(i) == 'E')) {
            i++;
            if (i >= end) {
                throw invalidPayload("invalid decimal exponent");
            }

            boolean negativeExponent = false;
            char sign = json.charAt(i);
            if (sign == '+' || sign == '-') {
                negativeExponent = sign == '-';
                i++;
            }

            if (i >= end) {
                throw invalidPayload("invalid decimal exponent");
            }

            int exponent = 0;
            boolean hasExponentDigits = false;
            while (i < end) {
                char c = json.charAt(i);
                if (c < '0' || c > '9') {
                    break;
                }
                hasExponentDigits = true;
                exponent = (exponent * 10) + (c - '0');
                i++;
            }
            if (!hasExponentDigits) {
                throw invalidPayload("invalid decimal exponent");
            }
            if (negativeExponent) {
                exponent = -exponent;
            }
            value *= Math.pow(10d, exponent);
        }

        if (i != end) {
            throw invalidPayload("invalid decimal token");
        }

        return negative ? -value : value;
    }

    private int findRequiredKey(String json, String quotedKey, int fromIndex) {
        int idx = findOptionalKey(json, quotedKey, fromIndex);
        if (idx < 0) {
            throw invalidPayload("missing key " + quotedKey);
        }
        return idx;
    }

    private int findOptionalKey(String json, String quotedKey, int fromIndex) {
        return json.indexOf(quotedKey, Math.max(fromIndex, 0));
    }

    private int findQuotedValueStart(String json, int keyIdx) {
        int valueStart = findValueStart(json, keyIdx);
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            throw invalidPayload("expected quoted value");
        }
        return valueStart + 1;
    }

    private int findClosingQuote(String json, int start) {
        int end = json.indexOf('"', start);
        if (end < 0) {
            throw invalidPayload("unterminated quoted value");
        }
        return end;
    }

    private int findValueStart(String json, int keyIdx) {
        int colon = json.indexOf(':', keyIdx);
        if (colon < 0) {
            throw invalidPayload("missing ':' after key");
        }
        return skipWs(json, colon + 1);
    }

    private int findValueEnd(String json, int start) {
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']'
                || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                break;
            }
            i++;
        }
        return i;
    }

    private IllegalArgumentException invalidPayload(String message) {
        return new IllegalArgumentException(message);
    }

    private int skipWs(String json, int index) {
        int i = index;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return i;
            }
            i++;
        }
        return i;
    }
}
