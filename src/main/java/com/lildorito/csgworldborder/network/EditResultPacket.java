package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.client.ClientBorders;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record EditResultPacket(boolean ok, String message, String selected)
{
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBoolean(ok);
        buf.writeUtf(message);
        buf.writeUtf(selected);
    }

    public static EditResultPacket decode(FriendlyByteBuf buf)
    {
        return new EditResultPacket(buf.readBoolean(), buf.readUtf(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorders.editResult(this));
    }
}
