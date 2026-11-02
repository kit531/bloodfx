package com.kit531.bloodfx;

/**
 * The kinds of hit the mod tells apart. Every damage source that is not one of the three
 * special sword hits counts as {@link #NORMAL}.
 */
public enum HitType {
    NORMAL("normal", 0),
    KNOCKBACK("knockback", 1),
    CRIT("crit", 2),
    SWEEP("sweep", 4);

    /** Used for sound file names and config keys. */
    public final String key;

    /**
     * Row of the 16x16 entity overlay texture that holds this hit type's colour.
     * Row 3 is vanilla's red hurt flash and is left untouched, so it is skipped here.
     */
    public final int overlayRow;

    HitType(String key, int overlayRow) {
        this.key = key;
        this.overlayRow = overlayRow;
    }
}
