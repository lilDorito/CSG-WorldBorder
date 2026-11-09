package com.lildorito.csgworldborder.command;

import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.border.BorderService;
import com.lildorito.csgworldborder.border.BorderSettings;
import com.lildorito.csgworldborder.border.CsgBorderData;
import com.lildorito.csgworldborder.border.PlayerBlocks;
import com.lildorito.csgworldborder.csg.Mode;
import com.lildorito.csgworldborder.csg.Outline;
import com.lildorito.csgworldborder.csg.Primitive;
import com.lildorito.csgworldborder.csg.Protection;
import com.lildorito.csgworldborder.csg.Shape;
import com.lildorito.csgworldborder.csg.ShapeState;
import com.lildorito.csgworldborder.csg.Transform;
import com.lildorito.csgworldborder.network.CsgNetwork;
import com.lildorito.csgworldborder.network.OpenGuiPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = CsgWorldBorder.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CsgBorderCommand
{
    private static final double MAX_ANGLE = 36000.0;
    private static final double MAX_OFFSET = 6.0E7;

    private static final String SHAPE_HELP = """
            Shape commands, /worldborder shape ...
              add <name> <type> <sizes> [at <pos>|here] [mode <mode>]
              add <name> box from <pos> to <pos> [mode <mode>]
              remove <name>
              move <name> to <pos>|here | by <dx> <dy> <dz> [over <s>]
              resize <name> to <sizes> | times <factor> [over <s>]
              rotate <name> to|by <yaw> <pitch> <roll> [over <s>]
              scale <name> to <factor> | <x> <y> <z> [over <s>]
              reshape <name> <type> <sizes> [over <s>]
              mode <name> add|subtract|intersect
              protect <name> none|edge|inside
              rename <name> <newName>
              order <name> up|down|top|bottom
              info <name>, list, clear""";

    @FunctionalInterface
    private interface Action
    {
        int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface Timed
    {
        int run(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException;
    }

    private static final SuggestionProvider<CommandSourceStack> SHAPE_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(data(ctx).shapes().keySet(), builder);

    private CsgBorderCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("worldborder")
                .requires(source -> source.hasPermission(2))
                .then(shapeCommand())
                .then(classicCommand())
                .then(protectionCommand())
                .then(formulaCommand())
                .then(settingsCommand())
                .then(Commands.literal("gui").executes(CsgBorderCommand::openGui)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeCommand()
    {
        return Commands.literal("shape")
                .executes(ctx -> say(ctx, SHAPE_HELP))
                .then(addCommand())
                .then(Commands.literal("remove").then(named(ctx -> reply(ctx, BorderService.remove(level(ctx), name(ctx))))))
                .then(Commands.literal("move").then(shapeName()
                        .then(Commands.literal("to")
                                .then(timed(Commands.literal("here"), (ctx, s) -> moveTo(ctx, here(ctx), s)))
                                .then(timed(Commands.argument("pos", Vec3Argument.vec3(false)), (ctx, s) -> moveTo(ctx, position(ctx), s))))
                        .then(Commands.literal("by").then(offset("dx").then(offset("dy").then(timed(offset("dz"), (ctx, s) ->
                                reply(ctx, BorderService.move(level(ctx), name(ctx), number(ctx, "dx"), number(ctx, "dy"), number(ctx, "dz"), true, s)))))))))
                .then(Commands.literal("resize").then(shapeName()
                        .then(Commands.literal("to").then(timed(size("a"), CsgBorderCommand::resizeTo)
                                .then(timed(size("b"), CsgBorderCommand::resizeTo)
                                        .then(timed(size("c"), CsgBorderCommand::resizeTo)))))
                        .then(Commands.literal("times").then(timed(factor("factor"), (ctx, s) ->
                                reply(ctx, BorderService.resizeTimes(level(ctx), name(ctx), number(ctx, "factor"), s)))))))
                .then(Commands.literal("rotate").then(shapeName()
                        .then(Commands.literal("to").then(angles(false)))
                        .then(Commands.literal("by").then(angles(true)))))
                .then(Commands.literal("scale").then(shapeName()
                        .then(Commands.literal("to").then(timed(factor("sx"), CsgBorderCommand::scaleTo)
                                .then(factor("sy").then(timed(factor("sz"), CsgBorderCommand::scaleTo)))))))
                .then(Commands.literal("reshape").then(shapeTypes(shapeName(), false)))
                .then(Commands.literal("mode").then(modes(shapeName(), mode -> ctx ->
                        reply(ctx, BorderService.setMode(level(ctx), name(ctx), mode)))))
                .then(Commands.literal("protect").then(protections(shapeName())))
                .then(Commands.literal("rename").then(shapeName().then(Commands.argument("newName", StringArgumentType.word())
                        .executes(ctx -> reply(ctx, BorderService.rename(level(ctx), name(ctx), StringArgumentType.getString(ctx, "newName")))))))
                .then(Commands.literal("order").then(shapeName()
                        .then(Commands.literal("up").executes(ctx -> reply(ctx, BorderService.order(level(ctx), name(ctx), -1))))
                        .then(Commands.literal("down").executes(ctx -> reply(ctx, BorderService.order(level(ctx), name(ctx), 1))))
                        .then(Commands.literal("top").executes(ctx -> reply(ctx, BorderService.order(level(ctx), name(ctx), -BorderService.MAX_SHAPES))))
                        .then(Commands.literal("bottom").executes(ctx -> reply(ctx, BorderService.order(level(ctx), name(ctx), BorderService.MAX_SHAPES))))))
                .then(Commands.literal("info").then(named(CsgBorderCommand::info)))
                .then(Commands.literal("list").executes(CsgBorderCommand::list))
                .then(Commands.literal("clear").executes(ctx -> reply(ctx, BorderService.clear(level(ctx)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addCommand()
    {
        return Commands.literal("add").then(shapeTypes(Commands.argument("name", StringArgumentType.word()), true));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T shapeTypes(T node, boolean adding)
    {
        node.then(Commands.literal("sphere").then(typed(size("radius"), Primitive.SPHERE, adding)))
                .then(Commands.literal("cylinder").then(size("radius").then(typed(size("height"), Primitive.CYLINDER, adding))))
                .then(Commands.literal("cone").then(size("radius").then(typed(size("height"), Primitive.CONE, adding))))
                .then(Commands.literal("torus").then(size("radius").then(typed(size("thickness"), Primitive.TORUS, adding))))
                .then(Commands.literal("box")
                        .then(size("width").then(size("height").then(typed(size("depth"), Primitive.BOX, adding))))
                        .then(Commands.literal("from").then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.literal("to").then(boxFromTo(Commands.argument("to", BlockPosArgument.blockPos()), adding))))));
        return node;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T typed(T node, Primitive primitive, boolean adding)
    {
        if (!adding) return timed(node, (ctx, s) -> reshape(ctx, primitive, s));
        Function<Mode, Action> add = mode -> ctx -> add(ctx, primitive, mode);
        node.then(Commands.literal("at")
                .then(modeTail(Commands.literal("here"), add))
                .then(modeTail(Commands.argument("pos", Vec3Argument.vec3(false)), add)));
        return modeTail(node, add);
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T boxFromTo(T node, boolean adding)
    {
        if (!adding) return timed(node, (ctx, s) ->
        {
            Shape old = data(ctx).shape(name(ctx));
            Transform base = old == null ? Transform.at(0.0, 0.0, 0.0) : old.to().transform();
            return reply(ctx, BorderService.reshape(level(ctx), name(ctx), corners(ctx, base), s));
        });
        Function<Mode, Action> add = mode -> ctx ->
                reply(ctx, BorderService.add(level(ctx), name(ctx), corners(ctx, Transform.at(0.0, 0.0, 0.0)), mode));
        return modeTail(node, add);
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T modeTail(T node, Function<Mode, Action> action)
    {
        node.executes(action.apply(null)::run);
        node.then(modes(Commands.literal("mode"), action));
        return node;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T modes(T node, Function<Mode, Action> action)
    {
        for (Mode mode : Mode.values()) node.then(Commands.literal(mode.id()).executes(action.apply(mode)::run));
        return node;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T protections(T node)
    {
        for (Protection protection : Protection.values())
        {
            node.then(Commands.literal(protection.id()).executes(ctx -> reply(ctx, BorderService.protect(level(ctx), name(ctx), protection))));
        }
        return node;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> angles(boolean relative)
    {
        return angle("yaw").then(angle("pitch").then(timed(angle("roll"), (ctx, s) -> reply(ctx, BorderService.rotate(level(ctx),
                name(ctx), number(ctx, "yaw"), number(ctx, "pitch"), number(ctx, "roll"), relative, s)))));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T timed(T node, Timed action)
    {
        node.executes(ctx -> action.run(ctx, 0));
        node.then(Commands.literal("over").then(Commands.argument("seconds", IntegerArgumentType.integer(0, BorderService.MAX_SECONDS))
                .executes(ctx -> action.run(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))));
        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> classicCommand()
    {
        LiteralArgumentBuilder<CommandSourceStack> outline = Commands.literal("outline").executes(ctx ->
                say(ctx, "The classic border is a " + data(ctx).outline().id() + ". Outlines: " + BorderService.outlineList()));
        for (Outline value : Outline.values())
        {
            outline.then(Commands.literal(value.id()).executes(ctx -> reply(ctx, BorderService.setOutline(level(ctx), value))));
        }
        return Commands.literal("classic")
                .executes(CsgBorderCommand::classicStatus)
                .then(Commands.literal("on").executes(ctx -> reply(ctx, BorderService.setClassic(level(ctx), true))))
                .then(Commands.literal("off").executes(ctx -> reply(ctx, BorderService.setClassic(level(ctx), false))))
                .then(outline);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> protectionCommand()
    {
        return Commands.literal("protection")
                .executes(CsgBorderCommand::protectionStatus)
                .then(Commands.literal("remember")
                        .then(Commands.literal("all").executes(ctx -> reply(ctx, BorderService.remember(level(ctx), null))))
                        .then(named(ctx -> reply(ctx, BorderService.remember(level(ctx), name(ctx))))));
    }

    private static int protectionStatus(CommandContext<CommandSourceStack> ctx)
    {
        CsgBorderData data = data(ctx);
        BorderSettings.Values settings = BorderSettings.server();
        StringBuilder text = new StringBuilder("Protection in this dimension:");
        text.append("\nWhole border edge (protectEdge): ").append(settings.protectEdge() ? "on" : "off");
        List<String> done = new ArrayList<>();
        Map<String, List<String>> areas = BorderService.areas(data);
        for (String name : data.shapes().keySet())
        {
            if (data.mode(name) != Mode.ADD || done.contains(name)) continue;
            List<String> area = areas.getOrDefault(name, List.of(name));
            done.addAll(area);
            Protection protection = Protection.NONE;
            for (String member : area) protection = protection.max(data.protection(member));
            text.append("\nArea ").append(String.join(" + ", area)).append(": ").append(protection.id());
        }
        text.append("\nMap blocks only (protectMapBlocksOnly): ").append(settings.protectMapBlocksOnly() ? "on" : "off");
        text.append("\nPlayer blocks remembered: ").append(PlayerBlocks.get(level(ctx)).size());
        return say(ctx, text.toString());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> formulaCommand()
    {
        return Commands.literal("formula")
                .executes(CsgBorderCommand::showFormula)
                .then(Commands.literal("set").then(Commands.argument("formula", StringArgumentType.greedyString())
                        .executes(ctx -> reply(ctx, BorderService.setFormula(level(ctx), StringArgumentType.getString(ctx, "formula"))))))
                .then(Commands.literal("auto").executes(ctx -> reply(ctx, BorderService.setFormula(level(ctx), null))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> settingsCommand()
    {
        LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get");
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset")
                .then(Commands.literal("all").executes(CsgBorderCommand::resetAll));
        for (BorderSettings.Key key : BorderSettings.Key.values())
        {
            get.then(Commands.literal(key.id()).executes(ctx ->
                    say(ctx, key.id() + " = " + BorderSettings.server().get(key) + ". " + key.description())));
            if (key.kind() == BorderSettings.Kind.BOOLEAN)
            {
                set.then(Commands.literal(key.id()).then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> reply(ctx, BorderSettings.set(key, Boolean.toString(BoolArgumentType.getBool(ctx, "value")))))));
            }
            else
            {
                set.then(Commands.literal(key.id()).then(Commands.argument("value", IntegerArgumentType.integer(key.min(), key.max()))
                        .executes(ctx -> reply(ctx, BorderSettings.set(key, Integer.toString(IntegerArgumentType.getInteger(ctx, "value")))))));
            }
            reset.then(Commands.literal(key.id()).executes(ctx ->
                    reply(ctx, BorderSettings.set(key, String.valueOf(BorderSettings.Values.DEFAULT.get(key))))));
        }
        return Commands.literal("settings")
                .executes(CsgBorderCommand::listSettings)
                .then(get)
                .then(set)
                .then(reset)
                .then(Commands.literal("reload").executes(ctx -> reply(ctx, BorderSettings.load(ctx.getSource().getServer()))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, Primitive primitive, Mode mode)
    {
        Vec3 at = hasLiteral(ctx, "here") || !has(ctx, "pos") ? here(ctx) : position(ctx);
        double[] sizes = typedSizes(ctx, primitive);
        ShapeState state = new ShapeState(primitive, sizes[0], sizes[1], sizes[2], Transform.at(at.x, at.y, at.z));
        return reply(ctx, BorderService.add(level(ctx), name(ctx), state, mode));
    }

    private static int reshape(CommandContext<CommandSourceStack> ctx, Primitive primitive, int seconds)
    {
        Shape old = data(ctx).shape(name(ctx));
        Transform base = old == null ? Transform.at(0.0, 0.0, 0.0) : old.to().transform();
        double[] sizes = typedSizes(ctx, primitive);
        return reply(ctx, BorderService.reshape(level(ctx), name(ctx), new ShapeState(primitive, sizes[0], sizes[1], sizes[2], base), seconds));
    }

    private static int moveTo(CommandContext<CommandSourceStack> ctx, Vec3 pos, int seconds)
    {
        return reply(ctx, BorderService.move(level(ctx), name(ctx), pos.x, pos.y, pos.z, false, seconds));
    }

    private static int resizeTo(CommandContext<CommandSourceStack> ctx, int seconds)
    {
        int count = has(ctx, "c") ? 3 : has(ctx, "b") ? 2 : 1;
        double[] sizes = new double[count];
        String[] names = {"a", "b", "c"};
        for (int i = 0; i < count; i++) sizes[i] = number(ctx, names[i]);
        return reply(ctx, BorderService.resize(level(ctx), name(ctx), sizes, seconds));
    }

    private static int scaleTo(CommandContext<CommandSourceStack> ctx, int seconds)
    {
        double sx = number(ctx, "sx");
        boolean each = has(ctx, "sz");
        return reply(ctx, BorderService.scale(level(ctx), name(ctx), sx, each ? number(ctx, "sy") : sx, each ? number(ctx, "sz") : sx, seconds));
    }

    private static ShapeState corners(CommandContext<CommandSourceStack> ctx, Transform base) throws CommandSyntaxException
    {
        BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");
        int minX = Math.min(from.getX(), to.getX()), maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY()), maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ()), maxZ = Math.max(from.getZ(), to.getZ());
        Transform transform = base.withPosition((minX + maxX + 1) * 0.5, (minY + maxY + 1) * 0.5, (minZ + maxZ + 1) * 0.5);
        return new ShapeState(Primitive.BOX, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, transform);
    }

    private static double[] typedSizes(CommandContext<CommandSourceStack> ctx, Primitive primitive)
    {
        return switch (primitive)
        {
            case SPHERE -> new double[] {number(ctx, "radius"), 0.0, 0.0};
            case CYLINDER, CONE -> new double[] {number(ctx, "radius"), number(ctx, "height"), 0.0};
            case TORUS -> new double[] {number(ctx, "radius"), number(ctx, "thickness"), 0.0};
            case BOX -> new double[] {number(ctx, "width"), number(ctx, "height"), number(ctx, "depth")};
        };
    }

    private static int info(CommandContext<CommandSourceStack> ctx)
    {
        CsgBorderData data = data(ctx);
        String name = name(ctx);
        Shape shape = data.shape(name);
        if (shape == null)
        {
            ctx.getSource().sendFailure(Component.literal("No shape named " + name + ". See /worldborder shape list"));
            return 0;
        }
        long now = Util.getMillis();
        int index = new ArrayList<>(data.shapes().keySet()).indexOf(name) + 1;
        String text = BorderService.describe(name, shape.to(), data.mode(name))
                + ", layer " + index + " of " + data.shapes().size()
                + protectionText(data, BorderService.areas(data), name)
                + (shape.isMoving(now) ? " (animating, " + shape.remaining(now) / 1000L + "s left)" : "");
        return say(ctx, text);
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
    {
        CsgBorderData data = data(ctx);
        if (data.shapes().isEmpty())
        {
            say(ctx, "No shapes. The classic " + data.outline().id() + " border is active. Start with /worldborder shape add <name> sphere <radius>");
            return 0;
        }
        long now = Util.getMillis();
        StringBuilder text = new StringBuilder("Shapes, top to bottom:");
        int i = 1;
        Map<String, List<String>> areas = BorderService.areas(data);
        for (Shape shape : data.shapes().values())
        {
            text.append("\n").append(i++).append(". ").append(BorderService.describe(shape.name(), shape.to(), data.mode(shape.name())));
            text.append(protectionText(data, areas, shape.name()));
            if (shape.isMoving(now)) text.append(" (animating)");
        }
        text.append("\nClassic border: ").append(data.classic() ? "on" : "off").append(", ").append(data.outline().id());
        text.append("\nFormula").append(data.hasCustomFormula() ? " (custom): " : ": ").append(data.expression().format());
        say(ctx, text.toString());
        return data.shapes().size();
    }

    private static String protectionText(CsgBorderData data, Map<String, List<String>> areas, String name)
    {
        if (data.mode(name) != Mode.ADD) return "";
        List<String> area = areas.getOrDefault(name, List.of(name));
        Protection protection = Protection.NONE;
        for (String member : area) protection = protection.max(data.protection(member));
        if (protection == Protection.NONE) return "";
        return ", " + protection.id() + " protected" + (area.size() > 1 ? " (area " + String.join(" + ", area) + ")" : "");
    }

    private static int classicStatus(CommandContext<CommandSourceStack> ctx)
    {
        CsgBorderData data = data(ctx);
        String outline = data.outline().id();
        String text = data.shapes().isEmpty()
                ? "The classic " + outline + " border is active (there are no shapes)"
                : "Classic " + outline + " border is " + (data.classic() ? "on: it limits your shapes too" : "off: only your shapes count");
        say(ctx, text);
        return data.classic() ? 1 : 0;
    }

    private static int showFormula(CommandContext<CommandSourceStack> ctx)
    {
        CsgBorderData data = data(ctx);
        return say(ctx, "Formula" + (data.hasCustomFormula() ? " (custom): " : " (automatic): ") + data.expression().format());
    }

    private static int listSettings(CommandContext<CommandSourceStack> ctx)
    {
        BorderSettings.Values values = BorderSettings.server();
        StringBuilder text = new StringBuilder("World border settings (saved in this world):");
        for (BorderSettings.Key key : BorderSettings.Key.values())
        {
            text.append("\n").append(key.id()).append(" = ").append(values.get(key));
        }
        return say(ctx, text.toString());
    }

    private static int resetAll(CommandContext<CommandSourceStack> ctx)
    {
        for (BorderSettings.Key key : BorderSettings.Key.values())
        {
            BorderService.Result result = BorderSettings.set(key, String.valueOf(BorderSettings.Values.DEFAULT.get(key)));
            if (!result.ok()) return reply(ctx, result);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("All world border settings are back to their defaults"), true);
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        CsgNetwork.send(ctx.getSource().getPlayerOrException(), new OpenGuiPacket());
        return 1;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> shapeName()
    {
        return Commands.argument("name", StringArgumentType.word()).suggests(SHAPE_NAMES);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> named(Action action)
    {
        return shapeName().executes(action::run);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> size(String name)
    {
        return Commands.argument(name, DoubleArgumentType.doubleArg(BorderService.MIN_SIZE, BorderService.MAX_SIZE));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> factor(String name)
    {
        return Commands.argument(name, DoubleArgumentType.doubleArg(BorderService.MIN_SCALE, BorderService.MAX_SCALE));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> angle(String name)
    {
        return Commands.argument(name, DoubleArgumentType.doubleArg(-MAX_ANGLE, MAX_ANGLE));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> offset(String name)
    {
        return Commands.argument(name, DoubleArgumentType.doubleArg(-MAX_OFFSET, MAX_OFFSET));
    }

    private static Vec3 position(CommandContext<CommandSourceStack> ctx)
    {
        Vec3 raw = Vec3Argument.getVec3(ctx, "pos");
        String[] typed = typedTokens(ctx, "pos");
        return new Vec3(blockCenter(raw.x, typed, 0), blockCenter(raw.y, typed, 1), blockCenter(raw.z, typed, 2));
    }

    private static String[] typedTokens(CommandContext<CommandSourceStack> ctx, String argument)
    {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes())
        {
            if (node.getNode() instanceof LiteralCommandNode || !node.getNode().getName().equals(argument)) continue;
            return node.getRange().get(ctx.getInput()).trim().split("\\s+");
        }
        return new String[0];
    }

    private static boolean has(CommandContext<CommandSourceStack> ctx, String argument)
    {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes())
        {
            if (!(node.getNode() instanceof LiteralCommandNode) && node.getNode().getName().equals(argument)) return true;
        }
        return false;
    }

    private static boolean hasLiteral(CommandContext<CommandSourceStack> ctx, String literal)
    {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes())
        {
            if (node.getNode() instanceof LiteralCommandNode<CommandSourceStack> l && l.getLiteral().equals(literal)) return true;
        }
        return false;
    }

    private static double number(CommandContext<CommandSourceStack> ctx, String name)
    {
        return DoubleArgumentType.getDouble(ctx, name);
    }

    private static int say(CommandContext<CommandSourceStack> ctx, String text)
    {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, BorderService.Result result)
    {
        if (!result.ok())
        {
            ctx.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static double blockCenter(double value, String[] typed, int axis)
    {
        if (axis >= typed.length) return value;
        String token = typed[axis];
        boolean whole = !token.isEmpty() && token.chars().allMatch(c -> Character.isDigit(c) || c == '-' || c == '+');
        return whole ? value + 0.5 : value;
    }

    private static Vec3 here(CommandContext<CommandSourceStack> ctx)
    {
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static String name(CommandContext<CommandSourceStack> ctx)
    {
        return StringArgumentType.getString(ctx, "name");
    }

    private static ServerLevel level(CommandContext<CommandSourceStack> ctx)
    {
        return ctx.getSource().getLevel();
    }

    private static CsgBorderData data(CommandContext<CommandSourceStack> ctx)
    {
        return CsgBorderData.get(level(ctx));
    }
}
