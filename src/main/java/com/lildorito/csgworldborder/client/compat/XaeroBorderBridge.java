package com.lildorito.csgworldborder.client.compat;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.MapOutline;
import com.lildorito.csgworldborder.csg.Volume;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class XaeroBorderBridge
{
    private static final String CONTEXT = "dev.alazi.xaeroworldborder.client.map.WorldBorderRenderContext";
    private static final String ELEMENT = "dev.alazi.xaeroworldborder.client.map.WorldBorderElement";

    private static final MapOutlineCache CACHE = new MapOutlineCache();
    private static boolean ready;
    private static boolean broken;
    private static Field dimension, cameraX, cameraZ, scale, backgroundScale, screenWidth, screenHeight;
    private static Method setElements;
    private static Constructor<?> element;

    private XaeroBorderBridge() {}

    public static void replace(Object context)
    {
        if (broken) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Volume volume = BorderVolume.active(minecraft.level.getWorldBorder());
        if (volume == null) return;
        try
        {
            if (!ready) bind(context.getClass().getClassLoader());
            if (!minecraft.level.dimension().equals(dimension.get(context))) return;

            double coordinateScale = Math.max(backgroundScale.getDouble(context), 1.0E-6);
            double zoom = Math.max(scale.getDouble(context), 1.0E-6);
            int width = screenWidth.getInt(context);
            int height = screenHeight.getInt(context);
            double halfWidth = width * 0.5 * coordinateScale / zoom;
            double halfHeight = height * 0.5 * coordinateScale / zoom;
            double margin = Math.max(width, height) * coordinateScale / zoom;
            double cx = cameraX.getDouble(context);
            double cz = cameraZ.getDouble(context);

            List<MapOutline.Segment> segments = CACHE.segments(volume,
                    cx - halfWidth - margin, cz - halfHeight - margin, cx + halfWidth + margin, cz + halfHeight + margin,
                    MapOutline.DEFAULT_CELLS_ACROSS);
            List<Object> elements = new ArrayList<>(segments.size());
            for (MapOutline.Segment s : segments) elements.add(element.newInstance(s.x1(), s.z1(), s.x2(), s.z2()));
            setElements.invoke(context, elements);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            broken = true;
            CsgWorldBorder.LOGGER.warn("CSG WorldBorder: could not hook Xaero World Border, it will show the bounding rectangle", e);
        }
    }

    private static void bind(ClassLoader loader) throws ReflectiveOperationException
    {
        Class<?> contextClass = Class.forName(CONTEXT, false, loader);
        Class<?> elementClass = Class.forName(ELEMENT, false, loader);
        dimension = field(contextClass, "mapDimension");
        cameraX = field(contextClass, "cameraX");
        cameraZ = field(contextClass, "cameraZ");
        scale = field(contextClass, "scale");
        backgroundScale = field(contextClass, "backgroundCoordinateScale");
        screenWidth = field(contextClass, "screenWidth");
        screenHeight = field(contextClass, "screenHeight");
        setElements = contextClass.getDeclaredMethod("setElements", List.class);
        setElements.setAccessible(true);
        element = elementClass.getDeclaredConstructor(double.class, double.class, double.class, double.class);
        element.setAccessible(true);
        ready = true;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
