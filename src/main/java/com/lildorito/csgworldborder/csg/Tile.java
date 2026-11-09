package com.lildorito.csgworldborder.csg;

public record Tile(int axis, int plane, int u0, int v0, int u1, int v1, boolean insidePositive)
{
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;
}
