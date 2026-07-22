package ru.kiviuly.skyblockwars.sbw.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.skyblockwars.arena.Arena;
import ru.kiviuly.skyblockwars.menu.Menu;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig;
import ru.kiviuly.skyblockwars.sbw.SkyBlockWarsGame;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.skyblockwars.sbw.epoch.EpochBlock;
import ru.kiviuly.skyblockwars.util.Items;
import ru.kiviuly.skyblockwars.util.Msg;

/**
 * Палитра блоков: клик по материалу добавляет его в эпоху (вес 1). Показывает все
 * ставящиеся блоки постранично; можно добавить несколько подряд, не выходя.
 */
public class BlockPaletteMenu extends Menu
{
    private static final List<Material> BLOCKS;

    static
    {
        List<Material> l = new ArrayList<>();
        for (Material m : Material.values())
        {
            if (!m.isLegacy() && m.isBlock() && m.isItem() && !m.isAir()) {l.add(m);}
        }
        l.sort(Comparator.comparing(Material::name));
        BLOCKS = List.copyOf(l);
    }

    private static final int SLOT_BACK = 46;

    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private final Epoch epoch;
    private int page = 0;

    public BlockPaletteMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena, ArenaGameConfig cfg, Epoch epoch)
    {
        super(54, Msg.get("sbwgui.palette-title"));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
        this.epoch = epoch;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        int pages = pageCount(BLOCKS.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < BLOCKS.size(); i++)
        {
            inventory.setItem(i, Items.named(BLOCKS.get(start + i), null, Msg.getList("sbwgui.palette-add")));
        }
        renderControls(page, pages, false, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_PREV) {if (page > 0) {page--; render();} return;}
        if (raw == SLOT_NEXT) {page++; render(); return;}
        if (raw == SLOT_BACK) {new EpochMenu(plugin, game, arena, cfg, epoch).open(p); return;}
        if (raw >= 0 && raw < PAGE_SIZE)
        {
            int idx = page * PAGE_SIZE + raw;
            if (idx >= BLOCKS.size()) {return;}
            Material mat = BLOCKS.get(idx);
            epoch.getBlocks().add(new EpochBlock(mat, 1));
            game.saveConfig(cfg);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
            Msg.send(p, "sbwgui.palette-added", Msg.ph("block", mat.name().toLowerCase(Locale.ROOT)));
        }
    }
}
