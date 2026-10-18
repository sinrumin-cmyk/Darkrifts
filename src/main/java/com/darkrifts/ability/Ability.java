package com.darkrifts.ability;

import java.util.Locale;

/** The abilities of the Rift Blade. The order here is the order in the radial menu (clockwise from the top). */
public enum Ability {
    /** Draw a rift with the held right mouse button (the original ability, no instant cast). */
    RIFT(0),
    /** Rifts open around you and spikes burst out of them. */
    SPIKES(140),
    /** A line of rifts races forward, spikes bursting out of each one. */
    WAVE(160),
    /** A long cut through the air that wounds everything on the line. */
    SLASH(60),
    /** A rift opens on the target you look at and a bolt of void falls through it. */
    STRIKE(100),
    /** Instant dash forward through a tear. */
    STEP(50),
    /** Everything around you is pulled into a point in front of you. */
    PULL(180),
    /** Drains the life of nearby enemies and gives it to you. */
    DRAIN(240),
    /** A barrier of rifts that throws back and hurts everything that comes close. */
    SHIELD(400),
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
