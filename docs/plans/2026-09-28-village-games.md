# Игры с жителями — план работ

> **Исполнение:** в одной сессии, без подагентов и воркфлоу (решение заказчика). Шаги — чекбоксы.

**Цель:** по вечерам в пивной, а где её нет — у ратуши, собирается компания жителей; игрок
садится к ним играть в «очко» на костях и в армрестлинг в такт — на медяки в чужой деревне,
на интерес в своей колонии; соперник помнит счёт, радуется, досадует и зовёт отыграться
словами над головой. Днём дети зовут игрока в прятки и дарят гостинец тому, кто нашёл всех.

**Архитектура.**
- **Слова над головой** — фраза в отслеживаемых данных тела, сервер её гасит по времени,
  клиент рисует строкой над подписью. Фразы — строки словаря по случаю и нраву.
- **Правила** — чистые классы без мира: `Ochko`, `ArmWrestle`, `Purse`, `Strength`,
  `Rivalry`. Их проверяют модульные проверки на подставленных костях и тиках.
- **Память игр** — своё сохраняемое состояние мира `GamesLedger`: счёт каждого жителя с каждым
  игроком, потраченное из кошелька за день, бодрость после игры. Кодек жителя полон
  (16 полей), кодек поселения тоже — их не трогаем; замысел поправлен (раздел «Где лежит
  состояние»).
- **Вечер** — место игры (`GameSpot`), компания (`Company`), живая партия (`Bout`, `Bouts`).
  Как праздник, игра подменяет решение жителя: `Games.takesOver` в `WorkTicker.decide`.
- **Щелчок** — `Games.answer` до прежних веток `interactMob`: партия, прятки, окно игры
  или отказ; у кого своё дело по щелчку (затейник, выдающий квесты, купец за прилавком),
  тому обычный щелчок, а игре — щелчок с Shift.
- **Окно игры** — снимок `GameView` с кодеком и экран `GameScreen`, как у затейника; ход
  считает сервер, клиент только просит и рисует.
- **Прятки** — своя сессия `HideAndSeek`: места — те же, что у ярмарочного поиска.

**Стек:** Minecraft 1.20.1, Fabric, Yarn 1.20.1+build.10, Java 17, owo-lib, GeckoLib;
модульные проверки — JUnit 5 (`./gradlew test`), игровые — Fabric gametest
(`./gradlew runGametest`); модели мебели — `tools/make-furniture.py`.

**Замысел:** [docs/plans/2026-09-28-village-games-design.md](2026-09-28-village-games-design.md) —
план выводится из него, и читать их надо вместе.

**Порядок исполнения:** 1 → 2 → … → 17 по номерам.

**Время и игроки — доводы, а не спрос у мира.** Мир игровых проверок общий: время суток
в нём не подвинешь, а подставной игрок (`createMockSurvivalPlayer`) не входит в
`world.getPlayers()`. Поэтому всё игровое принимает день, время и список игроков рядом
параметром (`Company.of(..., day, part, timeOfDay, players)`, `Bouts.start(..., day,
timeOfDay)`, `HideAndSeek.invite(..., day, timeOfDay, players)`), а тикер мира подставляет
настоящие. Игрок в правилах — `PlayerEntity`, не `ServerPlayerEntity`; где нужен сетевой
игрок (окно, полоса, заголовок), он проверяется `instanceof`. Партия и прятки помнят
подставного игрока так же, как состязание праздника (`Match.playerOf`). Кости — подставные
(`IntSupplier`), тики правил — свой счётчик.

## Общие ограничения

- Модуль `villagepax`, пакет `com.villagepax`; новое — в `com.villagepax.sim.games`.
  Комментарии и javadoc — по-русски, в тоне кода: не «что», а «почему».
- Народ добавляется данными: никаких `if (culture == …)`; нрав — через `Natures.of`.
  Кошелёк и сила по ремеслу — решение по игре о ремёслах мода; ремесло из чужого датапака
  получает обычные (4 медяка, сила 0,4) — это сказано в javadoc.
- Кодек жителя (16 полей) и кодек поселения не расширять: память игр — в `GamesLedger`.
- Числа решения по игре — из замысла, дословно: кошелёк купец **18**, старейшина **8**,
  прочие **4**, честолюбивый **×1,5**, трус **×0,5**; ставки **1, 2, 5, 9**; ставка не больше
  **половины** остатка кошелька и не больше монет игрока; очко **21**, ровный на равном стоит
  с **18**; армрестлинг: край **±100**, от края до края **24** тика, партия **600** тиков,
  попадание **+18**, промах **−12**, давление **0,2 + 0,5 × сила** за тик, зелёное
  **0,30 − 0,15 × сила**, лентяй сдаётся на **+50**, трус на **+70**; обида после **трёх**
  поражений подряд за вечер; фраза — **60** тиков, не чаще раза в **40** тиков; компания —
  от **двух** до **четырёх**, в двух шагах от места; сама с собой — раз в **200** тиков при
  игроке в **24** блоках; оклик прохожему — в **6** блоках, не чаще раза в **6000** тиков на
  жителя; соперник дальше **8** блоков — сдача; прятки — до **четырёх** детей в **32**
  блоках, отсчёт **200** тиков, поиск **2400** тиков, найден в **2,5** блока, приглашение
  в **8** блоках и не чаще раза в **6000** тиков на поселение, игрок дальше **48** блоков —
  конец, гостинец — сласть народа и **2** медяка; колония — **+3** бодрости наутро.
- В проверках числа решения пишутся руками, а не читаются из констант.
- Мир игровых проверок общий: всё поставленное и все тела убирать в `finally`; площадь больше
  8 блоков от угла — `WIDE_STRUCTURE`; проверки с детьми и компанией — в своих `batchId`.
- Каждый новый блок: блокстейт, модель предмета, добыча, оба словаря, тег инструмента
  (`BlockResourcesTest`); каждый ключ — в `ru_ru` и `en_us` (`LangTest`).
- Модель игорного стола пишет генератор (`tools/make-furniture.py`), он уже в
  `check-generated.py`.
- Щелчок по жителю не съедает действие предметом без нужды: отказ фразой вне компании —
  только пустой рукой или с Shift (правило `interactMob` с первой недели).
- Файлы на Windows правятся с сохранением LF: Python — `read_bytes().decode()` /
  `write_bytes()`, длинные сценарии — файлом через Write, не heredoc.
- Отказ говорит причину — фразой над головой или строкой над рукой, никогда молча.
- Ничего не публиковать и не пушить; коммиты локальные, в конце
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Книга, README и `docs/datapacks.md` дописываются до сдачи (задача 16).

## Review Focus

1. **Игрок вышел из игры посреди партии** (или умер, или сменил мир). Партия снимается
   без выплаты, соперник возвращается в компанию, ничего не падает. Проверка — задача 9
   (`aBoutEndsWhenThePlayerIsGone`).
2. **Соперник пропал посреди партии** — погиб, выгрузился чанк, ушёл из деревни. Партия
   снимается без выплаты, окно у игрока закрывается с причиной. Проверка — задача 9
   (`aBoutEndsWhenTheRivalIsGone`).
3. **Двое за одним столом**: второй игрок зовёт того же соперника — «Уже играю»; один
   игрок не может вести две партии. Проверка — задача 9 (`oneRivalOneBout`).
4. **Монеты только в кошеле-предмете** или россыпью разного достоинства: ставку видно
   и платят из кошеля, сдача не теряется. Проверка — задача 10
   (`stakesArePaidFromAPurseToo`).
5. **Прятки кончились, а ребёнок так и остался без подписи и стоит в кустах** — после
   конца пряток любого рода (все найдены, время, игрок ушёл) у всех детей подпись видна
   и они вернулись к обычному дню. Проверка — задача 15 (`hidingEndsWithEveryoneBack`).

---

## Часть A. Слова

### Task 1: слова над головой

**Файлы:**
- Изменить: `src/main/java/com/villagepax/entity/CitizenEntity.java` — отслеживаемые данные
  `SPEECH` (`TrackedDataHandlerRegistry.OPTIONAL_TEXT_COMPONENT`), поля `speechUntil`
  и `spokeAt` (серверные, не сохраняются), методы `say(Text line)`, `speech()`, гашение
  в `tick()`.
- Изменить: `src/client/java/com/villagepax/client/CitizenEntityRenderer.java` — `render(...)`
  рисует фразу строкой над подписью.
- Изменить: оба словаря — `villagepax.speech`: `«%s»` / `“%s”` (кавычки отличают сказанное
  от имени).
- Проверка: `src/gametest/java/com/villagepax/gametest/GamesTests.java` (новый класс, вписать
  в `src/gametest/resources/fabric.mod.json`).

**Interfaces:**
- Produces: `CitizenEntity#say(Text line)` — показать фразу на 60 тиков; вернёт `false`, если
  прошлой фразе меньше 40 тиков (новая не встала). `CitizenEntity#speech(): Optional<Text>`.
  `CitizenEntity.SPEECH_TICKS = 60`, `CitizenEntity.SPEECH_GAP = 40`.

- [ ] **Step 1: Проверка — красная.** В `GamesTests`:

```java
/**
 * Фраза встаёт над головой и через три секунды гаснет сама.
 * Вторая фраза раньше двух секунд не встаёт: иначе компания тараторила бы.
 */
@GameTest(templateName = EMPTY_STRUCTURE, batchId = "games_speech", tickLimit = 100)
public void aLineShowsOverTheHeadAndFades(TestContext context) {
    ServerWorld world = context.getWorld();
    SettlementManager manager = SettlementManager.get(world);
    BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
    Settlement colony = colonyWithBuilder(world, manager, hall);
    Citizen talker = hireWithBody(world, colony, BuildJob.BUILDER, context.getAbsolutePos(new BlockPos(4, 1, 4)));
    CitizenEntity body = (CitizenEntity) world.getEntity(talker.entityUuid().orElseThrow());
    if (!body.say(Text.literal("Шесть!"))) {
        context.throwGameTestException("Первая фраза не встала");
    }
    if (body.say(Text.literal("Ещё!"))) {
        context.throwGameTestException("Вторая фраза встала раньше двух секунд");
    }
    if (!body.speech().map(Text::getString).equals(Optional.of("Шесть!"))) {
        context.throwGameTestException("Над головой не та фраза: " + body.speech());
    }
    context.runAtTick(70, () -> {
        try {
            if (body.speech().isPresent()) {
                context.throwGameTestException("Фраза не погасла через три секунды: " + body.speech());
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    });
}
```

- [ ] **Step 2:** `bash .superpowers/sdd/2026-09-28-fields/check.sh game` — не собирается
  (`say` нет): это и есть красное.
- [ ] **Step 3: Код тела.**

