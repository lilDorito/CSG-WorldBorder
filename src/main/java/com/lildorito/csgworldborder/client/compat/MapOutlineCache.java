package com.lildorito.csgworldborder.client.compat;

import com.lildorito.csgworldborder.csg.MapOutline;
import com.lildorito.csgworldborder.csg.Volume;

import javax.annotation.Nullable;
import java.util.List;

public final class MapOutlineCache
{
    private static final int SNAP_CELLS = 32;

    private record Key(Volume volume, int revision, int step, int x0, int z0, int x1, int z1) {}

    private Key key;
    @Nullable
    private MapOutline.Grid grid;
    private List<MapOutline.Segment> segments = List.of();

    @Nullable
    public MapOutline.Grid grid(Volume volume, double minX, double minZ, double maxX, double maxZ, int cellsAcross)
    {
        update(volume, minX, minZ, maxX, maxZ, cellsAcross);
        return grid;
    }

    public List<MapOutline.Segment> segments(Volume volume, double minX, double minZ, double maxX, double maxZ, int cellsAcross)
    {
        update(volume, minX, minZ, maxX, maxZ, cellsAcross);
        return segments;
    }

    private void update(Volume volume, double minX, double minZ, double maxX, double maxZ, int cellsAcross)
    {
        int step = MapOutline.stepFor(Math.max(maxX - minX, maxZ - minZ), cellsAcross);
        int snap = step * SNAP_CELLS;
        int x0 = Math.floorDiv((int) Math.floor(minX), snap) * snap;
        int z0 = Math.floorDiv((int) Math.floor(minZ), snap) * snap;
        int x1 = (Math.floorDiv((int) Math.ceil(maxX), snap) + 1) * snap;
        int z1 = (Math.floorDiv((int) Math.ceil(maxZ), snap) + 1) * snap;
        Key next = new Key(volume, volume.revision(), step, x0, z0, x1, z1);
        if (next.equals(key)) return;
        key = next;
        grid = MapOutline.sampleWithStep(volume, x0, z0, x1, z1, step);
        segments = grid == null ? List.of() : MapOutline.trace(grid);
    }
}
