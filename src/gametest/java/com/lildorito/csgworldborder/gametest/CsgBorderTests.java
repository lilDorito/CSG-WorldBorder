package com.lildorito.csgworldborder.gametest;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.api.CsgWorldBorderApi;
import com.lildorito.csgworldborder.border.BorderProtection;
import com.lildorito.csgworldborder.border.BorderService;
import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.border.CsgBorderData;
import com.lildorito.csgworldborder.border.PlayerBlocks;
import com.lildorito.csgworldborder.csg.Volume;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.example.combat.Bullet;
import net.example.combat.Charge;
import net.example.combat.Shell;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@GameTestHolder(CsgWorldBorder.MODID)
@PrefixGameTestTemplate(false)
public final class CsgBorderTests
{
    private CsgBorderTests() {}

    @GameTest(template = "empty", batch = "volume", timeoutTicks = 100000)
    public static void volume(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        WorldBorder border = level.getWorldBorder();
        BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1)).offset(96, 40, 96);
        for (int cx = -2; cx <= 2; cx++)
        {
            for (int cz = -2; cz <= 2; cz++) level.getChunk((o.getX() >> 4) + cx, (o.getZ() >> 4) + cz);
        }
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-14, -12, -14), o.offset(14, 36, 14)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        String c = o.getX() + " " + o.getY() + " " + o.getZ();

        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
        helper.getLevel().getWorldBorder().setSize(5.9999968E7);
        helper.assertTrue(BorderVolume.active(border) == null, "no shapes means classic border");
        helper.assertTrue(border.isWithinBounds(o.above(200)), "vanilla square is unlimited in Y");
        helper.assertTrue(run(helper, "worldborder get") > 0, "vanilla /worldborder get still works");

        helper.assertTrue(run(helper, "worldborder shape add ball sphere 6 at " + c) == 1, "create sphere");
                Volume volume = BorderVolume.active(border);
        helper.assertTrue(volume != null, "first shape activates the 3D border");
        helper.assertTrue(CsgBorderData.get(level).expression().format().equals("border & ball"), "auto formula " + CsgBorderData.get(level).expression().format());

        helper.assertTrue(border.isWithinBounds(o), "center inside");
        helper.assertTrue(border.isWithinBounds(o.above(6)), "6 above inside");
        helper.assertTrue(!border.isWithinBounds(o.above(7)), "7 above outside (vertical border)");
        helper.assertTrue(!border.isWithinBounds(o.below(7)), "7 below outside");
        helper.assertTrue(!border.isWithinBounds(o.east(7)), "7 east outside");
        helper.assertTrue(border.isWithinBounds(o.offset(6, 1, 0)) && border.isWithinBounds(o.offset(6, 2, 0))
                && border.isWithinBounds(o.offset(6, 0, 2)) && !border.isWithinBounds(o.offset(6, 3, 0)), "flat 5x5 patch at the side, no single-block pole");
        helper.assertTrue(border.isWithinBounds(o.offset(0, 6, 2)) && border.isWithinBounds(o.offset(2, 6, 0)), "flat patch at the top");
        helper.assertTrue(!border.isWithinBounds(new AABB(o.above(10))), "box above outside");
        helper.assertTrue(border.isWithinBounds(new AABB(o.above(6)).inflate(2)), "box overlapping inside");
        helper.assertTrue(border.isWithinBounds(new ChunkPos(o)), "chunk column intersects");
        helper.assertTrue(!border.isWithinBounds(new ChunkPos(o.east(200))), "far chunk does not");

        Pig pig = EntityType.PIG.create(level);
        helper.assertTrue(pig != null, "pig created");
        pig.setNoAi(true);
        pig.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0.0F, 0.0F);
        double distance = border.getDistanceToBorder(pig);
        helper.assertTrue(distance > 5.0 && distance < 7.5, "distance from center, got " + distance);
        for (int i = 0; i < 40; i++) pig.move(MoverType.SELF, new Vec3(0.3, 0.0, 0.0));
        helper.assertTrue(pig.getX() > o.getX() + 6.0 && pig.getX() <= o.getX() + 6.56,
                "pig stopped by east wall in small steps, x offset " + (pig.getX() - o.getX()));
        pig.move(MoverType.SELF, new Vec3(20.0, 0.0, 0.0));
        helper.assertTrue(pig.getX() <= o.getX() + 6.56, "big push still blocked, x offset " + (pig.getX() - o.getX()));
        pig.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0.0F, 0.0F);
        pig.move(MoverType.SELF, new Vec3(0.0, 20.0, 0.0));
        helper.assertTrue(pig.getY() > o.getY() + 5.5 && pig.getY() <= o.getY() + 6.11,
                "pig stopped by ceiling, y offset " + (pig.getY() - o.getY()));
        pig.moveTo(o.getX() + 0.5, o.getY() + 20.0, o.getZ() + 0.5, 0.0F, 0.0F);
        pig.move(MoverType.SELF, new Vec3(0.0, -15.0, 0.0));
        helper.assertTrue(pig.getY() < o.getY() + 6.0, "outside entity passes through border, y offset " + (pig.getY() - o.getY()));
        pig.moveTo(o.getX() + 0.5, o.getY() + 30.0, o.getZ() + 0.5, 0.0F, 0.0F);
        helper.assertTrue(border.getDistanceToBorder(pig) < -20.0, "far outside is negative");

        BlockPos clamped = border.clampToBounds(o.getX() + 50.5, o.getY() + 0.5, o.getZ() + 0.5);
        helper.assertTrue(border.isWithinBounds(clamped) && clamped.getX() >= o.getX() + 4, "clampToBounds nearest inside " + clamped);

        BlockPos oldSpawn = level.getSharedSpawnPos();
        level.setDefaultSpawnPos(o.offset(40, 0, 0), 0.0F);
        helper.assertTrue(border.isWithinBounds(level.getSharedSpawnPos()), "shared spawn moved inside");
        level.setDefaultSpawnPos(oldSpawn, 0.0F);

        String from = (o.getX() - 6) + " " + o.getY() + " " + (o.getZ() - 6);
        String to = (o.getX() + 6) + " " + (o.getY() + 8) + " " + (o.getZ() + 6);
        helper.assertTrue(run(helper, "worldborder shape add room box from " + from + " to " + to) == 1, "box from/to");
        helper.assertTrue(formula(level).equals("border & (ball | room)"), "union layer " + formula(level));
        helper.assertTrue(border.isWithinBounds(o.offset(6, 8, 6)) && !border.isWithinBounds(o.offset(7, 8, 6)), "box corners inclusive");

        helper.assertTrue(run(helper, "worldborder shape remove ball") == 1, "remove ball");
        BlockPos portalAt = o.offset(0, 2, 0);
        var portal = level.getPortalForcer().createPortal(portalAt, Direction.Axis.X);
        helper.assertTrue(portal.isPresent(), "portal created inside room");
        int portalBlocks = 0;
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-8, -2, -8), o.offset(8, 12, 8)))
        {
            if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL) || level.getBlockState(pos).is(Blocks.OBSIDIAN))
            {
                helper.assertTrue(border.isWithinBounds(pos), "portal block inside border " + pos);
                portalBlocks++;
                level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        helper.assertTrue(portalBlocks >= 6, "portal blocks found " + portalBlocks);
        helper.assertTrue(run(helper, "worldborder shape add ball sphere 3 at " + c + " mode add") == 1, "add with position and mode");
        helper.assertTrue(CsgBorderData.get(level).shape("ball").to().a() == 3.0, "small ball");
        helper.assertTrue(run(helper, "worldborder shape resize ball to 6") == 1, "edit size only, position kept");
        helper.assertTrue(CsgBorderData.get(level).shape("ball").to().a() == 6.0 && CsgBorderData.get(level).shape("ball").to().transform().x() == o.getX() + 0.5, "resized in place");
        helper.assertTrue(run(helper, "worldborder shape move ball to " + (o.getX() + 2) + " " + o.getY() + " " + o.getZ()) == 1, "edit position");
        helper.assertTrue(CsgBorderData.get(level).shape("ball").to().transform().x() == o.getX() + 2.5 && CsgBorderData.get(level).shape("ball").to().a() == 6.0, "moved, size kept");
        helper.assertTrue(run(helper, "worldborder shape move ball by -2 0 0") == 1, "moved back by offset");
        helper.assertTrue(run(helper, "worldborder shape resize ghost to 3") == 0, "editing a missing shape fails");
        helper.assertTrue(run(helper, "worldborder shape add ball sphere 2") == 0, "adding over an existing name fails");
        helper.assertTrue(run(helper, "worldborder shape mode ball sideways") == 0, "bad mode rejected");

        helper.assertTrue(run(helper, "worldborder shape rotate room to 45 0 0") == 1, "rotate");
        helper.assertTrue(run(helper, "worldborder shape scale room to 1 0.5 1") == 1, "scale xyz");
        helper.assertTrue(run(helper, "worldborder shape mode room subtract") == 1, "mode subtract");
        helper.assertTrue(run(helper, "worldborder shape order room up") == 1, "reorder up");
        helper.assertTrue(formula(level).equals("border & (all - room | ball)"), "reordered " + formula(level));
        helper.assertTrue(run(helper, "worldborder shape order room bottom") == 1, "reorder bottom");
        helper.assertTrue(formula(level).equals("border & (ball - room)"), "subtract layer " + formula(level));
        helper.assertTrue(!border.isWithinBounds(o.offset(0, 2, 0)) && border.isWithinBounds(o.offset(0, -3, 0)), "subtraction works");
        helper.assertTrue(run(helper, "worldborder shape mode room intersect") == 1, "mode intersect");
        helper.assertTrue(border.isWithinBounds(o.offset(0, 2, 0)) && !border.isWithinBounds(o.offset(0, -3, 0)), "intersection works");

        helper.assertTrue(run(helper, "worldborder classic off") == 1, "classic off");
        helper.assertTrue(formula(level).equals("ball & room"), "classic off formula " + formula(level));
        helper.assertTrue(run(helper, "worldborder classic on") == 1, "classic on");
        helper.assertTrue(run(helper, "worldborder formula set ball ^ nope") == 0, "unknown shape rejected");
        helper.assertTrue(run(helper, "worldborder formula set (ball") == 0, "bad formula rejected");
        helper.assertTrue(run(helper, "worldborder formula set ball ^ room") == 1, "custom formula");
        helper.assertTrue(formula(level).equals("ball ^ room"), "custom formula active");
        helper.assertTrue(run(helper, "worldborder shape rename room hall") == 1, "rename");
        helper.assertTrue(formula(level).equals("ball ^ hall"), "rename updates formula " + formula(level));
        helper.assertTrue(run(helper, "worldborder formula auto") == 1, "formula auto");
        helper.assertTrue(formula(level).equals("border & (ball & hall)"), "auto again " + formula(level));

        CsgBorderData data = CsgBorderData.get(level);
        CsgBorderData copy = CsgBorderData.load(data.save(new CompoundTag()));
        helper.assertTrue(copy.expression().format().equals(data.expression().format()) && copy.shapes().size() == data.shapes().size()
                && copy.mode("hall") == data.mode("hall") && copy.classic() == data.classic(), "save and load roundtrip");
        helper.assertTrue(run(helper, "worldborder shape remove hall") == 1, "remove hall");
        helper.assertTrue(run(helper, "worldborder shape resize ball to 10 over 1") == 1, "animated resize");
        helper.assertTrue(!border.isWithinBounds(o.east(9)), "not grown yet");

        waitFor(helper, () -> BorderVolume.active(border) != null && !BorderVolume.active(border).isAnimating(), Util.getMillis() + 10_000L, () ->
        {
            Volume v = BorderVolume.active(border);
            helper.assertTrue(v != null && !v.isAnimating(), "animation finished");
            helper.assertTrue(border.isWithinBounds(o.east(10)) && !border.isWithinBounds(o.east(11)), "grown to radius 10");
            helper.assertTrue(run(helper, "worldborder shape remove ball") == 1, "remove last");
            helper.assertTrue(BorderVolume.active(border) == null, "classic border returns when the last shape is gone");
            helper.assertTrue(run(helper, "worldborder shape add x sphere 3 at " + c) == 1 && run(helper, "worldborder shape clear") == 1, "clear");
            helper.assertTrue(BorderVolume.active(border) == null, "classic border after clear");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "ticking", timeoutTicks = 400)
    public static void blockEntitiesOutsideStop(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        WorldBorder border = level.getWorldBorder();
        BlockPos o = helper.absolutePos(new BlockPos(1, 0, 1));
        String box = (o.getX() - 3) + " " + (o.getY() - 3) + " " + (o.getZ() - 3) + " to " + (o.getX() + 3) + " " + (o.getY() + 3) + " " + (o.getZ() + 3);
        String away = (o.getX() + 20) + " " + o.getY() + " " + o.getZ() + " to " + (o.getX() + 26) + " " + (o.getY() + 6) + " " + (o.getZ() + 6);
        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
        helper.getLevel().getWorldBorder().setSize(5.9999968E7);
        helper.assertTrue(run(helper, "worldborder shape add keep box from " + box) == 1, "box around hopper");
        level.setBlock(o, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(o.above(), Blocks.HOPPER.defaultBlockState(), 3);
        helper.assertTrue(border.isWithinBounds(o.above()), "hopper inside");
        HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(o.above());
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(o);
        hopper.setItem(0, new ItemStack(Items.EMERALD, 1));

        helper.runAfterDelay(40, () ->
        {
            helper.assertTrue(chest.countItem(Items.EMERALD) == 1, "hopper ticked while inside");
            chest.clearContent();
            helper.assertTrue(run(helper, "worldborder shape reshape keep box from " + away) == 1, "move border away");
            helper.assertTrue(!border.isWithinBounds(o.above()), "hopper now outside");
            hopper.setItem(0, new ItemStack(Items.DIAMOND, 4));
            helper.runAfterDelay(40, () ->
            {
                helper.assertTrue(chest.isEmpty(), "hopper outside the border did not tick");
                helper.assertTrue(run(helper, "worldborder shape reshape keep box from " + box) == 1, "border back");
                helper.runAfterDelay(40, () ->
                {
                    helper.assertTrue(!chest.isEmpty(), "hopper ticks again once inside");
                    level.setBlock(o.above(), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(o, Blocks.AIR.defaultBlockState(), 2);
                    run(helper, "worldborder shape clear");
                    run(helper, "worldborder classic outline square");
                    helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
                    helper.getLevel().getWorldBorder().setSize(5.9999968E7);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "map", timeoutTicks = 400)
    public static void mapOutlineFor2dMods(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        WorldBorder border = level.getWorldBorder();
        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
        helper.getLevel().getWorldBorder().setSize(5.9999968E7);
        double vanillaMinX = border.getMinX();
        double vanillaMaxZ = border.getMaxZ();
        BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1)).offset(0, 30, 0);
        String c = o.getX() + " " + o.getY() + " " + o.getZ();

        helper.assertTrue(run(helper, "worldborder shape add ball sphere 10 at " + c) == 1, "sphere");
        helper.assertTrue(border.getMinX() == o.getX() - 10 && border.getMaxX() == o.getX() + 11
                && border.getMinZ() == o.getZ() - 10 && border.getMaxZ() == o.getZ() + 11,
                "corners report the map rectangle: " + border.getMinX() + " " + border.getMaxX() + " " + border.getMinZ() + " " + border.getMaxZ());
        helper.assertTrue(CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ()) && CsgWorldBorderApi.isOnMap(level, o.getX() + 10, o.getZ())
                && !CsgWorldBorderApi.isOnMap(level, o.getX() + 11, o.getZ()) && !CsgWorldBorderApi.isOnMap(level, o.getX() + 8, o.getZ() + 8),
                "a sphere is its equator circle on the map");
        var outline = CsgWorldBorderApi.mapOutline(level, o.getX() - 50, o.getZ() - 50, o.getX() + 50, o.getZ() + 50);
        double length = 0.0;
        for (var seg : outline) length += Math.abs(seg.x2() - seg.x1()) + Math.abs(seg.z2() - seg.z1());
        helper.assertTrue(outline.size() > 8 && length > 70.0 && length < 90.0, "outline of a radius 10.5 disc, length " + length + " in " + outline.size());
        for (var seg : outline)
        {
            double mx = (seg.x1() + seg.x2()) * 0.5 - (o.getX() + 0.5), mz = (seg.z1() + seg.z2()) * 0.5 - (o.getZ() + 0.5);
            double r = Math.sqrt(mx * mx + mz * mz);
            helper.assertTrue(r > 8.5 && r < 11.5, "outline hugs the circle, r " + r);
        }

        helper.assertTrue(run(helper, "worldborder shape add hole cylinder 3 200 at " + c + " mode subtract") == 1, "hole through");
        helper.assertTrue(!CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ()) && CsgWorldBorderApi.isOnMap(level, o.getX() + 5, o.getZ()),
                "a hole through every height removes the column");
        helper.assertTrue(run(helper, "worldborder shape resize hole to 3 4") == 1, "pocket");
        helper.assertTrue(!CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ()) && CsgWorldBorderApi.isOnMap(level, o.getX() + 5, o.getZ()), "a buried pocket still shows as a hole");
        run(helper, "worldborder shape remove hole");

        helper.assertTrue(run(helper, "worldborder shape reshape ball torus 8 2") == 1, "torus");
        helper.assertTrue(CsgWorldBorderApi.isOnMap(level, o.getX() + 8, o.getZ()) && !CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ())
                && !CsgWorldBorderApi.isOnMap(level, o.getX() + 12, o.getZ()), "a torus is a ring on the map");
        helper.assertTrue(run(helper, "worldborder shape reshape ball cone 6 10") == 1, "cone");
        helper.assertTrue(CsgWorldBorderApi.isOnMap(level, o.getX() + 6, o.getZ()) && !CsgWorldBorderApi.isOnMap(level, o.getX() + 8, o.getZ()), "a cone is its base circle");
        helper.assertTrue(run(helper, "worldborder shape reshape ball cylinder 3 20") == 1 && run(helper, "worldborder shape rotate ball to 0 90 0") == 1, "lying cylinder");
        helper.assertTrue(CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ() + 9) && CsgWorldBorderApi.isOnMap(level, o.getX() + 3, o.getZ())
                && !CsgWorldBorderApi.isOnMap(level, o.getX() + 5, o.getZ()) && !CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ() + 12),
                "a lying cylinder is its widest slice, a long rectangle");
        helper.assertTrue(run(helper, "worldborder shape reshape ball sphere 10") == 1 && run(helper, "worldborder shape rotate ball by 0 -90 0") == 1, "sphere again");

        helper.assertTrue(run(helper, "worldborder shape add box2 box 6 6 6 at " + (o.getX() + 40) + " " + o.getY() + " " + o.getZ()) == 1, "second shape");
        helper.assertTrue(border.getMaxX() == o.getX() + 44, "rectangle covers both shapes: " + border.getMaxX());
        helper.assertTrue(level.getWorldBorder().isWithinBounds(o) && border.isWithinBounds(o.offset(40, 0, 0)), "3D logic unaffected");

        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
        helper.getLevel().getWorldBorder().setSize(5.9999968E7);
        helper.assertTrue(border.getMinX() == vanillaMinX && border.getMaxZ() == vanillaMaxZ, "classic corners return");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "outline", timeoutTicks = 400)
    public static void classicOutlines(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        WorldBorder border = level.getWorldBorder();
        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
        helper.getLevel().getWorldBorder().setSize(5.9999968E7);
        double size = border.getSize(), centerX = border.getCenterX(), centerZ = border.getCenterZ();
        BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1));
        border.setCenter(o.getX() + 0.5, o.getZ() + 0.5);
        border.setSize(41.0);
        helper.assertTrue(BorderVolume.active(border) == null, "square outline is pure vanilla");
        helper.assertTrue(border.isWithinBounds(o.offset(15, 0, 15)), "square corner inside");

        helper.assertTrue(run(helper, "worldborder classic outline circle") == 1, "circle");
        helper.assertTrue(BorderVolume.active(border) != null, "a circle activates the 3D border without shapes");
        helper.assertTrue(border.isWithinBounds(o.offset(20, 0, 0)) && border.isWithinBounds(o.offset(14, 0, 14))
                && !border.isWithinBounds(o.offset(15, 0, 15)) && !border.isWithinBounds(o.offset(21, 0, 0)), "circle of radius 20.5");
        helper.assertTrue(border.isWithinBounds(o.offset(0, 300, 0)) && border.isWithinBounds(o.offset(0, -60, 0)), "a column from bottom to top");
        helper.assertTrue(CsgWorldBorderApi.isOnMap(level, o.getX() + 20, o.getZ()) && !CsgWorldBorderApi.isOnMap(level, o.getX() + 15, o.getZ() + 15), "circle on the map");

        helper.assertTrue(run(helper, "worldborder classic outline hexagon") == 1, "hexagon");
        helper.assertTrue(border.isWithinBounds(o.offset(20, 0, 0)) && border.isWithinBounds(o.offset(0, 0, 17))
                && !border.isWithinBounds(o.offset(0, 0, 18)) && !border.isWithinBounds(o.offset(15, 0, 10)), "hexagon with corners on X");

        helper.assertTrue(run(helper, "worldborder classic outline octagon") == 1, "octagon");
        helper.assertTrue(border.isWithinBounds(o.offset(20, 0, 0)) && border.isWithinBounds(o.offset(14, 0, 14))
                && !border.isWithinBounds(o.offset(15, 0, 15)) && border.isWithinBounds(o.offset(20, 0, 8)), "regular octagon");

        helper.assertTrue(run(helper, "worldborder classic outline diamond") == 1, "diamond");
        helper.assertTrue(border.isWithinBounds(o.offset(20, 0, 0)) && border.isWithinBounds(o.offset(10, 0, 10))
                && !border.isWithinBounds(o.offset(11, 0, 10)), "diamond");

        helper.assertTrue(run(helper, "worldborder classic outline triangle") == 1, "triangle");
        helper.assertTrue(border.isWithinBounds(o.offset(0, 0, -20)) && border.isWithinBounds(o.offset(0, 0, 10))
                && !border.isWithinBounds(o.offset(0, 0, 11)) && border.isWithinBounds(o.offset(17, 0, 10))
                && !border.isWithinBounds(o.offset(17, 0, 0)), "triangle pointing north");

        helper.assertTrue(run(helper, "worldborder classic outline star") == 1, "star");
        helper.assertTrue(border.isWithinBounds(o) && border.isWithinBounds(o.offset(0, 0, -19)) && border.isWithinBounds(o.offset(0, 0, 4))
                && !border.isWithinBounds(o.offset(7, 0, -10)) && !border.isWithinBounds(o.offset(0, 0, 10)), "five-point star pointing north");
        helper.assertTrue(CsgWorldBorderApi.isOnMap(level, o.getX(), o.getZ() - 19) && !CsgWorldBorderApi.isOnMap(level, o.getX() + 7, o.getZ() - 10), "star on the map");

        helper.assertTrue(run(helper, "worldborder classic outline circle") == 1
                && run(helper, "worldborder shape add ball sphere 40 at " + o.getX() + " " + o.getY() + " " + o.getZ()) == 1, "shape inside a circle");
        helper.assertTrue(border.isWithinBounds(o.offset(14, 0, 14)) && !border.isWithinBounds(o.offset(15, 0, 15)), "classic circle limits the shape");
        helper.assertTrue(run(helper, "worldborder classic off") == 1 && border.isWithinBounds(o.offset(15, 0, 15)), "classic off ignores the circle");
        run(helper, "worldborder classic on");
        CsgBorderData data = CsgBorderData.get(level);
        helper.assertTrue(CsgBorderData.load(data.save(new CompoundTag())).outline() == data.outline(), "outline saved");
        helper.assertTrue(run(helper, "worldborder shape remove ball") == 1 && BorderVolume.active(border) != null, "circle stays after the last shape");
        helper.assertTrue(CsgWorldBorderApi.classicOutline(level).id().equals("circle"), "api outline");

        border.setSize(21.0);
        helper.runAfterDelay(2, () ->
        {
            helper.assertTrue(border.isWithinBounds(o.offset(10, 0, 0)) && !border.isWithinBounds(o.offset(8, 0, 8)), "vanilla set resizes the circle");
            helper.assertTrue(run(helper, "worldborder classic outline square") == 1 && BorderVolume.active(border) == null, "square is vanilla again");
            border.setCenter(centerX, centerZ);
            border.setSize(size);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "areas", timeoutTicks = 400)
    public static void protectedAreas(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        resetSettings(helper);
        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        level.getWorldBorder().setCenter(0.0, 0.0);
        level.getWorldBorder().setSize(5.9999968E7);
        BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1)).offset(0, 40, 0);
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-1, -1, -1), o.offset(27, 5, 7)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        helper.assertTrue(run(helper, "worldborder shape add a box from " + at(o) + " to " + at(o.offset(6, 4, 6))) == 1
                && run(helper, "worldborder shape add b box from " + at(o.offset(5, 0, 0)) + " to " + at(o.offset(12, 4, 6))) == 1
                && run(helper, "worldborder shape add c box from " + at(o.offset(20, 0, 0)) + " to " + at(o.offset(26, 4, 6))) == 1, "three boxes");
        BlockPos inA = o.offset(2, 2, 3), inB = o.offset(9, 2, 3), inC = o.offset(23, 2, 3);
        helper.assertTrue(!BorderProtection.isProtected(level, inA), "nothing protected by default");

        helper.assertTrue(run(helper, "worldborder shape protect a inside") == 1, "protect a");
        helper.assertTrue(BorderProtection.isProtected(level, inA) && BorderProtection.isProtected(level, inB), "overlapping shapes are one area");
        helper.assertTrue(!BorderProtection.isProtected(level, inC), "a separate shape is its own area");
        FakePlayer survivor = FakePlayerFactory.getMinecraft(level);
        survivor.setGameMode(GameType.SURVIVAL);
        level.setBlock(inB, Blocks.OAK_LOG.defaultBlockState(), 3);
        helper.assertTrue(!survivor.gameMode.destroyBlock(inB) && level.getBlockState(inB).is(Blocks.OAK_LOG), "survival can't break inside");
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        BlockHitResult logHit = new BlockHitResult(Vec3.atCenterOf(inB), Direction.UP, inB, false);
        survivor.gameMode.useItemOn(survivor, level, survivor.getMainHandItem(), InteractionHand.MAIN_HAND, logHit);
        helper.assertTrue(level.getBlockState(inB).is(Blocks.OAK_LOG), "no stripping logs inside, like adventure mode");
        CsgBorderData data = CsgBorderData.get(level);
        helper.assertTrue(data.protection("b") == com.lildorito.csgworldborder.csg.Protection.INSIDE, "setting goes to every shape of the area");

        helper.assertTrue(run(helper, "worldborder shape protect b none") == 1, "unprotect by another member");
        helper.assertTrue(!BorderProtection.isProtected(level, inA) && !BorderProtection.isProtected(level, inB), "whole area unprotected");
        survivor.gameMode.useItemOn(survivor, level, survivor.getMainHandItem(), InteractionHand.MAIN_HAND, logHit);
        helper.assertTrue(level.getBlockState(inB).is(Blocks.STRIPPED_OAK_LOG), "axe works once unprotected");
        level.setBlock(inB, Blocks.AIR.defaultBlockState(), 3);

        helper.assertTrue(run(helper, "worldborder shape protect a inside") == 1 && run(helper, "worldborder shape move c by -8 0 0") == 1, "c joins");
        helper.assertTrue(BorderProtection.isProtected(level, o.offset(15, 2, 3)), "a touching shape joins the area and protected wins");
        helper.assertTrue(BorderService.area(data, "c").size() == 3, "area of three");

        helper.assertTrue(run(helper, "worldborder shape protect c edge") == 1, "edge");
        helper.assertTrue(!BorderProtection.isProtected(level, inA) && BorderProtection.isProtected(level, o.offset(0, 2, 3))
                && BorderProtection.isProtected(level, o.offset(2, 0, 3)) && BorderProtection.isProtected(level, o.offset(2, 4, 3)), "edge only");

        helper.assertTrue(run(helper, "worldborder shape add hole box 2 2 2 at " + at(inB) + " mode subtract") == 1, "hole");
        helper.assertTrue(run(helper, "worldborder shape protect hole inside") == 0, "subtract shapes have no area");
        helper.assertTrue(run(helper, "worldborder shape list") == 4 && run(helper, "worldborder shape info a") == 1, "list and info");
        helper.assertTrue(CsgBorderData.load(data.save(new CompoundTag())).protection("a") == com.lildorito.csgworldborder.csg.Protection.EDGE, "saved");

        helper.assertTrue(run(helper, "worldborder shape add probe sphere 2 at 10 70 10") == 1
                && data.shape("probe").to().transform().x() == 10.5 && data.shape("probe").to().transform().y() == 70.5, "whole numbers are block centers");
        helper.assertTrue(run(helper, "worldborder shape move probe to 10.0 70.0 10.25") == 1
                && data.shape("probe").to().transform().x() == 10.0 && data.shape("probe").to().transform().z() == 10.25, "decimals are exact");
        helper.assertTrue(run(helper, "worldborder shape move probe to 10 70.0 -3") == 1
                && data.shape("probe").to().transform().x() == 10.5 && data.shape("probe").to().transform().y() == 70.0
                && data.shape("probe").to().transform().z() == -2.5, "each axis on its own");
        helper.assertTrue(run(helper, "worldborder shape clear") == 1, "clear");
        BlockPos far = o.offset(0, 100, 0);
        helper.assertTrue(run(helper, "worldborder shape add s1 sphere 100 at " + at(far)) == 1
                && run(helper, "worldborder shape add s2 sphere 100 at " + at(far.offset(150, 0, 0))) == 1
                && run(helper, "worldborder shape add s3 sphere 100 at " + at(far.offset(450, 0, 0))) == 1, "big spheres");
        helper.assertTrue(BorderService.area(data, "s1").size() == 2 && BorderService.area(data, "s3").size() == 1, "big shapes connect by sampling");
        helper.assertTrue(run(helper, "worldborder shape protect s1 inside") == 1 && BorderProtection.isProtected(level, far.offset(150, 0, 0))
                && !BorderProtection.isProtected(level, far.offset(450, 0, 0)), "big area protected");
        run(helper, "worldborder shape clear");
        helper.succeed();
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @GameTest(template = "empty", batch = "mapblocks", timeoutTicks = 400)
    public static void mapBlocksAndWeapons(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        resetSettings(helper);
        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        run(helper, "worldborder protection remember all");
        level.getWorldBorder().setCenter(0.0, 0.0);
        level.getWorldBorder().setSize(5.9999968E7);
        BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1)).offset(0, 60, 0);
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-1, -1, -1), o.offset(9, 7, 9)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        helper.assertTrue(run(helper, "worldborder shape add a box from " + at(o) + " to " + at(o.offset(8, 6, 8))) == 1, "box");
        BlockPos map = o.offset(4, 1, 4);
        level.setBlock(map, Blocks.STONE.defaultBlockState(), 3);

        helper.assertTrue(run(helper, "worldborder settings set protectMapBlocksOnly true") == 1, "map blocks only");
        FakePlayer survivor = FakePlayerFactory.getMinecraft(level);
        survivor.setGameMode(GameType.SURVIVAL);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 8));
        BlockPos placed = map.above();
        survivor.gameMode.useItemOn(survivor, level, survivor.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(map).add(0, 0.5, 0), Direction.UP, map, false));
        helper.assertTrue(level.getBlockState(placed).is(Blocks.OAK_PLANKS), "survival placed a block");
        helper.assertTrue(PlayerBlocks.isPlayerBlock(level, placed) && !PlayerBlocks.isPlayerBlock(level, map), "player block remembered");

        helper.assertTrue(run(helper, "worldborder shape protect a inside") == 1, "protect after placing");
        helper.assertTrue(BorderProtection.isProtected(level, map) && !BorderProtection.isProtected(level, placed), "only map blocks protected");
        helper.assertTrue(!survivor.gameMode.destroyBlock(map) && level.getBlockState(map).is(Blocks.STONE), "map block holds");
        helper.assertTrue(survivor.gameMode.destroyBlock(placed) && level.getBlockState(placed).isAir(), "player block breaks");
        helper.assertTrue(!PlayerBlocks.isPlayerBlock(level, placed), "forgotten after breaking");

        level.setBlock(placed, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        PlayerBlocks.get(level).record(placed, Blocks.OAK_PLANKS);
        helper.assertTrue(!BorderProtection.isProtected(level, placed), "remembered player block");
        helper.assertTrue(run(helper, "worldborder protection remember a") == 1 && BorderProtection.isProtected(level, placed), "remember makes it a map block");
        level.setBlock(placed, Blocks.AIR.defaultBlockState(), 3);
        PlayerBlocks.get(level).record(placed, Blocks.OAK_PLANKS);
        helper.assertTrue(BorderProtection.isProtected(level, placed), "a stale record of another block type doesn't free the spot");

        helper.assertTrue(run(helper, "worldborder settings set protectMapBlocksOnly false") == 1, "off");
        level.setBlock(placed, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        helper.assertTrue(BorderProtection.isProtected(level, placed), "with the setting off, everything is protected");
        CompoundTag saved = PlayerBlocks.get(level).save(new CompoundTag());
        helper.assertTrue(saved.getLongArray("positions").length == PlayerBlocks.get(level).size(), "saved");

        BlockPos target = o.offset(2, 1, 2);
        level.setBlock(target, Blocks.GLASS.defaultBlockState(), 3);
        helper.assertTrue(!new Charge(level).explode(target) && !Charge.inLambda(level, target)
                && !new Shell(level).hit(target, null) && !new Shell(level).hit(target, survivor)
                && level.getBlockState(target).is(Blocks.GLASS), "other mods can't break protected blocks directly");
        survivor.setGameMode(GameType.CREATIVE);
        helper.assertTrue(new Shell(level).hit(target, survivor) && level.getBlockState(target).isAir(), "a creative breaker may");
        survivor.setGameMode(GameType.SURVIVAL);
        level.setBlock(target, Blocks.GLASS.defaultBlockState(), 3);
        helper.assertTrue(run(helper, "setblock " + at(target) + " air destroy") == 1 && level.getBlockState(target).isAir(), "commands may");
        level.setBlock(target, Blocks.GLASS.defaultBlockState(), 3);
        helper.assertTrue(level.destroyBlock(target, false) && level.getBlockState(target).isAir(), "vanilla code may");
        level.setBlock(target, Blocks.GLASS.defaultBlockState(), 3);
        BlockPos outside = o.offset(12, 1, 2);
        level.setBlock(outside, Blocks.GLASS.defaultBlockState(), 3);
        Explosion late = new Explosion(level, null, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 2.0F, false, Explosion.BlockInteraction.DESTROY);
        late.getToBlow().add(target);
        late.getToBlow().addAll(List.of(target, outside));
        late.finalizeExplosion(false);
        helper.assertTrue(level.getBlockState(target).is(Blocks.GLASS) && level.getBlockState(outside).isAir(), "blocks added to an explosion late still respect protection");
        helper.assertTrue(run(helper, "worldborder shape protect a none") == 1
                && new Charge(level).explode(target) && level.getBlockState(target).isAir(), "other mods still break unprotected blocks");

        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        run(helper, "worldborder shape protect a inside");
        BlockPos ground = o.offset(6, 1, 6);
        level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
        survivor.gameMode.useItemOn(survivor, level, survivor.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
        helper.assertTrue(level.getBlockState(ground.above()).isAir(), "flint and steel blocked in a protected area");
        run(helper, "worldborder shape protect a none");
        survivor.gameMode.useItemOn(survivor, level, survivor.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
        helper.assertTrue(level.getBlockState(ground.above()).is(Blocks.FIRE), "flint and steel works when unprotected");
        level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);

        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-1, -1, -1), o.offset(9, 7, 9)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        run(helper, "worldborder shape clear");
        run(helper, "worldborder protection remember all");
        resetSettings(helper);
        helper.succeed();
    }

    private static final String[] DOCUMENTED = {
            "worldborder shape add arena sphere 40",
            "worldborder shape add pit cylinder 6 10 at ~ ~-8 ~ mode subtract",
            "worldborder shape resize arena to 60 over 30",
            "worldborder gui",
            "worldborder shape clear",
            "worldborder classic outline circle",
            "worldborder shape add zone sphere 300 at 0 70 0",
            "worldborder shape resize zone to 40 over 600",
            "worldborder shape rotate gate by 90 0 0 over 5",
            "worldborder shape move lift by 0 20 0 over 20",
            "worldborder set 1000",
            "worldborder set 100 600",
            "worldborder shape add zone cylinder 500 400 at 0 64 0",
            "worldborder shape move zone to 300 64 -200 over 300",
            "worldborder shape resize zone to 150 over 300",
            "worldborder center 0 0",
            "worldborder set 1200",
            "worldborder damage buffer 0",
            "worldborder damage amount 1",
            "worldborder warning distance 15",
            "worldborder settings set blockProjectiles true",
            "worldborder set 600 240",
            "worldborder set 250 180",
            "worldborder set 20 120",
            "worldborder shape resize zone to 120 over 240",
            "worldborder shape add arena box from -30 60 -30 to 30 90 30",
            "worldborder settings set protectEdge true",
            "worldborder settings set protectThickness 2",
            "worldborder shape protect lobby inside",
            "worldborder shape protect arena edge",
            "worldborder shape protect mine none",
            "worldborder shape add lobby box 40 12 40 at 0 70 0",
            "worldborder shape add dome sphere 25 at 0 76 0",
            "worldborder shape add arena box 60 20 60 at 300 70 0",
            "worldborder shape protect dome inside",
            "worldborder settings set protectMapBlocksOnly true",
            "worldborder protection remember all",
            "worldborder protection remember arena",
            "worldborder shape add arena sphere 40 at 0 80 0",
            "worldborder shape add tower cylinder 12 120 at 100 100 0",
            "worldborder shape add spire cone 20 60 at here",
            "worldborder shape add track torus 80 6 at 0 64 0",
            "worldborder shape add room box 30 12 30 at 0 70 0",
            "worldborder shape add room box from -15 64 -15 to 15 76 15",
            "worldborder shape add pit cylinder 6 20 at ~ ~-10 ~ mode subtract",
            "worldborder shape move arena to 0 100 0",
            "worldborder shape move arena to here",
            "worldborder shape resize arena to 60",
            "worldborder shape resize tower to 12 200",
            "worldborder shape resize room to 40 12 40",
            "worldborder shape resize zone to 30 over 1800",
            "worldborder shape resize arena times 0.5 over 60",
            "worldborder shape rotate tunnel to 0 90 0",
            "worldborder shape rotate ring to 45 90 0",
            "worldborder shape rotate spike to 0 180 0",
            "worldborder shape scale egg to 1 1.5 1",
            "worldborder shape scale lens to 1 0.25 1",
            "worldborder shape reshape arena cylinder 40 30",
            "worldborder shape reshape room box from 0 60 0 to 20 80 20 over 10",
            "worldborder shape list",
            "worldborder protection",
            "worldborder classic",
            "worldborder classic on",
            "worldborder classic off",
            "worldborder classic outline",
            "worldborder set 2000",
            "worldborder set 200 1800",
            "worldborder formula",
            "worldborder formula auto",
            "worldborder formula set all - jail",
            "worldborder formula set (outer - inner) ^ cross",
            "worldborder formula set border & (spawn | arena1 | arena2 | arena3)",
            "worldborder settings",
            "worldborder settings reset all",
            "worldborder settings reload",
            "worldborder settings set protectThickness 3",
            "worldborder settings get insideWarning",
            "worldborder settings reset borderFadeDistance",
            "worldborder set 4000",
            "worldborder set 800",
            "worldborder set 40 1800",
            "worldborder classic outline star",
            "worldborder set 1500",
            "worldborder shape add zone sphere 400 at 0 64 0",
            "worldborder shape add dome sphere 50 at 0 64 0",
            "worldborder shape add floor box 120 60 120 at 0 34 0 mode subtract",
            "worldborder shape add shaft cylinder 8 20 at 0 60 0",
            "worldborder shape add arena box 80 30 80 at 200 75 0",
            "worldborder shape add arena box from -40 60 -40 to 40 100 40",
            "worldborder shape add island sphere 60 at 0 120 0",
            "worldborder shape scale island to 1 0.5 1",
            "worldborder shape add cave sphere 15 at 0 110 0 mode subtract",
            "worldborder shape add gate box 20 20 4 at 0 70 50 mode subtract",
            "worldborder shape rotate gate by 3600 0 0 over 600",
            "worldborder shape add lift box 5 6 5 at 0 64 0",
            "worldborder shape move lift by 0 60 0 over 30",
            "worldborder shape add hub cylinder 30 40 at 0 70 0",
            "worldborder shape add north box 60 30 60 at 0 70 -200",
            "worldborder shape add south box 60 30 60 at 0 70 200",
            "worldborder shape add bridge box 6 6 400 at 0 70 0",
            "worldborder formula set border & (hub | north | south | bridge)",
            "worldborder classic outline square",
            "execute at @e[tag=br_next,limit=1] run worldborder shape move zone to ~ 64 ~ over 240"
    };

    @GameTest(template = "empty", batch = "docs", timeoutTicks = 100)
    public static void documentedCommandsParse(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO))).withSuppressedOutput();
        List<String> broken = new ArrayList<>();
        for (String command : DOCUMENTED)
        {
            ParseResults<CommandSourceStack> parse = level.getServer().getCommands().getDispatcher().parse(command, source);
            CommandContextBuilder<CommandSourceStack> last = parse.getContext();
            while (last.getChild() != null) last = last.getChild();
            boolean complete = !parse.getReader().canRead() && parse.getExceptions().isEmpty() && last.getCommand() != null;
            if (!complete) broken.add(command);
        }
        helper.assertTrue(broken.isEmpty(), "documented commands that don't parse: " + broken);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "protection", timeoutTicks = 100000)
    public static void protectedEdge(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        WorldBorder border = level.getWorldBorder();
        BlockPos o = helper.absolutePos(new BlockPos(1, 1, 1)).offset(0, 30, 0);
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-7, -7, -7), o.offset(7, 7, 7)))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        run(helper, "worldborder shape clear");
        run(helper, "worldborder classic outline square");
        helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
        helper.getLevel().getWorldBorder().setSize(5.9999968E7);
        resetSettings(helper);
        String box = (o.getX() - 4) + " " + (o.getY() - 4) + " " + (o.getZ() - 4) + " to " + (o.getX() + 4) + " " + (o.getY() + 4) + " " + (o.getZ() + 4);
        helper.assertTrue(run(helper, "worldborder shape add arena box from " + box) == 1, "arena");

        BlockPos wall = o.offset(4, 0, 0);
        BlockPos inner = o.offset(3, 0, 0);
        BlockPos floor = o.offset(0, -4, 0);
        BlockPos ceiling = o.offset(0, 4, 0);
        helper.assertTrue(!BorderProtection.isProtected(level, wall), "off by default");
        helper.assertTrue(run(helper, "worldborder settings set protectEdge true") == 1, "turn on");
        try
        {
            String saved = java.nio.file.Files.readString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("serverconfig").resolve("csgworldborder-server.toml"));
            helper.assertTrue(saved.contains("protectEdge = true"), "saved in the world folder");
        }
        catch (java.io.IOException e)
        {
            helper.fail("settings file missing: " + e);
        }
        helper.assertTrue(run(helper, "worldborder settings reload") == 1 && BorderSettings.server().protectEdge(), "reload keeps the value");
        helper.assertTrue(BorderProtection.isProtected(level, wall) && BorderProtection.isProtected(level, floor)
                && BorderProtection.isProtected(level, ceiling) && !BorderProtection.isProtected(level, inner)
                && !BorderProtection.isProtected(level, o) && !BorderProtection.isProtected(level, o.offset(5, 0, 0)), "edge layer only");
        helper.assertTrue(run(helper, "worldborder settings set protectFloor false") == 1 && !BorderProtection.isProtected(level, floor)
                && BorderProtection.isProtected(level, ceiling), "floor toggle");
        helper.assertTrue(run(helper, "worldborder settings set protectThickness 2") == 1 && BorderProtection.isProtected(level, inner)
                && !BorderProtection.isProtected(level, o.offset(2, 0, 0)), "two layers");
        helper.assertTrue(run(helper, "worldborder settings set protectThickness 9") == 0, "range checked");
        run(helper, "worldborder settings set protectThickness 1");
        run(helper, "worldborder settings set protectFloor true");

        FakePlayer survivor = FakePlayerFactory.getMinecraft(level);
        survivor.setGameMode(GameType.SURVIVAL);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!survivor.gameMode.destroyBlock(wall) && level.getBlockState(wall).is(Blocks.STONE), "survival cannot break the wall");
        level.setBlock(inner, Blocks.STONE.defaultBlockState(), 3);
        boolean broke = survivor.gameMode.destroyBlock(inner);
        helper.assertTrue(broke && level.getBlockState(inner).isAir(), "survival can break inside: broke=" + broke + " state=" + level.getBlockState(inner)
                + " protected=" + BorderProtection.isProtected(level, inner) + " spawnProtected=" + level.getServer().isUnderSpawnProtection(level, inner, survivor)
                + " mayInteract=" + level.mayInteract(survivor, inner) + " mode=" + survivor.gameMode.getGameModeForPlayer());

        BlockPos wallAir = o.offset(4, 1, 0);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        new ItemStack(Items.STONE).useOn(new UseOnContext(survivor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(wallAir), Direction.WEST, wallAir, false)));
        helper.assertTrue(level.getBlockState(wallAir).isAir(), "survival cannot place in the edge");
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        ((BucketItem) Items.WATER_BUCKET).emptyContents(survivor, level, wallAir, null, survivor.getMainHandItem());
        helper.assertTrue(level.getFluidState(wallAir).isEmpty(), "survival cannot pour into the edge");

        survivor.setGameMode(GameType.CREATIVE);
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        new ItemStack(Items.STONE).useOn(new UseOnContext(survivor, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(wallAir), Direction.WEST, wallAir, false)));
        helper.assertTrue(level.getBlockState(wallAir).is(Blocks.STONE), "creative can place in the edge");
        helper.assertTrue(survivor.gameMode.destroyBlock(wallAir) && level.getBlockState(wallAir).isAir(), "creative can break the edge");
        survivor.setGameMode(GameType.SURVIVAL);

        for (int dy = -3; dy <= 3; dy++)
        {
            for (int dz = -3; dz <= 3; dz++)
            {
                level.setBlock(o.offset(4, dy, dz), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(o.offset(3, dy, dz), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        level.explode(null, o.getX() + 2.5, o.getY() + 0.5, o.getZ() + 0.5, 4.0F, Level.ExplosionInteraction.BLOCK);
        boolean wallIntact = true;
        for (int dy = -3; dy <= 3; dy++)
        {
            for (int dz = -3; dz <= 3; dz++) wallIntact &= level.getBlockState(o.offset(4, dy, dz)).is(Blocks.STONE);
        }
        helper.assertTrue(wallIntact, "explosion leaves the wall");
        helper.assertTrue(level.getBlockState(inner).isAir(), "explosion breaks the inside");

        level.setBlock(o.offset(1, 0, 0), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), 3);
        level.setBlock(o.offset(2, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(o.offset(3, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(o.offset(1, 0, 1), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);

        level.setBlock(o.offset(3, 2, 0), Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(o.offset(4, 2, 0), Blocks.TORCH.defaultBlockState(), 3);

        helper.runAfterDelay(30, () ->
        {
            helper.assertTrue(level.getBlockState(o.offset(2, 0, 0)).is(Blocks.STONE) && level.getBlockState(wall).is(Blocks.STONE),
                    "piston could not push into the wall");
            helper.assertTrue(level.getBlockState(o.offset(4, 2, 0)).is(Blocks.TORCH), "water does not wash away the edge torch");
            helper.assertTrue(!level.getFluidState(o.offset(2, 2, 0)).isEmpty(), "water still flows inside");

            for (BlockPos pos : BlockPos.betweenClosed(o.offset(-7, -7, -7), o.offset(7, 7, 7)))
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            helper.assertTrue(run(helper, "worldborder settings set blockProjectiles true") == 1, "projectiles on");
            Snowball probe = new Snowball(level, o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5);
            Vec3 start = Vec3.atCenterOf(o), end = start.add(30.0, 0.0, 0.0);
            BlockHitResult traced = Bullet.trace(level, probe, start, end);
            helper.assertTrue(traced.getType() == HitResult.Type.BLOCK && !level.getWorldBorder().isWithinBounds(traced.getBlockPos())
                    && traced.getLocation().x < o.getX() + 6.0, "a mod's own raycast stops at the border");
            helper.assertTrue(Bullet.trace(level, null, start, end).getType() == HitResult.Type.MISS, "a raycast without a projectile passes");
            Snowball drifter = new Snowball(level, o.getX() + 0.5, o.getY() - 1.5, o.getZ() + 0.5);
            drifter.setNoGravity(true);
            level.addFreshEntity(drifter);
            Snowball kept = new Snowball(level, o.getX() + 0.5, o.getY() - 2.5, o.getZ() + 0.5);
            kept.setNoGravity(true);
            level.addFreshEntity(kept);
            Arrow arrow = new Arrow(level, o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5);
            arrow.setDeltaMovement(2.5, 0.0, 0.0);
            level.addFreshEntity(arrow);
            Snowball snowball = new Snowball(level, o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 0.5);
            snowball.setDeltaMovement(2.5, 0.0, 0.0);
            level.addFreshEntity(snowball);
            helper.runAfterDelay(20, () ->
            {
                helper.assertTrue(arrow.getX() < o.getX() + 5.0, "arrow stopped at the wall, x offset " + (arrow.getX() - o.getX()));
                helper.assertTrue(snowball.isRemoved(), "snowball broke on the wall");
                drifter.setPos(o.getX() + 20.5, o.getY() - 1.5, o.getZ() + 0.5);
                helper.assertTrue(drifter.isRemoved(), "a projectile moving itself outside is removed at the border");
                helper.assertTrue(run(helper, "worldborder settings set blockProjectiles false") == 1, "projectiles off");
                kept.setPos(o.getX() + 20.5, o.getY() - 2.5, o.getZ() + 0.5);
                helper.assertTrue(!kept.isRemoved() && kept.getX() > o.getX() + 20.0, "setting off: it may leave");
                kept.discard();
                helper.assertTrue(Bullet.trace(level, probe, start, end).getType() == HitResult.Type.MISS, "setting off: the raycast passes");
                Arrow free = new Arrow(level, o.getX() + 0.5, o.getY() + 2.5, o.getZ() + 0.5);
                free.setNoGravity(true);
                free.setDeltaMovement(2.5, 0.0, 0.0);
                level.addFreshEntity(free);
                helper.runAfterDelay(10, () ->
                {
                    helper.assertTrue(free.getX() > o.getX() + 6.0, "without the setting arrows fly through, x offset " + (free.getX() - o.getX()));
                    free.discard();
                    arrow.discard();
                    resetSettings(helper);
                    run(helper, "worldborder shape clear");
                    run(helper, "worldborder classic outline square");
                    helper.getLevel().getWorldBorder().setCenter(0.0, 0.0);
                    helper.getLevel().getWorldBorder().setSize(5.9999968E7);
                    helper.succeed();
                });
            });
        });
    }

    private static void resetSettings(GameTestHelper helper)
    {
        run(helper, "worldborder settings set protectEdge false");
        run(helper, "worldborder settings set protectThickness 1");
        run(helper, "worldborder settings set protectWalls true");
        run(helper, "worldborder settings set protectFloor true");
        run(helper, "worldborder settings set protectCeiling true");
        run(helper, "worldborder settings set protectMapBlocksOnly false");
        run(helper, "worldborder settings set blockProjectiles false");
    }

    private static void waitFor(GameTestHelper helper, BooleanSupplier condition, long deadline, Runnable then)
    {
        if (condition.getAsBoolean() || Util.getMillis() > deadline)
        {
            then.run();
            return;
        }
        helper.runAfterDelay(2, () -> waitFor(helper, condition, deadline, then));
    }

    private static String formula(ServerLevel level)
    {
        return CsgBorderData.get(level).expression().format();
    }

    private static int run(GameTestHelper helper, String command)
    {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level).withPermission(4).withSuppressedOutput();
        return level.getServer().getCommands().performPrefixedCommand(source, command);
    }
}
