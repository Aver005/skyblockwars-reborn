package ru.kiviuly.skyblockwars.sbw.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.menu.Menu;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig.EpochMode;
import ru.kiviuly.skyblockwars.sbw.SkyBlockWarsGame;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Продвинутый редактор эпох арены: список эпох (порядок = последовательность), с
 * добавлением, удалением, переупорядочиванием и переключением режима эпох
 * (personal/shared). Клик по эпохе — редактировать; см. подсказки в лоре.
 * Если эпох ещё нет — засеиваем набором по умолчанию как шаблон для правки.
 */
public class EpochListMenu extends Menu
{
    private static final int SLOT_CLOSE = 46;
    private static final int SLOT_ADD = 48;
    private static final int SLOT_MODE = 50;
    private static final int SLOT_TIME = 52;

    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private int page = 0;

    public EpochListMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena, ArenaGameConfig cfg)
    {
        super(54, Msg.get("sbwgui.list-title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
        if (cfg.epochs().isEmpty())
        {
            cfg.epochs().addAll(game.defaultEpochs()); // шаблон, который админ дальше правит
            game.saveConfig(cfg);
        }
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
        List<Epoch> epochs = cfg.epochs();
        int pages = pageCount(epochs.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < epochs.size(); i++)
        {
            inventory.setItem(i, epochIcon(epochs.get(start + i), start + i));
        }
        renderControls(page, pages, false, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_CLOSE, Items.named(Material.BARRIER, Msg.get("hub.close-name")));
        inventory.setItem(SLOT_ADD, Items.named(Material.LIME_DYE, Msg.get("sbwgui.list-add-name"), Msg.getList("sbwgui.list-add-lore")));
        inventory.setItem(SLOT_MODE, modeButton());
        inventory.setItem(SLOT_TIME, timeButton());
    }

    private ItemStack timeButton()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.list-time-match", Msg.ph("time", formatTime(cfg.getMatchSeconds()))));
        lore.add(Msg.get("sbwgui.list-time-fight", Msg.ph("time", formatTime(cfg.getFightSeconds()))));
        lore.addAll(Msg.getList("sbwgui.list-time-lore"));
        return Items.named(Material.CLOCK, Msg.get("sbwgui.list-time-name"), lore);
    }

    private static String formatTime(int seconds)
    {
        int sec = Math.max(0, seconds);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private ItemStack epochIcon(Epoch epoch, int index)
    {
        Material mat = epoch.getBlocks().isEmpty() ? Material.CLOCK : epoch.getBlocks().get(0).getMaterial();
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.list-epoch-num", Msg.ph("n", index + 1)));
        lore.add(epoch.hasThreshold()
            ? Msg.get("sbwgui.epoch-threshold-value", Msg.ph("n", epoch.getThreshold()))
            : Msg.get("sbwgui.epoch-threshold-final"));
        lore.add(Msg.get("sbwgui.list-epoch-blocks", Msg.ph("n", epoch.getBlocks().size())));
        if (!epoch.getInherits().isEmpty()) {lore.add(Msg.get("sbwgui.list-epoch-inherits", Msg.ph("n", epoch.getInherits().size())));}
        lore.addAll(Msg.getList("sbwgui.list-epoch-actions"));
        return Items.named(mat, Items.flat(Msg.mm(epoch.getName())), lore);
    }

    private ItemStack modeButton()
    {
        boolean personal = cfg.getEpochMode() == EpochMode.PERSONAL;
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.list-mode-value", Msg.ph("mode", Msg.raw(personal ? "sbwgui.mode-personal" : "sbwgui.mode-shared"))));
        lore.addAll(Msg.getList("sbwgui.list-mode-lore"));
        return Items.named(personal ? Material.PLAYER_HEAD : Material.BEACON, Msg.get("sbwgui.list-mode-name"), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw >= 0 && raw < PAGE_SIZE)
        {
            int idx = page * PAGE_SIZE + raw;
            List<Epoch> epochs = cfg.epochs();
            if (idx >= epochs.size()) {return;}
            if (e.isShiftClick() && e.isLeftClick()) {move(p, idx, -1);}
            else if (e.isShiftClick() && e.isRightClick()) {move(p, idx, 1);}
            else if (e.isRightClick()) {remove(p, idx);}
            else if (e.isLeftClick()) {new EpochMenu(plugin, game, arena, cfg, epochs.get(idx)).open(p);}
            return;
        }
        switch (raw)
        {
            case SLOT_PREV -> {if (page > 0) {page--; render();}}
            case SLOT_NEXT -> {page++; render();}
            case SLOT_CLOSE -> p.closeInventory();
            case SLOT_ADD -> addEpoch(p);
            case SLOT_MODE -> toggleMode(p);
            case SLOT_TIME -> new PhaseTimeMenu(plugin, game, arena, cfg).open(p);
            default -> {}
        }
    }

    private void move(Player p, int idx, int dir)
    {
        List<Epoch> epochs = cfg.epochs();
        int to = idx + dir;
        if (to < 0 || to >= epochs.size()) {return;}
        Collections.swap(epochs, idx, to);
        game.saveConfig(cfg);
        render();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, dir < 0 ? 1.3f : 0.8f);
    }

    private void remove(Player p, int idx)
    {
        cfg.epochs().remove(idx);
        game.saveConfig(cfg);
        render();
        p.playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1f);
    }

    private void addEpoch(Player p)
    {
        Epoch epoch = new Epoch("Эпоха " + (cfg.epochs().size() + 1), -1);
        cfg.epochs().add(epoch);
        game.saveConfig(cfg);
        new EpochMenu(plugin, game, arena, cfg, epoch).open(p);
    }

    private void toggleMode(Player p)
    {
        cfg.setEpochMode(cfg.getEpochMode() == EpochMode.PERSONAL ? EpochMode.SHARED : EpochMode.PERSONAL);
        game.saveConfig(cfg);
        inventory.setItem(SLOT_MODE, modeButton());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }
}
