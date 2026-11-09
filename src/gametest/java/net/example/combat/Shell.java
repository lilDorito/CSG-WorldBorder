package net.example.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class Shell
{
    private final ServerLevel level;

    public Shell(ServerLevel level)
    {
        this.level = level;
    }

    public boolean hit(BlockPos pos, Entity breaker)
    {
        return level.destroyBlock(pos, false, breaker);
    }
}
