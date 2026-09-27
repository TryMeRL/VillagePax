# Праздники на ярмарке — план работ

> **Исполнение:** в одной сессии, без подагентов и воркфлоу (решение заказчика). Шаги — чекбоксы.

**Цель:** раз в лунный месяц деревня каждого народа и колония игрока празднуют на ярмарке:
затейник трубит зов, жители водят хоровод вокруг столба, на столе пироги, вечером фейерверк;
игрок играет с жителями в три состязания (поиск, ловля, стрельба), получает ленты и кубок
и меняет ленты в лавке затейника на шапку народа, флажки и фейерверки.

**Архитектура.**
- **Календарь** — чистая функция дня и фазы луны народа.
- **Праздник** — данные народа (`villagepax/festivals/*.json`).
- **Ярмарка** — новое здание-мастерская у всех семи народов. Места праздника (загон,
  стрелковая черта) отмечены новыми маркерами схемы, сердце праздника и мишени — блоками.
- **Затейник** — новое ремесло с логикой `entertain`: стоит у прилавка, жонглирует, зазывает.
- **День праздника** подменяет решение жителя: гулянье у столба вместо работы. Состязания
  идут через общий движок `Match`: отсчёт, полоса со счётом, места, призы. Виды состязаний —
  `Hunt`, `Chase`, `Archery`.
- **Состояние.** Что поставил праздник и кто что получил — в `SettlementManager`, как убранство
  улиц. Кодек поселения не трогается: он полон.

**Стек:** Minecraft 1.20.1, Fabric, Yarn 1.20.1+build.10, Java 17, owo-lib, GeckoLib;
модульные проверки — JUnit 5 (`./gradlew test`), игровые — Fabric gametest
(`./gradlew runGametest`); арт — сценарии Python в `tools/` (Pillow).

**Замысел:** [docs/plans/2026-09-27-festivals-design.md](2026-09-27-festivals-design.md) — план
выводится из него, и читать их надо вместе.

**Порядок исполнения:** 1 → 2 → 3 → 4 → 8 → 5 → 6 → 7 → 9 → 10 → 11 … 24. Задача 5 стоит
в плане рядом с обликом по смыслу, а исполняется после ярмарки: её проверке нужна схема.

**Время — довод, а не спрос у мира.** Мир игровых проверок общий, и менять в нём время суток
нельзя: соседние проверки живут по распорядку. Поэтому всё праздничное принимает день
и время параметром (`FestivalDay.today(settlement, day)`, `Feast.tend(..., day)`,
`Matches.start(..., day, timeOfDay)`, `WorkTicker.decide(..., part, day)`), а тикер мира
подставляет настоящие. Тот же приём, что у суточных решений: «раз в N дней» — функция
от дня-числа.

## Общие ограничения

- Модуль `villagepax`, пакет `com.villagepax`; комментарии и javadoc — по-русски, в тоне кода:
  не «что», а «почему».
- Народ добавляется данными: никаких `if (culture == …)` вне данных; виды состязаний, зверьки
  и вещицы — закрытые перечисления в коде, как черты и домены.
- Кодек поселения (16 полей) и снимок разговора `QuestView` (16 полей) не расширять.
- Мир игровых проверок общий: всё поставленное убирать в `finally`; площадь больше 8 блоков
  от угла — `WIDE_STRUCTURE`; проверки, спавнящие зверьков и стрелы, — в своих `batchId`.
- Затирать в мире только белый список: воздух и заменимое. Чужой блок на месте нашего —
  не трогать и не убирать.
- Каждый новый блок: блокстейт, модель предмета, добыча, оба словаря, тег инструмента
  (`BlockResourcesTest`); каждый ключ — в `ru_ru` и `en_us` (`LangTest`).
- Арт пишется генератором (`tools/make-festival.py`), генератор вписан в `check-generated.py`.
- Блок в схеме ярмарки колония обязана уметь сделать: дерево, камень, палки и рецепты мода
  из них. Никакого железа, шерсти, красителей.
- Числа, выражающие решение по игре, в проверках пишутся руками, а не читаются из констант.
- Каждую проверку проходимости и уборки доказывать поломкой: временно сломать код, увидеть
  красное, вернуть.
- Ничего не публиковать и не пушить; коммиты локальные, в конце
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Книга-справочник, README и `docs/datapacks.md` дописываются тем же коммитом, что механика,
  или последней задачей — но до сдачи.

## Review Focus

1. **Сервер остановлен посреди состязания.** Вещицы, зверьки и праздничные луки не должны
   пережить перезапуск; пироги прошлого праздника исчезают при первом взгляде на ярмарку.
   Проверка — задачи 13 и 17.
2. **Чужой блок на месте нашего.** Игрок поставил цветок туда, где стоял пирог, или сундук
   на место вещицы. Уборка трогает только то, что там всё ещё наше. Проверка — задачи 13 и 18.
3. **Ярмарка без мишени или с затоптанным загоном** (сломали, набег). Состязание недоступно
   с названной причиной, а не падает и не висит. Проверка — задачи 19 и 20.
4. **Двое игроков у одного затейника.** Второй «Начать» во время идущего состязания получает
   «идёт другое», а приз один на игрока, а не на состязание. Проверка — задачи 17 и 21.
5. **Колония с ярмаркой, но без затейника** (ушёл, умер, переназначен). Праздника нет,
   прибавки нет, экран честно говорит почему. Проверка — задачи 11 и 16.

---

## Часть A. Основа

### Task 1: календарь праздников

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/festival/FestivalCalendar.java`
- Проверка: `src/test/java/com/villagepax/sim/festival/FestivalCalendarTest.java`

**Интерфейсы:**
- Производит: `FestivalCalendar.LUNAR_MONTH` (8), `moonPhase(long day) → int`,
  `isFestivalDay(long day, int phase) → boolean`, `daysUntil(long today, int phase) → int`.

- [ ] **Шаг 1. Проверка**

```java
package com.villagepax.sim.festival;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FestivalCalendarTest {

    @Test
    void theMoonFollowsTheDayLikeVanilla() {
        for (long day = -20; day <= 20; day++) {
            long time = day * 24_000L + 100;
            // Ванильная DimensionType#getMoonPhase — буквально.
            int vanilla = (int) (time / 24000L % 8L + 8L) % 8;
            if (time < 0 && time % 24000L != 0) {
                // Ваниль делит с усечением к нулю; для отрицательного времени
                // её ответ — это фаза дня, начатого раньше. Отрицательного
                // времени в мире не бывает: сравнение — только для прошлого.
                continue;
            }
            assertEquals(vanilla, FestivalCalendar.moonPhase(day), "день " + day);
        }
        assertEquals(7, FestivalCalendar.moonPhase(-1));
    }

    @Test
    void aPeopleCelebratesOncePerLunarMonth() {
        int festivals = 0;
        for (long day = 0; day < 80; day++) {
            if (FestivalCalendar.isFestivalDay(day, 4)) {
                festivals++;
            }
        }
        assertEquals(10, festivals);
    }

    @Test
    void daysUntilCountsForwardAndTodayIsZero() {
        assertEquals(0, FestivalCalendar.daysUntil(4, 4));
        assertEquals(3, FestivalCalendar.daysUntil(1, 4));
        assertEquals(7, FestivalCalendar.daysUntil(5, 4));
        assertEquals(1, FestivalCalendar.daysUntil(-1, 0));
    }
}
```

- [ ] **Шаг 2.** `./gradlew test --tests com.villagepax.sim.festival.FestivalCalendarTest` — падает:
  класса нет.
- [ ] **Шаг 3. Код**

```java
package com.villagepax.sim.festival;

/**
 * Календарь праздников: у народа праздник раз в лунный месяц, в свою фазу луны.
 * <p>
 * Чистая арифметика по номеру дня, и потому ничего не хранится: день праздника
 * не записан ни в поселении, ни в мире, его спрашивают у календаря. Хранимый
 * «следующий праздник» разошёлся бы с календарём при первой же команде
 * {@code /time set}, а вычисленный разойтись не может.
 * <p>
 * Фаза — та же, что рисует небо: ванильная {@code getMoonPhase} делит время
 * на сутки и берёт остаток от восьми. Поэтому норманнская ярмарка в полнолуние
 * — это действительно полная луна над площадью, а праздник фонарей ямато —
 * тёмная ночь новолуния.
 */
public final class FestivalCalendar {

    /** Лунный месяц Minecraft: восемь фаз, по одной на сутки. */
    public static final int LUNAR_MONTH = 8;

    private FestivalCalendar() {
    }

    /** Фаза луны дня: 0 — полнолуние, 4 — новолуние. */
    public static int moonPhase(long day) {
        return (int) Math.floorMod(day, (long) LUNAR_MONTH);
    }

    public static boolean isFestivalDay(long day, int phase) {
        return moonPhase(day) == Math.floorMod(phase, LUNAR_MONTH);
    }

