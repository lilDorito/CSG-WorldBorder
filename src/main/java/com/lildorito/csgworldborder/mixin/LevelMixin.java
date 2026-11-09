package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin
{
    @Shadow
    @Final
    protected WritableLevelData levelData;

    @Shadow
    public abstract WorldBorder getWorldBorder();

    @Inject(method = "getSharedSpawnPos", at = @At("HEAD"), cancellable = true)
    private void csg$sharedSpawn(CallbackInfoReturnable<BlockPos> cir)
    {
        Volume volume = BorderVolume.active(getWorldBorder());
        if (volume == null) return;
        BlockPos spawn = new BlockPos(levelData.getXSpawn(), levelData.getYSpawn(), levelData.getZSpawn());
        if (volume.contains(spawn.getX(), spawn.getY(), spawn.getZ()))
        {
            cir.setReturnValue(spawn);
            return;
        }
        int[] inside = volume.nearestInside(spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5);
        if (inside != null) cir.setReturnValue(new BlockPos(inside[0], inside[1], inside[2]));
    }
}
