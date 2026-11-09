package com.lildorito.csgworldborder.api;

import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.MapOutline;
import com.lildorito.csgworldborder.csg.Outline;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class CsgWorldBorderApi
{
    private CsgWorldBorderApi() {}

    public record Rect(double minX, double minZ, double maxX, double maxZ) {}

    public static boolean isActive(Level level)
    {
        return volume(level) != null;
    }

    public static Outline classicOutline(Level level)
    {
        return BorderVolume.of(level.getWorldBorder()).outline();
    }

    public static boolean isInside(Level level, BlockPos pos)
    {
        return level.getWorldBorder().isWithinBounds(pos);
    }

    public static boolean isOnMap(Level level, int x, int z)
    {
        Volume volume = volume(level);
        if (volume == null)
        {
            var border = level.getWorldBorder();
            return x + 1 > border.getMinX() && x < border.getMaxX() && z + 1 > border.getMinZ() && z < border.getMaxZ();
        }
        return volume.mapContains(x, z);
    }

    public static Optional<Rect> mapBounds(Level level)
    {
        var border = level.getWorldBorder();
        return Optional.of(new Rect(border.getMinX(), border.getMinZ(), border.getMaxX(), border.getMaxZ()));
    }

    public static List<MapOutline.Segment> mapOutline(Level level, double minX, double minZ, double maxX, double maxZ)
    {
        return mapOutline(level, minX, minZ, maxX, maxZ, MapOutline.DEFAULT_CELLS_ACROSS);
    }

    public static List<MapOutline.Segment> mapOutline(Level level, double minX, double minZ, double maxX, double maxZ,
                                                      int cellsAcross)
    {
        Volume volume = volume(level);
        if (volume == null) return List.of();
        return MapOutline.trace(volume, minX, minZ, maxX, maxZ, cellsAcross);
    }

    public static int revision(Level level)
    {
        Volume volume = volume(level);
        return volume == null ? 0 : volume.revision();
    }

    public static Optional<BlockPos> nearestInside(Level level, double x, double y, double z)
    {
        Volume volume = volume(level);
        if (volume == null) return Optional.of(level.getWorldBorder().clampToBounds(x, y, z));
        int[] inside = volume.nearestInside(x, y, z);
        return inside == null ? Optional.empty() : Optional.of(new BlockPos(inside[0], inside[1], inside[2]));
    }

    @Nullable
    private static Volume volume(Level level)
    {
        return BorderVolume.active(level.getWorldBorder());
    }
}