```java
/**
 * Фраза над головой: что житель сказал вслух.
 * <p>
 * Отслеживаемые данные, а не чат: чат забивается и не показывает, кто
 * говорит, а над головой видно — вот этот сказал. Ключ с подстановками,
 * а не готовая строка: клиент покажет её на своём языке.
 */
private static final TrackedData<Optional<Text>> SPEECH =
        DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.OPTIONAL_TEXT_COMPONENT);

/** Сколько тиков фраза видна: три секунды. */
public static final int SPEECH_TICKS = 60;

/** Сколько тиков после фразы новая не встаёт: две секунды. */
public static final int SPEECH_GAP = 40;

/** До какого тика мира фраза видна. Только сервер; не сохраняется: это голос, а не память. */
private long speechUntil;

/** Когда сказана последняя фраза. */
private long spokeAt = -SPEECH_GAP;

/** Сказать вслух. @return встала ли фраза: чаще раза в две секунды житель не говорит */
public boolean say(Text line) {
    long now = getWorld().getTime();
    if (now - spokeAt < SPEECH_GAP) {
        return false;
    }
    spokeAt = now;
    speechUntil = now + SPEECH_TICKS;
    dataTracker.set(SPEECH, Optional.of(line));
    return true;
}

/** Что житель сейчас говорит. */
public Optional<Text> speech() {
    return dataTracker.get(SPEECH);
}
```

  В `initDataTracker` — `dataTracker.startTracking(SPEECH, Optional.empty());`. В `tick()`
  на сервере: `if (speechUntil != 0 && getWorld().getTime() >= speechUntil) { speechUntil = 0;
  dataTracker.set(SPEECH, Optional.empty()); }`.
- [ ] **Step 4: Отрисовка.** В `CitizenEntityRenderer`:

```java
/** На сколько выше подписи встаёт фраза: строка и промежуток. */
private static final float SPEECH_RISE = 0.3f;

/**
 * Сказанное — строкой над подписью, в кавычках: имя и слово не спутать.
 * <p>
 * Рисуется всегда, а не только когда видна подпись: спрятавшийся ребёнок
 * без подписи всё равно кричит «Нашёл!», когда его нашли.
 */
@Override
public void render(CitizenEntity citizen, float yaw, float delta, MatrixStack matrices,
                   VertexConsumerProvider buffers, int light) {
    super.render(citizen, yaw, delta, matrices, buffers, light);
    citizen.speech().ifPresent(line -> {
        matrices.push();
        matrices.translate(0, SPEECH_RISE + (statureOf(citizen).height() - 1.0f) * citizen.getHeight(), 0);
        super.renderLabelIfPresent(citizen, Text.translatable("villagepax.speech", line),
                matrices, buffers, light);
        matrices.pop();
    });
}
```

  Сигнатуру `render` сверить с `GeoEntityRenderer` GeckoLib 4 для 1.20.1 (`render(T, float,
  float, MatrixStack, VertexConsumerProvider, int)`); `super.renderLabelIfPresent` —
  ванильный, без нашей поправки на рост: поправку даёт `translate` выше.
- [ ] **Step 5:** зелёные (и `LangTest`) → `git add` → коммит `feat: слова жителя над головой`.

### Task 2: фразы — случаи, нравы, запас

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/Say.java` (случаи), `Lines.java` (выбор ключа
  и `say`).
- Изменить: `src/main/resources/assets/villagepax/lang/ru_ru.json`, `en_us.json` — пул фраз.
- Проверка: `src/test/java/com/villagepax/sim/games/LinesTest.java`.

**Interfaces:**
- Produces: `enum Say` с `id()`; `Lines.pick(Say, Nature, Predicate<String> known,
  IntUnaryOperator choose): String` (чистая); `Lines.say(CitizenEntity body, Citizen citizen,
  Say say, Object... args): boolean` — выбрать ключ по нраву жителя и сказать.

- [ ] **Step 1: Случаи.**

```java
package com.villagepax.sim.games;

import com.villagepax.core.Named;

/** По какому поводу житель говорит за игрой. Порядок не значим; имя — часть ключа словаря. */
public enum Say implements Named {
    ACCEPT("accept"), BUSY("busy"), ASLEEP("asleep"), FESTIVAL("festival"), PLAYING("playing"),
    BROKE("broke"), SULK("sulk"), PIOUS("pious"), FULL("full"),
    ROLL_HIGH("roll_high"), ROLL_LOW("roll_low"), BUST("bust"), OCHKO("ochko"),
    WIN("win"), LOSE("lose"), PUSH("push"), STRAIN("strain"),
    REMATCH("rematch"), BOAST("boast"), AMBIENT_THROW("ambient_throw"), AMBIENT_REPLY("ambient_reply"),
    HIDE_INVITE("hide_invite"), HIDE_NOWHERE("hide_nowhere"), HIDE_FOUND("hide_found"),
    HIDE_LOST("hide_lost"), HIDE_THANKS("hide_thanks"), THANKS("thanks");

    private final String id;

