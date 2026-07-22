# STATE — текущее состояние

Last updated: 2026-07-22

## Кратко

Проект **SkyBlockWars Reborn** отпочкован от шаблона MCMGP: игро-независимый каркас
платформы мини-игр + одна точка расширения (`Minigame`). Ребрендинг выполнен
(package `ru.kiviuly.skyblockwars`, плагин `SkyBlockWars`, команда `/sbw`, ветка
`develop`), сборка зелёная (`SkyBlockWars-1.0.0.jar`). Игра SkyBlockWars ещё НЕ
реализована — активна заглушка `TemplateGame`; впереди эпохи/спавны/фазы матча.

## Что развёрнуто (каркас)

- **arena/** — `Arena` (мир, лобби, спавны, лимиты, тайминги, обобщённая
  `settings`-мапа), `ArenaManager` (реестр + player→session + join/leave),
  `ArenaCheck` (валидатор CRITICAL/WARNING/GOOD), `SetupMarkers` (маркеры точек).
- **game/** — `GamePhase` (LOBBY/COUNTDOWN/RUNNING/ENDING), `MatchPlayer`,
  `MatchResult`, `Minigame` (абстрактная точка расширения), `GameSession`
  (движок жизненного цикла), `TemplateGame` (заглушка `Minigame`).
- **player/** — `PlayerSnapshot` (save/clear/restore, `snapshots/<uuid>.yml`,
  переживает рестарт).
- **menu/** — `Menu` (InventoryHolder) + `MenuListener`, `AnvilInputMenu`,
  `ArenaSelectMenu`, `ArenaHubMenu`, `ArenaSettingsMenu`, `ArenaPointsMenu`.
- **listener/** — `GameListener`, `ProtectionListener`, `ChatListener`, `SetupListener`.
- **command/** — `MinigameCommand` (`/sbw` + алиасы `/skyblockwars`, `/sbwreborn`).
- **stats/** — `StatsRepository` (SQLite: wins/loses/kills/played, async-запись).
- **ui/** — `GameScoreboard` (сайдбар), `GameBossBar` (фаза/таймер).
- **util/** — `Keys` (PDC), `Items` (`fromSpec` из YML-спеки), `Msg`
  (каталог `messages.yml`), `DebugLog` (`/sbw debuglog`, ASCII, кольцевой буфер).
- **SkyBlockWarsPlugin** — bootstrap/wiring; держит `game()`, `ArenaManager`, `StatsRepository`.
- **Конфиги** — `config.yml`, `plugin.yml`, `messages.yml` (тексты игрокам).

## Статус проверки

- `[DONE]` **Сборка** — `./gradlew clean build` ЗЕЛЁНЫЙ, jar собран
  (`build/libs/SkyBlockWars-1.0.0.jar`, ~102 КБ). Компиляция с первого раза.
- `[DONE]` **Скрипт переименования** — `rename.sh` протестирован на одноразовой
  копии: `com.acme.spleef Spleef spleef` → перенос пакета, переименование класса,
  правки plugin.yml/gradle, 0 старых ссылок, переименованный проект компилируется.
- `[?]` **Смоук на сервере** — НЕ проводился (по решению владельца — только
  сборка). Ожидаемый успех: `[SkyBlockWars] SkyBlockWars enabled` в логе, ноль стектрейсов.
- `[?]` **Плейтест матча** — НЕ проводился (демо-игры нет; полноценно проверяется
  после реализации конкретного `Minigame`).

## Чего НЕТ (осознанно)

- Реальной игры. Активен `TemplateGame` — заглушка `Minigame` (no-op хуки).
  Это ожидаемо: шаблон поставляется голым.

## Следующий шаг (для нового проекта на базе шаблона)

1. Ребренд под свой проект — скрипт `rename.sh` / `rename.bat` (package/plugin/
   command/artifact); точные аргументы — в шапке скрипта.
2. Реализовать `Minigame` под свою игру и зарегистрировать в `SkyBlockWarsPlugin.onEnable`
   вместо `new TemplateGame(this)` — см. [`docs/02-making-a-game.md`](../docs/02-making-a-game.md).
3. Собрать, задеплоить, смоук на сервере, снять пометки `[?]` выше, обновить STATE.

## Куда двигаться в самом шаблоне

Держать каркас чистым и обобщённым: расширения ядра — для любой игры, игровая
специфика — только через `Minigame`. Инварианты — [CONVENTIONS.md](CONVENTIONS.md).
