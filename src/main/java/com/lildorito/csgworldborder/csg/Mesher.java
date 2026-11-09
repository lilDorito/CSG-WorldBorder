package com.lildorito.csgworldborder.csg;

import java.util.ArrayList;
import java.util.List;

public final class Mesher
{
    private Mesher() {}

    public static List<Tile> mesh(Volume volume, int sectionX, int sectionY, int sectionZ)
    {
        int fill = volume.sectionFill(sectionX, sectionY, sectionZ);
        if (fill >= 0
                && volume.sectionFill(sectionX + 1, sectionY, sectionZ) == fill
                && volume.sectionFill(sectionX, sectionY + 1, sectionZ) == fill
                && volume.sectionFill(sectionX, sectionY, sectionZ + 1) == fill)
        {
            return List.of();
        }

        List<Tile> tiles = new ArrayList<>();
        int bx = sectionX << 4;
        int by = sectionY << 4;
        int bz = sectionZ << 4;
        int[] mask = new int[256];

        for (int axis = 0; axis < 3; axis++)
        {
            for (int layer = 1; layer <= 16; layer++)
            {
                boolean any = false;
                for (int v = 0; v < 16; v++)
                {
                    for (int u = 0; u < 16; u++)
                    {
                        int x, y, z;
                        if (axis == Tile.X) { x = bx + layer; y = by + v; z = bz + u; }
                        else if (axis == Tile.Y) { x = bx + u; y = by + layer; z = bz + v; }
                        else { x = bx + u; y = by + v; z = bz + layer; }

                        boolean positive = volume.contains(x, y, z);
                        boolean negative = axis == Tile.X ? volume.contains(x - 1, y, z)
                                : axis == Tile.Y ? volume.contains(x, y - 1, z)
                                : volume.contains(x, y, z - 1);

                        int value = positive == negative ? 0 : positive ? 2 : 1;
                        mask[(v << 4) | u] = value;
                        if (value != 0) any = true;
                    }
                }
                if (!any) continue;

                int plane = (axis == Tile.X ? bx : axis == Tile.Y ? by : bz) + layer;
                int ou = axis == Tile.X ? bz : bx;
                int ov = axis == Tile.Y ? bz : by;
                greedy(mask, axis, plane, ou, ov, tiles);
            }
        }
        return tiles;
    }

    private static void greedy(int[] mask, int axis, int plane, int ou, int ov, List<Tile> out)
    {
        for (int v = 0; v < 16; v++)
        {
            for (int u = 0; u < 16; )
            {
                int value = mask[(v << 4) | u];
                if (value == 0)
                {
                    u++;
                    continue;
                }

                int width = 1;
                while (u + width < 16 && mask[(v << 4) | (u + width)] == value) width++;

                int height = 1;
                grow:
                while (v + height < 16)
                {
                    for (int k = 0; k < width; k++)
                    {
                        if (mask[((v + height) << 4) | (u + k)] != value) break grow;
                    }
                    height++;
                }

                for (int dv = 0; dv < height; dv++)
                {
                    for (int k = 0; k < width; k++) mask[((v + dv) << 4) | (u + k)] = 0;
                }

                out.add(new Tile(axis, plane, ou + u, ov + v, ou + u + width, ov + v + height, value == 2));
                u += width;
            }
        }
    }
}
