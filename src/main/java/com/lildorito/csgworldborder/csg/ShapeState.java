package com.lildorito.csgworldborder.csg;

public record ShapeState(Primitive primitive, double a, double b, double c, Transform transform)
{
    public ShapeState lerp(ShapeState to, double t)
    {
        if (to.primitive != primitive) return t < 1.0 ? this : to;
        return new ShapeState(primitive,
                Transform.lerp(a, to.a, t), Transform.lerp(b, to.b, t), Transform.lerp(c, to.c, t),
                transform.lerp(to.transform, t));
    }

    public ShapeState withTransform(Transform transform)
    {
        return new ShapeState(primitive, a, b, c, transform);
    }

    public ShapeState withSize(double a, double b, double c)
    {
        return new ShapeState(primitive, a, b, c, transform);
    }
}
