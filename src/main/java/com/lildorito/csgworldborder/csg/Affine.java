package com.lildorito.csgworldborder.csg;

public final class Affine
{
    private static final double MIN_SCALE = 1.0E-6;

    private final double tx;
    private final double ty;
    private final double tz;
    private final double r00, r01, r02, r10, r11, r12, r20, r21, r22;
    private final double i00, i01, i02, i10, i11, i12, i20, i21, i22;
    private final double sx;
    private final double sy;
    private final double sz;
    private final double distanceScale;

    Affine(Transform transform)
    {
        tx = transform.x();
        ty = transform.y();
        tz = transform.z();
        sx = Math.max(Math.abs(transform.scaleX()), MIN_SCALE);
        sy = Math.max(Math.abs(transform.scaleY()), MIN_SCALE);
        sz = Math.max(Math.abs(transform.scaleZ()), MIN_SCALE);
        distanceScale = Math.min(sx, Math.min(sy, sz));

        double a = Math.toRadians(-transform.yaw());
        double b = Math.toRadians(transform.pitch());
        double c = Math.toRadians(transform.roll());
        double ca = Math.cos(a), sa = Math.sin(a);
        double cb = Math.cos(b), sb = Math.sin(b);
        double cc = Math.cos(c), sc = Math.sin(c);

        double m00 = ca, m01 = sa * sb, m02 = sa * cb;
        double m10 = 0.0, m11 = cb, m12 = -sb;
        double m20 = -sa, m21 = ca * sb, m22 = ca * cb;

        r00 = m00 * cc + m01 * sc;
        r01 = -m00 * sc + m01 * cc;
        r02 = m02;
        r10 = m10 * cc + m11 * sc;
        r11 = -m10 * sc + m11 * cc;
        r12 = m12;
        r20 = m20 * cc + m21 * sc;
        r21 = -m20 * sc + m21 * cc;
        r22 = m22;

        i00 = r00 / sx; i01 = r10 / sx; i02 = r20 / sx;
        i10 = r01 / sy; i11 = r11 / sy; i12 = r21 / sy;
        i20 = r02 / sz; i21 = r12 / sz; i22 = r22 / sz;
    }

    public double localX(double x, double y, double z)
    {
        return i00 * (x - tx) + i01 * (y - ty) + i02 * (z - tz);
    }

    public double localY(double x, double y, double z)
    {
        return i10 * (x - tx) + i11 * (y - ty) + i12 * (z - tz);
    }

    public double localZ(double x, double y, double z)
    {
        return i20 * (x - tx) + i21 * (y - ty) + i22 * (z - tz);
    }

    public Bounds bounds(double ex, double ey, double ez)
    {
        double hx = Math.abs(r00) * sx * ex + Math.abs(r01) * sy * ey + Math.abs(r02) * sz * ez;
        double hy = Math.abs(r10) * sx * ex + Math.abs(r11) * sy * ey + Math.abs(r12) * sz * ez;
        double hz = Math.abs(r20) * sx * ex + Math.abs(r21) * sy * ey + Math.abs(r22) * sz * ez;
        return new Bounds(tx - hx, ty - hy, tz - hz, tx + hx, ty + hy, tz + hz);
    }

    public double distanceScale() { return distanceScale; }
}
