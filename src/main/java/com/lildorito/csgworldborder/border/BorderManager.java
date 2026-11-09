package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.csg.Volume;
import com.lildorito.csgworldborder.network.CsgNetwork;
import com.lildorito.csgworldborder.network.SettingsPacket;
import com.lildorito.csgworldborder.network.SyncBorderPacket;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CsgWorldBorder.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BorderManager
{
    private BorderManager() {}

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level) apply(level);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event)
    {
        BorderSettings.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        BorderSettings.unload();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player);
        CsgNetwork.send(player, new SettingsPacket(BorderSettings.server()));
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    public static void install(Level level, Volume volume)
    {
        volume.limitHeight(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    public static void apply(ServerLevel level)
    {
        CsgBorderData data = CsgBorderData.get(level);
        Volume volume = BorderVolume.of(level.getWorldBorder());
        install(level, volume);
        volume.define(data.expression(), data.shapes(), data.outline(), data.protectionRules(), Util.getMillis());
    }

    public static void update(ServerLevel level)
    {
        apply(level);
        CsgNetwork.sendToDimension(packet(level));
    }

    private static void sync(ServerPlayer player)
    {
        CsgNetwork.send(player, packet(player.serverLevel()));
        PlayerBlocks.syncTo(player);
    }

    private static SyncBorderPacket packet(ServerLevel level)
    {
        return SyncBorderPacket.of(level);
    }
}
