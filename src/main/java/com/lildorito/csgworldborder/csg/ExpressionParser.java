package com.lildorito.csgworldborder.csg;

public final class ExpressionParser
{
    public static final String BORDER = "border";
    public static final String ALL = "all";

    private final String text;
    private int cursor;

    private ExpressionParser(String text)
    {
        this.text = text;
    }

    public static Node parse(String text)
    {
        ExpressionParser parser = new ExpressionParser(text);
        Node node = parser.expression();
        parser.skipSpaces();
        if (parser.cursor < text.length()) throw parser.error("Unexpected '" + text.charAt(parser.cursor) + "'");
        return node;
    }

    public static boolean isValidName(String name)
    {
        if (name.isEmpty() || name.equals(BORDER) || name.equals(ALL)) return false;
        for (int i = 0; i < name.length(); i++)
        {
            if (!isNameChar(name.charAt(i))) return false;
        }
        return true;
    }

    static int precedence(Operation operation)
    {
        return operation == Operation.INTERSECT ? 2 : 1;
    }

    private Node expression()
    {
        Node left = term();
        while (true)
        {
            skipSpaces();
            if (cursor >= text.length()) return left;
            Operation operation = Operation.bySymbol(text.charAt(cursor));
            if (operation == null || operation == Operation.INTERSECT) return left;
            cursor++;
            left = new Node.Combine(operation, left, term());
        }
    }

    private Node term()
    {
        Node left = factor();
        while (true)
        {
            skipSpaces();
            if (cursor >= text.length() || text.charAt(cursor) != '&') return left;
            cursor++;
            left = new Node.Combine(Operation.INTERSECT, left, factor());
        }
    }

    private Node factor()
    {
        skipSpaces();
        if (cursor >= text.length()) throw error("Expected a shape name");
        char c = text.charAt(cursor);
        if (c == '(')
        {
            cursor++;
            Node inner = expression();
            skipSpaces();
            if (cursor >= text.length() || text.charAt(cursor) != ')') throw error("Expected ')'");
            cursor++;
            return inner;
        }
        int start = cursor;
        while (cursor < text.length() && isNameChar(text.charAt(cursor))) cursor++;
        if (start == cursor) throw error("Expected a shape name, found '" + c + "'");
        String name = text.substring(start, cursor);
        if (name.equals(BORDER)) return Node.BORDER;
        if (name.equals(ALL)) return Node.ALL;
        return new Node.Ref(name);
    }

    private void skipSpaces()
    {
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
    }

    private IllegalArgumentException error(String message)
    {
        return new IllegalArgumentException(message + " at position " + (cursor + 1));
    }

    private static boolean isNameChar(char c)
    {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }
}
