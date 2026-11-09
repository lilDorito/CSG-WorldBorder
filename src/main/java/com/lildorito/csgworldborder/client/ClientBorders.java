package com.lildorito.csgworldborder.client;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.border.BorderManager;
import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.ExpressionParser;
import com.lildorito.csgworldborder.csg.Node;
import com.lildorito.csgworldborder.csg.Shape;
import com.lildorito.csgworldborder.csg.Volume;
import com.lildorito.csgworldborder.network.EditResultPacket;
import com.lildorito.csgworldborder.network.PlayerBlocksPacket;
import com.lildorito.csgworldborder.network.SyncBorderPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CsgWorldBorder.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientBorders
{
    public static final KeyMapping OPEN_GUI = new KeyMapping("key.csgworldborder.gui", GLFW.GLFW_KEY_0, "key.categories.csgworldborder");

    private static final Map<ResourceKey<Level>, SyncBorderPacket> PENDING = new HashMap<>();
    @Nullable
    private static SyncBorderPacket state;
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Block>> PLAYER_BLOCKS = new HashMap<>();

    private ClientBorders() {}

    public static void receive(SyncBorderPacket packet)
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(packet.dimension()))
        {
            apply(level, packet);
            if (Minecraft.getInstance().screen instanceof BorderScreen screen) screen.onSync();
            return;
        }
        PENDING.put(packet.dimension(), packet);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        BorderManager.install(level, BorderVolume.of(level.getWorldBorder()));
        state = null;
        SyncBorderPacket packet = PENDING.remove(level.dimension());
        if (packet != null) apply(level, packet);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        PENDING.clear();
        PLAYER_BLOCKS.clear();
        state = null;
        BorderSettings.setClient(BorderSettings.Values.DEFAULT);
        BorderRenderer.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        while (OPEN_GUI.consumeClick())
        {
            if (Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null) openScreen();
        }
    }

    @Nullable
    public static SyncBorderPacket state()
    {
        return state;
    }

    public static void openScreen()
    {
        Minecraft.getInstance().setScreen(new BorderScreen());
    }

    public static void receivePlayerBlocks(PlayerBlocksPacket packet)
    {
        Long2ObjectOpenHashMap<Block> map = PLAYER_BLOCKS.computeIfAbsent(packet.dimension(), k -> new Long2ObjectOpenHashMap<>());
        if (packet.reset()) map.clear();
        for (int i = 0; i < packet.positions().length; i++)
        {
            Block block = BuiltInRegistries.BLOCK.byId(packet.blocks()[i]);
            if (block == Blocks.AIR) map.remove(packet.positions()[i]);
            else map.put(packet.positions()[i], block);
        }
    }

    public static Map<Long, Block> playerBlocks()
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return Map.of();
        Long2ObjectOpenHashMap<Block> map = PLAYER_BLOCKS.get(level.dimension());
        return map == null ? Map.of() : map;
    }

    public static void receiveSettings(BorderSettings.Values values)
    {
        BorderSettings.setClient(values);
        if (Minecraft.getInstance().screen instanceof SettingsScreen screen) screen.onSync();
    }

    public static void editResult(EditResultPacket packet)
    {
        if (Minecraft.getInstance().screen instanceof BorderScreen screen) screen.onResult(packet);
        if (Minecraft.getInstance().screen instanceof SettingsScreen screen) screen.onResult(packet);
    }

    private static void apply(ClientLevel level, SyncBorderPacket packet)
    {
        Node expression;
        try
        {
            expression = ExpressionParser.parse(packet.expression());
        }
        catch (IllegalArgumentException e)
        {
            CsgWorldBorder.LOGGER.error("CSG WorldBorder: server sent invalid expression '{}'", packet.expression());
            expression = Node.BORDER;
        }
        Map<String, Shape> shapes = new LinkedHashMap<>();
        for (Shape shape : packet.shapes()) shapes.put(shape.name(), shape);
        Volume volume = BorderVolume.of(level.getWorldBorder());
        BorderManager.install(level, volume);
        volume.define(expression, shapes, packet.outline(), packet.protectionRules(), Util.getMillis());
        state = packet;
    }
}
