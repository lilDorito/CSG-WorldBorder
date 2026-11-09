package com.lildorito.csgworldborder.border;

public final class RawCorners
{
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private RawCorners() {}

    public static void enter()
    {
        DEPTH.get()[0]++;
    }

    public static void exit()
    {
        DEPTH.get()[0]--;
    }

    public static boolean active()
    {
        return DEPTH.get()[0] > 0;
    }
}
