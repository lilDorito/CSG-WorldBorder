package com.atsuishio.superbwarfare.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class MelonBombEntity
{
    private MelonBombEntity() {}

    public static boolean blast(ServerLevel level, BlockPos pos)
    {
        return level.destroyBlock(pos, false);
    }
}
