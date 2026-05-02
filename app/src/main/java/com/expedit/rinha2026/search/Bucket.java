package com.expedit.rinha2026.search;

public final class Bucket {
    public int key;
    public int startVector;
    public int count;
    public final float[] minBounds = new float[14];
    public final float[] maxBounds = new float[14];
}
