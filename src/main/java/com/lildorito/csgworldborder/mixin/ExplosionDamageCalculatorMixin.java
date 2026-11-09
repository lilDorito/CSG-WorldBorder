package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin
{
    @Inject(method = "shouldBlockExplode", at = @At("HEAD"), cancellable = true)
    private void csg$protected(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state, float power, CallbackInfoReturnable<Boolean> cir)
    {
        if (getter instanceof Level level && !level.isClientSide && BorderProtection.isProtected(level, pos)) cir.setReturnValue(false);
    }
}