    /** Сколько дней до праздника: 0 — сегодня. */
    public static int daysUntil(long today, int phase) {
        return Math.floorMod(Math.floorMod(phase, LUNAR_MONTH) - moonPhase(today), LUNAR_MONTH);
    }
}
```

- [ ] **Шаг 4.** Та же команда — зелёная.
- [ ] **Шаг 5.** Коммит `feat: календарь праздников — фаза луны народа`.

### Task 2: праздник как данные народа

**Файлы:**
- Создать: `src/main/java/com/villagepax/core/festival/Festival.java` (запись и кодек),
  `ContestKind.java`, `Critter.java`, `TokenKind.java`, `Festivals.java` (загрузчик).
- Изменить: `src/main/java/com/villagepax/VillagePax.java` — зарегистрировать загрузчик.
- Проверка: `src/test/java/com/villagepax/core/festival/FestivalCodecTest.java`

**Интерфейсы:**
- Производит:
  - `Festival(Identifier culture, String name, int moonPhase, Fireworks fireworks,
    List<Contest> contests, List<Prize> prizes)`, `Festival.CODEC`;
  - `Festival.Contest(ContestKind kind, String name, Optional<TokenKind> token, int count,
    Optional<Critter> critter)` и `pieces() → int` — сколько вещиц, зверьков или выстрелов;
  - `Festival.Prize(Identifier item, int count, int price)`;
  - `Festival.Fireworks(List<Integer> colors, String shape)`;
  - `ContestKind {HUNT, CHASE, ARCHERY}`, `Critter {PIG, CHICKEN, FOX, RABBIT, SHEEP}`,
    `TokenKind {EGG, JADE, OMAMORI, RUNE, HORSESHOE, GEM, FIREFLY}` — у каждого `id()` и `CODEC`
    через `EnumCodecs.of`;
  - `Festivals.of(Identifier culture) → Optional<Festival>`, `Festivals.all()`.

- [ ] **Шаг 1. Проверка кодека**

```java
package com.villagepax.core.festival;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FestivalCodecTest {

    private static final String NORMAN = """
            {"culture": "villagepax:norman", "name": "villagepax.festival.norman", "moon_phase": 0,
             "fireworks": {"colors": [16711680, 16766720], "shape": "star"},
             "contests": [
               {"kind": "chase", "name": "a", "critter": "pig"},
               {"kind": "archery", "name": "b"},
               {"kind": "hunt", "name": "c", "token": "egg"}],
             "prizes": [{"item": "minecraft:firework_rocket", "count": 3, "price": 1}]}""";

    private static Festival parse(String json) {
        return Festival.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElse(null);
    }

    @Test
    void aFestivalReadsWhole() {
        Festival festival = parse(NORMAN);
        assertNotNull(festival);
        assertEquals(0, festival.moonPhase());
        assertEquals(3, festival.contests().size());
        assertEquals(ContestKind.CHASE, festival.contests().get(0).kind());
        assertEquals(Critter.PIG, festival.contests().get(0).critter().orElseThrow());
        assertEquals(TokenKind.EGG, festival.contests().get(2).token().orElseThrow());
        assertEquals("star", festival.fireworks().shape());
        assertEquals(1, festival.prizes().get(0).price());
    }

    /** Без числа поиск прячет десять вещиц, ловля выпускает трёх зверьков, стрельба — восемь выстрелов. */
    @Test
    void piecesHaveTheirDefaults() {
        Festival festival = parse(NORMAN);
        assertEquals(3, festival.contests().get(0).pieces());
        assertEquals(8, festival.contests().get(1).pieces());
        assertEquals(10, festival.contests().get(2).pieces());
    }

    @Test
    void anUnknownKindIsAnErrorNotASilence() {
        assertNull(parse(NORMAN.replace("\"archery\"", "\"race\"")));
    }
}
```

- [ ] **Шаг 2.** `./gradlew test --tests com.villagepax.core.festival.FestivalCodecTest` — не собирается.
- [ ] **Шаг 3. Перечисления** (образец — `core/building/BuildingType.Role`): `ContestKind`
  (`hunt`, `chase`, `archery`), `Critter` (`pig`, `chicken`, `fox`, `rabbit`, `sheep`),
  `TokenKind` (`egg`, `jade`, `omamori`, `rune`, `horseshoe`, `gem`, `firefly`). У каждого
  `implements Named`, `CODEC = EnumCodecs.of(values(), "<что это по-русски>")`.
- [ ] **Шаг 4. Запись**

```java
package com.villagepax.core.festival;

/**
 * Праздник народа — данные, как боги и торг.
 * <p>
 * Виды состязаний, зверьки и вещицы — закрытые перечисления в коде: датапак
 * выбирает, как в игре пройдёт праздник, но не приносит своего состязания,
 * потому что состязание — это поведение, а поведение живёт в коде.
 * Поля, решающие смысл, обязательные: у необязательного поля DFU глотает
 * ошибку вложенного кодека, и описка {@code "kind": "hnut"} молча давала бы
 * праздник без поиска.
 *
 * @param culture   чей праздник
 * @param name      ключ названия: «Ярмарка полной луны»
 * @param moonPhase в какую фазу луны: 0 — полнолуние, 4 — новолуние
 * @param fireworks цвета и форма вечернего фейерверка
 * @param contests  состязания, в порядке показа у затейника
 * @param prizes    что продаёт лавка затейника за ленты
 */
