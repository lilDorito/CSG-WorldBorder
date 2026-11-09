package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin
{
    @Inject(method = "tryCatchFire", at = @At("HEAD"), cancellable = true, remap = false)
    private void csg$noBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci)
    {
        if (BorderProtection.isProtected(level, pos)) ci.cancel();
    }
}
