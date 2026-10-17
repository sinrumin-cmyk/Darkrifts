package com.darkrifts;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue RIFT_LIFETIME_SECONDS = B
            .comment("How long a rift torn with the Rift Blade stays open, in seconds. 0 = permanent.")
            .defineInRange("riftLifetimeSeconds", 300, 0, 86400);

    public static final ModConfigSpec.DoubleValue STROKE_RADIUS = B
            .comment("Distance (in blocks) from your eyes at which the rift is drawn while you drag the Rift Blade.")
            .defineInRange("strokeRadius", 8.0, 3.0, 24.0);

    public static final ModConfigSpec.IntValue MAX_RIFT_BLOCKS = B
            .comment("Maximum number of rift blocks a single stroke can create.")
            .defineInRange("maxRiftBlocks", 220, 10, 2000);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
