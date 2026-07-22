# 02 — Как сделать игру

Своя игра пишется в одном месте — наследнике `Minigame`. Ядро (арены, лобби,
отсчёты, таймер, откат мира, снапшоты игроков, HUD, статистика) уже готово;
твоё дело — правила. Package root: `ru.kiviuly.mcmgp`.

## Два шага

**1. Наследуй `Minigame`.** `Minigame` — логика без состояния: хуки получают
`GameSession` и работают через его API. Состояние матча живёт в сессии, не в полях
твоего класса (матчей может идти несколько параллельно на разных аренах).

**2. Зарегистрируй инстанцию в `McmgpPlugin.onEnable`** — замени заглушку:

```java
// McmgpPlugin.onEnable(), вместо:
// this.game = new TemplateGame(this);
this.game = new SpleefGame(this);
```

Конструктор наследника делай по образцу `TemplateGame` (обычно принимает
`McmgpPlugin`). Одна инстанция на плагин; ядро берёт её через `plugin.game()`.

## Каркас класса

```java
package ru.kiviuly.mcmgp.game.spleef;

import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import ru.kiviuly.mcmgp.McmgpPlugin;
import ru.kiviuly.mcmgp.game.GameSession;
import ru.kiviuly.mcmgp.game.MatchPlayer;
import ru.kiviuly.mcmgp.game.MatchResult;
import ru.kiviuly.mcmgp.game.Minigame;
import ru.kiviuly.mcmgp.util.Msg;

/** Spleef: ломай пол под соперниками; упал ниже линии — выбыл. */
public class SpleefGame extends Minigame
{
    public SpleefGame(McmgpPlugin plugin)
    {
        super(plugin);
    }

    @Override
    public String id() {return "spleef";}          // уникальный id (обязателен)

    @Override
    public String displayName() {return "Spleef";} // по умолчанию = id()

    @Override
    public void giveLoadout(GameSession s, Player p)
    {
        // Каждому на старте — лопата. Материал/зачары можно взять из настроек арены.
        p.getInventory().addItem(new ItemStack(Material.DIAMOND_SHOVEL));
    }

    @Override
    public void onTick(GameSession s)
    {
        // Число берём из настроек арены — у каждой арены своё значение.
        int killY = s.arena().getSetting("kill-y", 60);
        for (Player p : s.alivePlayers())
        {
            if (p.getLocation().getBlockY() < killY)
            {
                // Летальный урон ядро перехватит и переведёт игрока в спектатора,
                // затем вызовет onPlayerEliminated (fake death, spectator-on-death).
                p.damage(1000.0);
            }
        }
    }

    @Override
    public void onPlayerEliminated(GameSession s, MatchPlayer mp)
    {
        // Объявляем выбывание. Текст — ключ из messages.yml, число — плейсхолдер <n>.
        Msg.send(s.arena().getWorld(), "spleef.eliminated",
            Msg.ph("player", mp.name()), Msg.ph("n", s.aliveCount()));
    }

    @Override
    public MatchResult checkResult(GameSession s)
    {
        // Остался один — он победитель; вышло время — ничья. Ровно это делает дефолт,
        // поэтому этот хук можно вообще не переопределять:
        return s.defaultResult();
    }

    @Override
    public void onEnd(GameSession s, MatchResult result)
    {
        if (result.hasWinner())
        {
            Msg.send(s.arena().getWorld(), "spleef.win",
                Msg.ph("player", result.winners().get(0).name()));
        }
    }

    @Override
    public List<Component> scoreboardLines(GameSession s, Player viewer)
    {
        // Доп. строки сайдбара поверх стандартных (фаза/таймер рисует ядро).
        return List.of(Msg.get("spleef.sidebar-alive", Msg.ph("n", s.aliveCount())));
    }
}
```

> Тексты в примерах — только ключи `messages.yml`; ни одной строки для игрока в
> Java (инвариант 1). Заведи ключи `spleef.*` в `messages.yml`.

## Хуки `Minigame`

Все — с разумным дефолтом (no-op / ниже указано иное), переопределяй нужные.