public record Festival(Identifier culture, String name, int moonPhase, Fireworks fireworks,
                       List<Contest> contests, List<Prize> prizes) {

    public record Contest(ContestKind kind, String name, Optional<TokenKind> token, int count,
                          Optional<Critter> critter) {

        public static final Codec<Contest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ContestKind.CODEC.fieldOf("kind").forGetter(Contest::kind),
                Codec.STRING.fieldOf("name").forGetter(Contest::name),
                TokenKind.CODEC.optionalFieldOf("token").forGetter(Contest::token),
                Codec.INT.optionalFieldOf("count", 0).forGetter(Contest::count),
                Critter.CODEC.optionalFieldOf("critter").forGetter(Contest::critter)
        ).apply(instance, Contest::new));

        /** Сколько вещиц прячется, зверьков выпускается или выстрелов даётся. */
        public int pieces() {
            if (count > 0) {
                return count;
            }
            return switch (kind) {
                case HUNT -> 10;
                case CHASE -> 3;
                case ARCHERY -> 8;
            };
        }
    }

    public record Prize(Identifier item, int count, int price) {
        public static final Codec<Prize> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Prize::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Prize::count),
                Codec.INT.fieldOf("price").forGetter(Prize::price)
        ).apply(instance, Prize::new));
    }

    public record Fireworks(List<Integer> colors, String shape) {
        public static final Fireworks PLAIN = new Fireworks(List.of(0xFFFFFF), "large_ball");
        public static final Codec<Fireworks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.listOf().fieldOf("colors").forGetter(Fireworks::colors),
                Codec.STRING.optionalFieldOf("shape", "large_ball").forGetter(Fireworks::shape)
        ).apply(instance, Fireworks::new));
    }

    public static final Codec<Festival> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("culture").forGetter(Festival::culture),
            Codec.STRING.fieldOf("name").forGetter(Festival::name),
            Codec.INT.fieldOf("moon_phase").forGetter(Festival::moonPhase),
            Fireworks.CODEC.optionalFieldOf("fireworks", Fireworks.PLAIN).forGetter(Festival::fireworks),
            Contest.CODEC.listOf().fieldOf("contests").forGetter(Festival::contests),
            Prize.CODEC.listOf().optionalFieldOf("prizes", List.of()).forGetter(Festival::prizes)
    ).apply(instance, Festival::new));
}
```

- [ ] **Шаг 5. Загрузчик** `Festivals` — по образцу `core/faith/Gods`: папка
  `villagepax/festivals`, сортировка по имени файла, `of(culture)` — первый по опознавателю.
  Сверка при загрузке (в лог, не отказом, как у богов):
  - фаза вне 0..7;
  - поиск без `token`, ловля без `critter`;
  - цена меньше 1;
  - `Registries.ITEM.containsId(prize.item())` ложно;
  - у народа два праздника — взят первый, и об этом сказано.
- [ ] **Шаг 6.** Зарегистрировать в `VillagePax.onInitialize` рядом с `Gods`.
- [ ] **Шаг 7.** Проверка кодека — зелёная; `./gradlew test` — зелёный целиком.
- [ ] **Шаг 8.** Коммит `feat: праздник — данные народа`.

---

## Часть B. Ярмарка и затейник

### Task 3: маркеры загона и стрелковой черты

**Файлы:**
- Изменить: `src/main/java/com/villagepax/sim/build/MarkerKind.java` — роды `PEN("pen")`,
  `SHOOTING("shooting")`.
- Изменить: `src/main/java/com/villagepax/block/ModBlocks.java` — `MARKER_PEN`, `MARKER_SHOOTING`
  через `registerMarker`.
- Ресурсы маркеров — те же, что у `marker_decor`: блокстейт, модель, текстура, название
  в словарях. Скопировать набор файлов `marker_decor` и перекрасить текстуру.
- Проверка: дописать в существующую модульную проверку маркеров (найти `byBlockPath`
  в `src/test`); если её нет — создать `src/test/java/com/villagepax/sim/build/MarkerKindTest.java`.

- [ ] **Шаг 1.** Проверка: `MarkerKind.byBlockPath("marker_pen")` → `PEN`,
  `byBlockPath("marker_shooting")` → `SHOOTING`, `byBlockPath("marker_penguin")` → пусто.
- [ ] **Шаг 2.** Красная.
- [ ] **Шаг 3.** Роды с javadoc («где бегают зверьки ловли» / «где встаёт стрелок»), блоки.
  Убедиться, что `BuildPlan` превращает маркеры неизвестного ему рода в воздух и точку интереса,
  а не только известные пять (прочитать ветку разбора маркеров). Если там перечисление —
  дописать.
- [ ] **Шаг 4.** `./gradlew test` — зелёный (включая `BlockResourcesTest`, `LangTest`).
- [ ] **Шаг 5.** Коммит `feat: маркеры загона и стрелковой черты`.

### Task 4: праздничные блоки и предметы

**Файлы:**
- Создать: `tools/make-festival.py` — весь арт праздника; вписать в `GENERATORS`
  в `tools/check-generated.py`.
- Создать в `src/main/java/com/villagepax/block/festival/`:
  - `FestivalHeartBlock` — сердце праздника: столб из звеньев со свойством `TOP`;
  - `ArcheryTargetBlock` — мишень (счёт — в задаче 20);
  - `BuntingBlock` — гирлянда флажков, `HORIZONTAL_AXIS`, без столкновений;
  - `FeastPieBlock` — наследник `CakeBlock`, ставится без опоры;
  - `TrophyBlock` и `TrophyBlockEntity` — кубок с надписью;
  - `FestivalTokenBlock` — вещица поиска со свойством `KIND` (`TokenKind`).
- Изменить: `ModBlocks` (регистрации), `block/entity/ModBlockEntities` (тип блок-сущности кубка).
- Создать в `src/main/java/com/villagepax/item/festival/`:
  - `FestivalHatItem` — `Item implements Equipment`, слот головы, народ шапки;
  - `FestivalBowItem` — наследник `BowItem`, счёт выстрелов в задаче 20;
  - `ModFestivalItems` — ленты, лук, мячики жонглёра, семь шапок, через `ModItems.add`.
- Изменить: `VillagePax.onInitialize` — `ModFestivalItems.register()` сразу после снаряжения.
- Клиент: `VillagePaxClient` — предикаты `pull` и `pulling` для праздничного лука (как у
  ванильного) и прозрачная отрисовка для флажков, вещиц и сердец
  (`BlockRenderLayerMap.INSTANCE.putBlock(..., RenderLayer.getCutout())`).

**Что именно** (имена — опознаватели, в скобках — по-русски):

| Блок | Народ | Облик | Свет | Рецепт |
|---|---|---|---|---|
| `maypole` (майское дерево) | норманны | звено: столб в спиральных лентах; верх — венок с лентами | — | 2 палки + тёмный дуб → 2 |
| `volador_pole` (шест воладоров) | майя | звено: крашеный столб; верх — рама с верёвками | — | 2 палки + джунгли → 2 |
| `taiko_drum` (барабан тайко) | ямато | барабан на подставке; щелчок — удар | — | вишня + 2 палки |
| `yule_fire` (йольский костёр) | северяне | сруб из поленьев, пламя движется (анимированная текстура), дым частицами; не жжёт — это убранство, а не огонь | 15 | 4 еловых бревна + палка |
| `rainbow_pole` (радужный столб) | пони | звено: столб в радужных лентах; верх — звезда. Не арка: радуга аркой — это крашеная шерсть, а красителей у колонии нет | 6 у верха | 2 палки + акация → 2 |
| `festival_forge` (праздничный горн) | гномы | каменный очаг с углями | 13 | 5 булыжников + палка |
| `glow_tree` (светящееся деревце) | эльфы | деревце со светлячками в кроне | 12 | берёза + 2 палки |
| `archery_target` (мишень) | все | соломенный щит в кольцах: белый, красный, золотая середина | — | 4 доски + палка |
| `<народ>_bunting` ×7 (гирлянда флажков) | каждый | верёвка с флажками в цветах народа | — | 2 палки + доски своей породы → 3 |
| `feast_pie` (праздничный пирог) | все | семь кусков, как у торта, своя корочка | — | нет, ставит праздник |
| `trophy` (кубок) | все | золотой кубок на подставке | 4 | нет, даёт праздник |
| `festival_token` (вещица) | семь видов | яйцо, нефритовая фигурка, омамори, руна, подкова, самоцвет, светлячок | светлячок 10, самоцвет 6 | нет, ставит праздник |

| Предмет | Облик |
|---|---|
| `festival_ribbon` (праздничная лента) | розетка с хвостами, стопка 64 |
| `festival_bow` (праздничный лук) | лук с лентой, три кадра натяжения |
| `juggling_balls` (мячики жонглёра) | три пёстрых мячика |
| `norman_wreath`, `maya_feather_crown`, `kitsune_mask`, `nord_straw_crown`, `pony_party_hat`, `dwarf_candle_cap`, `elf_firefly_wreath` | объёмные модели предмета с видом `head` |

Породы флажков: норманны — тёмный дуб, майя — джунгли, пони — акация, северяне — ель,
ямато — вишня, эльфы — берёза, гномы — булыжник вместо досок (у них нет дерева в зале).

Теги:
- `mineable/axe` — деревянное;
- `mineable/pickaxe` — горн, кубок, самоцвет;
- `villagepax:build_decor` — флажки, сердца, мишень: билдер ставит их последними;
- `villagepax:guest_usable` — вещица, пирог, кубок, барабан: гость колонии играет наравне с хозяином;
- новый `villagepax:festival_hearts` — все семь сердец.

- [ ] **Шаг 1.** `make-festival.py` по образцу `make-wonders.py`:
  - текстуры рисуются через `make-textures.py`;
  - модели — `box()`/`model()`;
  - пишет блокстейты, модели предметов, добычу, рецепты, теги и имена в оба словаря.
  Добыча:
  - вещица и пирог не дают ничего — пустой пул;
  - кубок — сам кубок с `minecraft:copy_nbt` из блок-сущности, поля `Engraving`;
  - остальное — сам блок.
- [ ] **Шаг 2.** Классы блоков:
  - `FestivalHeartBlock` — `TOP` пересчитывается в `getStateForNeighborUpdate`: верх — если над
    звеном не такое же звено;
  - `FeastPieBlock`:
    - `canPlaceAt` → `true`;
    - `getStateForNeighborUpdate` не ломает пирог без опоры;
    - `onUse` зовёт `tryEat`, а свечу не принимает — иначе пирог станет ванильным тортом
      со свечой и уборка его не узнает;
  - `TrophyBlockEntity` хранит `Engraving` (NbtCompound: `contest`, `festival` — ключи
    словаря; `village`, `winner` — строки; `day` — число); `TrophyBlock.onUse` пишет надпись
    в чат тому, кто щёлкнул;
  - `TrophyBlock.appendTooltip` — строки надписи из `BlockEntityTag` стопки;
  - `FestivalTokenBlock` — форма по виду, `noCollision`.
- [ ] **Шаг 3.** Предметы:
  - `FestivalHatItem`: `getSlotType()` → `HEAD`, `use` → `equipAndSwap`;
  - `FestivalBowItem` пока просто наследник `BowItem`.
- [ ] **Шаг 4.** `python tools/make-festival.py`; `./gradlew test` — зелёный:
  `BlockResourcesTest`, `LangTest`, `ModelReferencesTest`.
- [ ] **Шаг 5.** Посмотреть глазами: `python tools/preview-models.py <модель>` для каждого
  блока и шапки. Сердца, флажки и вещицы должны узнаваться с первого взгляда, как народ.
- [ ] **Шаг 6.** `python tools/check-generated.py` — «совпадает» (после коммита).
- [ ] **Шаг 7.** Коммит `feat: праздничные блоки, ленты, лук и шапки народов`.

### Task 5: ремесло «затейник»

**Файлы:**
- Создать: `src/main/resources/data/villagepax/villagepax/professions/entertainer.json`
  ```json
  {"display_name": "villagepax.profession.entertainer", "job": "villagepax:entertain",
   "needs_workplace": true, "hiring_priority": 5, "workplace": "fairground"}
  ```
  Приоритет 5 — ниже купца (10): пришлый становится затейником, только когда нужные ремёсла
  заняты. Деревня ставит затейника явно, при основании (задача 10).
- Создать: `src/main/java/com/villagepax/sim/work/EntertainJob.java`
- Изменить:
  - `sim/work/Jobs.java` — `new EntertainJob()`;
  - `sim/Villages.java` — `ENTERTAINER = new Identifier(MOD_ID, "entertainer")` с javadoc
    по образцу `MERCHANT`.
- Изменить: `core/culture/Culture.java` — необязательное поле `titles`
  (`Map<Identifier, String>`: ремесло → ключ имени у этого народа) и метод
  `titleOf(Identifier profession) → Optional<String>`. Кодек: 11 полей из 16.
- Изменить: `entity/CitizenEntity.label` (строка с `profession.displayName()`) — имя ремесла
  у народа, если народ его назвал: `culture.titleOf(p).orElse(profession.displayName())`.
  То же в экране затейника (задача 21).
- Данные: в каждую культуру —
  `"titles": {"villagepax:entertainer": "villagepax.profession.entertainer.<народ>"}`.
  Имена:

  | Народ | Имя |
  |---|---|
  | норманны | Жонглёр |
  | майя | Воладор |
  | ямато | Рассказчик |
  | северяне | Скальд |
  | пони | Затейник |
  | гномы | Запевала |
  | эльфы | Менестрель |

  English: Jongleur, Volador, Storyteller, Skald, Party Pony, Song-Leader, Minstrel.
- Проверка: `src/gametest/java/com/villagepax/gametest/FestivalTests.java` (новый класс,
  вписать в `src/gametest/resources/fabric.mod.json`).

```java
package com.villagepax.sim.work;

/**
 * Затейник: стоит у своего прилавка на ярмарке, жонглирует и зазывает.
 * <p>
 * Ремесло без выработки, как купец, и по той же причине: он производит не вещь,
 * а место встречи. В будни к нему подходят узнать, когда праздник; в праздник —
 * играть. Мячики в руке — вывеска ремесла, как монета у купца: по ним игрок
 * узнаёт затейника с другого конца деревни.
 * <p>
 * Нет ярмарки — бродит, как всякий без дела. Праздника без затейника не бывает,
 * и это правило живёт в {@code FestivalDay}, а не здесь.
 */
