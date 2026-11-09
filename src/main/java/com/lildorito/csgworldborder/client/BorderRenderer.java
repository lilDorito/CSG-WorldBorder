package com.lildorito.csgworldborder.client;

import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.csg.Bounds;
import com.lildorito.csgworldborder.csg.Mesher;
import com.lildorito.csgworldborder.csg.Node;
import com.lildorito.csgworldborder.csg.Shape;
import com.lildorito.csgworldborder.csg.ShapeState;
import com.lildorito.csgworldborder.csg.Square;
import com.lildorito.csgworldborder.csg.Tile;
import com.lildorito.csgworldborder.csg.Volume;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.border.BorderStatus;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class BorderRenderer
{
    private static final ResourceLocation FORCEFIELD_LOCATION = new ResourceLocation("textures/misc/forcefield.png");
    private static final double VISIBLE_FRACTION = 0.6;
    private static final long MESH_BUDGET_NANOS = 4_000_000L;
    private static final int PREVIEW_COLOR = 0xFF9A1F;
    private static final float PREVIEW_ALPHA = 0.55F;
    private static final String PREVIEW_NAME = "preview";

    public static final BorderRenderer MAIN = new BorderRenderer();
    public static final BorderRenderer PREVIEW = new BorderRenderer();

    private static ShapeState previewState;
    private static Volume previewVolume;

    private final Map<Long, Entry> meshes = new HashMap<>();
    private final Set<Volume> tracked = Collections.newSetFromMap(new WeakHashMap<>());
    private Volume current;

    private static final class Entry
    {
        List<Tile> tiles;
        boolean dirty;

        Entry(List<Tile> tiles)
        {
            this.tiles = tiles;
        }
    }

    private BorderRenderer() {}

    public static void clear()
    {
        MAIN.reset();
        setPreview(null);
    }

    public static void setPreview(ShapeState state)
    {
        if (state == null)
        {
            previewState = null;
            previewVolume = null;
            PREVIEW.reset();
            return;
        }
        if (state.equals(previewState)) return;
        previewState = state;
        if (previewVolume == null) previewVolume = new Volume(() -> Square.UNLIMITED);
        previewVolume.define(new Node.Ref(PREVIEW_NAME), Map.of(PREVIEW_NAME, Shape.still(PREVIEW_NAME, state)), Util.getMillis());
    }

    public static void renderPreview(Camera camera)
    {
        Volume volume = previewVolume;
        if (volume == null) return;
        PREVIEW.render(camera, volume, PREVIEW_COLOR, 1.0, true);
    }

    private void reset()
    {
        meshes.clear();
        current = null;
    }

    private void invalidate(Volume volume, Bounds region)
    {
        if (volume != current) return;
        if (region.isEmpty()) return;
        if (Double.isInfinite(region.minX()) || Double.isInfinite(region.maxX())
                || Double.isInfinite(region.minZ()) || Double.isInfinite(region.maxZ()))
        {
            for (Entry entry : meshes.values()) entry.dirty = true;
            return;
        }
        int x0 = floor(region.minX() - 1.0) >> 4, x1 = floor(region.maxX() + 1.0) >> 4;
        int z0 = floor(region.minZ() - 1.0) >> 4, z1 = floor(region.maxZ() + 1.0) >> 4;
        double minY = region.minY(), maxY = region.maxY();
        for (Map.Entry<Long, Entry> e : meshes.entrySet())
        {
            long key = e.getKey();
            int sx = sectionX(key), sy = sectionY(key), sz = sectionZ(key);
            if (sx < x0 || sx > x1 || sz < z0 || sz > z1) continue;
            if (sy * 16.0 > maxY + 1.0 || sy * 16.0 + 16.0 < minY - 1.0) continue;
            e.getValue().dirty = true;
        }
    }

    public static void renderMain(Camera camera, Volume volume, WorldBorder border)
    {
        MAIN.render(camera, volume, statusColor(volume, border), BorderSettings.client().borderFadeDistance() / 100.0, false);
    }

    private void render(Camera camera, Volume volume, int color, double fraction, boolean highlight)
    {
        if (current != volume)
        {
            meshes.clear();
            current = volume;
        }
        if (tracked.add(volume)) volume.addListener(region -> invalidate(volume, region));

        Minecraft minecraft = Minecraft.getInstance();
        double range = minecraft.options.getEffectiveRenderDistance() * 16.0;
        double visible = range * fraction;
        Vec3 cam = camera.getPosition();

        int radius = (int) Math.ceil(visible / 16.0);
        int csx = floor(cam.x) >> 4, csy = floor(cam.y) >> 4, csz = floor(cam.z) >> 4;

        List<long[]> pending = new ArrayList<>();
        List<List<Tile>> visibleTiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    int sx = csx + dx, sy = csy + dy, sz = csz + dz;
                    double nearest = sectionDistance(cam, sx, sy, sz);
                    if (nearest > visible) continue;
                    long key = key(sx, sy, sz);
                    Entry entry = meshes.get(key);
                    if (entry == null || entry.dirty) pending.add(new long[] {key, (long) nearest});
                    if (entry != null && !entry.tiles.isEmpty()) visibleTiles.add(entry.tiles);
                }
            }
        }

        if (!pending.isEmpty())
        {
            pending.sort(Comparator.comparingLong(p -> p[1]));
            long deadline = System.nanoTime() + MESH_BUDGET_NANOS;
            for (long[] p : pending)
            {
                long key = p[0];
                List<Tile> tiles = Mesher.mesh(volume, sectionX(key), sectionY(key), sectionZ(key));
                Entry entry = meshes.get(key);
                if (entry == null)
                {
                    meshes.put(key, new Entry(tiles));
                    if (!tiles.isEmpty()) visibleTiles.add(tiles);
                }
                else
                {
                    entry.tiles = tiles;
                    entry.dirty = false;
                }
                if (System.nanoTime() > deadline) break;
            }
        }

        if (visibleTiles.isEmpty()) return;
        draw(cam, highlight ? range : visible / VISIBLE_FRACTION, visible, visibleTiles, color, highlight);
    }

    private static void draw(Vec3 cam, double range, double visible, List<List<Tile>> groups, int color, boolean highlight)
    {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float scroll = (float) (Util.getMillis() % 3000L) / 3000.0F;

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        if (highlight) RenderSystem.defaultBlendFunc();
        else RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderTexture(0, FORCEFIELD_LOCATION);
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.polygonOffset(-3.0F, -3.0F);
        RenderSystem.enablePolygonOffset();
        RenderSystem.disableCull();

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (List<Tile> tiles : groups)
        {
            for (Tile tile : tiles)
            {
                if (distanceToTile(cam, tile) > visible) continue;
                emit(builder, cam, range, tile, scroll, red, green, blue, highlight);
                quads++;
            }
        }
        if (quads > 0) BufferUploader.drawWithShader(builder.end());
        else builder.end();

        RenderSystem.enableCull();
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
    }

    private static void emit(BufferBuilder builder, Vec3 cam, double range, Tile tile,
                             float scroll, float red, float green, float blue, boolean highlight)
    {
        double p = tile.plane();
        double u0 = tile.u0(), u1 = tile.u1(), v0 = tile.v0(), v1 = tile.v1();
        double[][] corners = new double[4][];
        switch (tile.axis())
        {
            case Tile.X ->
            {
                corners[0] = new double[] {p, v0, u0};
                corners[1] = new double[] {p, v0, u1};
                corners[2] = new double[] {p, v1, u1};
                corners[3] = new double[] {p, v1, u0};
            }
            case Tile.Y ->
            {
                corners[0] = new double[] {u0, p, v0};
                corners[1] = new double[] {u1, p, v0};
                corners[2] = new double[] {u1, p, v1};
                corners[3] = new double[] {u0, p, v1};
            }
            default ->
            {
                corners[0] = new double[] {u0, v0, p};
                corners[1] = new double[] {u1, v0, p};
                corners[2] = new double[] {u1, v1, p};
                corners[3] = new double[] {u0, v1, p};
            }
        }
        double[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++)
        {
            double x = corners[i][0], y = corners[i][1], z = corners[i][2];
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            double fade = 1.0 - Math.sqrt(dx * dx + dy * dy + dz * dz) / range;
            fade = fade <= 0.0 ? 0.0 : highlight ? PREVIEW_ALPHA * Math.min(1.0, fade * 4.0) : Math.min(1.0, fade * fade * fade * fade);
            builder.vertex(dx, dy, dz)
                    .uv(scroll + (float) (uv[i][0] * 0.5), scroll + (float) (uv[i][1] * 0.5))
                    .color(red, green, blue, (float) fade)
                    .endVertex();
        }
    }

    private static int statusColor(Volume volume, WorldBorder border)
    {
        BorderStatus status = border.getStatus();
        if (status == BorderStatus.STATIONARY && volume.isAnimating()) status = BorderStatus.SHRINKING;
        return status.getColor();
    }

    private static double distanceToTile(Vec3 cam, Tile tile)
    {
        double minX, maxX, minY, maxY, minZ, maxZ;
        switch (tile.axis())
        {
            case Tile.X -> { minX = maxX = tile.plane(); minY = tile.v0(); maxY = tile.v1(); minZ = tile.u0(); maxZ = tile.u1(); }
            case Tile.Y -> { minY = maxY = tile.plane(); minX = tile.u0(); maxX = tile.u1(); minZ = tile.v0(); maxZ = tile.v1(); }
            default -> { minZ = maxZ = tile.plane(); minX = tile.u0(); maxX = tile.u1(); minY = tile.v0(); maxY = tile.v1(); }
        }
        double dx = Math.max(Math.max(minX - cam.x, cam.x - maxX), 0.0);
        double dy = Math.max(Math.max(minY - cam.y, cam.y - maxY), 0.0);
        double dz = Math.max(Math.max(minZ - cam.z, cam.z - maxZ), 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double sectionDistance(Vec3 cam, int sx, int sy, int sz)
    {
        double x0 = sx * 16.0, y0 = sy * 16.0, z0 = sz * 16.0;
        double dx = Math.max(Math.max(x0 - cam.x, cam.x - (x0 + 16.0)), 0.0);
        double dy = Math.max(Math.max(y0 - cam.y, cam.y - (y0 + 16.0)), 0.0);
        double dz = Math.max(Math.max(z0 - cam.z, cam.z - (z0 + 16.0)), 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static long key(int sx, int sy, int sz)
    {
        return ((long) sx & 0x3FFFFFL) << 42 | ((long) sy & 0xFFFFFL) << 22 | ((long) sz & 0x3FFFFFL);
    }

    private static int sectionX(long key) { return (int) (key >> 42); }
    private static int sectionY(long key) { return (int) (key << 22 >> 44); }
    private static int sectionZ(long key) { return (int) (key << 42 >> 42); }

    private static int floor(double value)
    {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
