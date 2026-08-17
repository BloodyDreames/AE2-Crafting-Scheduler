package dev.BloodyDreamsWork.ae2_scheduler.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.localization.GuiText;

import dev.BloodyDreamsWork.ae2_scheduler.menu.CpuStatus;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;
import dev.BloodyDreamsWork.ae2_scheduler.net.SchedulerActionPayload;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerRedstoneMode;

public final class SchedulerScreen extends AEBaseScreen<SchedulerMenu> {
    private final CpuTableRenderer table;
    private final Scrollbar scrollbar;
    private final AE2Button manageButton;
    private final AE2Button cancelButton;
    private final SettingToggleButton<RedstoneMode> redstoneButton;

    @Nullable
    private CpuSelection selectedCpu;

    public SchedulerScreen(SchedulerMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);

        this.table = new CpuTableRenderer(this, this::isSelected);
        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.BIG);

        this.manageButton = widgets.addButton("suspend",
                Component.translatable("gui.ae2_crafting_scheduler.manage"), this::toggleSelectedCpu);
        this.cancelButton = widgets.addButton("cancel",
                Component.translatable("gui.ae2_crafting_scheduler.cancel_express"), this::cancelSelectedExpressJob);

        this.redstoneButton = new SettingToggleButton<RedstoneMode>(
                Settings.REDSTONE_CONTROLLED,
                RedstoneMode.IGNORE,
                value -> value != RedstoneMode.SIGNAL_PULSE,
                (button, backwards) -> send(
                        backwards ? SchedulerActionPayload.Action.CYCLE_REDSTONE
                                : SchedulerActionPayload.Action.CYCLE_REDSTONE_BACKWARDS,
                        BlockPos.ZERO)) {
            @Override
            public List<Component> getTooltipMessage() {
                return SchedulerScreen.this.buildSchedulerTooltip(super.getTooltipMessage());
            }
        };
        addToLeftToolbar(this.redstoneButton);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var cpus = menu.getStatus().cpus();
        scrollbar.setRange(0, table.getScrollableRows(cpus.size()), 1);
        var selected = ensureVisibleSelection(cpus);

        int managed = 0;
        for (var cpu : cpus) {
            if (cpu.managed()) {
                managed++;
            }
        }

        var screenTitle = Component.translatable("gui.ae2_crafting_scheduler.title_counts",
                getGuiDisplayName(Component.translatable("block.ae2_crafting_scheduler.crafting_scheduler")),
                managed,
                cpus.size());
        if (!menu.getStatus().operational()) {
            screenTitle = screenTitle.copy().append(" - ").append(
                    Component.translatable("gui.ae2_crafting_scheduler.status.offline")
                            .withStyle(ChatFormatting.RED));
        }
        setTextContent(TEXT_ID_DIALOG_TITLE, screenTitle);

        redstoneButton.set(toAe2RedstoneMode(menu.getStatus().redstoneMode()));

        manageButton.active = selected != null && selected.supported();
        manageButton.setMessage(Component.translatable(selected != null && selected.managed()
                ? "gui.ae2_crafting_scheduler.release"
                : "gui.ae2_crafting_scheduler.manage"));

        cancelButton.active = selected != null && selected.supported() && selected.hasPausedJob();
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);

        var cpus = menu.getStatus().cpus();
        table.render(graphics, mouseX, mouseY, cpus, scrollbar.getCurrentScroll());
        if (cpus.isEmpty()) {
            graphics.drawString(font, GuiText.NoCraftingCPUs.text(), 12, 24,
                    getStyle().getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB(), false);
        }
    }

    @Nullable
    @Override
    public StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        var hovered = table.getHoveredStack();
        if (hovered != null) {
            return hovered;
        }
        return super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var clicked = table.getEntryAt(mouseX, mouseY, menu.getStatus().cpus(), scrollbar.getCurrentScroll());
        if (clicked != null && (button == 0 || button == 1)) {
            setFocused(null);
            selectedCpu = CpuSelection.of(clicked);
            if (button == 1 && clicked.supported() && clicked.hasPausedJob()) {
                send(SchedulerActionPayload.Action.CANCEL_EXPRESS, clicked.pos());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleSelectedCpu() {
        var selected = getSelectedCpu();
        if (selected != null && selected.supported()) {
            send(SchedulerActionPayload.Action.TOGGLE_CPU, selected.pos());
        }
    }

    private void cancelSelectedExpressJob() {
        var selected = getSelectedCpu();
        if (selected != null && selected.supported() && selected.hasPausedJob()) {
            send(SchedulerActionPayload.Action.CANCEL_EXPRESS, selected.pos());
        }
    }

    @Nullable
    private CpuStatus getSelectedCpu() {
        if (selectedCpu == null) {
            return null;
        }
        for (var cpu : menu.getStatus().cpus()) {
            if (selectedCpu.matches(cpu)) {
                return cpu;
            }
        }
        return null;
    }

    @Nullable
    private CpuStatus ensureVisibleSelection(List<CpuStatus> cpus) {
        int first = table.getFirstVisibleIndex(scrollbar.getCurrentScroll());
        int last = table.getLastVisibleIndex(cpus.size(), scrollbar.getCurrentScroll());

        var selected = getSelectedCpu();
        int selectedIndex = selected == null ? -1 : cpus.indexOf(selected);
        if (selectedIndex >= first && selectedIndex < last) {
            return selected;
        }

        CpuStatus supported = null;
        CpuStatus fallback = null;
        for (int i = first; i < last; i++) {
            var cpu = cpus.get(i);
            if (fallback == null) {
                fallback = cpu;
            }
            if (cpu.supported() && cpu.managed()) {
                selectedCpu = CpuSelection.of(cpu);
                return cpu;
            }
            if (supported == null && cpu.supported()) {
                supported = cpu;
            }
        }

        selected = supported != null ? supported : fallback;
        selectedCpu = selected == null ? null : CpuSelection.of(selected);
        return selected;
    }

    private boolean isSelected(CpuStatus cpu) {
        return selectedCpu != null && selectedCpu.matches(cpu);
    }

    private List<Component> buildSchedulerTooltip(List<Component> redstoneTooltip) {
        var lines = new ArrayList<>(redstoneTooltip);
        var status = menu.getStatus();

        var state = status.operational()
                ? Component.translatable("gui.ae2_crafting_scheduler.status.active")
                        .withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.ae2_crafting_scheduler.status.offline")
                        .withStyle(ChatFormatting.RED);
        lines.add(Component.translatable("gui.ae2_crafting_scheduler.status", state));

        int managed = 0;
        int active = 0;
        int paused = 0;
        for (var cpu : status.cpus()) {
            managed += cpu.managed() ? 1 : 0;
            active += cpu.hasActiveJob() ? 1 : 0;
            paused += cpu.hasPausedJob() ? 1 : 0;
        }
        lines.add(Component.translatable("gui.ae2_crafting_scheduler.counts", managed, active, paused));

        var preemption = status.preemption()
                ? Component.translatable("gui.ae2_crafting_scheduler.on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.ae2_crafting_scheduler.off").withStyle(ChatFormatting.GRAY);
        lines.add(Component.translatable("gui.ae2_crafting_scheduler.preemption", preemption,
                status.maxExpress(), status.minPreempt()));
        return lines;
    }

    private static RedstoneMode toAe2RedstoneMode(SchedulerRedstoneMode mode) {
        return switch (mode) {
            case IGNORE -> RedstoneMode.IGNORE;
            case ACTIVE_WITH_SIGNAL -> RedstoneMode.HIGH_SIGNAL;
            case ACTIVE_WITHOUT_SIGNAL -> RedstoneMode.LOW_SIGNAL;
        };
    }

    private static void send(SchedulerActionPayload.Action action, BlockPos cpu) {
        PacketDistributor.sendToServer(new SchedulerActionPayload(action, cpu));
    }

    private record CpuSelection(BlockPos pos, boolean supported) {
        static CpuSelection of(CpuStatus cpu) {
            return new CpuSelection(cpu.pos(), cpu.supported());
        }

        boolean matches(CpuStatus cpu) {
            return supported == cpu.supported() && pos.equals(cpu.pos());
        }
    }
}