public class EntertainJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "entertain");

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        context.hold(new ItemStack(ModFestivalItems.JUGGLING_BALLS));
        if (context.state().phase() != JobState.Phase.IDLE) {
            context.goIdle();
        }
        Building fair = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (fair == null) {
            return Optional.empty();
        }
        List<BlockPos> counter = Workplaces.stations(fair);
        return counter.isEmpty() ? Optional.empty() : Optional.of(counter.get(0));
    }
}
```

- [ ] **Шаг 1.** Проверка `anEntertainerKeepsHisCounterWithBallsInHand` (исполняется после
  задачи 8 — см. порядок в начале плана): колония, норманнская ярмарка поставлена
  `standUp` (задача 8), житель-затейник нанят и привязан к ней; решение
  `WorkTicker.decide(..., Schedule.MORNING_WORK)` ставит цель в клетку у прилавка
  (`Workplaces.stations(fair).get(0)`, не дальше 1.5 блока), в руке `juggling_balls`.
- [ ] **Шаг 2.** Красная (логики нет — житель без дела).
- [ ] **Шаг 3.** Логика, регистрация, `ENTERTAINER`, `titles` у культур, подпись, словари.
- [ ] **Шаг 4.** Зелёная; `./gradlew test` (DatapackWordsTest знает новые слова датапака) —
  зелёный.
- [ ] **Шаг 5.** Коммит `feat: затейник — ремесло ярмарки`.

### Task 6: облик затейника

**Файлы:**
- Изменить: `tools/make-citizen-textures.py`:
  - `craft_entertainer(skin, parts, look, woman, people)` — пёстрый наряд ромбами в два цвета
    народа (`look["accent"]` и `look["cloth_lit"]`), зубчатый воротник, пояс; у северян — меховая
    накидка (скальд), у майя — перья на рукавах (воладор), у ямато — хаори с гербом;
  - `pony_entertainer` — попона ромбами;
  - знак на боку `"entertainer": ["Y.P", ".O.", "P.Y"]` — три мячика (буквы — из палитры знаков);
  - строки палитры `(False|True, "entertainer")` в таблице цветов ремёсел.
- Изменить: `tools/citizen_body.py` — приметы:
  - `Bone("cap@entertainer+!yamato+!nord", "head", …)` — колпак с двумя рожками и бубенцами;
  - `Bone("mask@entertainer+yamato", "head", …)` — маска лисы на боку головы;
  - у пони: `Bone("ruff@entertainer", "neck", …)` — воротник-жабо.
- Перезапустить: `python tools/make-citizen-models.py`, `python tools/make-citizen-textures.py`.
- Проверки: `CitizenFacesTest`, `CitizenMarksTest`, `MarksTest` — зелёные без правки кода
  проверок: они берут ремёсла из датапака.

- [ ] **Шаг 1.** `./gradlew test` — `CitizenFacesTest` красный: у ремесла `entertainer` нет лиц
  (это и есть проверка, написанная раньше).
- [ ] **Шаг 2.** Наряды, приметы, генераторы.
- [ ] **Шаг 3.** `./gradlew test` — зелёный.
- [ ] **Шаг 4.** Контактный лист: `powershell tools/contact-sheet.ps1` и
  `python tools/preview-citizens.py --people norman,nord,pony,yamato --craft entertainer`.
  Смотреть глазами: затейник отличим от купца и старейшины с другого конца деревни.
- [ ] **Шаг 5.** Коммит `art: затейник у каждого народа в своём наряде`.

### Task 7: пляска, ликование и жонглирование

**Файлы:**
- Изменить: `src/main/resources/assets/villagepax/animations/entity/citizen.animation.json`
  и `citizen_pony.animation.json` — дорожки:
  - `dance` (петля 1.0 с): руки вверх и в стороны попеременно / у коня шея, голова, уши, хвост
    в такт;
  - `sway` (петля 1.0 с): туловище качается / у коня корпус пританцовывает;
  - `cheer` (один раз, 1.2 с): обе руки вверх, прыжок туловищем / у коня голова вверх, уши
    торчком;
  - `juggle` (петля 0.8 с): руки подбрасывают по очереди на уровне груди / у коня голова
    кивает — мячики в зубах.
- Изменить: `src/main/java/com/villagepax/entity/CitizenEntity.java`:
  - отслеживаемое поле `DANCING` (boolean), `setDancing`, `isDancing`;
  - `RawAnimation DANCE = thenLoop("dance")`, `SWAY = thenLoop("sway")`,
    `JUGGLE = thenLoop("juggle")`, `CHEER = thenPlay("cheer")`;
  - `armsFor`: спящий → `SLEEP`; пляшущий → `DANCE`; мячики в руке и не на ходу → `JUGGLE`;
    дальше как было;
  - контроллер дыхания: пляшущий → `SWAY`;
  - контроллер рук: `.triggerableAnim("cheer", CHEER)` и метод `cheer()` рядом с `greet()`.
- Проверка: `CitizenAnimationTest` сверяет: дорожки есть у обоих тел, кости существуют, каждую
  дорожку зовёт код.

- [ ] **Шаг 1.** Дописать в код только имена дорожек (`thenLoop("dance")` и т. д.) —
  `CitizenAnimationTest` красный: «код зовёт движение, которого не написано».
- [ ] **Шаг 2.** Дорожки в обоих файлах. Кости брать только из geo-моделей
  (`geo/entity/citizen.geo.json`, `citizen_pony.geo.json`).
- [ ] **Шаг 3.** Поле `DANCING`, выбор дорожек, `cheer()`.
- [ ] **Шаг 4.** `./gradlew test` — зелёный.
- [ ] **Шаг 5.** Позы глазами: `python tools/preview-citizens.py --poses dance@0,dance@0.5,cheer@0.4,juggle@0.2`.
- [ ] **Шаг 6.** Коммит `feat: жители пляшут, ликуют и жонглируют`.

### Task 8: ярмарка — схема у семи народов и места праздника

**Файлы:**
- Изменить: `tools/make-schematics.py` — `fairground_lvl1` для семи народов.
  - Символы легенды:
    - `П` → `villagepax:marker_pen`, `Ч` → `villagepax:marker_shooting`;
    - `♦` → `villagepax:archery_target`;
    - сердце каждого народа своим символом: `Ж` майское дерево, `В` шест воладоров,
      `Т` барабан (на помосте-ягуре из вишни, с факелами: бумажному фонарику нужна бумага,
      а её у колонии нет), `Й` йольский костёр, `Р` радужный столб, `Г` горн, `Э` деревце;
    - флажки — `ф` (порода по народу через таблицу замен, как у других общих схем).
  - Двойники латиницы в кириллице не использовать (с/c, е/e, к/k, р/p, о/o, а/a, х/x, у/y) —
    сверять палитру после генерации.
- Создать: `src/main/resources/data/villagepax/villagepax/buildings/<народ>/fairground.json`
  ×7:
  `{"display_name": "villagepax.building.<народ>.fairground", "role": "workplace", "profession": "villagepax:entertainer"}`.
- Изменить: семь файлов `cultures/*.json` — `villagepax:<народ>/fairground` в `buildings`,
  последним.
- Создать: `src/main/java/com/villagepax/sim/festival/Fair.java` (запись) и `Fairs.java`:

```java
/**
 * Ярмарка поселения и её места: где сердце праздника, где загон, откуда стреляют,
 * где мишени и столы.
 * <p>
 * Всё берётся из схемы, а не ищется в мире: схема знает место точно, а поиск
 * вокруг нашёл бы и мишень соседней ярмарки, стоящей в двух шагах. Тем же
 * правилом храм узнаётся по алтарю ({@code Altars}).
 */
public record Fair(Building building, BlockPos heart, List<BlockPos> pen, List<BlockPos> shooting,
                   List<BlockPos> targets, List<BlockPos> tables, BlockPos counter, Box area) {
}

public final class Fairs {
    /** Готовая ярмарка поселения — здание, в котором работает затейник. */
    public static Optional<Fair> of(Settlement settlement) { ... }
    /** Места одной ярмарки по её схеме. Пусто — схема без сердца или прилавка. */
    public static Optional<Fair> of(Building building) { ... }
}
```

  Сердце — нижний блок из тега `villagepax:festival_hearts` в схеме. Загон и черта — точки
  интереса `PEN` и `SHOOTING`. Мишени — блоки `archery_target`. Столы — блоки `villagepax:table`,
  место пирога — над столом. Прилавок — первая точка `WORKSTATION`. Мир в расчётах не нужен.

**Раскладка** (норманны, 13×13, север сверху; остальные народы — та же раскладка, свой
материал и своё сердце):

- **Запад, три клетки в ширину — стрелище.** Черта на юге, мишени на 4, 7 и 10 шагов от неё.
  От остальной площадки отделено забором.
- **Северо-восток — загон.** 7×7 снаружи, борт из брёвен в один блок, внутри 5×5 клеток `П`.
- **Середина юга — сердце.** Вокруг кольцо радиусом 2 для хоровода: ровное, без блоков.
- **Юго-восток** — стол на три места с лавками.
- **Северо-запад** — прилавок затейника с маркером `K`.
- **Юг** — ворота с маркером `D`. Столбы с флажками по углам, факелы на столбах по правилу
  света.
- **Пол** — утоптанная земля, кольцо у сердца мощено камнем.

У гномов ярмарка — зал в толще (как `chamber()`): загон, стрелище и сердце-горн под сводом,
свет — подвесные фонари `'` (железо у гномов есть: у них горн). Эльфы — помост на ветвях
(как `bower()`), загон огорожен перилами.

- **Проверки** (в `FestivalTests`):

```java
@GameTest(templateName = EMPTY_STRUCTURE, batchId = "festival")
public void everyPeopleHasAFairWithAllItsPlaces(TestContext context) {
    List<String> complaints = new ArrayList<>();
    for (Identifier culture : CultureManager.all().keySet()) {
        Identifier type = new Identifier(culture.getNamespace(), culture.getPath() + "/fairground");
        if (BuildingTypes.get(type).isEmpty()) {
            complaints.add(culture + ": нет ярмарки");
            continue;
        }
        Building site = Building.planned(type, BlockPos.ORIGIN, BlockRotation.NONE);  // имя конструктора — по Building
        Fair fair = Fairs.of(site).orElse(null);
        if (fair == null) {
            complaints.add(culture + ": у ярмарки нет сердца или прилавка");
            continue;
        }
        // Числа — решение по игре: загон 5×5, три мишени, хоть одна черта и один стол.
        if (fair.pen().size() < 25) complaints.add(culture + ": загон меньше 5×5 — " + fair.pen().size());
        if (fair.targets().size() != 3) complaints.add(culture + ": мишеней " + fair.targets().size());
        if (fair.shooting().isEmpty()) complaints.add(culture + ": негде встать стрелку");
        if (fair.tables().isEmpty()) complaints.add(culture + ": некуда ставить пироги");
    }
    if (!complaints.isEmpty()) {
        context.throwGameTestException("Ярмарки народов:\n  " + String.join("\n  ", complaints));
    }
    context.complete();
}
```

  Плюс существующие сквозные проверки проходят с новыми схемами без правок:
  - `everySchematicIsBuiltFromStandableSpots`;
  - `everyBuildingLetsYouInFromTheGround`;
  - `everyOrderHasAGhostToShow`;
  - `everyDeclaredBuildingCanBeBuilt`;
  - `noModBlockIsLeftUnused` — туда же дописать исключения задачи 24, если сработает раньше.

- **Помощник проверок** (в `GameTestSupport`) — поставить здание готовым в заданной точке,
  тем же путём, каким деревня получает подарок (`Raising.raise`), только без поиска места:

```java
/** Поставить здание готовым на месте: запас в саму стройку и весь план за раз. */
static Building standUp(ServerWorld world, SettlementManager manager, Settlement settlement,
                        Identifier type, BlockPos anchor) {
    Building site = Building.planned(type, anchor, BlockRotation.NONE);
    manager.update(settlement.id(), state -> state.addBuilding(site));
    Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElseThrow();
    Materials.required(schematic).forEach((item, count) ->
            site.stock().add(Registries.ITEM.getId(item), count));
    BuildJob.Outcome outcome = BuildJob.advance(world, manager, settlement.id(), site.id(),
            Integer.MAX_VALUE);
    if (outcome != BuildJob.Outcome.FINISHED) {
        throw new IllegalStateException(type + " не встала: " + outcome);
    }
    return settlement.building(site.id()).orElseThrow();
}
```

  Уборка после проверок, которые им пользуются: `clearSkirt` и снос по плану схемы в `finally`.

- [ ] **Шаг 1.** Проверка мест — красная (ярмарок нет).
- [ ] **Шаг 2.** Схемы семи народов, данные зданий и культур; `python tools/make-schematics.py`
  (сам проверяет вложенность уровней — у ярмарки уровень один).
- [ ] **Шаг 3.** Посмотреть: `python tools/preview-schematics.py <народ>/fairground_lvl1` для всех
  семи. Кольцо хоровода свободно, загон замкнут, мишени видны с черты.
- [ ] **Шаг 4.** `Fair`/`Fairs`.
- [ ] **Шаг 5.** `./gradlew runGametest` — новая и сквозные проверки зелёные.
- [ ] **Шаг 6.** Коммит `feat: ярмарка у всех семи народов`.

