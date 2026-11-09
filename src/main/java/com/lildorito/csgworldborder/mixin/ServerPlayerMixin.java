package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Redirect(method = "fudgeSpawnLocation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(DD)D"))
    private double csg$spawnDistance(WorldBorder border, double x, double z, ServerLevel level)
    {
        Volume volume = BorderVolume.active(border);
        if (volume == null) return border.getDistanceToBorder(x, z);
        return volume.signedDistance(x + 0.5, level.getSharedSpawnPos().getY() + 0.5, z + 0.5);
    }

    @Redirect(method = "fudgeSpawnLocation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean csg$spawnFree(ServerLevel level, Entity entity)
    {
        if (!level.noCollision(entity)) return false;
        Volume volume = BorderVolume.active(level.getWorldBorder());
        return volume == null || csg$inside(volume, entity);
    }

    @Inject(method = "fudgeSpawnLocation", at = @At("TAIL"))
    private void csg$spawnFallback(ServerLevel level, CallbackInfo ci)
    {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Volume volume = BorderVolume.active(level.getWorldBorder());
        if (volume == null || csg$inside(volume, self)) return;
        int[] inside = volume.nearestInside(self.getX(), self.getY() + 0.5, self.getZ());
        if (inside == null) return;
        self.moveTo(inside[0] + 0.5, inside[1], inside[2] + 0.5, 0.0F, 0.0F);
        int top = level.getMaxBuildHeight() - 1;
        while (!level.noCollision(self) && self.getY() < top && volume.contains(self.getX(), self.getY() + 1.0, self.getZ()))
        {
            self.setPos(self.getX(), self.getY() + 1.0, self.getZ());
        }
    }

    @Unique
    private static boolean csg$inside(Volume volume, Entity entity)
    {
        AABB box = entity.getBoundingBox();
        return volume.contains(box.getCenter().x, box.getCenter().y, box.getCenter().z);
    }
}
