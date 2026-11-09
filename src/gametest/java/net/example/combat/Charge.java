package net.example.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public final class Charge
{
    private final Level level;

    public Charge(Level level)
    {
        this.level = level;
    }

    public boolean explode(BlockPos pos)
    {
        return level.destroyBlock(pos, false);
    }

    public static boolean inLambda(Level level, BlockPos pos)
    {
        Supplier<Boolean> blast = () -> level.destroyBlock(pos, true);
        return blast.get();
    }
}
