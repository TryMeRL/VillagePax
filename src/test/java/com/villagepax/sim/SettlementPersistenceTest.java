package com.villagepax.sim;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Потеря состояния — самый болезненный баг в модах такого класса: всё работает
 * до перезахода в мир, а потом колонии на двести часов просто нет. Поэтому
 * круговой прогон через NBT проверяется раньше, чем что-либо начнёт это
 * состояние менять.
 */
class SettlementPersistenceTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Settlement sample() {
        Settlement settlement = new Settlement(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                NORMAN,
                Owner.of(PLAYER),
                "Бовуар",
                new BlockPos(120, 68, -340),
                SettlementLevel.TOWN,
                new SettlementStats(48, 82, 61, 130),
                List.of(),
                List.of());

        Building townHall = new Building(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Identifier("villagepax", "norman/town_hall"),
                3,
                new BlockPos(120, 68, -340),
                BlockRotation.CLOCKWISE_90,
                BuildProgress.DONE,
                List.of());

        Building lumberjack = new Building(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Identifier("villagepax", "norman/lumberjack"),
                1,
                new BlockPos(134, 68, -330),
                BlockRotation.NONE,
                BuildProgress.DAMAGED,
                List.of());

        Citizen builder = new Citizen(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                "Rollo", "de Beauvoir", NORMAN, Gender.MALE, 480_000L,
                Optional.of(new Identifier("villagepax", "builder")),
                77, 14,
                Optional.of(townHall.id()),
                Optional.of(lumberjack.id()),
                Optional.of(new Vec3d(134.5, 68.0, -330.5)),
                7.5f);

        Citizen child = Citizen.newborn("Aveline", "de Beauvoir", NORMAN, Gender.FEMALE);

