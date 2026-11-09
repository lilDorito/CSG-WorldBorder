package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.client.ClientBorders;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenGuiPacket()
{
    public void encode(FriendlyByteBuf buf) {}

    public static OpenGuiPacket decode(FriendlyByteBuf buf)
    {
        return new OpenGuiPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientBorders::openScreen);
    }
}
