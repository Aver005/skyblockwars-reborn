package ru.kiviuly.skyblockwars.sbw.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;
import ru.kiviuly.skyblockwars.arena.Arena;
import ru.kiviuly.skyblockwars.menu.AnvilInputMenu;
import ru.kiviuly.skyblockwars.menu.Menu;
import ru.kiviuly.skyblockwars.sbw.ArenaGameConfig;
import ru.kiviuly.skyblockwars.sbw.SkyBlockWarsGame;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.skyblockwars.sbw.epoch.EpochBlock;
import ru.kiviuly.skyblockwars.util.Items;
import ru.kiviuly.skyblockwars.util.Msg;

/** Редактор одной эпохи: имя (наковальня), рубеж (± кликами), сетка блоков, добавление блока. */
public class EpochMenu extends Menu
{
    private static final int CONTENT = 45;      // 0..44 — блоки эпохи
    private static final int SLOT_BACK = 45;
    private static final int SLOT_FROM_INV = 46;
    private static final int SLOT_NAME = 47;
    private static final int SLOT_FROM_CHEST = 48;
    private static final int SLOT_THRESHOLD = 49;
    private static final int SLOT_COPY = 50;
    private static final int SLOT_ADD_BLOCK = 51;
    private static final int SLOT_INHERIT = 52;
    private static final int SLOT_CLOSE = 53;

    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private final Epoch epoch;

