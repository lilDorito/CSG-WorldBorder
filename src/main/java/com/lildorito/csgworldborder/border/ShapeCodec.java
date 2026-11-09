package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.csg.Primitive;
import com.lildorito.csgworldborder.csg.Shape;
import com.lildorito.csgworldborder.csg.ShapeState;
import com.lildorito.csgworldborder.csg.Transform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

public final class ShapeCodec
{
    private ShapeCodec() {}

    public static CompoundTag save(Shape shape, long now)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", shape.name());
        tag.put("from", saveState(shape.from()));
        tag.put("to", saveState(shape.to()));
        tag.putLong("elapsed", shape.elapsed(now));
        tag.putLong("duration", shape.isMoving(now) ? shape.duration() : 0L);
        return tag;
    }

    @Nullable
    public static Shape load(CompoundTag tag, long now)
    {
        ShapeState from = loadState(tag.getCompound("from"));
        ShapeState to = loadState(tag.getCompound("to"));
        if (from == null || to == null) return null;
        Shape shape = new Shape(tag.getString("name"), from, to, 0L, tag.getLong("duration"));
        return shape.rebase(tag.getLong("elapsed"), now);
    }

    public static void write(FriendlyByteBuf buf, Shape shape, long now)
    {
        buf.writeUtf(shape.name());
        writeState(buf, shape.from());
        writeState(buf, shape.to());
        buf.writeVarLong(shape.elapsed(now));
        buf.writeVarLong(shape.isMoving(now) ? shape.duration() : 0L);
    }

    public static Shape read(FriendlyByteBuf buf, long now)
    {
        String name = buf.readUtf();
        ShapeState from = readState(buf);
        ShapeState to = readState(buf);
        long elapsed = buf.readVarLong();
        long duration = buf.readVarLong();
        return new Shape(name, from, to, 0L, duration).rebase(elapsed, now);
    }

    private static CompoundTag saveState(ShapeState state)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("primitive", state.primitive().id());
        tag.putDouble("a", state.a());
        tag.putDouble("b", state.b());
        tag.putDouble("c", state.c());
        Transform t = state.transform();
        ListTag values = new ListTag();
        for (double value : new double[] {t.x(), t.y(), t.z(), t.yaw(), t.pitch(), t.roll(), t.scaleX(), t.scaleY(), t.scaleZ()})
        {
            values.add(DoubleTag.valueOf(value));
        }
        tag.put("transform", values);
        return tag;
    }

    @Nullable
    private static ShapeState loadState(CompoundTag tag)
    {
        Primitive primitive = Primitive.byId(tag.getString("primitive"));
        if (primitive == null) return null;
        ListTag values = tag.getList("transform", Tag.TAG_DOUBLE);
        if (values.size() != 9) return null;
        Transform transform = new Transform(values.getDouble(0), values.getDouble(1), values.getDouble(2),
                values.getDouble(3), values.getDouble(4), values.getDouble(5),
                values.getDouble(6), values.getDouble(7), values.getDouble(8));
        return new ShapeState(primitive, tag.getDouble("a"), tag.getDouble("b"), tag.getDouble("c"), transform);
    }

    public static void writeState(FriendlyByteBuf buf, ShapeState state)
    {
        buf.writeEnum(state.primitive());
        buf.writeDouble(state.a());
        buf.writeDouble(state.b());
        buf.writeDouble(state.c());
        Transform t = state.transform();
        buf.writeDouble(t.x());
        buf.writeDouble(t.y());
        buf.writeDouble(t.z());
        buf.writeDouble(t.yaw());
        buf.writeDouble(t.pitch());
        buf.writeDouble(t.roll());
        buf.writeDouble(t.scaleX());
        buf.writeDouble(t.scaleY());
        buf.writeDouble(t.scaleZ());
    }

    public static ShapeState readState(FriendlyByteBuf buf)
    {
        Primitive primitive = buf.readEnum(Primitive.class);
        double a = buf.readDouble();
        double b = buf.readDouble();
        double c = buf.readDouble();
        Transform transform = new Transform(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new ShapeState(primitive, a, b, c, transform);
    }
}
