package ru.kiviuly.skyblockwars.sbw;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.skyblockwars.arena.Arena;
import ru.kiviuly.skyblockwars.game.GameSession;
import ru.kiviuly.skyblockwars.game.MatchPlayer;
import ru.kiviuly.skyblockwars.game.MatchResult;
import ru.kiviuly.skyblockwars.game.Minigame;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig.EpochMode;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.skyblockwars.sbw.epoch.EpochBlock;
import ru.kiviuly.skyblockwars.util.Msg;

/**
 * SkyBlockWars Reborn — арена выживания на блоках-эпохах. Каждого игрока телепортирует
 * на свой спавн; блок под ним — блок возрождения. Пока чужой игрок его не сломал,
 * игрок при гибели возрождается на нём. Ломая свой блок, игрок получает его добычу,
 * блок восстанавливается случайным из текущей эпохи, а прогресс идёт к рубежу эпохи
 * (боссбар). Смена эпох — персонально у каждого или общая на арену (режим в конфиге).
 *
 * Точка расширения ядра: логика без состояния, состояние матча — в {@link SbwState}
 * ({@link GameSession#data()}). Игро-конфиг арены (центр/радиус/режим/эпохи) — в
 * {@link ArenaGameConfig} (файл game/&lt;ID&gt;.yml); кольцо спавнов — в ядровой
 * {@code arena.getSpawns()}.
 */
public class SkyBlockWarsGame extends Minigame
{
    private static final List<String> SUBS = List.of("setcenter", "setradius", "setmaxplayers", "setmode");

    /** Кэш игро-конфигов арен (ленивая загрузка, сброс на reload). */
    private final Map<String, ArenaGameConfig> configs = new HashMap<>();
    private final EpochBossBar bossBar = new EpochBossBar();

    public SkyBlockWarsGame(SkyBlockWarsPlugin plugin)
    {
        super(plugin);
        plugin.getServer().getPluginManager().registerEvents(new SbwListener(plugin, this), plugin);
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

    // ===== жизненный цикл матча =====

    @Override
    public void onStart(GameSession s)
    {
        SbwState st = ensureState(s);
        Epoch first = st.epochAt(0);
        s.broadcast("sbw.match-begin", Msg.ph("epoch", first != null ? first.getName() : "-"));
    }

    @Override
    public void giveLoadout(GameSession s, Player p)
    {
        SbwState st = ensureState(s);
        SbwState.PlayerData pd = st.player(p.getUniqueId());
        // блок возрождения — под ногами игрока (ядро уже телепортировало его на спавн)
        Location anchor = p.getLocation().getBlock().getRelative(0, -1, 0).getLocation();
        Epoch epoch = st.currentEpoch(p.getUniqueId());
        placeRespawnBlock(s, anchor, epoch != null ? epoch.pickRandom() : null);
        pd.respawnBlock = anchor;
        pd.respawnAlive = true;
        st.setOwner(anchor, p.getUniqueId());
        giveKit(p);
        bossBar.add(p);
        bossBar.update(st, p);
    }

    @Override
    public void onTick(GameSession s)
    {
        SbwState st = SbwState.of(s);
        if (st != null) {bossBar.updateAll(st, s.alivePlayers());}
    }

    @Override
    public boolean onLethalDamage(GameSession s, Player p)
    {
        SbwState st = SbwState.of(s);
        if (st == null) {return true;}
        SbwState.PlayerData pd = st.peek(p.getUniqueId());
        if (pd != null && pd.respawnAlive && pd.respawnBlock != null)
        {
            respawn(p, pd);
            return false; // возродили — ядро не выбивает
        }
        return true; // якоря нет — выбывание
    }

    @Override
    public void onPlayerEliminated(GameSession s, MatchPlayer mp) {bossBar.remove(Bukkit.getPlayer(mp.getUuid()));}

    @Override
    public void onPlayerRemoved(GameSession s, UUID id) {bossBar.remove(Bukkit.getPlayer(id));}

    @Override
    public void onEnd(GameSession s, MatchResult result) {bossBar.clearAll(s.onlinePlayers());}

    @Override
    public List<Component> scoreboardLines(GameSession s, Player viewer)
    {
        SbwState st = SbwState.of(s);
        // Ядровой сайдбар ОБЩИЙ для всех (viewer здесь == null). Персональный прогресс эпохи —
        // в боссбаре (per-player). В сайдбаре показываем эпоху только в общем режиме (SHARED).
        if (st == null || st.mode() != EpochMode.SHARED) {return List.of();}
        Epoch epoch = st.epochAt(st.sharedEpochIndex());
        int prog = st.sharedProgress();
        int goal = epoch != null ? epoch.getThreshold() : 0;
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.get("sbw.sidebar-epoch", Msg.ph("epoch", epoch != null ? epoch.getName() : "-")));
        lines.add(goal > 0
            ? Msg.get("sbw.sidebar-progress", Msg.ph("prog", prog), Msg.ph("goal", goal))
            : Msg.get("sbw.sidebar-progress-final", Msg.ph("prog", prog)));
        return lines;
    }