    Say(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
```

- [ ] **Step 2: Проверка — красная.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Фраза выбирается из запаса нрава, а если нраву нечего сказать — из общего.
 * Номера идут подряд с единицы: словарь пополняется дописыванием, без правки кода.
 */
class LinesTest {

    private static final String BASE = "villagepax.games.say.win";

    @Test
    void aNatureSpeaksFromItsOwnPool() {
        Set<String> known = Set.of(BASE + ".ambitious.1", BASE + ".ambitious.2", BASE + ".1");
        assertEquals(BASE + ".ambitious.2", Lines.pick(Say.WIN, Nature.AMBITIOUS, known::contains, bound -> 1));
    }

    @Test
    void withoutItsOwnPoolTheCommonOneSpeaks() {
        Set<String> known = Set.of(BASE + ".1", BASE + ".2", BASE + ".3");
        assertEquals(BASE + ".3", Lines.pick(Say.WIN, Nature.COWARD, known::contains, bound -> bound - 1));
    }

    @Test
    void aGapEndsThePool() {
        Set<String> known = Set.of(BASE + ".1", BASE + ".3");
        assertEquals(BASE + ".1", Lines.pick(Say.WIN, Nature.EVEN, known::contains, bound -> bound - 1));
    }

    /** Совсем пустой запас — всё равно ключ: игрок увидит его и поймёт, что забыли слово. */
    @Test
    void anEmptyPoolStillNamesAKey() {
        assertEquals(BASE + ".1", Lines.pick(Say.WIN, Nature.EVEN, key -> false, bound -> 0));
    }
}
```

- [ ] **Step 3:** `bash .superpowers/sdd/2026-09-28-fields/check.sh unit` — не собирается: красное.
- [ ] **Step 4: Код.**

```java
package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Что житель говорит за игрой: ключ словаря по случаю и нраву.
 * <p>
 * Ключи — {@code villagepax.games.say.<случай>.<нрав>.<n>} с запасом
 * {@code villagepax.games.say.<случай>.<n>}. Счёт идёт по словарю, а не
 * по числу в коде: новая фраза дописывается строкой, и мод её подхватывает.
 * Считает сервер по своему словарю (Fabric грузит в него {@code en_us}
 * модов); наборы ключей у языков одинаковы — это держит {@code LangTest}.
 */
public final class Lines {

    private static final String PREFIX = "villagepax.games.say.";

    private Lines() {
    }

    /** Ключ фразы: сперва запас нрава, потом общий. {@code choose} — случай от 0 до границы. */
    static String pick(Say say, Nature nature, Predicate<String> known, IntUnaryOperator choose) {
        String base = PREFIX + say.id();
        String own = base + "." + nature.id();
        int mine = count(own, known);
        if (mine > 0) {
            return own + "." + (1 + choose.applyAsInt(mine));
        }
        int common = count(base, known);
        return common > 0 ? base + "." + (1 + choose.applyAsInt(common)) : base + ".1";
    }

    private static int count(String prefix, Predicate<String> known) {
        int n = 0;
        while (known.test(prefix + "." + (n + 1))) {
            n++;
        }
        return n;
    }

    /** Сказать над головой фразу по нраву этого жителя. @return встала ли фраза */
    public static boolean say(CitizenEntity body, Citizen citizen, Say say, Object... args) {
        String key = pick(say, Natures.of(citizen), Language.getInstance()::hasTranslation,
                bound -> body.getRandom().nextInt(bound));
        return body.say(Text.translatable(key, args));
    }
}
```

- [ ] **Step 5: Словарь.** В оба языка — пулы. Минимум три общих фразы на случай и хотя бы
  по одной своей у честолюбивого и труса там, где нрав слышен (согласие, победа, поражение,
  обида). Подстановка `%s` — имя игрока там, где житель к нему обращается. Русский пул:

```
accept.1 «Садись, сыграем.»  accept.2 «Давай! Ставь.»  accept.3 «А давай.»
accept.ambitious.1 «Ну держись, %s!»  accept.coward.1 «Только по маленькой…»
busy.1 «Не сейчас — работаю.»  busy.2 «Вечером приходи.»  busy.3 «Дела, дела…»
asleep.1 «Сплю я…»  asleep.2 «Завтра, всё завтра…»  asleep.3 «Какие кости — ночь!»
festival.1 «Сегодня праздник — на ярмарку!»  festival.2 «Не до костей: праздник!»  festival.3 «Ярмарка ждёт!»
playing.1 «Уже играю.»  playing.2 «Погоди, доиграю.»  playing.3 «Занят, видишь — партия.»
broke.1 «Продулся, приходи завтра.»  broke.2 «Пусто в кармане.»  broke.3 «Всё спустил. Завтра!»
sulk.1 «С тобой сегодня больше не играю.»  sulk.2 «Хватит с меня.»  sulk.3 «Колдун ты, что ли…»
sulk.ambitious.1 «Завтра отыграюсь, вот увидишь.»
pious.1 «На деньги не играю.»  pious.2 «Грех это — кости.»  pious.3 «Не для меня забава.»
full.1 «За столом места нет.»  full.2 «Нас уже четверо.»  full.3 «Подожди, пока кто встанет.»
roll_high.1 «Шесть!»  roll_high.2 «Ого!»  roll_high.3 «Вот это бросок!»
roll_low.1 «Эх, единица…»  roll_low.2 «Мелочь.»  roll_low.3 «Ну и кость…»
bust.1 «Перебор!»  bust.2 «Тьфу, перебрал.»  bust.3 «Жадность сгубила.»
ochko.1 «Очко!»  ochko.2 «Двадцать одно!»  ochko.3 «Вот это везение!»
win.1 «Моя взяла!»  win.2 «Ха! Плати!»  win.3 «Везёт же мне.»
win.ambitious.1 «Я лучший за этим столом!»  win.coward.1 «Ой, выиграл… правда?»
lose.1 «Эх, где моё везение?»  lose.2 «Твоя взяла.»  lose.3 «Ну ничего, отыграюсь.»
lose.ambitious.1 «Ещё партию! Сейчас же!»  lose.coward.1 «Так я и знал…»
push.1 «Ничья — забирай своё.»  push.2 «Поровну!»  push.3 «Ни тебе, ни мне.»
strain.1 «Ы-ы-ы!»  strain.2 «Не… сдамся!»  strain.3 «Крепкий ты!»
rematch.1 «Эй, %s! Сочтёмся вечером!»  rematch.2 «Жду реванша!»  rematch.3 «Вечером — к столу!»
boast.1 «А, %s! Опять проиграть пришёл?»  boast.2 «Помнишь, как я тебя?»  boast.3 «Мастер костей идёт!»
ambient_throw.1 «Шесть!»  ambient_throw.2 «Пять и четыре!»  ambient_throw.3 «Ставлю ещё!»
ambient_reply.1 «Везёт же!»  ambient_reply.2 «Перебор, ха!»  ambient_reply.3 «Мой бросок!»
hide_invite.1 «Поиграешь с нами в прятки?»  hide_invite.2 «Сыграем в прятки?»  hide_invite.3 «Чур, ты водишь!»
hide_nowhere.1 «А тут и спрятаться негде…»  hide_nowhere.2 «Ой, некуда прятаться.»  hide_nowhere.3 «Давай в другом месте?»
hide_found.1 «Нашёл!»  hide_found.2 «Ой, нашёл…»  hide_found.3 «Как ты догадался?»
hide_lost.1 «Не нашёл, не нашёл!»  hide_lost.2 «А я тут был!»  hide_lost.3 «Мы победили!»
hide_thanks.1 «Спасибо, что поиграл с детьми.»  hide_thanks.2 «Держи гостинец!»  hide_thanks.3 «Уважил ребятню!»
thanks.1 «Славный вечер, хозяин.»  thanks.2 «Спасибо за игру!»  thanks.3 «Ещё сыграем!»
```

  Английский — тот же смысл в тех же ключах: «Sit down, let's play.», «Six!», «Bust!»,
  «Twenty-one!», «Mine!», «Where did my luck go?», «Hey, %s! We'll settle it tonight!»,
  «Found me!», «Want to play hide-and-seek?» и т. д. — каждую строку перевести, не пропуская
  ни одной: `LangTest` сверяет наборы ключей.
- [ ] **Step 6:** зелёные (модульные и `LangTest`) → коммит `feat: фразы игр — по случаю и нраву`.

---

## Часть B. Правила без мира

### Task 3: «очко»

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/Ochko.java`
- Проверка: `src/test/java/com/villagepax/sim/games/OchkoTest.java`

**Interfaces:**
- Produces: `new Ochko(Nature rival, IntSupplier dice)`; `roll(): int`, `stand()`,
  `rivalStep(): OptionalInt`, `phase(): Phase {PLAYER, RIVAL, DONE}`,
  `outcome(): Optional<Outcome {WIN, LOSE, PUSH}>` — глазами игрока, `stakes(): int` — во сколько
  ставок итог (0, 1, 2), `mine()/theirs(): List<Integer>`, `myTotal()/theirTotal(): int`.
  `Ochko.LIMIT = 21`.

- [ ] **Step 1: Проверка — красная.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * «Очко» на костях — решение по игре, и числа здесь написаны руками.
 * <p>
 * Игрок бросает первым; соперник видит его счёт и добирает, пока не обгонит.
 * На равном решает нрав: трус и лентяй встают, ровный — с восемнадцати,
 * честолюбивый рискует. Ровно двадцать одно платится вдвойне.
 */
class OchkoTest {

    private static IntSupplier dice(int... faces) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int face : faces) {
            queue.add(face);
        }
        return queue::removeFirst;
    }

    private static Ochko play(Nature rival, int[] mine, int... theirs) {
        int[] all = new int[mine.length + theirs.length];
        System.arraycopy(mine, 0, all, 0, mine.length);
        System.arraycopy(theirs, 0, all, mine.length, theirs.length);
        Ochko game = new Ochko(rival, dice(all));
        for (int i = 0; i < mine.length; i++) {
            game.roll();
        }
        if (game.phase() == Ochko.Phase.PLAYER) {
            game.stand();
        }
        while (game.phase() == Ochko.Phase.RIVAL) {
            game.rivalStep();
        }
        return game;
    }

    @Test
    void overTwentyOneLosesAtOnceAndTheRivalDoesNotRoll() {
        Ochko game = new Ochko(Nature.EVEN, dice(6, 6, 6, 5));
        game.roll();
        game.roll();
        game.roll();
        game.roll();
        assertEquals(Ochko.Phase.DONE, game.phase());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
        assertEquals(List.of(), game.theirs());
        assertEquals(1, game.stakes());
    }

    @Test
    void standingNeedsARollFirst() {
        Ochko game = new Ochko(Nature.EVEN, dice());
        assertThrows(IllegalStateException.class, game::stand);
    }

    @Test
    void theRivalRollsUntilHeIsAhead() {
        Ochko game = play(Nature.EVEN, new int[]{6, 5, 4}, 5, 6, 6);
        assertEquals(15, game.myTotal());
        assertEquals(17, game.theirTotal());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
        assertEquals(1, game.stakes());
    }

    @Test
    void theRivalBustsAndPays() {
        Ochko game = play(Nature.EVEN, new int[]{6, 6, 6}, 6, 6, 5, 6);
        assertEquals(Optional.of(Ochko.Outcome.WIN), game.outcome());
        assertEquals(23, game.theirTotal());
    }

    @Test
    void aCowardStopsOnATie() {
        Ochko game = play(Nature.COWARD, new int[]{5, 5}, 6, 4);
        assertEquals(Optional.of(Ochko.Outcome.PUSH), game.outcome());
        assertEquals(0, game.stakes());
    }

    @Test
    void aLazyOneStopsOnATieToo() {
        assertEquals(Optional.of(Ochko.Outcome.PUSH), play(Nature.LAZY, new int[]{5, 5}, 6, 4).outcome());
    }

    @Test
    void theAmbitiousRollsOnATie() {
        Ochko game = play(Nature.AMBITIOUS, new int[]{5, 5}, 6, 4, 3);
        assertEquals(13, game.theirTotal());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
    }

    @Test
    void anEvenOneStopsOnATieFromEighteen() {
        assertEquals(Optional.of(Ochko.Outcome.PUSH),
                play(Nature.EVEN, new int[]{6, 6, 6}, 6, 6, 6).outcome());
        assertEquals(20, play(Nature.EVEN, new int[]{6, 6, 5}, 6, 6, 5, 3).theirTotal());
    }

    @Test
    void twentyOnePaysDouble() {
        Ochko game = play(Nature.EVEN, new int[]{6, 6, 5, 4}, 6, 6, 6, 4);
        assertEquals(21, game.myTotal());
        assertEquals(Optional.of(Ochko.Outcome.WIN), game.outcome());
        assertEquals(2, game.stakes());
    }

    /** У игрока двадцать: ровный добирает с восемнадцати и выбрасывает ровно очко. */
    @Test
    void theRivalsTwentyOneCostsDouble() {
        Ochko game = play(Nature.EVEN, new int[]{6, 6, 6, 2}, 6, 6, 6, 3);
        assertEquals(21, game.theirTotal());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
        assertEquals(2, game.stakes());
    }

    /** Оба с очком — ничья: на двадцати одном никто больше не бросает, даже честолюбивый. */
    @Test
    void bothWithTwentyOneIsATie() {
        Ochko game = play(Nature.AMBITIOUS, new int[]{6, 6, 5, 4}, 6, 6, 6, 3);
        assertEquals(Optional.of(Ochko.Outcome.PUSH), game.outcome());
        assertEquals(List.of(6, 6, 6, 3), game.theirs());
    }

    @Test
    void reachingTwentyOneStandsByItself() {
        Ochko game = new Ochko(Nature.EVEN, dice(6, 6, 6, 3));
        game.roll();
        game.roll();
        game.roll();
        game.roll();
        assertEquals(Ochko.Phase.RIVAL, game.phase());
    }

    @Test
    void aStoppedRivalReturnsNoFace() {
        Ochko game = new Ochko(Nature.EVEN, dice(3, 6));
        game.roll();
        game.stand();
        assertEquals(OptionalInt.of(6), game.rivalStep());
        assertEquals(OptionalInt.empty(), game.rivalStep());
        assertEquals(Ochko.Phase.DONE, game.phase());
    }
}
```

- [ ] **Step 2:** `check.sh unit` — не собирается: красное.
- [ ] **Step 3: Код.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

/**
 * «Очко» на костях: правила без мира.
 * <p>
 * Кость подставная: в игре её бросает случай мира, в проверке — список.
 * Игрок бросает, сколько хочет; соперник бросает вторым, видя счёт игрока,
 * и добирает, пока не обгонит, — так и сидят за столом: кто бросает вторым,
 * тот знает, до чего добирать. На равном решает нрав.
 */
public final class Ochko {

    /** Больше — перебор; ровно — «очко». */
    public static final int LIMIT = 21;

    /** С какого счёта ровный встаёт на равном. */
    static final int EVEN_STOPS_AT = 18;

    public enum Phase { PLAYER, RIVAL, DONE }

    /** Итог глазами игрока. */
    public enum Outcome { WIN, LOSE, PUSH }

    private final Nature rival;
    private final IntSupplier dice;
    private final List<Integer> mine = new ArrayList<>();
    private final List<Integer> theirs = new ArrayList<>();
    private Phase phase = Phase.PLAYER;
    private Outcome outcome;

    public Ochko(Nature rival, IntSupplier dice) {
        this.rival = rival;
        this.dice = dice;
    }

    /** Бросок игрока. Перебор — конец; ровно двадцать одно — ход сам переходит к сопернику. */
    public int roll() {
        if (phase != Phase.PLAYER) {
            throw new IllegalStateException("бросает не игрок: " + phase);
        }
        int face = face();
        mine.add(face);
        if (myTotal() > LIMIT) {
            end(Outcome.LOSE);
        } else if (myTotal() == LIMIT) {
            phase = Phase.RIVAL;
        }
        return face;
    }

    /** «Хватит» — только после первого броска: без броска нечего и хватать. */
    public void stand() {
        if (phase != Phase.PLAYER || mine.isEmpty()) {
            throw new IllegalStateException("встать можно после броска, а сейчас " + phase);
        }
        phase = Phase.RIVAL;
    }

    /**
     * Один шаг соперника: бросок, если он бросает, иначе итог.
     *
     * @return выпавшее, или пусто, если соперник встал и партия кончена
     */
    public OptionalInt rivalStep() {
        if (phase != Phase.RIVAL) {
            throw new IllegalStateException("бросает не соперник: " + phase);
        }
        if (!rivalGoesOn()) {
            end(theirTotal() > myTotal() ? Outcome.LOSE : Outcome.PUSH);
            return OptionalInt.empty();
        }
        int face = face();
        theirs.add(face);
        if (theirTotal() > LIMIT) {
            end(Outcome.WIN);
        }
        return OptionalInt.of(face);
    }

    /** Бросит ли соперник ещё: позади — бросает, впереди — встаёт, на равном — по нраву. */
    boolean rivalGoesOn() {
        int his = theirTotal();
        int yours = myTotal();
        if (his == LIMIT || his > yours) {
            return false;
        }
        if (his < yours) {
            return true;
        }
        return switch (rival) {
            case COWARD, LAZY -> false;
            case AMBITIOUS -> true;
            case EVEN, PIOUS -> his < EVEN_STOPS_AT;
        };
    }

    /** Во сколько ставок итог: ничья — ноль, «очко» победителя — две, иначе одна. */
    public int stakes() {
        if (outcome == null || outcome == Outcome.PUSH) {
            return 0;
        }
        int winner = outcome == Outcome.WIN ? myTotal() : theirTotal();
        return winner == LIMIT ? 2 : 1;
    }

    public Phase phase() {
        return phase;
    }

    public Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public List<Integer> mine() {
        return List.copyOf(mine);
    }

    public List<Integer> theirs() {
        return List.copyOf(theirs);
    }

    public int myTotal() {
        return mine.stream().mapToInt(Integer::intValue).sum();
    }

    public int theirTotal() {
        return theirs.stream().mapToInt(Integer::intValue).sum();
    }

    private int face() {
        int face = dice.getAsInt();
        if (face < 1 || face > 6) {
            throw new IllegalArgumentException("у кости нет грани " + face);
        }
        return face;
    }

    private void end(Outcome result) {
        outcome = result;
        phase = Phase.DONE;
    }
}
```

- [ ] **Step 4:** зелёные. Доказать поломкой: в `rivalGoesOn` убрать `his == LIMIT ||` —
  `bothWithTwentyOneIsATie` красная → вернуть → коммит `feat: «очко» — правила`.

### Task 4: армрестлинг в такт

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/ArmWrestle.java`
- Проверка: `src/test/java/com/villagepax/sim/games/ArmWrestleTest.java`

**Interfaces:**
- Produces: `new ArmWrestle(double strength, Nature nature)`; `tick()` — тик партии с давлением
  соперника; `press(): boolean` — нажатие игрока; `balance(): double`, `elapsed(): long`,
  `outcome(): Optional<Outcome {WIN, LOSE, DRAW}>`, `zoneWidth(): double`,
  `static markerAt(long tick): double` (0…1, туда и обратно), `inZone(double marker)`.
  Константы: `EDGE = 100`, `PASS_TICKS = 24`, `LIMIT_TICKS = 600`.

- [ ] **Step 1: Проверка — красная.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Армрестлинг в такт — решение по игре, и числа здесь написаны руками.
 * <p>
 * Отметка бежит от края до края за двадцать четыре тика; в зелёном
 * посередине нажатие клонит руку соперника на восемнадцать, мимо — свою
 * на двенадцать. Соперник давит сам каждый тик, и чем он сильнее,
 * тем уже зелёное.
 */
class ArmWrestleTest {

    private static void ticks(ArmWrestle bout, int many) {
        for (int i = 0; i < many; i++) {
            bout.tick();
        }
    }

    /** Проход: отметка доходит до середины, нажатие, отметка доходит до края. */
    private static void passes(ArmWrestle bout, int many) {
        for (int i = 0; i < many; i++) {
            ticks(bout, 12);
            bout.press();
            ticks(bout, 12);
        }
    }

    @Test
    void theMarkerRunsThereAndBack() {
        assertEquals(0.0, ArmWrestle.markerAt(0), 1e-9);
        assertEquals(0.5, ArmWrestle.markerAt(12), 1e-9);
        assertEquals(1.0, ArmWrestle.markerAt(24), 1e-9);
        assertEquals(0.5, ArmWrestle.markerAt(36), 1e-9);
        assertEquals(0.0, ArmWrestle.markerAt(48), 1e-9);
    }

    @Test
    void theStrongerTheNarrowerTheGreen() {
        assertEquals(0.30, new ArmWrestle(0.0, Nature.EVEN).zoneWidth(), 1e-9);
        assertEquals(0.18, new ArmWrestle(0.8, Nature.EVEN).zoneWidth(), 1e-9);
    }

    @Test
    void aHitInTheGreenLeansTheRivalsArm() {
        ArmWrestle bout = new ArmWrestle(0.4, Nature.EVEN);
        ticks(bout, 12);
        double before = bout.balance();
        assertTrue(bout.press());
        assertEquals(before + 18, bout.balance(), 1e-9);
    }

    @Test
    void aMissLeansYours() {
        ArmWrestle bout = new ArmWrestle(0.4, Nature.EVEN);
        ticks(bout, 2);
        double before = bout.balance();
        assertFalse(bout.press());
        assertEquals(before - 12, bout.balance(), 1e-9);
    }

    /** За один проход засчитывается одно нажатие: второе в том же зелёном — промах. */
    @Test
    void oneHitPerPass() {
        ArmWrestle bout = new ArmWrestle(0.4, Nature.EVEN);
        ticks(bout, 12);
        assertTrue(bout.press());
        assertFalse(bout.press());
    }

    @Test
    void theRivalPushesEveryTick() {
        ArmWrestle bout = new ArmWrestle(0.8, Nature.EVEN);
        ticks(bout, 10);
        assertEquals(-10 * (0.2 + 0.5 * 0.8), bout.balance(), 1e-9);
    }

    @Test
    void doingNothingLosesAtTheEdge() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.EVEN);
        ticks(bout, 200);
        assertEquals(Optional.of(ArmWrestle.Outcome.LOSE), bout.outcome());
    }

