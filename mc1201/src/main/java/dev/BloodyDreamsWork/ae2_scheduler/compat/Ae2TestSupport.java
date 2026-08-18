package dev.BloodyDreamsWork.ae2_scheduler.compat;

import java.util.Arrays;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.BlockDefinition;
import appeng.menu.AutoCraftingMenu;

/**
 * AE2 15.x flavour of the test-framework details the shared game-test plots need.
 *
 * <p>
 * AE2 15.x has no {@code CraftingPatternHelper.encodeCraftingPattern}, so it is reimplemented here
 * on top of the 1.20.1 recipe API ({@code TransientCraftingContainer} instead of 1.21.1's
 * {@code CraftingInput}, and a bare {@code CraftingRecipe} instead of a {@code RecipeHolder}). The
 * ME Chest is also named differently on this branch.
 */
public final class Ae2TestSupport {
    private Ae2TestSupport() {
    }

    public static ItemStack encodeCraftingPattern(ServerLevel level, Object[] ingredients,
            boolean allowSubstitutions, boolean allowFluidSubstitutions) {
        var stacks = Arrays.stream(ingredients)
                .map(in -> {
                    if (in instanceof ItemLike itemLike) {
                        return new ItemStack(itemLike);
                    } else if (in instanceof ItemStack itemStack) {
                        return itemStack;
                    } else if (in == null) {
                        return ItemStack.EMPTY;
                    } else {
                        throw new IllegalArgumentException("Unsupported argument: " + in);
                    }
                })
                .toArray(ItemStack[]::new);

        var container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            container.setItem(i, stacks[i].copy());
        }

        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level)
                .orElseThrow(() -> new RuntimeException("No crafting recipe matches the provided input."));

        var padded = new ItemStack[9];
        for (int i = 0; i < padded.length; i++) {
            padded[i] = i < stacks.length ? stacks[i] : ItemStack.EMPTY;
        }

        return PatternDetailsHelper.encodeCraftingPattern(
                recipe,
                padded,
                recipe.assemble(container, level.registryAccess()),
                allowSubstitutions,
                allowFluidSubstitutions);
    }

    /** The ME Chest, which doubles as a sub-menu host for the crafting confirmation menu. */
    public static BlockDefinition<?> meChest() {
        return AEBlocks.CHEST;
    }

    public static boolean isMeChest(BlockEntity blockEntity) {
        return blockEntity instanceof ChestBlockEntity;
    }
}
