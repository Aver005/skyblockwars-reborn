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
import ru.kiviuly.skyblockwars.util.Items;
import ru.kiviuly.skyblockwars.util.Msg;

/**
 * Редактор таймингов фаз арены: длительность обычной фазы (match) и схватки (fight),
 * ± кликами. Дефолты берутся из глобального config.yml при создании арены; здесь
 * правятся per-arena. Сохраняется на каждое изменение.
 */
public class PhaseTimeMenu extends Menu
{
    private static final int SLOT_MATCH = 11;
    private static final int SLOT_FIGHT = 15;
    private static final int SLOT_BACK = 22;

    private final SkyBlockWarsPlugin plugin;
    private final SkyBlockWarsGame game;
    private final Arena arena;
    private final ArenaGameConfig cfg;

    public PhaseTimeMenu(SkyBlockWarsPlugin plugin, SkyBlockWarsGame game, Arena arena, ArenaGameConfig cfg)
    {
        super(27, Msg.get("sbwgui.time-title"));
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.cfg = cfg;
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
        inventory.setItem(SLOT_MATCH, timeButton("sbwgui.time-match-name", cfg.getMatchSeconds()));
        inventory.setItem(SLOT_FIGHT, timeButton("sbwgui.time-fight-name", cfg.getFightSeconds()));
        inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR, Msg.get("menu.back")));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack timeButton(String nameKey, int seconds)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("sbwgui.time-value", Msg.ph("time", formatTime(seconds)), Msg.ph("n", seconds)));
        lore.addAll(Msg.getList("sbwgui.time-adjust"));
        return Items.named(Material.CLOCK, Msg.get(nameKey), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_BACK) {new EpochListMenu(plugin, game, arena, cfg).open(p); return;}
        boolean match = raw == SLOT_MATCH;
        if (!match && raw != SLOT_FIGHT) {return;}
        int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 60 : 10);
        int cur = match ? cfg.getMatchSeconds() : cfg.getFightSeconds();
        int min = match ? 1 : 0; // обычная фаза не может быть 0; схватка — можно 0
        int next = Math.max(min, cur + delta);
        if (next == cur) {return;}
        if (match) {cfg.setMatchSeconds(next);} else {cfg.setFightSeconds(next);}
        game.saveConfig(cfg);
        render();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, delta > 0 ? 1.3f : 0.8f);
    }

    private static String formatTime(int seconds)
    {
        int sec = Math.max(0, seconds);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
