package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.client.Minecraft;

import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

/**
 * Client-only receiver for the status packet. Kept in its own class so that a dedicated server
 * never loads {@link Minecraft}.
 */
final class ClientStatusHandler {
    private ClientStatusHandler() {
    }

    static void accept(SchedulerStatus status) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof SchedulerMenu menu) {
            menu.setStatus(status);
        }
    }
}
