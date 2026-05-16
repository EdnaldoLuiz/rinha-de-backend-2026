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
    private int i1 = Integer.MAX_VALUE;
    private int i2 = Integer.MAX_VALUE;
    private int i3 = Integer.MAX_VALUE;
    private int i4 = Integer.MAX_VALUE;
    private int i5 = Integer.MAX_VALUE;

    public void reset() {
        d1 = Long.MAX_VALUE;
        d2 = Long.MAX_VALUE;
        d3 = Long.MAX_VALUE;
        d4 = Long.MAX_VALUE;
        d5 = Long.MAX_VALUE;
        l1 = l2 = l3 = l4 = l5 = 0;
        i1 = i2 = i3 = i4 = i5 = Integer.MAX_VALUE;
    }

    public long currentWorst() {
        return d5;
    }

    public boolean isFull() {
        return d5 < Long.MAX_VALUE;
    }

    public void offer(long dist, byte label, int origId) {
        if (better(dist, origId, d1, i1)) {
            d5 = d4; l5 = l4; i5 = i4;
            d4 = d3; l4 = l3; i4 = i3;
            d3 = d2; l3 = l2; i3 = i2;
            d2 = d1; l2 = l1; i2 = i1;
            d1 = dist; l1 = label; i1 = origId;
            return;
        }
        if (better(dist, origId, d2, i2)) {
            d5 = d4; l5 = l4; i5 = i4;
            d4 = d3; l4 = l3; i4 = i3;
            d3 = d2; l3 = l2; i3 = i2;
            d2 = dist; l2 = label; i2 = origId;
            return;
        }
        if (better(dist, origId, d3, i3)) {
            d5 = d4; l5 = l4; i5 = i4;
            d4 = d3; l4 = l3; i4 = i3;
            d3 = dist; l3 = label; i3 = origId;
            return;
        }
        if (better(dist, origId, d4, i4)) {
            d5 = d4; l5 = l4; i5 = i4;
            d4 = dist; l4 = label; i4 = origId;
            return;
        }
        if (better(dist, origId, d5, i5)) {
            d5 = dist; l5 = label; i5 = origId;
        }
    }

    public int fraudCount() {
        return l1 + l2 + l3 + l4 + l5;
    }

    public void copyState(long[] distances, byte[] labels, int[] ids) {
        distances[0] = d1;
        distances[1] = d2;
        distances[2] = d3;
        distances[3] = d4;
        distances[4] = d5;

        labels[0] = l1;
        labels[1] = l2;
        labels[2] = l3;
        labels[3] = l4;
        labels[4] = l5;

        ids[0] = i1;
        ids[1] = i2;
        ids[2] = i3;
        ids[3] = i4;
        ids[4] = i5;
    }

    private static boolean better(long distA, int idA, long distB, int idB) {
        return distA < distB || (distA == distB && idA < idB);
    }
}