### Task 9: праздники семи народов — данные и слова

**Файлы:**
- Создать: `src/main/resources/data/villagepax/villagepax/festivals/<народ>.json` ×7 по таблице.
- Изменить: оба словаря — названия праздников, состязаний, правила состязаний одной строкой,
  реплики затейника.
- Изменить: `docs/datapacks.md` — раздел `festivals/<народ>.json`: поля, закрытые списки
  (`hunt|chase|archery`, зверьки, вещицы), что будет без праздника; строка в «Куда класть
  файлы» и в чек-листе народа.
- Проверки: модульная `src/test/java/com/villagepax/core/festival/FestivalDataTest.java`
  (с диска, без игры):
  1. у каждого народа из `cultures/` есть праздник, а у праздника — существующий народ;
  2. фазы у встроенных народов разные;
  3. у поиска есть вещица, у ловли — зверёк;
  4. каждое `name` — ключ в обоих словарях;
  5. каждый приз — предмет, объявленный в исходниках: `register("` / `add("` в `ModItems`,
     `ModBlocks`, `ModFestivalItems`, либо `minecraft:`.
  `DatapackGuideTest.everyDatapackFolderIsDocumented` сам потребует раздел `festivals/`.

| Народ | Файл, фаза | Состязания (вид: вещица/зверёк) | Фейерверк | Лавка |
|---|---|---|---|---|
| норманны | `norman` 0 | ловля: pig; стрельба; поиск: egg | красный, золотой; star | венок 8, флажки ×4 — 2, ракеты ×3 — 1 |
| пони | `pony` 1 | поиск: horseshoe; ловля: sheep; стрельба | радуга (6 цветов); large_ball | колпак 8, флажки, ракеты |
| майя | `maya` 2 | стрельба; ловля: chicken; поиск: jade | бирюза, золото; burst | корона из перьев 8, флажки, ракеты |
| эльфы | `elf` 3 | стрельба; поиск: firefly; ловля: rabbit | лунный, зелёный; small_ball | венок светлячков 8, флажки, ракеты |
| ямато | `yamato` 4 | поиск: omamori; стрельба; ловля: fox | алый, белый; large_ball | маска кицунэ 8, флажки, ракеты |
| гномы | `dwarf` 5 | ловля: pig; поиск: gem; стрельба | медь, золото; burst | колпак со свечой 8, флажки, ракеты |
| северяне | `nord` 6 | стрельба; поиск: rune; ловля: rabbit | лёд, синий; star | соломенная корона 8, флажки, ракеты |

Названия состязаний по народу (ru): «Ловля поросят», «Стрельба из лука», «Поиск крашеных яиц»;
«Поиск подков», «Ловля ягнят»… — у каждого народа свои три строки.

- [ ] **Шаг 1.** `FestivalDataTest` — красная.
- [ ] **Шаг 2.** Семь файлов, слова, раздел документации.
- [ ] **Шаг 3.** `./gradlew test` — зелёный; `./gradlew runGametest` (загрузчик не ругается
  в лог: `grep "не загружен\|Праздник" build/...` в выводе прогона).
- [ ] **Шаг 4.** Коммит `feat: праздники семи народов`.

### Task 10: деревня встаёт с ярмаркой и затейником

**Файлы:**
- Изменить: `src/main/java/com/villagepax/sim/Villages.java`:
  - в `found` после `raiseStall` — `raiseFair(world, manager, village, culture)`: поставить
    ярмарку готовой через `Raising.raise`, как ларёк;
  - и `settle(world, village, entertainer(cultureId, culture, random))` — пятым основателем,
    явно, с javadoc о том, зачем явно (праздник без затейника не бывает, а пришлые приходят
    раз в день).
  - Старые деревни — без правки: планировщик хочет по одной мастерской каждого вида,
    и ярмарка из списка народа будет заложена сама.
- Проверки (`FestivalTests`, `WIDE_STRUCTURE`):
  - `aNewVillageHasItsFairAndItsEntertainer` — `Villages.found` на ровном: есть готовая ярмарка,
    есть житель с ремеслом `entertainer`, его мастерская — эта ярмарка.
  - Существующая `VillageTests.everyFounderHasACraft` проходит без правки.
  - Существующие проверки основания деревни, которые считают жителей или здания, —
    поправить числа руками с комментарием «пятый основатель — затейник».

- [ ] **Шаг 1.** Проверка — красная.
- [ ] **Шаг 2.** Код.
- [ ] **Шаг 3.** Весь `./gradlew runGametest` — зелёный. Упавшие старые проверки основания
  разобрать поодиночке: правится число, а не смысл.
- [ ] **Шаг 4.** Коммит `feat: деревня встаёт с ярмаркой и затейником`.

---

## Часть C. День праздника

### Task 11: идёт ли праздник — одно правило на весь мод

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/festival/FestivalDay.java`
- Проверки: модульная `src/test/java/com/villagepax/sim/festival/FestivalHoursTest.java`
  и игровая `FestivalTests.theFestivalNeedsAFairAHostAndPeace`.

**Интерфейсы:**
- Потребляет: `Festivals.of`, `FestivalCalendar.isFestivalDay`, `Fairs.of(Settlement)`,
  `Villages.ENTERTAINER`, `Settlement.siege()`.
- Производит:
  - `FestivalDay.Verdict {ON, NO_FESTIVAL, NOT_TODAY, NO_FAIR, NO_HOST, BESIEGED}`
    с `reasonKey()` → `villagepax.festival.reason.<id>`;
  - `today(Settlement, long day) → Verdict`, `isOn(Settlement, long day) → boolean`;
  - `host(Settlement, Fair) → Optional<Citizen>` — затейник, привязанный к этой ярмарке;
  - `revels(boolean autonomous, Schedule part) → boolean` — кто в эту часть суток гуляет:
    деревня все части кроме сна, колония `DAY_WORK` и `LEISURE`;
  - `contestsOpen(boolean autonomous, long timeOfDay) → boolean` — деревня `[0, 12000)`,
    колония `[7000, 12000)`;
  - постоянная `DUSK = 12_000L`.

```java
/**
 * Идёт ли у поселения праздник — одно правило на весь мод.
 * <p>
 * Спрашивают его зов на рассвете, пироги, хоровод, состязания, фейерверк,
 * прибавка к довольству и экран затейника. Семь ответов на один вопрос
 * разошлись бы при первой правке: пироги стояли бы на ярмарке без затейника,
 * а экран говорил бы, что праздника нет.
 * <p>
 * Праздник идёт, если сегодня фаза его народа, у поселения готовая ярмарка,
 * у ярмарки есть затейник и поселение не в осаде. Отказ называет причину
 * первой по порядку: сперва «не сегодня», потом «нет ярмарки», «нет затейника»,
 * «набег». Игрок, которому сказали «нет затейника» в будний день, пошёл бы
 * искать затейника зря.
 */
public final class FestivalDay { ... }
```

- [ ] **Шаг 1.** `FestivalHoursTest` — числа руками: деревня гуляет утром (`MORNING_WORK`),
  колония утром нет, после обеда да; сон — никто; состязания колонии с 7000, деревни с 0,
  у обоих до 11999 включительно, 12000 — нет.
- [ ] **Шаг 2.** Игровая: колония с ярмаркой (`standUp`) и затейником. Ответы:
  - день 8 (полнолуние) — `ON`;
  - день 9 — `NOT_TODAY`;
  - затейник уволен — `NO_HOST`;
  - ярмарка `DAMAGED` — `NO_FAIR`;
  - осада (`settlement.besiege(...)` через `rememberRaid`) — `BESIEGED`.
- [ ] **Шаг 3.** Обе красные → код → зелёные.
- [ ] **Шаг 4.** Коммит `feat: одно правило «идёт ли праздник»`.

### Task 12: зов на рассвете и зазывала

**Файлы:**
- Создать:
  - `sim/festival/FestivalTicker.java` — раз в 20 тиков обходит поселения; на смене дня
    у поселения зовёт `Heralds.dawn`, дальше (в следующих задачах) — `Feast.tend`
    и `Fireworks.tend`;
  - `sim/festival/Heralds.java`.
- Изменить: `EntertainJob.tick` — зазывает (`Heralds.barker`).
- Изменить: `VillagePax` — `FestivalTicker.register()`.

**Интерфейсы:**
- `Heralds.lineFor(Settlement, long day) → Optional<String>` — ключ строки зова:
  `villagepax.festival.today`, `villagepax.festival.tomorrow` или пусто. Чистая, над
  `FestivalDay`.
- `Heralds.dawn(ServerWorld, Settlement, long day)`:
  - строка всем игрокам в 160 блоках от середины поселения и хозяину колонии, где бы он ни был;
  - рог (`SoundEvents.GOAT_HORN_SOUNDS.get(0)`) у сердца, если чанк загружен.
- `Heralds.barkerLine(int daysUntil, long minute) → String` — ключ реплики затейника:
  - в праздник — `villagepax.entertainer.today.<0..3>`;
  - в будни — `villagepax.entertainer.call.<0..5>`;
  - выбор по минуте, чтобы подряд не повторялось.
- Затейник зазывает раз в минуту ближайшего игрока в 8 блоках (память — `Map<UUID, Long>`
  в `Heralds`, чистится вместе с телом). Строка в чат: «Жонглёр Тибо: «…»».

- [ ] **Шаг 1.** Модульная `HeraldsTest`: у `barkerLine` ключей ровно 10, все есть в обоих
  словарях (читать с диска, как `LangTest`), две минуты подряд — разные строки.
- [ ] **Шаг 2.** Игровая `theVillageCallsOnItsDayAndTheDayBefore`: колония с ярмаркой и затейником:
  - `lineFor(день 8)` → `today`;
  - `lineFor(день 7)` → `tomorrow`;
  - `lineFor(день 3)` → пусто;
  - без затейника `lineFor(день 8)` → пусто.
- [ ] **Шаг 3.** Красные → код → зелёные.
- [ ] **Шаг 4.** Коммит `feat: зов праздника и зазывала на ярмарке`.

### Task 13: пироги на столе

**Файлы:**
- Создать: `sim/festival/Feast.java`
- Изменить: `sim/SettlementManager.java` — память праздника:

```java
/**
 * Что поставил праздник этому поселению и в какой день.
 * <p>
 * Помнится поблочно и с именем блока, как убранство улиц, но по другой
 * причине: убранство стоит, пока стоит деревня, а праздничное живёт день.
 * Имя нужно уборке: убирается только то, что там <b>всё ещё наше</b> —
 * цветок, посаженный игроком на место съеденного пирога, остаётся цветком.
 */
