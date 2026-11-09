package com.lildorito.csgworldborder.csg;

import java.util.Locale;

public enum Primitive
{
    BOX(3)
    {
        @Override
        public boolean contains(double a, double b, double c, double x, double y, double z, double pad)
        {
            return Math.abs(x) <= a * 0.5 + EPSILON && Math.abs(y) <= b * 0.5 + EPSILON && Math.abs(z) <= c * 0.5 + EPSILON;
        }

        @Override
        public double distance(double a, double b, double c, double x, double y, double z)
        {
            double qx = Math.abs(x) - a * 0.5;
            double qy = Math.abs(y) - b * 0.5;
            double qz = Math.abs(z) - c * 0.5;
            double outside = length(Math.max(qx, 0.0), Math.max(qy, 0.0), Math.max(qz, 0.0));
            return outside + Math.min(Math.max(qx, Math.max(qy, qz)), 0.0);
        }

        @Override
        public double extentX(double a, double b, double c) { return a * 0.5; }
        @Override
        public double extentY(double a, double b, double c) { return b * 0.5; }
        @Override
        public double extentZ(double a, double b, double c) { return c * 0.5; }
    },
    SPHERE(1)
    {
        @Override
        public boolean contains(double a, double b, double c, double x, double y, double z, double pad)
        {
            double r = a + pad;
            return x * x + y * y + z * z <= r * r + EPSILON;
        }

        @Override
        public double distance(double a, double b, double c, double x, double y, double z)
        {
            return length(x, y, z) - a;
        }

        @Override
        public double extentX(double a, double b, double c) { return a; }
        @Override
        public double extentY(double a, double b, double c) { return a; }
        @Override
        public double extentZ(double a, double b, double c) { return a; }
    },
    CYLINDER(2)
    {
        @Override
        public boolean contains(double a, double b, double c, double x, double y, double z, double pad)
        {
            double r = a + pad;
            return Math.abs(y) <= b * 0.5 + EPSILON && x * x + z * z <= r * r + EPSILON;
        }

        @Override
        public double distance(double a, double b, double c, double x, double y, double z)
        {
            double dx = Math.sqrt(x * x + z * z) - a;
            double dy = Math.abs(y) - b * 0.5;
            return Math.min(Math.max(dx, dy), 0.0) + length(Math.max(dx, 0.0), Math.max(dy, 0.0), 0.0);
        }

        @Override
        public double extentX(double a, double b, double c) { return a; }
        @Override
        public double extentY(double a, double b, double c) { return b * 0.5; }
        @Override
        public double extentZ(double a, double b, double c) { return a; }
    },
    CONE(2)
    {
        @Override
        public boolean contains(double a, double b, double c, double x, double y, double z, double pad)
        {
            if (Math.abs(y) > b * 0.5 + EPSILON || b <= 0.0) return false;
            double radius = a * (0.5 - y / b) + pad;
            return x * x + z * z <= radius * radius + EPSILON;
        }

        @Override
        public double distance(double a, double b, double c, double x, double y, double z)
        {
            double h = b * 0.5;
            double qx = Math.sqrt(x * x + z * z);
            double qy = y;
            double k2x = -a;
            double k2y = 2.0 * h;
            double cax = qx - Math.min(qx, qy < 0.0 ? a : 0.0);
            double cay = Math.abs(qy) - h;
            double k2 = k2x * k2x + k2y * k2y;
            double t = k2 <= 0.0 ? 0.0 : clamp(((-qx) * k2x + (h - qy) * k2y) / k2, 0.0, 1.0);
            double cbx = qx + k2x * t;
            double cby = qy - h + k2y * t;
            double sign = cbx < 0.0 && cay < 0.0 ? -1.0 : 1.0;
            return sign * Math.sqrt(Math.min(cax * cax + cay * cay, cbx * cbx + cby * cby));
        }

        @Override
        public double extentX(double a, double b, double c) { return a; }
        @Override
        public double extentY(double a, double b, double c) { return b * 0.5; }
        @Override
        public double extentZ(double a, double b, double c) { return a; }
    },
    TORUS(2)
    {
        @Override
        public boolean contains(double a, double b, double c, double x, double y, double z, double pad)
        {
            double ring = Math.sqrt(x * x + z * z) - a;
            double r = b + pad;
            return ring * ring + y * y <= r * r + EPSILON;
        }

        @Override
        public double distance(double a, double b, double c, double x, double y, double z)
        {
            double ring = Math.sqrt(x * x + z * z) - a;
            return Math.sqrt(ring * ring + y * y) - b;
        }

        @Override
        public double extentX(double a, double b, double c) { return a + b; }
        @Override
        public double extentY(double a, double b, double c) { return b; }
        @Override
        public double extentZ(double a, double b, double c) { return a + b; }
    };

    private static final double EPSILON = 1.0E-9;

    private final int parameters;

    Primitive(int parameters)
    {
        this.parameters = parameters;
    }

    public abstract boolean contains(double a, double b, double c, double x, double y, double z, double pad);

    public abstract double distance(double a, double b, double c, double x, double y, double z);

    public abstract double extentX(double a, double b, double c);

    public abstract double extentY(double a, double b, double c);

    public abstract double extentZ(double a, double b, double c);

    public int parameters() { return parameters; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Primitive byId(String id)
    {
        for (Primitive primitive : values())
        {
            if (primitive.id().equals(id)) return primitive;
        }
        return null;
    }

    static double length(double x, double y, double z)
    {
        return Math.sqrt(x * x + y * y + z * z);
    }

    static double clamp(double value, double min, double max)
    {
        return value < min ? min : Math.min(value, max);
    }
}
