package com.lildorito.csgworldborder.csg;

public enum Operation
{
    UNION('|'),
    INTERSECT('&'),
    SUBTRACT('-'),
    XOR('^');

    private final char symbol;

    Operation(char symbol)
    {
        this.symbol = symbol;
    }

    public char symbol() { return symbol; }

    public static Operation bySymbol(char symbol)
    {
        if (symbol == '+') return UNION;
        for (Operation operation : values())
        {
            if (operation.symbol == symbol) return operation;
        }
        return null;
    }
}
