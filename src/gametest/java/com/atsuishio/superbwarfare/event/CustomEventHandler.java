package com.atsuishio.superbwarfare.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class CustomEventHandler
{
    private CustomEventHandler() {}

    public static boolean bulletHit(Level level, BlockPos pos)
    {
        return level.destroyBlock(pos, false, null);
    }
}
