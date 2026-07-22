package ru.kiviuly.skyblockwars.sbw;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
import ru.kiviuly.skyblockwars.sbw.menu.EpochListMenu;
import ru.kiviuly.skyblockwars.sbw.menu.KitEditorMenu;
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
    private static final List<String> SUBS = List.of("setcenter", "setradius", "setmaxplayers", "setmode", "epochs", "kit");

    /** Кэш игро-конфигов арен (ленивая загрузка, сброс на reload). */
    private final Map<String, ArenaGameConfig> configs = new HashMap<>();

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
        return configs.computeIfAbsent(id, k -> ArenaGameConfig.load(k, dataFile(k),
            defaultRadius(), defaultMode(), defaultMatchSeconds(), defaultFightSeconds()));
    }

    public void saveConfig(ArenaGameConfig cfg) {cfg.save(dataFile(cfg.arenaId()));}

    private File dataFile(String arenaId) {return new File(new File(plugin.getDataFolder(), "game"), arenaId + ".yml");}

    private int defaultRadius() {return Math.max(1, plugin.getConfig().getInt("skyblockwars.radius-default", 40));}
    private int defaultMatchSeconds() {return Math.max(1, plugin.getConfig().getInt("skyblockwars.match-seconds", 1800));}
    private int defaultFightSeconds() {return Math.max(0, plugin.getConfig().getInt("skyblockwars.fight-seconds", 300));}
    private double destructionRate() {return Math.max(0.01, Math.min(1.0, plugin.getConfig().getDouble("skyblockwars.destruction-rate", 0.08)));}
    private int destructionMaxSeconds() {return Math.max(10, plugin.getConfig().getInt("skyblockwars.destruction-max-seconds", 120));}

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
    public boolean allowLobbyPvp() {return plugin.getConfig().getBoolean("skyblockwars.lobby-duel", true);}

    @Override
    public void onStart(GameSession s)
    {
        announceLobbyWinner(s);
        SbwState st = ensureState(s);
        st.setStartedCount(s.players().size()); // с кем начали — чтобы соло-игра не завершалась мгновенно
        Epoch first = st.epochAt(0);
        s.broadcast("sbw.match-begin", Msg.ph("epoch", first != null ? first.getName() : "-"));
    }

    /**
     * Условие победы. «Последний выживший» побеждает ТОЛЬКО если матч начинали 2+
     * игрока. Соло-игра (форс-старт с одним) не заканчивается сама — игрок играет,
     * пока не выйдет или не проиграет (умрёт без якоря). Матч кончается, когда живых 0.
     */
    @Override
    public MatchResult checkResult(GameSession s)
    {
        int alive = s.aliveCount();
        if (alive == 0) {return MatchResult.draw();} // все выбыли/вышли — без победителя
        SbwState st = SbwState.of(s);
        int started = st != null ? st.startedCount() : s.players().size();
        if (started > 1 && alive == 1)
        {
            for (MatchPlayer mp : s.players()) {if (mp.isAlive()) {return MatchResult.of(mp.getUuid());}}
        }
        return null; // продолжаем
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
        giveKit(s, p);
        st.bossBar().add(p);
        st.bossBar().update(st, p, s.elapsedSeconds());
    }

    @Override
    public void onTick(GameSession s)
    {
        SbwState st = SbwState.of(s);
        if (st == null) {return;}
        int elapsed = s.elapsedSeconds();
        SbwState.MatchPhase computed = st.phaseAt(elapsed);
        if (computed != st.getMatchPhase()) {onPhaseChange(s, st, computed);}
        if (st.getMatchPhase() == SbwState.MatchPhase.DESTRUCTION)
        {
            tickDestruction(s, st);
            int destrElapsed = elapsed - (st.matchSeconds() + st.fightSeconds());
            if (destrElapsed >= destructionMaxSeconds() && s.alivePlayers().size() > 1) {suddenDeath(s);}
        }
        st.bossBar().updateAll(st, s.alivePlayers(), elapsed);
    }

    /** Переход фазы матча: обычная → схватка (слом всех блоков) → разрушение арены. */
    private void onPhaseChange(GameSession s, SbwState st, SbwState.MatchPhase to)
    {
        st.setMatchPhase(to);
        if (to == SbwState.MatchPhase.FIGHT) {enterFight(s, st);}
        else if (to == SbwState.MatchPhase.DESTRUCTION) {enterDestruction(s, st);}
    }

    /** Начало схватки: ломаем ВСЕ блоки возрождения, отключаем респавны, даём мягкое падение. */
    private void enterFight(GameSession s, SbwState st)
    {
        breakAllAnchors(st);
        for (Player p : s.alivePlayers())
        {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, false)); // 8с — не упасть в пустоту сразу
            p.showTitle(Title.title(Msg.get("sbw.fight-title"), Msg.get("sbw.fight-subtitle")));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1f);
        }
        s.broadcast("sbw.fight-begin");
    }

    /** Начало разрушения арены: поставленные игроками блоки начинают хаотично исчезать. */
    private void enterDestruction(GameSession s, SbwState st)
    {
        breakAllAnchors(st); // на случай fight-seconds=0 (схватку проскочили) — гарантированно без респавнов
        for (Player p : s.alivePlayers())
        {
            p.showTitle(Title.title(Msg.get("sbw.destruction-title"), Msg.get("sbw.destruction-subtitle")));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.9f, 1f);
        }
        s.broadcast("sbw.destruction-begin");
    }

    /** Сломать все блоки возрождения и отключить респавны (идемпотентно). */
    private void breakAllAnchors(SbwState st)
    {
        for (Location loc : st.ownerLocations()) {loc.getBlock().setType(Material.AIR, false);}
        st.clearOwners();
        for (SbwState.PlayerData pd : st.allPlayers())
        {
            pd.respawnAlive = false;
            pd.respawnBlock = null;
        }
    }

    /** Аварийное завершение затянувшегося разрушения: выбить всех, кроме одного — оставшийся победит. */
    private void suddenDeath(GameSession s)
    {
        List<Player> alive = new ArrayList<>(s.alivePlayers());
        for (int i = 0; i < alive.size() - 1; i++) {s.eliminate(alive.get(i), true);}
    }

    /** Каждую секунду фазы разрушения убираем случайную долю оставшихся поставленных блоков. */
    private void tickDestruction(GameSession s, SbwState st)
    {
        Set<Location> placed = st.placedBlocks();
        if (placed.isEmpty()) {return;}
        int toRemove = Math.max(1, (int) Math.ceil(placed.size() * destructionRate()));
        List<Location> list = new ArrayList<>(placed);
        Collections.shuffle(list);
        int done = 0;
        for (Location loc : list)
        {
            if (done >= toRemove) {break;}
            Block b = loc.getBlock();
            if (!b.getType().isAir()) {b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType());}
            b.setType(Material.AIR, false);
            st.removePlaced(loc);
            done++;
        }
    }

    @Override
    public boolean onLethalDamage(GameSession s, Player p)
    {
        SbwState st = SbwState.of(s);
        if (st == null) {return true;}
        dropInventory(s, p); // вещи выпадают при смерти
        SbwState.PlayerData pd = st.peek(p.getUniqueId());
        if (pd != null && pd.respawnAlive && pd.respawnBlock != null)
        {
            respawn(s, p, pd); // возрождение выдаёт свежий стартовый набор
            return false; // возродили — ядро не выбивает
        }
        return true; // якоря нет — выбывание (пустой спектатор, вещи уже выпали)
    }

    @Override
    public void onPlayerEliminated(GameSession s, MatchPlayer mp)
    {
        SbwState st = SbwState.of(s);
        if (st != null) {st.bossBar().remove(Bukkit.getPlayer(mp.getUuid()));}
    }

    @Override
    public void onPlayerRemoved(GameSession s, UUID id)
    {
        SbwState st = SbwState.of(s);
        if (st != null) {st.bossBar().remove(Bukkit.getPlayer(id));}
    }

    @Override
    public void onCleanup(GameSession s)
    {
        SbwState st = SbwState.of(s);
        if (st != null) {st.bossBar().clearAll();} // надёжно при любом завершении (в т.ч. форс-стоп/reload)
    }

    @Override
    public List<Component> scoreboardLines(GameSession s, Player viewer)
    {
        SbwState st = SbwState.of(s);
        if (st == null) {return List.of();}
        List<Component> lines = new ArrayList<>();
        int rem = st.phaseRemaining(s.elapsedSeconds());
        switch (st.getMatchPhase())
        {
            case NORMAL -> lines.add(Msg.get("sbw.sidebar-phase-normal", Msg.ph("time", formatTime(rem))));
            case FIGHT -> lines.add(Msg.get("sbw.sidebar-phase-fight", Msg.ph("time", formatTime(rem))));
            case DESTRUCTION -> lines.add(Msg.get("sbw.sidebar-phase-destruction"));
        }
        // Персональный прогресс эпохи — в боссбаре (viewer тут == null); общий (SHARED) дублируем в сайдбар.
        if (st.mode() == EpochMode.SHARED)
        {
            Epoch epoch = st.epochAt(st.sharedEpochIndex());
            int prog = st.sharedProgress();
            int goal = epoch != null ? epoch.getThreshold() : 0;
            lines.add(Msg.get("sbw.sidebar-epoch", Msg.ph("epoch", epoch != null ? epoch.getName() : "-")));
            lines.add(goal > 0
                ? Msg.get("sbw.sidebar-progress", Msg.ph("prog", prog), Msg.ph("goal", goal))
                : Msg.get("sbw.sidebar-progress-final", Msg.ph("prog", prog)));
        }
        return lines;
    }

    private static String formatTime(int seconds)
    {
        int sec = Math.max(0, seconds);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    // ===== ломание блоков (зовётся из SbwListener) =====

    /** Игрок сломал СВОЙ блок: добыча + прогресс к рубежу + рефилл случайным блоком эпохи. */
    public void mineOwnBlock(GameSession s, SbwState st, Player p, Block block)
    {
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType()); // видимый «слом»: частицы + звук
        giveYield(s, p, block);
        boolean advanced = advanceProgress(s, st, p);
        Epoch epoch = st.currentEpoch(p.getUniqueId());
        placeRespawnBlock(s, block.getLocation(), epoch != null ? epoch.pickRandom() : null);
        st.bossBar().update(st, p, s.elapsedSeconds());
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
        // Событие отменено в SbwListener — убираем блок сами, БЕЗ ванильного дропа, чтобы лут
        // контейнера-якоря не «утекал» несобираемыми предметами (их откат не ведёт).
        s.rememberBlock(block);
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        block.setType(Material.AIR, false);
        Player ownerP = Bukkit.getPlayer(owner);
        String ownerName = ownerP != null ? ownerP.getName() : owner.toString().substring(0, 8);
        s.broadcast("sbw.block-destroyed", Msg.ph("breaker", breaker.getName()), Msg.ph("victim", ownerName));
        if (ownerP != null)
        {
            ownerP.showTitle(Title.title(Msg.get("sbw.your-block-title"), Msg.get("sbw.your-block-subtitle")));
            ownerP.playSound(ownerP.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
        }
    }

    // ===== лобби-дуэль (разминка без последствий) =====

    /** Урон в лобби: считаем нанесённый игроками урон и НЕ даём умереть (авто-хил). Из SbwListener. */
    public void lobbyDamage(GameSession s, Player victim, EntityDamageEvent e)
    {
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID)
        {
            e.setCancelled(true);
            if (s.arena().getLobby() != null) {victim.teleport(s.arena().getLobby());}
            fullHeal(victim);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player attacker
            && !attacker.equals(victim) && plugin.arenas().sessionOf(attacker) == s)
        {
            double total = lobbyTally(s).merge(attacker.getUniqueId(), e.getFinalDamage(), Double::sum);
            attacker.sendActionBar(Msg.get("sbw.lobby-damage", Msg.ph("dmg", oneDecimal(total))));
        }
        if (victim.getHealth() - e.getFinalDamage() <= 0.5) // без смертей — авто-хил перед гибелью
        {
            e.setCancelled(true);
            fullHeal(victim);
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 1f);
        }
    }

    private void announceLobbyWinner(GameSession s)
    {
        Map<UUID, Double> tally = lobbyTally(s);
        var top = tally.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (top != null && top.getValue() > 0)
        {
            Player tp = Bukkit.getPlayer(top.getKey());
            s.broadcast("sbw.lobby-winner", Msg.ph("player", tp != null ? tp.getName() : "?"), Msg.ph("dmg", oneDecimal(top.getValue())));
        }
        s.data().remove("sbw.lobbydmg");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Double> lobbyTally(GameSession s)
    {
        return (Map<UUID, Double>) s.data().computeIfAbsent("sbw.lobbydmg", k -> new HashMap<UUID, Double>());
    }

    private static String oneDecimal(double v) {return String.format("%.1f", v);}

    // ===== helpers =====

    private SbwState ensureState(GameSession s)
    {
        SbwState st = SbwState.of(s);
        if (st == null)
        {
            ArenaGameConfig cfg = config(s.arena().getId());
            st = new SbwState(cfg.getEpochMode(), resolveEpochs(cfg), cfg.getMatchSeconds(), cfg.getFightSeconds());
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
                st.bossBar().updateAll(st, s.alivePlayers(), s.elapsedSeconds());
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

    /** Добыча блока ДРОПОМ у игрока (видимый предмет, авто-подбор): контейнер отдаёт лут, обычный блок — сам себя. */
    private void giveYield(GameSession s, Player p, Block block)
    {
        if (block.getState() instanceof Container c)
        {
            for (ItemStack it : c.getInventory().getContents())
            {
                if (it != null && !it.getType().isAir()) {dropTracked(s, p.getLocation(), it.clone());}
            }
        }
        else
        {
            dropTracked(s, p.getLocation(), new ItemStack(block.getType()));
        }
    }

    /** Уронить предмет с учётом отката матча (удалится в cleanup, если не подобрали). */
    private void dropTracked(GameSession s, Location loc, ItemStack item)
    {
        if (item == null || item.getType().isAir()) {return;}
        s.trackEntity(loc.getWorld().dropItemNaturally(loc, item));
    }

    /** Вещи выпадают при смерти (событие смерти отменено ядром — дропаем и чистим сами). */
    private void dropInventory(GameSession s, Player p)
    {
        Location loc = p.getLocation();
        var inv = p.getInventory();
        for (ItemStack it : inv.getStorageContents()) {if (it != null) {dropTracked(s, loc, it.clone());}}
        for (ItemStack it : inv.getArmorContents()) {if (it != null) {dropTracked(s, loc, it.clone());}}
        dropTracked(s, loc, inv.getItemInOffHand().clone());
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        inv.setItemInOffHand(null);
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

    private void respawn(GameSession s, Player p, SbwState.PlayerData pd)
    {
        Block anchor = pd.respawnBlock.getBlock();
        if (anchor.getType().isAir()) // якорь мог осыпаться (гравитационный блок) — восстановим опору
        {
            s.rememberBlock(anchor);
            anchor.setType(Material.STONE, false);
        }
        Location to = pd.respawnBlock.clone().add(0.5, 1.0, 0.5);
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        p.teleport(to);
        fullHeal(p);
        giveKit(s, p); // после дропа вещей на смерти — свежий стартовый набор
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

    /** Выдать стартовый набор арены (из GUI-редактора) либо дефолтный, если не настроен. */
    private void giveKit(GameSession s, Player p)
    {
        Map<Integer, ItemStack> kit = config(s.arena().getId()).kit();
        if (kit.isEmpty()) {giveDefaultKit(p); return;}
        for (Map.Entry<Integer, ItemStack> e : kit.entrySet())
        {
            ItemStack it = e.getValue().clone();
            if (tryEquipArmor(p, it)) {continue;} // броню из набора авто-надеваем
            int slot = e.getKey();
            if (slot >= 0 && slot < 36) {p.getInventory().setItem(slot, it);}
            else {p.getInventory().addItem(it);}
        }
    }

    private void giveDefaultKit(Player p)
    {
        p.getInventory().addItem(
            new ItemStack(Material.STONE_SWORD),
            new ItemStack(Material.STONE_PICKAXE),
            new ItemStack(Material.STONE_AXE),
            new ItemStack(Material.STONE_SHOVEL),
            new ItemStack(Material.COOKED_BEEF, 16));
    }

    private boolean tryEquipArmor(Player p, ItemStack it)
    {
        var inv = p.getInventory();
        String n = it.getType().name();
        if (n.endsWith("_HELMET") && isAir(inv.getHelmet())) {inv.setHelmet(it); return true;}
        if (n.endsWith("_CHESTPLATE") && isAir(inv.getChestplate())) {inv.setChestplate(it); return true;}
        if (n.endsWith("_LEGGINGS") && isAir(inv.getLeggings())) {inv.setLeggings(it); return true;}
        if (n.endsWith("_BOOTS") && isAir(inv.getBoots())) {inv.setBoots(it); return true;}
        return false;
    }

    private static boolean isAir(ItemStack it) {return it == null || it.getType().isAir();}

    /** Эпохи по умолчанию — чтобы игра работала до настройки арены (образец: 3 эпохи, есть контейнер). */
    public List<Epoch> defaultEpochs()
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
            case "epochs" -> {return cmdEpochs(p, args);}
            case "kit" -> {return cmdKit(p, args);}
            default -> {return false;}
        }
    }

    private boolean cmdEpochs(Player p, String[] args)
    {
        Arena arena = setupArena(p, args, "sbw.usage-epochs");
        if (arena == null) {return true;}
        new EpochListMenu(plugin, this, arena, config(arena.getId())).open(p);
        return true;
    }

    private boolean cmdKit(Player p, String[] args)
    {
        Arena arena = setupArena(p, args, "sbw.usage-kit");
        if (arena == null) {return true;}
        new KitEditorMenu(plugin, this, arena, config(arena.getId())).open(p);
        return true;
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
