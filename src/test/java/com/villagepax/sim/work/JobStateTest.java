package com.villagepax.sim.work;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Состояние работы живёт в данных, потому что тело жителя исчезает вместе
 * с чанком. Всё, что здесь проверяется, сводится к одному: курьер с полными
 * руками после перезахода в мир доносит груз, а не начинает путь заново.
 */
class JobStateTest {

    private static final UUID SITE = UUID.randomUUID();
    private static final Identifier LOG = new Identifier("minecraft", "oak_log");

    @Test
    void idleStateCarriesNothingAndKnowsNoBuilding() {
        assertTrue(JobState.IDLE.isIdle());
        assertFalse(JobState.IDLE.isCarrying());
        assertEquals(Optional.empty(), JobState.IDLE.building());
        assertFalse(JobState.IDLE.hasStrandedLoad());
    }

    @Test
    void workRequiresABuilding() {
        // Фаза без здания — работа над ничем; такое состояние не должно
        // существовать вовсе, иначе цель на сущности пойдёт в никуда.
        assertThrows(IllegalArgumentException.class, () ->
                new JobState(JobState.Phase.TO_SITE, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new JobState(JobState.Phase.WORKING, Optional.empty(), Optional.empty()));
    }

    @Test
    void loadOfNothingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JobState.Load(LOG, 0));
        assertThrows(IllegalArgumentException.class, () -> new JobState.Load(LOG, -3));
    }

    @Test
    void courierPicksUpAndDelivers() {
        JobState fetching = JobState.startAt(SITE, JobState.Phase.TO_STORAGE);
        assertFalse(fetching.isCarrying());

        JobState loaded = fetching.carrying(LOG, 32).withPhase(JobState.Phase.TO_SITE);
        assertTrue(loaded.isCarrying());
        assertEquals(32, loaded.carried().orElseThrow().count());
        assertEquals(LOG, loaded.carried().orElseThrow().item());
        assertEquals(Optional.of(SITE), loaded.building());

        JobState delivered = loaded.emptyHanded();
        assertFalse(delivered.isCarrying());
        assertEquals(JobState.Phase.TO_SITE, delivered.phase());
    }

    /**
     * Самое коварное место: задание отменилось, а груз в руках остался.
     * Обнулять состояние целиком нельзя — материалы исчезли бы из мира.
     */
    @Test
    void cancelledWorkLeavesTheLoadToReturn() {
        JobState loaded = JobState.startAt(SITE, JobState.Phase.TO_SITE).carrying(LOG, 12);

        JobState cancelled = loaded.withPhase(JobState.Phase.IDLE);

        assertTrue(cancelled.isIdle());
        assertEquals(Optional.empty(), cancelled.building(), "привязка к зданию снимается");
        assertTrue(cancelled.isCarrying(), "а груз остаётся");
        assertTrue(cancelled.hasStrandedLoad(), "и это повод вернуть его на склад");
    }

    @Test
    void emptyHandedWorkerGoingIdleEqualsTheIdleState() {
        JobState working = JobState.startAt(SITE, JobState.Phase.WORKING);

        assertEquals(JobState.IDLE, working.withPhase(JobState.Phase.IDLE));
    }

    @Test
    void travellingPhasesAreTheOnesWithADestination() {
        assertTrue(JobState.Phase.TO_STORAGE.isTravelling());
        assertTrue(JobState.Phase.TO_SITE.isTravelling());
        assertFalse(JobState.Phase.WORKING.isTravelling());
        assertFalse(JobState.Phase.IDLE.isTravelling());
    }

    @Test
    void survivesRoundTripLoadedAndEmpty() {
        JobState loaded = JobState.startAt(SITE, JobState.Phase.TO_SITE).carrying(LOG, 64);

        assertEquals(loaded, roundTrip(loaded));
        assertEquals(JobState.IDLE, roundTrip(JobState.IDLE));
        assertEquals(JobState.startAt(SITE, JobState.Phase.WORKING),
                roundTrip(JobState.startAt(SITE, JobState.Phase.WORKING)));
    }

    @Test
    void strandedLoadSurvivesRoundTrip() {
        JobState stranded = JobState.startAt(SITE, JobState.Phase.TO_SITE)
                .carrying(LOG, 5)
                .withPhase(JobState.Phase.IDLE);

        JobState restored = roundTrip(stranded);

        assertTrue(restored.hasStrandedLoad(), "иначе груз пропал бы при перезаходе в мир");
        assertEquals(5, restored.carried().orElseThrow().count());
    }

    /**
     * Нечитаемая фаза не должна ронять жителя целиком.
     * <p>
     * {@code optionalFieldOf} в DFU глотает ошибку вложенного кодека и молча
     * подставляет значение по умолчанию — это стоит знать. Здесь оно как раз
     * то, что нужно: житель заново решает, чем заняться, а груз лежит
     * отдельным полем, уцелеет и вернётся на склад. В файлах культур такая
     * ловушка была бы вредна, и там поля объявлены обязательными.
     */
    @Test
    void unreadablePhaseDegradesToIdleWithoutLosingTheLoad() {
        NbtCompound encoded = (NbtCompound) JobState.CODEC
                .encodeStart(NbtOps.INSTANCE,
                        JobState.startAt(SITE, JobState.Phase.TO_SITE).carrying(LOG, 7))
                .result().orElseThrow();
        encoded.putString("phase", "гуляет");

        JobState restored = JobState.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();

        assertTrue(restored.isIdle());
        assertTrue(restored.isCarrying(), "груз обязан уцелеть: он в отдельном поле");
        assertEquals(7, restored.carried().orElseThrow().count());
        assertTrue(restored.hasStrandedLoad(), "и его есть кому вернуть на склад");
    }

    /** В простое привязка к зданию снимается сама: у состояния один вид. */
    @Test
    void idlePhaseAlwaysForgetsTheBuilding() {
        JobState odd = new JobState(JobState.Phase.IDLE, Optional.of(SITE),
                Optional.of(new JobState.Load(LOG, 2)));

        assertEquals(Optional.empty(), odd.building());
        assertTrue(odd.hasStrandedLoad());
    }

    private static JobState roundTrip(JobState state) {
        NbtElement encoded = JobState.CODEC.encodeStart(NbtOps.INSTANCE, state).result().orElseThrow();
        DataResult<JobState> decoded = JobState.CODEC.parse(NbtOps.INSTANCE, encoded);
        return decoded.result().orElseThrow(() -> new AssertionError(
                "состояние работы не раскодировалось: "
                        + decoded.error().map(Object::toString).orElse("?")));
    }
}
