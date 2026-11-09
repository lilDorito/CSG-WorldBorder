package com.lildorito.csgworldborder.border;

import com.lildorito.csgworldborder.csg.Areas;
import com.lildorito.csgworldborder.csg.ExpressionParser;
import com.lildorito.csgworldborder.csg.Mode;
import com.lildorito.csgworldborder.csg.Node;
import com.lildorito.csgworldborder.csg.Outline;
import com.lildorito.csgworldborder.csg.Protection;
import com.lildorito.csgworldborder.csg.Shape;
import com.lildorito.csgworldborder.csg.ShapeState;
import com.lildorito.csgworldborder.csg.Transform;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class BorderService
{
    public static final double MIN_SIZE = 0.01;
    public static final double MAX_SIZE = 6.0E7;
    public static final double MIN_SCALE = 0.001;
    public static final double MAX_SCALE = 10000.0;
    public static final double MAX_COORDINATE = 3.0E7;
    public static final int MAX_SECONDS = 1_000_000;
    public static final int MAX_SHAPES = 256;

    private BorderService() {}

    public record Result(boolean ok, String message)
    {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    public static Result edit(ServerLevel level, @Nullable String oldName, String name, ShapeState target,
                              @Nullable Mode mode, int seconds)
    {
        if (!ExpressionParser.isValidName(name)) return Result.fail(invalidName(name));
        String problem = validate(target);
        if (problem != null) return Result.fail(problem);
        if (seconds < 0 || seconds > MAX_SECONDS) return Result.fail("Time must be between 0 and " + MAX_SECONDS + " seconds");

        CsgBorderData data = CsgBorderData.get(level);
        String current = oldName != null && data.shape(oldName) != null ? oldName : name;
        if (!current.equals(name))
        {
            if (data.shape(name) != null) return Result.fail("A shape named " + name + " already exists");
            data.rename(current, name);
        }

        Shape old = data.shape(name);
        if (old == null && data.shapes().size() >= MAX_SHAPES) return Result.fail("Too many shapes (limit " + MAX_SHAPES + ")");

        long now = Util.getMillis();
        boolean animated = old != null && seconds > 0 && old.state(now).primitive() == target.primitive();
        Shape shape = old == null ? Shape.still(name, target) : old.animateTo(target, now, animated ? seconds * 1000L : 0L);
        data.putShape(shape);
        if (mode != null) data.setMode(name, mode);
        if (data.mode(name) != Mode.ADD) data.setProtection(name, Protection.NONE);
        BorderManager.update(level);

        String verb = old == null ? "Created " : "Updated ";
        return Result.ok(verb + describe(name, target, data.mode(name)) + (animated ? " over " + seconds + "s" : "")
                + (data.hasCustomFormula() && old == null ? ". Your custom formula ignores new shapes until you add it there" : ""));
    }

    public static Result setMode(ServerLevel level, String name, Mode mode)
    {
        CsgBorderData data = CsgBorderData.get(level);
        if (data.shape(name) == null) return noShape(name);
        data.setMode(name, mode);
        if (mode != Mode.ADD) data.setProtection(name, Protection.NONE);
        BorderManager.update(level);
        return Result.ok("Shape " + name + " now " + mode.id() + "s" + (data.hasCustomFormula() ? " (custom formula is active, modes are ignored)" : ""));
    }

    public static Result rename(ServerLevel level, String from, String to)
    {
        if (!ExpressionParser.isValidName(to)) return Result.fail(invalidName(to));
        CsgBorderData data = CsgBorderData.get(level);
        if (data.shape(from) == null) return noShape(from);
        if (data.shape(to) != null) return Result.fail("A shape named " + to + " already exists");
        data.rename(from, to);
        BorderManager.update(level);
        return Result.ok("Renamed " + from + " to " + to);
    }

    public static Result remove(ServerLevel level, String name)
    {
        CsgBorderData data = CsgBorderData.get(level);
        if (!data.removeShape(name)) return noShape(name);
        BorderManager.update(level);
        return Result.ok("Removed " + name + (data.shapes().isEmpty() ? ". No shapes left, the classic border is back" : ""));
    }

    public static Result clear(ServerLevel level)
    {
        CsgBorderData data = CsgBorderData.get(level);
        int count = data.shapes().size();
        data.clear();
        BorderManager.update(level);
        return Result.ok("Removed " + count + " shape(s). The classic border is back");
    }

    public static Result add(ServerLevel level, String name, ShapeState state, @Nullable Mode mode)
    {
        if (!ExpressionParser.isValidName(name)) return Result.fail(invalidName(name));
        if (CsgBorderData.get(level).shape(name) != null)
        {
            return Result.fail("A shape named " + name + " already exists. Change it with move, resize, rotate, scale or reshape, or pick another name");
        }
        return edit(level, null, name, state, mode == null ? Mode.ADD : mode, 0);
    }

    public static Result move(ServerLevel level, String name, double x, double y, double z, boolean relative, int seconds)
    {
        return change(level, name, seconds, "Moved", "Moving", s ->
        {
            Transform t = s.transform();
            return s.withTransform(relative ? t.withPosition(t.x() + x, t.y() + y, t.z() + z) : t.withPosition(x, y, z));
        }, s -> "to " + fmt(s.transform().x()) + " " + fmt(s.transform().y()) + " " + fmt(s.transform().z()));
    }

    public static Result resize(ServerLevel level, String name, double[] sizes, int seconds)
    {
        return change(level, name, seconds, "Resized", "Resizing", s ->
        {
            double[] out = {s.a(), s.b(), s.c()};
            int count = Math.min(sizes.length, s.primitive().parameters());
            for (int i = 0; i < count; i++) out[i] = sizes[i];
            return s.withSize(out[0], out[1], out[2]);
        }, s -> "to " + sizeText(s));
    }

    public static Result resizeTimes(ServerLevel level, String name, double factor, int seconds)
    {
        return change(level, name, seconds, "Resized", "Resizing",
                s -> s.withSize(s.a() * factor, s.b() * factor, s.c() * factor), s -> "to " + sizeText(s));
    }

    public static Result rotate(ServerLevel level, String name, double yaw, double pitch, double roll, boolean relative, int seconds)
    {
        return change(level, name, seconds, "Rotated", "Rotating", s ->
        {
            Transform t = s.transform();
            return s.withTransform(relative
                    ? t.withRotation(t.yaw() + yaw, t.pitch() + pitch, t.roll() + roll)
                    : t.withRotation(yaw, pitch, roll));
        }, s -> "to " + fmt(s.transform().yaw()) + " " + fmt(s.transform().pitch()) + " " + fmt(s.transform().roll()));
    }

    public static Result scale(ServerLevel level, String name, double sx, double sy, double sz, int seconds)
    {
        return change(level, name, seconds, "Scaled", "Scaling", s -> s.withTransform(s.transform().withScale(sx, sy, sz)),
                s -> "to " + fmt(s.transform().scaleX()) + " " + fmt(s.transform().scaleY()) + " " + fmt(s.transform().scaleZ()));
    }

    public static Result reshape(ServerLevel level, String name, ShapeState target, int seconds)
    {
        return change(level, name, seconds, "Reshaped", "Reshaping", s -> target,
                s -> "into a " + s.primitive().id() + " " + sizeText(s));
    }

    private static Result change(ServerLevel level, String name, int seconds, String done, String doing,
                                 UnaryOperator<ShapeState> change, Function<ShapeState, String> detail)
    {
        if (seconds < 0 || seconds > MAX_SECONDS) return Result.fail("Time must be between 0 and " + MAX_SECONDS + " seconds");
        CsgBorderData data = CsgBorderData.get(level);
        Shape old = data.shape(name);
        if (old == null) return noShape(name);
        ShapeState target = change.apply(old.to());
        String problem = validate(target);
        if (problem != null) return Result.fail(problem);
        long now = Util.getMillis();
        boolean morphs = old.state(now).primitive() == target.primitive();
        boolean animated = seconds > 0 && morphs;
        data.putShape(old.animateTo(target, now, animated ? seconds * 1000L : 0L));
        BorderManager.update(level);
        return Result.ok((animated ? doing : done) + " " + name + " " + detail.apply(target)
                + (animated ? " over " + seconds + "s" : "")
                + (seconds > 0 && !morphs ? " instantly, a shape can't animate into another type" : ""));
    }

    public static Result order(ServerLevel level, String name, int offset)
    {
        CsgBorderData data = CsgBorderData.get(level);
        if (data.shape(name) == null) return noShape(name);
        if (!data.move(name, offset)) return Result.ok(name + " is already at the " + (offset < 0 ? "top" : "bottom"));
        BorderManager.update(level);
        int index = new ArrayList<>(data.shapes().keySet()).indexOf(name) + 1;
        return Result.ok("Moved " + name + " to layer " + index + " of " + data.shapes().size()
                + (data.hasCustomFormula() ? " (custom formula is active, layer order is ignored)" : ""));
    }

    public static Result setOutline(ServerLevel level, String id)
    {
        Outline outline = Outline.byId(id);
        if (outline == null) return Result.fail("Unknown outline " + id + ". Use " + outlineList());
        return setOutline(level, outline);
    }

    public static Result setOutline(ServerLevel level, Outline outline)
    {
        CsgBorderData data = CsgBorderData.get(level);
        data.setOutline(outline);
        BorderManager.update(level);
        boolean ignored = !Volume.usesBorder(data.expression());
        return Result.ok("The classic border is now a " + outline.id()
                + (ignored ? ". It doesn't limit your shapes right now: classic is off, or your formula has no border" : ""));
    }

    public static String outlineList()
    {
        List<String> ids = new ArrayList<>();
        for (Outline outline : Outline.values()) ids.add(outline.id());
        return String.join(", ", ids);
    }

    public static String sizeText(ShapeState state)
    {
        return switch (state.primitive())
        {
            case BOX -> fmt(state.a()) + "x" + fmt(state.b()) + "x" + fmt(state.c());
            case SPHERE -> "radius " + fmt(state.a());
            case CYLINDER, CONE -> "radius " + fmt(state.a()) + " height " + fmt(state.b());
            case TORUS -> "radius " + fmt(state.a()) + " thickness " + fmt(state.b());
        };
    }

    public static Result protect(ServerLevel level, String name, String id)
    {
        Protection protection = Protection.byId(id);
        if (protection == null) return Result.fail("Unknown protection " + id + ". Use none, edge or inside");
        return protect(level, name, protection);
    }

    public static Result protect(ServerLevel level, String name, Protection protection)
    {
        CsgBorderData data = CsgBorderData.get(level);
        if (data.shape(name) == null) return noShape(name);
        if (data.mode(name) != Mode.ADD)
        {
            return Result.fail(name + " is a " + data.mode(name).id() + " shape. Only add shapes make areas: protect one of the add shapes it touches");
        }
        List<String> area = area(data, name);
        for (String member : area) data.setProtection(member, protection);
        BorderManager.update(level);
        String what = switch (protection)
        {
            case NONE -> "no longer protected";
            case EDGE -> "protected along the border";
            case INSIDE -> "protected inside";
        };
        return Result.ok((area.size() == 1 ? "Area " + name : "Area " + String.join(" + ", area)) + " is " + what);
    }

    public static Result remember(ServerLevel level, @Nullable String name)
    {
        PlayerBlocks blocks = PlayerBlocks.get(level);
        if (name == null)
        {
            int count = blocks.forgetAll(key -> true);
            return Result.ok("Every block in this dimension is now a map block (" + count + " player block(s) forgotten)");
        }
        CsgBorderData data = CsgBorderData.get(level);
        if (data.shape(name) == null) return noShape(name);
        List<String> area = data.mode(name) == Mode.ADD ? area(data, name) : List.of(name);
        Areas.CellTest region = Areas.region(data.shapes(), area, Util.getMillis());
        int count = blocks.forgetAll(key -> region.test(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key)));
        return Result.ok("Every block in " + (area.size() == 1 ? name : "area " + String.join(" + ", area))
                + " is now a map block (" + count + " player block(s) forgotten)");
    }

    public static List<String> area(CsgBorderData data, String name)
    {
        List<String> group = areas(data).get(name);
        return group == null ? List.of(name) : group;
    }

    public static Map<String, List<String>> areas(CsgBorderData data)
    {
        Map<String, Protection> rules = new LinkedHashMap<>();
        for (String member : data.shapes().keySet())
        {
            if (data.mode(member) == Mode.ADD) rules.put(member, data.protection(member));
        }
        return Areas.build(data.shapes(), rules, Util.getMillis()).groups();
    }

    public static Result setClassic(ServerLevel level, boolean classic)
    {
        CsgBorderData data = CsgBorderData.get(level);
        data.setClassic(classic);
        BorderManager.update(level);
        return Result.ok(classic
                ? "The classic border now also limits your shapes"
                : "Shapes now ignore the classic border" + (data.shapes().isEmpty() ? " (it still applies while there are no shapes)" : ""));
    }

    public static Result setFormula(ServerLevel level, @Nullable String text)
    {
        CsgBorderData data = CsgBorderData.get(level);
        if (text == null || text.isBlank() || text.trim().equalsIgnoreCase("auto"))
        {
            data.setFormula(null);
            BorderManager.update(level);
            return Result.ok("Formula is automatic again: " + data.expression().format());
        }
        Node node;
        try
        {
            node = ExpressionParser.parse(text);
        }
        catch (IllegalArgumentException e)
        {
            return Result.fail("Invalid formula: " + e.getMessage());
        }
        List<String> unknown = new ArrayList<>();
        Set<String> names = new HashSet<>();
        node.collectNames(names);
        for (String n : names)
        {
            if (data.shape(n) == null) unknown.add(n);
        }
        if (!unknown.isEmpty()) return Result.fail("Unknown shape(s) in formula: " + String.join(", ", unknown));
        data.setFormula(node);
        BorderManager.update(level);
        return Result.ok("Formula set to " + node.format());
    }

    @Nullable
    public static String validate(ShapeState s)
    {
        Transform t = s.transform();
        int sizes = s.primitive().parameters();
        double[] dims = {s.a(), s.b(), s.c()};
        for (int i = 0; i < sizes; i++)
        {
            if (!(dims[i] >= MIN_SIZE && dims[i] <= MAX_SIZE)) return "Sizes must be between " + MIN_SIZE + " and " + (long) MAX_SIZE;
        }
        for (double v : new double[] {t.x(), t.y(), t.z()})
        {
            if (!(Math.abs(v) <= MAX_COORDINATE)) return "Position is out of range";
        }
        for (double v : new double[] {t.yaw(), t.pitch(), t.roll()})
        {
            if (!Double.isFinite(v) || Math.abs(v) > 1.0E6) return "Rotation is out of range";
        }
        for (double v : new double[] {t.scaleX(), t.scaleY(), t.scaleZ()})
        {
            if (!(v >= MIN_SCALE && v <= MAX_SCALE)) return "Scale must be between " + MIN_SCALE + " and " + (long) MAX_SCALE;
        }
        return null;
    }

    public static String describe(String name, ShapeState state, Mode mode)
    {
        Transform t = state.transform();
        StringBuilder text = new StringBuilder(name).append(" (").append(mode.id()).append(", ").append(state.primitive().id());
        text.append(" ").append(sizeText(state));
        text.append(") at ").append(fmt(t.x())).append(" ").append(fmt(t.y())).append(" ").append(fmt(t.z()));
        if (t.isRotated()) text.append(", rotated ").append(fmt(t.yaw())).append(" ").append(fmt(t.pitch())).append(" ").append(fmt(t.roll()));
        if (t.scaleX() != 1.0 || t.scaleY() != 1.0 || t.scaleZ() != 1.0)
        {
            text.append(", scaled ").append(fmt(t.scaleX())).append(" ").append(fmt(t.scaleY())).append(" ").append(fmt(t.scaleZ()));
        }
        return text.toString();
    }

    public static String fmt(double value)
    {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static Result noShape(String name)
    {
        return Result.fail("No shape named " + name + ". See /worldborder shape list");
    }

    private static String invalidName(String name)
    {
        return "Invalid shape name '" + name + "'. Use letters, digits and _ (border and all are reserved)";
    }
}
