package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.csg.Protection;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.border.WorldBorder;

import javax.annotation.Nullable;

public final class BorderProtection
{
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private BorderProtection() {}

    public static boolean isProtected(LevelReader reader, BlockPos pos)
    {
        if (!(reader instanceof Level level)) return false;
        BorderSettings.Values settings = BorderSettings.of(level);
        WorldBorder border = level.getWorldBorder();
        Volume volume = BorderVolume.active(border);
        Protection area = volume == null ? Protection.NONE : volume.areas().at(pos.getX(), pos.getY(), pos.getZ());
        if (!settings.protectEdge() && area == Protection.NONE) return false;
        if (!border.isWithinBounds(pos)) return false;
        if (settings.protectMapBlocksOnly() && PlayerBlocks.isPlayerBlock(level, pos)) return false;
        if (area == Protection.INSIDE) return true;
        int depth = settings.protectThickness();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (settings.protectWalls())
        {
            for (Direction side : SIDES)
            {
                if (reaches(border, pos, side, depth, cursor)) return true;
            }
        }
        if (settings.protectFloor() && reaches(border, pos, Direction.DOWN, depth, cursor)) return true;
        return settings.protectCeiling() && reaches(border, pos, Direction.UP, depth, cursor);
    }

    public static boolean blocks(LevelReader reader, BlockPos pos, @Nullable Entity actor)
    {
        if (actor instanceof Player player && player.isCreative()) return false;
        return isProtected(reader, pos);
    }

    private static boolean reaches(WorldBorder border, BlockPos pos, Direction side, int depth, BlockPos.MutableBlockPos cursor)
    {
        for (int k = 1; k <= depth; k++)
        {
            cursor.setWithOffset(pos, side.getStepX() * k, side.getStepY() * k, side.getStepZ() * k);
            if (!border.isWithinBounds(cursor)) return true;
        }
        return false;
    }
}
