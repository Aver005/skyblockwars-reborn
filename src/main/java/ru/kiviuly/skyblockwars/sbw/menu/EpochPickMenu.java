package ru.kiviuly.skyblockwars.sbw.menu;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
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
 * Выбор другой эпохи для одного из двух действий (флаг {@code inheritMode}):
 *  - COPY ({@code false}) — способ 3 «редактировать из другой эпохи»: клик КОПИРУЕТ
 *    блоки выбранной эпохи в текущую (глубокая копия с весами и лутом), одноразово.
 *  - INHERIT ({@code true}) — задача про наследование: клик ВКЛЮЧАЕТ/выключает эпоху в
 *    список наследуемых (её блоки подмешиваются при спавне, НЕ копируясь в текущую).
 * Список — все эпохи арены, кроме самой редактируемой.
 */
public class EpochPickMenu extends Menu
{
    private static final int SLOT_BACK = 46;

    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private final Epoch epoch;
    private final boolean inheritMode;
    private int page = 0;

    public EpochPickMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena, ArenaGameConfig cfg,
        Epoch epoch, boolean inheritMode)
    {
        super(54, Msg.get(inheritMode ? "sbwgui.inherit-title" : "sbwgui.copy-title"));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
        this.epoch = epoch;
        this.inheritMode = inheritMode;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    /** Все эпохи арены, кроме редактируемой (по стабильному id). */
    private List<Epoch> others()
    {
        List<Epoch> out = new ArrayList<>();
        for (Epoch e : cfg.epochs()) {if (!e.id().equals(epoch.id())) {out.add(e);}}
        return out;
    }

    private void render()
    {
        inventory.clear();
        List<Epoch> list = others();
        int pages = pageCount(list.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < list.size(); i++)
        {
            inventory.setItem(i, icon(list.get(start + i)));
        }
        renderControls(page, pages, false, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
    }

    private ItemStack icon(Epoch e)
    {
        Material mat = e.getBlocks().isEmpty() ? Material.CLOCK : e.getBlocks().get(0).getMaterial();
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.list-epoch-blocks", Msg.ph("n", e.getBlocks().size())));
        if (inheritMode)
        {
            boolean on = epoch.getInherits().contains(e.id());
            lore.add(Msg.get(on ? "sbwgui.inherit-on" : "sbwgui.inherit-off"));
            lore.addAll(Msg.getList("sbwgui.inherit-toggle"));
        }
        else
        {
            lore.addAll(Msg.getList("sbwgui.copy-pick"));
        }
        return Items.named(mat, Items.flat(Msg.mm(e.getName())), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_PREV) {if (page > 0) {page--; render();} return;}
        if (raw == SLOT_NEXT) {page++; render(); return;}
        if (raw == SLOT_BACK) {new EpochMenu(plugin, game, arena, cfg, epoch).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}
        List<Epoch> list = others();
        int idx = page * PAGE_SIZE + raw;
        if (idx >= list.size()) {return;}
        Epoch picked = list.get(idx);
        if (inheritMode) {toggleInherit(p, picked);}
        else {copyFrom(p, picked);}
    }

    private void toggleInherit(Player p, Epoch picked)
    {
        List<String> inherits = epoch.getInherits();
        if (inherits.remove(picked.id())) {p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);}
        else {inherits.add(picked.id()); p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);}
        game.saveConfig(cfg);
        render();
    }

    private void copyFrom(Player p, Epoch picked)
    {
        if (picked.getBlocks().isEmpty()) {Msg.send(p, "sbwgui.copy-empty"); return;}
        for (EpochBlock b : picked.getBlocks()) {epoch.getBlocks().add(b.copy());}
        game.saveConfig(cfg);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        Msg.send(p, "sbwgui.copy-done", Msg.ph("n", picked.getBlocks().size()), Msg.phC("epoch", Items.flat(Msg.mm(picked.getName()))));
        new EpochMenu(plugin, game, arena, cfg, epoch).open(p);
    }
}
