package dev.BloodyDreamsWork.ae2_scheduler.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.style.PaletteColor;
import appeng.core.AEConfig;
import appeng.core.definitions.AEBlocks;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;

import dev.BloodyDreamsWork.ae2_scheduler.menu.CpuStatus;

final class CpuTableRenderer extends AbstractTableRenderer<CpuStatus> {
    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 19;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int CELL_BORDER = 1;
    private static final int COLUMNS = 3;
    private static final int ROWS = 6;

    private static final int BACKGROUND_ALPHA = 0x5A000000;
    private static final int MAX_TEXT_WIDTH = 86;
    private static final AEKey CPU_ICON = Objects.requireNonNull(AEItemKey.of(AEBlocks.CRAFTING_UNIT));

    private final Predicate<CpuStatus> isSelected;

    CpuTableRenderer(AEBaseScreen<?> screen, Predicate<CpuStatus> isSelected) {
        super(screen, TABLE_X, TABLE_Y, ROWS);
        this.isSelected = isSelected;
    }

    @Nullable
    CpuStatus getEntryAt(double mouseX, double mouseY, List<CpuStatus> entries, int scrollOffset) {
        double relativeX = mouseX - screen.getGuiLeft() - TABLE_X;
        double relativeY = mouseY - screen.getGuiTop() - TABLE_Y;
        if (relativeX < 0 || relativeY < 0) {
            return null;
        }

        int column = (int) (relativeX / (CELL_WIDTH + CELL_BORDER));
        int row = (int) (relativeY / (CELL_HEIGHT + CELL_BORDER));
        if (column >= COLUMNS || row >= ROWS) {
            return null;
        }

        double cellX = column * (CELL_WIDTH + CELL_BORDER);
        double cellY = row * (CELL_HEIGHT + CELL_BORDER);
        if (relativeX > cellX + CELL_WIDTH || relativeY > cellY + CELL_HEIGHT) {
            return null;
        }

        int index = (row + scrollOffset) * COLUMNS + column;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    int getFirstVisibleIndex(int scrollOffset) {
        return Math.max(0, scrollOffset) * COLUMNS;
    }

    int getLastVisibleIndex(int size, int scrollOffset) {
        return Math.min(size, getFirstVisibleIndex(scrollOffset) + ROWS * COLUMNS);
    }

    @Override
    protected List<Component> getEntryDescription(CpuStatus entry) {
        var lines = new ArrayList<Component>(2);
        lines.add(fit(Component.literal(entry.name())));

        var state = entry.state().displayName();
        if (entry.hasActiveJob()) {
            state = state.copy().append(" " + Math.round(entry.activeProgress() * 100) + "%");
        } else if (entry.hasPausedJob()) {
            state = state.copy().append(" " + Math.round(entry.pausedProgress() * 100) + "%");
        }
        lines.add(fit(state));
        return lines;
    }

    @Override
    protected AEKey getEntryStack(CpuStatus entry) {
        return CPU_ICON;
    }

    @Override
    protected List<Component> getEntryTooltip(CpuStatus entry) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(entry.name()));
        lines.add(entry.state().displayName().copy().withStyle(stateColor(entry)));

        if (entry.coProcessors() == 1) {
            lines.add(ButtonToolTips.CpuStatusCoProcessor.text(Tooltips.ofNumber(entry.coProcessors()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (entry.coProcessors() > 1) {
            lines.add(ButtonToolTips.CpuStatusCoProcessors.text(Tooltips.ofNumber(entry.coProcessors()))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(ButtonToolTips.CpuStatusStorage.text(Tooltips.ofBytes(entry.storageBytes()))
                .withStyle(ChatFormatting.GRAY));

        if (entry.hasActiveJob()) {
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_active",
                    Component.literal(entry.activeLabel()),
                    Tooltips.ofPercent(entry.activeProgress()),
                    Tooltips.ofNumber(entry.activeOperations())));
        }
        if (entry.hasPausedJob()) {
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_paused",
                    Component.literal(entry.pausedLabel()),
                    Tooltips.ofPercent(entry.pausedProgress()),
                    Tooltips.ofNumber(entry.pausedOperations())));
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_in_flight",
                    Tooltips.ofNumber(entry.inFlight())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_cancel_express")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        if (!entry.errorReason().isEmpty()) {
            lines.add(Component.literal(entry.errorReason()).withStyle(ChatFormatting.RED));
        }
        if (!entry.supported()) {
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_unsupported")
                    .withStyle(ChatFormatting.RED));
        } else if (entry.managed()) {
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_managed")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_unmanaged")
                    .withStyle(ChatFormatting.GRAY));
        }

        lines.add(Component.translatable("gui.ae2_crafting_scheduler.tooltip_select")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    protected int getEntryBackgroundColor(CpuStatus entry) {
        if (isSelected.test(entry)) {
            int selection = screen.getStyle().getColor(PaletteColor.SELECTION_COLOR).toARGB();
            return (selection & 0x00FFFFFF) | BACKGROUND_ALPHA;
        }
        if (!AEConfig.instance().isUseColoredCraftingStatus()) {
            return 0;
        }
        return switch (entry.state()) {
            case ERROR, UNAVAILABLE, UNSUPPORTED -> AEColor.RED.blackVariant | BACKGROUND_ALPHA;
            case PAUSE_REQUESTED, DRAINING_IN_FLIGHT_WORK, PAUSED, RUNNING_EXPRESS_JOB,
                    EXPRESS_COMPLETED, RESTORING -> AEColor.YELLOW.blackVariant | BACKGROUND_ALPHA;
            default -> {
                if (entry.managed()) {
                    yield AEColor.GREEN.blackVariant | BACKGROUND_ALPHA;
                }
                yield 0;
            }
        };
    }

    private static Component fit(Component text) {
        var font = Minecraft.getInstance().font;
        var value = text.getString();
        if (font.width(value) <= MAX_TEXT_WIDTH) {
            return text;
        }
        var ellipsis = "...";
        var shortened = font.plainSubstrByWidth(value, MAX_TEXT_WIDTH - font.width(ellipsis)) + ellipsis;
        return Component.literal(shortened).withStyle(text.getStyle());
    }

    private static ChatFormatting stateColor(CpuStatus entry) {
        return switch (entry.state()) {
            case ERROR, UNAVAILABLE, UNSUPPORTED -> ChatFormatting.RED;
            case RUNNING_EXPRESS_JOB -> ChatFormatting.AQUA;
            case PAUSED, DRAINING_IN_FLIGHT_WORK, PAUSE_REQUESTED, EXPRESS_COMPLETED, RESTORING ->
                ChatFormatting.YELLOW;
            case RUNNING, RESUMED -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
    }
}
