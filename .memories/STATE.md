# STATE — текущее состояние

Last updated: 2026-07-24 (порт на платформу MgCore — ядро вынесено в отдельный плагин)

## Кратко

**SkyBlockWars Reborn** — теперь **тонкий игровой плагин** поверх платформы
**MgCore** (`ru.kiviuly.mg`, соседний репо `../kiviuly-mg-core`). Своего каркаса
БОЛЬШЕ НЕТ: арены, жизненный цикл матча, меню, снапшоты, откат мира, стата, HUD,
чат, i18n и утилиты дал `mg-api`/`mg-core`. В этом репо осталась только игра —
`SkyBlockWarsPlugin` (17 строк bootstrap) + пакет `sbw/` (18 файлов).
Ветка `refactor/Migrate-to-MG-core`, коммит `b437795` (BREAKING).

Суть игры не изменилась: каждого телепортирует на свой спавн; блок под ним — блок
возрождения. Пока чужой не сломал — при гибели игрок возрождается. Ломая свой блок,
игрок добывает его содержимое, блок восстанавливается случайным из текущей эпохи
(по весам), прогресс идёт к рубежу эпохи (боссбар). 30 мин игры → слом всех блоков →
5 мин схватки → разрушение арены. Победитель — последний выживший.

## Что изменил порт на MgCore (коммит b437795)

- **Удалено из репо** (переехало в mg-core): `arena/`, `game/` (движок + `Minigame`
  + `TemplateGame`), `listener/`, `menu/` (базовый `Menu`, `AnvilInputMenu`,
  `MenuListener`, все ядровые меню), `player/PlayerSnapshot`, `stats/`, `ui/`,
  `util/` (`Msg`, `Keys`, `Items`, `DebugLog`), `command/MinigameCommand`. −3600 строк.
- **Импорты игры** переехали на `ru.kiviuly.mg.api.*`: `arena.Arena`,
  `game.Match` (бывший `GameSession`), `game.MatchPlayer`, `game.MatchResult`,
  `game.Minigame`, `game.GamePhase`, `menu.Menu`, `menu.AnvilInputMenu`,
  `util.Msg` / `util.Items` / `util.Keys` / `util.DebugLog`.
  **`GameSession` → `Match`** — единственное переименование в API.
- **Bootstrap**: `SkyBlockWarsPlugin.onEnable` берёт `MgCore` из Bukkit
  `ServicesManager` (`load(MgCore.class)`), при отсутствии — самовыключение;
  `Msg.merge(this)` домешивает свой `messages.yml` в каталог ядра; затем
  `core.register(new SkyBlockWarsGame(this, core))`.
- **`plugin.yml`**: `depend: [MgCore]`, СВОИХ КОМАНД НЕТ (секция `commands` убрана).
- **Сборка**: `settings.gradle.kts` → `includeBuild("../kiviuly-mg-core")`
  (dependency substitution по `group:name`), `build.gradle.kts` →
  `compileOnly("ru.kiviuly.mg:mg-api:1.0.0")`. Классы `mg-api` в рантайме отдаёт
  jar MgCore (mg-api вложен внутрь него), поэтому только `compileOnly`.

## Как теперь устроено (граница «ядро / игра»)

| Что | Где живёт |
|---|---|
| Арены, матч, фазы, снапшоты, откат мира, стата, HUD, чат, `/mg` | плагин **MgCore** (`../kiviuly-mg-core`) |
| Правила SkyBlockWars, эпохи, кольцо спавнов, GUI эпох/кита | **этот репо**, пакет `sbw/` |
| Игро-конфиг арены (центр/радиус/режим/фазы/эпохи/кит) | `plugins/SkyBlockWars/game/<ID>.yml` |
| Конфиг площадки (мир/лобби/спавны/лимиты) | `plugins/MgCore/arenas/<id>.yml` |

**MgCore держит ОДНУ игру**: `MgCorePlugin.register(Minigame)` перезаписывает поле
`game`, а ядро само регистрирует `TemplateGame` в своём `onEnable`. SkyBlockWars
(`depend: MgCore`) грузится позже и вытесняет заглушку — порядок загрузки критичен.
Второй игровой плагин на том же сервере затрёт первый (ограничение ядра, не баг игры).

