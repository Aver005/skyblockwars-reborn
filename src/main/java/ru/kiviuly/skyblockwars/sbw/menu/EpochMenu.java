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
    private static final int SLOT_NAME = 47;
    private static final int SLOT_THRESHOLD = 49;
    private static final int SLOT_ADD_BLOCK = 51;
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