| Хук | Когда зовётся | Дефолт |
|---|---|---|
| `String id()` | — (идентификатор игры) | **абстрактный, обязателен** |
| `String displayName()` | для показа | `id()` |
| `onLobbyJoin(s, p)` | игрок вошёл в лобби | no-op |
| `onStart(s)` | матч стартовал (игроки уже на спавнах, `SURVIVAL`, очищены) | no-op |
| `giveLoadout(s, p)` | на старте, каждому игроку (кит/предметы) | no-op |
| `onTick(s)` | каждую секунду матча | no-op |
| `onPlayerEliminated(s, mp)` | игрок погиб/выбыл (уже спектатор) | no-op |
| `checkResult(s)` | ядром на тике; не-null завершает матч | `s.defaultResult()` |
| `onEnd(s, result)` | матч завершается (объявить/наградить) | no-op |
| `scoreboardLines(s, viewer)` | сбор доп. строк сайдбара | пустой список |

Порядок вызовов на дистанции матча — в таблице жизненного цикла
[01-architecture.md](01-architecture.md#жизненный-цикл-матча).

## API `GameSession`, которым пользуется игра

```java
s.arena()              // Arena — конфиг площадки (мир, лобби, спавны, getSetting)
s.phase()              // GamePhase: LOBBY / COUNTDOWN / RUNNING / ENDING
s.elapsedSeconds()     // сколько идёт матч
s.remainingSeconds()   // сколько осталось (при match-duration-seconds > 0)
s.players()            // Collection<MatchPlayer> — все участники матча
s.alivePlayers()       // List<Player> — живые прямо сейчас
s.aliveCount()         // сколько живых
s.rememberBlock(block) // ЗАПОМНИ блок ПЕРЕД изменением — иначе не откатится
s.trackEntity(entity)  // зарегистрируй заспавненную сущность — удалится после матча
s.data()               // Map<String,Object> — состояние игры (см. ниже)
s.defaultResult()      // стандартный итог: последний живой или ничья
s.plugin()             // McmgpPlugin (доступ к game(), ArenaManager, StatsRepository)
```

## Где хранить состояние

Никогда — в полях `Minigame` (инстанция одна, а матчей много). Есть два места:

**1. `session.data()`** — произвольное состояние матча (`Map<String,Object>`):

```java
// onStart: инициализация
s.data().put("round", 1);

// onTick: чтение/обновление
int round = (int) s.data().getOrDefault("round", 1);
s.data().put("round", round + 1);
```

**2. `MatchPlayer`** — состояние на игрока: `uuid`, `name`, флаг `alive`, счётчик
`kills`. Перебираешь через `s.players()`, находишь своего и читаешь/меняешь. Для
своих per-player счётчиков клади их в `data()` под ключом с UUID.

## Настройки под конкретную игру (per-arena)

Числа игры не хардкодь — держи в настройках арены, у каждой арены своё значение:

```java
int killY   = s.arena().getSetting("kill-y", 60);      // с дефолтом
int shrinkAt = s.arena().getSetting("shrink-at", 30);
```

Админ задаёт их командой `/mg set <ID> kill-y 60` или `±`-редактором в
`/mg gui <ID>`. Хранятся в секции `settings` файла `arenas/<id>.yml`
(см. [03-commands-and-config.md](03-commands-and-config.md)). Ключи — свободные,
это пространство имён твоей игры.

## HUD

Стандартный сайдбар и босс-бар (фаза + таймер) рисует ядро, если включены в
`config.yml` (`hud.scoreboard` / `hud.bossbar`). Свои строки добавляй через
`scoreboardLines(s, viewer)` — они лягут поверх стандартных. Строки — `Component`
из `Msg` (MiniMessage), а не голый текст.

## Статистика

Пиши итоги через `StatsRepository` (доступен как `s.plugin().stats()` — сверься с
актуальным геттером в `McmgpPlugin`). Стандартный движок сам инкрементит
`played`; победу/поражения/убийства начисляй из `onEnd` / `onPlayerEliminated` по
правилам своей игры. Прямых SQL-строк вне `StatsRepository` быть не должно.

## Чек-лист новой игры

- [ ] `id()` уникален и стабилен (по нему идёт статистика).
- [ ] Все тексты игрокам — ключи в `messages.yml`, ни одной строки в Java.
- [ ] Числа игры — через `arena.getSetting(...)`, не хардкод.
- [ ] Меняешь блок в матче → сначала `s.rememberBlock(block)`.
- [ ] Спавнишь сущность → `s.trackEntity(entity)`.
- [ ] Состояние — в `s.data()` / `MatchPlayer`, не в полях `Minigame`.
- [ ] Логика — main thread; предметы/сущности метишь через PDC (`util/Keys`).

Полный список инвариантов — [.memories/CONVENTIONS.md](../.memories/CONVENTIONS.md).
