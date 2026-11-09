package com.lildorito.csgworldborder.border;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

public final class BorderRaycast
{
    private BorderRaycast() {}

    public static boolean applies(CollisionContext context, BlockGetter getter, Vec3 from)
    {
        if (!(context instanceof EntityCollisionContext entityContext)) return false;
        if (!(entityContext.getEntity() instanceof Projectile)) return false;
        if (!(getter instanceof Level level) || !BorderSettings.of(level).blockProjectiles()) return false;
        return level.getWorldBorder().isWithinBounds(BlockPos.containing(from));
    }

    public static boolean leaves(Entity entity, double x, double y, double z)
    {
        if (!(entity instanceof Projectile) || entity.isRemoved()) return false;
        Level level = entity.level();
        if (level.isClientSide || !BorderSettings.of(level).blockProjectiles()) return false;
        WorldBorder border = level.getWorldBorder();
        return border.isWithinBounds(entity.blockPosition()) && !border.isWithinBounds(BlockPos.containing(x, y, z));
    }

    public static BlockHitResult impact(Entity entity, double x, double y, double z)
    {
        Vec3 from = entity.position();
        Vec3 to = new Vec3(x, y, z);
        BlockHitResult hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.BLOCK) return hit;
        Vec3 motion = to.subtract(from);
        return new BlockHitResult(from, Direction.getNearest(motion.x, motion.y, motion.z).getOpposite(), BlockPos.containing(to), false);
    }

    public static boolean outside(BlockGetter getter, BlockPos pos)
    {
        return getter instanceof Level level && !level.getWorldBorder().isWithinBounds(pos);
    }
}
