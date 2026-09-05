package com.villagepax.sim;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureKind;
import com.villagepax.core.culture.NamePools;
import com.villagepax.core.culture.SpawnSettings;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
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
