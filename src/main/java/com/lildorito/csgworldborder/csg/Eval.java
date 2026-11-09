package com.lildorito.csgworldborder.csg;

import java.util.List;
import java.util.Map;

abstract sealed class Eval permits Eval.Leaf, Eval.SquareLeaf, Eval.Constant, Eval.Combine
{
    static final double FAR = 1.0E9;
    static final double CURVE_PAD = 0.5;
    static final double MARGIN = 1.0;
    static final int SLICE_SAMPLES = 32;
    static final int SLICE_GRID = 16;

    final Bounds bounds;

    Eval(Bounds bounds)
    {
        this.bounds = bounds;
    }

    abstract boolean contains(int x, int y, int z);

    abstract double distance(double x, double y, double z);

    abstract boolean contains2D(int x, int z);

    static Eval compile(Node node, Map<String, Shape> shapes, Square square, long now, List<Bounds> moving)
    {
        if (node instanceof Node.Border) return new SquareLeaf(square);
        if (node instanceof Node.All) return Constant.FILLED;
        if (node instanceof Node.Ref ref)
        {
            Shape shape = shapes.get(ref.name());
            if (shape == null) return Constant.VOID;
            Leaf leaf = new Leaf(shape.state(now));
            if (moving != null && shape.isMoving(now)) moving.add(leaf.bounds);
            return leaf;
        }
        Node.Combine combine = (Node.Combine) node;
        Eval left = compile(combine.left(), shapes, square, now, moving);
        Eval right = compile(combine.right(), shapes, square, now, moving);
        return new Combine(combine.operation(), left, right);
    }

    static Bounds leafBounds(ShapeState state)
    {
        return leafBounds(state, state.transform().affine());
    }

    static Bounds leafBounds(ShapeState state, Affine affine)
    {
        Primitive p = state.primitive();
        Bounds b = affine.bounds(
                p.extentX(state.a(), state.b(), state.c()),
                p.extentY(state.a(), state.b(), state.c()),
                p.extentZ(state.a(), state.b(), state.c()));
        double m = MARGIN + (p == Primitive.BOX ? 0.0 : CURVE_PAD);
        return new Bounds(b.minX() - m, b.minY() - m, b.minZ() - m, b.maxX() + m, b.maxY() + m, b.maxZ() + m);
    }

    static final class Leaf extends Eval
    {
        private final Primitive primitive;
        private final double a;
        private final double b;
        private final double c;
        private final Affine affine;
        private final double pad;
        private final int sliceY;

        Leaf(ShapeState state)
        {
            this(state, state.transform().affine());
        }

        private Leaf(ShapeState state, Affine affine)
        {
            super(leafBounds(state, affine));
            this.primitive = state.primitive();
            this.a = state.a();
            this.b = state.b();
            this.c = state.c();
            this.affine = affine;
            this.pad = CURVE_PAD / affine.distanceScale();
            this.sliceY = widestSlice(state);
        }

        private int widestSlice(ShapeState state)
        {
            Transform t = state.transform();
            if (t.pitch() % 360.0 == 0.0 && t.roll() % 360.0 == 0.0)
            {
                if (primitive != Primitive.CONE) return (int) Math.floor(t.y());
                return (int) Math.ceil(t.y() - state.b() * 0.5 * t.scaleY() - 0.5);
            }
            int lowY = (int) Math.floor(bounds.minY()), highY = (int) Math.ceil(bounds.maxY());
            int stepY = Math.max(1, (highY - lowY) / SLICE_SAMPLES);
            double spanX = bounds.maxX() - bounds.minX(), spanZ = bounds.maxZ() - bounds.minZ();
            int best = (int) Math.floor(t.y());
            int bestCount = -1;
            for (int y = lowY; y <= highY; y += stepY)
            {
                int count = 0;
                for (int i = 0; i < SLICE_GRID; i++)
                {
                    for (int j = 0; j < SLICE_GRID; j++)
                    {
                        int x = (int) Math.floor(bounds.minX() + spanX * (i + 0.5) / SLICE_GRID);
                        int z = (int) Math.floor(bounds.minZ() + spanZ * (j + 0.5) / SLICE_GRID);
                        if (contains(x, y, z)) count++;
                    }
                }
                if (count > bestCount)
                {
                    bestCount = count;
                    best = y;
                }
            }
            return best;
        }

