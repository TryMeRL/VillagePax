package com.villagepax.sim;

import com.mojang.serialization.DataResult;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import com.villagepax.sim.work.JobState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Круговой прогон сохранения как свойство, а не как один рукописный пример.
 * <p>
 * Настоящее утверждение звучит не «вот это поселение переживает NBT», а
 * «<b>любое</b> поселение переживает NBT». Разница существенная: пример
 * проверяет ту комбинацию полей, которую я догадался написать, а свойство
 * перебирает пустые и заполненные списки, отсутствующие необязательные поля,
 * все повороты схем, все уровни, крайние координаты и строки, до которых
 * рука бы не дошла.
 * <p>
 * Пример-тест в {@code SettlementPersistenceTest} остаётся: он читается как
 * документация и точно называет, что именно должно сохраняться.
 */
class SettlementRoundTripProperty {

    @Property(tries = 300)
    void anySettlementSurvivesRoundTrip(@ForAll("settlements") Settlement original) {
        Settlement restored = roundTrip(original);
        assertSameSettlement(original, restored);
    }

    @Property(tries = 300)
    void roundTripIsStable(@ForAll("settlements") Settlement original) {
        // Второй прогон не должен ничего доломать: если кодек теряет поле,
        // расхождение обычно проявляется именно на повторе.
        assertSameSettlement(roundTrip(original), roundTrip(roundTrip(original)));
    }

    @Property(tries = 200)
    void encodedFormIsDeterministic(@ForAll("settlements") Settlement original) {
        assertEquals(encode(original), encode(roundTrip(original)),
                "одно и то же поселение обязано кодироваться одинаково");
    }

    // --- прогон ---

    private static NbtElement encode(Settlement settlement) {
        DataResult<NbtElement> encoded = Settlement.CODEC.encodeStart(NbtOps.INSTANCE, settlement);
        return encoded.result().orElseThrow(() -> new AssertionError(
                "не закодировалось: " + encoded.error().map(Object::toString).orElse("?")));
    }

    private static Settlement roundTrip(Settlement original) {
        DataResult<Settlement> decoded = Settlement.CODEC.parse(NbtOps.INSTANCE, encode(original));
        return decoded.result().orElseThrow(() -> new AssertionError(
                "не раскодировалось: " + decoded.error().map(Object::toString).orElse("?")));
    }

    private static void assertSameSettlement(Settlement before, Settlement after) {
        assertEquals(before.id(), after.id(), "идентификатор");
        assertEquals(before.culture(), after.culture(), "народ");
        assertEquals(before.owner(), after.owner(), "владелец");
        assertEquals(before.name(), after.name(), "название");
        assertEquals(before.center(), after.center(), "центр");
        assertEquals(before.level(), after.level(), "уровень");
        assertEquals(before.stats(), after.stats(), "показатели");

        assertEquals(before.buildings().size(), after.buildings().size(), "число зданий");
        for (int i = 0; i < before.buildings().size(); i++) {
            Building a = before.buildings().get(i);
            Building b = after.buildings().get(i);
            assertEquals(a.id(), b.id(), "здание " + i + ": идентификатор");
            assertEquals(a.type(), b.type(), "здание " + i + ": тип");
            assertEquals(a.level(), b.level(), "здание " + i + ": уровень");
            assertEquals(a.anchor(), b.anchor(), "здание " + i + ": якорь");
            assertEquals(a.rotation(), b.rotation(), "здание " + i + ": поворот");
            assertEquals(a.progress(), b.progress(), "здание " + i + ": состояние стройки");
            assertEquals(a.workers(), b.workers(), "здание " + i + ": работники");
            assertEquals(a.nextStep(), b.nextStep(), "здание " + i + ": шаг стройки");
            assertEquals(a.stock().contents(), b.stock().contents(), "здание " + i + ": запас площадки");
        }

        assertEquals(before.citizens().size(), after.citizens().size(), "число жителей");
        for (int i = 0; i < before.citizens().size(); i++) {
            Citizen a = before.citizens().get(i);
            Citizen b = after.citizens().get(i);
            assertEquals(a.id(), b.id(), "житель " + i + ": идентификатор");
            assertEquals(a.firstName(), b.firstName(), "житель " + i + ": имя");
            assertEquals(a.lastName(), b.lastName(), "житель " + i + ": фамилия");
            assertEquals(a.culture(), b.culture(), "житель " + i + ": народ");
            assertEquals(a.gender(), b.gender(), "житель " + i + ": пол");
            // Вся жизнь целиком: прожитые дни, супруг и родители.
            // Поле у них одно (запись Life), и терять его целиком
            // проще всего — именно так теряются вложенные записи
            // с optionalFieldOf.
            assertEquals(a.life(), b.life(), "житель " + i + ": жизнь");
            assertEquals(a.profession(), b.profession(), "житель " + i + ": профессия");
            assertEquals(a.happiness(), b.happiness(), "житель " + i + ": счастье");
            assertEquals(a.saturation(), b.saturation(), "житель " + i + ": сытость");
            assertEquals(a.home(), b.home(), "житель " + i + ": дом");
            assertEquals(a.workplace(), b.workplace(), "житель " + i + ": работа");
            assertEquals(a.position(), b.position(), "житель " + i + ": позиция");
            assertEquals(a.health(), b.health(), 0.0f, "житель " + i + ": здоровье");
            assertEquals(a.jobState(), b.jobState(), "житель " + i + ": состояние работы");
        }
    }

