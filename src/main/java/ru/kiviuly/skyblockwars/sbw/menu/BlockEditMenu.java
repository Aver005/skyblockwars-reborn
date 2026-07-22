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

/** Редактор одного блока эпохи: вес (± кликами), редактор лута (для контейнеров), удаление. */
public class BlockEditMenu extends Menu
{
    private static final int SLOT_ICON = 11;
    private static final int SLOT_WEIGHT = 13;
    private static final int SLOT_LOOT = 15;
    private static final int SLOT_REMOVE = 29;
    private static final int SLOT_BACK = 33;

    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;
    private final Epoch epoch;
    private final EpochBlock block;

    public BlockEditMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena,
        ArenaGameConfig cfg, Epoch epoch, EpochBlock block)
    {
        super(36, Msg.get("sbwgui.block-title"));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
        this.epoch = epoch;
        this.block = block;
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
        inventory.setItem(SLOT_ICON, icon());
        inventory.setItem(SLOT_WEIGHT, weightButton());
        if (block.isContainer())
        {
            inventory.setItem(SLOT_LOOT, Items.named(Material.CHEST,
                Msg.get("sbwgui.block-loot-name"),
                appended(Msg.getList("sbwgui.block-loot-lore"), Msg.get("sbwgui.block-loot-count", Msg.ph("n", block.getContents().size())))));
        }
        inventory.setItem(SLOT_REMOVE, Items.named(Material.RED_DYE, Msg.get("sbwgui.block-remove-name"), Msg.getList("sbwgui.block-remove-lore")));
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack icon()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.block-weight", Msg.ph("n", block.getWeight())));
        lore.add(Msg.get(block.isContainer() ? "sbwgui.block-is-container" : "sbwgui.block-is-plain"));
        return Items.named(block.getMaterial(), null, lore);
    }

    private ItemStack weightButton()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.block-weight", Msg.ph("n", block.getWeight())));
        lore.addAll(Msg.getList("sbwgui.weight-adjust"));
        return Items.named(Material.COMPARATOR, Msg.get("sbwgui.block-weight-name"), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_WEIGHT ->
            {
                int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
                int next = Math.max(1, block.getWeight() + delta);
                if (next != block.getWeight())
                {
                    block.setWeight(next);
                    game.saveConfig(cfg);
                    inventory.setItem(SLOT_ICON, icon());
                    inventory.setItem(SLOT_WEIGHT, weightButton());
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, delta > 0 ? 1.3f : 0.8f);
                }
            }
            case SLOT_LOOT ->
            {
                if (block.isContainer()) {new LootEditorMenu(plugin, game, arena, cfg, epoch, block).open(p);}
            }
            case SLOT_REMOVE ->
            {
                epoch.getBlocks().remove(block);
                game.saveConfig(cfg);
                new EpochMenu(plugin, game, arena, cfg, epoch).open(p);
            }
            case SLOT_BACK -> new EpochMenu(plugin, game, arena, cfg, epoch).open(p);
            default -> {}
        }
    }

    private static List<Component> appended(List<Component> base, Component extra)
    {
        List<Component> out = new ArrayList<>(base);
        out.add(extra);
        return out;
    }
}
