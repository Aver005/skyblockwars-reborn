package ru.kiviuly.skyblockwars.sbw;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.skyblockwars.sbw.epoch.EpochBlock;

/**
 * Игро-специфичная конфигурация одной арены SkyBlockWars. Хранится ОТДЕЛЬНО от
 * ядрового {@code arenas/<id>.yml} — в {@code game/<ID>.yml}, чтобы не трогать
 * ядровую модель Arena. Здесь всё, что не влезает в неё: центр арены, радиус кольца
 * спавнов, режим эпох и сами эпохи (блоки с весами и вложенным лутом). Location/
 * ItemStack сериализуются нативно Bukkit-YAML, так что вложенные структуры (лут
 * контейнеров) переживают перезапуск.
 *
 * Кольцо спавнов при этом пишется в ЯДРОВОЙ {@code arena.getSpawns()} — спавны это
 * обобщённое понятие, их ведёт ядро; центр/радиус/эпохи — игро-специфика, их ведём мы.
 */
public class ArenaGameConfig
{
    /** Режим продвижения эпох: PERSONAL — у каждого своя цепочка; SHARED — общая на арену. */
    public enum EpochMode {PERSONAL, SHARED}

    private final String arenaId;
    private Location center;          // центр арены (/sbw setcenter)
    private int radius;               // радиус кольца спавнов
    private EpochMode epochMode;
    private int matchSeconds;         // длительность обычной фазы матча
    private int fightSeconds;         // длительность схватки после слома блоков
    private final List<Epoch> epochs = new ArrayList<>();
    private final Map<Integer, ItemStack> kit = new LinkedHashMap<>(); // стартовый набор (слот→предмет), пусто = дефолт

    private ArenaGameConfig(String arenaId) {this.arenaId = arenaId;}

    public static ArenaGameConfig load(String arenaId, File file, int defaultRadius, EpochMode defaultMode,
        int defaultMatchSeconds, int defaultFightSeconds)
    {
        ArenaGameConfig c = new ArenaGameConfig(arenaId);
        c.radius = Math.max(1, defaultRadius);
        c.epochMode = defaultMode;
        c.matchSeconds = Math.max(1, defaultMatchSeconds);
        c.fightSeconds = Math.max(0, defaultFightSeconds);
        if (file.exists())
        {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            c.center = y.getLocation("center");
            c.radius = Math.max(1, y.getInt("radius", c.radius));
            c.epochMode = parseMode(y.getString("epoch-mode"), defaultMode);
            c.matchSeconds = Math.max(1, y.getInt("match-seconds", c.matchSeconds));
            c.fightSeconds = Math.max(0, y.getInt("fight-seconds", c.fightSeconds));
            loadEpochs(c, y);
            loadKit(c, y);
        }
        return c;
    }

    public void save(File file)
    {
        YamlConfiguration y = new YamlConfiguration();
        y.set("center", center);
        y.set("radius", radius);
        y.set("epoch-mode", epochMode.name());
        y.set("match-seconds", matchSeconds);
        y.set("fight-seconds", fightSeconds);
        saveEpochs(y);
        saveKit(y);
        try {file.getParentFile().mkdirs(); y.save(file);}
        catch (IOException e) {throw new RuntimeException("Failed to save SBW game config " + arenaId, e);}
    }

    // ===== эпохи (persistence) =====

    private static void loadEpochs(ArenaGameConfig c, YamlConfiguration y)
    {
        ConfigurationSection es = y.getConfigurationSection("epochs");
        if (es == null) {return;}
        for (String key : sortedNumeric(es))
        {
            ConfigurationSection sec = es.getConfigurationSection(key);
            if (sec == null) {continue;}
            Epoch epoch = new Epoch(sec.getString("name", "Epoch"), sec.getInt("threshold", -1));
            ConfigurationSection bs = sec.getConfigurationSection("blocks");
            if (bs != null)
            {
                for (String bk : sortedNumeric(bs))
                {
                    ConfigurationSection bsec = bs.getConfigurationSection(bk);
                    if (bsec == null) {continue;}
                    Material mat = Material.matchMaterial(bsec.getString("type", ""));
                    if (mat == null || mat.isAir()) {continue;}
                    EpochBlock block = new EpochBlock(mat, bsec.getInt("weight", 1));
                    ConfigurationSection cs = bsec.getConfigurationSection("contents");
                    if (cs != null)
                    {
                        for (String slot : cs.getKeys(false))
                        {
                            ItemStack it = cs.getItemStack(slot);
                            if (it == null) {continue;}
                            try {block.getContents().put(Integer.parseInt(slot), it);}
                            catch (NumberFormatException ignored) {}
                        }
                    }
                    epoch.getBlocks().add(block);
                }
            }
            c.epochs.add(epoch);
        }
    }

