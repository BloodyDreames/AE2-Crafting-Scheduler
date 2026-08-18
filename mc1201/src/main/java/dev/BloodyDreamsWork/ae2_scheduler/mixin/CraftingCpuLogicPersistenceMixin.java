package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;

import appeng.crafting.execution.CraftingCpuLogic;

import dev.BloodyDreamsWork.ae2_scheduler.park.ParkPersistence;

/**
 * Persists the parked job alongside AE2's own CPU state.
 *
 * <p>
 * Split out of {@code CraftingCpuLogicMixin} because on Minecraft 1.20.1 neither AE2 method takes a
 * registry lookup, so the injection descriptors are specific to this version. The state itself lives
 * in the shared mixin and is reached through {@link ParkPersistence}.
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicPersistenceMixin {
    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void acs$writePark(CompoundTag data, CallbackInfo ci) {
        ((ParkPersistence) this).acs$writeParkData(data, null);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void acs$readPark(CompoundTag data, CallbackInfo ci) {
        ((ParkPersistence) this).acs$readParkData(data, null);
    }
}
