package com.expedit.rinha2026.infra.config;

import com.expedit.rinha2026.domain.MccRiskTable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MccRiskTableLoader {
    public MccRiskTable load(String json) {
        float defaultRisk = readDefaultRisk(json, 0.5f);
        Map<Integer, Float> map = new HashMap<>();

        Pattern pairPattern = Pattern.compile("\\\"(\\d{3,6})\\\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pairPattern.matcher(json);
        while (matcher.find()) {
            int mcc = Integer.parseInt(matcher.group(1));
            float risk = Float.parseFloat(matcher.group(2));
            map.put(mcc, risk);
        }

        return new MccRiskTable(map, defaultRisk);
    }

    private float readDefaultRisk(String json, float fallback) {
        Pattern pattern = Pattern.compile("\\\"default\\\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Float.parseFloat(matcher.group(1));
        }
        return fallback;
    }
}
