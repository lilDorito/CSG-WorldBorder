package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.border.CsgBorderData;
import com.lildorito.csgworldborder.border.ShapeCodec;
import com.lildorito.csgworldborder.client.ClientBorders;
import com.lildorito.csgworldborder.csg.Mode;
import com.lildorito.csgworldborder.csg.Outline;
import com.lildorito.csgworldborder.csg.Protection;
import com.lildorito.csgworldborder.csg.Shape;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record SyncBorderPacket(ResourceKey<Level> dimension, String expression, List<Shape> shapes, List<Mode> modes,
                               List<Protection> protections,
                               boolean classic, Outline outline, String formula)
{
    public static SyncBorderPacket of(ServerLevel level)
    {
        CsgBorderData data = CsgBorderData.get(level);
        List<Shape> shapes = new ArrayList<>(data.shapes().values());
        List<Mode> modes = new ArrayList<>(shapes.size());
        for (Shape shape : shapes) modes.add(data.mode(shape.name()));
        List<Protection> protections = new ArrayList<>(shapes.size());
        for (Shape shape : shapes) protections.add(data.protection(shape.name()));
        String formula = data.formula() == null ? "" : data.formula().format();
        return new SyncBorderPacket(level.dimension(), data.expression().format(), shapes, modes, protections, data.classic(), data.outline(), formula);
    }

    public Map<String, Protection> protectionRules()
    {
        Map<String, Protection> rules = new LinkedHashMap<>();
        boolean any = false;
        for (int i = 0; i < shapes.size(); i++)
        {
            if (modes.get(i) != Mode.ADD) continue;
            rules.put(shapes.get(i).name(), protections.get(i));
            any |= protections.get(i) != Protection.NONE;
        }
        return any ? rules : Map.of();
    }

    public void encode(FriendlyByteBuf buf)
    {
        long now = Util.getMillis();
        buf.writeResourceKey(dimension);
        buf.writeUtf(expression);
        buf.writeVarInt(shapes.size());
        for (int i = 0; i < shapes.size(); i++)
        {
            ShapeCodec.write(buf, shapes.get(i), now);
            buf.writeEnum(modes.get(i));
            buf.writeEnum(protections.get(i));
        }
        buf.writeBoolean(classic);
        buf.writeEnum(outline);
        buf.writeUtf(formula);
    }

    public static SyncBorderPacket decode(FriendlyByteBuf buf)
    {
        long now = Util.getMillis();
        ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);
        String expression = buf.readUtf();
        int count = buf.readVarInt();
        List<Shape> shapes = new ArrayList<>(count);
        List<Mode> modes = new ArrayList<>(count);
        List<Protection> protections = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            shapes.add(ShapeCodec.read(buf, now));
            modes.add(buf.readEnum(Mode.class));
            protections.add(buf.readEnum(Protection.class));
        }
        boolean classic = buf.readBoolean();
        Outline outline = buf.readEnum(Outline.class);
        String formula = buf.readUtf();
        return new SyncBorderPacket(dimension, expression, shapes, modes, protections, classic, outline, formula);
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBorders.receive(this));
    }
}
