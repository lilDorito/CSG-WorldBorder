package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.ProtectedBlockList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin
{
    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<BlockPos> toBlow;

    @Shadow
    @Final
    private Level level;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Explosion$BlockInteraction;)V",
            at = @At("TAIL"))
    private void csg$protectedBlocks(CallbackInfo ci)
    {
        if (level != null && !level.isClientSide) toBlow = new ProtectedBlockList(level);
    }
}
