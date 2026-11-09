package com.atsuishio.superbwarfare.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class C4Entity
{
    private C4Entity() {}

    public static boolean blast(Level level, BlockPos pos)
    {
        return level.destroyBlock(pos, false);
    }
}
