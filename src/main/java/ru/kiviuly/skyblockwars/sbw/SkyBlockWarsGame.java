package ru.kiviuly.skyblockwars.sbw;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.skyblockwars.arena.Arena;
import ru.kiviuly.skyblockwars.game.Minigame;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig.EpochMode;
import ru.kiviuly.skyblockwars.util.Msg;

/**
 * SkyBlockWars Reborn — арена выживания на блоках-эпохах. Каждого игрока телепортирует
 * на свой спавн; блок под ним — блок возрождения. Пока чужой игрок его не сломал —
 * игрок возрождается. Ломая свой блок, игрок получает материалы, блок восстанавливается
 * случайным из текущей эпохи, а прогресс идёт к рубежу эпохи (боссбар). У арены есть
 * эпохи (наборы блоков с весами) и фазовый таймер матча (см. последующие фазы).
 *
 * Точка расширения ядра: логика без состояния, состояние матча — в GameSession.
 * Игро-специфичная конфигурация арены (центр/радиус/режим эпох/эпохи) — в {@link
 * ArenaGameConfig} (файл {@code game/<ID>.yml}); кольцо спавнов пишется в ядровой
 * {@code arena.getSpawns()}.
 */
public class SkyBlockWarsGame extends Minigame
{
    private static final List<String> SUBS = List.of("setcenter", "setradius", "setmaxplayers", "setmode");

    /** Кэш игро-конфигов арен (ленивая загрузка, сброс на reload). */
    private final Map<String, ArenaGameConfig> configs = new HashMap<>();

    public SkyBlockWarsGame(SkyBlockWarsPlugin plugin)
    {
        super(plugin);
    }

    @Override
    public String id() {return "skyblockwars";}

    @Override
    public String displayName() {return Msg.raw("sbw.display-name");}

    // ===== игро-конфиг арены =====

    /** Игро-конфиг арены (ленивая загрузка из game/<ID>.yml). */
    public ArenaGameConfig config(String arenaId)
    {
        String id = arenaId.toUpperCase(Locale.ROOT);
        return configs.computeIfAbsent(id, k -> ArenaGameConfig.load(k, dataFile(k), defaultRadius(), defaultMode()));
    }

    public void saveConfig(ArenaGameConfig cfg) {cfg.save(dataFile(cfg.arenaId()));}

    private File dataFile(String arenaId) {return new File(new File(plugin.getDataFolder(), "game"), arenaId + ".yml");}

    private int defaultRadius() {return Math.max(1, plugin.getConfig().getInt("skyblockwars.radius-default", 40));}

    private EpochMode defaultMode()
    {
        String raw = plugin.getConfig().getString("skyblockwars.epoch-mode-default", "personal");
        try {return EpochMode.valueOf(raw.toUpperCase(Locale.ROOT));}
        catch (IllegalArgumentException e) {return EpochMode.PERSONAL;}
    }

    @Override
    public void onReload() {configs.clear();}

    @Override
    public void onArenaRemoved(String arenaId)
    {
        String id = arenaId.toUpperCase(Locale.ROOT);
        configs.remove(id);
        File f = dataFile(id);
        if (f.exists()) {f.delete();}
    }

    // ===== команды =====

    @Override
    public boolean onCommand(Player p, String sub, String[] args)
    {
        switch (sub)
        {
            case "setcenter" -> {return cmdSetCenter(p, args);}
            case "setradius" -> {return cmdSetRadius(p, args);}
            case "setmaxplayers" -> {return cmdSetMaxPlayers(p, args);}
            case "setmode" -> {return cmdSetMode(p, args);}
            default -> {return false;}
        }
    }

    /** Резолв арены для setup-команды; null + сообщение, если недоступна. */
    private Arena setupArena(Player p, String[] args, String usageKey)
    {
        if (args.length < 2) {Msg.send(p, usageKey); return null;}
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {Msg.send(p, "errors.arena-not-found", Msg.ph("arena", args[1])); return null;}
        if (arena.getSession() != null) {Msg.send(p, "sbw.busy", Msg.ph("arena", arena.getId())); return null;}
        return arena;
    }

    private boolean cmdSetCenter(Player p, String[] args)
    {
        Arena arena = setupArena(p, args, "sbw.usage-setcenter");
        if (arena == null) {return true;}
        ArenaGameConfig cfg = config(arena.getId());
        cfg.setCenter(p.getLocation().toBlockLocation().add(0.5, 0, 0.5));
        arena.setWorldName(p.getWorld().getName()); // спавны кольца — в мире админа
        saveConfig(cfg);
        int n = regenerateSpawns(arena, cfg);
        Msg.send(p, "sbw.center-set", Msg.ph("arena", arena.getId()), Msg.ph("n", n), Msg.ph("radius", cfg.getRadius()));
        return true;
    }

