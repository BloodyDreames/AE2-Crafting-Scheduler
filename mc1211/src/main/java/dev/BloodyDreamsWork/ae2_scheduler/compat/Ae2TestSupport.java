package dev.BloodyDreamsWork.ae2_scheduler.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.BlockDefinition;
import appeng.server.testplots.CraftingPatternHelper;

/**
 * AE2 19.x flavour of the test-framework details the shared game-test plots need.
 */
public final class Ae2TestSupport {
    private Ae2TestSupport() {
    }

    public static ItemStack encodeCraftingPattern(ServerLevel level, Object[] ingredients,
            boolean allowSubstitutions, boolean allowFluidSubstitutions) {
        return CraftingPatternHelper.encodeCraftingPattern(level, ingredients, allowSubstitutions,
                allowFluidSubstitutions);
    }

    /** The ME Chest, which doubles as a sub-menu host for the crafting confirmation menu. */
    public static BlockDefinition<?> meChest() {
        return AEBlocks.ME_CHEST;
    }

    public static boolean isMeChest(BlockEntity blockEntity) {
        return blockEntity instanceof MEChestBlockEntity;
    }
}
