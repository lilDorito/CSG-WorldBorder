package com.lildorito.csgworldborder.csg;

import javax.annotation.Nullable;
import java.util.Locale;

public enum Protection
{
    NONE("None"),
    EDGE("Edge"),
    INSIDE("Inside");

    private final String title;

    Protection(String title)
    {
        this.title = title;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }

    public Protection max(Protection other)
    {
        return other.ordinal() > ordinal() ? other : this;
    }

    @Nullable
    public static Protection byId(String id)
    {
        for (Protection protection : values())
        {
            if (protection.id().equalsIgnoreCase(id)) return protection;
        }
        return null;
    }
}