        lumberjack.assign(builder.id());
        settlement.addBuilding(townHall);
        settlement.addBuilding(lumberjack);
        settlement.addCitizen(builder);
        settlement.addCitizen(child);
        return settlement;
    }

    private static Settlement roundTrip(Settlement original) {
        DataResult<NbtElement> encoded = Settlement.CODEC.encodeStart(NbtOps.INSTANCE, original);
        NbtElement nbt = encoded.result().orElseThrow(() ->
                new AssertionError("не закодировалось: " + encoded.error().map(Object::toString).orElse("?")));

        DataResult<Settlement> decoded = Settlement.CODEC.parse(NbtOps.INSTANCE, nbt);
        return decoded.result().orElseThrow(() ->
                new AssertionError("не раскодировалось: " + decoded.error().map(Object::toString).orElse("?")));
    }

    @Test
    void settlementSurvivesRoundTrip() {
        Settlement before = sample();
        Settlement after = roundTrip(before);

        assertEquals(before.id(), after.id());
        assertEquals(before.culture(), after.culture());
        assertEquals(before.name(), after.name(), "имя с кириллицей должно пережить NBT");
        assertEquals(before.center(), after.center());
        assertEquals(before.level(), after.level());
        assertEquals(before.stats(), after.stats());
        assertEquals(Optional.of(PLAYER), after.owner().player());
        assertFalse(after.owner().isAutonomous());
    }

    @Test
    void buildingsSurviveRoundTrip() {
        Settlement after = roundTrip(sample());

        assertEquals(2, after.buildings().size());

        Building townHall = after.buildings().get(0);
        assertEquals(3, townHall.level());
        assertEquals(BlockRotation.CLOCKWISE_90, townHall.rotation(), "поворот схемы обязан сохраняться");
        assertEquals(BuildProgress.DONE, townHall.progress());

        Building lumberjack = after.buildings().get(1);
        assertEquals(BuildProgress.DAMAGED, lumberjack.progress(),
                "повреждённое здание чинится, а не исчезает");
        assertEquals(1, lumberjack.workers().size(), "назначенный работник должен сохраниться");
    }

    @Test
    void citizensSurviveRoundTripIncludingAssignments() {
        Settlement after = roundTrip(sample());

        assertEquals(2, after.citizens().size());

        Citizen builder = after.citizens().get(0);
        assertEquals("Rollo de Beauvoir", builder.fullName());
        assertEquals(Gender.MALE, builder.gender());
        assertEquals(480_000L, builder.ageTicks(), "возраст нужен для старения и смены поколений");
        assertEquals(Optional.of(new Identifier("villagepax", "builder")), builder.profession());
        assertEquals(77, builder.happiness());
        assertTrue(builder.home().isPresent());
        assertTrue(builder.workplace().isPresent());

        assertEquals(Optional.of(new Vec3d(134.5, 68.0, -330.5)), builder.position(),
                "позиция нужна, чтобы житель возродился там же, где его оставили");
        assertEquals(7.5f, builder.health(), 0.001f,
                "раненый житель не должен исцеляться от выгрузки чанка");

        Citizen child = after.citizens().get(1);
        assertTrue(child.isUnemployed(), "у новорождённого нет работы");
        assertTrue(child.isHomeless());
        assertTrue(child.position().isEmpty(), "житель без позиции появится у ратуши");
    }

    @Test
    void autonomousSettlementRoundTrips() {
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Рокмон", new BlockPos(0, 64, 0));
        Settlement after = roundTrip(village);

        assertTrue(after.owner().isAutonomous(), "деревня народа не принадлежит игроку");
        assertEquals(SettlementLevel.HAMLET, after.level());
        assertEquals(SettlementStats.INITIAL, after.stats());
        assertEquals(0, after.population());
    }

    /**
     * Нужды жителя и суточный счётчик обязаны переживать перезаход в мир.
     * Иначе игрок выходит из игры, возвращается — и голодный житель бодр,
     * а колония заново проживает вчерашний день.
     */
    @Test
    void needsAndDayCounterSurviveRoundTrip() {
        Settlement colony = new Settlement(UUID.randomUUID(), NORMAN, Owner.of(UUID.randomUUID()),
                "Рокмон", new BlockPos(10, 64, 10), SettlementLevel.HAMLET,
                SettlementStats.INITIAL, List.of(), List.of(), 42L);

        Citizen hungry = Citizen.newborn("Aubert", "", NORMAN, Gender.MALE);
        hungry.setSaturation(3);
        hungry.setBed(new BlockPos(12, 65, 14));
        hungry.addDiscontent();
        hungry.addDiscontent();
        colony.addCitizen(hungry);

        Settlement restored = roundTrip(colony);
        Citizen back = restored.citizens().get(0);

        assertEquals(42L, restored.lastDay(), "последний посчитанный день");
        assertTrue(restored.hasSeenADay());
        assertEquals(3, back.saturation(), "сытость");
        assertEquals(2, back.discontent(), "дни недовольства");
        assertEquals(Optional.of(new BlockPos(12, 65, 14)), back.bed(), "место для сна");
        assertFalse(back.isHomeless());
    }

    /** У новой колонии суточные нужды ещё ни разу не считались. */
    @Test
    void freshSettlementHasNotSeenADay() {
        Settlement colony = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Новь", BlockPos.ORIGIN);

        assertFalse(colony.hasSeenADay());
        assertFalse(roundTrip(colony).hasSeenADay(), "и это должно пережить сохранение");
    }

    @Test
    void claimFollowsLevel() {
        Settlement hamlet = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Хутор", new BlockPos(0, 64, 0));

        assertTrue(hamlet.claims(new ChunkPos(2, 0)), "радиус хутора 2 чанка");
        assertFalse(hamlet.claims(new ChunkPos(3, 0)));

        hamlet.setLevel(SettlementLevel.CAPITAL);
        assertTrue(hamlet.claims(new ChunkPos(6, 0)), "у столицы границы шире");
    }

    @Test
    void overlappingSettlementsAreDetected() {
        Settlement first = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Первое", new BlockPos(0, 64, 0));
        Settlement near = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Рядом", new BlockPos(48, 64, 0));
        Settlement far = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Далеко", new BlockPos(400, 64, 0));

        assertTrue(first.overlaps(near), "границы пересекаются, это повод для спора");
        assertFalse(first.overlaps(far));
    }

    @Test
    void populationIsCappedByLevel() {
        Settlement hamlet = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Хутор", new BlockPos(0, 64, 0));

        for (int i = 0; i < SettlementLevel.HAMLET.maxCitizens(); i++) {
            assertTrue(hamlet.hasRoomForCitizen(), "место есть, жителей " + i);
            hamlet.addCitizen(Citizen.newborn("Имя" + i, "", NORMAN, Gender.MALE));
        }
        assertFalse(hamlet.hasRoomForCitizen(), "хутор заполнен, дальше нужен апгрейд");
    }
}
