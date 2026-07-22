package ru.kiviuly.skyblockwars.sbw;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.skyblockwars.util.Items;
import ru.kiviuly.skyblockwars.util.Msg;

/**
 * Боссбар прогресса эпох — по одному на игрока. Показывает текущую эпоху и прогресс
 * до рубежа (или «финал», если рубежа нет). В режиме SHARED у всех значения общие,
 * в PERSONAL — свои. Заменяет ядровой босс-бар (в config.yml hud.bossbar: false).
 */
public class EpochBossBar
{
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public void add(Player p)
    {
        BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        bars.put(p.getUniqueId(), bar);
        p.showBossBar(bar);
    }

    public void remove(Player p)
    {
        if (p == null) {return;}
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {p.hideBossBar(bar);}
    }

    public void update(SbwState st, Player p)
    {
        if (st == null || p == null) {return;}
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {return;}
        Epoch epoch = st.currentEpoch(p.getUniqueId());
        int prog = st.progressFor(p.getUniqueId());
        int goal = epoch != null ? epoch.getThreshold() : 0;
        String name = epoch != null ? epoch.getName() : "-";
        if (goal > 0)
        {
            bar.progress(Math.max(0f, Math.min(1f, prog / (float) goal)));
            bar.color(BossBar.Color.GREEN);
            bar.name(Items.flat(Msg.get("sbw.bossbar-epoch", Msg.ph("epoch", name), Msg.ph("prog", prog), Msg.ph("goal", goal))));
        }
        else
        {
            bar.progress(1f);
            bar.color(BossBar.Color.PURPLE);
            bar.name(Items.flat(Msg.get("sbw.bossbar-epoch-final", Msg.ph("epoch", name), Msg.ph("prog", prog))));
        }
    }

    public void updateAll(SbwState st, Iterable<Player> players)
    {
        for (Player p : players) {update(st, p);}
    }

    public void clearAll(Iterable<? extends Player> players)
    {
        for (Player p : players)
        {
            BossBar bar = bars.get(p.getUniqueId());
            if (bar != null) {p.hideBossBar(bar);}
        }
        bars.clear();
    }
}
