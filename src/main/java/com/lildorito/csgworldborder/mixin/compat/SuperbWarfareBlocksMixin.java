package com.lildorito.csgworldborder.mixin.compat;

import com.lildorito.csgworldborder.border.BorderProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {
        "com.atsuishio.superbwarfare.entity.projectile.C4Entity",
        "com.atsuishio.superbwarfare.entity.projectile.Tm62Entity",
        "com.atsuishio.superbwarfare.entity.projectile.CannonShellEntity",
        "com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity",
        "com.atsuishio.superbwarfare.entity.projectile.AerialBombEntity",
        "com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile",
        "com.atsuishio.superbwarfare.entity.projectile.MelonBombEntity",
        "com.atsuishio.superbwarfare.entity.projectile.MediumRocketEntity",
        "com.atsuishio.superbwarfare.entity.vehicle.AnnihilatorEntity",
        "com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleMotionUtils",
        "com.atsuishio.superbwarfare.event.CustomEventHandler"}, remap = false)
public abstract class SuperbWarfareBlocksMixin
{
    @Redirect(method = "*", require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private static boolean csg$destroy(Level level, BlockPos pos, boolean drop)
    {
        return !BorderProtection.isProtected(level, pos) && level.destroyBlock(pos, drop);
    }

    @Redirect(method = "*", require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private static boolean csg$destroyServer(ServerLevel level, BlockPos pos, boolean drop)
    {
        return !BorderProtection.isProtected(level, pos) && level.destroyBlock(pos, drop);
    }

    @Redirect(method = "*", require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private static boolean csg$destroyBy(Level level, BlockPos pos, boolean drop, Entity breaker)
    {
        return !BorderProtection.isProtected(level, pos) && level.destroyBlock(pos, drop, breaker);
    }
}
