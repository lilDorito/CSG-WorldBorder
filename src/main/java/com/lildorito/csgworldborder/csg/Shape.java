package com.lildorito.csgworldborder.csg;

public record Shape(String name, ShapeState from, ShapeState to, long start, long duration)
{
    public static Shape still(String name, ShapeState state)
    {
        return new Shape(name, state, state, 0L, 0L);
    }

    public ShapeState state(long now)
    {
        if (duration <= 0L || now >= start + duration) return to;
        if (now <= start) return from;
        return from.lerp(to, (double) (now - start) / (double) duration);
    }

    public boolean isMoving(long now)
    {
        return duration > 0L && now < start + duration;
    }

    public long remaining(long now)
    {
        return isMoving(now) ? start + duration - now : 0L;
    }

    public long elapsed(long now)
    {
        return isMoving(now) ? now - start : 0L;
    }

    public Shape animateTo(ShapeState target, long now, long duration)
    {
        if (duration <= 0L) return still(name, target);
        return new Shape(name, state(now), target, now, duration);
    }

    public Shape rebase(long elapsed, long now)
    {
        if (duration <= 0L || elapsed >= duration) return still(name, to);
        return new Shape(name, from, to, now - elapsed, duration);
    }
}