## Что реализовано — игра (`sbw/`, 18 файлов)

- **`SkyBlockWarsGame`** (наследник `Minigame`) — оркестратор: хуки матча, ломание
  блоков, эпохи, фазы, лобби-дуэль, подкоманды. Конструктор сам регистрирует
  `SbwListener`. Реализует хуки ядра: `onStart`, `checkResult`, `giveLoadout`,
  `onTick`, `onLethalDamage`, `onPlayerEliminated`, `onPlayerRemoved`, `onCleanup`,
  `scoreboardLines`, `allowLobbyPvp`, `onCommand`/`tabComplete`/`helpLines`,
  `onReload`, `onArenaCreated`, `onArenaRemoved` — все они теперь ЯДРОВЫЕ (в `mg-api`),
  расширять `Minigame` нужно в репо mg-core.
- **`ArenaGameConfig`** — per-arena игро-конфиг в `game/<ID>.yml` (центр, радиус,
  режим эпох, длительности фаз, эпохи, стартовый набор `kit`). Дефолтные эпохи
  МАТЕРИАЛИЗУЮТСЯ в файл при первом обращении (`config()`) и при `onArenaCreated`.
- **`RingSpawns`** — расстановка спавнов по окружности вокруг центра; пишутся в
  ядровой `arena.getSpawns()` через `plugin.arenas().save(arena)`.
- **`SbwState`** (в `Match.data()`) — состояние матча: режим/эпохи, per-player прогресс
  и блок возрождения, общий прогресс (SHARED), карта «блок→владелец», фаза матча
  (NORMAL/FIGHT/DESTRUCTION), поставленные игроками блоки, боссбар.
- **`EpochBossBar`** — per-player боссбар: прогресс эпохи → отсчёт схватки → разрушение
  (ядровой боссбар выключен в `config.yml`, `hud.bossbar: false`).
- **`SbwListener`** — BlockBreak (свой блок = добыча+прогресс+рефилл; чужой = слом
  якоря), BlockPlace (учёт для разрушения), EntityDamage в лобби (дуэль без смертей).
  Приоритет HIGH — ядровой `GameListener` (NORMAL) уже запомнил блок для отката.
- **`epoch/`** — `Epoch` (стабильный `id`, блоки+рубеж, `inherits`, взвешенный выбор
  по своим и наследуемым транзитивно), `EpochBlock` (материал+вес+лут, `copy()`),
  `Containers`.
- **`menu/`** — 8 меню редакторов (наследуют `ru.kiviuly.mg.api.menu.Menu`):
  `EpochListMenu`, `EpochMenu`, `BlockEditMenu`, `BlockPaletteMenu`, `LootEditorMenu`,
  `EpochPickMenu`, `PhaseTimeMenu`, `KitEditorMenu`.

## Команды

Ядровые (`/mg`, алиасы `/mgcore`, `/kmg`; права `mg.admin`): `create`/`remove`/`list`/
`gui`/`set`/`enable`/`join`/`leave`/`stats`/`reload`/`save`/`debuglog`.
Игровые подкоманды делегируются в `SkyBlockWarsGame.onCommand` **тем же** `/mg`:

- `/mg setcenter <ID>` · `/mg setradius <ID> <n|default>` · `/mg setmaxplayers <ID> <n>`
- `/mg setmode <ID> personal|shared` · `/mg settime <ID> match|fight <n|default>`
- `/mg epochs <ID>` (GUI эпох) · `/mg kit <ID>` (GUI стартового набора)

## Статус проверки

- `[DONE]` **Сборка** — `./gradlew build` ЗЕЛЁНАЯ на 2026-07-24 (с `includeBuild`
  на `../kiviuly-mg-core`). Без соседнего репо сборка НЕ пройдёт.
- `[?]` **Сервер после порта НЕ поднимался.** Нужен деплой ОБОИХ плагинов и чистый
  лог (`MgCore: registered game 'skyblockwars'` + `SkyBlockWars enabled, game registered`).
