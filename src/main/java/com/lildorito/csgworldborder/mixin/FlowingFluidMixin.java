package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin
{
    @Inject(method = "canHoldFluid", at = @At("HEAD"), cancellable = true)
    private void csg$noWashingAway(BlockGetter getter, BlockPos pos, BlockState state, Fluid fluid, CallbackInfoReturnable<Boolean> cir)
    {
        if (state.isAir() || !state.getFluidState().isEmpty()) return;
        if (getter instanceof LevelReader reader && BorderProtection.isProtected(reader, pos)) cir.setReturnValue(false);
    }
}
