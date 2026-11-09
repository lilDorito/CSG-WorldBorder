package com.lildorito.csgworldborder.border;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;

public final class ProtectedBlockList extends ObjectArrayList<BlockPos>
{
    private final Level level;

    public ProtectedBlockList(Level level)
    {
        this.level = level;
    }

    @Override
    public boolean add(BlockPos pos)
    {
        return !BorderProtection.isProtected(level, pos) && super.add(pos);
    }

    @Override
    public void add(int index, BlockPos pos)
    {
        if (!BorderProtection.isProtected(level, pos)) super.add(index, pos);
    }

    @Override
    public boolean addAll(Collection<? extends BlockPos> positions)
    {
        return super.addAll(allowed(positions));
    }

    @Override
    public boolean addAll(int index, Collection<? extends BlockPos> positions)
    {
        return super.addAll(index, allowed(positions));
    }

    @Override
    public boolean addAll(int index, ObjectList<? extends BlockPos> positions)
    {
        return super.addAll(index, allowed(positions));
    }

    @Override
    public void addElements(int index, BlockPos[] positions, int offset, int length)
    {
        ObjectArrayList<BlockPos> kept = allowed(ObjectArrayList.wrap(positions).subList(offset, offset + length));
        super.addElements(index, kept.elements(), 0, kept.size());
    }

    private ObjectArrayList<BlockPos> allowed(Collection<? extends BlockPos> positions)
    {
        ObjectArrayList<BlockPos> kept = new ObjectArrayList<>(positions.size());
        for (BlockPos pos : positions)
        {
            if (!BorderProtection.isProtected(level, pos)) kept.add(pos);
        }
        return kept;
    }
}
