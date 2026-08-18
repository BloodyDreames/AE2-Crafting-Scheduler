package dev.BloodyDreamsWork.ae2_scheduler.compat;

import appeng.client.gui.widgets.Scrollbar;

/** AE2 15.x GUI details that differ from the 19.x branch. */
public final class Ae2Ui {
    private Ae2Ui() {
    }

    /**
     * The scrollbar style AE2 itself uses on the Crafting CPU screen this screen is styled after.
     * AE2 15.x has no wide {@code BIG} sprite, so it uses {@code DEFAULT} there too.
     */
    public static Scrollbar.Style craftingCpuScrollbar() {
        return Scrollbar.DEFAULT;
    }
}
