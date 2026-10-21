package com.wyrm.omrajput.data;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Snakey Rain 6.36 binary format. Pure Java, also exercised on the host. */
public final class RainWire {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    public RainWire(int opcode) { u8(opcode); }
    public RainWire u8(int v) { if (v < 0 || v > 255) throw new IllegalArgumentException("byte range"); out.write(v); return this; }
    public RainWire u16(int v) { if (v < 0 || v > 65535) throw new IllegalArgumentException("short range"); return u8(v & 255).u8(v >> 8); }
    public RainWire text(String v, boolean wide) {
        byte[] bytes = v.getBytes(StandardCharsets.UTF_8);
        if (wide) u16(bytes.length); else u8(bytes.length);
        out.write(bytes, 0, bytes.length); return this;
    }
    public RainWire f64(double v) {
        if (!Double.isFinite(v)) throw new IllegalArgumentException("non-finite");
        byte[] b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(v).array();
        out.write(b, 0, b.length); return this;
    }
    public RainWire raw(byte[] data) { out.write(data, 0, data.length); return this; }
    public byte[] bytes() { return out.toByteArray(); }
    public static byte[] login(String user, String pass) { return new RainWire('a').text(user, true).text(pass, true).u8(6).u8(36).bytes(); }
    public static byte[] pair(int opcode, int value) { return new RainWire(opcode).u8(value).bytes(); }
    /** Legacy private-tag proof, not password storage. */
    public static String tagProof(String key) {
        if (key.isEmpty()) return "";
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) { hex.append("0123456789abcdef".charAt((b & 255) >> 4)); hex.append("0123456789abcdef".charAt(b & 15)); }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static byte[] xy(int opcode, double x, double y) { return new RainWire(opcode).u16(coord(x)).u16(coord(y)).bytes(); }
    public static int coord(double value) { if (!Double.isFinite(value)) throw new IllegalArgumentException("coordinate"); return (int)Math.max(0, Math.min(65535, Math.round(value))); }
    public static final class Reader {
        private final ByteBuffer data;
        public Reader(byte[] bytes) {
            if (bytes.length < 1 || bytes.length > 16384) throw new IllegalArgumentException("message size");
            data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
        public int u8() { return data.get() & 255; }
        public int u16() { return data.getShort() & 65535; }
        public String text(boolean wide) {
            int n = wide ? u16() : u8();
            if (n > data.remaining()) throw new IllegalArgumentException("truncated string");
            byte[] bytes = new byte[n]; data.get(bytes); return new String(bytes, StandardCharsets.UTF_8);
        }
        public int remaining() { return data.remaining(); }
    }
}
