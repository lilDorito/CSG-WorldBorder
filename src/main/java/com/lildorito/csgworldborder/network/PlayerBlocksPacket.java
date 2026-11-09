package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.client.ClientBorders;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlayerBlocksPacket(ResourceKey<Level> dimension, boolean reset, long[] positions, int[] blocks)
{
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeResourceKey(dimension);
        buf.writeBoolean(reset);
        buf.writeVarInt(positions.length);
        for (int i = 0; i < positions.length; i++)
        {
            buf.writeLong(positions[i]);
            buf.writeVarInt(blocks[i]);
        }
    }

    public static PlayerBlocksPacket decode(FriendlyByteBuf buf)
    {
        ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);
        boolean reset = buf.readBoolean();
        int count = buf.readVarInt();
        long[] positions = new long[count];
        int[] blocks = new int[count];
        for (int i = 0; i < count; i++)
        {
            positions[i] = buf.readLong();
            blocks[i] = buf.readVarInt();
        }
        return new PlayerBlocksPacket(dimension, reset, positions, blocks);
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorders.receivePlayerBlocks(this));
    }
}
