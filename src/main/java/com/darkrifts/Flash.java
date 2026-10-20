package com.darkrifts;

/** Screen flash strength shared between common code (network handler) and the client overlay (client/FlashOverlay). */
public final class Flash {
    public static volatile float value;
    public static volatile int rgb = 0xE6C8FF;

    private Flash() {}

    public static void add(float amount, int color) {
        if (amount >= value) {
            rgb = color;
        }
        value = Math.max(value, Math.min(amount, 1.0F));
    }
}
