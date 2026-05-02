package com.expedit.rinha2026.domain;

public final class TopKState {
    public float d1 = Float.POSITIVE_INFINITY;
    public float d2 = Float.POSITIVE_INFINITY;
    public float d3 = Float.POSITIVE_INFINITY;
    public float d4 = Float.POSITIVE_INFINITY;
    public float d5 = Float.POSITIVE_INFINITY;

    public byte l1;
    public byte l2;
    public byte l3;
    public byte l4;
    public byte l5;

    public void reset() {
        d1 = d2 = d3 = d4 = d5 = Float.POSITIVE_INFINITY;
        l1 = l2 = l3 = l4 = l5 = 0;
    }
}