    // --- генераторы ---

    @Provide
    Arbitrary<Settlement> settlements() {
        return Combinators.combine(
                        uuids(),
                        identifiers(),
                        owners(),
                        names(),
                        positions(),
                        Arbitraries.of(SettlementLevel.values()),
                        stats(),
                        buildings().list().ofMaxSize(4))
                .as(SettlementDraft::new)
                .flatMap(draft -> citizens().list().ofMaxSize(6)
                        .map(cs -> new Settlement(draft.id(), draft.culture(), draft.owner(), draft.name(),
                                draft.center(), draft.level(), draft.stats(), draft.buildings(), cs)));
    }

    /** Промежуточная запись: у {@code Combinators} предел в восемь значений за раз. */
    private record SettlementDraft(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                                   SettlementLevel level, SettlementStats stats, List<Building> buildings) {
    }

    private Arbitrary<SettlementStats> stats() {
        return Combinators.combine(
                        Arbitraries.integers().between(0, 5_000),
                        Arbitraries.integers().between(0, SettlementStats.MAX_HAPPINESS),
                        Arbitraries.integers().between(0, SettlementStats.MAX_HAPPINESS),
                        Arbitraries.integers().between(0, 100_000))
                .as(SettlementStats::new);
    }

    private Arbitrary<Building> buildings() {
        return Combinators.combine(
                        uuids(),
                        identifiers(),
                        Arbitraries.integers().between(1, 5),
                        positions(),
                        Arbitraries.of(BlockRotation.values()),
                        Arbitraries.of(BuildProgress.values()),
                        uuids().list().ofMaxSize(3),
                        Arbitraries.integers().between(0, 4_000))
                .as(BuildingDraft::new)
                .flatMap(draft -> tallies().map(stock -> new Building(draft.id(), draft.type(),
                        draft.level(), draft.anchor(), draft.rotation(), draft.progress(),
                        draft.workers(), draft.nextStep(), stock)));
    }

    /** Ещё одна промежуточная запись: предел {@code Combinators} — восемь значений. */
    private record BuildingDraft(UUID id, Identifier type, int level, BlockPos anchor,
                                 BlockRotation rotation, BuildProgress progress, List<UUID> workers,
                                 int nextStep) {
    }

    /** Запас площадки: значения строго положительны, нули на загрузке отбрасываются. */
    private Arbitrary<ItemTally> tallies() {
        return Arbitraries.maps(identifiers(), Arbitraries.integers().between(1, 20_000))
                .ofMaxSize(5)
                .map(ItemTally::new);
    }

    /**
     * Состояние работы во всех своих видах, включая самый коварный —
     * груз в руках без здания, который надо вернуть на склад.
     */
    private Arbitrary<JobState> jobStates() {
        return Arbitraries.oneOf(
                Arbitraries.just(JobState.IDLE),
                Combinators.combine(uuids(), Arbitraries.of(
                                JobState.Phase.TO_STORAGE, JobState.Phase.TO_SITE, JobState.Phase.WORKING))
                        .as(JobState::startAt),
                Combinators.combine(uuids(), identifiers(), Arbitraries.integers().between(1, 64))
                        .as((building, item, count) -> JobState
                                .startAt(building, JobState.Phase.TO_SITE)
                                .carrying(item, count)),
                Combinators.combine(uuids(), identifiers(), Arbitraries.integers().between(1, 64))
                        .as((building, item, count) -> JobState
                                .startAt(building, JobState.Phase.TO_SITE)
                                .carrying(item, count)
                                .withPhase(JobState.Phase.IDLE)));
    }

