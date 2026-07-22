package ru.kiviuly.mcmgp.game;

import org.bukkit.entity.Player;
import ru.kiviuly.mcmgp.McmgpPlugin;
import ru.kiviuly.mcmgp.util.Msg;

/**
 * ЗАГЛУШКА игры — точка старта для твоей мини-игры. Ничего игрового не делает:
 * матч завершается дефолтным условием {@link GameSession#defaultResult()}
 * (последний выживший / ничья по таймеру), чтобы каркас был проверяемо рабочим.
 *
 * Как сделать свою игру:
 *   1. Создай наследника {@link Minigame} (или переименуй/наполни этот класс).
 *   2. Переопредели нужные хуки (onStart/onTick/giveLoadout/onPlayerEliminated/
 *      checkResult/onEnd/scoreboardLines). Состояние матча держи в
 *      {@link GameSession#data()} и полях {@link MatchPlayer}.
 *   3. Зарегистрируй его в {@link McmgpPlugin#onEnable} вместо {@code new TemplateGame(this)}.
 * Подробно — docs/02-making-a-game.md.
 */
public class TemplateGame extends Minigame
{
    public TemplateGame(McmgpPlugin plugin) {super(plugin);}

    @Override
    public String id() {return "template";}

    @Override
    public String displayName() {return Msg.raw("template-game.display-name");}

    @Override
    public void onStart(GameSession s)
    {
        // TODO: старт матча — раздать правила/цели, поставить блоки (через s.rememberBlock).
    }

    @Override
    public void giveLoadout(GameSession s, Player p)
    {
        // TODO: стартовый набор игрока (Items.fromSpec / вручную). Пусто = игрок без предметов.
    }

    @Override
    public void onTick(GameSession s)
    {
        // TODO: логика каждой секунды матча.
    }

    @Override
    public void onPlayerEliminated(GameSession s, MatchPlayer mp)
    {
        // TODO: реакция на выбывание (счёт, дроп, ...).
    }

    @Override
    public void onEnd(GameSession s, MatchResult result)
    {
        // TODO: концовка — награды/анимации. Победитель уже объявлен ядром.
    }

    // checkResult не переопределён — используется дефолт (последний выживший / таймер).
    // Свою победу задаёшь так:
    //   @Override public MatchResult checkResult(GameSession s) { ... return null; /* или MatchResult.of(...) */ }
}
