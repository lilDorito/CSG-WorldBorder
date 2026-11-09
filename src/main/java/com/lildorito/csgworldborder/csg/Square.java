package com.lildorito.csgworldborder.csg;

public record Square(double minX, double maxX, double minZ, double maxZ, Outline outline)
{
    public static final Square UNLIMITED = new Square(
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    public Square(double minX, double maxX, double minZ, double maxZ)
    {
        this(minX, maxX, minZ, maxZ, Outline.SQUARE);
    }

    public Square withOutline(Outline outline)
    {
        return outline == this.outline ? this : new Square(minX, maxX, minZ, maxZ, outline);
    }

    public boolean containsCell(int x, int z)
    {
        if (!(x + 1 > minX && x < maxX && z + 1 > minZ && z < maxZ)) return false;
        if (outline == Outline.SQUARE || !isFinite()) return true;
        double hx = (maxX - minX) * 0.5, hz = (maxZ - minZ) * 0.5;
        if (hx <= 0.0 || hz <= 0.0) return false;
        return outline.containsUnit((x + 0.5 - (minX + hx)) / hx, (z + 0.5 - (minZ + hz)) / hz);
    }

    public double distance(double x, double z)
    {
        if (outline == Outline.SQUARE || !isFinite())
        {
            double qx = Math.max(minX - x, x - maxX);
            double qz = Math.max(minZ - z, z - maxZ);
            double outside = Math.sqrt(Math.max(qx, 0.0) * Math.max(qx, 0.0) + Math.max(qz, 0.0) * Math.max(qz, 0.0));
            return outside + Math.min(Math.max(qx, qz), 0.0);
        }
        double hx = (maxX - minX) * 0.5, hz = (maxZ - minZ) * 0.5;
        double h = Math.min(hx, hz);
        if (h <= 0.0) return Math.hypot(x - (minX + hx), z - (minZ + hz));
        return outline.distanceUnit((x - (minX + hx)) / hx, (z - (minZ + hz)) / hz) * h;
    }

    public Bounds bounds()
    {
        return new Bounds(Math.floor(minX) - Eval.MARGIN, Double.NEGATIVE_INFINITY, Math.floor(minZ) - Eval.MARGIN,
                Math.ceil(maxX) + Eval.MARGIN, Double.POSITIVE_INFINITY, Math.ceil(maxZ) + Eval.MARGIN);
    }

    private boolean isFinite()
    {
        return Double.isFinite(minX) && Double.isFinite(maxX) && Double.isFinite(minZ) && Double.isFinite(maxZ);
    }
}
