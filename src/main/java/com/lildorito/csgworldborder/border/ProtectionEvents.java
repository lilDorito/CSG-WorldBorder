package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.CsgWorldBorder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CsgWorldBorder.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtectionEvents
{
    public static final TagKey<Item> BLOCKED_ON_PROTECTED =
            TagKey.create(Registries.ITEM, new ResourceLocation(CsgWorldBorder.MODID, "blocked_on_protected"));

    private ProtectionEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event)
    {
        if (BorderProtection.blocks(event.getLevel(), event.getPos(), event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        if (event.getPosition().isEmpty()) return;
        Player player = event.getEntity();
        if (BorderProtection.blocks(player.level(), event.getPosition().get(), player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (BorderProtection.blocks(event.getLevel(), event.getPos(), event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event)
    {
        for (var snapshot : event.getReplacedBlockSnapshots())
        {
            if (BorderProtection.blocks(event.getLevel(), snapshot.getPos(), event.getEntity()))
            {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFillBucket(FillBucketEvent event)
    {
        if (!(event.getTarget() instanceof BlockHitResult hit)) return;
        Level level = event.getLevel();
        if (BorderProtection.blocks(level, hit.getBlockPos(), event.getEntity())
                || BorderProtection.blocks(level, hit.getBlockPos().relative(hit.getDirection()), event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player.isCreative() || !stack.is(BLOCKED_ON_PROTECTED)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        if (BorderProtection.isProtected(level, pos) || (face != null && BorderProtection.isProtected(level, pos.relative(face))))
        {
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event)
    {
        if (event.isSimulated()) return;
        if (!(event.getLevel() instanceof Level level)) return;
        if (BorderProtection.blocks(level, event.getPos(), event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBonemeal(BonemealEvent event)
    {
        if (BorderProtection.blocks(event.getLevel(), event.getPos(), event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void recordPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerBlocks data = PlayerBlocks.get(level);
        if (player.isCreative()) data.forget(event.getPos());
        else if (BorderSettings.of(level).protectMapBlocksOnly()) data.record(event.getPos(), event.getPlacedBlock().getBlock());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void recordMultiPlace(BlockEvent.EntityMultiPlaceEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerBlocks data = PlayerBlocks.get(level);
        boolean record = !player.isCreative() && BorderSettings.of(level).protectMapBlocksOnly();
        for (var snapshot : event.getReplacedBlockSnapshots())
        {
            if (record) data.record(snapshot.getPos(), level.getBlockState(snapshot.getPos()).getBlock());
            else data.forget(snapshot.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void recordBreak(BlockEvent.BreakEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level) PlayerBlocks.get(level).forget(event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate event)
    {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> BorderProtection.isProtected(level, pos));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void recordExplosion(ExplosionEvent.Detonate event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PlayerBlocks data = PlayerBlocks.get(level);
        if (data.size() == 0) return;
        for (BlockPos pos : event.getAffectedBlocks()) data.forget(pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMobGrief(LivingDestroyBlockEvent event)
    {
        if (BorderProtection.isProtected(event.getEntity().level(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPiston(PistonEvent.Pre event)
    {
        if (!(event.getLevel() instanceof Level level)) return;
        if (event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                && BorderProtection.isProtected(level, event.getFaceOffsetPos()))
        {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        for (BlockPos pos : resolver.getToPush())
        {
            if (BorderProtection.isProtected(level, pos) || BorderProtection.isProtected(level, pos.relative(resolver.getPushDirection())))
            {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy())
        {
            if (BorderProtection.isProtected(level, pos))
            {
                event.setCanceled(true);
                return;
            }
        }
    }
}
