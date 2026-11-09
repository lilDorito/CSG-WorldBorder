package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.CsgWorldBorder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CsgNetwork
{
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CsgWorldBorder.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private CsgNetwork() {}

    public static void register()
    {
        CHANNEL.messageBuilder(SyncBorderPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncBorderPacket::encode)
                .decoder(SyncBorderPacket::decode)
                .consumerMainThread(SyncBorderPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenGuiPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenGuiPacket::encode)
                .decoder(OpenGuiPacket::decode)
                .consumerMainThread(OpenGuiPacket::handle)
                .add();
        CHANNEL.messageBuilder(EditResultPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EditResultPacket::encode)
                .decoder(EditResultPacket::decode)
                .consumerMainThread(EditResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(SettingsPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SettingsPacket::encode)
                .decoder(SettingsPacket::decode)
                .consumerMainThread(SettingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerBlocksPacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerBlocksPacket::encode)
                .decoder(PlayerBlocksPacket::decode)
                .consumerMainThread(PlayerBlocksPacket::handle)
                .add();
        CHANNEL.messageBuilder(EditBorderPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditBorderPacket::encode)
                .decoder(EditBorderPacket::decode)
                .consumerMainThread(EditBorderPacket::handle)
                .add();
    }

    public static void send(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet)
    {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToDimension(ResourceKey<Level> dimension, Object packet)
    {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    public static void sendToDimension(SyncBorderPacket packet)
    {
        CHANNEL.send(PacketDistributor.DIMENSION.with(packet::dimension), packet);
    }
}
