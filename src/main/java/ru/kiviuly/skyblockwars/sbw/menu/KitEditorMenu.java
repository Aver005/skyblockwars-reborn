package ru.kiviuly.skyblockwars.sbw.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.skyblockwars.arena.Arena;
import ru.kiviuly.skyblockwars.menu.Menu;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig;
import ru.kiviuly.skyblockwars.sbw.SkyBlockWarsGame;
import ru.kiviuly.skyblockwars.util.Msg;

/**
 * Редактор стартового набора арены: реальный инвентарь на 36 слотов — что положишь,
 * то игроки и получат на старте (и после возрождения). Броня в наборе авто-надевается
 * при выдаче. Пустой набор = дефолтный кит. Сохраняется по закрытию.
 */
public class KitEditorMenu extends Menu
{
    private static final int SIZE = 36;

    private final SkyBlockWarsPlugin plugin;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private final SkyBlockWarsGame game;

    public KitEditorMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena, ArenaGameConfig cfg)
    {
        super(SIZE, Msg.get("sbwgui.kit-title"));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
        for (var e : cfg.kit().entrySet())
        {
            int slot = e.getKey();
            if (slot >= 0 && slot < SIZE) {inventory.setItem(slot, e.getValue().clone());}
        }
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public void onClick(InventoryClickEvent e) {} // свободное редактирование; сохраняем по закрытию

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        cfg.kit().clear();
        for (int i = 0; i < SIZE; i++)
        {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) {cfg.kit().put(i, it.clone());}
        }
        game.saveConfig(cfg);
        if (e.getPlayer() instanceof Player p) {Msg.send(p, "sbw.kit-saved", Msg.ph("arena", arena.getId()));}
    }
}
