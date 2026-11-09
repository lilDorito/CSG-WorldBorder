package com.lildorito.csgworldborder.client;

import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.network.CsgNetwork;
import com.lildorito.csgworldborder.network.EditBorderPacket;
import com.lildorito.csgworldborder.network.EditResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

public class SettingsScreen extends Screen
{
    private static final int ROW = 22;
    private static final int WIDTH = 240;
    private static final int PERMISSION = 2;
    private static final int PANEL_COLOR = 0xC8101018;
    private static final int PANEL_EDGE = 0xFF3A3A4A;
    private static final int OK = 0x55FF55;
    private static final int ERROR = 0xFF5555;
    private static final int MUTED = 0xA0A0A0;

    @Nullable
    private final Screen parent;
    private boolean readOnly;
    private String status = "";
    private int statusColor = MUTED;
    private int top;

    public SettingsScreen(@Nullable Screen parent)
    {
        super(Component.literal("World Border Settings"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        Minecraft minecraft = Minecraft.getInstance();
        readOnly = minecraft.player == null || !minecraft.player.hasPermissions(PERMISSION);
        BorderSettings.Values values = BorderSettings.client();
        int x = (width - WIDTH) / 2;
        top = Math.max(24, (height - BorderSettings.Key.values().length * ROW - 40) / 2);
        int y = top;
        for (BorderSettings.Key key : BorderSettings.Key.values())
        {
            if (key.kind() == BorderSettings.Kind.BOOLEAN)
            {
                CycleButton<Boolean> button = addRenderableWidget(CycleButton.onOffBuilder((Boolean) values.get(key))
                        .create(x, y, WIDTH, 20, Component.literal(title(key)),
                                (b, value) -> send(key, value.toString())));
                button.setTooltip(Tooltip.create(Component.literal(key.description())));
                button.active = !readOnly && enabled(key, values);
            }
            else
            {
                Slider slider = addRenderableWidget(new Slider(x, y, WIDTH, key, (Integer) values.get(key)));
                slider.setTooltip(Tooltip.create(Component.literal(key.description())));
                slider.active = !readOnly && enabled(key, values);
            }
            y += ROW;
        }
        int half = (WIDTH - 4) / 2;
        Button remember = addRenderableWidget(Button.builder(Component.literal("Remember map blocks"),
                        b -> { if (!readOnly) CsgNetwork.sendToServer(EditBorderPacket.remember()); })
                .bounds(x, y + 6, half, 20)
                .tooltip(Tooltip.create(Component.literal("Every block in this dimension becomes a map block: blocks placed by players so far get protected like the rest of the map. Use it after building the map in survival.")))
                .build());
        remember.active = !readOnly;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(x + half + 4, y + 6, half, 20).build());
        if (readOnly && status.isEmpty())
        {
            status = "Read only: you need operator permission to change settings";
            statusColor = ERROR;
        }
    }

    private static boolean enabled(BorderSettings.Key key, BorderSettings.Values values)
    {
        return switch (key)
        {
            case BORDER_FADE_DISTANCE -> values.showBorder();
            default -> true;
        };
    }

    private void send(BorderSettings.Key key, String value)
    {
        if (readOnly) return;
        CsgNetwork.sendToServer(EditBorderPacket.setting(key, value));
    }

    public void onSync()
    {
        rebuildWidgets();
    }

    public void onResult(EditResultPacket packet)
    {
        status = packet.message();
        statusColor = packet.ok() ? OK : ERROR;
    }

    @Override
    public void onClose()
    {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        int x = (width - WIDTH) / 2;
        int bottom = top + BorderSettings.Key.values().length * ROW + 30;
        g.fill(x - 8, top - 22, x + WIDTH + 8, bottom + 4, PANEL_COLOR);
        g.renderOutline(x - 8, top - 22, WIDTH + 16, bottom - top + 26, PANEL_EDGE);
        g.drawCenteredString(font, title, width / 2, top - 15, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, font.plainSubstrByWidth(status, width - 16), width / 2, bottom + 10, statusColor);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    private static String title(BorderSettings.Key key)
    {
        return switch (key)
        {
            case PROTECT_EDGE -> "Protect whole border edge";
            case PROTECT_THICKNESS -> "Protected layers";
            case PROTECT_WALLS -> "Protect walls";
            case PROTECT_FLOOR -> "Protect floor";
            case PROTECT_CEILING -> "Protect ceiling";
            case PROTECT_MAP_BLOCKS_ONLY -> "Protect map blocks only";
            case INSIDE_WARNING -> "Red warning inside";
            case BLOCK_PROJECTILES -> "Border stops projectiles";
            case SHOW_BORDER -> "Show border";
            case BORDER_FADE_DISTANCE -> "Border fade distance";
        };
    }

    private final class Slider extends AbstractSliderButton
    {
        private final BorderSettings.Key key;
        private int sent;

        Slider(int x, int y, int width, BorderSettings.Key key, int current)
        {
            super(x, y, width, 20, Component.empty(), (double) (current - key.min()) / (key.max() - key.min()));
            this.key = key;
            this.sent = current;
            updateMessage();
        }

        private int current()
        {
            return key.min() + (int) Math.round(value * (key.max() - key.min()));
        }

        @Override
        protected void updateMessage()
        {
            String suffix = key == BorderSettings.Key.BORDER_FADE_DISTANCE ? "%" : "";
            setMessage(Component.literal(title(key) + ": " + current() + suffix));
        }

        @Override
        protected void applyValue() {}

        @Override
        public void onRelease(double mouseX, double mouseY)
        {
            super.onRelease(mouseX, mouseY);
            commit();
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers)
        {
            boolean handled = super.keyPressed(key, scan, modifiers);
            if (handled) commit();
            return handled;
        }

        private void commit()
        {
            int value = current();
            if (value == sent) return;
            sent = value;
            send(key, Integer.toString(value));
        }
    }
}
