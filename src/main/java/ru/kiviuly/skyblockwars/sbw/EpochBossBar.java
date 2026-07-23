package ru.kiviuly.skyblockwars.sbw;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.kiviuly.skyblockwars.sbw.SbwState.MatchPhase;
import ru.kiviuly.skyblockwars.sbw.epoch.Epoch;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Боссбар матча SkyBlockWars — по одному на игрока. В обычной фазе показывает эпоху и
 * прогресс до рубежа (в SHARED — общий, в PERSONAL — свой). В схватке — отсчёт до
 * разрушения, в разрушении — предупреждение. Заменяет ядровой босс-бар (hud.bossbar: false).
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

    public void update(SbwState st, Player p, int elapsed)
    {
        if (st == null || p == null) {return;}
        BossBar bar = bars.get(p.getUniqueId());
        if (bar == null) {return;}
        MatchPhase phase = st.getMatchPhase();
        if (phase == MatchPhase.FIGHT)
        {
            int rem = Math.max(0, st.phaseRemaining(elapsed));
            bar.color(BossBar.Color.RED);
            bar.progress(st.fightSeconds() > 0 ? Math.max(0f, Math.min(1f, rem / (float) st.fightSeconds())) : 0f);
            bar.name(Items.flat(Msg.get("sbw.bossbar-fight", Msg.ph("time", format(rem)))));
            return;
        }
        if (phase == MatchPhase.DESTRUCTION)
        {
            bar.color(BossBar.Color.PURPLE);
            bar.progress(1f);
            bar.name(Items.flat(Msg.get("sbw.bossbar-destruction")));
            return;
        }
        // NORMAL — прогресс эпохи
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
            bar.color(BossBar.Color.BLUE);
            bar.name(Items.flat(Msg.get("sbw.bossbar-epoch-final", Msg.ph("epoch", name), Msg.ph("prog", prog))));
        }
    }

    public void updateAll(SbwState st, Iterable<Player> players, int elapsed)
    {
        for (Player p : players) {update(st, p, elapsed);}
    }

    /** Скрыть и забыть все бары — по собственной карте (не зависит от списка игроков сессии). */
    public void clearAll()
    {
        for (Map.Entry<UUID, BossBar> e : bars.entrySet())
        {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {p.hideBossBar(e.getValue());}
        }
        bars.clear();
    }

    private static String format(int seconds) {return String.format("%d:%02d", seconds / 60, seconds % 60);}
}
