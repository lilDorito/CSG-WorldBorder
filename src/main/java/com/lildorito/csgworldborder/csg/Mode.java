package com.lildorito.csgworldborder.csg;

import java.util.Locale;

public enum Mode
{
    ADD(Operation.UNION, "+"),
    SUBTRACT(Operation.SUBTRACT, "-"),
    INTERSECT(Operation.INTERSECT, "&");

    private final Operation operation;
    private final String symbol;

    Mode(Operation operation, String symbol)
    {
        this.operation = operation;
        this.symbol = symbol;
    }

    public Operation operation() { return operation; }
    public String symbol() { return symbol; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public String title()
    {
        return switch (this)
        {
            case ADD -> "Add";
            case SUBTRACT -> "Subtract";
            case INTERSECT -> "Intersect";
        };
    }

    public static Mode byId(String id)
    {
        for (Mode mode : values())
        {
            if (mode.id().equals(id)) return mode;
        }
        return null;
    }
}