    /**
     * Сила 0: давление 0,2 за тик. После четвёртого нажатия (84-й тик)
     * перевес 4 × 18 − 0,2 × 84 = 55,2 — лентяй сдаётся, не дожидаясь края.
     */
    @Test
    void theLazyGivesUpWhenFarBehind() {
        ArmWrestle bout = new ArmWrestle(0.0, Nature.LAZY);
        passes(bout, 4);
        assertEquals(Optional.of(ArmWrestle.Outcome.WIN), bout.outcome());
        assertTrue(bout.balance() < 100);
    }

    /** Те же +55,2 трус терпит; после шестого нажатия +81,6 — сдаётся и он. */
    @Test
    void theCowardHoldsLongerThanTheLazy() {
        ArmWrestle bout = new ArmWrestle(0.0, Nature.COWARD);
        passes(bout, 4);
        assertEquals(Optional.empty(), bout.outcome());
        passes(bout, 2);
        assertEquals(Optional.of(ArmWrestle.Outcome.WIN), bout.outcome());
        assertTrue(bout.balance() < 100);
    }

    /** Сила 1: за проход давление 24 × 0,7 = 16,8, попадание +18 — к концу +30, до края далеко. */
    @Test
    void timeDecidesByWhoIsAhead() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.AMBITIOUS);
        passes(bout, 25);
        assertEquals(600, bout.elapsed());
        assertEquals(Optional.of(ArmWrestle.Outcome.WIN), bout.outcome());
        assertEquals(30, bout.balance(), 1e-6);
    }

    /** Давление 600 × 0,7 = 420, попадания 24 × 18 = 432, промах −12: к концу ровно ноль. */
    @Test
    void anEvenEndIsADraw() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.AMBITIOUS);
        ticks(bout, 2);
        assertFalse(bout.press());
        ticks(bout, 22);
        passes(bout, 24);
        assertEquals(Optional.of(ArmWrestle.Outcome.DRAW), bout.outcome());
    }

    @Test
    void nothingMovesAfterTheEnd() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.EVEN);
        ticks(bout, 200);
        double end = bout.balance();
        ticks(bout, 10);
        assertFalse(bout.press());
        assertEquals(end, bout.balance(), 1e-9);
    }
}
```

- [ ] **Step 2:** `check.sh unit` — красное.
- [ ] **Step 3: Код.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;

import java.util.Optional;

/**
 * Армрестлинг в такт: правила без мира.
 * <p>
 * Время — тики партии, а не часы мира: сервер ведёт отметку от тика начала,
 * клиент её только рисует, и нажатие судится по тому тику, в который пришло.
 * Допуск в тик — на дорогу пакета: иначе в гостях у сервера зелёное было бы
 * уже, чем дома.
 */
public final class ArmWrestle {

    /** Край полосы: рука легла. */
    public static final int EDGE = 100;

    /** От края шкалы до края — тиков. */
    public static final int PASS_TICKS = 24;

    /** Сколько длится партия: тридцать секунд. */
    public static final int LIMIT_TICKS = 600;

    static final double HIT = 18;
    static final double MISS = 12;
    static final double LAZY_GIVES_UP = 50;
    static final double COWARD_GIVES_UP = 70;

    /** Итог глазами игрока. */
    public enum Outcome { WIN, LOSE, DRAW }

    private final double strength;
    private final Nature nature;
    private double balance;
    private long elapsed;
    private long scoredPass = -1;
    private Outcome outcome;

    public ArmWrestle(double strength, Nature nature) {
        this.strength = Math.max(0, Math.min(1, strength));
        this.nature = nature;
    }

    /** Доля шкалы под зелёным: у сильного уже. */
    public double zoneWidth() {
        return 0.30 - 0.15 * strength;
    }

    /** Где отметка в этот тик: 0 — левый край, 1 — правый; туда и обратно. */
    public static double markerAt(long tick) {
        long t = Math.floorMod(tick, 2L * PASS_TICKS);
        return t <= PASS_TICKS ? (double) t / PASS_TICKS : 2.0 - (double) t / PASS_TICKS;
    }

    public boolean inZone(double marker) {
        return Math.abs(marker - 0.5) <= zoneWidth() / 2;
    }

    /** Тик партии: соперник давит, время идёт. */
    public void tick() {
        if (outcome != null) {
            return;
        }
        elapsed++;
        balance -= 0.2 + 0.5 * strength;
        settle();
    }

    /** Нажатие игрока. @return попал ли */
    public boolean press() {
        if (outcome != null) {
            return false;
        }
        long pass = elapsed / PASS_TICKS;
        boolean hit = pass != scoredPass
                && (inZone(markerAt(elapsed)) || inZone(markerAt(elapsed - 1)));
        if (hit) {
            balance += HIT;
            scoredPass = pass;
        } else {
            balance -= MISS;
        }
        settle();
        return hit;
    }

    private void settle() {
        balance = Math.max(-EDGE, Math.min(EDGE, balance));
        if (balance >= EDGE) {
            outcome = Outcome.WIN;
        } else if (balance <= -EDGE) {
            outcome = Outcome.LOSE;
        } else if (nature == Nature.LAZY && balance >= LAZY_GIVES_UP
                || nature == Nature.COWARD && balance >= COWARD_GIVES_UP) {
            outcome = Outcome.WIN;
        } else if (elapsed >= LIMIT_TICKS) {
            outcome = Math.abs(balance) < 1 ? Outcome.DRAW : balance > 0 ? Outcome.WIN : Outcome.LOSE;
        }
    }

    public double balance() {
        return balance;
    }

    public long elapsed() {
        return elapsed;
    }

    public Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }
}
```

  `markerAt(24)` — ровно правый край: условие `t <= PASS_TICKS`, чтобы 24 дало 1,0, а 36 — 0,5.
  Проход для «одного нажатия» — `elapsed / 24`: зелёное стоит посреди прохода и пересекается
  раз за проход, допуск в тик до края прохода не дотягивается.
- [ ] **Step 4:** зелёные; доказать поломкой (убрать `pass != scoredPass` — `oneHitPerPass`
  красная) → вернуть → коммит `feat: армрестлинг в такт — правила`.

