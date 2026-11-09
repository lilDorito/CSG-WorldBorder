package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.world.level.border.WorldBorder;

import javax.annotation.Nullable;

public interface BorderVolume
{
    Volume csg$volume();

    @Nullable
    static Volume active(@Nullable WorldBorder border)
    {
        if (border == null) return null;
        Volume volume = ((BorderVolume) border).csg$volume();
        return volume.isVanilla() ? null : volume;
    }

    static Volume of(WorldBorder border)
    {
        return ((BorderVolume) border).csg$volume();
    }
}
