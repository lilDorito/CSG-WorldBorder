package com.lildorito.csgworldborder.mixin.compat;

import com.lildorito.csgworldborder.border.BorderCollision;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "com.abdelaziz.canary.common.entity.CanaryEntityCollisions", remap = false)
public abstract class CanaryCollisionsMixin
{
    @Inject(method = "isWithinWorldBorder(Lnet/minecraft/world/level/border/WorldBorder;Lnet/minecraft/world/phys/AABB;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void csg$within(WorldBorder border, AABB box, CallbackInfoReturnable<Boolean> cir)
    {
        if (BorderVolume.active(border) != null) cir.setReturnValue(true);
    }

    @Inject(method = "getEntityWorldBorderCollisions(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Z)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void csg$collisions(Level level, Entity entity, AABB box, boolean includeBorder,
                                       CallbackInfoReturnable<List<VoxelShape>> cir)
    {
        if (!includeBorder || entity == null) return;
        Volume volume = BorderVolume.active(level.getWorldBorder());
        if (volume == null) return;
        VoxelShape wall = BorderCollision.around(volume, entity, box);
        if (wall.isEmpty()) return;
        List<VoxelShape> shapes = new ArrayList<>(cir.getReturnValue());
        shapes.add(wall);
        cir.setReturnValue(shapes);
    }
}
