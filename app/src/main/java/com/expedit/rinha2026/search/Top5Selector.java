package com.expedit.rinha2026.search;

public final class Top5Selector {
    private float d1 = Float.POSITIVE_INFINITY;
    private float d2 = Float.POSITIVE_INFINITY;
    private float d3 = Float.POSITIVE_INFINITY;
    private float d4 = Float.POSITIVE_INFINITY;
    private float d5 = Float.POSITIVE_INFINITY;

    private byte l1;
    private byte l2;
    private byte l3;
    private byte l4;
    private byte l5;

    public void reset() {
        d1 = Float.POSITIVE_INFINITY;
        d2 = Float.POSITIVE_INFINITY;
        d3 = Float.POSITIVE_INFINITY;
        d4 = Float.POSITIVE_INFINITY;
        d5 = Float.POSITIVE_INFINITY;
        l1 = l2 = l3 = l4 = l5 = 0;
    }

    public float currentWorst() {
        return d5;
    }

    public void offer(float dist, byte label) {
        if (dist < d1) {
            d5 = d4;
            l5 = l4;
            d4 = d3;
            l4 = l3;
            d3 = d2;
            l3 = l2;
            d2 = d1;
            l2 = l1;
            d1 = dist;
            l1 = label;
            return;
        }
        if (dist < d2) {
            d5 = d4;
            l5 = l4;
            d4 = d3;
            l4 = l3;
            d3 = d2;
            l3 = l2;
            d2 = dist;
            l2 = label;
            return;
        }
        if (dist < d3) {
            d5 = d4;
            l5 = l4;
            d4 = d3;
            l4 = l3;
            d3 = dist;
            l3 = label;
            return;
        }
        if (dist < d4) {
            d5 = d4;
            l5 = l4;
            d4 = dist;
            l4 = label;
            return;
        }
        if (dist < d5) {
            d5 = dist;
            l5 = label;
        }
    }

    public int fraudCount() {
        return l1 + l2 + l3 + l4 + l5;
    }

    public boolean isFull() {
        return d5 < Float.POSITIVE_INFINITY;
    }
}
