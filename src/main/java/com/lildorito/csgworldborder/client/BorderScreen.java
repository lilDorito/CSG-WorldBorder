package com.lildorito.csgworldborder.client;

import com.lildorito.csgworldborder.border.BorderService;
import com.lildorito.csgworldborder.border.BorderVolume;
import com.lildorito.csgworldborder.csg.Mode;
import com.lildorito.csgworldborder.csg.Outline;
import com.lildorito.csgworldborder.csg.Primitive;
import com.lildorito.csgworldborder.csg.Protection;
import com.lildorito.csgworldborder.csg.Shape;
import com.lildorito.csgworldborder.csg.ShapeState;
import com.lildorito.csgworldborder.csg.Transform;
import com.lildorito.csgworldborder.network.CsgNetwork;
import com.lildorito.csgworldborder.network.EditBorderPacket;
import com.lildorito.csgworldborder.network.EditResultPacket;
import com.lildorito.csgworldborder.network.SyncBorderPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BorderScreen extends Screen
{
    private static final int MARGIN = 8;
    private static final int ROW = 20;
    private static final int FIELD_HEIGHT = 18;
    private static final int LIST_ROW = 13;
    private static final int RIGHT_WIDTH = 204;
    private static final int LABEL_WIDTH = 48;
    private static final int PANEL_COLOR = 0xC8101018;
    private static final int PANEL_EDGE = 0xFF3A3A4A;
    private static final int SELECTED_COLOR = 0x80FFD23F;
    private static final int HOVER_COLOR = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xA0A0A0;
    private static final int ERROR = 0xFF5555;
    private static final int OK = 0x55FF55;
    private static final int PERMISSION = 2;

    private static final class Draft
    {
        String name = "";
        Primitive primitive = Primitive.SPHERE;
        Mode mode = Mode.ADD;
        final String[] pos = {"0", "0", "0"};
        final String[] size = {"8", "8", "8"};
        final String[] rot = {"0", "0", "0"};
        final String[] scale = {"1", "1", "1"};
        String seconds = "0";
        boolean dirty;
    }

    private final Draft draft = new Draft();
    @Nullable
    private String selected;
    private int scroll;
    private String status = "";
    private int statusColor = MUTED;
    private boolean readOnly;
    private boolean formulaDirty;
    private boolean loading;
    private long deleteArmedUntil;
    private String formulaText = "";

    private int leftX, leftY, leftW, listTop, listBottom;
    private int rightX, rightY;
    private int bottomY;

    private CycleButton<Boolean> classicButton;
    private CycleButton<Outline> outlineButton;
    private EditBox nameBox;
    private CycleButton<Primitive> typeButton;
    private CycleButton<Mode> modeButton;
    private CycleButton<Protection> protectButton;
    private final EditBox[] posBoxes = new EditBox[3];
    private final EditBox[] sizeBoxes = new EditBox[3];
    private final EditBox[] rotBoxes = new EditBox[3];
    private final EditBox[] scaleBoxes = new EditBox[3];
    private EditBox secondsBox;
    private EditBox formulaBox;
    private Button applyButton;
    private Button revertButton;
    private Button deleteButton;
    private Button upButton;
    private Button downButton;
    private Button newButton;
    private Button hereButton;
    private Button formulaSetButton;
    private Button formulaAutoButton;
    private final List<EditBox> numberBoxes = new ArrayList<>();

    public BorderScreen()
    {
        super(Component.literal("World Border Editor"));
    }

    @Override
    protected void init()
    {
        Minecraft minecraft = Minecraft.getInstance();
        readOnly = minecraft.player == null || !minecraft.player.hasPermissions(PERMISSION);
        numberBoxes.clear();

        bottomY = height - MARGIN - 12 - FIELD_HEIGHT - 4;
        leftX = MARGIN;
        leftY = 22;
        leftW = Math.max(120, Math.min(160, width - RIGHT_WIDTH - 3 * MARGIN - 60));
        rightX = width - RIGHT_WIDTH - MARGIN;
        rightY = 22;

        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> Minecraft.getInstance().setScreen(new SettingsScreen(this)))
                .bounds(width - MARGIN - 64, 2, 64, 16).build());

        classicButton = addRenderableWidget(CycleButton.onOffBuilder(classic())
                .create(leftX, leftY, leftW, FIELD_HEIGHT, Component.literal("Classic border"),
                        (button, value) -> send(EditBorderPacket.classic(value))));
        classicButton.setTooltip(Tooltip.create(Component.literal(
                "On: the classic border still limits your shapes.\nOff: only your shapes count.\nWith no shapes the classic border is always used.")));

        outlineButton = addRenderableWidget(CycleButton.<Outline>builder(value -> Component.literal(value.title()))
                .withValues(Outline.values())
                .withInitialValue(outline())
                .create(leftX, leftY + FIELD_HEIGHT + 2, leftW, FIELD_HEIGHT, Component.literal("Outline"),
                        (button, value) -> send(EditBorderPacket.outline(value.id()))));
        outlineButton.setTooltip(Tooltip.create(Component.literal(
                "Shape of the classic border: a column from the bottom of the world to the top.\nIt fits inside the vanilla square, and /worldborder set, center and add still resize and move it.")));

        listTop = leftY + 2 * FIELD_HEIGHT + 6;
        listBottom = bottomY - 6 - FIELD_HEIGHT;
        int buttonY = listBottom + 3;
        int bw = (leftW - 6) / 4;
        newButton = addRenderableWidget(Button.builder(Component.literal("New"), b -> send(EditBorderPacket.simple(EditBorderPacket.Action.NEW, "")))
                .bounds(leftX, buttonY, bw, FIELD_HEIGHT).tooltip(Tooltip.create(Component.literal("Add a sphere where you stand"))).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSelected())
                .bounds(leftX + bw + 2, buttonY, bw, FIELD_HEIGHT).build());
        upButton = addRenderableWidget(Button.builder(Component.literal("▲"), b -> moveSelected(EditBorderPacket.Action.UP))
                .bounds(leftX + 2 * (bw + 2), buttonY, bw, FIELD_HEIGHT).tooltip(Tooltip.create(Component.literal("Move layer up"))).build());
        downButton = addRenderableWidget(Button.builder(Component.literal("▼"), b -> moveSelected(EditBorderPacket.Action.DOWN))
                .bounds(leftX + 3 * (bw + 2), buttonY, leftW - 3 * (bw + 2), FIELD_HEIGHT).tooltip(Tooltip.create(Component.literal("Move layer down"))).build());

        int fx = rightX + LABEL_WIDTH;
        int fw = RIGHT_WIDTH - LABEL_WIDTH - 4;
        int third = (fw - 4) / 3;
        int y = rightY;

        nameBox = addRenderableWidget(new EditBox(font, fx, y, fw, FIELD_HEIGHT, Component.literal("Name")));
        nameBox.setMaxLength(32);
        nameBox.setFilter(s -> s.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_'));
        nameBox.setValue(draft.name);
        nameBox.setResponder(s -> { draft.name = s; touch(); });
        y += ROW;

        typeButton = addRenderableWidget(CycleButton.<Primitive>builder(p -> Component.literal(title(p)))
                .withValues(Primitive.values()).withInitialValue(draft.primitive).displayOnlyValue()
                .create(fx, y, fw, FIELD_HEIGHT, Component.literal("Shape"), (b, value) -> changeType(value)));
        y += ROW;

        modeButton = addRenderableWidget(CycleButton.<Mode>builder(m -> Component.literal(m.title()))
                .withValues(Mode.values()).withInitialValue(draft.mode).displayOnlyValue()
                .withTooltip(m -> Tooltip.create(Component.literal(modeHelp(m))))
                .create(fx, y, fw, FIELD_HEIGHT, Component.literal("Mode"), (b, value) -> { draft.mode = value; touch(); }));
        y += ROW;

        int posThird = (fw - 30 - 6) / 3;
        for (int i = 0; i < 3; i++)
        {
            posBoxes[i] = number(fx + i * (posThird + 2), y, posThird, draft.pos, i, new String[] {"x", "y", "z"}[i]);
        }
        hereButton = addRenderableWidget(Button.builder(Component.literal("Here"), b -> moveHere())
                .bounds(fx + 3 * (posThird + 2), y, fw - 3 * (posThird + 2), FIELD_HEIGHT)
                .tooltip(Tooltip.create(Component.literal("Center on the block you stand on"))).build());
        y += ROW;

        for (int i = 0; i < 3; i++) sizeBoxes[i] = number(fx + i * (third + 2), y, third, draft.size, i, "");
        y += ROW;
        for (int i = 0; i < 3; i++) rotBoxes[i] = number(fx + i * (third + 2), y, third, draft.rot, i, new String[] {"yaw", "pitch", "roll"}[i]);
        y += ROW;
        for (int i = 0; i < 3; i++) scaleBoxes[i] = number(fx + i * (third + 2), y, third, draft.scale, i, new String[] {"x", "y", "z"}[i]);
        y += ROW;

        secondsBox = addRenderableWidget(new EditBox(font, fx, y, third, FIELD_HEIGHT, Component.literal("Seconds")));
        secondsBox.setFilter(s -> s.chars().allMatch(Character::isDigit) && s.length() <= 7);
        secondsBox.setValue(draft.seconds);
        secondsBox.setResponder(s -> draft.seconds = s);
        secondsBox.setTooltip(Tooltip.create(Component.literal("Animate the change over this many seconds. 0 applies instantly")));
        protectButton = addRenderableWidget(CycleButton.<Protection>builder(p -> Component.literal(p.title()))
                .withValues(Protection.values()).withInitialValue(protectionOf(selected))
                .withTooltip(p -> Tooltip.create(Component.literal(protectHelp(p))))
                .create(fx + third + 26, y, fw - third - 26, FIELD_HEIGHT, Component.literal("Protect"),
                        (b, value) -> { if (!loading && selected != null) send(EditBorderPacket.protect(selected, value.id())); }));
        y += ROW + 2;

        int half = (RIGHT_WIDTH - 6) / 2;
        applyButton = addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(rightX + 2, y, half, ROW).build());
        revertButton = addRenderableWidget(Button.builder(Component.literal("Revert"), b -> { loadSelected(); touchClean(); })
                .bounds(rightX + 4 + half, y, half, ROW).build());

        int formulaX = MARGIN + 54;
        int buttonsW = 80;
        formulaBox = addRenderableWidget(new EditBox(font, formulaX, bottomY, width - formulaX - MARGIN - buttonsW - 4, FIELD_HEIGHT,
                Component.literal("Formula")));
        formulaBox.setMaxLength(4096);
        formulaBox.setValue(formulaText);
        formulaBox.setResponder(s -> { formulaText = s; formulaDirty = true; });
        formulaBox.setTooltip(Tooltip.create(Component.literal(
                "Advanced. Combine shapes by name: | union, & intersect, - subtract, ^ xor, ( ). "
                        + "border is the classic square, all is everything.\nSet makes it custom, Auto builds it from the layers again.")));
        formulaSetButton = addRenderableWidget(Button.builder(Component.literal("Set"), b -> send(EditBorderPacket.formula(formulaText)))
                .bounds(width - MARGIN - buttonsW, bottomY, 38, FIELD_HEIGHT).build());
        formulaAutoButton = addRenderableWidget(Button.builder(Component.literal("Auto"), b -> send(EditBorderPacket.formula("auto")))
                .bounds(width - MARGIN - buttonsW + 40, bottomY, buttonsW - 40, FIELD_HEIGHT).build());

        if (selected == null && !draft.dirty)
        {
            List<Shape> shapes = shapes();
            if (!shapes.isEmpty()) select(shapes.get(0).name());
        }
        refreshFormula();
        updateWidgets();
    }

    private EditBox number(int x, int y, int w, String[] target, int index, String hint)
    {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, w, FIELD_HEIGHT, Component.literal(hint)));
        box.setMaxLength(16);
        box.setFilter(s -> s.chars().allMatch(c -> Character.isDigit(c) || c == '-' || c == '.'));
        box.setValue(target[index]);
        if (!hint.isEmpty()) box.setHint(Component.literal(hint).withStyle(s -> s.withColor(0x606060)));
        box.setResponder(s -> { target[index] = s; touch(); });
        numberBoxes.add(box);
        return box;
    }

    @Override
    public void tick()
    {
        for (EditBox box : numberBoxes) box.tick();
        nameBox.tick();
        secondsBox.tick();
        formulaBox.tick();
        BorderRenderer.setPreview(previewState());
        if (deleteArmedUntil != 0L && Util.getMillis() > deleteArmedUntil)
        {
            deleteArmedUntil = 0L;
            deleteButton.setMessage(Component.literal("Delete"));
        }
    }

    @Override
    public void removed()
    {
        BorderRenderer.setPreview(null);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        boolean typing = getFocused() instanceof EditBox;
        if (!typing && ClientBorders.OPEN_GUI.matches(key, scan))
        {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    public void onSync()
    {
        if (selected != null && find(selected) == null) selected = null;
        if (!draft.dirty)
        {
            if (selected != null) loadSelected();
            else if (!shapes().isEmpty()) select(shapes().get(0).name());
            draft.dirty = false;
        }
        if (!formulaDirty) refreshFormula();
        classicButton.setValue(classic());
        outlineButton.setValue(outline());
        updateWidgets();
    }

    public void onResult(EditResultPacket packet)
    {
        status = packet.message();
        statusColor = packet.ok() ? OK : ERROR;
        if (packet.ok())
        {
            if (!packet.selected().isEmpty() && find(packet.selected()) != null) select(packet.selected());
            else if (selected != null && find(selected) == null) selected = null;
            formulaDirty = false;
            refreshFormula();
        }
        updateWidgets();
    }

    private void select(String name)
    {
        selected = name;
        loadSelected();
        touchClean();
    }

    private void loadSelected()
    {
        Shape shape = selected == null ? null : find(selected);
        if (shape == null) return;
        ShapeState s = shape.to();
        Transform t = s.transform();
        draft.name = shape.name();
        draft.primitive = s.primitive();
        draft.mode = modeOf(shape.name());
        fill(draft.pos, fmt(t.x()), fmt(t.y()), fmt(t.z()));
        fill(draft.size, fmt(s.a()), fmt(s.b() == 0.0 ? s.a() : s.b()), fmt(s.c() == 0.0 ? s.a() : s.c()));
        fill(draft.rot, fmt(t.yaw()), fmt(t.pitch()), fmt(t.roll()));
        fill(draft.scale, fmt(t.scaleX()), fmt(t.scaleY()), fmt(t.scaleZ()));
        pushDraft();
    }

    private static void fill(String[] target, String a, String b, String c)
    {
        target[0] = a;
        target[1] = b;
        target[2] = c;
    }

    private void pushDraft()
    {
        loading = true;
        nameBox.setValue(draft.name);
        typeButton.setValue(draft.primitive);
        modeButton.setValue(draft.mode);
        protectButton.setValue(protectionOf(selected));
        for (int i = 0; i < 3; i++)
        {
            posBoxes[i].setValue(draft.pos[i]);
            sizeBoxes[i].setValue(draft.size[i]);
            rotBoxes[i].setValue(draft.rot[i]);
            scaleBoxes[i].setValue(draft.scale[i]);
        }
        loading = false;
    }

    private void touch()
    {
        if (loading) return;
        draft.dirty = true;
        updateWidgets();
    }

    private void touchClean()
    {
        draft.dirty = false;
        updateWidgets();
    }

    private void changeType(Primitive primitive)
    {
        draft.primitive = primitive;
        touch();
    }

    private void moveHere()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        BlockPos pos = minecraft.player.blockPosition();
        String[] values = {fmt(pos.getX() + 0.5), fmt(pos.getY() + 0.5), fmt(pos.getZ() + 0.5)};
        for (int i = 0; i < 3; i++) posBoxes[i].setValue(values[i]);
        touch();
    }

    private void apply()
    {
        ShapeState state = draftState();
        if (state == null) return;
        int seconds = parseInt(draft.seconds);
        send(EditBorderPacket.put(selected == null ? "" : selected, draft.name, state, draft.mode, seconds));
        draft.dirty = false;
    }

    private void deleteSelected()
    {
        if (selected == null) return;
        long now = Util.getMillis();
        if (now > deleteArmedUntil)
        {
            deleteArmedUntil = now + 3000L;
            deleteButton.setMessage(Component.literal("Sure?").withStyle(s -> s.withColor(ERROR)));
            return;
        }
        deleteArmedUntil = 0L;
        deleteButton.setMessage(Component.literal("Delete"));
        send(EditBorderPacket.simple(EditBorderPacket.Action.REMOVE, selected));
    }

    private void moveSelected(EditBorderPacket.Action action)
    {
        if (selected != null) send(EditBorderPacket.simple(action, selected));
    }

    private void send(EditBorderPacket packet)
    {
        if (readOnly) return;
        CsgNetwork.sendToServer(packet);
    }

    private void updateWidgets()
    {
        if (applyButton == null) return;
        boolean hasSelection = selected != null && find(selected) != null;
        boolean valid = draftState() != null && !draft.name.isEmpty();
        boolean editable = !readOnly && (hasSelection || draft.dirty);

        int count = draft.primitive.parameters();
        for (int i = 0; i < 3; i++)
        {
            sizeBoxes[i].visible = i < count;
            sizeBoxes[i].setHint(Component.literal(sizeHint(draft.primitive, i)).withStyle(s -> s.withColor(0x606060)));
        }
        for (EditBox box : numberBoxes) box.setTextColor(isNumber(box.getValue()) ? 0xE0E0E0 : ERROR);
        nameBox.setTextColor(draft.name.isEmpty() || draft.name.equals("border") || draft.name.equals("all") ? ERROR : 0xE0E0E0);

        for (EditBox box : numberBoxes) box.setEditable(editable);
        nameBox.setEditable(editable);
        secondsBox.setEditable(editable);
        typeButton.active = editable;
        modeButton.active = editable;
        protectButton.active = !readOnly && hasSelection && modeOf(selected) == Mode.ADD;
        hereButton.active = editable;
        applyButton.active = editable && valid && draft.dirty;
        revertButton.active = editable && draft.dirty && hasSelection;
        deleteButton.active = !readOnly && hasSelection;
        upButton.active = !readOnly && hasSelection && indexOf(selected) > 0;
        downButton.active = !readOnly && hasSelection && indexOf(selected) < shapes().size() - 1;
        newButton.active = !readOnly;
        classicButton.active = !readOnly;
        outlineButton.active = !readOnly;
        formulaBox.setEditable(!readOnly);
        formulaSetButton.active = !readOnly && !shapes().isEmpty();
        formulaAutoButton.active = !readOnly && customFormula();
        if (readOnly && status.isEmpty())
        {
            status = "Read only: you need operator permission to edit the border";
            statusColor = ERROR;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        panel(g, leftX - 4, leftY - 4, leftX + leftW + 4, listBottom + FIELD_HEIGHT + 7);
        boolean idle = selected == null && !draft.dirty;
        panel(g, rightX - 4, rightY - 4, rightX + RIGHT_WIDTH + 4, applyButton.getY() + ROW + (idle ? 16 : 4));
        panel(g, MARGIN - 4, bottomY - 4, width - MARGIN + 4, height - 3);

        g.drawCenteredString(font, title, width / 2, 7, TEXT);
        renderList(g, mouseX, mouseY);
        renderLabels(g);

        String formulaLabel = customFormula() ? "Formula*" : "Formula";
        g.drawString(font, formulaLabel, MARGIN, bottomY + 5, customFormula() ? 0xFFD23F : MUTED);
        g.drawString(font, font.plainSubstrByWidth(status, width - 2 * MARGIN), MARGIN, height - MARGIN - 6, statusColor);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderLabels(GuiGraphics g)
    {
        String[] labels = {"Name", "Shape", "Mode", "Position", sizeLabel(draft.primitive), "Rotation", "Scale", "Animate"};
        for (int i = 0; i < labels.length; i++)
        {
            g.drawString(font, labels[i], rightX + 2, rightY + i * ROW + 5, MUTED);
        }
        g.drawString(font, "sec", secondsBox.getX() + secondsBox.getWidth() + 4, secondsBox.getY() + 5, MUTED);
        if (selected == null && !draft.dirty)
        {
            g.drawCenteredString(font, "Select a shape or press New", rightX + RIGHT_WIDTH / 2, applyButton.getY() + ROW + 3, MUTED);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY)
    {
        List<Shape> shapes = shapes();
        int top = listTop;
        int bottom = listBottom - 2;
        g.fill(leftX, top, leftX + leftW, bottom, 0x60000000);
        if (shapes.isEmpty())
        {
            int y = top + 6;
            for (var line : font.split(Component.literal("No shapes yet. The classic " + outline().id() + " border is active. Press New to add one."), leftW - 8))
            {
                g.drawString(font, line, leftX + 4, y, MUTED);
                y += 10;
            }
            return;
        }
        int visible = Math.max(1, (bottom - top) / LIST_ROW);
        scroll = Math.max(0, Math.min(scroll, shapes.size() - visible));
        g.enableScissor(leftX, top, leftX + leftW, bottom);
        for (int i = scroll; i < shapes.size() && i < scroll + visible + 1; i++)
        {
            Shape shape = shapes.get(i);
            int y = top + (i - scroll) * LIST_ROW;
            boolean hover = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= y && mouseY < y + LIST_ROW && mouseY < bottom;
            if (shape.name().equals(selected)) g.fill(leftX, y, leftX + leftW, y + LIST_ROW, SELECTED_COLOR);
            else if (hover) g.fill(leftX, y, leftX + leftW, y + LIST_ROW, HOVER_COLOR);
            Mode mode = modeOf(shape.name());
            g.drawString(font, mode.symbol(), leftX + 4, y + 3, modeColor(mode));
            String type = title(shape.to().primitive());
            int typeW = font.width(type);
            g.drawString(font, font.plainSubstrByWidth(shape.name(), leftW - typeW - 22), leftX + 14, y + 3, TEXT);
            g.drawString(font, type, leftX + leftW - typeW - 4, y + 3, MUTED);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseX >= leftX && mouseX < leftX + leftW && mouseY >= listTop && mouseY < listBottom - 2)
        {
            int index = scroll + (int) ((mouseY - listTop) / LIST_ROW);
            List<Shape> shapes = shapes();
            if (index >= 0 && index < shapes.size())
            {
                select(shapes.get(index).name());
                setFocused(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (mouseX >= leftX && mouseX < leftX + leftW && mouseY >= listTop && mouseY < listBottom)
        {
            scroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1)
    {
        g.fill(x0, y0, x1, y1, PANEL_COLOR);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, PANEL_EDGE);
    }

    @Nullable
    private ShapeState previewState()
    {
        if (draft.dirty)
        {
            ShapeState state = draftState();
            if (state != null) return state;
        }
        Shape shape = selected == null ? null : find(selected);
        return shape == null ? null : shape.to();
    }

    @Nullable
    private ShapeState draftState()
    {
        try
        {
            double[] p = parse(draft.pos);
            double[] s = parse(draft.size);
            double[] r = parse(draft.rot);
            double[] k = parse(draft.scale);
            Primitive primitive = draft.primitive;
            double a = s[0];
            double b = primitive.parameters() > 1 ? s[1] : 0.0;
            double c = primitive.parameters() > 2 ? s[2] : 0.0;
            ShapeState state = new ShapeState(primitive, a, b, c, new Transform(p[0], p[1], p[2], r[0], r[1], r[2], k[0], k[1], k[2]));
            return BorderService.validate(state) == null ? state : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static double[] parse(String[] values)
    {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) out[i] = Double.parseDouble(values[i]);
        return out;
    }

    private static boolean isNumber(String value)
    {
        try
        {
            return Double.isFinite(Double.parseDouble(value));
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    private static int parseInt(String value)
    {
        try
        {
            return Math.min(BorderService.MAX_SECONDS, Integer.parseInt(value));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private void refreshFormula()
    {
        SyncBorderPacket state = ClientBorders.state();
        formulaText = state == null ? "border" : state.expression();
        if (formulaBox != null) formulaBox.setValue(formulaText);
        formulaDirty = false;
    }

    private List<Shape> shapes()
    {
        SyncBorderPacket state = ClientBorders.state();
        return state == null ? List.of() : state.shapes();
    }

    private boolean classic()
    {
        SyncBorderPacket state = ClientBorders.state();
        return state == null || state.classic();
    }

    private Outline outline()
    {
        SyncBorderPacket state = ClientBorders.state();
        return state == null ? Outline.SQUARE : state.outline();
    }

    private boolean customFormula()
    {
        SyncBorderPacket state = ClientBorders.state();
        return state != null && !state.formula().isEmpty();
    }

    @Nullable
    private Shape find(String name)
    {
        for (Shape shape : shapes())
        {
            if (shape.name().equals(name)) return shape;
        }
        return null;
    }

    private int indexOf(String name)
    {
        List<Shape> shapes = shapes();
        for (int i = 0; i < shapes.size(); i++)
        {
            if (shapes.get(i).name().equals(name)) return i;
        }
        return -1;
    }

    private Protection protectionOf(@Nullable String name)
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || name == null || indexOf(name) < 0) return Protection.NONE;
        return BorderVolume.of(level.getWorldBorder()).areas().protectionOf(name);
    }

    private static String protectHelp(Protection protection)
    {
        return switch (protection)
        {
            case NONE -> "Not protected. Survival players can build and break here.";
            case EDGE -> "Edge: blocks along the border of this area can't be broken or built on, except in creative. Depth and sides follow the protect settings.";
            case INSIDE -> "Inside: like adventure mode in the whole area. No breaking, placing, buckets or explosions; doors, buttons and chests still work.";
        } + "\nApplies at once to every shape connected to this one: touching or overlapping shapes are one area.";
    }

    private Mode modeOf(String name)
    {
        SyncBorderPacket state = ClientBorders.state();
        if (state == null) return Mode.ADD;
        int index = indexOf(name);
        return index < 0 ? Mode.ADD : state.modes().get(index);
    }

    private static String title(Primitive primitive)
    {
        return switch (primitive)
        {
            case BOX -> "Box";
            case SPHERE -> "Sphere";
            case CYLINDER -> "Cylinder";
            case CONE -> "Cone";
            case TORUS -> "Torus";
        };
    }

    private static String sizeLabel(Primitive primitive)
    {
        return "Size";
    }

    private static String sizeHint(Primitive primitive, int index)
    {
        return switch (primitive)
        {
            case BOX -> new String[] {"width", "height", "depth"}[index];
            case SPHERE -> "radius";
            case CYLINDER, CONE -> new String[] {"radius", "height", ""}[index];
            case TORUS -> new String[] {"radius", "thickness", ""}[index];
        };
    }

    private static String modeHelp(Mode mode)
    {
        return switch (mode)
        {
            case ADD -> "Add: the space inside this shape becomes playable";
            case SUBTRACT -> "Subtract: cuts this shape out of the layers above it";
            case INTERSECT -> "Intersect: keeps only what overlaps with this shape";
        };
    }

    private static int modeColor(Mode mode)
    {
        return switch (mode)
        {
            case ADD -> 0x55FF55;
            case SUBTRACT -> 0xFF5555;
            case INTERSECT -> 0x55FFFF;
        };
    }

    private static String fmt(double value)
    {
        return BorderService.fmt(value);
    }
}
