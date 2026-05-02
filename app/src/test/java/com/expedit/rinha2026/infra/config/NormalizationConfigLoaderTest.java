package com.expedit.rinha2026.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.NormalizationConfig;
import org.junit.jupiter.api.Test;

class NormalizationConfigLoaderTest {
    @Test
    void shouldReadOfficialNormalizationKeys() {
        String json = """
            {
              "max_amount": 10000,
              "max_installments": 12,
              "amount_vs_avg_ratio": 10,
              "max_minutes": 1440,
              "max_km": 1000,
              "max_tx_count_24h": 20,
              "max_merchant_avg_amount": 10000
            }
            """;

        NormalizationConfig cfg = new NormalizationConfigLoader().load(json);

        assertEquals(10000f, cfg.maxAmount);
        assertEquals(12f, cfg.maxInstallments);
        assertEquals(10f, cfg.maxAmountVsAvgRatio);
        assertEquals(1440f, cfg.maxMinutes);
        assertEquals(1000f, cfg.maxKm);
        assertEquals(20f, cfg.maxTxCount24h);
        assertEquals(10000f, cfg.maxMerchantAvgAmount);
    }
}
