package com.lildorito.csgworldborder.csg;

public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
{
    public static final Bounds INFINITE = new Bounds(
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    public static final Bounds EMPTY = new Bounds(
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

    public boolean isEmpty()
    {
        return minX > maxX || minY > maxY || minZ > maxZ;
    }

    public boolean contains(double x, double y, double z)
    {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        return maxX >= this.minX && minX <= this.maxX
                && maxY >= this.minY && minY <= this.maxY
                && maxZ >= this.minZ && minZ <= this.maxZ;
    }

    public Bounds union(Bounds other)
    {
        if (isEmpty()) return other;
        if (other.isEmpty()) return this;
        return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    public Bounds intersect(Bounds other)
    {
        Bounds result = new Bounds(Math.max(minX, other.minX), Math.max(minY, other.minY), Math.max(minZ, other.minZ),
                Math.min(maxX, other.maxX), Math.min(maxY, other.maxY), Math.min(maxZ, other.maxZ));
        return result.isEmpty() ? EMPTY : result;
    }
}
