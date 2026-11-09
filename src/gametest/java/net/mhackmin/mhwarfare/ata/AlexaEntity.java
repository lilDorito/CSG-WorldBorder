package net.mhackmin.mhwarfare.ata;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class AlexaEntity
{
    private AlexaEntity() {}

    public static boolean explode(ServerLevel level, BlockPos pos)
    {
        return level.destroyBlock(pos, false, null);
    }
}
