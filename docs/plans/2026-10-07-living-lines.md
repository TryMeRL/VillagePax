# Живые реплики жителей — план реализации

> **Для исполнителя:** план исполняется в этой же сессии, задача за задачей
> (подагенты и воркфлоу заказчик не разрешает). Шаги — чекбоксами.

**Цель:** житель над головой говорит о своём настоящем — голоден, нет
кровати, некому строить, у него родился ребёнок, завтра праздник, вчера
был набег, у ворот враг, льёт дождь, он здесь новенький — и об игроке,
смотря по доверию; а когда сказать нечего — о своём ремесле.

**Почему:** заказчик хочет «хороший мод», жалоба на одинаковость. Поиск
по образцам: главная жалоба на MineColonies — «жители как роботы»,
и ценят именно разговор о *настоящем* положении колонии; в Millénaire
житель «рассказывает о своей жизни, если слушать». Основа в моде уже
есть: фраза над головой (`CitizenEntity.say`), пулы фраз по случаю и
нраву в словаре (`sim/games/Lines`), оклик прохожих (`Passersby`).

**Архитектура:** чистое правило «о чём сказать» (`Chatter.topics` по
записи `Situation`) отделено от мира; сборка положения из мира и сама
фраза — `Chatter.speak`; часы — `ChatterTicker` раз в секунду для
игроков рядом с телами. Ключи словаря — `villagepax.say.<тема>[.<нрав>].<n>`,
подбор — общим `Lines.pickKey` (тот же, что у игр).

**Стек:** Fabric 1.20.1, JUnit 5, Fabric gametest.

**Замысел:** `docs/plans/2026-10-07-bugs-and-plan.md`, «Третий круг».

## Общие ограничения

- Фраза — только рядом с игроком: тело в 6 блоках от него.
- Не чаще раза в 60 с на жителя и раза в 8 с на игрока (иначе гул).
- Молчит: спит или дремлет; за игрой (`Bouts.rivalOf`); прячется
  (`HideAndSeek.at(...).spotOf`); соперник состязания; куклы (набег,
  обоз, союзник) — у них нет записи жителя.
- Чужая фраза поверх не встаёт: `say(..., false)` — недавнюю не перебивает.
- Слова в обоих словарях, наборы ключей равны (`LangTest`); у каждой
  темы не меньше трёх общих фраз (`ChatterTest.everyTopicHasWords`).
- Правило не загружает чанков и не спрашивает выгруженный мир.

## Что проверить глазами при разборе (не покрыто тестами задач)

1. Толпа у ратуши вечером: три жителя рядом не говорят разом — общий
   предел на игрока держит одну фразу в 8 с.
2. Игрок стоит у ратуши своей колонии: хозяину — «Хозяин!», а не «чужак».
3. Ребёнок не говорит о ремесле и о налоге — у него нет ремесла.
4. Житель за партией/в прятках молчит про голод.
5. Деревня, обиженная игроком (доверие ниже −40), не здоровается тепло.

---

### Задача 1: общий подбор ключа фразы

**Файлы:**
- Изменить: `src/main/java/com/villagepax/sim/games/Lines.java`
- Тест: `src/test/java/com/villagepax/sim/games/LinesTest.java`

**Даёт:** `public static String Lines.pickKey(String base, Nature nature,
Predicate<String> known, IntUnaryOperator choose)`;
`public static boolean Lines.sayKey(CitizenEntity body, Citizen citizen,
String base, boolean urgent, Object... args)`.

- [x] Тест: `pickKey("villagepax.say.hungry", LAZY, ...)` берёт из запаса
  нрава, без него — из общего, пустой запас — `base + ".1"`.
- [x] Прогнать — падает: метода нет.
- [x] `pick(Say, ...)` переписать через `pickKey(PREFIX + say.id(), ...)`;
  `sayKey` — как `say/sayNow`, но по базе.
- [x] Прогнать `LinesTest` — зелёный. Коммит.

