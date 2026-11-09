package com.lildorito.csgworldborder.csg;

import java.util.ArrayList;
import java.util.List;

public final class MapOutline
{
    public static final int DEFAULT_CELLS_ACROSS = 384;

    public record Segment(double x1, double z1, double x2, double z2) {}

    public record Grid(int originX, int originZ, int step, int width, int depth, boolean[] inside)
    {
        public boolean at(int gx, int gz)
        {
            return gx >= 0 && gz >= 0 && gx < width && gz < depth && inside[gz * width + gx];
        }
    }

    private MapOutline() {}

    public static Grid sample(Volume volume, double minX, double minZ, double maxX, double maxZ, int cellsAcross)
    {
        double[] rect = volume.mapRect();
        if (rect == null) return null;
        if (!Double.isNaN(rect[0])) minX = Math.max(minX, rect[0] - 1.0);
        if (!Double.isNaN(rect[1])) minZ = Math.max(minZ, rect[1] - 1.0);
        if (!Double.isNaN(rect[2])) maxX = Math.min(maxX, rect[2] + 1.0);
        if (!Double.isNaN(rect[3])) maxZ = Math.min(maxZ, rect[3] + 1.0);
        if (maxX <= minX || maxZ <= minZ) return null;

        return sampleWithStep(volume, minX, minZ, maxX, maxZ, stepFor(Math.max(maxX - minX, maxZ - minZ), cellsAcross));
    }

    public static int stepFor(double span, int cellsAcross)
    {
        int step = 1;
        while (span / step > Math.max(16, cellsAcross) && step < (1 << 20)) step <<= 1;
        return step;
    }

    public static Grid sampleWithStep(Volume volume, double minX, double minZ, double maxX, double maxZ, int step)
    {
        double[] rect = volume.mapRect();
        if (rect == null) return null;
        if (!Double.isNaN(rect[0])) minX = Math.max(minX, rect[0] - 1.0);
        if (!Double.isNaN(rect[1])) minZ = Math.max(minZ, rect[1] - 1.0);
        if (!Double.isNaN(rect[2])) maxX = Math.min(maxX, rect[2] + 1.0);
        if (!Double.isNaN(rect[3])) maxZ = Math.min(maxZ, rect[3] + 1.0);
        if (maxX <= minX || maxZ <= minZ) return null;
        int gx0 = Math.floorDiv((int) Math.floor(minX), step) - 1;
        int gz0 = Math.floorDiv((int) Math.floor(minZ), step) - 1;
        int gx1 = Math.floorDiv((int) Math.ceil(maxX), step) + 1;
        int gz1 = Math.floorDiv((int) Math.ceil(maxZ), step) + 1;
        int width = gx1 - gx0 + 1;
        int depth = gz1 - gz0 + 1;
        boolean[] inside = new boolean[width * depth];
        int half = step / 2;
        for (int gz = 0; gz < depth; gz++)
        {
            for (int gx = 0; gx < width; gx++)
            {
                inside[gz * width + gx] = volume.mapContains((gx0 + gx) * step + half, (gz0 + gz) * step + half);
            }
        }
        return new Grid(gx0 * step, gz0 * step, step, width, depth, inside);
    }

    public static List<Segment> trace(Volume volume, double minX, double minZ, double maxX, double maxZ, int cellsAcross)
    {
        Grid grid = sample(volume, minX, minZ, maxX, maxZ, cellsAcross);
        return grid == null ? List.of() : trace(grid);
    }

    public static List<Segment> trace(Grid grid)
    {
        List<Segment> out = new ArrayList<>();
        int s = grid.step();
        for (int gx = 1; gx < grid.width(); gx++)
        {
            int start = -1;
            for (int gz = 0; gz <= grid.depth(); gz++)
            {
                boolean edge = gz < grid.depth() && grid.at(gx - 1, gz) != grid.at(gx, gz);
                if (edge && start < 0) start = gz;
                if (!edge && start >= 0)
                {
                    double x = grid.originX() + (double) gx * s;
                    out.add(new Segment(x, grid.originZ() + (double) start * s, x, grid.originZ() + (double) gz * s));
                    start = -1;
                }
            }
        }
        for (int gz = 1; gz < grid.depth(); gz++)
        {
            int start = -1;
            for (int gx = 0; gx <= grid.width(); gx++)
            {
                boolean edge = gx < grid.width() && grid.at(gx, gz - 1) != grid.at(gx, gz);
                if (edge && start < 0) start = gx;
                if (!edge && start >= 0)
                {
                    double z = grid.originZ() + (double) gz * s;
                    out.add(new Segment(grid.originX() + (double) start * s, z, grid.originX() + (double) gx * s, z));
                    start = -1;
                }
            }
        }
        return out;
    }
}