- `[BUG]` **Тексты игрокам говорят `/sbw`, а команда теперь `/mg`** — 39 вхождений
  `/sbw` в `src/main/resources/messages.yml` (в т.ч. `sbw.help`, все `sbw.usage-*`,
  `sbw.radius-set-no-center`). Игрок получает нерабочую подсказку. Чинить заменой на
  `/mg` (см. инвариант 1 — тексты только из `messages.yml`).
- `[TODO]` **Мёртвые секции конфигов игры.** `src/main/resources/config.yml` всё ещё
  содержит ядровые `arena-defaults`/`match`/`chat`/`hud`/`debug-log` — их читает
  MgCore из СВОЕГО `plugins/MgCore/config.yml`, здесь они ни на что не влияют
  (плагин читает только `skyblockwars.*`). Аналогично `messages.yml`: секции
  `menu.*`/`errors.*`/`admin.*`/`stats.*`/`game.*` перекрыты каталогом ядра (поиск
  идёт ядро → игры) — живут только ключи `sbw.*`. Почистить, оставив `sbw.*`
  (+ решить, где брать `errors.arena-not-found`/`errors.not-a-number`/`menu.back`,
  которые игра использует из ядрового каталога — это ОК, но зависимость неявная).
- `[TODO]` **Документация репо протухла целиком.** `docs/01-architecture.md`,
  `docs/02-making-a-game.md`, `docs/03-commands-and-config.md`, `README.md`,
  `CLAUDE.md`, `AGENTS.md` описывают удалённый каркас, `TemplateGame` и `/sbw`.
  Актуальная архитектура платформы — в `../kiviuly-mg-core/docs/` (`01-architecture`,
  `02-api-reference`, `03-making-a-game`). Живой из локальных — только
  `docs/04-skyblockwars.md` (правила игры), и то с командами `/sbw`.
- `[~]` **Функционал игры** (эпохи: импорт+наследование, тайминги per-arena, кит,
  фазы, лобби-дуэль) — реализован до порта, сборка зелёная, руками НЕ прокликан.
  Порт мог задеть: делегирование команд через `/mg`, открытие меню (`Menu` из
  `mg-api`), `Match.data()`, откат блоков.
- `[DONE]` **Ревью кода (до порта)** — исправлено: `suddenDeath` через `s.eliminate`;
  боссбар в `SbwState` + хук `onCleanup`; `fight-seconds:0`; дроп чужого якоря;
  страховка респавна на осыпавшийся якорь; соло-игра не завершается мгновенно
  (`SbwState.startedCount`, победа «последний выживший» только при 2+ стартовавших).

## Данные (runtime)

- `plugins/MgCore/`: `config.yml`, `messages.yml`, `arenas/<id>.yml`, `snapshots/`,
  `stats.db` — **ядро**.
- `plugins/SkyBlockWars/`: `config.yml` (только секция `skyblockwars.*` реально
  читается), `messages.yml` (ключи `sbw.*`), `game/<ID>.yml` — **игра**.

## Сборка и деплой

```bash
./gradlew build                 # требует соседний репо ../kiviuly-mg-core (includeBuild)
./gradlew deploy                # копирует ТОЛЬКО SkyBlockWars.jar (каталог из .env DEPLOY_DIR)
cd ../kiviuly-mg-core && ./gradlew :mg-core:deploy   # MgCore.jar — отдельно, свой .env
```

Оба плагина обязаны лежать в `plugins/`. MgCore.jar несёт в себе классы `mg-api`.

## Следующий шаг

1. Починить `/sbw` → `/mg` в `messages.yml` (`[BUG]` выше) — игрок видит неверные команды.
2. Поднять сервер с обоими плагинами, снять `[?]`: вход/старт/эпохи/GUI/подкоманды.
3. Почистить мёртвые секции `config.yml`/`messages.yml`.
4. Переписать `README.md`/`CLAUDE.md`/`AGENTS.md`/`docs/01–03` под «игра на MgCore»
   (или удалить 01–03, сославшись на `../kiviuly-mg-core/docs/`).

## Правило

Расширять ЯДРО (новые хуки `Minigame`, API арен/матча) — в репо `../kiviuly-mg-core`,
не здесь. В этом репо — только игровая специфика в `sbw/`. Числа арены-игры — в
`game/<ID>.yml` (`ArenaGameConfig`). Инварианты — [CONVENTIONS.md](CONVENTIONS.md).
