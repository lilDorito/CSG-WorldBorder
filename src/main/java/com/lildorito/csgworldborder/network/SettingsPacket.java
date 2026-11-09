package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.client.ClientBorders;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SettingsPacket(BorderSettings.Values values)
{
    public void encode(FriendlyByteBuf buf)
    {
        values.write(buf);
    }

    public static SettingsPacket decode(FriendlyByteBuf buf)
    {
        return new SettingsPacket(BorderSettings.Values.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorders.receiveSettings(values));
    }
}
