package com.villagepax.sim;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureKind;
import com.villagepax.core.culture.NamePools;
import com.villagepax.core.culture.SpawnSettings;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Правила основания колонии. Проверяются без запущенной игры — именно для
 * этого они и отделены от предмета-чертежа.
 */
class FoundingTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");
    private static final BlockPos SOMEWHERE = new BlockPos(500, 70, 500);

    private static Culture norman() {
        return new Culture(
                "villagepax.culture.norman",
                CultureKind.HISTORICAL,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of("Rollo"), List.of("Adelise"), List.of("Бовуар", "Рокмон")),
                List.of(new Identifier("villagepax", "norman/town_hall")),
                List.of(),
                Map.of());
    }

    private static Random seeded() {
        return new Random(42);
    }

    @Test
    void foundsColonyOnEmptyMap() {
        SettlementManager manager = new SettlementManager();

        FoundingOutcome outcome = Founding.attempt(
                manager, UUID.randomUUID(), NORMAN, norman(), SOMEWHERE, seeded());

        FoundingOutcome.Founded founded = assertInstanceOf(FoundingOutcome.Founded.class, outcome);
        assertEquals(NORMAN, founded.settlement().culture());
        assertEquals(SettlementLevel.HAMLET, founded.settlement().level());
        assertTrue(norman().namePools().settlement().contains(founded.settlement().name()),
                "имя берётся из списка культуры");
    }

    @Test
    void refusesUnknownCulture() {
        SettlementManager manager = new SettlementManager();

        FoundingOutcome outcome = Founding.attempt(
                manager, UUID.randomUUID(), NORMAN, null, SOMEWHERE, seeded());

        FoundingOutcome.Refused refused = assertInstanceOf(FoundingOutcome.Refused.class, outcome);
        assertEquals(Founding.KEY_UNKNOWN_CULTURE, refused.translationKey());
    }

    @Test
    void refusesSecondColonyForSamePlayer() {
        SettlementManager manager = new SettlementManager();
        UUID player = UUID.randomUUID();

        manager.add(Settlement.found(NORMAN, Owner.of(player), "Бовуар", new BlockPos(0, 64, 0)));

        FoundingOutcome outcome = Founding.attempt(
                manager, player, NORMAN, norman(), SOMEWHERE, seeded());

        FoundingOutcome.Refused refused = assertInstanceOf(FoundingOutcome.Refused.class, outcome);
        assertEquals(Founding.KEY_ALREADY_OWNER, refused.translationKey());
        assertEquals("Бовуар", refused.arguments()[0], "в сообщении названа существующая колония");
    }

    @Test
    void refusesWhenBordersWouldOverlap() {
        SettlementManager manager = new SettlementManager();
        manager.add(Settlement.found(NORMAN, Owner.AUTONOMOUS, "Рокмон", new BlockPos(510, 70, 500)));

        FoundingOutcome outcome = Founding.attempt(
                manager, UUID.randomUUID(), NORMAN, norman(), SOMEWHERE, seeded());

        FoundingOutcome.Refused refused = assertInstanceOf(FoundingOutcome.Refused.class, outcome);
        assertEquals(Founding.KEY_TOO_CLOSE, refused.translationKey());
        assertEquals("Рокмон", refused.arguments()[0], "в сообщении назван мешающий сосед");
    }

    @Test
    void allowsColonyNextToDistantNeighbour() {
        SettlementManager manager = new SettlementManager();
        manager.add(Settlement.found(NORMAN, Owner.AUTONOMOUS, "Далеко", new BlockPos(5000, 70, 5000)));

        FoundingOutcome outcome = Founding.attempt(
                manager, UUID.randomUUID(), NORMAN, norman(), SOMEWHERE, seeded());

        assertTrue(outcome.isSuccess(), "далёкий сосед не мешает");
    }

    @Test
    void anotherPlayerMayFoundElsewhere() {
        SettlementManager manager = new SettlementManager();
        UUID first = UUID.randomUUID();
        manager.add(Settlement.found(NORMAN, Owner.of(first), "Бовуар", new BlockPos(0, 64, 0)));

        FoundingOutcome outcome = Founding.attempt(
                manager, UUID.randomUUID(), NORMAN, norman(), SOMEWHERE, seeded());

        assertTrue(outcome.isSuccess(), "ограничение одна колония — на игрока, а не на мир");
    }

    @Test
    void firstBuilderGetsNameFromCultureAndBuilderProfession() {
        Culture culture = norman();

        for (long seed = 0; seed < 30; seed++) {
            Citizen builder = Founding.firstBuilder(NORMAN, culture, new Random(seed));

            assertEquals(Optional.of(Founding.PROFESSION_BUILDER), builder.profession(),
                    "первый житель обязан быть строителем: без него не встанет ни одно здание");
            assertEquals(NORMAN, builder.culture());

            List<String> pool = builder.gender() == Gender.MALE
                    ? culture.namePools().male()
                    : culture.namePools().female();
            assertTrue(pool.contains(builder.firstName()),
                    "имя " + builder.firstName() + " не из списка народа, сид " + seed);
        }
    }

    /** Недописанный датапак не должен лишать игрока строителя. */
    @Test
    void firstBuilderSurvivesEmptyNamePools() {
        Culture nameless = new Culture("villagepax.culture.void", CultureKind.FANTASY,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of(), List.of(), List.of()),
                List.of(), List.of(), Map.of());

        Citizen builder = Founding.firstBuilder(NORMAN, nameless, new Random(1));

        assertEquals(Founding.FALLBACK_BUILDER_NAME, builder.firstName());
        assertEquals(Optional.of(Founding.PROFESSION_BUILDER), builder.profession());
    }

    /** Один пустой список не должен мешать: имя берётся из другого. */
    @Test
    void firstBuilderFallsBackToTheOtherPool() {
        Culture menOnly = new Culture("villagepax.culture.men", CultureKind.HISTORICAL,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of("Rollo"), List.of(), List.of()),
                List.of(), List.of(), Map.of());

        for (long seed = 0; seed < 10; seed++) {
            assertEquals("Rollo", Founding.firstBuilder(NORMAN, menOnly, new Random(seed)).firstName());
        }
    }

    /**
     * Жалоба заказчика: «пятнадцать одинаковых имён». Пока в именнике
     * есть свободное имя, второго Рольфа в поселении не будет.
     */
    @Test
    void newcomerTakesAFreeNameWhileThePoolHasOne() {
        Culture culture = new Culture("villagepax.culture.norman", CultureKind.HISTORICAL,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of("Rollo", "Robert", "Raoul", "Roger"),
                        List.of("Emma", "Alix", "Ide", "Douce"), List.of()),
                List.of(), List.of(), Map.of());

        for (long seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            List<Citizen> village = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < 8; i++) {
                Citizen next = Founding.newCitizen(NORMAN, culture, random, village);
                assertTrue(seen.add(next.firstName()),
                        "имя " + next.firstName() + " повторилось при свободных, сид " + seed);
                village.add(next);
            }
        }
    }

    /**
     * Именник кончился — повторяется самое редкое имя, а не случайное:
     * двух Рольфов терпеть можно, пятерых — нет.
     */
    @Test
    void whenThePoolRunsOutTheRarestNameRepeats() {
        Culture culture = new Culture("villagepax.culture.norman", CultureKind.HISTORICAL,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of("Rollo", "Robert"), List.of("Emma", "Alix"), List.of()),
                List.of(), List.of(), Map.of());

        for (long seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            List<Citizen> village = new java.util.ArrayList<>();
            for (int i = 0; i < 12; i++) {
                village.add(Founding.newCitizen(NORMAN, culture, random, village));
            }
            for (String name : List.of("Rollo", "Robert", "Emma", "Alix")) {
                long times = village.stream().filter(one -> one.firstName().equals(name)).count();
                assertEquals(3, times, name + " выпало " + times + " раз из 12, сид " + seed);
            }
        }
    }

    /**
     * Мужчин и женщин поровну, насколько это в силах прибывающих:
     * свадьбы — только между ними, и колония из одних мужчин не растёт.
     */
    @Test
    void newcomersEvenOutMenAndWomen() {
        Culture culture = norman();
        for (long seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            List<Citizen> village = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                village.add(Founding.newCitizen(NORMAN, culture, random, village));
                long men = village.stream().filter(one -> one.gender() == Gender.MALE).count();
                long women = village.size() - men;
                assertTrue(Math.abs(men - women) <= 1,
                        men + " мужчин на " + women + " женщин, сид " + seed);
            }
        }
    }

    /** Две «Кан» на карте путают компас: имя деревни не повторяется, пока есть другое. */
    @Test
    void settlementNameAvoidsNamesAlreadyOnTheMap() {
        Culture culture = norman();
        for (long seed = 0; seed < 20; seed++) {
            String first = Founding.pickName(culture, new Random(seed), List.of());
            String second = Founding.pickName(culture, new Random(seed), List.of(first));
            assertTrue(!first.equals(second), "второе имя повторило первое, сид " + seed);
        }
    }

    /**
     * Мир, начатый до большого именника: двойники уже живут. На рассвете
     * второй и следующий получают свободное имя своего пола, первый
     * остаётся как был, отчество не трогается.
     */
    @Test
    void twinsInAnOldWorldGetDistinctNames() {
        Culture culture = new Culture("villagepax.culture.norman", CultureKind.HISTORICAL,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of("Rollo", "Robert", "Raoul"), List.of("Emma", "Alix"), List.of()),
                List.of(), List.of(), Map.of());
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", SOMEWHERE);
        Citizen first = Citizen.newborn("Rollo", "", NORMAN, Gender.MALE);
        Citizen twin = Citizen.newborn("Rollo", "", NORMAN, Gender.MALE);
        Citizen son = Citizen.newborn("Rollo", "fils de Robert", NORMAN, Gender.MALE);
        Citizen sister = Citizen.newborn("Emma", "", NORMAN, Gender.FEMALE);
        Citizen another = Citizen.newborn("Emma", "", NORMAN, Gender.FEMALE);
        for (Citizen one : List.of(first, twin, son, sister, another)) {
            village.addCitizen(one);
        }

        List<Founding.Renamed> renamed = Founding.tellTwinsApart(village, culture, seeded());

        assertEquals(2, renamed.size(), "переименованы ровно двойники: " + renamed);
        assertEquals("Rollo", village.citizen(first.id()).orElseThrow().firstName(), "первый остаётся");
        assertEquals("Rollo fils de Robert", village.citizen(son.id()).orElseThrow().fullName(),
                "с отчеством он уже не двойник");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Citizen one : village.citizens()) {
            assertTrue(names.add(one.fullName()), "двойник остался: " + one.fullName());
        }
        assertTrue(culture.namePools().male().contains(village.citizen(twin.id()).orElseThrow().firstName()));
        assertTrue(culture.namePools().female().contains(village.citizen(another.id()).orElseThrow().firstName()));
        assertTrue(Founding.tellTwinsApart(village, culture, seeded()).isEmpty(), "второй раз — нечего");
    }

    /**
     * Две деревни с одним названием в старом мире: вторая получает свободное.
     * Колонию игрока не переименовывают никогда — её назвал он.
     */
    @Test
    void aVillageNamedLikeAnotherGetsAFreeName() {
        Culture culture = new Culture("villagepax.culture.norman", CultureKind.HISTORICAL,
                new SpawnSettings("#minecraft:is_forest", 10, 48),
                new NamePools(List.of("Rollo"), List.of("Emma"), List.of("Бовуар", "Рокмон", "Кан")),
                List.of(), List.of(), Map.of());
        SettlementManager manager = new SettlementManager();
        Settlement first = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", SOMEWHERE);
        Settlement second = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", new BlockPos(5000, 70, 5000));
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Рокмон",
                new BlockPos(-5000, 70, -5000));
        Settlement colonyTwin = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Рокмон",
                new BlockPos(-9000, 70, -9000));
        for (Settlement one : List.of(first, second, colony, colonyTwin)) {
            manager.add(one);
        }

        assertTrue(Founding.renameIfTwin(manager, first, culture, seeded()).isEmpty(),
                "первую по списку не трогают");
        assertEquals(Optional.of("Кан"), Founding.renameIfTwin(manager, second, culture, seeded()));
        assertEquals("Кан", second.name());
        assertTrue(Founding.renameIfTwin(manager, colonyTwin, culture, seeded()).isEmpty(),
                "колонию игрока не переименовывают");
    }

    @Test
    void fallsBackWhenCultureHasNoSettlementNames() {
        Culture nameless = new Culture(
                "villagepax.culture.nameless",
                CultureKind.FANTASY,
                new SpawnSettings("#minecraft:is_hill", 10, 48),
                new NamePools(List.of(), List.of(), List.of()),
                List.of(),
                List.of(),
                Map.of());

        assertEquals(Founding.FALLBACK_NAME, Founding.pickName(nameless, seeded()),
                "недописанный датапак не должен ронять основание колонии");
    }
}
