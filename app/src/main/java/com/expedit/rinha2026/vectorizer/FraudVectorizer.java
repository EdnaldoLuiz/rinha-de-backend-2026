package com.expedit.rinha2026.vectorizer;

import com.expedit.rinha2026.domain.Constants;
import com.expedit.rinha2026.domain.FraudRequestFields;
import com.expedit.rinha2026.domain.MccRiskTable;
import com.expedit.rinha2026.domain.NormalizationConfig;
import com.expedit.rinha2026.domain.QueryVector;

public final class FraudVectorizer {
    private static final short ONE = (short) Constants.VECTOR_SCALE;
    private static final short ZERO = 0;
    private static final short NO_LAST_TX = (short) -Constants.VECTOR_SCALE;

    private final NormalizationConfig normalization;
    private final MccRiskTable mccRiskTable;

    public FraudVectorizer(NormalizationConfig normalization, MccRiskTable mccRiskTable) {
        this.normalization = normalization;
        this.mccRiskTable = mccRiskTable;
    }

    public void vectorize(FraudRequestFields req, QueryVector queryVector) {
        short[] q = queryVector.values;

        q[0] = quantize01((float) (req.amount / normalization.maxAmount));
        q[1] = quantize01((float) (req.installments / normalization.maxInstallments));

        double denom = req.customerAvgAmount <= 0d ? 1d : req.customerAvgAmount;
        q[2] = quantize01((float) ((req.amount / denom) / normalization.maxAmountVsAvgRatio));

        q[3] = quantize01(req.requestedHour / 23f);
        q[4] = quantize01(DayOfWeekCalculator.dayOfWeek(req.requestedYear, req.requestedMonth, req.requestedDay) / 6f);

        if (!req.hasLastTransaction) {
            q[5] = NO_LAST_TX;
            q[6] = NO_LAST_TX;
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
            q[5] = quantize01((float) (minutes / normalization.maxMinutes));
            q[6] = quantize01((float) (req.lastKmFromCurrent / normalization.maxKm));
        }

        q[7] = quantize01((float) (req.terminalKmFromHome / normalization.maxKm));
        q[8] = quantize01((float) (req.customerTxCount24h / normalization.maxTxCount24h));
        q[9] = req.terminalIsOnline ? ONE : ZERO;
        q[10] = req.terminalCardPresent ? ONE : ZERO;
        q[11] = isKnownMerchant(req.merchantId, req.knownMerchantIds, req.knownMerchantCount) ? ZERO : ONE;
        q[12] = quantize01(mccRiskTable.lookupOrDefault(req.merchantMcc));
        q[13] = quantize01((float) (req.merchantAvgAmount / normalization.maxMerchantAvgAmount));
    }

    private boolean isKnownMerchant(int merchantId, int[] knownMerchantIds, int size) {
        for (int i = 0; i < size; i++) {
            if (knownMerchantIds[i] == merchantId) {
                return true;
            }
        }
        return false;
    }

    private short quantize01(float value) {
        float clamped = Clamp.clamp01(value);
        int scaled = Math.round(clamped * Constants.VECTOR_SCALE);
        if (scaled < 0) {
            return 0;
        }
        if (scaled > Constants.VECTOR_SCALE) {
            return ONE;
        }
        return (short) scaled;
    }
}
