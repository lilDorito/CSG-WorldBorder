package com.lildorito.csgworldborder.client.compat;

import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.MapOutline;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class AtaMapBridge
{
    private static final int SHADE = 0xB0000000;
    private static final int LINE = 0xFFFF5050;

    private static final MapOutlineCache CACHE = new MapOutlineCache();

    private AtaMapBridge() {}

    public static boolean shade(GuiGraphics g, int left, int top, int right, int bottom,
                                double centerX, double centerY, double viewX, double viewZ, double scale)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(scale > 0.0)) return false;
        Volume volume = BorderVolume.active(minecraft.level.getWorldBorder());
        if (volume == null) return false;

        double wx0 = viewX + (left - centerX) * scale;
        double wx1 = viewX + (right - centerX) * scale;
        double wz0 = viewZ + (top - centerY) * scale;
        double wz1 = viewZ + (bottom - centerY) * scale;
        int cells = Math.max(64, (right - left) / 2);
        MapOutline.Grid grid = CACHE.grid(volume, wx0, wz0, wx1, wz1, cells);
        if (grid == null)
        {
            g.fill(left, top, right, bottom, SHADE);
            return true;
        }

        int s = grid.step();
        int gx0 = clamp(screen(grid.originX(), viewX, centerX, scale), left, right);
        int gx1 = clamp(screen(grid.originX() + (double) grid.width() * s, viewX, centerX, scale), left, right);
        int gy0 = clamp(screen(grid.originZ(), viewZ, centerY, scale), top, bottom);
        int gy1 = clamp(screen(grid.originZ() + (double) grid.depth() * s, viewZ, centerY, scale), top, bottom);
        if (gy0 > top) g.fill(left, top, right, gy0, SHADE);
        if (gy1 < bottom) g.fill(left, gy1, right, bottom, SHADE);
        if (gx0 > left) g.fill(left, gy0, gx0, gy1, SHADE);
        if (gx1 < right) g.fill(gx1, gy0, right, gy1, SHADE);

        for (int gz = 0; gz < grid.depth(); gz++)
        {
            int y0 = clamp(screen(grid.originZ() + (double) gz * s, viewZ, centerY, scale), top, bottom);
            int y1 = clamp(screen(grid.originZ() + (double) (gz + 1) * s, viewZ, centerY, scale), top, bottom);
            if (y1 <= y0) continue;
            int start = -1;
            for (int gx = 0; gx <= grid.width(); gx++)
            {
                boolean outside = gx < grid.width() && !grid.at(gx, gz);
                if (outside && start < 0) start = gx;
                if (!outside && start >= 0)
                {
                    int x0 = clamp(screen(grid.originX() + (double) start * s, viewX, centerX, scale), left, right);
                    int x1 = clamp(screen(grid.originX() + (double) gx * s, viewX, centerX, scale), left, right);
                    if (x1 > x0) g.fill(x0, y0, x1, y1, SHADE);
                    start = -1;
                }
            }
        }

        for (MapOutline.Segment seg : MapOutline.trace(grid))
        {
            int x0 = screen(seg.x1(), viewX, centerX, scale);
            int x1 = screen(seg.x2(), viewX, centerX, scale);
            int y0 = screen(seg.z1(), viewZ, centerY, scale);
            int y1 = screen(seg.z2(), viewZ, centerY, scale);
            if (x0 == x1)
            {
                if (x0 < left || x0 >= right) continue;
                int a = clamp(Math.min(y0, y1), top, bottom), b = clamp(Math.max(y0, y1), top, bottom);
                if (b > a) g.fill(x0, a, x0 + 1, b, LINE);
            }
            else
            {
                if (y0 < top || y0 >= bottom) continue;
                int a = clamp(Math.min(x0, x1), left, right), b = clamp(Math.max(x0, x1), left, right);
                if (b > a) g.fill(a, y0, b, y0 + 1, LINE);
            }
        }
        return true;
    }

    private static int screen(double world, double view, double center, double scale)
    {
        return (int) Math.round(center + (world - view) / scale);
    }

    private static int clamp(int value, int min, int max)
    {
        return value < min ? min : Math.min(value, max);
    }
}
