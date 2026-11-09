package com.lildorito.csgworldborder.border;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.lildorito.csgworldborder.CsgWorldBorder;
import com.lildorito.csgworldborder.network.CsgNetwork;
import com.lildorito.csgworldborder.network.SettingsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class BorderSettings
{
    public enum Kind
    {
        BOOLEAN,
        INTEGER
    }

    public enum Key
    {
        PROTECT_EDGE("protectEdge", "Blocks along the inside of the whole border cannot be broken or placed except in creative", false),
        PROTECT_THICKNESS("protectThickness", "How many layers in from the border are protected", 1, 1, 8),
        PROTECT_WALLS("protectWalls", "Protect the layers next to the sides of the border", true),
        PROTECT_FLOOR("protectFloor", "Protect the layers above the bottom of the border", true),
        PROTECT_CEILING("protectCeiling", "Protect the layers below the top of the border", true),
        PROTECT_MAP_BLOCKS_ONLY("protectMapBlocksOnly", "Protection covers only map blocks: blocks placed by survival and adventure players stay breakable wherever the border goes", false),
        INSIDE_WARNING("insideWarning", "Show the red screen when near the border from inside. Off still shows it outside", true),
        BLOCK_PROJECTILES("blockProjectiles", "Arrows, tridents, snowballs, pearls and fireballs stop at the border", false),
        SHOW_BORDER("showBorder", "Draw the border forcefield. Off makes it invisible but it still works", true),
        BORDER_FADE_DISTANCE("borderFadeDistance", "How far the forcefield stays visible, in percent of render distance", 60, 5, 100);

        private final String id;
        private final String description;
        private final Kind kind;
        private final int defaultInt;
        private final int min;
        private final int max;
        private final boolean defaultBool;

        Key(String id, String description, boolean defaultValue)
        {
            this.id = id;
            this.description = description;
            this.kind = Kind.BOOLEAN;
            this.defaultBool = defaultValue;
            this.defaultInt = 0;
            this.min = 0;
            this.max = 1;
        }

        Key(String id, String description, int defaultValue, int min, int max)
        {
            this.id = id;
            this.description = description;
            this.kind = Kind.INTEGER;
            this.defaultBool = false;
            this.defaultInt = defaultValue;
            this.min = min;
            this.max = max;
        }

        public String id() { return id; }
        public String description() { return description; }
        public Kind kind() { return kind; }
        public int min() { return min; }
        public int max() { return max; }

        @Nullable
        public static Key byId(String id)
        {
            for (Key key : values())
            {
                if (key.id.equalsIgnoreCase(id)) return key;
            }
            return null;
        }
    }

    public record Values(boolean protectEdge, int protectThickness, boolean protectWalls, boolean protectFloor,
                         boolean protectCeiling, boolean protectMapBlocksOnly, boolean insideWarning, boolean blockProjectiles, boolean showBorder,
                         int borderFadeDistance)
    {
        public static final Values DEFAULT = new Values(false, 1, true, true, true, false, true, false, true, 60);

        public Object get(Key key)
        {
            return switch (key)
            {
                case PROTECT_EDGE -> protectEdge;
                case PROTECT_THICKNESS -> protectThickness;
                case PROTECT_WALLS -> protectWalls;
                case PROTECT_FLOOR -> protectFloor;
                case PROTECT_CEILING -> protectCeiling;
                case PROTECT_MAP_BLOCKS_ONLY -> protectMapBlocksOnly;
                case INSIDE_WARNING -> insideWarning;
                case BLOCK_PROJECTILES -> blockProjectiles;
                case SHOW_BORDER -> showBorder;
                case BORDER_FADE_DISTANCE -> borderFadeDistance;
            };
        }

        public void write(FriendlyByteBuf buf)
        {
            buf.writeBoolean(protectEdge);
            buf.writeVarInt(protectThickness);
            buf.writeBoolean(protectWalls);
            buf.writeBoolean(protectFloor);
            buf.writeBoolean(protectCeiling);
            buf.writeBoolean(protectMapBlocksOnly);
            buf.writeBoolean(insideWarning);
            buf.writeBoolean(blockProjectiles);
            buf.writeBoolean(showBorder);
            buf.writeVarInt(borderFadeDistance);
        }

        public static Values read(FriendlyByteBuf buf)
        {
            return new Values(buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }
    }

    private static final String FILE = "csgworldborder-server.toml";

    private static volatile Values server = Values.DEFAULT;
    private static volatile Values client = Values.DEFAULT;
    @Nullable
    private static Path file;

    private BorderSettings() {}

    public static Values of(Level level)
    {
        return level.isClientSide ? client : server;
    }

    public static Values server()
    {
        return server;
    }

    public static Values client()
    {
        return client;
    }

    public static void setClient(Values values)
    {
        client = values;
    }

    public static synchronized BorderService.Result load(MinecraftServer minecraftServer)
    {
        file = minecraftServer.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE);
        try
        {
            Files.createDirectories(file.getParent());
            Path defaults = FMLPaths.GAMEDIR.get().resolve("defaultconfigs").resolve(FILE);
            if (!Files.exists(file) && Files.exists(defaults)) Files.copy(defaults, file);
            Map<Key, Object> values = new EnumMap<>(Key.class);
            if (Files.exists(file))
            {
                try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build())
                {
                    config.load();
                    for (Key key : Key.values()) values.put(key, sanitize(key, config.get(key.id)));
                }
            }
            server = build(values);
            write();
            broadcast();
            return new BorderService.Result(true, "Reloaded settings from " + FILE);
        }
        catch (IOException | RuntimeException e)
        {
            CsgWorldBorder.LOGGER.error("CSG WorldBorder: could not read {}, using defaults", file, e);
            server = Values.DEFAULT;
            broadcast();
            return new BorderService.Result(false, "Could not read " + FILE + ": " + e.getMessage());
        }
    }

    public static synchronized BorderService.Result set(Key key, String raw)
    {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        Object value;
        if (key.kind == Kind.BOOLEAN)
        {
            if (text.equals("true") || text.equals("on")) value = true;
            else if (text.equals("false") || text.equals("off")) value = false;
            else return new BorderService.Result(false, key.id + " must be true or false");
        }
        else
        {
            int number;
            try
            {
                number = Integer.parseInt(text);
            }
            catch (NumberFormatException e)
            {
                return new BorderService.Result(false, key.id + " must be a whole number");
            }
            if (number < key.min || number > key.max)
            {
                return new BorderService.Result(false, key.id + " must be between " + key.min + " and " + key.max);
            }
            value = number;
        }
        Map<Key, Object> values = new EnumMap<>(Key.class);
        for (Key k : Key.values()) values.put(k, server.get(k));
        values.put(key, value);
        server = build(values);
        try
        {
            write();
        }
        catch (RuntimeException e)
        {
            CsgWorldBorder.LOGGER.error("CSG WorldBorder: could not save {}", file, e);
        }
        broadcast();
        return new BorderService.Result(true, "Set " + key.id + " to " + server.get(key));
    }

    public static synchronized void unload()
    {
        file = null;
        server = Values.DEFAULT;
    }

    private static void write()
    {
        if (file == null) return;
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().preserveInsertionOrder().build())
        {
            for (Key key : Key.values())
            {
                config.set(key.id, server.get(key));
                String range = key.kind == Kind.BOOLEAN ? "true or false" : key.min + " to " + key.max;
                config.setComment(key.id, " " + key.description + "\n Allowed: " + range + ". Default: " + Values.DEFAULT.get(key));
            }
            config.save();
        }
    }

    private static Object sanitize(Key key, @Nullable Object raw)
    {
        Object fallback = Values.DEFAULT.get(key);
        if (key.kind == Kind.BOOLEAN) return raw instanceof Boolean b ? b : fallback;
        if (raw instanceof Number n)
        {
            int value = n.intValue();
            return value < key.min || value > key.max ? fallback : value;
        }
        return fallback;
    }

    private static Values build(Map<Key, Object> v)
    {
        return new Values(b(v, Key.PROTECT_EDGE), i(v, Key.PROTECT_THICKNESS), b(v, Key.PROTECT_WALLS), b(v, Key.PROTECT_FLOOR),
                b(v, Key.PROTECT_CEILING), b(v, Key.PROTECT_MAP_BLOCKS_ONLY), b(v, Key.INSIDE_WARNING), b(v, Key.BLOCK_PROJECTILES), b(v, Key.SHOW_BORDER),
                i(v, Key.BORDER_FADE_DISTANCE));
    }

    private static boolean b(Map<Key, Object> values, Key key)
    {
        Object value = values.get(key);
        return value instanceof Boolean flag ? flag : (Boolean) Values.DEFAULT.get(key);
    }

    private static int i(Map<Key, Object> values, Key key)
    {
        Object value = values.get(key);
        return value instanceof Integer number ? number : (Integer) Values.DEFAULT.get(key);
    }

    private static void broadcast()
    {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        CsgNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new SettingsPacket(server));
    }
}
