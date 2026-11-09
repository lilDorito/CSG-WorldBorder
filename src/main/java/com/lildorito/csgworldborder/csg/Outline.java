package com.lildorito.csgworldborder.csg;

import javax.annotation.Nullable;
import java.util.Locale;

public enum Outline
{
    SQUARE("Square"),
    CIRCLE("Circle"),
    HEXAGON("Hexagon"),
    OCTAGON("Octagon"),
    DIAMOND("Diamond"),
    TRIANGLE("Triangle"),
    STAR("Star");

    private static final double SQRT3 = Math.sqrt(3.0);
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double EPSILON = 1.0E-9;
    private static final double STAR_INNER = Math.cos(Math.toRadians(72.0)) / Math.cos(Math.toRadians(36.0));
    private static final double[][] STAR_POINTS = starPoints();

    private final String title;

    Outline(String title)
    {
        this.title = title;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }

    @Nullable
    public static Outline byId(String id)
    {
        for (Outline outline : values())
        {
            if (outline.id().equalsIgnoreCase(id)) return outline;
        }
        return null;
    }

    boolean containsUnit(double u, double v)
    {
        double au = Math.abs(u), av = Math.abs(v);
        return switch (this)
        {
            case SQUARE -> au <= 1.0 + EPSILON && av <= 1.0 + EPSILON;
            case CIRCLE -> u * u + v * v <= 1.0 + EPSILON;
            case HEXAGON -> av <= SQRT3 * 0.5 + EPSILON && SQRT3 * au + av <= SQRT3 + EPSILON;
            case OCTAGON -> au <= 1.0 + EPSILON && av <= 1.0 + EPSILON && au + av <= SQRT2 + EPSILON;
            case DIAMOND -> au + av <= 1.0 + EPSILON;
            case TRIANGLE -> v <= 0.5 + EPSILON && SQRT3 * 0.5 * au - 0.5 * v <= 0.5 + EPSILON;
            case STAR -> polygon(STAR_POINTS, u, v) <= EPSILON;
        };
    }

    double distanceUnit(double u, double v)
    {
        double au = Math.abs(u), av = Math.abs(v);
        return switch (this)
        {
            case SQUARE -> Math.max(au, av) - 1.0;
            case CIRCLE -> Math.sqrt(u * u + v * v) - 1.0;
            case HEXAGON -> Math.max(av - SQRT3 * 0.5, (SQRT3 * au + av - SQRT3) * 0.5);
            case OCTAGON -> Math.max(Math.max(au, av) - 1.0, (au + av - SQRT2) / SQRT2);
            case DIAMOND -> (au + av - 1.0) / SQRT2;
            case TRIANGLE -> Math.max(v - 0.5, SQRT3 * 0.5 * au - 0.5 * v - 0.5);
            case STAR -> polygon(STAR_POINTS, u, v);
        };
    }

    private static double[][] starPoints()
    {
        double[][] points = new double[10][];
        for (int i = 0; i < 10; i++)
        {
            double angle = Math.toRadians(-90.0 + i * 36.0);
            double radius = i % 2 == 0 ? 1.0 : STAR_INNER;
            points[i] = new double[] {Math.cos(angle) * radius, Math.sin(angle) * radius};
        }
        return points;
    }

    private static double polygon(double[][] points, double u, double v)
    {
        double best = Double.MAX_VALUE;
        double sign = 1.0;
        for (int i = 0, j = points.length - 1; i < points.length; j = i, i++)
        {
            double ex = points[j][0] - points[i][0], ez = points[j][1] - points[i][1];
            double wx = u - points[i][0], wz = v - points[i][1];
            double t = Math.max(0.0, Math.min(1.0, (wx * ex + wz * ez) / (ex * ex + ez * ez)));
            double bx = wx - ex * t, bz = wz - ez * t;
            best = Math.min(best, bx * bx + bz * bz);
            boolean below = v >= points[i][1], above = v < points[j][1], left = ex * wz > ez * wx;
            if ((below && above && left) || (!below && !above && !left)) sign = -sign;
        }
        return sign * Math.sqrt(best);
    }
}