private final Map<UUID, Festive> festive = new LinkedHashMap<>();

public record Placed(long at, Identifier block) {}
public record Festive(long day, List<Placed> placed) {}

public Optional<Festive> festiveOf(UUID village);
public void recordFestive(UUID village, long day, BlockPos at, Identifier block);
public void startFestive(UUID village, long day);   // «сегодня уже ставили», даже если места не было
public void forgetFestive(UUID village, BlockPos at);
public void clearFestive(UUID village);
```

  NBT — список `{village, day, at: long[], blocks: string[]}` рядом с `DECOR_TAG`; `remove(id)`
  забывает и праздничное.
- Изменить: `FestivalTicker` — `Feast.tend(world, manager, settlement, day)`.
- Проверки (`FestivalTests`, `WIDE_STRUCTURE`, batch `feast`):

```java
@GameTest(templateName = WIDE_STRUCTURE, batchId = "feast")
public void piesStandOnTheirDayAndLeaveTheNext(TestContext context) {
    // колония, ярмарка standUp в углу площадки, затейник привязан
    // Feast.tend(..., день 8) → над каждым столом ярмарки пирог (feast_pie)
    // один пирог заменить цветком (игрок посадил)
    // Feast.tend(..., день 9) → пирогов нет, цветок стоит, память праздника пуста
    // finally: снос ярмарки по плану, clearSkirt, manager.remove(colony)
}
```

  И вторая: `aFeastIsLaidOnceADay` — два `tend` подряд в тот же день: съеденный (убранный)
  пирог не ставится снова.

- [ ] **Шаг 1.** Красные.
- [ ] **Шаг 2.** `Feast.tend`:
  - сегодня праздник и ярмарка в загруженном чанке, а сегодня ещё не ставили — ставить
    `FEAST_PIE` на каждое место над столом, где воздух, и записать;
  - иначе убрать всё записанное не сегодняшним днём, чьи чанки загружены: убирается,
    только если блок там всё ещё тот же.
  - Сохранение: проверка `SettlementPersistenceTest` (модульная) дописывается —
    `festive` переживает `writeNbt`/`fromNbt`.
- [ ] **Шаг 3.** Зелёные; доказать поломкой: убрать проверку «всё ещё тот же» — цветок исчезает,
  проверка красная; вернуть.
- [ ] **Шаг 4.** Коммит `feat: праздничные пироги на столе ярмарки`.

### Task 14: гулянье — хоровод вокруг сердца

**Файлы:**
- Создать: `sim/festival/Revels.java`
- Изменить: `sim/work/WorkTicker.java`:
  - перегрузка `decide(world, manager, settlement, citizen, part, day)` — старая зовёт её
    с днём мира;
  - в начале решения `body.setDancing(false)`;
  - после `leash` — `if (Revels.takesOver(context, part, day)) return;`;
  - в `leash` — гуляющих держит у сердца ярмарки на 16 блоков;
  - `tick` передаёт `today`.
- Изменить: `sim/VillageMusic.java` — в день праздника песня играет у сердца ярмарки, а не у ратуши
  (`Revels.stage(settlement, day).orElse(center)`).

**Правила** (`Revels`):
- не гуляют: затейник, купец, стража (`Villages.ENTERTAINER`, `MERCHANT`, `GUARD`);
- в обед голодный идёт есть — гулянье его ждёт;
- соперник идущего состязания — решение за состязанием (подключается в задаче 17:
  `Matches.steers(context)`; до неё — ложь);
- хоровод:
  - 8 мест по кругу радиусом 2 вокруг сердца, на уровне сердца;
  - место жителя — `(его номер + время / 100) % 8`, то есть раз в пять секунд хоровод
    сдвигается на шаг;
  - занятое стеной место — следующее по кругу;
- кто не поместился (девятый и дальше по устойчивому порядку от опознавателя) — смотрит:
  12 мест радиусом 4, взгляд на сердце;
- пляшущий: `setDancing(true)`, руки пусты, взгляд (`workFocus`) — на сердце.

- Проверки (`FestivalTests`, `WIDE_STRUCTURE`, batch `revels`):
  - `villagersDanceAroundTheHeartOnTheirDay`: деревня (автономная), ярмарка standUp, затейник,
    три жителя с телами:
    - `decide(..., DAY_WORK, день 0)` → у троих цель не дальше 3 блоков от сердца и `isDancing`;
    - у затейника цель — прилавок;
    - `decide(..., DAY_WORK, день 1)` → никто не пляшет.
  - `aColonyWorksTillLunchOnItsFestival`: колония норманнов, лесоруб при роще:
    - `MORNING_WORK`, день 0 → цель в роще, не пляшет;
    - `DAY_WORK`, день 0 → в хороводе.
  - `nineDancersAndTheTenthWatches`: десять жителей — восемь в кольце радиуса 2, двое дальше,
    все смотрят на сердце.
- [ ] **Шаг 1.** Красные → **Шаг 2.** код → **Шаг 3.** зелёные.
- [ ] **Шаг 4.** Весь `./gradlew runGametest` — старые проверки распорядка не задеты
  (в них день не праздничный: у норманнов праздник в день 0 по модулю 8 — проверить,
  что старые проверки не живут в такой день; если живут — их день задать явно).
- [ ] **Шаг 5.** Коммит `feat: в праздник жители водят хоровод на ярмарке`.

### Task 15: фейерверк на закате

**Файлы:**
- Создать: `sim/festival/Fireworks.java`
  - `rocket(Festival, int flight) → ItemStack` — ракета в цветах народа (NBT `Fireworks`:
    `Flight`, `Explosions[{Type, Colors, FadeColors, Trail, Flicker}]`; тип по `shape`:
    `small_ball` 0, `large_ball` 1, `star` 2, `creeper` 3, `burst` 4);
  - `launch(ServerWorld, Fair, Festival, Random) → FireworkRocketEntity`;
  - `tend(ServerWorld, Settlement, long day, long timeOfDay)` — с 12000 до 13000, раз в 40 тиков,
    если праздник идёт, ярмарка загружена и игрок в 64 блоках.
- Изменить: `FestivalTicker` — `Fireworks.tend`.
- Проверка `aRocketCarriesItsPeoplesColours`:
  - `rocket(норманнский праздник, 1)` → цвета красный и золотой, форма `star` (числа руками);
  - `launch` ставит ракету в 4 блоках от сердца; в `finally` — `discard`.
- [ ] Шаги: красная → код → зелёная → коммит `feat: фейерверк праздника в цветах народа`.

### Task 16: праздник веселит колонию; камень майя знает праздники

**Файлы:**
- Изменить: `sim/work/Needs.java` — перегрузка `newDay(world, manager, settlement, today)`;
  в прибавке сытого:

```java
+ FestivalDay.cheer(settlement, today - 1)
```

  `FestivalDay.cheer(settlement, day)` — `CHEER = 6`, если праздник в тот день шёл, иначе 0.
  Javadoc: лучший день месяца стоит больше сытости (+5), и за него колония платит жителем
  на ярмарке и половиной рабочего дня.
- Изменить: `WorkTicker.rollOverDay` — передать `today` в `Needs.newDay`.
- Изменить: `block/wonder/Wonders.MayaCalendar.reading(ServerWorld)` →
  `reading(ServerWorld, BlockPos)`: строка ближайшего праздника поселения с ярмаркой
  и затейником в 256 блоках — «Ближайший праздник: Ярмарка полной луны в «Бовуаре» — через
  3 дн.» или «…сегодня». Нет таких — строки нет.
- Проверки:
  - `aFestivalCheersTheColony`: три колонии — с ярмаркой и затейником, с ярмаркой без
    затейника, без ярмарки. `Needs.newDay` за день после праздника — у первой довольство
    выше на 6 (руками), у второй и третьей прибавки нет;
  - `theCalendarStoneKnowsTheNearestFestival`: камень у колонии с ярмаркой — в чтении есть
    строка с ключом `villagepax.maya_calendar.festival` (сравнивать по ключу
    `TranslatableTextContent`, а не по тексту).
- [ ] Шаги: красные → код → зелёные → коммит `feat: праздник веселит колонию, камень майя считает дни до него`.

---

## Часть D. Состязания

### Task 17: движок состязаний, места и призы

**Файлы:**
- Создать в `sim/festival/`:
  - `Contestant` — `record Contestant(UUID id, boolean player, String name)`;
  - `Standings` — чистая: места и ленты;
  - `Match` — идущее состязание (абстрактный);
  - `Matches` — реестр идущих, тик, запуск, уборка;
  - `Awards` — ленты, кубок, доверие, «приз уже взят».
- Изменить: `SettlementManager` — `awarded(village, day, player, contest) → boolean`,
  `markAwarded(...)`; NBT — список `{village, day, keys: string[]}` («игрок/номер»).
  Хранится только последний день.
- Изменить: `Revels.takesOver` — `Matches.steers(context)`.
- Изменить: `VillagePax` — `Matches.register()`:
  - `END_WORLD_TICK`;
  - `SERVER_STOPPING` → `Matches.stopAll`;
  - `SERVER_STARTED` → `Matches.recover`: убрать оставшееся от прошлого запуска
    по `festiveOf`.

**Интерфейсы:**

```java
public final class Standings {
    public record Placing(Contestant who, int score, int place) {}
    /** Места по очкам; равные делят место (5, 5, 3 → 1, 1, 3); без очков мест нет. */
    public static List<Placing> rank(Map<Contestant, Integer> scores) { ... }
    /** Лент за место: 3, 2, 1, дальше ничего. */
    public static int ribbonsFor(int place) { ... }
}

public abstract class Match {
    public static final int COUNTDOWN = 60;     // три секунды отсчёта
    public static final int LENGTH = 1200;      // минута
    public static final double CATCHMENT = 16;  // кто стоит ближе — участвует
    public static final double LEAVE = 48;      // кто ушёл дальше — выбыл
    public static final int RIVALS = 3;

    protected Match(UUID id, Settlement settlement, Fair fair, Festival festival, int index, long day)
    public UUID id();  public UUID settlement();  public int index();  public long day();
    public Festival.Contest contest();
    public boolean involves(UUID contestant);
    public void join(PlayerEntity player);          // на старте: все в CATCHMENT
    public void addRival(Citizen citizen);
    public void score(UUID contestant, int points);
    public int scoreOf(UUID contestant);