    public EpochMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena, ArenaGameConfig cfg, Epoch epoch)
    {
        super(54, Msg.get("sbwgui.epoch-title"));
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
        List<EpochBlock> blocks = epoch.getBlocks();
        for (int i = 0; i < CONTENT && i < blocks.size(); i++) {inventory.setItem(i, blockIcon(blocks.get(i)));}
        for (int i = CONTENT; i < inventory.getSize(); i++) {inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));}
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
        inventory.setItem(SLOT_NAME, Items.named(Material.NAME_TAG, Msg.get("sbwgui.epoch-name-btn"),
            appended(Msg.getList("sbwgui.epoch-name-lore"), Items.flat(Msg.mm(epoch.getName())))));
        inventory.setItem(SLOT_THRESHOLD, thresholdButton());
        inventory.setItem(SLOT_ADD_BLOCK, Items.named(Material.EMERALD, Msg.get("sbwgui.epoch-addblock-name"), Msg.getList("sbwgui.epoch-addblock-lore")));
        inventory.setItem(SLOT_FROM_INV, Items.named(Material.CHEST_MINECART, Msg.get("sbwgui.epoch-frominv-name"), Msg.getList("sbwgui.epoch-frominv-lore")));
        inventory.setItem(SLOT_FROM_CHEST, Items.named(Material.CHEST, Msg.get("sbwgui.epoch-fromchest-name"), Msg.getList("sbwgui.epoch-fromchest-lore")));
        inventory.setItem(SLOT_COPY, Items.named(Material.BOOK, Msg.get("sbwgui.epoch-copy-name"), Msg.getList("sbwgui.epoch-copy-lore")));
        inventory.setItem(SLOT_INHERIT, Items.named(Material.ENDER_EYE, Msg.get("sbwgui.epoch-inherit-name"),
            appended(Msg.getList("sbwgui.epoch-inherit-lore"), Msg.get("sbwgui.epoch-inherit-count", Msg.ph("n", epoch.getInherits().size())))));
        inventory.setItem(SLOT_CLOSE, Items.named(Material.BARRIER, Msg.get("hub.close-name")));
    }

    private ItemStack blockIcon(EpochBlock b)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.block-weight", Msg.ph("n", b.getWeight())));
        if (b.isContainer()) {lore.add(Msg.get("sbwgui.block-loot-count", Msg.ph("n", b.getContents().size())));}
        lore.addAll(Msg.getList("sbwgui.block-click"));
        return Items.named(b.getMaterial(), null, lore);
    }

    private ItemStack thresholdButton()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(epoch.hasThreshold()
            ? Msg.get("sbwgui.epoch-threshold-value", Msg.ph("n", epoch.getThreshold()))
            : Msg.get("sbwgui.epoch-threshold-final"));
        lore.addAll(Msg.getList("sbwgui.threshold-adjust"));
        return Items.named(Material.TARGET, Msg.get("sbwgui.epoch-threshold-name"), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw >= 0 && raw < CONTENT)
        {
            if (raw < epoch.getBlocks().size()) {new BlockEditMenu(plugin, game, arena, cfg, epoch, epoch.getBlocks().get(raw)).open(p);}
            return;
        }
        switch (raw)
        {
            case SLOT_BACK -> new EpochListMenu(plugin, game, arena, cfg).open(p);
            case SLOT_CLOSE -> p.closeInventory();
            case SLOT_ADD_BLOCK -> new BlockPaletteMenu(plugin, game, arena, cfg, epoch).open(p);
            case SLOT_FROM_INV -> importFromInventory(p);
            case SLOT_FROM_CHEST -> importFromChest(p);
            case SLOT_COPY -> new EpochPickMenu(plugin, game, arena, cfg, epoch, false).open(p);
            case SLOT_INHERIT -> new EpochPickMenu(plugin, game, arena, cfg, epoch, true).open(p);
            case SLOT_NAME -> editName(p);
            case SLOT_THRESHOLD ->
            {
                int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
                int next = Math.max(-1, epoch.getThreshold() + delta);
                if (next != epoch.getThreshold())
                {
                    epoch.setThreshold(next);
                    game.saveConfig(cfg);
                    inventory.setItem(SLOT_THRESHOLD, thresholdButton());
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, delta > 0 ? 1.3f : 0.8f);
                }
            }
            default -> {}
        }
    }

    /** Способ 1: добавить блоки из инвентаря админа (количество каждого материала → вес). */
    private void importFromInventory(Player p)
    {
        int added = importItems(Arrays.asList(p.getInventory().getStorageContents()));
        if (added == 0) {Msg.send(p, "sbw.import-empty"); return;}
        finishImport(p, added);
    }

    /** Способ 2: добавить блоки из содержимого контейнера, на который смотрит админ. */
    private void importFromChest(Player p)
    {
        Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof Container c)) {Msg.send(p, "sbw.import-look-chest"); return;}
        int added = importItems(Arrays.asList(c.getInventory().getContents()));
        if (added == 0) {Msg.send(p, "sbw.import-empty"); return;}
        finishImport(p, added);
    }

    /**
     * Слить предметы в блоки эпохи: только ставящиеся блоки; одинаковые материалы —
     * в один блок с суммой количеств как весом (в существующий блок — прибавляем вес).
     * Возвращает число затронутых материалов.
     */
    private int importItems(Iterable<ItemStack> items)
    {
        Map<Material, Integer> amounts = new LinkedHashMap<>();
        for (ItemStack it : items)
        {
            if (it == null) {continue;}
            Material m = it.getType();
            if (m.isLegacy() || m.isAir() || !m.isBlock() || !m.isItem()) {continue;}
            amounts.merge(m, it.getAmount(), Integer::sum);
        }
        for (Map.Entry<Material, Integer> e : amounts.entrySet())
        {
            EpochBlock existing = findBlock(e.getKey());
            if (existing != null) {existing.setWeight(existing.getWeight() + e.getValue());}
            else {epoch.getBlocks().add(new EpochBlock(e.getKey(), e.getValue()));}
        }
        return amounts.size();
    }

    private EpochBlock findBlock(Material mat)
    {
        for (EpochBlock b : epoch.getBlocks()) {if (b.getMaterial() == mat) {return b;}}
        return null;
    }

    private void finishImport(Player p, int added)
    {
        game.saveConfig(cfg);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        Msg.send(p, "sbwgui.import-added", Msg.ph("n", added));
        render();
    }

    private void editName(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("sbwgui.epoch-name-anvil"), epoch.getName(), text ->
        {
            epoch.setName(text);
            game.saveConfig(cfg);
            new EpochMenu(plugin, game, arena, cfg, epoch).open(p);
        }).open(p);
    }

    private static List<Component> appended(List<Component> base, Component extra)
    {
        List<Component> out = new ArrayList<>(base);
        out.add(extra);
        return out;
    }
}
