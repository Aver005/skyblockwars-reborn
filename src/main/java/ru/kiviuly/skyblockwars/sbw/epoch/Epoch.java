package ru.kiviuly.skyblockwars.sbw.epoch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Эпоха арены: набор взвешенных блоков ({@link EpochBlock}) + рубеж. Рубеж — сколько
 * СВОИХ блоков игрок должен сломать, чтобы эпоха закончилась и наступила следующая.
 * {@code threshold <= 0} = рубежа нет: на этой эпохе игрок остаётся до конца матча
 * (так же ведёт себя последняя эпоха в последовательности).
 */
public class Epoch
{
    private String name;
    private int threshold;   // рубеж; <=0 = без рубежа (остаться до конца)
    private final List<EpochBlock> blocks = new ArrayList<>();

    public Epoch(String name, int threshold)
    {
        this.name = name;
        this.threshold = threshold;
    }

    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    public int getThreshold() {return threshold;}
    public void setThreshold(int threshold) {this.threshold = threshold;}
    public List<EpochBlock> getBlocks() {return blocks;}

    /** Есть ли конечный рубеж (иначе эпоха бесконечная — до конца матча). */
    public boolean hasThreshold() {return threshold > 0;}

    public int totalWeight()
    {
        int sum = 0;
        for (EpochBlock b : blocks) {sum += Math.max(1, b.getWeight());}
        return sum;
    }

    /** Взвешенно-случайный блок эпохи; {@code null}, если блоков нет. */
    public EpochBlock pickRandom()
    {
        int total = totalWeight();
        if (total <= 0 || blocks.isEmpty()) {return null;}
        int r = ThreadLocalRandom.current().nextInt(total);
        for (EpochBlock b : blocks)
        {
            r -= Math.max(1, b.getWeight());
            if (r < 0) {return b;}
        }
        return blocks.get(blocks.size() - 1);
    }
}
