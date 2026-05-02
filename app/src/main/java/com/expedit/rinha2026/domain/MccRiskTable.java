package com.expedit.rinha2026.domain;

import java.util.Arrays;
import java.util.Map;

public final class MccRiskTable {
    private final float[] riskByMcc;
    private final float defaultRisk;

    public MccRiskTable(Map<Integer, Float> riskByMcc, float defaultRisk) {
        this.defaultRisk = defaultRisk;
        this.riskByMcc = buildLookup(riskByMcc, defaultRisk);
    }

    public float lookupOrDefault(int mcc) {
        if (mcc < 0 || mcc >= riskByMcc.length) {
            return defaultRisk;
        }
        return riskByMcc[mcc];
    }

    public static MccRiskTable defaultTable() {
        return new MccRiskTable(Map.of(), 0.5f);
    }

    private static float[] buildLookup(Map<Integer, Float> configuredRisks, float defaultRisk) {
        int maxMcc = 0;
        for (Integer mcc : configuredRisks.keySet()) {
            if (mcc != null && mcc > maxMcc) {
                maxMcc = mcc;
            }
        }

        float[] lookup = new float[maxMcc + 1];
        Arrays.fill(lookup, defaultRisk);
        for (Map.Entry<Integer, Float> entry : configuredRisks.entrySet()) {
            Integer mcc = entry.getKey();
            if (mcc != null && mcc >= 0) {
                lookup[mcc] = entry.getValue();
            }
        }
        return lookup;
    }
}
