package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.csg.Volume;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public final class BorderCollision
{
    private static final ThreadLocal<Query> PENDING = new ThreadLocal<>();

    private record Query(Volume volume, AABB area, AABB own) {}

    private BorderCollision() {}

    public static boolean prepare(Volume volume, Entity entity, AABB area)
    {
        AABB own = entity.getBoundingBox();
        PENDING.remove();
        if (!volume.contains(own.getCenter().x, own.getCenter().y, own.getCenter().z)) return false;
        if (!volume.hasSolidCell(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ)) return false;
        PENDING.set(new Query(volume, area, own));
        return true;
    }

    public static VoxelShape around(Volume volume, Entity entity, AABB area)
    {
        if (!prepare(volume, entity, area)) return Shapes.empty();
        VoxelShape shape = take();
        return shape == null ? Shapes.empty() : shape;
    }

    @Nullable
    public static VoxelShape take()
    {
        Query query = PENDING.get();
        if (query == null) return null;
        PENDING.remove();
        return build(query.volume(), query.area(), query.own());
    }

    public static VoxelShape build(Volume volume, AABB area, AABB own)
    {
        int x0 = floor(area.minX), y0 = floor(area.minY), z0 = floor(area.minZ);
        int sx = floor(area.maxX) - x0 + 1;
        int sy = floor(area.maxY) - y0 + 1;
        int sz = floor(area.maxZ) - z0 + 1;
        BitSetDiscreteVoxelShape cells = new BitSetDiscreteVoxelShape(sx, sy, sz);
        boolean[] any = {false};
        boolean fits = volume.forEachSolidCell(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ,
                own.minX, own.minY, own.minZ, own.maxX, own.maxY, own.maxZ,
                (x, y, z) ->
                {
                    cells.fill(x - x0, y - y0, z - z0);
                    any[0] = true;
                });
        if (!fits || !any[0]) return Shapes.empty();
        return new ArrayVoxelShape(cells, coords(x0, sx), coords(y0, sy), coords(z0, sz));
    }

    private static DoubleArrayList coords(int start, int size)
    {
        DoubleArrayList list = new DoubleArrayList(size + 1);
        for (int i = 0; i <= size; i++) list.add(start + i);
        return list;
    }

    private static int floor(double value)
    {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