    private void saveEpochs(YamlConfiguration y)
    {
        if (epochs.isEmpty()) {return;}
        ConfigurationSection es = y.createSection("epochs");
        for (int i = 0; i < epochs.size(); i++)
        {
            Epoch epoch = epochs.get(i);
            ConfigurationSection sec = es.createSection(String.valueOf(i));
            sec.set("name", epoch.getName());
            sec.set("threshold", epoch.getThreshold());
            List<EpochBlock> blocks = epoch.getBlocks();
            ConfigurationSection bs = sec.createSection("blocks");
            for (int j = 0; j < blocks.size(); j++)
            {
                EpochBlock block = blocks.get(j);
                ConfigurationSection bsec = bs.createSection(String.valueOf(j));
                bsec.set("type", block.getMaterial().name());
                bsec.set("weight", block.getWeight());
                if (block.hasContents())
                {
                    ConfigurationSection cs = bsec.createSection("contents");
                    for (var e : block.getContents().entrySet()) {cs.set(String.valueOf(e.getKey()), e.getValue());}
                }
            }
        }
    }

    // ===== стартовый набор (kit) =====

    private static void loadKit(ArenaGameConfig c, YamlConfiguration y)
    {
        ConfigurationSection ks = y.getConfigurationSection("kit");
        if (ks == null) {return;}
        for (String slot : ks.getKeys(false))
        {
            ItemStack it = ks.getItemStack(slot);
            if (it == null) {continue;}
            try {c.kit.put(Integer.parseInt(slot), it);}
            catch (NumberFormatException ignored) {}
        }
    }

    private void saveKit(YamlConfiguration y)
    {
        if (kit.isEmpty()) {return;}
        ConfigurationSection ks = y.createSection("kit");
        for (Map.Entry<Integer, ItemStack> e : kit.entrySet()) {ks.set(String.valueOf(e.getKey()), e.getValue());}
    }

    /** Ключи секции, отсортированные как числа (0,1,2,…) — порядок эпох/блоков стабилен. */
    private static List<String> sortedNumeric(ConfigurationSection sec)
    {
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort(Comparator.comparingInt(ArenaGameConfig::parseIntSafe));
        return keys;
    }

    private static int parseIntSafe(String s)
    {
        try {return Integer.parseInt(s);}
        catch (NumberFormatException e) {return Integer.MAX_VALUE;}
    }

    private static EpochMode parseMode(String raw, EpochMode def)
    {
        if (raw == null) {return def;}
        try {return EpochMode.valueOf(raw.toUpperCase(Locale.ROOT));}
        catch (IllegalArgumentException e) {return def;}
    }

    // ===== accessors =====

    public String arenaId() {return arenaId;}
    public Location getCenter() {return center;}
    public void setCenter(Location center) {this.center = center;}
    public int getRadius() {return radius;}
    public void setRadius(int radius) {this.radius = Math.max(1, radius);}
    public EpochMode getEpochMode() {return epochMode;}
    public void setEpochMode(EpochMode epochMode) {this.epochMode = epochMode;}
    public int getMatchSeconds() {return matchSeconds;}
    public void setMatchSeconds(int matchSeconds) {this.matchSeconds = Math.max(1, matchSeconds);}
    public int getFightSeconds() {return fightSeconds;}
    public void setFightSeconds(int fightSeconds) {this.fightSeconds = Math.max(0, fightSeconds);}

    /** Изменяемый список эпох арены (порядок = последовательность эпох). */
    public List<Epoch> epochs() {return epochs;}

    /** Изменяемый стартовый набор (слот 0..35 → предмет). Пусто = дефолтный набор. */
    public Map<Integer, ItemStack> kit() {return kit;}
}