    /** Приготовить место: вещицы, зверьки, луки. Ложь — негде, и отказ назовёт причину. */
    protected abstract boolean prepare(ServerWorld world, Random random);
    /** Повести соперника: куда идти и что делать в этот тик. */
    protected abstract void steer(ServerWorld world, CitizenEntity body, Citizen rival);
    /** Кончилось ли раньше срока: всё найдено, пойманы все, выстрелы кончились. */
    protected abstract boolean exhausted(ServerWorld world);
    /** Убрать всё своё из мира — только то, что там всё ещё наше. */
    protected abstract void clear(ServerWorld world);

    final void tick(ServerWorld world)   // отсчёт (заголовок 3-2-1), время, полоса, выбывшие, конец
    final void finish(ServerWorld world) // места → Awards → ликование → строки → clear
    final void cancel(ServerWorld world, String reasonKey)  // без призов → clear
}

public final class Matches {
    public enum Verdict implements Named {
        YES("yes"), NOT_TODAY("not_today"), CLOSED("closed"), BUSY("busy"),
        NO_FAIR("no_fair"), NO_HOST("no_host"), BESIEGED("besieged"), NO_ROOM("no_room"),
        TOO_FAR("too_far");
        public String reasonKey() { return "villagepax.contest.reason." + id; }
    }
    public static Verdict start(ServerWorld world, PlayerEntity player, Settlement settlement,
                                int index, long day, long timeOfDay);
    public static Optional<Match> at(UUID settlement);
    public static boolean steers(WorkContext context);
    public static boolean collect(ServerWorld world, BlockPos token, PlayerEntity player);   // поиск
    public static boolean grab(ServerWorld world, Entity critter, PlayerEntity player);      // ловля
    public static boolean hit(ServerWorld world, BlockPos target, ProjectileEntity arrow, int rings); // стрельба
    public static void tick(ServerWorld world);
    public static void stopAll(MinecraftServer server);
    public static void recover(ServerWorld world);
}

public final class Awards {
    public static final int TRUST_FOR_PLAYING = 2;
    public static final int TRUST_FOR_WINNING = 3;
    /** Раздать по местам: ленты, кубок первому, доверие чужой деревни до знакомства — раз за праздник. */
    public static void grant(ServerWorld world, SettlementManager manager, Settlement settlement,
                             Festival festival, int index, long day, List<Standings.Placing> placings,
                             Function<UUID, PlayerEntity> players);
    /** Кубок с надписью: ключи словаря, а не готовый текст, — читается на языке того, кто смотрит. */
    public static ItemStack trophy(String contest, String festival, String village, long day, String winner);
}
```

  - Полоса — `ServerBossBar`: название состязания, осталось секунд, «вы: N · лучший: имя M».
  - Отсчёт — `TitleS2CPacket`.
  - Показываются только `ServerPlayerEntity`: подставной игрок проверок получает очки
    и призы так же, но без полосы.
  - Соперники:
    - жители этого поселения с живыми телами в 32 блоках от сердца, кроме затейника, купца
      и стражи;
    - для поиска сперва дети, для ловли и стрельбы только взрослые;
    - устойчивый порядок — от опознавателя и дня.

- Проверки:
  - модульная `StandingsTest`:
    - `{a:5, b:5, c:3}` → места 1, 1, 3;
    - `{a:0, b:2}` → только b, первое;
    - ленты 3/2/1/0 (числа руками);
  - игровая `theWinnerGetsRibbonsAndATrophyOnce` (деревня, подставной игрок):
    - `Awards.grant` с игроком на первом месте → 3 ленты и кубок, в надписи ключ состязания,
      имя деревни, день;
    - доверие +5;
    - второй `grant` в тот же день — ничего;
    - второй игрок, победивший в повторе того же состязания, свой приз получает: приз один
      на игрока, а не на состязание;
    - доверие у знакомства (`Standing.KNOWN.from()`) не растёт выше.
- [ ] Шаги: красные → код → зелёные → коммит `feat: движок состязаний, места и призы`.

### Task 18: поиск

**Файлы:**
- Создать: `sim/festival/Hunt.java`, `sim/festival/HidingPlaces.java`
- Изменить: `FestivalTokenBlock.onUse` → `Matches.collect`; без идущего поиска вещица просто
  убирается (остаток).

**Правила** (`HidingPlaces.find(world, settlement, fair, count, random)`):
- колонны в 24 блоках от сердца, только в загруженных чанках;
- на земле народа:
  - у поверхностных — под открытым небом;
  - у гномов и эльфов — на полу в 3 блоках по высоте от пола ярмарки;
- ноги в воздухе или в низкой траве, голова в воздухе, под ногами твёрдое и не забор;
- не в следе здания, не на мостовой (`Roads.pavedColumns`), не в загоне и не на стрелище;
- не ближе 4 блоков друг к другу;
- сперва «спрятанные» места — с соседом-стеной, забором, цветком или кустом, — потом
  остальные; порядок — от случая дня.

**Соперник поиска:** видит вещицу в 6 блоках и идёт к ней; дошёл (1.5) — вещица его.
Не видит — идёт к случайной точке в 16 блоках от сердца, раз в три секунды новой.

- Проверки (batch `hunt`, `WIDE_STRUCTURE`):
  - `huntTokensHideWhereOneCanStand`: ровная площадка, дом (standUp) и полоса мостовой →
    не меньше 5 мест; все стоячие, ни одного в следе дома или на мостовой, попарно ≥ 4,
    все в 24 от сердца;
  - `everyTokenFoundEndsTheHuntWithRibbons`: `Matches.start` (подставной игрок, день
    праздника) → вещицы стоят; `collect` каждой → счёт = числу вещиц, состязание кончилось,
    вещиц нет, у игрока 3 ленты;
  - `aChildFindsATokenToo` (`runAtTick`): ребёнок-соперник в 4 блоках от вещицы → за 200 тиков
    его счёт ≥ 1;
  - `aForeignBlockOnATokenSpotStays`: после старта одну вещицу заменить сундуком,
    `cancel` → сундук на месте;
  - `aSiegeCallsTheHuntOff`: идёт поиск, поселение осаждено → на следующем `Matches.tick`
    состязание снято, вещиц нет, лент нет;
  - `leftoverTokensVanishAfterARestart`: вещица стоит и записана в `festive`, состязания нет →
    `Matches.recover` → воздух.
- [ ] Шаги: красные → код → зелёные; уборку доказать поломкой (снять проверку «всё ещё наше» —
  сундук исчезает) → вернуть → коммит `feat: состязание «поиск»`.

### Task 19: ловля

**Файлы:**
- Создать в `src/main/java/com/villagepax/entity/festival/`:
  - `FestivalCritters` — типы сущностей и атрибуты;
  - `FestivalPigEntity`, `FestivalChickenEntity`, `FestivalFoxEntity`, `FestivalRabbitEntity`,
    `FestivalSheepEntity` — наследники ванильных;
  - `PenRunner` (общее поле загона и опознавателя состязания);
  - `RunAroundThePenGoal`.
- Создать: `sim/festival/Chase.java`
- Изменить: `VillagePax` — `FestivalCritters.init()`; `VillagePaxClient` — ванильные
  отрисовщики для пяти типов (`PigEntityRenderer::new` и т. д.).

**Устройство зверька:**
- тип с `disableSaving()` и `disableSummon()` — не переживает выгрузку и не оставляет мусора;
- детёныш (`setBaby(true)`), неуязвим ко всему, кроме `/kill` и пустоты;
- не привязывается, не кормится;
- `initGoals`:
  - `super.initGoals()` — ванильные поля целей должны быть заданы: овца читает свою цель
    травы в `mobTick`;
  - затем снять все цели (`goalSelector.clear(goal -> true)`: проверить сигнатуру в Yarn
    1.20.1; иначе `getGoals().clear()`) и поставить свои: `SwimGoal`, `RunAroundThePenGoal`,
    `LookAroundGoal`.
- `RunAroundThePenGoal`, раз в 5 тиков:
  - ловец (игрок не в зрителях или житель) ближе 6 → клетка загона, дальняя от всех ловцов,
    из трёх лучших случайная, скорость × по виду (поросёнок, цыплёнок 1.4; ягнёнок 1.5;
    лис 1.2; заяц 1.3) — идущего обгоняет, бегущего нет;
  - никого → раз в три секунды к случайной клетке загона шагом;
  - вне загона — к ближайшей клетке загона.
- `interactMob` → `Matches.grab`.

**Соперник ловли:** цель — ближайший зверёк, раз в 5 тиков. Ближе 1.3 — пойман. Честолюбивому —
«скорость» I на время состязания, ленивому — «медлительность» I.

**Недоступно** (`NO_ROOM`), если стоячих клеток загона меньше 9 (загон засыпан или сломан).

- Проверки (batch `chase`):
  - `aCritterRunsFromAChaserButStaysInThePen`: загон 5×5 на ровном, поросёнок в загоне,
    кукла-житель рядом; 100 тиков → расстояние до ловца хоть раз выросло на 2+, и все
    100 тиков поросёнок в клетках загона (±0.7);
  - `aClickCatchesACritter`: `grab` подставным игроком → очко, зверёк исчез;
  - `aRivalCatchesByComingClose`: соперник вплотную → очко соперника;
  - `aFilledPenMakesTheChaseUnavailable`: загон засыпан землёй → `start` = `NO_ROOM`;
  - `critterBodiesAreNotSaved`: тип зверька не сохраняется (`EntityType#isSaveable` ложь).
- [ ] Шаги: красные → код → зелёные → доказать поломкой «не выходит из загона» (убрать
  возврат в загон) → вернуть → коммит `feat: состязание «ловля» — зверьки в загоне`.

### Task 20: стрельба

**Файлы:**
- Создать: `sim/festival/ArcheryScore.java` (чистая), `sim/festival/Archery.java`
- Изменить: `ArcheryTargetBlock.onProjectileHit` → `ArcheryScore.rings(...)` →
  `Matches.hit`.
- Изменить: `FestivalBowItem` — лук состязания:
  - `use`: состязание (NBT `Match` — опознаватель) идёт, стрелок участвует, выстрелы
    (`Shots`) есть → натягивает; иначе лук рассыпается с облачком и строкой
    «праздничный лук вернулся к затейнику»;
  - `onStoppedUsing`: стрела без боеприпаса (`ArrowEntity`, `pickupType = DISALLOWED`, метка
    `villagepax_festival`), выстрелов на один меньше;
  - `inventoryTick`: раз в секунду — нет такого состязания → лук исчезает.
