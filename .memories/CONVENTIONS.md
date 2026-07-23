# CONVENTIONS — стиль и инварианты

Last updated: 2026-07-24 (после порта на MgCore)

## Стиль кода

- **Java 25**, Paper API. Никакого NMS/рефлексии — только публичный API.
- **Скобки Allman** (открывающая на своей строке) — как во всём коде базы.
  Короткие guard-ветки в одну строку: `if (x == null) {return;}`.
- **Импорты, а не FQN.** Никаких инлайновых `net.kyori.adventure...Foo x` — всегда
  `import`. Группы: `java` → `ru.kiviuly` → `net.kyori` → `org.bukkit`. При
  коллизии имён (adventure `BossBar` vs `org.bukkit.boss.BossBar`, `mg.api.menu.Menu`
  vs чужие) импортируй тот, что чаще в файле; второй — полным именем.
- **Пакеты по подсистемам**: игра целиком в `sbw/` (`sbw/epoch/`, `sbw/menu/`),
  без god-utils. Всё, что не про правила SkyBlockWars, — не сюда, а в mg-core.
- **Комментарии/Javadoc — по-русски**, только там, где код сам не объясняет.

## Где что живёт (после порта)

Ядро — отдельный плагин **MgCore** (репо `../kiviuly-mg-core`, модули `mg-api`
контракты + `mg-core` реализация). Игра видит ТОЛЬКО `mg-api`:

| Нужно | Брать из |
|---|---|
| Матч, игроки, результат, фаза | `ru.kiviuly.mg.api.game.{Match, MatchPlayer, MatchResult, GamePhase}` |
| Точка расширения | `ru.kiviuly.mg.api.game.Minigame` |
| Арена и реестр | `ru.kiviuly.mg.api.arena.{Arena, ArenaService}` |
| GUI | `ru.kiviuly.mg.api.menu.{Menu, AnvilInputMenu}` |
| Тексты / PDC / предметы / лог | `ru.kiviuly.mg.api.util.{Msg, Keys, Items, DebugLog}` |
| Фасад платформы | `ru.kiviuly.mg.api.MgCore` (из Bukkit `ServicesManager`) |

**`GameSession` больше нет — это `Match`.** Ищешь класс каркаса в `ru.kiviuly.skyblockwars.*`
— его там нет, он переехал; не воссоздавай локальную копию.

**Расширять ядро — в репо mg-core.** Нужен новый хук `Minigame` или метод `Match` —
правь `../kiviuly-mg-core/mg-api` (+ вызов в `mg-core`), собери оба, и только потом
используй здесь. Обобщённо, чтобы годилось любой игре: игровой специфики в ядре нет.

## Инварианты (не ломать)

1. **Тексты игрокам — ТОЛЬКО из `messages.yml`** (MiniMessage) через `Msg`
   (`ru.kiviuly.mg.api.util.Msg`). Ни одной захардкоженной строки для игрока в Java.
   Свои ключи — в неймспейсе **`sbw.*`**: каталоги ищутся по порядку (ядро → игры),
   и голый `errors.foo` в нашем файле будет перекрыт ядровым. Файл домешивается
   вызовом `Msg.merge(this)` в `onEnable`.
2. **Логи сервера — только ASCII** (Windows-консоль коверкает не-латиницу).
   Игрокам — MiniMessage; в консоль — ASCII (`DebugLog` фильтрует не-ASCII в `?`).
3. **Данные на предметах/сущностях — только PDC** (`Keys` из `mg-api`). Парсить
   лор/имена запрещено (лор — отображение).
4. **Любое изменение блока мира в матче** — сначала `match.rememberBlock(block)`,
   иначе cleanup не откатит. **Заспавненные сущности** — `match.trackEntity(...)`.
5. **Снапшот игрока неприкосновенен**: снапшот/восстановление ведёт ЯДРО. Игра не
   лезет в инвентарь вне матча; всё, что выдала (кит, добыча), обязано либо остаться
   в матче, либо быть учтено `trackEntity` при дропе.
6. **Игровая логика — main thread.** Async — только SQLite (в ядре). Никаких
   async-обращений к Bukkit API.
7. **GUI — только через `ru.kiviuly.mg.api.menu.Menu`** (InventoryHolder). Заголовки
   инвентарей не сравниваем. Слушателя меню регистрирует ядро — свой не заводим.
8. **MiniMessage: плейсхолдеры не должны совпадать с именами тегов** — для чисел
   `<n>` (не `<gold>`). **Ключи YAML не должны быть голыми `on/off/yes/no`**
   (парсятся как boolean) — пиши `state-on`/`state-off`.
9. **Своих команд у плагина нет.** `plugin.yml` без секции `commands`; подкоманды
   приходят в `Minigame.onCommand` от ядровой `/mg`. Значит и в текстах команда —
   `/mg <sub>`, а не `/sbw` (сейчас нарушено, см. STATE `[BUG]`).

## Правило границы

Это игра, а не платформа. Состояние матча — в `Match.data()` (`SbwState`), не в полях
`SkyBlockWarsGame` (одна инстанция на сервер: поле утечёт между аренами и переживёт
форс-стоп). Числа арены-игры — в `game/<ID>.yml` (`ArenaGameConfig`), не новыми
полями ядрового `Arena`.

## Процесс

- Сборка требует соседний репо `../kiviuly-mg-core` (`includeBuild` в
  `settings.gradle.kts`); `mg-api` — `compileOnly`, в рантайме его классы даёт jar MgCore.
- Проверка изменений: `./gradlew deploy` (каталог из `.env`/`DEPLOY_DIR`) **плюс**
  `cd ../kiviuly-mg-core && ./gradlew :mg-core:deploy`, если ядро тоже менялось →
  перезапуск сервера → лог без стектрейсов (`MgCore: registered game 'skyblockwars'`,
  `SkyBlockWars enabled, game registered: skyblockwars`) + ручная проверка фичи.
- После нетривиального изменения — обнови `.memories/STATE.md` (см. `INDEX.md`,
  «Правило поддержки»). Память противоречит коду → прав код, чини память.
