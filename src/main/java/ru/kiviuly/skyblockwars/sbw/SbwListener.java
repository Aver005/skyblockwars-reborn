package ru.kiviuly.skyblockwars.sbw;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.skyblockwars.game.GamePhase;
import ru.kiviuly.skyblockwars.game.GameSession;

/**
 * Игро-специфичные события SkyBlockWars. Пока — ломание блоков возрождения:
 *  - свой блок  → добыча + прогресс к рубежу + рефилл случайным блоком эпохи;
 *  - чужой блок → уничтожение якоря возрождения владельца.
 * Обычные блоки (мосты/постройки) не трогаем — их откат ведёт ядро.
 * Приоритет HIGH: ядровой {@code GameListener.onBreak} (NORMAL) уже запомнил блок для отката.
 */
public class SbwListener implements Listener
{
    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;

    public SbwListener(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game)
    {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e)
    {
        Player p = e.getPlayer();
        GameSession s = plugin.arenas().sessionOf(p);
        if (s == null || s.phase() != GamePhase.RUNNING) {return;}
        SbwState st = SbwState.of(s);
        if (st == null) {return;}
        UUID owner = st.ownerOf(e.getBlock().getLocation());
        if (owner == null) {return;} // обычный блок — ядро откатит, игровой логики нет
        if (owner.equals(p.getUniqueId()))
        {
            e.setCancelled(true); // свой блок ломаем сами (добыча + рефилл), ваниль не нужна
            game.mineOwnBlock(s, st, p, e.getBlock());
        }
        else
        {
            game.destroyEnemyBlock(s, st, p, owner, e.getBlock()); // чужой якорь — ломается ванильно (дроп атакующему)
        }
    }

    /** Запомнить блоки, поставленные игроками — на фазе разрушения они хаотично исчезают. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e)
    {
        GameSession s = plugin.arenas().sessionOf(e.getPlayer());
        if (s == null || s.phase() != GamePhase.RUNNING) {return;}
        SbwState st = SbwState.of(s);
        if (st != null) {st.addPlaced(e.getBlock().getLocation());}
    }

    /**
     * Лобби-дуэль: в лобби/отсчёте считаем нанесённый урон и не даём умереть (авто-хил).
     * Приоритет HIGH после ядрового GameListener (который не гасит урон при allowLobbyPvp).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLobbyDamage(EntityDamageEvent e)
    {
        if (!(e.getEntity() instanceof Player victim)) {return;}
        GameSession s = plugin.arenas().sessionOf(victim);
        if (s == null || s.phase() == GamePhase.RUNNING || s.phase() == GamePhase.ENDING) {return;}
        game.lobbyDamage(s, victim, e);
    }
}
