package com.expedit.rinha2026.fallback;

import com.expedit.rinha2026.domain.FraudRequestFields;

public final class FallbackScorer {
    public FallbackDecision score(FraudRequestFields req) {
        int points = 0;

        if (req.customerAvgAmount > 0d && req.amount / req.customerAvgAmount > 3.0d) {
            points++;
        }
        if (req.terminalKmFromHome > 100d) {
            points++;
        }
        if (req.customerTxCount24h > 30) {
            points++;
        }
        if (!req.terminalCardPresent && req.terminalIsOnline) {
            points++;
        }

        int fraudCount = Math.min(points, 5);
        return new FallbackDecision(fraudCount);
    }
}
