package com.lildorito.csgworldborder.mixin.compat;

import com.lildorito.csgworldborder.border.BorderProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.mhackmin.mhwarfare.ata.AlexaEntity", remap = false)
public abstract class MhWarfareBlocksMixin
{
    @Redirect(method = "*", require = 0,
            at = @At(value = "INVOKE", remap = true, target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private static boolean csg$destroyBy(ServerLevel level, BlockPos pos, boolean drop, Entity breaker)
    {
        return !BorderProtection.isProtected(level, pos) && level.destroyBlock(pos, drop, breaker);
    }
}
