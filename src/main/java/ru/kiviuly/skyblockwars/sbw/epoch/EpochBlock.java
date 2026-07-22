package ru.kiviuly.skyblockwars.sbw.epoch;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Один взвешенный вариант блока внутри эпохи. Обычный блок — {@code material} + вес.
 * Контейнер (сундук/бочка/…) дополнительно несёт вложенный лут {@code contents}
 * (слот → предмет), который кладётся внутрь при спавне блока. Вес — относительная
 * вероятность появления среди блоков эпохи.
 */
public class EpochBlock
{
    private Material material;
    private int weight;
    private final Map<Integer, ItemStack> contents = new LinkedHashMap<>(); // слот -> предмет (для контейнеров)

    public EpochBlock(Material material, int weight)
    {
        this.material = material;
        this.weight = Math.max(1, weight);
    }

    public Material getMaterial() {return material;}
    public void setMaterial(Material material) {this.material = material;}
    public int getWeight() {return weight;}
    public void setWeight(int weight) {this.weight = Math.max(1, weight);}

    /** Изменяемая мапа слот→предмет для вложенного лута (пусто = без лута). */
    public Map<Integer, ItemStack> getContents() {return contents;}

    public boolean isContainer() {return Containers.isContainer(material);}
    public boolean hasContents() {return !contents.isEmpty();}

    /** Глубокая копия (материал + вес + клонированный вложенный лут) — для копирования блоков между эпохами. */
    public EpochBlock copy()
    {
        EpochBlock c = new EpochBlock(material, weight);
        for (Map.Entry<Integer, ItemStack> e : contents.entrySet()) {c.contents.put(e.getKey(), e.getValue().clone());}
        return c;
    }
}
