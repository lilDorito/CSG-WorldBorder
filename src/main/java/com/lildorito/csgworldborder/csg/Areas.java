package com.lildorito.csgworldborder.csg;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Areas
{
    static final Areas EMPTY = new Areas(List.of(), new int[0], new Protection[0], 0L);

    private static final long REUSE_MILLIS = 1000L;
    private static final long EXACT_CELLS = 1L << 18;
    private static final int SAMPLES = 48;

    private final List<Member> members;
    private final int[] area;
    private final Protection[] protection;
    private final long built;

    private record Member(String name, Eval.Leaf leaf, Protection own) {}

    private Areas(List<Member> members, int[] area, Protection[] protection, long built)
    {
        this.members = members;
        this.area = area;
        this.protection = protection;
        this.built = built;
    }

    public static Areas build(Map<String, Shape> shapes, Map<String, Protection> rules, long now)
    {
        return build(shapes, rules, now, null);
    }

    static Areas build(Map<String, Shape> shapes, Map<String, Protection> rules, long now, @Nullable Areas previous)
    {
        List<Member> members = new ArrayList<>();
        for (Map.Entry<String, Protection> rule : rules.entrySet())
        {
            Shape shape = shapes.get(rule.getKey());
            if (shape != null) members.add(new Member(rule.getKey(), new Eval.Leaf(shape.state(now)), rule.getValue()));
        }
        if (members.isEmpty()) return EMPTY;
        if (previous != null && now - previous.built < REUSE_MILLIS && sameMembers(previous.members, members))
        {
            return new Areas(members, previous.area, previous.protection, previous.built);
        }
        int[] parent = new int[members.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int i = 0; i < members.size(); i++)
        {
            for (int j = i + 1; j < members.size(); j++)
            {
                if (find(parent, i) == find(parent, j)) continue;
                if (touches(members.get(i).leaf(), members.get(j).leaf())) parent[find(parent, i)] = find(parent, j);
            }
        }
        int[] area = new int[members.size()];
        Protection[] protection = new Protection[members.size()];
        Arrays.fill(protection, Protection.NONE);
        for (int i = 0; i < members.size(); i++)
        {
            area[i] = find(parent, i);
            protection[area[i]] = protection[area[i]].max(members.get(i).own());
        }
        return new Areas(members, area, protection, now);
    }

    public Protection at(int x, int y, int z)
    {
        Protection best = Protection.NONE;
        for (int i = 0; i < members.size(); i++)
        {
            Protection candidate = protection[area[i]];
            if (candidate.ordinal() <= best.ordinal()) continue;
            Eval.Leaf leaf = members.get(i).leaf();
            if (leaf.bounds.contains(x + 0.5, y + 0.5, z + 0.5) && leaf.contains(x, y, z)) best = candidate;
        }
        return best;
    }

    public Map<String, List<String>> groups()
    {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<Integer, List<String>> byRoot = new LinkedHashMap<>();
        for (int i = 0; i < members.size(); i++) byRoot.computeIfAbsent(area[i], k -> new ArrayList<>()).add(members.get(i).name());
        for (List<String> names : byRoot.values())
        {
            for (String name : names) groups.put(name, names);
        }
        return groups;
    }

    public Protection protectionOf(String name)
    {
        for (int i = 0; i < members.size(); i++)
        {
            if (members.get(i).name().equals(name)) return protection[area[i]];
        }
        return Protection.NONE;
    }

    @FunctionalInterface
    public interface CellTest
    {
        boolean test(int x, int y, int z);
    }

    public static CellTest region(Map<String, Shape> shapes, List<String> names, long now)
    {
        List<Eval.Leaf> leaves = new ArrayList<>();
        for (String name : names)
        {
            Shape shape = shapes.get(name);
            if (shape != null) leaves.add(new Eval.Leaf(shape.state(now)));
        }
        return (x, y, z) ->
        {
            for (Eval.Leaf leaf : leaves)
            {
                if (leaf.bounds.contains(x + 0.5, y + 0.5, z + 0.5) && leaf.contains(x, y, z)) return true;
            }
            return false;
        };
    }

    private static boolean sameMembers(List<Member> a, List<Member> b)
    {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
        {
            if (!a.get(i).name().equals(b.get(i).name()) || a.get(i).own() != b.get(i).own()) return false;
        }
        return true;
    }

    private static int find(int[] parent, int i)
    {
        while (parent[i] != i)
        {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static boolean touches(Eval.Leaf a, Eval.Leaf b)
    {
        Bounds ab = a.bounds, bb = b.bounds;
        double minX = Math.max(ab.minX(), bb.minX()) - 1.0, maxX = Math.min(ab.maxX(), bb.maxX()) + 1.0;
        double minY = Math.max(ab.minY(), bb.minY()) - 1.0, maxY = Math.min(ab.maxY(), bb.maxY()) + 1.0;
        double minZ = Math.max(ab.minZ(), bb.minZ()) - 1.0, maxZ = Math.min(ab.maxZ(), bb.maxZ()) + 1.0;
        if (minX > maxX || minY > maxY || minZ > maxZ) return false;
        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY), z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.ceil(maxX), y1 = (int) Math.ceil(maxY), z1 = (int) Math.ceil(maxZ);
        long cells = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (cells <= EXACT_CELLS) return touchesExact(a, b, x0, y0, z0, x1, y1, z1);
        double step = Math.max(Math.max(x1 - x0, y1 - y0), z1 - z0) / (double) SAMPLES;
        double reach = step * 0.87 + 1.0;
        for (double x = x0 + step * 0.5; x <= x1; x += step)
        {
            for (double y = y0 + step * 0.5; y <= y1; y += step)
            {
                for (double z = z0 + step * 0.5; z <= z1; z += step)
                {
                    if (Math.max(a.distance(x, y, z), b.distance(x, y, z)) <= reach) return true;
                }
            }
        }
        return false;
    }

    private static boolean touchesExact(Eval.Leaf a, Eval.Leaf b, int x0, int y0, int z0, int x1, int y1, int z1)
    {
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (!a.contains(x, y, z)) continue;
                    if (b.contains(x, y, z) || b.contains(x + 1, y, z) || b.contains(x - 1, y, z) || b.contains(x, y + 1, z)
                            || b.contains(x, y - 1, z) || b.contains(x, y, z + 1) || b.contains(x, y, z - 1)) return true;
                }
            }
        }
        return false;
    }
}