    private boolean cmdSetRadius(Player p, String[] args)
    {
        Arena arena = setupArena(p, args, "sbw.usage-setradius");
        if (arena == null) {return true;}
        if (args.length < 3) {Msg.send(p, "sbw.usage-setradius"); return true;}
        int radius;
        if (args[2].equalsIgnoreCase("default")) {radius = defaultRadius();}
        else
        {
            try {radius = Integer.parseInt(args[2]);}
            catch (NumberFormatException e) {Msg.send(p, "errors.not-a-number"); return true;}
        }
        ArenaGameConfig cfg = config(arena.getId());
        cfg.setRadius(Math.max(1, radius));
        saveConfig(cfg);
        if (cfg.getCenter() == null)
        {
            Msg.send(p, "sbw.radius-set-no-center", Msg.ph("arena", arena.getId()), Msg.ph("radius", cfg.getRadius()));
            return true;
        }
        int n = regenerateSpawns(arena, cfg);
        Msg.send(p, "sbw.radius-set", Msg.ph("arena", arena.getId()), Msg.ph("radius", cfg.getRadius()), Msg.ph("n", n));
        return true;
    }

    private boolean cmdSetMaxPlayers(Player p, String[] args)
    {
        Arena arena = setupArena(p, args, "sbw.usage-setmaxplayers");
        if (arena == null) {return true;}
        if (args.length < 3) {Msg.send(p, "sbw.usage-setmaxplayers"); return true;}
        int max;
        try {max = Integer.parseInt(args[2]);}
        catch (NumberFormatException e) {Msg.send(p, "errors.not-a-number"); return true;}
        arena.setMaxPlayers(Math.max(1, max));
        plugin.arenas().save(arena);
        ArenaGameConfig cfg = config(arena.getId());
        int n = cfg.getCenter() == null ? 0 : regenerateSpawns(arena, cfg);
        Msg.send(p, "sbw.maxplayers-set", Msg.ph("arena", arena.getId()), Msg.ph("max", arena.getMaxPlayers()), Msg.ph("n", n));
        return true;
    }

    private boolean cmdSetMode(Player p, String[] args)
    {
        Arena arena = setupArena(p, args, "sbw.usage-setmode");
        if (arena == null) {return true;}
        if (args.length < 3) {Msg.send(p, "sbw.usage-setmode"); return true;}
        EpochMode mode;
        try {mode = EpochMode.valueOf(args[2].toUpperCase(Locale.ROOT));}
        catch (IllegalArgumentException e) {Msg.send(p, "sbw.usage-setmode"); return true;}
        ArenaGameConfig cfg = config(arena.getId());
        cfg.setEpochMode(mode);
        saveConfig(cfg);
        Msg.send(p, "sbw.mode-set", Msg.ph("arena", arena.getId()), Msg.ph("mode", mode.name().toLowerCase(Locale.ROOT)));
        return true;
    }

    /** Пересобрать кольцо спавнов в ядровой arena.getSpawns() по центру/радиусу/максу. */
    private int regenerateSpawns(Arena arena, ArenaGameConfig cfg)
    {
        List<Location> ring = RingSpawns.ring(cfg.getCenter(), cfg.getRadius(), arena.getMaxPlayers());
        arena.getSpawns().clear();
        arena.getSpawns().addAll(ring);
        plugin.arenas().save(arena);
        return ring.size();
    }

    // ===== таб-комплит / справка =====

    @Override
    public List<String> tabComplete(Player p, String[] args)
    {
        List<String> out = new ArrayList<>();
        if (args.length == 1)
        {
            filter(SUBS, args[0], out);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {return out;}
        if (args.length == 2)
        {
            filter(new ArrayList<>(plugin.arenas().ids()), args[1], out);
        }
        else if (args.length == 3)
        {
            switch (sub)
            {
                case "setradius" -> filter(List.of("default", "20", "40", "60", "80"), args[2], out);
                case "setmaxplayers" -> filter(List.of("4", "8", "12", "16"), args[2], out);
                case "setmode" -> filter(List.of("personal", "shared"), args[2], out);
                default -> {}
            }
        }
        return out;
    }

    @Override
    public List<Component> helpLines(Player p) {return Msg.getList("sbw.help");}

    private void filter(List<String> options, String prefix, List<String> out)
    {
        String low = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {if (o.toLowerCase(Locale.ROOT).startsWith(low)) {out.add(o);}}
    }
}
