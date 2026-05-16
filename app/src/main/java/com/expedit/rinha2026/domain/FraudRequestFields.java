package com.expedit.rinha2026.domain;

public final class FraudRequestFields {
    public double amount;
    public int installments;

    public int requestedYear;
    public int requestedMonth;
    public int requestedDay;
    public int requestedHour;
    public int requestedMinute;
    public int requestedSecond;

    public double customerAvgAmount;
    public int customerTxCount24h;

    public int knownMerchantCount;
    public final int[] knownMerchantIds = new int[64];

    public int merchantId;
    public int merchantMcc;
    public double merchantAvgAmount;

    public boolean terminalIsOnline;
    public boolean terminalCardPresent;
    public double terminalKmFromHome;

    public boolean hasLastTransaction;
    public int lastYear;
    public int lastMonth;
    public int lastDay;
    public int lastHour;
    public int lastMinute;
    public int lastSecond;
    public double lastKmFromCurrent;
}
