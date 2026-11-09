package com.lildorito.csgworldborder.border;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class BorderRaycast
{
    private static final int MAX_STEPS = 1024;

    private BorderRaycast() {}

    public static BlockHitResult clip(Level level, ClipContext context, @Nullable Entity entity)
    {
        BlockHitResult hit = level.clip(context);
        if (!(entity instanceof Projectile) || !BorderSettings.of(level).blockProjectiles()) return hit;
        BlockHitResult wall = wall(level, context.getFrom(), context.getTo());
        if (wall == null) return hit;
        if (hit.getType() == HitResult.Type.MISS
                || wall.getLocation().distanceToSqr(context.getFrom()) < hit.getLocation().distanceToSqr(context.getFrom()))
        {
            return wall;
        }
        return hit;
    }

    @Nullable
    public static BlockHitResult wall(Level level, Vec3 from, Vec3 to)
    {
        WorldBorder border = level.getWorldBorder();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos(floor(from.x), floor(from.y), floor(from.z));
        if (!border.isWithinBounds(cell)) return null;
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        if (dx * dx + dy * dy + dz * dz < 1.0E-12) return null;

        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double maxX = dx == 0 ? Double.POSITIVE_INFINITY : ((dx > 0 ? cell.getX() + 1 - from.x : from.x - cell.getX()) * deltaX);
        double maxY = dy == 0 ? Double.POSITIVE_INFINITY : ((dy > 0 ? cell.getY() + 1 - from.y : from.y - cell.getY()) * deltaY);
        double maxZ = dz == 0 ? Double.POSITIVE_INFINITY : ((dz > 0 ? cell.getZ() + 1 - from.z : from.z - cell.getZ()) * deltaZ);

        for (int i = 0; i < MAX_STEPS; i++)
        {
            double t;
            Direction face;
            if (maxX <= maxY && maxX <= maxZ)
            {
                t = maxX;
                maxX += deltaX;
                cell.move(stepX, 0, 0);
                face = stepX > 0 ? Direction.WEST : Direction.EAST;
            }
            else if (maxY <= maxZ)
            {
                t = maxY;
                maxY += deltaY;
                cell.move(0, stepY, 0);
                face = stepY > 0 ? Direction.DOWN : Direction.UP;
            }
            else
            {
                t = maxZ;
                maxZ += deltaZ;
                cell.move(0, 0, stepZ);
                face = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
            }
            if (t > 1.0) return null;
            if (!border.isWithinBounds(cell))
            {
                Vec3 point = new Vec3(from.x + dx * t, from.y + dy * t, from.z + dz * t);
                return new BlockHitResult(point, face, cell.immutable(), false);
            }
        }
        return null;
    }

    private static int floor(double value)
    {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
