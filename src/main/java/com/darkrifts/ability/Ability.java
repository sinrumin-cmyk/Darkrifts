package com.darkrifts.ability;

import java.util.Locale;

/** The abilities of the Rift Blade. The order here is the order in the radial menu (clockwise from the top). */
public enum Ability {
    /** Draw a rift with the held right mouse button (the original ability, no instant cast). */
    RIFT(0),
    /** Rifts open around you and spikes burst out of them. */
    SPIKES(140),
    /** Instant dash forward through a tear. */
    STEP(50),
    /** Everything around you is pulled into a point in front of you. */
    PULL(180),
    /** Turn dark: invisible, shielded, and nearby mobs go blind. */
    CLOAK(300),
    /** Seals the entity you are looking at inside the void prison. */
    SEAL(200);

    public final int cooldown;

    Ability(int cooldown) {
        this.cooldown = cooldown;
    }

    public String key() {
        return "ability.darkrifts." + name().toLowerCase(Locale.ROOT);
    }

    public String descKey() {
        return key() + ".desc";
    }
}
