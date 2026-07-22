package ru.kiviuly.skyblockwars.sbw;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import ru.kiviuly.skyblockwars.game.GameSession;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig.EpochMode;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;

/**
 * Состояние матча SkyBlockWars: живёт в {@link GameSession#data()} под ключом
 * {@link #KEY} (не в полях {@link ru.kiviuly.skyblockwars.game.Minigame} — тех одна на
 * все матчи). Держит режим и список эпох матча, per-player прогресс/блок возрождения,
 * общий прогресс (SHARED) и карту «блок → чей это блок возрождения».
 */
public class SbwState
{
    public static final String KEY = "sbw.state";

    /** Per-player состояние в матче. */
    public static final class PlayerData
    {
        public Location respawnBlock;      // блок возрождения игрока (null = потерян/сломан)
        public boolean respawnAlive = true; // цел ли якорь (можно ли возродиться)
        public int epochIndex = 0;          // текущая эпоха (режим PERSONAL)
        public int progress = 0;            // прогресс к рубежу (режим PERSONAL)
    }

    private final EpochMode mode;
    private final List<Epoch> epochs;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<Location, UUID> blockOwners = new HashMap<>();
    private int sharedEpochIndex = 0;
    private int sharedProgress = 0;

    public SbwState(EpochMode mode, List<Epoch> epochs)
    {
        this.mode = mode;
        this.epochs = epochs;
    }

    public static SbwState of(GameSession s) {return (SbwState) s.data().get(KEY);}

    public EpochMode mode() {return mode;}
    public List<Epoch> epochs() {return epochs;}

    public PlayerData player(UUID id) {return players.computeIfAbsent(id, k -> new PlayerData());}
    public PlayerData peek(UUID id) {return players.get(id);}

    // ===== карта владельцев блоков возрождения =====

    public UUID ownerOf(Location loc) {return blockOwners.get(key(loc));}
    public void setOwner(Location loc, UUID id) {blockOwners.put(key(loc), id);}
    public void removeOwner(Location loc) {blockOwners.remove(key(loc));}

    // ===== общий прогресс (SHARED) =====

    public int sharedEpochIndex() {return sharedEpochIndex;}
    public void setSharedEpochIndex(int i) {this.sharedEpochIndex = i;}
    public int sharedProgress() {return sharedProgress;}
    public void setSharedProgress(int p) {this.sharedProgress = p;}

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
