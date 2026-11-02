package com.kit531.bloodfx.client;

/**
 * Keeps a handle on the game's overlay texture (the 16x16 texture that holds the red
 * "I was hurt" colour) and repaints it when the settings change.
 */
public final class OverlayHook {
    private static OverlayRecolor current;
    private static boolean pending;

    private OverlayHook() {
    }

    /** Called by the overlay texture mixin when the game creates the texture. */
    public static void attach(OverlayRecolor overlay) {
        current = overlay;
        pending = true;
    }

    public static void requestApply() {
        pending = true;
    }

    /** Must be called on the render thread, which is where client ticks run. */
    public static void applyIfNeeded() {
        if (pending && current != null) {
            pending = false;
            current.bloodfx$recolor();
        }
    }
}