- Изменить: `VillagePaxClient` — предикаты натяжения (задача 4) проверить на новом луке.

```java
/**
 * Очки за попадание в мишень — по месту на грани.
 * <p>
 * Кольца квадратные, как рисует мишень: середина — треть грани, среднее
 * кольцо — до пяти восьмых, остальное — край. Смещения — от середины грани,
 * в долях блока, от −0.5 до 0.5.
 */
public final class ArcheryScore {
    public static final int BULLSEYE = 5, INNER = 3, EDGE = 1;
    public static int rings(double u, double v) { ... }            // max(|u|,|v|) ≤ 0.125 → 5; ≤ 0.3125 → 3; иначе 1
    public static double[] onFace(Vec3d hit, Direction side, BlockPos block) { ... } // две оси грани
    /** Множитель дальности: ближняя 1, средняя 2, дальняя 3 — по расстоянию от черты. */
    public static int range(List<BlockPos> targets, BlockPos line, BlockPos target) { ... }
}
```

**Состязание:**
- у каждого участника-игрока праздничный лук (`Shots` 8), в руку или рядом на землю;
- соперники стреляют по очереди: у каждого 8 стрел, стрела раз в 30 тиков, стоя
  на стрелковой черте;
- мишень — по нраву: страж и честолюбивый бьют по дальней, ровный по средней, ленивый
  по ближней;
- разброс — страж 1.5, честолюбивый 3, ровный 4.5, ленивый 7; формула выстрела скелета:
  прицел `d, y + dist·0.2, f`, скорость 1.6;
- кончилось раньше — все выстрелы сделаны;
- уборка: стрелы с меткой в 32 блоках — `discard`, луки — из инвентарей участников и с земли.

**Недоступно** (`NO_ROOM`): на местах мишеней нет мишеней, или нет стрелковой черты.

- Проверки:
  - модульная `ArcheryScoreTest`, числа руками:
    - `rings(0, 0)` = 5, `rings(0.1, -0.05)` = 5;
    - `rings(0.2, 0)` = 3, `rings(0.3, 0.3)` = 3;
    - `rings(0.4, 0)` = 1, `rings(0.49, 0.49)` = 1;
    - `range`: три мишени на 4, 7, 10 от черты → 1, 2, 3;
  - игровые (batch `archery`, `WIDE_STRUCTURE`):
    - `aBullseyeScoresFiveTimesTheRange`: стрела игрока, пущенная в середину дальней мишени
      с полуметра, → 15 очков;
    - `aRivalsArrowCountsOnTheSameTarget`: `Archery.shoot(соперник, мишень)` → за 60 тиков очки
      соперника > 0 или стрела воткнулась мимо (тогда счёт 0 честно) — проверять,
      что попавшая стрела соперника начислена сопернику, а не игроку;
    - `theFestivalBowCrumblesAfterTheMatch`: после `finish` в инвентаре нет праздничного
      лука; лук с чужим опознавателем исчезает на `inventoryTick`;
    - `aBrokenTargetClosesTheRange`: мишень сломана → `start` = `NO_ROOM`.
- [ ] Шаги: красные → код → зелёные → коммит `feat: состязание «стрельба» — мишени и праздничный лук`.

### Task 21: экран затейника

**Файлы:**
- Создать: `src/main/java/com/villagepax/screen/FestivalView.java`,
  `src/main/java/com/villagepax/screen/FestivalNet.java`,
  `src/client/java/com/villagepax/client/screen/FestivalScreen.java`.
- Изменить: `CitizenEntity.interactMob` — затейник отвечает на щелчок всегда, в своей колонии
  и в чужой деревне: `FestivalNet.open(...)`, `greet()`, `SUCCESS`. Ветка — до проверки
  «выдаёт квесты или торгует».
- Изменить: `VillagePaxClient` — приёмник `FestivalNet.OPEN` → `FestivalScreen.open`;
  `VillagePax` — `FestivalNet.registerServer()`.

```java
/**
 * Что показывает экран затейника: праздник, состязания и лавка.
 * <p>
 * Снимок, а не живая связь: экран не спрашивает сервер на каждом кадре,
 * а получает снимок при открытии и после каждого действия. Предмет назван
 * опознавателем, а не кодеком предмета, — снимок читается проверкой без
 * запущенной игры.
 */
public record FestivalView(UUID village, String villageName, String host, String festival,
                           int daysUntil, Optional<String> closed, List<ContestLine> contests,
                           int ribbons, List<PrizeLine> prizes, Optional<String> running) {
    public record ContestLine(String name, String kind, boolean awarded, Optional<String> refusal) {}
    public record PrizeLine(Identifier item, int count, int price, boolean affordable) {}
    public static final Codec<FestivalView> CODEC = ...;
}
```

- **Пакеты:** `villagepax:festival_open` (сервер → клиент, NBT снимка),
  `villagepax:festival_start` (uuid, номер), `villagepax:festival_buy` (uuid, номер). Сервер
  проверяет, что игрок в 8 блоках от живого тела затейника этой деревни, — как `QuestNet.TALK_RANGE`.
- **Экран** (owo, `Look`):
  - заголовок: праздник и деревня;
  - строка «сегодня» или «через N дн.» / причина закрытия;
  - карточка «Состязания»: имя, правило одной строкой (`villagepax.contest.rule.<kind>`),
    кнопка «Начать» или плашка с причиной, плашка «приз взят»;
  - карточка «Лавка»: сколько лент, строки призов с ценой и кнопкой «Взять».
  - Размеры — через `PanelMetrics` (новые постоянные для экрана затейника проверяются
    `PanelMetricsTest.addsUp`).
- Проверки:
  - модульная `FestivalViewCodecTest` — снимок проходит кодек туда и обратно;
  - игровая `theEntertainerTellsWhenAndWhy`:
    - в будни снимок закрыт с `not_today` и днями;
    - в праздник открыт;
    - после приза у состязания `awarded`;
    - второй игрок во время идущего получает `busy`;
    - `start` из 20 блоков — отказ `too_far`.
- [ ] Шаги: красные → код → зелёные → коммит `feat: экран затейника — состязания и лавка`.

### Task 22: лавка и шапки

**Файлы:**
- Создать: `sim/festival/PrizeStall.java`
  - `buy(ServerWorld, PlayerEntity, Settlement, int index, long day) → Verdict {YES, CLOSED, NO_SUCH, POOR}`;
  - берёт ленты из инвентаря, выдаёт приз (в сумку, лишнее — под ноги);
  - ракеты — `Fireworks.rocket(festival, 2)` в нужном числе, а не голая ванильная ракета.
- Изменить: `FestivalNet` — обработчик `festival_buy`.
- Изменить: `CitizenEntity.tick` (сервер, раз в 20 тиков): ближайший игрок в 6 блоках в шапке
  народа этого жителя → `greet()`, не чаще раза в 600 тиков. Время последнего взмаха —
  поле `lastWave` (для проверки — метод `lastWave()`). Народ жителя — по поселению.
- Проверки (batch `stall`):
  - `theStallTradesRibbonsForAHat`:
    - 8 лент → шапка, лент 0;
    - 7 лент → `POOR`, ничего не изменилось;
    - в будни → `CLOSED`;
  - `rocketsFromTheStallCarryThePeoplesColours`;
  - `aHatMakesItsPeopleWave`: житель-норманн и подставной игрок в венке в 3 блоках →
    после тика тела `lastWave` обновилось; в чужой шапке (маска кицунэ) — нет.
- [ ] Шаги: красные → код → зелёные → коммит `feat: лавка затейника; в шапке народа тебе машут`.

---

## Часть E. Слова и проверка

### Task 23: книга, README, руководство для датапаков

**Файлы:**
- Изменить: `sim/Guide.java` и словари — страница «Праздники»: когда (луна народа, камень
  майя), где (ярмарка, затейник), что (хоровод, пироги, фейерверк), три состязания, ленты,
  кубок, лавка, колония празднует сама. Проверка `GuidePagesTest` — страница влезает.
- Изменить: `README.md` — раздел «## Праздники» после «Как встают деревни».
- Проверить: `docs/datapacks.md` (раздел задачи 9) описывает всё, что есть в коде
  (`DatapackGuideTest`).
- Изменить: `docs/plans/2026-09-27-festivals-design.md` — строка «Сделано» и отступления
  от замысла, если были (например, зайцы вместо козлят).
- [ ] Шаги: правки → `./gradlew test` → коммит `docs: праздники в книге, README и руководстве`.

### Task 24: лакмус и полная проверка

**Файлы:**
- Изменить: `ColonyTests.noModBlockIsLeftUnused` — законные исключения с комментарием:
  - пирог ставит праздник;
  - кубок даёт праздник;
  - вещица — праздник, и её вид назван в данных народа.
- Создать: проверка-лакмус `FestivalTests.aLunarMonthOfAVillage` (`WIDE_STRUCTURE`,
  `tickLimit` с запасом). Восемь дней подряд через `Heralds.lineFor(settlement, day)`
  и `Feast.tend(world, manager, settlement, day)`. Утверждения:
  - праздничный день ровно один;
  - в этот день на столах пироги, зов — `today`;
  - накануне зов — `tomorrow`;
  - наутро после праздника в следе ярмарки нет ни одного блока праздника (обход по следу).
- [ ] **Шаг 1.** Лакмус — зелёный (или найденная им беда исправлена отдельным коммитом с
  проверкой).
- [ ] **Шаг 2.** `./gradlew build` — выход 0.
- [ ] **Шаг 3.** `./gradlew test` — 0 отказов.
- [ ] **Шаг 4.** `./gradlew runGametest` трижды подряд — все проверки зелёные все три раза
  (мерцание — повод разобраться, а не перезапускать).
- [ ] **Шаг 5.** `python tools/check-generated.py` — «совпадает».
- [ ] **Шаг 6.** Настоящий рельеф:
  - копия «Проверка деревень на холмах» → `run/fair-check`, `level-name`;
  - `villagepax raise` норманнов, пони и гномов в трёх местах;
  - `tools/village-report.py` — ярмарка стоит, зазоры держатся, вход проходим;
  - `run/server.properties` вернуть на `playtest`.
- [ ] **Шаг 7.** Вычитка всего диффа от `8ab30f6` глазами: комментарии не обещают будущего,
  названия по-ванильному, ни одного `if (culture == …)`.
- [ ] **Шаг 8.** Память проекта: решения по праздникам и уроки.
- [ ] **Шаг 9.** Коммит `test: лунный месяц деревни — лакмус праздников`.