    // ===== ломание блоков (зовётся из SbwListener) =====

    /** Игрок сломал СВОЙ блок: добыча + прогресс к рубежу + рефилл случайным блоком эпохи. */
    public void mineOwnBlock(GameSession s, SbwState st, Player p, Block block)
    {
        giveYield(p, block);
        boolean advanced = advanceProgress(s, st, p);
        Epoch epoch = st.currentEpoch(p.getUniqueId());
        placeRespawnBlock(s, block.getLocation(), epoch != null ? epoch.pickRandom() : null);
        p.playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 0.6f, 1.2f);
        bossBar.update(st, p);
        if (advanced) {p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);}
    }

    /** Игрок сломал ЧУЖОЙ блок возрождения: владелец теряет якорь (выбывает при след. смерти). */
    public void destroyEnemyBlock(GameSession s, SbwState st, Player breaker, UUID owner, Block block)
    {
        SbwState.PlayerData od = st.peek(owner);
        if (od != null)
        {
            od.respawnAlive = false;
            od.respawnBlock = null;
        }
        st.removeOwner(block.getLocation());
        Player ownerP = Bukkit.getPlayer(owner);
        String ownerName = ownerP != null ? ownerP.getName() : owner.toString().substring(0, 8);
        s.broadcast("sbw.block-destroyed", Msg.ph("breaker", breaker.getName()), Msg.ph("victim", ownerName));
        if (ownerP != null)
        {
            ownerP.showTitle(Title.title(Msg.get("sbw.your-block-title"), Msg.get("sbw.your-block-subtitle")));
            ownerP.playSound(ownerP.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
        }
        // блок ломается ванильно (событие не отменяли) — дроп достаётся атакующему
    }

    // ===== helpers =====

    private SbwState ensureState(GameSession s)
    {
        SbwState st = SbwState.of(s);
        if (st == null)
        {
            ArenaGameConfig cfg = config(s.arena().getId());
            st = new SbwState(cfg.getEpochMode(), resolveEpochs(cfg));
            s.data().put(SbwState.KEY, st);
        }
        return st;
    }

    private List<Epoch> resolveEpochs(ArenaGameConfig cfg)
    {
        return cfg.epochs().isEmpty() ? defaultEpochs() : cfg.epochs();
    }

    /** Прогресс +1 к рубежу и при достижении — переход к следующей эпохе. true = эпоха сменилась. */
    private boolean advanceProgress(GameSession s, SbwState st, Player p)
    {
        if (st.mode() == EpochMode.SHARED)
        {
            int idx = st.sharedEpochIndex();
            st.setSharedProgress(st.sharedProgress() + 1);
            Epoch cur = st.epochAt(idx);
            if (cur != null && cur.hasThreshold() && idx < st.epochs().size() - 1 && st.sharedProgress() >= cur.getThreshold())
            {
                st.setSharedEpochIndex(idx + 1);
                st.setSharedProgress(0);
                Epoch next = st.epochAt(idx + 1);
                s.broadcast("sbw.epoch-advance-shared", Msg.ph("epoch", next != null ? next.getName() : "-"));
                bossBar.updateAll(st, s.alivePlayers());
                return true;
            }
            return false;
        }
        SbwState.PlayerData pd = st.player(p.getUniqueId());
        int idx = pd.epochIndex;
        pd.progress++;
        Epoch cur = st.epochAt(idx);
        if (cur != null && cur.hasThreshold() && idx < st.epochs().size() - 1 && pd.progress >= cur.getThreshold())
        {
            pd.epochIndex = idx + 1;
            pd.progress = 0;
            Epoch next = st.epochAt(idx + 1);
            Msg.send(p, "sbw.epoch-advance", Msg.ph("epoch", next != null ? next.getName() : "-"));
            return true;
        }
        return false;
    }

    /** Добыча блока в инвентарь: контейнер отдаёт своё содержимое, обычный блок — сам себя. */
    private void giveYield(Player p, Block block)
    {
        if (block.getState() instanceof Container c)
        {
            for (ItemStack it : c.getInventory().getContents())
            {
                if (it != null && !it.getType().isAir()) {giveOrDrop(p, it.clone());}
            }
        }
        else
        {
            giveOrDrop(p, new ItemStack(block.getType()));
        }
    }

    /** Поставить/восстановить блок возрождения (+ вложить лут, если контейнер). Помечает для отката. */
    private void placeRespawnBlock(GameSession s, Location loc, EpochBlock pick)
    {
        Block b = loc.getBlock();
        s.rememberBlock(b); // идемпотентно: исходный AIR уже запомнен при первой установке
        if (pick == null) {b.setType(Material.STONE, false); return;} // эпоха без блоков — опора, чтобы не упасть
        b.setType(pick.getMaterial(), false);
        if (pick.isContainer() && pick.hasContents() && b.getState() instanceof Container c)
        {
            for (Map.Entry<Integer, ItemStack> e : pick.getContents().entrySet())
            {
                int slot = e.getKey();
                if (slot >= 0 && slot < c.getInventory().getSize()) {c.getInventory().setItem(slot, e.getValue().clone());}
            }
            c.update(true, false);
        }
    }

    private void respawn(Player p, SbwState.PlayerData pd)
    {
        Location to = pd.respawnBlock.clone().add(0.5, 1.0, 0.5);
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        p.teleport(to);
        fullHeal(p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, false, false)); // 3с почти-неуязвимости
        p.playSound(to, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.8f);
        Msg.send(p, "sbw.respawned");
    }

    private void fullHeal(Player p)
    {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(10f);
        p.setFireTicks(0);
        for (PotionEffect ef : p.getActivePotionEffects()) {p.removePotionEffect(ef.getType());}
    }

    private void giveKit(Player p)
    {
        p.getInventory().addItem(
            new ItemStack(Material.STONE_SWORD),
            new ItemStack(Material.STONE_PICKAXE),
            new ItemStack(Material.STONE_AXE),
            new ItemStack(Material.STONE_SHOVEL),
            new ItemStack(Material.COOKED_BEEF, 16));
    }

    private void giveOrDrop(Player p, ItemStack item)
    {
        for (ItemStack rem : p.getInventory().addItem(item).values())
        {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
    }

    /** Эпохи по умолчанию — чтобы игра работала до настройки арены (образец: 3 эпохи, есть контейнер). */
    private List<Epoch> defaultEpochs()
    {
        Epoch stone = new Epoch("Каменный век", 16);
        stone.getBlocks().add(new EpochBlock(Material.COBBLESTONE, 6));
        stone.getBlocks().add(new EpochBlock(Material.DIRT, 4));
        stone.getBlocks().add(new EpochBlock(Material.OAK_LOG, 3));
        stone.getBlocks().add(new EpochBlock(Material.COAL_ORE, 2));

        Epoch iron = new Epoch("Железный век", 24);
        iron.getBlocks().add(new EpochBlock(Material.STONE, 5));
        iron.getBlocks().add(new EpochBlock(Material.IRON_ORE, 3));
        iron.getBlocks().add(new EpochBlock(Material.OAK_PLANKS, 3));
        EpochBlock chest = new EpochBlock(Material.CHEST, 1);
        chest.getContents().put(11, new ItemStack(Material.GOLDEN_APPLE));
        chest.getContents().put(13, new ItemStack(Material.IRON_INGOT, 3));
        chest.getContents().put(15, new ItemStack(Material.ARROW, 8));
        iron.getBlocks().add(chest);

        Epoch diamond = new Epoch("Алмазный век", -1); // финальная — до конца матча
        diamond.getBlocks().add(new EpochBlock(Material.END_STONE, 4));
        diamond.getBlocks().add(new EpochBlock(Material.OBSIDIAN, 3));
        diamond.getBlocks().add(new EpochBlock(Material.DIAMOND_ORE, 2));

        return new ArrayList<>(List.of(stone, iron, diamond));
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
