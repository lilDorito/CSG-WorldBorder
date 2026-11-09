package com.lildorito.csgworldborder.csg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Volume
{
    private static final int SECTION_CACHE_LIMIT = 16384;
    private static final int SEARCH_RADIUS = 8;
    private static final int MAX_CELLS = 1 << 16;
    private static final int NEAREST_RADIUS = 16;
    private static final int TRACE_STEPS = 64;
    private static final long[] EMPTY_SECTION = new long[64];

    private final Supplier<Square> squareSource;
    private final Map<Long, long[]> sections = new ConcurrentHashMap<>();
    private volatile int revision;
    private final List<Consumer<Bounds>> listeners = new CopyOnWriteArrayList<>();

    private Node expression = Node.BORDER;
    private Map<String, Shape> shapes = Collections.emptyMap();
    private Set<String> referenced = Collections.emptySet();
    private boolean usesBorder = true;
    private Square square = Square.UNLIMITED;
    private Outline outline = Outline.SQUARE;
    private Map<String, Protection> protections = Collections.emptyMap();
    private volatile Areas areas;
    private volatile int areasRevision = -1;
    private Bounds sweep = Bounds.EMPTY;
    private boolean animating;
    private long now;
    private int minY = -(1 << 25);
    private int maxY = 1 << 25;
    private volatile State state = new State(Eval.compile(Node.BORDER, Map.of(), Square.UNLIMITED, 0L, null), List.of(), true);

    private record State(Eval root, List<Bounds> volatileRegions, boolean vanilla) {}

    @FunctionalInterface
    public interface CellConsumer
    {
        void accept(int x, int y, int z);
    }

    public Volume(Supplier<Square> squareSource)
    {
        this.squareSource = squareSource;
    }

    public void define(Node expression, Map<String, Shape> shapes, long now)
    {
        define(expression, shapes, Outline.SQUARE, Map.of(), now);
    }

    public void define(Node expression, Map<String, Shape> shapes, Outline outline, Map<String, Protection> protections, long now)
    {
        this.outline = outline;
        this.protections = Collections.unmodifiableMap(new LinkedHashMap<>(protections));
        this.areas = null;
        this.expression = expression;
        this.shapes = Collections.unmodifiableMap(new LinkedHashMap<>(shapes));
        Set<String> names = new HashSet<>();
        expression.collectNames(names);
        this.referenced = names;
        this.usesBorder = usesBorder(expression);
        this.now = now;
        this.square = squareSource.get().withOutline(outline);
        this.sweep = Bounds.EMPTY;
        sections.clear();
        compile();
        animating = !state.vanilla() && anyShapeMoving(now);
        notifyListeners(Bounds.INFINITE);
    }

    public void tick(long now)
    {
        this.now = now;
        if (state.vanilla()) return;

        Square current = squareSource.get().withOutline(outline);
        boolean squareMoved = usesBorder && !current.equals(square);
        Square previous = square;
        square = current;

        boolean moving = squareMoved || anyShapeMoving(now);
        if (!moving && !animating) return;

        compile();
        if (squareMoved)
        {
            Bounds swept = previous.bounds().union(current.bounds());
            List<Bounds> regions = new ArrayList<>(state.volatileRegions());
            regions.add(swept);
            state = new State(state.root(), List.copyOf(regions), false);
        }
        for (Bounds region : state.volatileRegions())
        {
            sweep = sweep.union(region);
            notifyListeners(region);
        }

        if (!moving)
        {
            invalidate(sweep);
            notifyListeners(sweep);
            sweep = Bounds.EMPTY;
        }
        animating = moving;
    }

    public boolean isVanilla() { return state.vanilla(); }
    public boolean isAnimating() { return animating; }
    public Outline outline() { return outline; }

    public Areas areas()
    {
        int current = revision;
        Areas cached = areas;
        if (cached != null && areasRevision == current) return cached;
        Areas built = protections.isEmpty() ? Areas.EMPTY : Areas.build(shapes, protections, now, animating ? cached : null);
        areas = built;
        areasRevision = current;
        return built;
    }

    public void limitHeight(int minY, int maxY)
    {
        this.minY = minY;
        this.maxY = maxY;
    }

    public int[] nearestInside(double x, double y, double z)
    {
        return nearestInside(x, y, z, minY, maxY);
    }

    public void addListener(Consumer<Bounds> listener)
    {
        listeners.add(listener);
    }

    public boolean contains(int x, int y, int z)
    {
        State s = state;
        Eval root = s.root();
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        if (!root.bounds.contains(cx, cy, cz)) return false;
        for (Bounds region : s.volatileRegions())
        {
            if (region.contains(cx, cy, cz)) return root.contains(x, y, z);
        }

        long key = sectionKey(x >> 4, y >> 4, z >> 4);
        long[] bits = sections.get(key);
        if (bits == null)
        {
            bits = computeSection(root, x >> 4, y >> 4, z >> 4);
            if (sections.size() >= SECTION_CACHE_LIMIT) sections.clear();
            sections.put(key, bits);
        }
        int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        return (bits[index >> 6] >>> (index & 63) & 1L) != 0L;
    }

    public int sectionFill(int sx, int sy, int sz)
    {
        State s = state;
        double x0 = sx * 16.0, y0 = sy * 16.0, z0 = sz * 16.0;
        if (!s.root().bounds.intersects(x0, y0, z0, x0 + 16.0, y0 + 16.0, z0 + 16.0)) return 0;
        for (Bounds region : s.volatileRegions())
        {
            if (region.intersects(x0, y0, z0, x0 + 16.0, y0 + 16.0, z0 + 16.0)) return -1;
        }
        long key = sectionKey(sx, sy, sz);
        long[] bits = sections.get(key);
        if (bits == null)
        {
            bits = computeSection(s.root(), sx, sy, sz);
            if (sections.size() >= SECTION_CACHE_LIMIT) sections.clear();
            sections.put(key, bits);
        }
        long first = bits[0];
        if (first != 0L && first != -1L) return -1;
        for (int i = 1; i < 64; i++)
        {
            if (bits[i] != first) return -1;
        }
        return first == 0L ? 0 : 1;
    }

    public boolean contains(double x, double y, double z)
    {
        return contains(floor(x), floor(y), floor(z));
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        int x0 = floor(minX), y0 = floor(minY), z0 = floor(minZ);
        int x1 = ceil(maxX) - 1, y1 = ceil(maxY) - 1, z1 = ceil(maxZ) - 1;
        if (x1 < x0) x1 = x0;
        if (y1 < y0) y1 = y0;
        if (z1 < z0) z1 = z0;
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS)
        {
            return state.root().bounds.intersects(minX, minY, minZ, maxX, maxY, maxZ);
        }
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (contains(x, y, z)) return true;
                }
            }
        }
        return false;
    }

    public double signedDistance(double px, double py, double pz)
    {
        int bx = floor(px), by = floor(py), bz = floor(pz);
        boolean inside = contains(bx, by, bz);
        double best = Double.POSITIVE_INFINITY;

        for (int r = 1; r <= SEARCH_RADIUS; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dy = -r; dy <= r; dy++)
                {
                    boolean edge = Math.abs(dx) == r || Math.abs(dy) == r;
                    for (int dz = -r; dz <= r; dz += edge ? 1 : 2 * r)
                    {
                        int x = bx + dx, y = by + dy, z = bz + dz;
                        if (contains(x, y, z) == inside) continue;
                        double d = cellDistance(px, py, pz, x, y, z);
                        if (d < best) best = d;
                    }
                }
            }
            if (best <= r) break;
        }

        if (best == Double.POSITIVE_INFINITY)
        {
            best = Math.max(SEARCH_RADIUS, Math.abs(state.root().distance(px, py, pz)));
        }
        return inside ? best : -best;
    }

    public boolean forEachSolidCell(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                    double ownMinX, double ownMinY, double ownMinZ,
                                    double ownMaxX, double ownMaxY, double ownMaxZ,
                                    CellConsumer consumer)
    {
        int x0 = floor(minX), y0 = floor(minY), z0 = floor(minZ);
        int x1 = floor(maxX), y1 = floor(maxY), z1 = floor(maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS) return false;
        double e = 1.0E-7;
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (contains(x, y, z)) continue;
                    boolean overlapsSelf = x + 1 > ownMinX + e && x < ownMaxX - e
                            && y + 1 > ownMinY + e && y < ownMaxY - e
                            && z + 1 > ownMinZ + e && z < ownMaxZ - e;
                    if (overlapsSelf) continue;
                    consumer.accept(x, y, z);
                }
            }
        }
        return true;
    }

    public boolean overlapsNewOutside(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                      double oldMinX, double oldMinY, double oldMinZ,
                                      double oldMaxX, double oldMaxY, double oldMaxZ)
    {
        double e = 1.0E-6;
        int x0 = floor(minX + e), y0 = floor(minY + e), z0 = floor(minZ + e);
        int x1 = ceil(maxX - e) - 1, y1 = ceil(maxY - e) - 1, z1 = ceil(maxZ - e) - 1;
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS) return false;
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (contains(x, y, z)) continue;
                    boolean before = x + 1 > oldMinX + e && x < oldMaxX - e
                            && y + 1 > oldMinY + e && y < oldMaxY - e
                            && z + 1 > oldMinZ + e && z < oldMaxZ - e;
                    if (!before) return true;
                }
            }
        }
        return false;
    }

    public boolean hasSolidCell(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        int x0 = floor(minX), y0 = floor(minY), z0 = floor(minZ);
        int x1 = floor(maxX), y1 = floor(maxY), z1 = floor(maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS) return true;
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (!contains(x, y, z)) return true;
                }
            }
        }
        return false;
    }

    public boolean intersectsColumns(double minX, double maxX, double minZ, double maxZ)
    {
        Bounds b = state.root().bounds;
        return !b.isEmpty() && maxX > b.minX() && minX < b.maxX() && maxZ > b.minZ() && minZ < b.maxZ();
    }

    public int[] nearestInside(double px, double py, double pz, int minY, int maxY)
    {
        if (minY > maxY) return null;
        int[] found = searchInside(px, Math.max(minY, Math.min(maxY, py)), pz, NEAREST_RADIUS, minY, maxY);
        if (found != null) return found;

        Eval root = state.root();
        double x = px, y = py, z = pz;
        for (int i = 0; i < TRACE_STEPS; i++)
        {
            double d = root.distance(x, y, z);
            if (d <= 0.5)
            {
                found = searchInside(x, y, z, 4, minY, maxY);
                if (found != null) return found;
            }
            double h = 0.5;
            double gx = root.distance(x + h, y, z) - root.distance(x - h, y, z);
            double gy = root.distance(x, y + h, z) - root.distance(x, y - h, z);
            double gz = root.distance(x, y, z + h) - root.distance(x, y, z - h);
            double length = Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (length < 1.0E-9 || Double.isNaN(length)) break;
            double step = Math.max(Math.abs(d), 1.0);
            x -= gx / length * step;
            y -= gy / length * step;
            z -= gz / length * step;
            y = Math.max(minY, Math.min(maxY + 0.999, y));
        }
        return null;
    }

    private int[] searchInside(double px, double py, double pz, int radius, int minY, int maxY)
    {
        int bx = floor(px), by = floor(py), bz = floor(pz);
        if (by >= minY && by <= maxY && contains(bx, by, bz)) return new int[] {bx, by, bz};
        int[] best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int r = 1; r <= radius; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dy = -r; dy <= r; dy++)
                {
                    int y = by + dy;
                    if (y < minY || y > maxY) continue;
                    boolean edge = Math.abs(dx) == r || Math.abs(dy) == r;
                    for (int dz = -r; dz <= r; dz += edge ? 1 : 2 * r)
                    {
                        int x = bx + dx, z = bz + dz;
                        if (!contains(x, y, z)) continue;
                        double cx = x + 0.5 - px, cy = y + 0.5 - py, cz = z + 0.5 - pz;
                        double d = cx * cx + cy * cy + cz * cz;
                        if (d < bestDistance)
                        {
                            bestDistance = d;
                            best = new int[] {x, y, z};
                        }
                    }
                }
            }
            if (best != null && Math.sqrt(bestDistance) <= r - 0.5) break;
        }
        return best;
    }

    private boolean anyShapeMoving(long now)
    {
        for (String name : referenced)
        {
            Shape shape = shapes.get(name);
            if (shape != null && shape.isMoving(now)) return true;
        }
        return false;
    }

    private void compile()
    {
        if (expression instanceof Node.Border && outline == Outline.SQUARE)
        {
            state = new State(Eval.compile(expression, shapes, square, now, null), List.of(), true);
            return;
        }
        List<Bounds> moving = new ArrayList<>();
        Eval root = Eval.compile(expression, shapes, square, now, moving);
        state = new State(root, List.copyOf(moving), false);
    }

    private void invalidate(Bounds region)
    {
        if (region.isEmpty()) return;
        if (Double.isInfinite(region.minX()) || Double.isInfinite(region.maxX())
                || Double.isInfinite(region.minZ()) || Double.isInfinite(region.maxZ()))
        {
            sections.clear();
            return;
        }
        sections.keySet().removeIf(key ->
        {
            double sx = sectionX(key) * 16.0;
            double sy = sectionY(key) * 16.0;
            double sz = sectionZ(key) * 16.0;
            return region.intersects(sx, sy, sz, sx + 16.0, sy + 16.0, sz + 16.0);
        });
    }

    private void notifyListeners(Bounds region)
    {
        revision++;
        for (Consumer<Bounds> listener : listeners) listener.accept(region);
    }

    public int revision() { return revision; }

    public double[] mapRect()
    {
        Bounds b = state.root().bounds;
        if (b.isEmpty()) return null;
        return new double[] {
                edge(b.minX() + Eval.MARGIN, true), edge(b.minZ() + Eval.MARGIN, true),
                edge(b.maxX() - Eval.MARGIN, false), edge(b.maxZ() - Eval.MARGIN, false)};
    }

    public boolean mapContains(int x, int z)
    {
        Eval root = state.root();
        Bounds b = root.bounds;
        if (x + 0.5 < b.minX() || x + 0.5 > b.maxX() || z + 0.5 < b.minZ() || z + 0.5 > b.maxZ()) return false;
        return root.contains2D(x, z);
    }

    private static double edge(double value, boolean low)
    {
        if (Double.isInfinite(value)) return Double.NaN;
        return low ? Math.ceil(value - 0.5) : Math.floor(value + 0.5);
    }

    private static long[] computeSection(Eval root, int sx, int sy, int sz)
    {
        double x0 = sx * 16.0, y0 = sy * 16.0, z0 = sz * 16.0;
        if (!root.bounds.intersects(x0, y0, z0, x0 + 16.0, y0 + 16.0, z0 + 16.0)) return EMPTY_SECTION;
        long[] bits = new long[64];
        int bx = sx << 4, by = sy << 4, bz = sz << 4;
        for (int y = 0; y < 16; y++)
        {
            for (int z = 0; z < 16; z++)
            {
                for (int x = 0; x < 16; x++)
                {
                    if (!root.contains(bx + x, by + y, bz + z)) continue;
                    int index = (y << 8) | (z << 4) | x;
                    bits[index >> 6] |= 1L << (index & 63);
                }
            }
        }
        return bits;
    }

    public static boolean usesBorder(Node node)
    {
        if (node instanceof Node.Border) return true;
        if (node instanceof Node.Combine combine) return usesBorder(combine.left()) || usesBorder(combine.right());
        return false;
    }

    private static double cellDistance(double px, double py, double pz, int x, int y, int z)
    {
        double dx = Math.max(Math.max(x - px, px - (x + 1)), 0.0);
        double dy = Math.max(Math.max(y - py, py - (y + 1)), 0.0);
        double dz = Math.max(Math.max(z - pz, pz - (z + 1)), 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static long sectionKey(int sx, int sy, int sz)
    {
        return ((long) sx & 0x3FFFFFL) << 42 | ((long) sy & 0xFFFFFL) << 22 | ((long) sz & 0x3FFFFFL);
    }

    static int sectionX(long key) { return (int) (key >> 42); }
    static int sectionY(long key) { return (int) (key << 22 >> 44); }
    static int sectionZ(long key) { return (int) (key << 42 >> 42); }

    static int floor(double value)
    {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    static int ceil(double value)
    {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }
}
