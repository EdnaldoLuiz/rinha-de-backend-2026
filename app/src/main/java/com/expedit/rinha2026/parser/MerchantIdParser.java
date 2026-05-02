package com.expedit.rinha2026.parser;

public final class MerchantIdParser {
    private MerchantIdParser() {
    }

    public static int parse(String merchantId) {
        int idx = merchantId.indexOf('-');
        if (idx < 0 || idx + 1 >= merchantId.length()) {
            return 0;
        }
        return Integer.parseInt(merchantId.substring(idx + 1));
    }
}
