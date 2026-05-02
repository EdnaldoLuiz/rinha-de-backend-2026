package com.expedit.rinha2026.infra.config;

import com.expedit.rinha2026.domain.NormalizationConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NormalizationConfigLoader {
    public NormalizationConfig load(String json) {
        NormalizationConfig cfg = new NormalizationConfig();

        cfg.maxAmount = readFloat(json, "max_amount", cfg.maxAmount);
        cfg.maxInstallments = readFloat(json, "max_installments", cfg.maxInstallments);
        cfg.maxAmountVsAvgRatio = readFloat(json, "amount_vs_avg_ratio", cfg.maxAmountVsAvgRatio);
        cfg.maxMinutes = readFloat(json, "max_minutes", cfg.maxMinutes);
        cfg.maxKm = readFloat(json, "max_km", cfg.maxKm);
        cfg.maxTxCount24h = readFloat(json, "max_tx_count_24h", cfg.maxTxCount24h);
        cfg.maxMerchantAvgAmount = readFloat(json, "max_merchant_avg_amount", cfg.maxMerchantAvgAmount);

        return cfg;
    }

    private float readFloat(String json, String key, float defaultValue) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Float.parseFloat(matcher.group(1));
        }
        return defaultValue;
    }
}
