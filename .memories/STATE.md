# STATE — текущее состояние

Last updated: 2026-07-22

## Кратко

Шаблон **MCMGP** развёрнут: игро-независимый каркас платформы мини-игр + одна
точка расширения (`Minigame`). Демо-игры внутри НЕТ — это bare framework.
Package root `ru.kiviuly.mcmgp`, плагин `MCMGP`, команда `/mg`.

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
- **command/** — `MinigameCommand` (`/mg` + алиасы `/minigame`, `/mcmgp`).
- **stats/** — `StatsRepository` (SQLite: wins/loses/kills/played, async-запись).
- **ui/** — `GameScoreboard` (сайдбар), `GameBossBar` (фаза/таймер).
- **util/** — `Keys` (PDC), `Items` (`fromSpec` из YML-спеки), `Msg`
  (каталог `messages.yml`), `DebugLog` (`/mg debuglog`, ASCII, кольцевой буфер).
- **McmgpPlugin** — bootstrap/wiring; держит `game()`, `ArenaManager`, `StatsRepository`.
- **Конфиги** — `config.yml`, `plugin.yml`, `messages.yml` (тексты игрокам).

## Статус проверки

- `[DONE]` **Сборка** — `./gradlew clean build` ЗЕЛЁНЫЙ, jar собран
  (`build/libs/MCMGP-1.0.0.jar`, ~102 КБ). Компиляция с первого раза.
- `[DONE]` **Скрипт переименования** — `rename.sh` протестирован на одноразовой
  копии: `com.acme.spleef Spleef spleef` → перенос пакета, переименование класса,
  правки plugin.yml/gradle, 0 старых ссылок, переименованный проект компилируется.
- `[?]` **Смоук на сервере** — НЕ проводился (по решению владельца — только
  сборка). Ожидаемый успех: `[MCMGP] MCMGP enabled` в логе, ноль стектрейсов.
- `[?]` **Плейтест матча** — НЕ проводился (демо-игры нет; полноценно проверяется
  после реализации конкретного `Minigame`).

## Чего НЕТ (осознанно)

- Реальной игры. Активен `TemplateGame` — заглушка `Minigame` (no-op хуки).
  Это ожидаемо: шаблон поставляется голым.

## Следующий шаг (для нового проекта на базе шаблона)

1. Ребренд под свой проект — скрипт `rename.sh` / `rename.bat` (package/plugin/
   command/artifact); точные аргументы — в шапке скрипта.
2. Реализовать `Minigame` под свою игру и зарегистрировать в `McmgpPlugin.onEnable`
   вместо `new TemplateGame(this)` — см. [`docs/02-making-a-game.md`](../docs/02-making-a-game.md).
3. Собрать, задеплоить, смоук на сервере, снять пометки `[?]` выше, обновить STATE.

## Куда двигаться в самом шаблоне

Держать каркас чистым и обобщённым: расширения ядра — для любой игры, игровая
специфика — только через `Minigame`. Инварианты — [CONVENTIONS.md](CONVENTIONS.md).
