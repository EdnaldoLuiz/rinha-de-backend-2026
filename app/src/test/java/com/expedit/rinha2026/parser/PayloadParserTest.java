package com.expedit.rinha2026.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.expedit.rinha2026.domain.FraudRequestFields;
import org.junit.jupiter.api.Test;

class PayloadParserTest {
    @Test
    void shouldParseMainFieldsWithSecondsAndLastTransaction() {
        String json = """
            {
              "transaction": {
                "id": "TX-001",
                "amount": 120.5,
                "installments": 2,
                "requested_at": "2026-01-10T12:30:45Z"
              },
              "customer": {
                "id": "CUST-001",
                "avg_amount": 80.0,
                "tx_count_24h": 3,
                "known_merchants": ["MERC-016", "MERC-002"]
              },
              "merchant": {
                "id": "MERC-016",
                "mcc": "5411",
                "avg_amount": 95.0
              },
              "terminal": {
                "id": "TERM-001",
                "is_online": true,
                "card_present": false,
                "km_from_home": 2.5
              },
              "last_transaction": {
                "id": "TX-000",
                "timestamp": "2026-01-10T11:00:15Z",
                "km_from_current": 1.25
              }
            }
            """;

        FraudRequestFields req = new PayloadParser().parse(json);

        assertEquals(120.5d, req.amount);
        assertEquals(2, req.installments);

        assertEquals(2026, req.requestedYear);
        assertEquals(1, req.requestedMonth);
        assertEquals(10, req.requestedDay);
        assertEquals(12, req.requestedHour);
        assertEquals(30, req.requestedMinute);
        assertEquals(45, req.requestedSecond);

        assertEquals(80.0d, req.customerAvgAmount);
        assertEquals(3, req.customerTxCount24h);
        assertEquals(2, req.knownMerchantCount);
        assertEquals(16, req.knownMerchantIds[0]);
        assertEquals(2, req.knownMerchantIds[1]);

        assertEquals(16, req.merchantId);
        assertEquals(5411, req.merchantMcc);
        assertEquals(95.0d, req.merchantAvgAmount);

        assertTrue(req.terminalIsOnline);
        assertFalse(req.terminalCardPresent);
        assertEquals(2.5d, req.terminalKmFromHome);

        assertTrue(req.hasLastTransaction);
        assertEquals(2026, req.lastYear);
        assertEquals(1, req.lastMonth);
        assertEquals(10, req.lastDay);
        assertEquals(11, req.lastHour);
        assertEquals(0, req.lastMinute);
        assertEquals(15, req.lastSecond);
        assertEquals(1.25d, req.lastKmFromCurrent);
    }

    @Test
    void shouldHandleNullLastTransactionAndEmptyKnownMerchants() {
        String json = """
            {
              "transaction": {
                "id": "TX-001",
                "amount": 1,
                "installments": 1,
                "requested_at": "2026-02-01T00:00:00Z"
              },
              "customer": {
                "id": "CUST-001",
                "avg_amount": 10.0,
                "tx_count_24h": 1,
                "known_merchants": []
              },
              "merchant": {
                "id": "MERC-001",
                "mcc": "5999",
                "avg_amount": 10.0
              },
              "terminal": {
                "id": "TERM-001",
                "is_online": false,
                "card_present": true,
                "km_from_home": 0
              },
              "last_transaction": null
            }
            """;

        FraudRequestFields req = new PayloadParser().parse(json);

        assertFalse(req.hasLastTransaction);
        assertEquals(0, req.knownMerchantCount);
        assertFalse(req.terminalIsOnline);
        assertTrue(req.terminalCardPresent);
        assertEquals(0, req.requestedSecond);
    }
}
