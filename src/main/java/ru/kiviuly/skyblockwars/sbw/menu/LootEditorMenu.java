package ru.kiviuly.skyblockwars.sbw.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.menu.Menu;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig;
import ru.kiviuly.skyblockwars.sbw.SkyBlockWarsGame;
import ru.kiviuly.skyblockwars.sbw.epoch.Containers;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.skyblockwars.sbw.epoch.EpochBlock;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Полный GUI-редактор лута контейнера (выбран вариант «полный редактор»): открывается
 * реальный инвентарь по размеру контейнера — что положишь, то и появится внутри блока
 * при спавне. Сохраняется по закрытию. Слоты сверх размера контейнера заблокированы.
 */
public class LootEditorMenu extends Menu
{
    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private final Epoch epoch;
    private final EpochBlock block;
    private final int validSlots;

    public LootEditorMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena,
        ArenaGameConfig cfg, Epoch epoch, EpochBlock block)
    {
        super(editorSize(block), Msg.get("sbwgui.loot-title"));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
        this.epoch = epoch;
        this.block = block;
        this.validSlots = Math.min(Containers.size(block.getMaterial()), inventory.getSize());
        render();
    }

    /** Размер редактора кратен 9 (правило инвентаря): под контейнер округляем вверх до 9/18/27. */
    private static int editorSize(EpochBlock b)
    {
        int size = Containers.size(b.getMaterial());
        return size <= 9 ? 9 : (size <= 18 ? 18 : 27);
    }

    private void render()
    {
        for (int i = validSlots; i < inventory.getSize(); i++)
        {
            inventory.setItem(i, Items.named(Material.BARRIER, Msg.get("sbwgui.loot-locked")));
        }
        for (var e : block.getContents().entrySet())
        {
            int slot = e.getKey();
            if (slot >= 0 && slot < validSlots) {inventory.setItem(slot, e.getValue().clone());}
        }
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public boolean isProtectedSlot(int slot) {return slot >= validSlots;}

    @Override
    public void onClick(InventoryClickEvent e) {} // свободное редактирование; сохраняем по закрытию

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        block.getContents().clear();
        for (int i = 0; i < validSlots; i++)
        {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) {block.getContents().put(i, it.clone());}
        }
        game.saveConfig(cfg);
        if (e.getPlayer() instanceof Player p)
        {
            Bukkit.getScheduler().runTask(plugin, () -> new BlockEditMenu(plugin, game, arena, cfg, epoch, block).open(p));
        }
    }
}
