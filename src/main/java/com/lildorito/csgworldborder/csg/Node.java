package com.lildorito.csgworldborder.csg;

import java.util.Set;

public sealed interface Node permits Node.Border, Node.All, Node.Ref, Node.Combine
{
    Node BORDER = new Border();
    Node ALL = new All();

    String format(int parentPrecedence);

    void collectNames(Set<String> names);

    default String format()
    {
        return format(0);
    }

    record Border() implements Node
    {
        @Override
        public String format(int parentPrecedence) { return ExpressionParser.BORDER; }

        @Override
        public void collectNames(Set<String> names) {}
    }

    record All() implements Node
    {
        @Override
        public String format(int parentPrecedence) { return ExpressionParser.ALL; }

        @Override
        public void collectNames(Set<String> names) {}
    }

    record Ref(String name) implements Node
    {
        @Override
        public String format(int parentPrecedence) { return name; }

        @Override
        public void collectNames(Set<String> names) { names.add(name); }
    }

    record Combine(Operation operation, Node left, Node right) implements Node
    {
        @Override
        public String format(int parentPrecedence)
        {
            int precedence = ExpressionParser.precedence(operation);
            String text = left.format(precedence) + " " + operation.symbol() + " " + right.format(precedence + 1);
            return precedence < parentPrecedence ? "(" + text + ")" : text;
        }

        @Override
        public void collectNames(Set<String> names)
        {
            left.collectNames(names);
            right.collectNames(names);
        }
    }
}
