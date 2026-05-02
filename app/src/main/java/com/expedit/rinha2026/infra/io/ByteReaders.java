package com.expedit.rinha2026.infra.io;

public final class ByteReaders {
    private ByteReaders() {
    }

    public static int readIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
            | ((bytes[offset + 1] & 0xFF) << 16)
            | ((bytes[offset + 2] & 0xFF) << 8)
            | (bytes[offset + 3] & 0xFF);
    }
}