### Task 5: кошелёк, сила и память соперника

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/Purse.java`, `Strength.java`, `Rivalry.java`
- Проверка: `src/test/java/com/villagepax/sim/games/PurseTest.java`, `RivalryTest.java`

**Interfaces:**
- Produces: `Purse.of(Optional<Identifier> profession, Nature nature): int`;
  `Purse.STAKES = List.of(1, 2, 5, 9)`; `Purse.allowed(int left, int playerCoins): List<Integer>`
  — ставки, которые соперник покрывает вдвое (ставка × 2 ≤ остаток) и которые есть у игрока
  (ставка ≤ его монет). `Strength.of(Optional<Identifier> profession, boolean elderly):
  double`. `record Rivalry(int won, int lost, int streak, long day, int lostToday)` —
  глазами жителя; `Rivalry.NONE`; `after(Result result, long day): Rivalry`;
  `sulks(long today): boolean`; `enum Result {WON, LOST, EVEN}`; `SULK_AFTER = 3`.

- [ ] **Step 1: Проверки — красные.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Кошелёк и сила — решение по игре, и числа здесь написаны руками. */
class PurseTest {

    private static Optional<Identifier> craft(String path) {
        return Optional.of(new Identifier("villagepax", path));
    }

    @Test
    void aMerchantCarriesTheMost() {
        assertEquals(18, Purse.of(craft("merchant"), Nature.EVEN));
        assertEquals(8, Purse.of(craft("elder"), Nature.EVEN));
        assertEquals(4, Purse.of(craft("farmer"), Nature.EVEN));
        assertEquals(4, Purse.of(Optional.empty(), Nature.EVEN));
    }

    @Test
    void natureScalesThePurse() {
        assertEquals(27, Purse.of(craft("merchant"), Nature.AMBITIOUS));
        assertEquals(6, Purse.of(craft("farmer"), Nature.AMBITIOUS));
        assertEquals(2, Purse.of(craft("farmer"), Nature.COWARD));
        assertEquals(4, Purse.of(craft("elder"), Nature.COWARD));
    }

    /** Ставка — не больше половины остатка: «очко» платится вдвойне, и платить есть из чего. */
    @Test
    void stakesFitHalfThePurse() {
        assertEquals(List.of(1, 2), Purse.allowed(4, 100));
        assertEquals(List.of(1, 2, 5, 9), Purse.allowed(18, 100));
        assertEquals(List.of(), Purse.allowed(1, 100));
    }

    /** И ставки, которой у игрока нет, окно не даёт выбрать. */
    @Test
    void stakesFitThePlayersCoins() {
        assertEquals(List.of(1, 2, 5), Purse.allowed(18, 5));
        assertEquals(List.of(), Purse.allowed(18, 0));
    }

    @Test
    void strengthComesFromTheCraftAndTheYears() {
        assertEquals(0.8, Strength.of(craft("builder"), false), 1e-9);
        assertEquals(0.8, Strength.of(craft("guard"), false), 1e-9);
        assertEquals(0.6, Strength.of(craft("courier"), false), 1e-9);
        assertEquals(0.4, Strength.of(craft("merchant"), false), 1e-9);
        assertEquals(0.4, Strength.of(Optional.empty(), false), 1e-9);
        assertEquals(0.6, Strength.of(craft("lumberjack"), true), 1e-9);
    }
}
```

```java
package com.villagepax.sim.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Житель помнит счёт с игроком и обижается после трёх поражений подряд за вечер. */
class RivalryTest {

    @Test
    void theRecordCounts() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.WON, 5).after(Rivalry.Result.LOST, 5);
        assertEquals(1, r.won());
        assertEquals(1, r.lost());
        assertEquals(-1, r.streak());
    }

    @Test
    void threeLossesInAnEveningSulk() {
        Rivalry r = Rivalry.NONE;
        for (int i = 0; i < 3; i++) {
            r = r.after(Rivalry.Result.LOST, 7);
        }
        assertTrue(r.sulks(7));
        assertFalse(r.sulks(8));
    }

    @Test
    void yesterdaysLossesDoNotCountTonight() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.LOST, 6).after(Rivalry.Result.LOST, 6)
                .after(Rivalry.Result.LOST, 7);
        assertFalse(r.sulks(7));
        assertEquals(-3, r.streak());
    }

    @Test
    void aWinBreaksTheEveningStreak() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.LOST, 7).after(Rivalry.Result.LOST, 7)
                .after(Rivalry.Result.WON, 7).after(Rivalry.Result.LOST, 7);
        assertFalse(r.sulks(7));
    }

    /** Ничья не прибавляет и не обрывает: «подряд» считают проигрыши. */
    @Test
    void aTieKeepsTheCount() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.LOST, 7).after(Rivalry.Result.LOST, 7)
                .after(Rivalry.Result.EVEN, 7).after(Rivalry.Result.LOST, 7);
        assertTrue(r.sulks(7));
    }
}
```

- [ ] **Step 2:** красное.
- [ ] **Step 3: Код.**

```java
package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Кошелёк жителя на вечер — из ничего, заново каждый день.
 * <p>
 * Жалование в моде не лежит у жителя в кармане: его платит казна и тут же
 * тратит рынок. Играть же на что-то надо, и кошелёк — это деньги «на вечер»:
 * у купца толще, честолюбивый ставит смелее, трус мельче. Ремесло чужого
 * датапака получает обычный кошелёк: о его достатке мод ничего не знает.
 */
public final class Purse {

    public static final List<Integer> STAKES = List.of(1, 2, 5, 9);

    private static final int MERCHANT = 18;
    private static final int ELDER = 8;
    private static final int COMMON = 4;

    private Purse() {
    }

    public static int of(Optional<Identifier> profession, Nature nature) {
        int base = profession.map(Identifier::getPath).map(path -> switch (path) {
            case "merchant" -> MERCHANT;
            case "elder" -> ELDER;
            default -> COMMON;
        }).orElse(COMMON);
        double scale = switch (nature) {
            case AMBITIOUS -> 1.5;
            case COWARD -> 0.5;
            default -> 1.0;
        };
        return Math.max(1, (int) Math.floor(base * scale));
    }

    /** Ставки, которые можно выбрать: соперник покрывает вдвое, а у игрока ставка есть. */
    public static List<Integer> allowed(int left, int playerCoins) {
        return STAKES.stream()
                .filter(stake -> 2 * stake <= left && stake <= playerCoins)
                .toList();
    }
}
```

```java
package com.villagepax.sim.games;

import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Сила руки: по ремеслу и годам.
 * <p>
 * Ремесло чужого датапака — средняя сила: о его работе мод ничего не знает.
 */
public final class Strength {

    private Strength() {
    }

    public static double of(Optional<Identifier> profession, boolean elderly) {
        double base = profession.map(Identifier::getPath).map(path -> switch (path) {
            case "builder", "guard", "lumberjack" -> 0.8;
            case "farmer", "courier", "brewer" -> 0.6;
            default -> 0.4;
        }).orElse(0.4);
        return Math.max(0, base - (elderly ? 0.2 : 0));
    }
}
```

  (Каменщика из замысла в моде нет — ремесла `mason` не существует; когда появится,
  строка дописывается к сильным.)

```java
package com.villagepax.sim.games;

/**
 * Счёт жителя с одним игроком — глазами жителя.
 *
 * @param won       сколько раз житель выиграл
 * @param lost      сколько раз проиграл
 * @param streak    серия: больше нуля — житель выиграл подряд, меньше — проиграл подряд
 * @param day       день последней партии
 * @param lostToday проигрышей подряд в этот день: из них и обида
 */
public record Rivalry(int won, int lost, int streak, long day, int lostToday) {

    public static final Rivalry NONE = new Rivalry(0, 0, 0, Long.MIN_VALUE, 0);

    /** После скольких поражений подряд за вечер житель встаёт из-за стола. */
    public static final int SULK_AFTER = 3;

    /** Исход партии глазами жителя. */
    public enum Result { WON, LOST, EVEN }

    public Rivalry after(Result result, long today) {
        int evening = day == today ? lostToday : 0;
        return switch (result) {
            case WON -> new Rivalry(won + 1, lost, Math.max(streak, 0) + 1, today, 0);
            case LOST -> new Rivalry(won, lost + 1, Math.min(streak, 0) - 1, today, evening + 1);
            case EVEN -> new Rivalry(won, lost, streak, today, evening);
        };
    }

    public boolean sulks(long today) {
        return day == today && lostToday >= SULK_AFTER;
    }
}
```

- [ ] **Step 4:** зелёные → коммит `feat: кошелёк, сила и счёт соперника`.

### Task 6: память игр — сохраняемое состояние

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/GamesLedger.java`
- Проверка: `src/test/java/com/villagepax/sim/games/GamesLedgerTest.java`

**Interfaces:**
- Produces: `GamesLedger.get(ServerWorld)`; `rivalry(UUID citizen, UUID player): Rivalry`;
  `record(UUID citizen, UUID player, Rivalry.Result result, long day)`;
  `spent(UUID citizen, long day): int`; `spend(UUID citizen, long day, int copper)`;
  `markCheer(UUID citizen, long day)`; `cheered(UUID citizen, long day): boolean`;
  `prune(Set<UUID> living, long today)`; `writeNbt` / `static fromNbt(NbtCompound)`.

- [ ] **Step 1: Проверка — красная.**

```java
package com.villagepax.sim.games;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Память игр переживает перезапуск: счёт, потраченное за день и бодрость после игры. */
class GamesLedgerTest {

    private static final UUID BERTHA = UUID.nameUUIDFromBytes("bertha".getBytes());
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());

    @Test
    void everythingSurvivesARestart() {
        GamesLedger ledger = new GamesLedger();
        ledger.record(BERTHA, PLAYER, Rivalry.Result.LOST, 9);
        ledger.record(BERTHA, PLAYER, Rivalry.Result.LOST, 9);
        ledger.spend(BERTHA, 9, 3);
        ledger.markCheer(BERTHA, 9);

        GamesLedger back = GamesLedger.fromNbt(ledger.writeNbt(new NbtCompound()));

        assertEquals(new Rivalry(0, 2, -2, 9, 2), back.rivalry(BERTHA, PLAYER));
        assertEquals(3, back.spent(BERTHA, 9));
        assertTrue(back.cheered(BERTHA, 9));
    }

    @Test
    void yesterdaysSpendingIsForgottenToday() {
        GamesLedger ledger = new GamesLedger();
        ledger.spend(BERTHA, 9, 3);
        assertEquals(0, ledger.spent(BERTHA, 10));
        ledger.spend(BERTHA, 10, 2);
        assertEquals(2, ledger.spent(BERTHA, 10));
    }

    @Test
    void aStrangerHasNoRecord() {
        assertEquals(Rivalry.NONE, new GamesLedger().rivalry(BERTHA, PLAYER));
        assertFalse(new GamesLedger().cheered(BERTHA, 1));
    }

    /** Ушедшего жителя и позавчерашнюю бодрость память не держит: мир живёт годами. */
    @Test
    void pruneForgetsTheGoneAndTheOld() {
        GamesLedger ledger = new GamesLedger();
        UUID gone = UUID.nameUUIDFromBytes("gone".getBytes());
        ledger.record(gone, PLAYER, Rivalry.Result.WON, 9);
        ledger.record(BERTHA, PLAYER, Rivalry.Result.WON, 9);
        ledger.markCheer(BERTHA, 7);
        ledger.prune(Set.of(BERTHA), 9);
        assertEquals(Rivalry.NONE, ledger.rivalry(gone, PLAYER));
        assertEquals(1, ledger.rivalry(BERTHA, PLAYER).won());
        assertFalse(ledger.cheered(BERTHA, 7));
    }
}
```

- [ ] **Step 2:** красное.
- [ ] **Step 3: Код.** `extends PersistentState`, ключ `villagepax_games`, `get` —
  `world.getPersistentStateManager().getOrCreate(GamesLedger::fromNbt, GamesLedger::new, KEY)`.
  Поля: `Map<UUID, Map<UUID, Rivalry>> rivals`, `Map<UUID, Spent> spent` (`record Spent(long day,
  int copper)`), `Map<UUID, Long> cheer`. `spend` на новый день начинает счёт с нуля. `prune`
  убирает счёт тех, кого нет среди живых, траты не сегодняшние и бодрость старше вчерашней.
  Каждое изменение — `markDirty()`. NBT — три списка: `rivals` [{citizen, player, won, lost,
  streak, day, today}], `spent` [{citizen, day, copper}], `cheer` [{citizen, day}]; битая запись
  пропускается, а не роняет чтение (как в `SettlementManager.fromNbt`).
- [ ] **Step 4:** зелёные → коммит `feat: память игр — счёт, кошелёк за день, бодрость`.

---

## Часть C. Вечер у стола

### Task 7: место игры и игорный стол

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/GameSpot.java`
- Изменить: `block/ModBlocks.java` — `GAME_TABLE = register("game_table", furniture(
  Block.createCuboidShape(0, 0, 0, 16, 16, 16)))`; `tools/make-furniture.py` — `game_table()`:
  стол как у `table()` и поверх столешницы две кости (`minecraft:block/bone_block_side`,
  2×2×2, вторая повёрнута на 22,5°) и две кружки (`minecraft:block/barrel_side`, 3×4×3);
  вписать в `main()`; ресурсы: `blockstates/game_table.json` (четыре поворота, как
  `table.json`), `models/item/game_table.json`, `loot_tables/blocks/game_table.json`, теги
  `minecraft:mineable/axe` и `villagepax:build_decor` (замысел, «Данные»), словари.
