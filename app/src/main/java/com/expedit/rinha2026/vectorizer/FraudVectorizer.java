package com.expedit.rinha2026.vectorizer;

import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.MccRiskTable;
import com.expedit.rinha2026.domain.NormalizationConfig;
import com.expedit.rinha2026.domain.QueryVector;

public final class FraudVectorizer {
    private final NormalizationConfig normalization;
    private final MccRiskTable mccRiskTable;

    public FraudVectorizer(NormalizationConfig normalization, MccRiskTable mccRiskTable) {
        this.normalization = normalization;
        this.mccRiskTable = mccRiskTable;
    }

    public void vectorize(FraudRequestFields req, QueryVector queryVector) {
        float[] q = queryVector.values;

        q[0] = Clamp.clamp01((float) (req.amount / normalization.maxAmount));
        q[1] = Clamp.clamp01((float) (req.installments / normalization.maxInstallments));

        double denom = req.customerAvgAmount <= 0d ? 1d : req.customerAvgAmount;
        q[2] = Clamp.clamp01((float) ((req.amount / denom) / normalization.maxAmountVsAvgRatio));

        q[3] = Clamp.clamp01(req.requestedHour / 23f);
        q[4] = Clamp.clamp01(DayOfWeekCalculator.dayOfWeek(req.requestedYear, req.requestedMonth, req.requestedDay) / 6f);

        if (!req.hasLastTransaction) {
            q[5] = -1f;
            q[6] = -1f;
        } else {
            long minutes = MinutesSinceLastTxCalculator.minutesBetween(
                req.requestedYear,
                req.requestedMonth,
                req.requestedDay,
                req.requestedHour,
                req.requestedMinute,
                req.requestedSecond,
                req.lastYear,
                req.lastMonth,
                req.lastDay,
                req.lastHour,
                req.lastMinute,
                req.lastSecond
            );
            q[5] = Clamp.clamp01((float) (minutes / normalization.maxMinutes));
            q[6] = Clamp.clamp01((float) (req.lastKmFromCurrent / normalization.maxKm));
        }

        q[7] = Clamp.clamp01((float) (req.terminalKmFromHome / normalization.maxKm));
        q[8] = Clamp.clamp01((float) (req.customerTxCount24h / normalization.maxTxCount24h));
        q[9] = req.terminalIsOnline ? 1f : 0f;
        q[10] = req.terminalCardPresent ? 1f : 0f;
        q[11] = isKnownMerchant(req.merchantId, req.knownMerchantIds, req.knownMerchantCount) ? 0f : 1f;
        q[12] = Clamp.clamp01(mccRiskTable.lookupOrDefault(req.merchantMcc));
        q[13] = Clamp.clamp01((float) (req.merchantAvgAmount / normalization.maxMerchantAvgAmount));

        q[14] = 0f;
        q[15] = 0f;
    }

    private boolean isKnownMerchant(int merchantId, int[] knownMerchantIds, int size) {
        for (int i = 0; i < size; i++) {
            if (knownMerchantIds[i] == merchantId) {
                return true;
            }
        }
        return false;
    }
}
