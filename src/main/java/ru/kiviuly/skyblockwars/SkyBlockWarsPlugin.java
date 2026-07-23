package ru.kiviuly.skyblockwars;

import org.bukkit.plugin.java.JavaPlugin;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.arena.ArenaService;
import ru.kiviuly.mg.api.util.Msg;
import ru.kiviuly.skyblockwars.sbw.SkyBlockWarsGame;

/**
 * SkyBlockWars — тонкий игровой плагин поверх платформы MgCore. Каркас (арены, жизненный
 * цикл матча, меню, снапшоты, откат мира, стата, HUD, i18n, тулкит) даёт mg-core; правила
 * игры — в {@link SkyBlockWarsGame} (наследник {@code Minigame}), который здесь
 * регистрируется в ядре через {@link MgCore#register}. Свой листенер (SbwListener) игра
 * регистрирует сама в конструкторе.
 */
public final class SkyBlockWarsPlugin extends JavaPlugin
{
    private MgCore core;
    private SkyBlockWarsGame game;

    @Override
    public void onEnable()
    {
        core = getServer().getServicesManager().load(MgCore.class);
        if (core == null)
        {
            getLogger().severe("MgCore не найден — SkyBlockWars выключается (нужен плагин MgCore).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        Msg.merge(this); // домешать свой messages.yml в общий каталог ядра (ключи sbw.*)

        game = new SkyBlockWarsGame(this, core);
        core.register(game);

        getLogger().info("SkyBlockWars enabled, game registered: " + game.id());
    }

    public ArenaService arenas() {return core.arenas();}
    public MgCore core() {return core;}
    public SkyBlockWarsGame game() {return game;}
}
