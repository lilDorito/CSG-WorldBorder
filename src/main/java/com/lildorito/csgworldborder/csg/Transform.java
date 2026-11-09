package com.lildorito.csgworldborder.csg;

public record Transform(double x, double y, double z,
                        double yaw, double pitch, double roll,
                        double scaleX, double scaleY, double scaleZ)
{
    public static Transform at(double x, double y, double z)
    {
        return new Transform(x, y, z, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    }

    public Transform withPosition(double x, double y, double z)
    {
        return new Transform(x, y, z, yaw, pitch, roll, scaleX, scaleY, scaleZ);
    }

    public Transform withRotation(double yaw, double pitch, double roll)
    {
        return new Transform(x, y, z, yaw, pitch, roll, scaleX, scaleY, scaleZ);
    }

    public Transform withScale(double scaleX, double scaleY, double scaleZ)
    {
        return new Transform(x, y, z, yaw, pitch, roll, scaleX, scaleY, scaleZ);
    }

    public Transform lerp(Transform to, double t)
    {
        return new Transform(
                lerp(x, to.x, t), lerp(y, to.y, t), lerp(z, to.z, t),
                lerp(yaw, to.yaw, t), lerp(pitch, to.pitch, t), lerp(roll, to.roll, t),
                lerp(scaleX, to.scaleX, t), lerp(scaleY, to.scaleY, t), lerp(scaleZ, to.scaleZ, t));
    }

    public boolean isRotated()
    {
        return yaw % 360.0 != 0.0 || pitch % 360.0 != 0.0 || roll % 360.0 != 0.0;
    }

    public Affine affine()
    {
        return new Affine(this);
    }

    static double lerp(double from, double to, double t)
    {
        return from + (to - from) * t;
    }
}
