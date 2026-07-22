package ru.kiviuly.skyblockwars.sbw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import ru.kiviuly.skyblockwars.game.GameSession;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig.EpochMode;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;

/**
 * Состояние матча SkyBlockWars: живёт в {@link GameSession#data()} под ключом
 * {@link #KEY} (не в полях {@link ru.kiviuly.skyblockwars.game.Minigame} — тех одна на
 * все матчи). Держит режим/эпохи матча, per-player прогресс и блок возрождения,
 * общий прогресс (SHARED), карту «блок → чей якорь», фазу матча
 * (обычная → схватка → разрушение) и множество поставленных игроками блоков.
 */
public class SbwState
{
    public static final String KEY = "sbw.state";

    /** Фаза матча поверх ядрового RUNNING. */
    public enum MatchPhase {NORMAL, FIGHT, DESTRUCTION}

    /** Per-player состояние в матче. */
    public static final class PlayerData
    {
        public Location respawnBlock;       // блок возрождения игрока (null = потерян/сломан)
        public boolean respawnAlive = true; // цел ли якорь (можно ли возродиться)
        public int epochIndex = 0;          // текущая эпоха (режим PERSONAL)
        public int progress = 0;            // прогресс к рубежу (режим PERSONAL)
    }

    private final EpochMode mode;
    private final List<Epoch> epochs;
    private final int matchSeconds;
    private final int fightSeconds;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<Location, UUID> blockOwners = new HashMap<>();
    private final Set<Location> placedBlocks = new LinkedHashSet<>();
    private final EpochBossBar bossBar = new EpochBossBar(); // per-match — не в полях Minigame
    private int sharedEpochIndex = 0;
    private int sharedProgress = 0;
    private MatchPhase matchPhase = MatchPhase.NORMAL;

    public SbwState(EpochMode mode, List<Epoch> epochs, int matchSeconds, int fightSeconds)
    {
        this.mode = mode;
        this.epochs = epochs;
        this.matchSeconds = Math.max(1, matchSeconds);
        this.fightSeconds = Math.max(0, fightSeconds);
    }

    public static SbwState of(GameSession s) {return (SbwState) s.data().get(KEY);}

    public EpochMode mode() {return mode;}
    public List<Epoch> epochs() {return epochs;}
    public EpochBossBar bossBar() {return bossBar;}

    public PlayerData player(UUID id) {return players.computeIfAbsent(id, k -> new PlayerData());}
    public PlayerData peek(UUID id) {return players.get(id);}
    public Collection<PlayerData> allPlayers() {return players.values();}

    // ===== карта владельцев блоков возрождения =====

    public UUID ownerOf(Location loc) {return blockOwners.get(key(loc));}
    public void setOwner(Location loc, UUID id) {blockOwners.put(key(loc), id);}
    public void removeOwner(Location loc) {blockOwners.remove(key(loc));}
    public Collection<Location> ownerLocations() {return new ArrayList<>(blockOwners.keySet());}
    public void clearOwners() {blockOwners.clear();}

    // ===== поставленные игроками блоки (для фазы разрушения) =====

    public void addPlaced(Location loc) {placedBlocks.add(key(loc));}
    public void removePlaced(Location loc) {placedBlocks.remove(key(loc));}
    public Set<Location> placedBlocks() {return placedBlocks;}

    // ===== общий прогресс (SHARED) =====

    public int sharedEpochIndex() {return sharedEpochIndex;}
    public void setSharedEpochIndex(int i) {this.sharedEpochIndex = i;}
    public int sharedProgress() {return sharedProgress;}
    public void setSharedProgress(int p) {this.sharedProgress = p;}

    // ===== фаза матча =====

    public MatchPhase getMatchPhase() {return matchPhase;}
    public void setMatchPhase(MatchPhase phase) {this.matchPhase = phase;}
    public int matchSeconds() {return matchSeconds;}
    public int fightSeconds() {return fightSeconds;}

    /** Какая фаза соответствует прошедшему времени матча. */
    public MatchPhase phaseAt(int elapsed)
    {
        if (elapsed < matchSeconds) {return MatchPhase.NORMAL;}
        if (elapsed < matchSeconds + fightSeconds) {return MatchPhase.FIGHT;}
        return MatchPhase.DESTRUCTION;
    }

    /** Секунд до конца текущей фазы (NORMAL/FIGHT); -1 в DESTRUCTION. */
    public int phaseRemaining(int elapsed)
    {
        if (elapsed < matchSeconds) {return matchSeconds - elapsed;}
        if (elapsed < matchSeconds + fightSeconds) {return matchSeconds + fightSeconds - elapsed;}
        return -1;
    }

    // ===== текущая эпоха с учётом режима =====

    public int epochIndexFor(UUID id) {return mode == EpochMode.SHARED ? sharedEpochIndex : player(id).epochIndex;}
    public int progressFor(UUID id) {return mode == EpochMode.SHARED ? sharedProgress : player(id).progress;}

    public Epoch epochAt(int index)
    {
        if (epochs.isEmpty()) {return null;}
        return epochs.get(Math.max(0, Math.min(index, epochs.size() - 1)));
    }

    public Epoch currentEpoch(UUID id) {return epochAt(epochIndexFor(id));}

    /** Нормализованный ключ по координатам блока (без yaw/pitch/дробей). */
    private static Location key(Location loc)
    {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
