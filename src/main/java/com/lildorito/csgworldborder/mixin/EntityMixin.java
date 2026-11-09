package com.lildorito.csgworldborder.mixin;

import com.lildorito.csgworldborder.border.BorderCollision;
import com.lildorito.csgworldborder.border.BorderRaycast;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin
{
    @Shadow
    protected boolean firstTick;

    @Unique
    private static final ThreadLocal<Boolean> CSG$STOPPING = ThreadLocal.withInitial(() -> false);

    @Unique
    private AABB csg$before;

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void csg$projectileLeaves(double x, double y, double z, CallbackInfo ci)
    {
        Entity self = (Entity) (Object) this;
        if (firstTick || CSG$STOPPING.get() || !BorderRaycast.leaves(self, x, y, z)) return;
        ci.cancel();
        CSG$STOPPING.set(true);
        try
        {
            ((ProjectileAccessor) self).csg$onHit(BorderRaycast.impact(self, x, y, z));
        }
        finally
        {
            CSG$STOPPING.set(false);
        }
        if (!self.isRemoved()) self.discard();
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void csg$beforeMove(MoverType type, Vec3 movement, CallbackInfo ci)
    {
        csg$before = null;
        Entity self = (Entity) (Object) this;
        if (self.noPhysics) return;
        Volume volume = BorderVolume.active(self.level().getWorldBorder());
        if (volume == null) return;
        AABB box = self.getBoundingBox();
        Vec3 center = box.getCenter();
        if (volume.contains(center.x, center.y, center.z)) csg$before = box;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void csg$afterMove(MoverType type, Vec3 movement, CallbackInfo ci)
    {
        AABB before = csg$before;
        csg$before = null;
        if (before == null) return;
        Entity self = (Entity) (Object) this;
        Volume volume = BorderVolume.active(self.level().getWorldBorder());
        if (volume == null) return;
        AABB after = self.getBoundingBox();
        if (after.equals(before)) return;
        if (!volume.overlapsNewOutside(after.minX, after.minY, after.minZ, after.maxX, after.maxY, after.maxZ,
                before.minX, before.minY, before.minZ, before.maxX, before.maxY, before.maxZ)) return;

        double mx = after.minX - before.minX;
        double my = after.minY - before.minY;
        double mz = after.minZ - before.minZ;
        VoxelShape wall = BorderCollision.build(volume, before.expandTowards(mx, my, mz), before);
        List<VoxelShape> shapes = List.of(wall);
        AABB box = before;
        double dy = Shapes.collide(Direction.Axis.Y, box, shapes, my);
        box = box.move(0.0, dy, 0.0);
        boolean xFirst = Math.abs(mx) >= Math.abs(mz);
        double dx = xFirst ? Shapes.collide(Direction.Axis.X, box, shapes, mx) : 0.0;
        if (xFirst) box = box.move(dx, 0.0, 0.0);
        double dz = Shapes.collide(Direction.Axis.Z, box, shapes, mz);
        box = box.move(0.0, 0.0, dz);
        if (!xFirst)
        {
            dx = Shapes.collide(Direction.Axis.X, box, shapes, mx);
            box = box.move(dx, 0.0, 0.0);
        }

        self.setPos(self.getX() + (box.minX - after.minX), self.getY() + (box.minY - after.minY), self.getZ() + (box.minZ - after.minZ));
        Vec3 motion = self.getDeltaMovement();
        self.setDeltaMovement(dx != mx ? 0.0 : motion.x, dy != my ? 0.0 : motion.y, dz != mz ? 0.0 : motion.z);
        if (dy != my && my < 0.0) self.setOnGround(true);
    }
}
