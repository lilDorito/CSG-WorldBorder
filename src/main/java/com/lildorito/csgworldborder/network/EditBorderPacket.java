package com.lildorito.csgworldborder.network;

import com.lildorito.csgworldborder.border.BorderService;
import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.border.CsgBorderData;
import com.lildorito.csgworldborder.border.ShapeCodec;
import com.lildorito.csgworldborder.csg.Mode;
import com.lildorito.csgworldborder.csg.Primitive;
import com.lildorito.csgworldborder.csg.ShapeState;
import com.lildorito.csgworldborder.csg.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public record EditBorderPacket(Action action, String name, String oldName, @Nullable ShapeState state, Mode mode,
                               int seconds, boolean flag, String text)
{
    private static final int PERMISSION = 2;
    private static final ShapeState EMPTY = new ShapeState(Primitive.SPHERE, 1.0, 0.0, 0.0, Transform.at(0.0, 0.0, 0.0));

    public enum Action
    {
        NEW,
        PUT,
        REMOVE,
        UP,
        DOWN,
        CLASSIC,
        FORMULA,
        SETTING,
        OUTLINE,
        PROTECT,
        REMEMBER
    }

    public static EditBorderPacket put(String oldName, String name, ShapeState state, Mode mode, int seconds)
    {
        return new EditBorderPacket(Action.PUT, name, oldName, state, mode, seconds, false, "");
    }

    public static EditBorderPacket simple(Action action, String name)
    {
        return new EditBorderPacket(action, name, "", null, Mode.ADD, 0, false, "");
    }

    public static EditBorderPacket classic(boolean on)
    {
        return new EditBorderPacket(Action.CLASSIC, "", "", null, Mode.ADD, 0, on, "");
    }

    public static EditBorderPacket setting(BorderSettings.Key key, String value)
    {
        return new EditBorderPacket(Action.SETTING, key.id(), "", null, Mode.ADD, 0, false, value);
    }

    public static EditBorderPacket protect(String name, String id)
    {
        return new EditBorderPacket(Action.PROTECT, name, "", null, Mode.ADD, 0, false, id);
    }

    public static EditBorderPacket remember()
    {
        return new EditBorderPacket(Action.REMEMBER, "", "", null, Mode.ADD, 0, false, "");
    }

    public static EditBorderPacket outline(String id)
    {
        return new EditBorderPacket(Action.OUTLINE, "", "", null, Mode.ADD, 0, false, id);
    }

    public static EditBorderPacket formula(String text)
    {
        return new EditBorderPacket(Action.FORMULA, "", "", null, Mode.ADD, 0, false, text);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeEnum(action);
        buf.writeUtf(name);
        buf.writeUtf(oldName);
        ShapeCodec.writeState(buf, state == null ? EMPTY : state);
        buf.writeEnum(mode);
        buf.writeVarInt(seconds);
        buf.writeBoolean(flag);
        buf.writeUtf(text);
    }

    public static EditBorderPacket decode(FriendlyByteBuf buf)
    {
        return new EditBorderPacket(buf.readEnum(Action.class), buf.readUtf(64), buf.readUtf(64), ShapeCodec.readState(buf),
                buf.readEnum(Mode.class), buf.readVarInt(), buf.readBoolean(), buf.readUtf(4096));
    }

    public void handle(Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null) return;
        if (!player.hasPermissions(PERMISSION))
        {
            CsgNetwork.send(player, new EditResultPacket(false, "You need operator permission to edit the border", name));
            return;
        }
        ServerLevel level = player.serverLevel();
        String selected = name;
        BorderService.Result result;
        switch (action)
        {
            case NEW ->
            {
                selected = freeName(level);
                BlockPos pos = player.blockPosition();
                ShapeState shape = new ShapeState(Primitive.SPHERE, 8.0, 0.0, 0.0,
                        Transform.at(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                result = BorderService.edit(level, null, selected, shape, Mode.ADD, 0);
            }
            case PUT -> result = state == null
                    ? new BorderService.Result(false, "Nothing to apply")
                    : BorderService.edit(level, oldName.isEmpty() ? null : oldName, name, state, mode, seconds);
            case REMOVE -> result = BorderService.remove(level, name);
            case UP -> result = BorderService.order(level, name, -1);
            case DOWN -> result = BorderService.order(level, name, 1);
            case CLASSIC -> result = BorderService.setClassic(level, flag);
            case FORMULA -> result = BorderService.setFormula(level, text);
            case OUTLINE -> result = BorderService.setOutline(level, text);
            case PROTECT -> result = BorderService.protect(level, name, text);
            case REMEMBER -> result = BorderService.remember(level, null);
            case SETTING ->
            {
                BorderSettings.Key key = BorderSettings.Key.byId(name);
                result = key == null ? new BorderService.Result(false, "Unknown setting " + name) : BorderSettings.set(key, text);
            }
            default -> result = new BorderService.Result(false, "Unknown action");
        }
        if (!result.ok() && action == Action.PUT) selected = oldName.isEmpty() ? name : oldName;
        CsgNetwork.send(player, new EditResultPacket(result.ok(), result.message(), selected));
    }

    private static String freeName(ServerLevel level)
    {
        CsgBorderData data = CsgBorderData.get(level);
        int i = 1;
        while (data.shape("shape" + i) != null) i++;
        return "shape" + i;
    }
}
