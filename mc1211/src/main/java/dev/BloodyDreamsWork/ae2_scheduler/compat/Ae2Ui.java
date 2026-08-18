package dev.BloodyDreamsWork.ae2_scheduler.compat;

import appeng.client.gui.widgets.Scrollbar;

/** AE2 19.x GUI details that differ from the 15.x branch. */
public final class Ae2Ui {
    private Ae2Ui() {
    }

    /**
     * The scrollbar style AE2 itself uses on the Crafting CPU screen this screen is styled after.
     * AE2 19.x introduced the wide {@code BIG} sprite; 15.x only has {@code DEFAULT}.
     */
    public static Scrollbar.Style craftingCpuScrollbar() {
        return Scrollbar.BIG;
    }
}
