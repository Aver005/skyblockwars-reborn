# SkyBlockWars — Minecraft Minigame Platform (шаблон)

![Paper](https://img.shields.io/badge/Paper-26.1.2-blue?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?style=flat-square)
![Kind](https://img.shields.io/badge/kind-bare%20framework-lightgrey?style=flat-square)

Переиспользуемый, **игро-независимый** каркас мини-игр для Paper 26.1.2 (Java 25,
Gradle). Даёт готовую платформу — арены, жизненный цикл матча, сетап-GUI,
SQLite-статистику и HUD — с одной точкой расширения, куда ты вставляешь правила
своей игры. Это **голый** фреймворк: настоящей демо-игры внутри нет, только
абстрактный класс `Minigame` и заглушка `TemplateGame`, которую ты заменяешь.

Шаблон вырос из мини-игры «побег из тюрьмы» (last man standing), обобщённой до
чистого фреймворка. Прежняя игровая специфика вынесена целиком в `Minigame` —
ядро о ней не знает.

## Что даёт из коробки

- **Арены** — мир, лобби, спавны, лимиты игроков и таймингов плюс обобщённая мапа
  числовых настроек под твою игру. Мультиарена, один YML-файл на арену.
- **Жизненный цикл матча** — `LOBBY → COUNTDOWN → RUNNING → ENDING` в одном движке
  (`GameSession`): отсчёты, таймер длительности, укороченный старт при полном лобби.
- **Сетап без правки конфигов руками** — `/sbw gui <ID>`: хаб настройки, `±`-редактор
  чисел, выдача маркеров-предметов для лобби и спавнов, валидатор арены перед включением.
- **Нулевой след** — снапшот игрока до матча и полный откат мира после (изменённые
  блоки, заспавненные сущности). Игрок не уносит игровые предметы и не теряет свои.
- **Статистика** — SQLite: победы / поражения / убийства / сыграно, `/sbw stats`.
- **HUD** — сайдбар и босс-бар с фазой и таймером (включаются в конфиге).
- **Точка расширения** — `Minigame`: наследуешь, реализуешь хуки, регистрируешь.
  Всё игро-специфичное живёт там; ядро не трогаешь.

## Быстрый старт

```bash
./gradlew build                                   # jar → build/libs/SkyBlockWars-1.0.0.jar
./gradlew deploy -PdeployDir=<server>/plugins     # сборка + копия в тестовый сервер
```

JDK 25 скачается сам (Gradle toolchain + foojay-resolver) — вручную ставить нечего.
Без `-PdeployDir` задача `deploy` кладёт jar в `build/deploy` (просто чтобы не падать).

Запусти сервер. Успех выглядит так: строка `[SkyBlockWars] SkyBlockWars enabled` в логе и **ноль**
стектрейсов. Дальше — арена без единого конфига руками:

```
/sbw create arena1        создать арену (мир = твой текущий)
/sbw gui arena1           хаб настройки: лобби, спавны, числа
/sbw check arena1         валидатор — почини CRITICAL
/sbw enable arena1        включить; игроки заходят через /sbw или /sbw join arena1
```

**Свою игру** пишут ровно в одном месте — наследник `Minigame`. Полная пошаговая
инструкция с примером класса — в [docs/02-making-a-game.md](docs/02-making-a-game.md).

## Структура пакетов

Package root: `ru.kiviuly.skyblockwars`.

| Пакет | Назначение |
|---|---|
| `util/` | `Keys` (PDC-ключи), `Items` (сборка предметов + `fromSpec` из YML-спеки), `Msg` (каталог `messages.yml`), `DebugLog` (`/sbw debuglog`) |
| `arena/` | `Arena` (конфиг площадки), `ArenaManager` (реестр + join/leave), `ArenaCheck` (валидатор), `SetupMarkers` (маркеры точек) |
| `player/` | `PlayerSnapshot` (снапшот/очистка/откат игрока, переживает рестарт) |
| `game/` | `GamePhase`, `MatchPlayer`, `MatchResult`, **`Minigame`** (точка расширения), `GameSession` (движок), `TemplateGame` (заглушка) |
| `stats/` | `StatsRepository` (SQLite: wins/loses/kills/played) |
| `ui/` | `GameScoreboard` (сайдбар), `GameBossBar` (фаза/таймер) |
| `menu/` | `Menu` (InventoryHolder) + меню сетапа и выбора арены, `AnvilInputMenu` (ввод текста) |
| `listener/` | `GameListener`, `ProtectionListener`, `ChatListener`, `SetupListener` |
| `command/` | `MinigameCommand` (`/sbw`) |
| — | `SkyBlockWarsPlugin` — bootstrap/wiring; держит зарегистрированный `Minigame` (`game()`), `ArenaManager`, `StatsRepository` |

Подробнее — в [docs/01-architecture.md](docs/01-architecture.md).

## Свой проект из шаблона

Для нового проекта на базе шаблона есть скрипт переименования (`rename.sh` для
Linux/macOS/Git Bash, `rename.bat`/`rename.ps1` для Windows): он ребрендит
Java-пакет, имя плагина, команду, права, главный класс и артефакт за один проход
(включая перенос каталога пакета). Запуск — на свежем клоне, до первой сборки,
из корня репозитория:

```bash
./rename.sh <new.base.package> <PluginName> <command>
# пример: ./rename.sh com.acme.spleef Spleef spleef
```
```bat
rem Windows:
rename.bat com.acme.spleef Spleef spleef
```

Скрипт правит файлы на месте — сделай коммит/бэкап до запуска и проверь `git diff`
после. Затем реализуй `Minigame` и собери (`./gradlew build`).

## Требования

- **Paper 26.1.2** (требует Java 25).
- **Java 25** — Gradle toolchain скачает JDK сам, отдельная установка не нужна.
- Ноль внешних зависимостей плагина, ноль NMS: только публичный Paper API,
  Adventure/MiniMessage и встроенный в Paper SQLite.

## Для разработчиков и агентов

Маршрут: **[CLAUDE.md](CLAUDE.md)** → [`.memories/INDEX.md`](.memories/INDEX.md).
Там порядок работы, железные правила и определение «сделано». Как устроено ядро —
[docs/01-architecture.md](docs/01-architecture.md); как сделать игру —
[docs/02-making-a-game.md](docs/02-making-a-game.md); команды и конфиг —
[docs/03-commands-and-config.md](docs/03-commands-and-config.md).