- Изменить: `sim/Streetscape.java` — `gameTable(world, manager, village, palette)`.
- Проверка: `GamesTests`.

**Interfaces:**
- Consumes: `SettlementManager#decorOf`, `SettlementManager#recordDecor`, `Access.entrances`,
  `Access.awayFrom`, `BuildingTypes.workplaceOf(culture.buildings(), CraftJob.BREWER)`.
- Produces: `GameSpot.of(ServerWorld, SettlementManager, Settlement): BlockPos` — на что
  смотрит компания: игорный стол из убранства; иначе клетка перед дверью достроенной
  пивной; иначе центр поселения. `GameSpot.TABLE_FROM = 3`, `GameSpot.TABLE_TO = 5`.

**Правила стола** (`Streetscape.gameTable`):
- только деревни народа на земле (как весь `dress`); у деревни, в убранстве которой игорного
  стола нет, ставится при каждом `dress`, пока не встанет, — старые деревни получают его
  на ближайшем обходе; колонии и чертогу стол не ставится;
- у двери достроенной пивной, если она есть, иначе у ратуши: клетки в 3–5 шагах кольцом,
  природная земля, не след здания, не дорога, воздух в рост — и то же для двух клеток лавок
  по бокам (ось — поперёк направления к центру деревни);
- стол лицом к центру, лавки лицом к столу; все три — в `recordDecor`.

- [ ] **Step 1: Проверки — красные** (batch `games_spot`, `WIDE_STRUCTURE`, луг как
  у `fairGround`, деревня — `colonyWithBuilder` и `setOwner(Owner.AUTONOMOUS)`):
  - `theSpotIsTheTableFirst`: игорный стол поставлен рукой и записан в убранство →
    `GameSpot.of` = стол;
  - `thenTheBreweryDoor`: без стола, пивная достроена (`standUp`) → клетка перед её дверью;
  - `thenTheTownHall`: ни того, ни другого → центр;
  - `aVillageGetsAGameTableOnce`: `Streetscape.dress` дважды → в убранстве ровно один стол
    и две лавки рядом с ним, стол в 3–5 от ратуши, на природной земле, лавки по бокам;
  - `aColonyGetsNoGameTable`: то же у колонии → стола нет.
- [ ] **Step 2:** красное → **Step 3:** код; генератор мебели → `python tools/make-furniture.py`,
  `python tools/check-generated.py` → **Step 4:** зелёные (`BlockResourcesTest`, `LangTest`
  тоже) → коммит `feat: игорный стол и место игры`.

### Task 8: вечерняя компания

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/Company.java`,
  `src/main/java/com/villagepax/sim/games/Games.java` (единая точка для `WorkTicker`
  и щелчка), `src/main/java/com/villagepax/sim/games/GamesTicker.java`
- Изменить: `sim/work/WorkTicker.java` — после `Revels.takesOver`:
  `if (com.villagepax.sim.games.Games.takesOver(context, part, day)) { return; }`;
  `VillagePax.onInitialize` — `GamesTicker.register()` (`ServerTickEvents.END_WORLD_TICK`
  через `Profiled.tick("games", …)`, как `Matches.register`);
  `entity/CitizenEntity.java` — триггерное движение `throw` (`triggerableAnim("throw",
  THROW)` рядом с `greet` и `cheer`, метод `throwDice()`) и петля `wrestle` по отслеживаемому
  флагу `WRESTLING`, как танец (`setWrestling(boolean)`, `isWrestling()`);
  `assets/villagepax/animations/entity/citizen.animation.json` и `citizen_pony.animation.json` —
  `throw` (1,0 с: правая рука трясёт кулак у груди и выбрасывает вперёд-вниз; у пони —
  голова вниз-вперёд и кивок) и `wrestle` (петля: правая рука вперёд и согнута, мелкая дрожь;
  у пони — шея вытянута вперёд, дрожь);
  `src/gametest/.../GameTestSupport.java` — `hireWithBody(world, settlement, profession, at,
  nature)`: перегрузка с нравом (прежняя зовёт её с ровным).

**Interfaces:**
- Consumes: `GameSpot.of`, `FestivalDay.isOn`, `Schedule`, `Natures.of`, `Ages.isAdult`,
  `Standing.canStandAt`, `Lines.say`.
- Produces: `Company.of(ServerWorld, Settlement, long day, Schedule part, long timeOfDay,
  List<? extends PlayerEntity> players): List<Citizen>`; `Company.standAt(ServerWorld,
  BlockPos spot, int index): BlockPos`; `Company.tick(ServerWorld, SettlementManager, Settlement,
  long day, long timeOfDay, List<? extends PlayerEntity> players)` — сама с собой;
  `Games.takesOver(WorkContext, Schedule, long day): boolean` — партия, прятки (задача 15),
  компания — в этом порядке; настоящие время и игроки — из мира.

**Правила компании:**
- время: досуг (`Schedule.LEISURE`); во сне — только до **14 000** и только если у места
  игры в 8 блоках игрок (засиживаются при госте); в праздник (`FestivalDay.isOn`) — никогда;
- кто: взрослые с телом, не набожные, не спящие; порядок — честолюбивые, ровные, лентяи,
  трусы, внутри нрава — по `Long.hashCode(citizen.id().getMostSignificantBits() ^ day)`;
  до четырёх; меньше двух — компании нет (одному за столом не компания: он идёт на сбор,
  а сыграть с ним всё равно можно — задача 9);
- где: `standAt(spot, i)` — i-я свободная клетка кольца в двух шагах от места: север, юг,
  восток, запад (лицом к лицу), потом углы; высота ног — уровень места, выше на один, ниже на
  один (`Standing.canStandAt`); не нашлось — само место;
- решение члена компании: `setWorkTarget(standAt)`, `setWorkFocus(spot)`, `holdNothing`;
- состав запоминается на тик мира: `WorkTicker` спрашивает его у каждого жителя;
- сама с собой (`tick`, каждые 200 тиков): игрок (не зритель) в 24 блоках от места, свободных от
  партии членов ≥ 2 → первый `throwDice()` и `Lines.say(AMBIENT_THROW)`, второй через 20 тиков
  `Lines.say(AMBIENT_REPLY)`; стук костей — `SoundEvents.BLOCK_WOOD_HIT` у места.

- [ ] **Step 1: Проверки — красные** (batch `games_company`, `WIDE_STRUCTURE`):
  - `theCompanyGathersAtTheTownHallInTheEvening`: деревня, шесть взрослых с телами разного нрава
    (набожный среди них), `Company.of(день 3, LEISURE, 11 500, [])` → четверо, без набожного,
    честолюбивые первыми; после `WorkTicker.decide(..., LEISURE, 3)` цели членов — клетки
    в двух шагах от центра, фокус — центр; набожный идёт на обычный сбор;
  - `noCompanyByDay`: `Company.of(день 3, DAY_WORK, 8 000, [])` → пусто;
  - `noCompanyOnAFestival`: день праздника народа (`FAIR_DAY`) → пусто;
  - `aLoneAdultIsNoCompany`: один взрослый с телом → пусто, он на сборе;
  - `aGuestKeepsThemUpLate`: `SLEEP`, 13 500, подставной игрок в 3 блоках от места → компания
    есть; без игрока → пусто; 14 500 с игроком → пусто;
  - `theCompanyPlaysAmongThemselves`: игрок в 10 блоках, `Company.tick` на тике, кратном 200 →
    у первого члена фраза над головой (ключ `ambient_throw`), через 20 тиков — у второго
    (`ambient_reply`); без игрока — ни фразы.
- [ ] **Step 2:** красное → **Step 3:** код (включая движения) → **Step 4:** зелёные, и все
  прежние проверки вечера (`eveningBringsCitizensToTheSquare`, `idleCitizensKeepToThePlaza…`,
  хоровод) — зелёные → доказать поломкой (не отсеивать набожного — красная) → вернуть →
  коммит `feat: вечерняя компания у стола`.

---

## Часть D. Партия

### Task 9: живая партия — жизнь и выплата

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/Bout.java`, `Bouts.java`
- Изменить: `sim/trade/Coins.java` — `public static List<ItemStack> stacksFor(int amount)`
  (крупными вперёд, по `WORTH`); `GamesTicker` — `Bouts.tick(world)` каждый тик,
  `SERVER_STOPPING` — `Bouts.stopAll` (снять без выплаты).

**Interfaces:**
- Consumes: `Ochko`, `ArmWrestle`, `Purse`, `Strength`, `GamesLedger`, `Company`, `Coins`,
  `Warehouse.of(world, settlement).addOrScatter`, `Lines.say`.
- Produces:
  - `enum Kind {DICE, ARM}`;
  - `Bouts.check(ServerWorld, PlayerEntity, Settlement, Citizen rival, long day, long timeOfDay):
    Verdict` и `Bouts.start(ServerWorld, PlayerEntity, Settlement, Citizen rival, Kind kind,
    int stake, long day, long timeOfDay): Verdict`;
  - `enum Verdict {YES, PLAYING, PIOUS, FESTIVAL, ASLEEP, BUSY, SULK, BROKE, FULL, TOO_FAR,
    STAKE}` (`Named`) с `say(): Optional<Say>` — фраза соперника; у `TOO_FAR` и `STAKE` фразы нет,
    их причина — строкой над рукой по `reasonKey()` (`villagepax.games.reason.<id>`);
  - `Bouts.of(UUID player): Optional<Bout>`, `Bouts.rivalOf(UUID citizen): Optional<Bout>`;
  - `Bout#roll()`, `Bout#stand()`, `Bout#press()`, `Bout#leave()` (сдача),
    `Bout#settled(): OptionalInt` — сколько медяков выиграно (+) или проиграно (−) по итогу;
  - `Bouts.steers(WorkContext): boolean` — соперник партии стоит, смотрит на игрока, в руке
    ничего (`setWrestling(true)` на армрестлинге);
  - `Bouts.withDice(IntSupplier): AutoCloseable` — кости для проверок (по умолчанию случай
    мира), возвращает прежние на `close`;
  - `Bouts.tick(ServerWorld)`, `Bouts.stopAll(MinecraftServer)`.

