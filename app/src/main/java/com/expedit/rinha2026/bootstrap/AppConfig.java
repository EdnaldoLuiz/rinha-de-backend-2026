package com.expedit.rinha2026.bootstrap;

public record AppConfig(
    int port,
    String normalizationLocation,
    String mccRiskLocation,
    String referencesLocation,
    String binaryIndexLocation,
    int serverThreads
) {
    public static AppConfig fromEnv() {
        String rawPort = System.getenv().getOrDefault("PORT", "9999");
        String rawServerThreads = System.getenv().getOrDefault("SERVER_THREADS", "1");
        String normalization = System.getenv().getOrDefault("NORMALIZATION_FILE", "classpath:normalization.json");
        String mccRisk = System.getenv().getOrDefault("MCC_RISK_FILE", "classpath:mcc_risk.json");
        String references = System.getenv().getOrDefault("REFERENCES_FILE", "classpath:references.json");
        String binaryIndex = System.getenv().getOrDefault("INDEX_BINARY_FILE", "");
        return new AppConfig(
            Integer.parseInt(rawPort),
            normalization,
            mccRisk,
            references,
            binaryIndex,
            Math.max(1, Integer.parseInt(rawServerThreads))
        );
    }
}
