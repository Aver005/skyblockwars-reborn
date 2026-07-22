package ru.kiviuly.skyblockwars.game;

import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import ru.kiviuly.skyblockwars.SkyBlockWarsPlugin;

/**
 * ТОЧКА РАСШИРЕНИЯ. Логика конкретной мини-игры — один наследник, зарегистрированный
 * в {@link SkyBlockWarsPlugin#onEnable} (замени {@code new TemplateGame(this)}). Класс
 * БЕЗ состояния матча: одно на весь плагин; состояние матча живёт в
 * {@link GameSession} ({@link GameSession#data()} + список {@link MatchPlayer}).
 * Все хуки по умолчанию пусты/разумны — переопредели нужные.
 *
 * Жизненный цикл (кто зовёт что): {@link GameSession} ведёт лобби→отсчёт→матч→конец,
 * телепорт/снапшот/откат мира; игра решает правила, лут и условие победы.
 */
public abstract class Minigame
{
    protected final SkyBlockWarsPlugin plugin;

    protected Minigame(SkyBlockWarsPlugin plugin) {this.plugin = plugin;}

    /** Уникальный id игры (например, "spleef"). */
    public abstract String id();

    /** Имя для HUD/сообщений (по умолчанию — id). */
    public String displayName() {return id();}

    // ===== хуки жизненного цикла =====

    /** Игрок вошёл в лобби (снапшот уже снят, инвентарь очищен). */
    public void onLobbyJoin(GameSession s, Player p) {}

    /** Матч начался: игроки уже на спавнах, SURVIVAL, очищены. Раздай правила/HUD. */
    public void onStart(GameSession s) {}

    /** Стартовый набор одному игроку (вызывается на старте для каждого). */
    public void giveLoadout(GameSession s, Player p) {}

    /** Каждую секунду матча. */
    public void onTick(GameSession s) {}

    /** Игрок выбыл (умер) — уже переведён в спектаторы. */
    public void onPlayerEliminated(GameSession s, MatchPlayer mp) {}

    /**
     * Условие завершения. Верни не-null, чтобы закончить матч, иначе null (продолжаем).
     * По умолчанию — «последний выживший, или ничья по истечении времени».
     */
    public MatchResult checkResult(GameSession s) {return s.defaultResult();}

    /** Матч завершается: объяви победителя, выдай награды. Мир ещё не откачен. */
    public void onEnd(GameSession s, MatchResult result) {}

    /** Доп. строки сайдбара под стандартными (пусто = только стандартные). */
    public List<Component> scoreboardLines(GameSession s, Player viewer) {return List.of();}

    // ===== обобщённые хуки расширения (движок зовёт, игра решает) =====

    /**
     * Смертельный урон по игроку в матче. Верни {@code true} — движок штатно выбивает
     * игрока в спектаторы (дефолт). Верни {@code false} — игра сама обработала событие
     * (например, возродила игрока на его блоке): движок НЕ выбивает. Урон уже отменён.
     */
    public boolean onLethalDamage(GameSession s, Player p) {return true;}

    /**
     * Разрешён ли PvP-урон в лобби/отсчёте (для лобби-разминок). По умолчанию нет —
     * вне фазы матча урона нет. При {@code true} движок не гасит урон в лобби; беречь
     * игроков от реальной смерти и считать статистику — забота самой игры.
     */
    public boolean allowLobbyPvp() {return false;}

    /**
     * Игро-специфичная подкоманда {@code /<cmd> <sub> ...} (движок зовёт после своих,
     * до «неизвестная подкоманда»; вызывающий уже прошёл проверку прав админа).
     * Верни {@code true}, если обработал. {@code args[0]} = сам {@code sub}.
     */
    public boolean onCommand(Player p, String sub, String[] args) {return false;}

    /** Подсказки таб-комплита для игро-специфичных подкоманд (движок домешивает к своим). */
    public List<String> tabComplete(Player p, String[] args) {return List.of();}

    /** Доп. строки в {@code /<cmd> help} (игро-специфичные команды). */
    public List<Component> helpLines(Player p) {return List.of();}

    /** {@code /<cmd> reload}: перечитать игро-специфичные конфиги. */
    public void onReload() {}

    /** Арена удалена ({@code /<cmd> remove}): подчистить игро-специфичные данные арены. */
    public void onArenaRemoved(String arenaId) {}
}