**Правила:**
- `check` — первая причина по порядку: соперник в другой партии или у игрока своя —
  `PLAYING`; набожный — `PIOUS`; праздник — `FESTIVAL`; сон (спит телом или сон по часам
  и не засиживаются) — `ASLEEP`; не досуг — `BUSY`; обида (`Rivalry.sulks`) — `SULK`;
  деревня народа и остаток кошелька < 2 (нет ни одной ставки) — `BROKE`; компания полна,
  а его в ней нет — `FULL`; нет живого тела или оно дальше 8 блоков — `TOO_FAR`; иначе `YES`;
- ставки — только в деревне народа (`owner().isAutonomous()`); в колонии — ставка 0 у всех,
  и у хозяина, и у гостя: колония не казино для гостей;
- `start`: `check`, ставка ∈ `Purse.allowed(остаток, Coins.total(инвентарь))` или колония со
  ставкой 0 — иначе `STAKE`; соперник говорит `ACCEPT` с именем игрока;
- игрок в партии — как у состязания (`Match.playerOf`): сетевой — по опознавателю с сервера,
  подставной — тот, что начал, пока не удалён (`isRemoved`);
- кости: игрок `roll` / `stand` из окна; после «хватит» соперник бросает раз в 10 тиков
  (`throwDice()`, стук), на выпавших 5–6 изредка (1 из 3) `ROLL_HIGH`, на 1–2 — `ROLL_LOW`;
- армрестлинг: `ArmWrestle.tick()` каждый тик; соперник раз за партию говорит `STRAIN`, когда
  перевес игрока переходит +60;
- итог: ставка × `stakes()` (у армрестлинга — 1, ничья — 0). Выигрыш игрока — из кошелька
  соперника: `GamesLedger.spend`, `Coins.earn(инвентарь)`, невлезшее —
  `player.getInventory().offerOrDrop`. Проигрыш — `Coins.pay(инвентарь, min(долг, монет))`
  и `Coins.stacksFor(заплаченное)` на склад деревни (`addOrScatter` у места игры); сдача
  от размена — `offerOrDrop`. Монет меньше долга бывает только при «очке» соперника вдвойне:
  платится сколько есть. Колония — без монет; с хозяином — `GamesLedger.markCheer(соперник,
  день)`, соперник говорит `THANKS`;
- фраза итога соперника: его «очко» — `OCHKO`, его перебор — `BUST`, его победа — `WIN`,
  поражение — `LOSE`, ничья — `PUSH`; счёт — `GamesLedger.record` глазами жителя;
- сдача (`leave`: окно закрыто до итога; игрок дальше 8 блоков от соперника — проверка раз
  в 20 тиков): игрок проигрывает одну ставку, соперник говорит `WIN`, игроку строка
  «Отошёл — партия сдана» (`villagepax.games.forfeit`);
- снятие без выплаты: игрок не в игре (вышел, мёртв, другой мир), тело соперника пропало,
  соперник ушёл из поселения, сервер останавливается — партия удаляется, игроку (если он
  здесь) строка «Партия прервана» (`villagepax.games.cancelled`) и закрытое окно; соперник
  возвращается в компанию; ничего не падает.

- [ ] **Step 1: Проверки — красные** (batch `games_bout`, `WIDE_STRUCTURE`; соперник —
  ровный фермер, кошелёк 4; кости — `Bouts.withDice`):
  - `aDiceBoutPaysTheWinner`: игрок-кукла с 10 медяками, ставка 2, кости игрока 6, 6, 5 →
    «хватит», соперника 6, 6, 4, 6 — перебор → у игрока 12 медяков, `GamesLedger.spent
    (соперник) = 2`, у соперника фраза `bust`;
  - `aLostBoutFillsTheVillageStore`: кости 6, 6 → «хватит», соперник 6, 6, 1 → у игрока 8,
    на складе деревни +2 медяка, у соперника фраза `win`;
  - `twentyOnePaysDouble`: игрок 6, 6, 5, 4 — «очко»; соперник 6, 6, 6, 4 — перебор → +4;
  - `aBrokeRivalRefuses`: `spend` на весь кошелёк → `check` = `BROKE`, фраза `broke`;
  - `threeLossesAndHeSulks`: три проигрыша соперника → `check` = `SULK`; назавтра — `YES`;
  - `oneRivalOneBout`: второй игрок тому же сопернику → `PLAYING`; первый игрок второму
    сопернику → `PLAYING`;
  - `walkingAwayForfeits`: партия идёт, кукла перенесена на 12 блоков → на следующей
    проверке партия сдана, у игрока −ставка, соперник говорит `win`;
  - `aBoutEndsWhenThePlayerIsGone`: кукла удалена (`discard`) посреди партии → партия снята,
    монеты не двигались, соперник вернулся в компанию;
  - `aBoutEndsWhenTheRivalIsGone`: тело соперника убрано → партия снята, монеты не двигались;
  - `theRivalFacesThePlayerAndStands`: во время партии цель соперника пуста, взгляд — на игрока.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные → доказать поломкой
  (не удалять партию при пропавшем игроке — `aBoutEndsWhenThePlayerIsGone` красная) → вернуть →
  коммит `feat: живая партия — ставки, итог, сдача`.

### Task 10: разговор — щелчок, окно игры и его пакеты

**Файлы:**
- Создать: `src/main/java/com/villagepax/screen/GameView.java`, `GamesNet.java`
- Изменить: `entity/CitizenEntity.java#interactMob` — перестроить: запись и поселение → есть ли
  у жителя своё дело по щелчку (`business`: затейник, выдающий квесты, купец за прилавком) →
  `Games.answer(...)`; ответ не `PASS` — `SUCCESS`; дальше прежние ветки (проверка
  «без ремесла — `PASS`» переезжает после игр: взрослый без ремесла тоже играет);
  `Games.java` — `answer`; `VillagePax` — `GamesNet.registerServer()`; `VillagePaxClient` —
  приём `GamesNet.OPEN` и `GamesNet.CLOSE`.
- Проверка: `src/test/java/com/villagepax/screen/GameViewCodecTest.java`, `GamesTests`.

**Interfaces:**
- Produces:
  - `Games.answer(ServerWorld, PlayerEntity, Settlement, Citizen, boolean business, long day,
    long timeOfDay): Answer {PASS, WINDOW, REFUSED, HIDING}`;
  - `record GameView(UUID village, UUID rival, String rivalName, String rivalTitle,
    String nature, int won, int lost, boolean coins, List<Integer> stakes,
    Optional<BoutLine> bout)` — `won`/`lost` глазами игрока;
    `record BoutLine(String kind, int stake, String phase, List<Integer> mine,
    List<Integer> theirs, int balance, long markerStart, double zone, Optional<String> outcome,
    int paid)`; оба с кодеком (`StrictCodecs.optional` для пустых);
  - `GamesNet.viewOf(ServerWorld, PlayerEntity, Settlement, Citizen rival, long day,
    long timeOfDay): GameView`;
  - каналы `villagepax:games_open` и `games_close` (S→C: снимок; закрыть с причиной),
    `games_start` (C→S: UUID соперника, вид, ставка), `games_roll`, `games_stand`,
    `games_press`, `games_leave` (C→S, без данных);
  - `GamesNet.open(ServerPlayerEntity, Settlement, Citizen rival)`, `GamesNet.send(player,
    view)`, `GamesNet.read(PacketByteBuf): Optional<GameView>`.

**Кто отвечает на щелчок** (`Games.answer`, по порядку):
1. своя партия с этим жителем — окно партии (`WINDOW`);
2. ребёнок — прятки (`HideAndSeek.clicked`, задача 15): ответил — `HIDING`, нет — `PASS`;
3. у жителя своё дело по щелчку и игрок не пригнулся — `PASS`: обычный щелчок — его делу,
   сыграть со старейшиной или купцом — щелчок с Shift;
4. `Bouts.check` = `YES` — окно игры с выбором игры и ставок (`WINDOW`);
5. отказ — если рука пуста или игрок пригнулся: соперник говорит причину (`REFUSED`);
   с предметом в руке — `PASS`, как прежде: не съедать действие предметом.

**Правила окна:**
- ставки в снимке — `Purse.allowed(...)`, у колонии `coins = false` и `stakes = [0]`;
- каждое изменение партии шлёт свежий снимок: бросок, шаг соперника, итог; армрестлинг —
  перевес раз в 2 тика;
- `markerStart` — `world.getTime()` начала армрестлинга: клиент рисует отметку сам по времени
  своего мира (`markerAt(time - markerStart)`), сервер судит по своему;
- `games_leave` до итога — `Bout#leave` (сдача); снятие партии шлёт `games_close` с причиной.

- [ ] **Step 1: Проверки — красные:**
  - модульная `GameViewCodecTest.aViewSurvivesTheWire`: снимок с партией и без — туда и обратно
    через `NbtOps` равен себе;
  - игровая `aClickOpensTheTableOrSaysWhyNot`: вечером `Games.answer` куклы по члену компании →
    `WINDOW`, в `viewOf` ставки 1 и 2 (кошелёк фермера 4); днём пустой рукой → `REFUSED`
    и у жителя фраза `busy`; днём с блоком в руке → `PASS`, фразы нет;
  - игровая `anEldersClickStaysHisOwn`: старейшина в компании, `business = true` → `PASS`;
    кукла пригнулась → `WINDOW`;
  - игровая `stakesArePaidFromAPurseToo`: у куклы нет россыпи, а в кошеле-предмете 20 медяков,
    соперник — купец (кошелёк 18) → в снимке ставки 1, 2, 5, 9; проигрыш 5 списан из кошеля,
    в кошеле 15.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные, и все прежние проверки
  разговора (квесты, прилавок, затейник) — зелёные → коммит `feat: щелчок и окно игры — снимок
  и пакеты`.

### Task 11: экран игры

**Файлы:**
- Создать: `src/client/java/com/villagepax/client/screen/GameScreen.java`
- Изменить: `client/screen/PanelMetrics.java` — `GAME_WIDTH = 280`, `GAME_HEIGHT = 200`;
  словари — строки экрана.
- Проверка: `src/test/java/com/villagepax/client/screen/PanelMetricsTest.java` (дописать).

**Устройство** (оформление — `Look.panel`, как у затейника):
- шапка: имя соперника, ремесло (`rivalTitle`), нрав (`villagepax.nature.<id>`), счёт
  «ты выиграл N, проиграл M»;
- без партии: две кнопки игры и ряд ставок (у колонии — «на интерес»), неподходящие
  не рисуются; ни одной ставки — строка «Нечего поставить» вместо ряда;
- кости: два ряда граней (свои и соперника) — цифрами в квадратах, суммы, кнопки «Бросить»
  и «Хватит» (только в фазе игрока), итог строкой и «Ещё партию» / «Встать»;
- армрестлинг: полоса перевеса −100…+100 (середина — черта), под ней шкала с зелёным
  (`zone`) и бегущей отметкой — положение считается каждый кадр из времени мира клиента
  и `markerStart` с частью тика; подсказка «Жми пробел, когда отметка в зелёном»; пробел
  и щелчок по шкале шлют `games_press`;
- закрытие экрана до итога — `games_leave`; `games_close` с сервера закрывает экран и
  показывает причину строкой над рукой.

- [ ] **Step 1: Проверка — красная:** `PanelMetricsTest.theGamePanelFitsTheSmallestWindow` —
  как у затейника: тело экрана положительной высоты, панель помещается в 320×240 при GUI 2.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные, сборка клиента
  (`./gradlew build`) → коммит `feat: экран игры — кости и армрестлинг`.

### Task 12: армрестлинг на сервере

