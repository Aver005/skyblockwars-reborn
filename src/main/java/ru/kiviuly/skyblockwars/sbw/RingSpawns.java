package ru.kiviuly.skyblockwars.sbw;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Расстановка спавнов по окружности вокруг центра арены. Точки равноудалены друг от
 * друга и от центра, повёрнуты лицом к центру. Возвращает БЛОЧНЫЕ координаты (целые):
 * ядро при старте матча само добавляет +0.5 по X/Z, центрируя игрока на блоке.
 */
public final class RingSpawns
{
    private RingSpawns() {}

    /**
     * {@code count} точек на окружности радиуса {@code radius} вокруг {@code center}
     * (на высоте центра). Пусто, если центр/мир не заданы или параметры невалидны.
     */
    public static List<Location> ring(Location center, int radius, int count)
    {
        List<Location> out = new ArrayList<>();
        if (center == null || center.getWorld() == null || count <= 0 || radius <= 0) {return out;}
        World world = center.getWorld();
        int by = center.getBlockY();
        for (int i = 0; i < count; i++)
        {
            double angle = 2.0 * Math.PI * i / count;
            int bx = (int) Math.floor(center.getX() + radius * Math.cos(angle));
            int bz = (int) Math.floor(center.getZ() + radius * Math.sin(angle));
            Location spawn = new Location(world, bx, by, bz);
            // повернуть игрока лицом к центру (по центру блока, чтобы yaw был точным)
            Vector dir = center.toVector().subtract(new Vector(bx + 0.5, by, bz + 0.5));
            if (dir.lengthSquared() > 1.0e-6) {spawn.setDirection(dir);}
            out.add(spawn);
        }
        return out;
    }
}
