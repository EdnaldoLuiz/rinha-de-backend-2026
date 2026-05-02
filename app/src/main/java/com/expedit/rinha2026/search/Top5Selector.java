package com.expedit.rinha2026.search;

public final class Top5Selector {
    private long d1 = Long.MAX_VALUE;
    private long d2 = Long.MAX_VALUE;
    private long d3 = Long.MAX_VALUE;
    private long d4 = Long.MAX_VALUE;
    private long d5 = Long.MAX_VALUE;

    private byte l1;
    private byte l2;
    private byte l3;
    private byte l4;
    private byte l5;

    public void reset() {
        d1 = Long.MAX_VALUE;
        d2 = Long.MAX_VALUE;
        d3 = Long.MAX_VALUE;
        d4 = Long.MAX_VALUE;
        d5 = Long.MAX_VALUE;
        l1 = l2 = l3 = l4 = l5 = 0;
    }

    public long currentWorst() {
        return d5;
    }

    public boolean isFull() {
        return d5 < Long.MAX_VALUE;
    }

    public void offer(long dist, byte label) {
        if (dist < d1) {
            d5 = d4; l5 = l4;
            d4 = d3; l4 = l3;
            d3 = d2; l3 = l2;
            d2 = d1; l2 = l1;
            d1 = dist; l1 = label;
            return;
        }
        if (dist < d2) {
            d5 = d4; l5 = l4;
            d4 = d3; l4 = l3;
            d3 = d2; l3 = l2;
            d2 = dist; l2 = label;
            return;
        }
        if (dist < d3) {
            d5 = d4; l5 = l4;
            d4 = d3; l4 = l3;
            d3 = dist; l3 = label;
            return;
        }
        if (dist < d4) {
            d5 = d4; l5 = l4;
            d4 = dist; l4 = label;
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
}
