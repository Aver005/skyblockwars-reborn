package ru.kiviuly.skyblockwars.sbw.epoch;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;

/**
 * Какие блоки — контейнеры (можно вложить лут: сундуки/бочки/шалкеры/раздатчики).
 * Ядру это не нужно — чисто игро-специфичный справочник SkyBlockWars.
 */
public final class Containers
{
    private static final Set<Material> CONTAINERS;

    static
    {
        Set<Material> s = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.DISPENSER, Material.DROPPER, Material.HOPPER);
        for (Material m : Material.values())
        {
            if (m.name().endsWith("SHULKER_BOX")) {s.add(m);}
        }
        CONTAINERS = s;
    }

    private Containers() {}

    public static boolean isContainer(Material m) {return m != null && CONTAINERS.contains(m);}

    /** Размер инвентаря контейнера в слотах (для GUI-редактора лута). */
    public static int size(Material m)
    {
        if (m == Material.HOPPER) {return 5;}
        if (m == Material.DISPENSER || m == Material.DROPPER) {return 9;}
        return 27; // сундук / бочка / шалкер
    }
}
