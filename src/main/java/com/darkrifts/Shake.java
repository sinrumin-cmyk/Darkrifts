package com.darkrifts;

/** Camera shake strength shared between common code (entities) and the client camera hook (client/ClientFx). */
public final class Shake {
    public static volatile float value;

    private Shake() {}

    public static void add(float amount) {
        value = Math.max(value, Math.min(amount, 4.0F));
    }
}
