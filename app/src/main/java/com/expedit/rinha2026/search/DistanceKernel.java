package com.expedit.rinha2026.search;

public final class DistanceKernel {
    private DistanceKernel() {
    }

    public static long squaredDistanceEarlyAbort(short[] query, short[] vectors, int offset, long currentWorst) {
        long dist = 0L;

        dist = add(query, vectors, offset, 9, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 10, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 11, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 5, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 6, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 7, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 8, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 12, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 0, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 1, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 2, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 3, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 4, dist);
        if (dist > currentWorst) return dist;

        dist = add(query, vectors, offset, 13, dist);
        return dist;
    }

    private static long add(short[] query, short[] vectors, int offset, int dim, long acc) {
        int d = vectors[offset + dim] - query[dim];
        return acc + ((long) d * d);
    }
}
