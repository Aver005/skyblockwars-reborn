# 03 — Команды и конфигурация

Справочник команды `/mg` и всех конфигов. Package root: `ru.kiviuly.mcmgp`.

Команда: `/mg` (алиасы `/minigame`, `/mcmgp`). Права: `mcmgp.admin` — админ-команды,
`mcmgp.admin.debug` — отладка; обе `default: op`.

## Команды игрока

| Команда | Что делает |
|---|---|
| `/mg` | открыть меню выбора арены (`ArenaSelectMenu`) |
| `/mg join <ID>` | войти в арену `<ID>` (в лобби; матч стартует по набору игроков) |
| `/mg leave` | выйти из лобби/матча — игрок восстанавливается из снапшота |
| `/mg stats [игрок]` | статистика: свою или указанного игрока |
| `/mg help` | список доступных команд |

## Команды админа (`mcmgp.admin`)

| Команда | Что делает |
|---|---|
| `/mg create <ID>` | создать арену `<ID>`; мир = твой текущий, числа — из `arena-defaults` |
| `/mg remove <ID>` | удалить арену |
| `/mg enable <ID>` | включить арену (сначала прогоняет `ArenaCheck`) |
| `/mg disable <ID>` | выключить арену |
| `/mg gui <ID>` | хаб настройки арены (`ArenaHubMenu`): точки, числа, действия |
| `/mg setlobby <ID>` | лобби арены = твоя текущая позиция |
| `/mg addspawn <ID>` | выдать маркер спавна; поставь блок — точка записана |
| `/mg set <ID> <key> <value>` | игро-специфичная числовая настройка (секция `settings`) |
| `/mg check <ID>` | валидатор арены: `CRITICAL` / `WARNING` / `GOOD` |
| `/mg start <ID>` | форс-старт матча на арене |
| `/mg stop <ID\|all>` | остановить матч на арене (или `all` — все) |
| `/mg list` | список арен и их статус |
| `/mg reload` | перечитать `config.yml` и `messages.yml` |
| `/mg save` | записать все арены на диск |
| `/mg debuglog <on\|off\|save\|clear>` | отладочный лог (право `mcmgp.admin.debug`) |

Настройка арены задумана **без правки YML руками**: команды и `/mg gui` выдают
маркеры-предметы и меняют числа, а `Arena.save` пишет файл сам. `/mg debuglog`:
`on/off` — тумблер (переживает рестарт), `save` — выгрузка буфера в
`plugins/MCMGP/logs/`, `clear` — очистка буфера.

## config.yml

Общие числа и флаги сервера. Тексты сюда не кладём — они в `messages.yml`.

```yaml
# Значения по умолчанию для НОВЫХ арен (у каждой арены потом свои копии).
arena-defaults:
  min-players: 2
  max-players: 12
  lobby-countdown-seconds: 20     # отсчёт в лобби до старта, когда набран минимум
  countdown-full-seconds: 5       # укороченный отсчёт, когда лобби заполнено
  match-duration-seconds: 600     # 0 = без лимита времени (матч до победителя)

# Поведение матча (общее для всех игр на сервере).
match:
  spectator-on-death: true        # смерть → спектатор (fake death), без ваниль-респавна
  restore-world: true             # откат изменённых блоков и удаление сущностей после матча
  return-to-lobby: true           # выживших/спектаторов вернуть в лобби арены

# Чат во время матча.
chat:
  scoped-to-arena: true           # игроки видят только участников своей арены
  format: "<gray>[<arena>] <white><player><gray>: <reset><message>"

# HUD (если модуль включён).
hud:
  scoreboard: true                # сайдбар
  bossbar: true                   # босс-бар с фазой/таймером

# Отладочный лог: [DBG] в консоль на ключевые действия (только ASCII).
debug-log:
  enabled: false
  console: true
  # buffer-lines: 20000           # опц.: размер кольцевого буфера (мин. 100)
```

| Ключ | Смысл |
|---|---|
| `arena-defaults.*` | стартовые значения для `/mg create`; правятся потом на арене |
| `match.spectator-on-death` | при `true` летальный урон в матче не убивает ванильно, а переводит в спектатора |
| `match.restore-world` | откатывать мир после матча (блоки/сущности из сессии) |
| `match.return-to-lobby` | телепорт участников в лобби арены на выходе |
| `chat.scoped-to-arena` | ограничить матч-чат участниками арены |
| `chat.format` | формат строки чата (MiniMessage; плейсхолдеры `<arena>`, `<player>`, `<message>`) |
| `hud.scoreboard` / `hud.bossbar` | включение сайдбара / босс-бара |
| `debug-log.enabled` | стартовое состояние `/mg debuglog` (переживает рестарт) |
| `debug-log.console` | дублировать записи в консоль строкой `[DBG]` |
| `debug-log.buffer-lines` | размер кольцевого буфера лога (опц., по умолч. 20000) |

## arenas/&lt;id&gt;.yml

По файлу на арену. Обычно им управляют команды и `/mg gui` (после правок делай
`/mg save`); руками можно, но после — `/mg reload`. Пример:

```yaml
display-name: "Арена 1"           # имя (MiniMessage)
description: ""                   # описание (MiniMessage)
world: world                      # имя мира арены
enabled: false                    # включена ли (см. /mg enable + ArenaCheck)
min-players: 2
max-players: 12
lobby-countdown-seconds: 20
countdown-full-seconds: 5
match-duration-seconds: 600       # 0 = без лимита
lobby: { ... }                    # Location лобби (пишется /mg setlobby)
spawns:                           # список Location спавнов (маркеры /mg addspawn)
  - { ... }
settings:                         # игро-специфичные ЧИСЛА (getSetting/setSetting)
  kill-y: 60                      # ← ключи и смысл определяет твоя игра
  shrink-at: 30
```

Секция `settings` — пространство имён твоей игры: ядро её только хранит, читает её
`arena.getSetting("kill-y", 60)` (см. [02-making-a-game.md](02-making-a-game.md)).
Задаётся командой `/mg set <ID> <key> <value>` или `±`-редактором в `/mg gui`.

## messages.yml

**Все** тексты игрокам — здесь, в формате MiniMessage; в Java ни одной
захардкоженной строки (инвариант 1). Читается через `util/Msg`
(`Msg.get(key, ...)`, `Msg.send(...)`). Новый текст = новый ключ; дефолты зашиты в
jar, поэтому добавленные ключи работают без ручного слияния после обновления.

Две ловушки при написании ключей (инвариант 8):

- **Плейсхолдеры не должны совпадать с именами тегов MiniMessage.** Для чисел
  используй `<n>` (а не, например, `<gold>`), иначе плейсхолдер съест одноимённый
  тег. В коде — `Msg.ph("n", value)`.
- **Ключи YAML не должны быть голыми `on/off/yes/no`** — YAML парсит их как
  boolean. Пиши `state-on` / `state-off` и т.п.

Полные инварианты и стиль — [.memories/CONVENTIONS.md](../.memories/CONVENTIONS.md).
