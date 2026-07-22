package ru.kiviuly.skyblockwars.sbw.epoch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Эпоха арены: набор взвешенных блоков ({@link EpochBlock}) + рубеж. Рубеж — сколько
 * СВОИХ блоков игрок должен сломать, чтобы эпоха закончилась и наступила следующая.
 * {@code threshold <= 0} = рубежа нет: на этой эпохе игрок остаётся до конца матча
 * (так же ведёт себя последняя эпоха в последовательности).
 *
 * У эпохи есть стабильный {@link #id()} (переживает переименование/переупорядочивание)
 * и список наследуемых эпох {@link #getInherits()} — их id. Наследование даёт эпохе
 * блоки других эпох, НЕ храня их у себя: при спавне блока берётся объединённый список
 * (свои + унаследованные, транзитивно, с защитой от циклов) — см. {@link #pickRandom(List)}.
 */
public class Epoch
{
    private String id;
    private String name;
    private int threshold;   // рубеж; <=0 = без рубежа (остаться до конца)
    private final List<EpochBlock> blocks = new ArrayList<>();
    private final List<String> inherits = new ArrayList<>(); // id эпох, чьи блоки подмешиваются

    public Epoch(String name, int threshold)
    {
        this(UUID.randomUUID().toString(), name, threshold);
    }

    public Epoch(String id, String name, int threshold)
    {
        this.id = id;
        this.name = name;
        this.threshold = threshold;
    }

    public String id() {return id;}
    public void setId(String id) {this.id = id;}
    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    public int getThreshold() {return threshold;}
    public void setThreshold(int threshold) {this.threshold = threshold;}
    public List<EpochBlock> getBlocks() {return blocks;}

    /** Изменяемый список id наследуемых эпох (их блоки подмешиваются, не храня у себя). */
    public List<String> getInherits() {return inherits;}

    /** Есть ли конечный рубеж (иначе эпоха бесконечная — до конца матча). */
    public boolean hasThreshold() {return threshold > 0;}

    // ===== выбор блока =====

    /** Взвешенно-случайный блок ТОЛЬКО из своих блоков; {@code null}, если их нет. */
    public EpochBlock pickRandom() {return pickFrom(blocks);}

    /**
     * Взвешенно-случайный блок из ОБЪЕДИНЁННОГО списка (свои + унаследованные эпохи из
     * {@code all}, транзитивно, каждая эпоха учитывается один раз). {@code null}, если пусто.
     */
    public EpochBlock pickRandom(List<Epoch> all) {return pickFrom(effectiveBlocks(all));}

    /** Свои блоки + блоки всех наследуемых эпох (транзитивно, без дублей/циклов). */
    public List<EpochBlock> effectiveBlocks(List<Epoch> all)
    {
        List<EpochBlock> out = new ArrayList<>();
        collect(all, out, new HashSet<>());
        return out;
    }

    private void collect(List<Epoch> all, List<EpochBlock> out, Set<String> visited)
    {
        if (!visited.add(id)) {return;} // цикл или повторная эпоха — пропускаем
        out.addAll(blocks);
        for (String ref : inherits)
        {
            Epoch e = byId(all, ref);
            if (e != null) {e.collect(all, out, visited);}
        }
    }

    private static Epoch byId(List<Epoch> all, String id)
    {
        if (all == null) {return null;}
        for (Epoch e : all) {if (e.id.equals(id)) {return e;}}
        return null;
    }

    public int totalWeight() {return totalWeight(blocks);}

    private static int totalWeight(List<EpochBlock> list)
    {
        int sum = 0;
        for (EpochBlock b : list) {sum += Math.max(1, b.getWeight());}
        return sum;
    }

    private static EpochBlock pickFrom(List<EpochBlock> list)
    {
        int total = totalWeight(list);
        if (total <= 0 || list.isEmpty()) {return null;}
        int r = ThreadLocalRandom.current().nextInt(total);
        for (EpochBlock b : list)
        {
            r -= Math.max(1, b.getWeight());
            if (r < 0) {return b;}
        }
        return list.get(list.size() - 1);
    }
}
