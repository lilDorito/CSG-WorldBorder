package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.client.ClientBorders;
import com.lildorito.csgworldborder.network.CsgNetwork;
import com.lildorito.csgworldborder.network.PlayerBlocksPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

@Mod.EventBusSubscriber(modid = CsgWorldBorder.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerBlocks extends SavedData
{
    private static final String DATA_NAME = "csgworldborder_player_blocks";
    private static final int BATCH = 16384;

    private final Long2ObjectOpenHashMap<Block> blocks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Block> pending = new Long2ObjectOpenHashMap<>();

    public static PlayerBlocks get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(PlayerBlocks::load, PlayerBlocks::new, DATA_NAME);
    }

    public static boolean isPlayerBlock(Level level, BlockPos pos)
    {
        Map<Long, Block> map = level.isClientSide ? ClientBorders.playerBlocks() : level instanceof ServerLevel server ? get(server).blocks : null;
        if (map == null || map.isEmpty()) return false;
        Block block = map.get(pos.asLong());
        return block != null && level.getBlockState(pos).is(block);
    }

    public int size()
    {
        return blocks.size();
    }

    public void record(BlockPos pos, Block block)
    {
        if (block == Blocks.AIR) return;
        long key = pos.asLong();
        if (blocks.put(key, block) == block) return;
        pending.put(key, block);
        setDirty();
    }

    public void forget(BlockPos pos)
    {
        long key = pos.asLong();
        if (blocks.remove(key) == null) return;
        pending.put(key, Blocks.AIR);
        setDirty();
    }

    public int forgetAll(LongPredicate where)
    {
        List<Long> removed = new ArrayList<>();
        for (Long2ObjectMap.Entry<Block> entry : blocks.long2ObjectEntrySet())
        {
            if (where.test(entry.getLongKey())) removed.add(entry.getLongKey());
        }
        for (long key : removed)
        {
            blocks.remove(key);
            pending.put(key, Blocks.AIR);
        }
        if (!removed.isEmpty()) setDirty();
        return removed.size();
    }

    public static void syncTo(ServerPlayer player)
    {
        PlayerBlocks data = get(player.serverLevel());
        List<PlayerBlocksPacket> packets = packets(player.serverLevel(), data.blocks, true);
        for (PlayerBlocksPacket packet : packets) CsgNetwork.send(player, packet);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels())
        {
            PlayerBlocks data = level.getDataStorage().get(PlayerBlocks::load, DATA_NAME);
            if (data == null || data.pending.isEmpty()) continue;
            for (PlayerBlocksPacket packet : packets(level, data.pending, false)) CsgNetwork.sendToDimension(level.dimension(), packet);
            data.pending.clear();
        }
    }

    private static List<PlayerBlocksPacket> packets(ServerLevel level, Long2ObjectOpenHashMap<Block> entries, boolean reset)
    {
        List<PlayerBlocksPacket> packets = new ArrayList<>();
        long[] positions = new long[Math.min(entries.size(), BATCH)];
        int[] ids = new int[positions.length];
        int count = 0;
        boolean first = reset;
        for (Long2ObjectMap.Entry<Block> entry : entries.long2ObjectEntrySet())
        {
            positions[count] = entry.getLongKey();
            ids[count] = BuiltInRegistries.BLOCK.getId(entry.getValue());
            if (++count == positions.length)
            {
                packets.add(new PlayerBlocksPacket(level.dimension(), first, positions.clone(), ids.clone()));
                first = false;
                count = 0;
            }
        }
        if (count > 0 || first)
        {
            long[] p = new long[count];
            int[] b = new int[count];
            System.arraycopy(positions, 0, p, 0, count);
            System.arraycopy(ids, 0, b, 0, count);
            packets.add(new PlayerBlocksPacket(level.dimension(), first, p, b));
        }
        return packets;
    }

    private static PlayerBlocks load(CompoundTag tag)
    {
        PlayerBlocks data = new PlayerBlocks();
        ListTag palette = tag.getList("palette", Tag.TAG_STRING);
        List<Block> blocks = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++)
        {
            ResourceLocation id = ResourceLocation.tryParse(palette.getString(i));
            blocks.add(id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id));
        }
        long[] positions = tag.getLongArray("positions");
        int[] indices = tag.getIntArray("blocks");
        for (int i = 0; i < Math.min(positions.length, indices.length); i++)
        {
            int index = indices[i];
            if (index < 0 || index >= blocks.size() || blocks.get(index) == Blocks.AIR) continue;
            data.blocks.put(positions[i], blocks.get(index));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        Map<Block, Integer> indexOf = new IdentityHashMap<>();
        ListTag palette = new ListTag();
        long[] positions = new long[blocks.size()];
        int[] indices = new int[blocks.size()];
        int i = 0;
        for (Long2ObjectMap.Entry<Block> entry : blocks.long2ObjectEntrySet())
        {
            Block block = entry.getValue();
            Integer index = indexOf.get(block);
            if (index == null)
            {
                index = palette.size();
                indexOf.put(block, index);
                palette.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(block).toString()));
            }
            positions[i] = entry.getLongKey();
            indices[i] = index;
            i++;
        }
        tag.put("palette", palette);
        tag.putLongArray("positions", positions);
        tag.putIntArray("blocks", indices);
        return tag;
    }
}