        @Override
        boolean contains2D(int x, int z)
        {
            return contains(x, sliceY, z);
        }

        @Override
        boolean contains(int x, int y, int z)
        {
            double px = x + 0.5;
            double py = y + 0.5;
            double pz = z + 0.5;
            if (!bounds.contains(px, py, pz)) return false;
            return primitive.contains(a, b, c,
                    affine.localX(px, py, pz), affine.localY(px, py, pz), affine.localZ(px, py, pz), pad);
        }

        @Override
        double distance(double x, double y, double z)
        {
            return primitive.distance(a, b, c, affine.localX(x, y, z), affine.localY(x, y, z), affine.localZ(x, y, z))
                    * affine.distanceScale();
        }
    }

    static final class SquareLeaf extends Eval
    {
        private final Square square;

        SquareLeaf(Square square)
        {
            super(square.bounds());
            this.square = square;
        }

        @Override
        boolean contains(int x, int y, int z)
        {
            return square.containsCell(x, z);
        }

        @Override
        boolean contains2D(int x, int z)
        {
            return square.containsCell(x, z);
        }

        @Override
        double distance(double x, double y, double z)
        {
            return square.distance(x, z);
        }
    }

    static final class Constant extends Eval
    {
        static final Constant FILLED = new Constant(true);
        static final Constant VOID = new Constant(false);

        private final boolean value;

        private Constant(boolean value)
        {
            super(value ? Bounds.INFINITE : Bounds.EMPTY);
            this.value = value;
        }

        @Override
        boolean contains(int x, int y, int z) { return value; }

        @Override
        boolean contains2D(int x, int z) { return value; }

        @Override
        double distance(double x, double y, double z) { return value ? -FAR : FAR; }
    }

    static final class Combine extends Eval
    {
        private final Operation operation;
        private final Eval left;
        private final Eval right;

        Combine(Operation operation, Eval left, Eval right)
        {
            super(combinedBounds(operation, left.bounds, right.bounds));
            this.operation = operation;
            this.left = left;
            this.right = right;
        }

        private static Bounds combinedBounds(Operation operation, Bounds left, Bounds right)
        {
            return switch (operation)
            {
                case UNION, XOR -> left.union(right);
                case INTERSECT -> left.intersect(right);
                case SUBTRACT -> left;
            };
        }

        @Override
        boolean contains(int x, int y, int z)
        {
            return switch (operation)
            {
                case UNION -> left.contains(x, y, z) || right.contains(x, y, z);
                case INTERSECT -> left.contains(x, y, z) && right.contains(x, y, z);
                case SUBTRACT -> left.contains(x, y, z) && !right.contains(x, y, z);
                case XOR -> left.contains(x, y, z) != right.contains(x, y, z);
            };
        }

        @Override
        boolean contains2D(int x, int z)
        {
            return switch (operation)
            {
                case UNION -> left.contains2D(x, z) || right.contains2D(x, z);
                case INTERSECT -> left.contains2D(x, z) && right.contains2D(x, z);
                case SUBTRACT -> left.contains2D(x, z) && !right.contains2D(x, z);
                case XOR -> left.contains2D(x, z) != right.contains2D(x, z);
            };
        }

        @Override
        double distance(double x, double y, double z)
        {
            double l = left.distance(x, y, z);
            double r = right.distance(x, y, z);
            return switch (operation)
            {
                case UNION -> Math.min(l, r);
                case INTERSECT -> Math.max(l, r);
                case SUBTRACT -> Math.max(l, -r);
                case XOR -> Math.max(Math.min(l, r), -Math.max(l, r));
            };
        }
    }
}
