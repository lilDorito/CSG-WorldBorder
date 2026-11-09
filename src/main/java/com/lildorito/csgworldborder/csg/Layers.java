package com.lildorito.csgworldborder.csg;

import java.util.List;

public final class Layers
{
    private Layers() {}

    public record Layer(String name, Mode mode) {}

    public static Node build(List<Layer> layers, boolean classic)
    {
        if (layers.isEmpty()) return Node.BORDER;
        Node acc = null;
        for (Layer layer : layers)
        {
            Node ref = new Node.Ref(layer.name());
            if (acc == null)
            {
                acc = layer.mode() == Mode.ADD || layer.mode() == Mode.INTERSECT
                        ? ref
                        : new Node.Combine(Operation.SUBTRACT, Node.ALL, ref);
                continue;
            }
            acc = new Node.Combine(layer.mode().operation(), acc, ref);
        }
        if (!classic) return acc;
        return new Node.Combine(Operation.INTERSECT, Node.BORDER, acc);
    }
}
