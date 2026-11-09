package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderCollision;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.border.RawCorners;
import com.lildorito.csgworldborder.csg.Square;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin implements BorderVolume
{
    @Unique
    private final Volume csg$volume = new Volume(this::csg$square);

    @Unique
    private boolean csg$changed;

    @Shadow
    @Final
    private List<BorderChangeListener> listeners;

    @Shadow
    public abstract double getCenterX();

    @Shadow
    public abstract double getCenterZ();

    @Shadow
    public abstract double getMinX();

    @Shadow
    public abstract double getMaxX();

    @Shadow
    public abstract double getMinZ();

    @Shadow
    public abstract double getMaxZ();

    @Override
    public Volume csg$volume()
    {
        return csg$volume;
    }

    @Unique
    private int csg$rectRevision = -1;

    @Unique
    private double[] csg$rect;

    @Unique
    private Square csg$square()
    {
        RawCorners.enter();
        try
        {
            return new Square(getMinX(), getMaxX(), getMinZ(), getMaxZ());
        }
        finally
        {
            RawCorners.exit();
        }
    }

    @Unique
    private void csg$corner(int side, CallbackInfoReturnable<Double> cir)
    {
        if (RawCorners.active()) return;
        Volume volume = csg$active();
        if (volume == null) return;
        int revision = volume.revision();
        if (revision != csg$rectRevision || csg$rect == null)
        {
            csg$rect = volume.mapRect();
            csg$rectRevision = revision;
        }
        double[] rect = csg$rect;
        if (rect == null || Double.isNaN(rect[side])) return;
        cir.setReturnValue(rect[side]);
    }

    @Inject(method = "getMinX", at = @At("HEAD"), cancellable = true)
    private void csg$minX(CallbackInfoReturnable<Double> cir)
    {
        csg$corner(0, cir);
    }

    @Inject(method = "getMinZ", at = @At("HEAD"), cancellable = true)
    private void csg$minZ(CallbackInfoReturnable<Double> cir)
    {
        csg$corner(1, cir);
    }

    @Inject(method = "getMaxX", at = @At("HEAD"), cancellable = true)
    private void csg$maxX(CallbackInfoReturnable<Double> cir)
    {
        csg$corner(2, cir);
    }

    @Inject(method = "getMaxZ", at = @At("HEAD"), cancellable = true)
    private void csg$maxZ(CallbackInfoReturnable<Double> cir)
    {
        csg$corner(3, cir);
    }

    @Unique
    private Volume csg$active()
    {
        return csg$volume.isVanilla() ? null : csg$volume;
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void csg$isWithinBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        Volume volume = csg$active();
        if (volume != null) cir.setReturnValue(volume.contains(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("HEAD"), cancellable = true)
    private void csg$isWithinChunk(ChunkPos pos, CallbackInfoReturnable<Boolean> cir)
    {
        Volume volume = csg$active();
        if (volume != null)
        {
            cir.setReturnValue(volume.intersectsColumns(pos.getMinBlockX(), pos.getMaxBlockX() + 1,
                    pos.getMinBlockZ(), pos.getMaxBlockZ() + 1));
        }
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true)
    private void csg$isWithinBox(AABB box, CallbackInfoReturnable<Boolean> cir)
    {
        Volume volume = csg$active();
        if (volume != null)
        {
            cir.setReturnValue(volume.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
    }

    @Inject(method = "clampToBounds", at = @At("HEAD"), cancellable = true)
    private void csg$clamp(double x, double y, double z, CallbackInfoReturnable<BlockPos> cir)
    {
        Volume volume = csg$active();
        if (volume == null) return;
        int[] inside = volume.nearestInside(x, y, z);
        if (inside != null) cir.setReturnValue(new BlockPos(inside[0], inside[1], inside[2]));
    }

    @Inject(method = "getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void csg$distance(Entity entity, CallbackInfoReturnable<Double> cir)
    {
        Volume volume = csg$active();
        if (volume == null) return;
        AABB box = entity.getBoundingBox();
        cir.setReturnValue(volume.signedDistance(entity.getX(), (box.minY + box.maxY) * 0.5, entity.getZ()));
    }

    @Inject(method = "isInsideCloseToBorder", at = @At("HEAD"), cancellable = true)
    private void csg$closeToBorder(Entity entity, AABB area, CallbackInfoReturnable<Boolean> cir)
    {
        Volume volume = csg$active();
        if (volume != null) cir.setReturnValue(BorderCollision.prepare(volume, entity, area));
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void csg$collisionShape(CallbackInfoReturnable<VoxelShape> cir)
    {
        VoxelShape shape = BorderCollision.take();
        if (shape != null)
        {
            cir.setReturnValue(shape);
            return;
        }
        if (csg$active() != null) cir.setReturnValue(Shapes.empty());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void csg$init(CallbackInfo ci)
    {
        csg$volume.addListener(region -> csg$changed = true);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void csg$tick(CallbackInfo ci)
    {
        csg$volume.tick(Util.getMillis());
        if (!csg$changed) return;
        csg$changed = false;
        WorldBorder self = (WorldBorder) (Object) this;
        for (BorderChangeListener listener : new ArrayList<>(listeners))
        {
            if (listener.getClass().getName().startsWith("net.minecraft.")) continue;
            listener.onBorderCenterSet(self, getCenterX(), getCenterZ());
        }
    }

    @Inject(method = "getDistanceToBorder(DD)D", at = @At("RETURN"), cancellable = true)
    private void csg$flatDistance(double x, double z, CallbackInfoReturnable<Double> cir)
    {
        if (csg$active() != null) cir.setReturnValue(Math.min(cir.getReturnValueD(), 0.0));
    }
}
