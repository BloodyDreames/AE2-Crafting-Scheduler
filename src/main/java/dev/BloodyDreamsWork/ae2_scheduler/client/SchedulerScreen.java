package dev.BloodyDreamsWork.ae2_scheduler.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.BloodyDreamsWork.ae2_scheduler.menu.CpuStatus;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;
import dev.BloodyDreamsWork.ae2_scheduler.net.SchedulerActionPayload;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.ManagedCpuState;

/**
 * The Scheduler's GUI: a compact status board.
 *
 * <p>
 * An overview strip at the top, then one row per Crafting CPU on the network with a checkbox for
 * "this Scheduler may pause this CPU", the job it is running, and -- when there is one -- the job it is
 * holding paused. Drawn with plain fills rather than a texture so it inherits the vanilla panel look
 * without shipping art that would clash with AE2's.
 */
public class SchedulerScreen extends AbstractContainerScreen<SchedulerMenu> {

    private static final int WIDTH = 248;
    private static final int HEIGHT = 222;
    private static final int HEADER_HEIGHT = 62;
    private static final int ROW_HEIGHT = 30;
    private static final int VISIBLE_ROWS = 5;
    private static final int PADDING = 7;

    private static final int COLOR_PANEL = 0xFF2B2B33;
    private static final int COLOR_PANEL_BORDER = 0xFF13131A;
    private static final int COLOR_ROW = 0xFF35353F;
    private static final int COLOR_ROW_MANAGED = 0xFF3D4553;
    private static final int COLOR_TEXT = 0xFFE6E6EC;
    private static final int COLOR_TEXT_DIM = 0xFF9A9AA8;
    private static final int COLOR_BAR_BG = 0xFF1B1B22;
    private static final int COLOR_BAR_ACTIVE = 0xFF4E9A5B;
    private static final int COLOR_BAR_PAUSED = 0xFFB0862E;

    private int scroll;

    public SchedulerScreen(SchedulerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = PADDING;
        this.titleLabelY = PADDING;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1,
                COLOR_PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var status = menu.getStatus();

        graphics.drawString(font, title, PADDING, PADDING, COLOR_TEXT, false);

        int y = PADDING + 13;
        var stateText = status.operational()
                ? Component.translatable("gui.ae2_crafting_scheduler.status.active")
                        .withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.ae2_crafting_scheduler.status.offline")
                        .withStyle(ChatFormatting.RED);
        graphics.drawString(font, Component.translatable("gui.ae2_crafting_scheduler.status", stateText),
                PADDING, y, COLOR_TEXT_DIM, false);

        int managed = 0;
        int active = 0;
        int paused = 0;
        for (var cpu : status.cpus()) {
            if (cpu.managed()) {
                managed++;
            }
            if (cpu.hasActiveJob()) {
                active++;
            }
            if (cpu.hasPausedJob()) {
                paused++;
            }
        }

        y += 11;
        graphics.drawString(font,
                Component.translatable("gui.ae2_crafting_scheduler.counts", managed, active, paused),
                PADDING, y, COLOR_TEXT_DIM, false);

        y += 11;
        var preemption = status.preemption()
                ? Component.translatable("gui.ae2_crafting_scheduler.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.ae2_crafting_scheduler.off").withStyle(ChatFormatting.GRAY);
        graphics.drawString(font,
                Component.translatable("gui.ae2_crafting_scheduler.preemption", preemption,
                        status.maxExpress(), status.minPreempt()),
                PADDING, y, COLOR_TEXT_DIM, false);

        y += 11;
        graphics.drawString(font, Component.translatable("gui.ae2_crafting_scheduler.redstone_label",
                status.redstoneMode().displayName()), PADDING, y, COLOR_TEXT_DIM, false);

        renderRows(graphics, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        var cpus = menu.getStatus().cpus();
        int listTop = HEADER_HEIGHT;
        int listBottom = imageHeight - PADDING;

        if (cpus.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.ae2_crafting_scheduler.no_cpus"), PADDING,
                    listTop + 4, COLOR_TEXT_DIM, false);
            return;
        }

        int maxScroll = Math.max(0, cpus.size() - VISIBLE_ROWS);
        scroll = Math.min(scroll, maxScroll);

        graphics.enableScissor(leftPos + PADDING, topPos + listTop, leftPos + imageWidth - PADDING,
                topPos + listBottom);

        for (int i = 0; i < VISIBLE_ROWS && i + scroll < cpus.size(); i++) {
            renderRow(graphics, cpus.get(i + scroll), PADDING, listTop + i * ROW_HEIGHT, mouseX, mouseY);
        }

        graphics.disableScissor();

        if (maxScroll > 0) {
            graphics.drawString(font,
                    Component.literal((scroll + 1) + "-"
                            + Math.min(cpus.size(), scroll + VISIBLE_ROWS) + "/" + cpus.size()),
                    imageWidth - PADDING - 34, PADDING, COLOR_TEXT_DIM, false);
        }
    }

    private void renderRow(GuiGraphics graphics, CpuStatus cpu, int x, int y, int mouseX, int mouseY) {
        int width = imageWidth - PADDING * 2;
        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 2, cpu.managed() ? COLOR_ROW_MANAGED : COLOR_ROW);

        // Checkbox
        var box = cpu.supported() ? (cpu.managed() ? "[x]" : "[ ]") : "[-]";
        graphics.drawString(font, box, x + 3, y + 3, cpu.supported() ? COLOR_TEXT : COLOR_TEXT_DIM, false);

