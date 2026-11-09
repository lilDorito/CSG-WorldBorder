package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.csg.ExpressionParser;
import com.lildorito.csgworldborder.csg.Layers;
import com.lildorito.csgworldborder.csg.Mode;
import com.lildorito.csgworldborder.csg.Node;
import com.lildorito.csgworldborder.csg.Outline;
import com.lildorito.csgworldborder.csg.Protection;
import com.lildorito.csgworldborder.csg.Shape;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsgBorderData extends SavedData
{
    private static final String DATA_NAME = CsgWorldBorder.MODID;

    private final Map<String, Shape> shapes = new LinkedHashMap<>();
    private final Map<String, Mode> modes = new HashMap<>();
    private final Map<String, Protection> protections = new HashMap<>();
    private boolean classic = true;
    private Outline outline = Outline.SQUARE;
    @Nullable
    private Node formula;

    public static CsgBorderData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(CsgBorderData::load, CsgBorderData::new, DATA_NAME);
    }

    public static CsgBorderData load(CompoundTag tag)
    {
        CsgBorderData data = new CsgBorderData();
        long now = Util.getMillis();
        ListTag list = tag.getList("shapes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            Shape shape = ShapeCodec.load(entry, now);
            if (shape == null) continue;
            Mode mode = Mode.byId(entry.getString("mode"));
            data.shapes.put(shape.name(), shape);
            data.modes.put(shape.name(), mode == null ? Mode.ADD : mode);
            Protection protection = Protection.byId(entry.getString("protect"));
            if (protection != null && protection != Protection.NONE) data.protections.put(shape.name(), protection);
        }
        data.classic = !tag.contains("classic") || tag.getBoolean("classic");
        Outline outline = Outline.byId(tag.getString("outline"));
        data.outline = outline == null ? Outline.SQUARE : outline;

        String text = tag.getString("formula");
        if (!text.isEmpty() && !data.shapes.isEmpty())
        {
            try
            {
                data.formula = ExpressionParser.parse(text);
            }
            catch (IllegalArgumentException e)
            {
                CsgWorldBorder.LOGGER.error("CSG WorldBorder: invalid saved formula '{}', using layers", text);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        long now = Util.getMillis();
        tag.putBoolean("classic", classic);
        tag.putString("outline", outline.id());
        if (formula != null) tag.putString("formula", formula.format());
        ListTag list = new ListTag();
        for (Shape shape : shapes.values())
        {
            CompoundTag entry = ShapeCodec.save(shape, now);
            entry.putString("mode", mode(shape.name()).id());
            if (protection(shape.name()) != Protection.NONE) entry.putString("protect", protection(shape.name()).id());
            list.add(entry);
        }
        tag.put("shapes", list);
        return tag;
    }

    @Override
    public boolean isDirty()
    {
        return super.isDirty() || anyMoving();
    }

    private boolean anyMoving()
    {
        long now = Util.getMillis();
        for (Shape shape : shapes.values())
        {
            if (shape.isMoving(now)) return true;
        }
        return false;
    }

    public Node expression()
    {
        if (shapes.isEmpty()) return Node.BORDER;
        if (formula != null) return formula;
        return Layers.build(layers(), classic);
    }

    public List<Layers.Layer> layers()
    {
        List<Layers.Layer> layers = new ArrayList<>(shapes.size());
        for (String name : shapes.keySet()) layers.add(new Layers.Layer(name, mode(name)));
        return layers;
    }

    public Map<String, Shape> shapes() { return Collections.unmodifiableMap(shapes); }
    public boolean classic() { return classic; }
    public boolean hasCustomFormula() { return formula != null; }

    @Nullable
    public Node formula() { return formula; }

    @Nullable
    public Shape shape(String name) { return shapes.get(name); }

    public Mode mode(String name)
    {
        return modes.getOrDefault(name, Mode.ADD);
    }

    public Protection protection(String name)
    {
        return protections.getOrDefault(name, Protection.NONE);
    }

    public void setProtection(String name, Protection protection)
    {
        if (!shapes.containsKey(name)) return;
        if (protection == Protection.NONE) protections.remove(name);
        else protections.put(name, protection);
        setDirty();
    }

    public Map<String, Protection> protectionRules()
    {
        Map<String, Protection> rules = new LinkedHashMap<>();
        for (String name : shapes.keySet())
        {
            if (mode(name) == Mode.ADD) rules.put(name, protection(name));
        }
        boolean any = false;
        for (Protection protection : rules.values()) any |= protection != Protection.NONE;
        return any ? rules : Map.of();
    }

    public boolean putShape(Shape shape)
    {
        boolean existed = shapes.put(shape.name(), shape) != null;
        modes.putIfAbsent(shape.name(), Mode.ADD);
        setDirty();
        return existed;
    }

    public void setMode(String name, Mode mode)
    {
        if (!shapes.containsKey(name)) return;
        modes.put(name, mode);
        setDirty();
    }

    public boolean rename(String from, String to)
    {
        if (!shapes.containsKey(from) || shapes.containsKey(to)) return false;
        Map<String, Shape> copy = new LinkedHashMap<>(shapes);
        shapes.clear();
        for (Map.Entry<String, Shape> entry : copy.entrySet())
        {
            Shape shape = entry.getValue();
            if (entry.getKey().equals(from))
            {
                shape = new Shape(to, shape.from(), shape.to(), shape.start(), shape.duration());
            }
            shapes.put(shape.name(), shape);
        }
        modes.put(to, modes.getOrDefault(from, Mode.ADD));
        modes.remove(from);
        Protection protection = protections.remove(from);
        if (protection != null) protections.put(to, protection);
        if (formula != null) formula = renameIn(formula, from, to);
        setDirty();
        return true;
    }

    public boolean removeShape(String name)
    {
        boolean removed = shapes.remove(name) != null;
        if (!removed) return false;
        modes.remove(name);
        protections.remove(name);
        if (shapes.isEmpty()) formula = null;
        setDirty();
        return true;
    }

    public void clear()
    {
        shapes.clear();
        modes.clear();
        protections.clear();
        formula = null;
        setDirty();
    }

    public boolean move(String name, int offset)
    {
        List<String> order = new ArrayList<>(shapes.keySet());
        int index = order.indexOf(name);
        if (index < 0) return false;
        int target = Math.max(0, Math.min(order.size() - 1, index + offset));
        if (target == index) return false;
        order.remove(index);
        order.add(target, name);
        Map<String, Shape> copy = new LinkedHashMap<>(shapes);
        shapes.clear();
        for (String key : order) shapes.put(key, copy.get(key));
        setDirty();
        return true;
    }

    public Outline outline() { return outline; }

    public void setOutline(Outline outline)
    {
        this.outline = outline;
        setDirty();
    }

    public void setClassic(boolean classic)
    {
        this.classic = classic;
        setDirty();
    }

    public void setFormula(@Nullable Node formula)
    {
        this.formula = formula;
        setDirty();
    }

    private static Node renameIn(Node node, String from, String to)
    {
        if (node instanceof Node.Ref ref) return ref.name().equals(from) ? new Node.Ref(to) : ref;
        if (node instanceof Node.Combine combine)
        {
            return new Node.Combine(combine.operation(), renameIn(combine.left(), from, to), renameIn(combine.right(), from, to));
        }
        return node;
    }
}
