package ru.kiviuly.mcmgp;

import java.sql.SQLException;

import org.bukkit.plugin.java.JavaPlugin;
import ru.kiviuly.mcmgp.arena.ArenaManager;
import ru.kiviuly.mcmgp.command.MinigameCommand;
import ru.kiviuly.mcmgp.game.Minigame;
import ru.kiviuly.mcmgp.game.TemplateGame;
import ru.kiviuly.mcmgp.listener.ChatListener;
import ru.kiviuly.mcmgp.listener.GameListener;
import ru.kiviuly.mcmgp.listener.ProtectionListener;
import ru.kiviuly.mcmgp.listener.SetupListener;
import ru.kiviuly.mcmgp.menu.MenuListener;
import ru.kiviuly.mcmgp.stats.StatsRepository;
import ru.kiviuly.mcmgp.util.DebugLog;
import ru.kiviuly.mcmgp.util.DebugLog.Cat;
import ru.kiviuly.mcmgp.util.Keys;
import ru.kiviuly.mcmgp.util.Msg;

/**
 * MCMGP — платформа мини-игр (шаблон). Ядро игро-независимо: конкретная игра
 * подключается через {@link Minigame} (см. {@link #game}). Чтобы сделать свою
 * игру — замени {@code new TemplateGame(this)} на свой класс (docs/02).
 */
public final class McmgpPlugin extends JavaPlugin
{
    private ArenaManager arenaManager;
    private StatsRepository statsRepository;
    private Minigame game;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Keys.init(this);
        Msg.init(this);
        DebugLog.init(this);

        arenaManager = new ArenaManager(this);
        statsRepository = new StatsRepository(this);
        try {statsRepository.open();}
        catch (SQLException e) {getLogger().severe("Failed to open stats.db: " + e.getMessage());}

        // >>> ТОЧКА РАСШИРЕНИЯ: подключи свою игру вместо TemplateGame <<<
        game = new TemplateGame(this);

        arenaManager.loadAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new GameListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new SetupListener(this), this);

        MinigameCommand command = new MinigameCommand(this);
        var mg = getCommand("mg");
        mg.setExecutor(command);
        mg.setTabCompleter(command);

        getLogger().info("MCMGP enabled, arenas loaded: " + arenaManager.all().size() + ", game: " + game.id());
        DebugLog.log(Cat.ADMIN, "plugin enable arenas=%d game=%s", arenaManager.all().size(), game.id());
    }

    @Override
    public void onDisable()
    {
        DebugLog.log(Cat.ADMIN, "plugin disable");
        if (arenaManager != null) {arenaManager.stopAll();}
        saveEverything();
        if (statsRepository != null) {statsRepository.close();}
    }

    public void saveEverything()
    {
        if (arenaManager != null) {arenaManager.saveAll();}
    }

    public void reloadEverything()
    {
        reloadConfig();
        Msg.reload();
        DebugLog.reload();
        arenaManager.loadAll();
    }

    public ArenaManager arenas() {return arenaManager;}
    public StatsRepository stats() {return statsRepository;}
    public Minigame game() {return game;}
}