    private Arbitrary<Citizen> citizens() {
        return Combinators.combine(
                        uuids(),
                        names(),
                        Arbitraries.strings().alpha().ofMaxLength(12),
                        identifiers(),
                        Arbitraries.of(Gender.values()),
                        lives(),
                        identifiers().optional(),
                        Arbitraries.integers().between(0, 100))
                .as(CitizenDraft::new)
                .flatMap(draft -> Combinators.combine(
                                Arbitraries.integers().between(0, 40),
                                uuids().optional(),
                                uuids().optional(),
                                precisePositions().optional(),
                                Arbitraries.floats().between(0.0f, Citizen.MAX_HEALTH),
                                jobStates())
                        .as((saturation, home, work, pos, health, job) -> new Citizen(
                                draft.id(), draft.first(), draft.last(), draft.culture(), draft.gender(),
                                draft.life(), draft.profession(), draft.happiness(), saturation,
                                home, work, pos, health, job)));
    }

    private record CitizenDraft(UUID id, String first, String last, Identifier culture, Gender gender,
                                Citizen.Life life, Optional<Identifier> profession, int happiness) {
    }

    /**
     * Жизнь наугад: и «возраста не помнит», и обычный возраст, и родня.
     * <p>
     * Отрицательное значение в выборке нарочно: {@link Citizen.Life#UNAGED}
     * — не крайний случай, а состояние всех, кто жил в мире до появления
     * возраста, и круг обязан переносить его наравне с остальными.
     */
    private Arbitrary<Citizen.Life> lives() {
        return Combinators.combine(
                        Arbitraries.integers().between(Citizen.Life.UNAGED, 400),
                        uuids().optional(),
                        uuids().list().ofMaxSize(2))
                .as(Citizen.Life::new);
    }

    private Arbitrary<UUID> uuids() {
        return Combinators.combine(Arbitraries.longs(), Arbitraries.longs()).as(UUID::new);
    }

    /**
     * Идентификаторы строятся из допустимых символов намеренно: смысл свойства
     * в том, что кодек не теряет корректные данные, а не в том, что он отвергает
     * заведомо неправильные — на это есть отдельные примеры-тесты.
     */
    private Arbitrary<Identifier> identifiers() {
        Arbitrary<String> namespace = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> path = Arbitraries.strings().withCharRange('a', 'z').withChars('/', '_')
                .ofMinLength(1).ofMaxLength(20);
        return Combinators.combine(namespace, path).as(Identifier::new);
    }

    private Arbitrary<Owner> owners() {
        return Arbitraries.oneOf(
                Arbitraries.just(Owner.AUTONOMOUS),
                uuids().map(Owner::of));
    }

    /** Имена с кириллицей, латиницей и пробелами: NBT обязан переносить всё. */
    private Arbitrary<String> names() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('А', 'я')
                .withChars(' ', '-')
                .ofMinLength(1).ofMaxLength(24);
    }

    private Arbitrary<BlockPos> positions() {
        return Combinators.combine(
                        Arbitraries.integers().between(-30_000_000, 30_000_000),
                        Arbitraries.integers().between(-64, 320),
                        Arbitraries.integers().between(-30_000_000, 30_000_000))
                .as(BlockPos::new);
    }

    private Arbitrary<Vec3d> precisePositions() {
        return Combinators.combine(
                        Arbitraries.doubles().between(-30_000_000, 30_000_000),
                        Arbitraries.doubles().between(-64, 320),
                        Arbitraries.doubles().between(-30_000_000, 30_000_000))
                .as(Vec3d::new);
    }

    @Property(tries = 50)
    void generatorProducesUsableSettlements(@ForAll("settlements") Settlement settlement) {
        assertTrue(settlement.population() >= 0);
        assertTrue(settlement.claims(settlement.center()), "поселение всегда владеет своим центром");
    }
}