        graphics.drawString(font, Component.literal(trim(cpu.name(), 22)), x + 24, y + 3, COLOR_TEXT, false);

        var stateColor = switch (cpu.state()) {
            case ERROR, UNAVAILABLE, UNSUPPORTED -> ChatFormatting.RED;
            case RUNNING_EXPRESS_JOB -> ChatFormatting.AQUA;
            case PAUSED, DRAINING_IN_FLIGHT_WORK, PAUSE_REQUESTED, RESTORING -> ChatFormatting.YELLOW;
            case RUNNING, RESUMED -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
        var stateText = cpu.state().displayName().copy().withStyle(stateColor);
        graphics.drawString(font, stateText, x + width - font.width(stateText) - 3, y + 3, COLOR_TEXT, false);

        // Second line: what the CPU is doing right now.
        var line2 = cpu.hasActiveJob()
                ? Component.literal(trim(cpu.activeLabel(), 30))
                : Component.translatable("gui.ae2_crafting_scheduler.idle");
        graphics.drawString(font, line2, x + 24, y + 13, COLOR_TEXT_DIM, false);
        if (cpu.hasActiveJob()) {
            drawBar(graphics, x + width - 62, y + 13, 58, cpu.activeProgress(), COLOR_BAR_ACTIVE);
        }

        // Third line: the job being held, if any.
        if (cpu.hasPausedJob()) {
            var held = Component
                    .translatable("gui.ae2_crafting_scheduler.paused_job", trim(cpu.pausedLabel(), 24))
                    .withStyle(ChatFormatting.YELLOW);
            graphics.drawString(font, held, x + 24, y + 22, COLOR_TEXT_DIM, false);
            drawBar(graphics, x + width - 62, y + 22, 58, cpu.pausedProgress(), COLOR_BAR_PAUSED);
        } else if (!cpu.errorReason().isEmpty()) {
            graphics.drawString(font, Component.literal(trim(cpu.errorReason(), 34))
                    .withStyle(ChatFormatting.RED), x + 24, y + 22, COLOR_TEXT_DIM, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.ae2_crafting_scheduler.cpu_specs",
                    formatBytes(cpu.storageBytes()), cpu.coProcessors()), x + 24, y + 22, COLOR_TEXT_DIM,
                    false);
        }

        // Tooltip with the details that do not fit.
        if (isHovering(x, y, width, ROW_HEIGHT - 2, mouseX, mouseY)) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.literal(cpu.name()));
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.cpu_specs",
                    formatBytes(cpu.storageBytes()), cpu.coProcessors()).withStyle(ChatFormatting.GRAY));
            if (cpu.hasActiveJob()) {
                lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_active",
                        cpu.activeLabel(), Math.round(cpu.activeProgress() * 100), cpu.activeOperations()));
            }
            if (cpu.hasPausedJob()) {
                lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_paused",
                        cpu.pausedLabel(), Math.round(cpu.pausedProgress() * 100), cpu.pausedOperations()));
                lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_in_flight",
                        cpu.inFlight()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_cancel_express")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (!cpu.supported()) {
                lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_unsupported")
                        .withStyle(ChatFormatting.RED));
            }
            graphics.renderComponentTooltip(font, lines, mouseX - leftPos, mouseY - topPos);
        }
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, float progress, int color) {
        graphics.fill(x, y, x + width, y + 7, COLOR_BAR_BG);
        int filled = Math.round(width * Math.min(1f, Math.max(0f, progress)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 7, color);
        }
        var text = Math.round(progress * 100) + "%";
        graphics.drawString(font, text, x + width / 2 - font.width(text) / 2, y - 1, COLOR_TEXT, false);
    }

    private boolean isHovering(int x, int y, int width, int height, int mouseX, int mouseY) {
        int rx = mouseX - leftPos;
        int ry = mouseY - topPos;
        return rx >= x && rx < x + width && ry >= y && ry < y + height && ry >= HEADER_HEIGHT
                && ry < imageHeight - PADDING;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var cpus = menu.getStatus().cpus();
        int rx = (int) mouseX - leftPos;
        int ry = (int) mouseY - topPos;

        // Redstone mode line in the header.
        if (rx >= PADDING && rx < imageWidth - PADDING && ry >= PADDING + 46 && ry < PADDING + 57) {
            PacketDistributor.sendToServer(new SchedulerActionPayload(
                    SchedulerActionPayload.Action.CYCLE_REDSTONE, net.minecraft.core.BlockPos.ZERO));
            return true;
        }

        if (ry >= HEADER_HEIGHT && ry < imageHeight - PADDING) {
            int index = (ry - HEADER_HEIGHT) / ROW_HEIGHT + scroll;
            if (index >= 0 && index < cpus.size()) {
                var cpu = cpus.get(index);
                if (cpu.supported()) {
                    var action = button == 1 && cpu.hasPausedJob()
                            ? SchedulerActionPayload.Action.CANCEL_EXPRESS
                            : SchedulerActionPayload.Action.TOGGLE_CPU;
                    PacketDistributor.sendToServer(new SchedulerActionPayload(action, cpu.pos()));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, menu.getStatus().cpus().size() - VISIBLE_ROWS);
        scroll = Math.min(maxScroll, Math.max(0, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024) {
            return (bytes / (1024 * 1024)) + "M";
        }
        if (bytes >= 1024) {
            return (bytes / 1024) + "k";
        }
        return Long.toString(bytes);
    }
}
