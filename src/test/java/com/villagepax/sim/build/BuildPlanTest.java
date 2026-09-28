package com.villagepax.sim.build;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * План стройки: то, что билдер в задаче 1.6 будет просто выполнять по порядку.
 * <p>
 * План принимает шаги в любом порядке и приводит их к одному — иначе порядок
 * стройки зависел бы от того, в каком порядке разбиратель схемы обошёл палитру,
 * а это деталь реализации, которая меняется от версии к версии.
 */
class BuildPlanTest {

    private static final Vec3i SIZE = new Vec3i(3, 3, 3);

    @Test
    void emptyPlanHasNothingToDo() {
        assertTrue(BuildPlan.EMPTY.isEmpty());
        assertEquals(0, BuildPlan.EMPTY.blockCount());
        assertEquals(List.of(), BuildPlan.EMPTY.steps());
        assertEquals(List.of(), BuildPlan.EMPTY.pois());
    }

    @Test
    void stepsAreOrderedRegardlessOfInputOrder() {
        BuildPlan plan = new BuildPlan(SIZE, List.of(
                BuildStep.placing(new BlockPos(0, 2, 0), BuildCategory.STRUCTURE, 0),
                BuildStep.clearing(new BlockPos(0, 1, 0)),
                BuildStep.placing(new BlockPos(0, 0, 0), BuildCategory.STRUCTURE, 0)),
                List.of());

        assertEquals(List.of(
                        BuildCategory.CLEAR, BuildCategory.STRUCTURE, BuildCategory.STRUCTURE),
                plan.steps().stream().map(BuildStep::category).toList());
        assertEquals(List.of(1, 0, 2), plan.steps().stream().map(s -> s.pos().getY()).toList());
    }

    @Test
    void samePlanBuiltTwiceFromShuffledInputIsEqual() {
        List<BuildStep> steps = List.of(
                BuildStep.placing(new BlockPos(1, 1, 1), BuildCategory.DECOR, 3),
                BuildStep.placing(new BlockPos(0, 0, 0), BuildCategory.STRUCTURE, 1),
                BuildStep.clearing(new BlockPos(2, 2, 2)));
        List<PointOfInterest> pois = List.of(
                new PointOfInterest(MarkerKind.STORAGE, new BlockPos(1, 1, 1)),
                new PointOfInterest(MarkerKind.DOOR, new BlockPos(0, 1, 2)));

        BuildPlan first = new BuildPlan(SIZE, steps, pois);
        BuildPlan second = new BuildPlan(SIZE, reversed(steps), reversed(pois));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    /** Своя перестановка: {@code List.reversed} появился в Java 21, а цель — 17. */
    private static <T> List<T> reversed(List<T> source) {
        List<T> copy = new ArrayList<>(source);
        Collections.reverse(copy);
        return copy;
    }

    @Test
    void planWithDifferentSizeIsNotEqual() {
        BuildPlan small = new BuildPlan(new Vec3i(1, 1, 1), List.of(), List.of());
        BuildPlan large = new BuildPlan(new Vec3i(2, 1, 1), List.of(), List.of());

        assertNotEquals(small, large);
    }

    @Test
    void blockCountIgnoresClearing() {
        BuildPlan plan = new BuildPlan(SIZE, List.of(
                BuildStep.clearing(new BlockPos(0, 0, 0)),
                BuildStep.clearing(new BlockPos(0, 1, 0)),
                BuildStep.placing(new BlockPos(1, 0, 0), BuildCategory.STRUCTURE, 0)),
                List.of());

        assertEquals(1, plan.blockCount(), "заявка на материалы считается по устанавливаемым блокам");
        assertEquals(3, plan.steps().size());
    }

    @Test
    void pointsOfInterestAreFilteredByKind() {
        BuildPlan plan = new BuildPlan(SIZE, List.of(), List.of(
                new PointOfInterest(MarkerKind.BED, new BlockPos(1, 0, 1)),
                new PointOfInterest(MarkerKind.BED, new BlockPos(2, 0, 1)),
                new PointOfInterest(MarkerKind.DOOR, new BlockPos(0, 0, 1))));

        assertEquals(List.of(new BlockPos(1, 0, 1), new BlockPos(2, 0, 1)),
                plan.positionsOf(MarkerKind.BED));
        assertEquals(List.of(new BlockPos(0, 0, 1)), plan.positionsOf(MarkerKind.DOOR));
        assertEquals(List.of(), plan.positionsOf(MarkerKind.WORKSTATION));
    }

    /**
     * Два шага на одну позицию — признак ошибки в разбирателе схемы: билдер
     * поставил бы там два блока подряд. Ловим сразу, а не через час стройки.
     */
    @Test
    void twoStepsOnOnePositionAreRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                new BuildPlan(SIZE, List.of(
                        BuildStep.placing(new BlockPos(1, 1, 1), BuildCategory.STRUCTURE, 0),
                        BuildStep.placing(new BlockPos(1, 1, 1), BuildCategory.DECOR, 1)),
                        List.of()));

        assertTrue(failure.getMessage().contains("1, 1, 1"), failure.getMessage());
    }

    /**
     * Грядка — единственная клетка с двумя шагами: её расчищают со всем
     * объёмом, а сеют последней. Расчистка при этом идёт первой.
     */
    @Test
    void aBedIsClearedFirstAndSownLast() {
        BlockPos bed = new BlockPos(1, 1, 1);
        BuildPlan plan = new BuildPlan(SIZE, List.of(
                BuildStep.placing(bed, BuildCategory.SOWING, 2),
                BuildStep.placing(new BlockPos(1, 0, 1), BuildCategory.STRUCTURE, 0),
                BuildStep.clearingFor(bed, 2)),
                List.of());

        assertEquals(List.of(BuildCategory.CLEAR, BuildCategory.STRUCTURE, BuildCategory.SOWING),
                plan.steps().stream().map(BuildStep::category).toList());
        assertEquals(1, plan.steps().stream().filter(step -> step.pos().equals(bed))
                .filter(BuildStep::placesBlock).count(), "посев на грядке один");
        assertEquals(2, plan.blockCount(), "расчистка под посев блока не ставит");
    }

    @Test
    void twoClearingsOnOnePositionAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPlan(SIZE, List.of(
                        BuildStep.clearing(new BlockPos(1, 1, 1)),
                        BuildStep.clearingFor(new BlockPos(1, 1, 1), 2)),
                        List.of()));
    }

    @Test
    void stepsAndPointsAreFrozen() {
        BuildPlan plan = new BuildPlan(SIZE,
                List.of(BuildStep.clearing(new BlockPos(0, 0, 0))),
                List.of(new PointOfInterest(MarkerKind.DOOR, new BlockPos(0, 0, 0))));

        assertThrows(UnsupportedOperationException.class,
                () -> plan.steps().add(BuildStep.clearing(new BlockPos(1, 1, 1))));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.pois().add(new PointOfInterest(MarkerKind.BED, new BlockPos(1, 1, 1))));
    }
}