**Файлы:**
- Изменить: `Bout.java` (ветка ARM), `GamesNet.java` (`games_press`).

**Правила:**
- партия начинается с `markerStart = world.getTime()` и тика 0 правил;
- каждый серверный тик — `ArmWrestle.tick()`; `games_press` — `ArmWrestle.press()` в тот же тик;
- у соперника `setWrestling(true)` на всю партию, `setWrestling(false)` — по итогу и снятию;
- итог — как у костей (ставка × 1), ничья — ставки назад.

- [ ] **Step 1: Проверки — красные** (batch `games_arm`; партия начинается на тике 0 проверки,
  и тик проверки `n` — тик правил `n`):
  - `pressingInTheGreenWinsTheArm`: соперник — купец (сила 0,4, давление 0,4 за тик), ставка 1;
    нажатие на тиках 12 + 24k (`runAtTick`) → за проход +18 − 9,6 = +8,4 — победа к 13-му
    проходу, +1 медяк;
  - `aSilentPlayerLosesTheArm`: без нажатий → поражение на 250-м тике (100 / 0,4), −1 медяк;
  - `theLazyRivalGivesUp`: ленивый соперник, нажатия в зелёном → победа до края.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные → коммит
  `feat: армрестлинг — партия на сервере`.

### Task 13: живые соперники — оклик и похвальба

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/Passersby.java`;
  `GamesTicker` — раз в 20 тиков с `world.getPlayers()`.

**Interfaces:**
- Produces: `Passersby.tick(ServerWorld, SettlementManager, long day, long timeOfDay,
  List<? extends PlayerEntity> players)`.

**Правила:**
- в любое время, кроме сна (замысел: «наутро… окликает»): игрок (не зритель) в 6 блоках
  от взрослого жителя с телом, у которого с ним есть счёт: житель проиграл последнюю
  партию (`streak < 0`) — `REMATCH` с именем игрока, выиграл (`streak > 0`) — `BOAST`; не чаще
  раза в 6000 тиков на жителя (память сервера);
- обиженный (`sulks`) не окликает; житель в партии не окликает — он занят игрой.

- [ ] **Step 1: Проверки — красные** (batch `games_rivals`):
  - `theLoserCallsForARematch`: счёт жителя «проиграл последнюю» → кукла в 4 блоках, утро →
    фраза `rematch`; через 100 тиков — второй раз не звучит;
  - `theWinnerBoasts`: «выиграл последнюю» → `boast`;
  - `aStrangerIsNotHailed`: без счёта — тишина; ночью — тишина.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные → коммит
  `feat: соперник окликает отыграться и хвалится`.

### Task 14: колония — на интерес и бодрость наутро

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/GameCheer.java` — 3, если
  `GamesLedger.cheered(житель, вчера)`, иначе 0.
- Изменить: `sim/work/Needs.java#newDay` — в сумму бодрости `+ GameCheer.of(world, citizen,
  today - 1)`, рядом с `FestivalDay.cheer`; `GamesTicker` — на смене дня
  `GamesLedger.prune(живые, сегодня)`.

**Правила:** колония: ставок нет у всех, итог без монет; с хозяином — `markCheer` соперника;
прятки в колонии (задача 15) — `markCheer` детям; бодрость — наутро, не больше 3 за день.

- [ ] **Step 1: Проверки — красные** (batch `games_colony`):
  - `aColonyPlaysForFun`: колония, хозяин-кукла с 10 медяками → снимок `coins = false`; партия
    кончилась — монеты те же, у соперника фраза `thanks`, `cheered` соперника в этот день;
  - `anEveningWithTheOwnerCheersUp`: `markCheer` вчера → `Needs.newDay` даёт на 3 больше, чем
    без него (сравнить двух колонистов);
  - `aGuestPlaysForFunInAColonyToo`: чужой игрок в колонии — тоже без монет, и бодрости нет.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные → коммит
  `feat: игры в колонии — на интерес и бодрость`.

---

## Часть E. Прятки

### Task 15: прятки с детьми

**Файлы:**
- Создать: `src/main/java/com/villagepax/sim/games/HideAndSeek.java`
- Изменить: `sim/festival/HidingPlaces.java` — перегрузка `find(ServerWorld, Settlement,
  BlockPos centre, int standingY, int count, Random)`; прежняя (с ярмаркой) зовёт её с
  `fair.heart()` и `fair.standingY()`; `core/culture/Culture.java` — поле
  `Optional<Identifier> treat` (`Identifier.CODEC.optionalFieldOf("treat")`), прежний
  конструктор без него — перегрузкой; данные: `cultures/pony.json` — `villagepax:rainbow_cupcake`,
  `maya.json` — `villagepax:cacao`, `elf.json` — `villagepax:elven_nectar`;
  `Games.answer` — ребёнок: `HideAndSeek.clicked(...)`; `Games.takesOver` —
  `HideAndSeek.steers(context)`; `GamesTicker` — `HideAndSeek.tick(world)` каждые 5 тиков
  и приглашения раз в 20 тиков с `world.getPlayers()`, `SERVER_STOPPING` — `HideAndSeek.stopAll`.
- Проверка: `src/gametest/java/com/villagepax/gametest/HideAndSeekTests.java` (вписать в
  `fabric.mod.json`); модульная — `aTreatIsOptional` в проверке кодека народа.

**Interfaces:**
- Produces: `HideAndSeek.invite(ServerWorld, Settlement, long day, long timeOfDay,
  List<? extends PlayerEntity> players)`, `HideAndSeek.clicked(ServerWorld, PlayerEntity,
  Settlement, Citizen child, long day, long timeOfDay): boolean`,
  `HideAndSeek.at(UUID settlement): Optional<Session>`, `HideAndSeek.found(ServerWorld,
  PlayerEntity, Citizen child)`, `HideAndSeek.steers(WorkContext): boolean`,
  `HideAndSeek.tick(ServerWorld)`, `HideAndSeek.stopAll(MinecraftServer)`.

**Правила:**
- приглашение: часы работы (`Schedule#isWork` — утро и день), не праздник, нет идущих пряток,
  с последнего приглашения поселения ≥ 6000 тиков; свободный ребёнок с телом в 8 блоках от
  игрока (не зрителя) говорит `HIDE_INVITE`;
- начало (щелчок по ребёнку в часы работы): свободные дети поселения с телами в 32 блоках от
  щёлкнутого, до четырёх; место сбора — ноги щёлкнутого; мест —
  `HidingPlaces.find(сбор, высота сбора, число детей)`; мест меньше, чем детей, — играет
  столько, сколько мест; ни одного — ребёнок говорит `HIDE_NOWHERE`, прятки не начинаются;
- отсчёт 200 тиков: сетевому игроку заголовок «Считаю… 10 … 1» раз в секунду; дети идут
  к местам, подпись снята (`setCustomNameVisible(false)`; держать снятой в `steers` — `label`
  в начале каждого решения её возвращает);
- поиск 2400 тиков: полоса сверху «Прятки: найдено k из n · м:сс»; найден — игрок в 2,5 блока
  (раз в 5 тиков) или щелчок по ребёнку: `HIDE_FOUND`, подпись видна, ребёнок идёт
  к месту сбора;
- все найдены: мать щёлкнутого ребёнка (или отец, или любой взрослый поселения с телом)
  говорит `HIDE_THANKS`, а игроку — гостинец народа (`treat`, без поля — печенье) и два
  медяка (`Coins.earn`, невлезшее — `offerOrDrop`) и строка «Гостинец от <имя>»
  (`villagepax.hide.gift`); в колонии — без гостинца, детям `markCheer`;
- время вышло: оставшиеся — `HIDE_LOST`, подпись видна, идут к месту сбора; гостинца нет;
- игрок ушёл дальше 48 блоков от места сбора или пропал — прятки кончаются, как по времени;
- конец любого рода: полоса снята, у всех детей подпись видна, дети отпущены в обычный день.

- [ ] **Step 1: Проверки — красные** (batch `hide`, `WIDE_STRUCTURE`, луг как у `fairGround`,
  дети — с телами, возраст ребёнка и родители в записи):
  - `aChildInvitesAPlayerByDay`: кукла в 5 блоках от ребёнка, 3000 → фраза `hide_invite`;
    через 100 тиков — не повторяется; ночью (14 000) — нет;
  - `noChildrenNoHiding`: деревня без детей → ни приглашения, ни пряток на щелчок по взрослому;
  - `theChildrenHideAndAreFound`: три ребёнка, щелчок → после 200 тиков каждый в 2 блоках
    от своего места, подписи сняты; кукла подходит к каждому → найдено 3 из 3, у куклы
    гостинец и +2 медяка, подписи видны;
  - `timeRunsOutAndTheyWin`: 2600 тиков без поиска → у всех детей `hide_lost`, подписи
    видны, гостинца нет;
  - `hidingEndsWithEveryoneBack`: кукла уходит на 60 блоков посреди поиска → прятки кончились,
    подписи видны, сессии нет;
  - `aColonysChildrenAreJustGlad`: колония → найдены все → гостинца нет, `cheered` у детей;
  - `aTreatComesFromThePeople`: пони → радужный кекс, норманны → печенье.
- [ ] **Step 2:** красное → **Step 3:** код → **Step 4:** зелёные → доказать поломкой
  (не возвращать подпись по времени — `timeRunsOutAndTheyWin` красная) → вернуть → коммит
  `feat: прятки с детьми`.

---

## Часть F. Слова и проверка

### Task 16: книга, README, руководство, замысел

**Файлы:**
- `sim/Guide.java` — страницы `villagepax.guide.games` (компания, кости, армрестлинг, ставки,
  щелчок с Shift по старейшине и купцу) и `villagepax.guide.hide` (прятки); строки — в оба
  словаря, не длиннее страницы (`GuideBookTest`);
- `README.md` — раздел «## Игры с жителями»;
- `docs/datapacks.md` — поле народа `treat`, фразы игр (ключи и запас), игорный стол в убранстве;
- замысел — раздел «## Сделано» с отступлениями, решёнными по ходу.

- [ ] Шаги: словари → `GuideBookTest`, `GuidePagesTest`, `LangTest` зелёные → коммит
  `docs: игры с жителями в книге, README и руководстве`.

### Task 17: лакмус и полная проверка

- [ ] **Лакмус** `anEveningAndADayInAVillage` (batch `litmus_games`, `WIDE_STRUCTURE`): деревня
  с шестью взрослыми и двумя детьми; вечер (день 3, 11 500): компания из четырёх у места,
  сама с собой хотя бы раз; кукла выигрывает в кости у первого (подставные кости)
  и проигрывает армрестлинг второму; наутро (день 4, 1 000) в 4 блоках от первого — оклик
  `rematch`, от второго — `boast`; днём ребёнок зовёт в прятки, кукла находит обоих →
  гостинец. В конце — монеты куклы = начальные + выигрыш − проигрыш + 2, на складе
  деревни — проигрыш.
- [ ] `./gradlew build` — exit 0; модульные — 0 падений; игровые — пять прогонов подряд
  зелёные; `python tools/check-generated.py` — совпадает.
- [ ] Самопроверка изменений: нет `if (culture == …)`, нет чисел решения вне констант с javadoc,
  нет забытых `System.out`/отладки.
- [ ] Память проекта — строка «Сделано» к записи о «Добавь веселья»; `main` перемоткой;
  отчёт по-русски.
