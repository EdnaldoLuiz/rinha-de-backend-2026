package com.expedit.rinha2026.vectorizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.MccRiskTable;
import com.expedit.rinha2026.domain.NormalizationConfig;
import com.expedit.rinha2026.domain.QueryVector;
import com.expedit.rinha2026.infra.config.MccRiskTableLoader;
import com.expedit.rinha2026.parser.PayloadParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FraudVectorizerTest {
    @Test
    void shouldBuildVectorAndPreserveSentinelForMissingLastTransaction() {
        NormalizationConfig cfg = new NormalizationConfig();
        MccRiskTable mcc = new MccRiskTable(Map.of(5411, 0.2f), 0.5f);
        FraudVectorizer vectorizer = new FraudVectorizer(cfg, mcc);

        FraudRequestFields req = new FraudRequestFields();
        req.amount = 100d;
        req.installments = 2;
        req.requestedYear = 2026;
        req.requestedMonth = 1;
        req.requestedDay = 12;
        req.requestedHour = 15;
        req.requestedMinute = 30;
        req.customerAvgAmount = 50d;
        req.customerTxCount24h = 10;
        req.merchantId = 16;
        req.knownMerchantIds[0] = 16;
        req.knownMerchantCount = 1;
        req.merchantMcc = 5411;
        req.merchantAvgAmount = 200d;
        req.terminalIsOnline = true;
        req.terminalCardPresent = false;
        req.terminalKmFromHome = 20d;
        req.hasLastTransaction = false;

        QueryVector q = new QueryVector();
        vectorizer.vectorize(req, q);

        assertEquals((short) -Constants.VECTOR_SCALE, q.values[5]);
        assertEquals((short) -Constants.VECTOR_SCALE, q.values[6]);
        assertEquals((short) 0, q.values[11]); // known merchant
        assertEquals((short) 2000, q.values[12]); // 0.2
    }

    @Test
    void shouldMatchOfficialLegitExampleVector() {
        String payload = """
            {
              "id": "tx-1329056812",
              "transaction": { "amount": 41.12, "installments": 2, "requested_at": "2026-03-11T18:45:53Z" },
              "customer": { "avg_amount": 82.24, "tx_count_24h": 3, "known_merchants": ["MERC-003", "MERC-016"] },
              "merchant": { "id": "MERC-016", "mcc": "5411", "avg_amount": 60.25 },
              "terminal": { "is_online": false, "card_present": true, "km_from_home": 29.2331036248 },
              "last_transaction": null
            }
            """;
        String mccRisk = """
            {
              "5411": 0.15,
              "5812": 0.30,
              "5912": 0.20,
              "5944": 0.45,
              "7801": 0.80,
              "7802": 0.75,
              "7995": 0.85,
              "4511": 0.35,
              "5311": 0.25,
              "5999": 0.50
            }
            """;

        FraudRequestFields req = new PayloadParser().parse(payload);
        FraudVectorizer vectorizer = new FraudVectorizer(
            new NormalizationConfig(),
            new MccRiskTableLoader().load(mccRisk)
        );

        QueryVector q = new QueryVector();
        vectorizer.vectorize(req, q);

        assertEquals((short) 41, q.values[0]);     // 0.0041
        assertEquals((short) 1667, q.values[1]);   // 0.1667
        assertEquals((short) 500, q.values[2]);    // 0.05
        assertEquals((short) 7826, q.values[3]);   // 0.7826
        assertEquals((short) 3333, q.values[4]);   // 0.3333
        assertEquals((short) -10000, q.values[5]);
        assertEquals((short) -10000, q.values[6]);
        assertEquals((short) 292, q.values[7]);    // 0.0292
        assertEquals((short) 1500, q.values[8]);   // 0.15
        assertEquals((short) 0, q.values[9]);
        assertEquals((short) 10000, q.values[10]);
        assertEquals((short) 0, q.values[11]);
        assertEquals((short) 1500, q.values[12]);  // 0.15
        assertEquals((short) 60, q.values[13]);    // 0.006
    }
}