### Задача 2: о чём сказать — чистое правило

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/life/Chatter.java`
- Тест: `src/test/java/com/villagepax/sim/life/ChatterTest.java`

**Даёт:** `enum Chatter.Topic implements Named` (`BESIEGED, HUNGRY,
NEWBORN, RAIDED, FESTIVAL, HOMELESS, NO_BUILDER, NEWCOMER, RAIN, OWNER,
FRIEND, STRANGER, WARY, WORK`); `record Chatter.Situation(boolean besieged,
boolean hungry, Optional<String> newborn, boolean raidedLately,
boolean festivalTomorrow, boolean homeless, boolean nobodyBuilds,
boolean newcomer, boolean raining, boolean owner, int trust,
Optional<String> trade, boolean child)`;
`static List<Topic> topics(Situation)`; `static Topic choose(List<Topic>,
IntUnaryOperator)`.

Правило: осада и голод — срочное, говорится первым и одно; иначе
наугад из подходящих. Хозяину — `OWNER`; доверие ≥ дружбы — `FRIEND`;
ниже нуля — `WARY`; иначе `STRANGER`. `WORK` — если есть ремесло и не
ребёнок. Ребёнок не говорит о ремесле, налоге и стройке.

- [x] Тесты: осада вытесняет всё; голод — второй; друг получает `FRIEND`,
  обидчик `WARY`, хозяин `OWNER`; ребёнок без `WORK` и `NO_BUILDER`;
  пустое положение даёт хотя бы приветствие.
- [x] Прогнать — падает. Реализовать. Прогнать — зелёный. Коммит.

### Задача 3: фраза в мире

**Файлы:**
- Изменить: `src/main/java/com/villagepax/sim/life/Chatter.java`
- Создать: `src/main/java/com/villagepax/sim/life/ChatterTicker.java`
- Изменить: `src/main/java/com/villagepax/VillagePax.java` (регистрация)
- Тест: `src/gametest/java/com/villagepax/gametest/ChatterTests.java`

**Даёт:** `static Situation Chatter.situation(ServerWorld, Settlement,
Citizen, PlayerEntity, long day)`; `static Optional<Topic>
Chatter.speak(ServerWorld, Settlement, Citizen, CitizenEntity,
PlayerEntity, long day, Random)` — говорит и возвращает тему;
`ChatterTicker.tick(ServerWorld, List<? extends PlayerEntity>)`.

Положение из мира: осада — `settlement.siege()`; голод —
`Needs.isHungry`; ребёнок — ребёнок этого жителя с `lived ≤ 1`, имя —
`firstName`; набег — `today - lastRaid ≤ 2`; праздник —
`FestivalDay.isOn(settlement, day + 1)`; бездомный — `isHomeless`;
некому строить — `BuilderJob.nobodyBuilds`; новенький — без родителей
и `lived - grownAt ≤ 1`; дождь — `world.isRaining()`; хозяин —
`owner().isOwnedBy(player)`; доверие — `reputationOf(player)`.

- [x] Игровой тест: колония, голодный житель рядом с подставным игроком
  — `speak` говорит `HUNGRY`, над головой ключ `villagepax.say.hungry.*`;
  второй вызов сразу — молчит (предел жителя); спящий молчит.
- [x] Прогнать — падает. Реализовать. Прогнать — зелёный. Коммит.

### Задача 4: слова

**Файлы:**
- Изменить: `src/main/resources/assets/villagepax/lang/ru_ru.json`, `en_us.json`
- Тест: `src/test/java/com/villagepax/sim/life/ChatterTest.java`

- [x] Тест `everyTopicHasWords`: у каждой темы ≥ 3 общих фраз в ru;
  у `WORK` — по ≥ 2 на каждое из 10 ремёсел (`villagepax.say.work.<ремесло>.<n>`).
- [x] Прогнать — падает. Написать фразы (ru и en, наборы равны).
  Нрав — свои варианты у нескольких тем (`.lazy.`, `.ambitious.`,
  `.coward.`, `.pious.`). Прогнать `test` целиком (`LangTest`). Коммит.

### Задача 5: документация и прогон

- [x] README: абзац о жителях, говорящих о своей жизни. Страница книги
  не нужна — это видно само.
- [x] Журнал `2026-10-07-bugs-and-plan.md`: статус с коммитом.
- [x] `test` и `runGametest` (`_JAVA_OPTIONS=-Xmx2G`) целиком — зелёные.
  Коммит.
