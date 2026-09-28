package com.villagepax.sim.build;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Приёмка задачи 1.5 звучит так: «список блоков детерминирован и одинаков
 * между запусками». Это утверждение о <b>любой</b> схеме, а не об одной
 * написанной от руки, поэтому проверяется свойством.
 * <p>
 * Разница не косметическая. Пример поймал бы только ту расстановку блоков,
 * которую я догадался придумать; свойство перебирает пустые схемы, схемы
 * из одного блока, схемы, где все блоки в одном столбце, и все смеси
 * категорий — то есть ровно те случаи, на которых порядок и перестаёт
 * быть полным.
 */
class BuildOrderProperty {

    private static final Vec3i SIZE = new Vec3i(8, 8, 8);

    /** Главное свойство: порядок не зависит от того, как схему обошли при разборе. */
    @Property(tries = 300)
    void planIsIndependentOfInputOrder(@ForAll("stepLists") List<BuildStep> steps,
                                      @ForAll long shuffleSeed) {
        List<BuildStep> shuffled = new ArrayList<>(steps);
        Collections.shuffle(shuffled, new Random(shuffleSeed));

        assertEquals(new BuildPlan(SIZE, steps, List.of()), new BuildPlan(SIZE, shuffled, List.of()));
    }

    @Property(tries = 300)
    void planLosesAndInventsNothing(@ForAll("stepLists") List<BuildStep> steps) {
        BuildPlan plan = new BuildPlan(SIZE, steps, List.of());

        assertEquals(steps.size(), plan.steps().size(), "число шагов");
        assertEquals(new HashSet<>(steps), new HashSet<>(plan.steps()), "состав шагов");
    }

    @Property(tries = 300)
    void categoriesNeverInterleave(@ForAll("stepLists") List<BuildStep> steps) {
        int previous = Integer.MIN_VALUE;
        for (BuildStep step : new BuildPlan(SIZE, steps, List.of()).steps()) {
            assertTrue(step.category().ordinal() >= previous,
                    "категории обязаны идти блоками: " + step.category());
            previous = step.category().ordinal();
        }
    }

    /**
     * Расчистка обязана идти сверху вниз, установка снизу вверх. Первое —
     * чтобы снятый низ не оставил висеть верх, второе — чтобы кладка выглядела
     * кладкой и чтобы фундамент, стены и крыша встали в этом порядке сами.
     */
    @Property(tries = 300)
    void heightsRunTheRightWayWithinEachCategory(@ForAll("stepLists") List<BuildStep> steps) {
        BuildPlan plan = new BuildPlan(SIZE, steps, List.of());

        for (BuildCategory category : BuildCategory.values()) {
            List<Integer> heights = plan.steps().stream()
                    .filter(step -> step.category() == category)
                    .map(step -> step.pos().getY())
                    .toList();

            for (int i = 1; i < heights.size(); i++) {
                int previous = heights.get(i - 1);
                int current = heights.get(i);
                if (category.topDown()) {
                    assertTrue(current <= previous, category.id() + ": высота обязана не расти");
                } else {
                    assertTrue(current >= previous, category.id() + ": высота обязана не убывать");
                }
            }
        }
    }

    @Property(tries = 200)
    void blockCountMatchesStepsThatPlaceSomething(@ForAll("stepLists") List<BuildStep> steps) {
        BuildPlan plan = new BuildPlan(SIZE, steps, List.of());

        long placing = steps.stream().filter(step -> step.category() != BuildCategory.CLEAR).count();
        assertEquals(placing, plan.blockCount());
    }

    @Property(tries = 200)
    void pointsOfInterestAreOrderedIndependentlyOfInput(@ForAll("poiLists") List<PointOfInterest> pois,
                                                        @ForAll long shuffleSeed) {
        List<PointOfInterest> shuffled = new ArrayList<>(pois);
        Collections.shuffle(shuffled, new Random(shuffleSeed));

        assertEquals(new BuildPlan(SIZE, List.of(), pois), new BuildPlan(SIZE, List.of(), shuffled));
    }

    // --- генераторы ---

    /**
     * Шаги с попарно разными позициями: два шага на одну позицию план отвергает
     * намеренно, и это проверено отдельным примером, а не свойством.
     */
    @Provide
    Arbitrary<List<BuildStep>> stepLists() {
        return steps().list().ofMaxSize(40).map(BuildOrderProperty::onePerPosition);
    }

    @Provide
    Arbitrary<List<PointOfInterest>> poiLists() {
        return Combinators.combine(Arbitraries.of(MarkerKind.values()), positions())
                .as(PointOfInterest::new)
                .list().uniqueElements().ofMaxSize(12);
    }

    private Arbitrary<BuildStep> steps() {
        return Combinators.combine(
                        positions(),
                        Arbitraries.of(BuildCategory.values()),
                        Arbitraries.integers().between(0, 30))
                .as((pos, category, palette) -> category != BuildCategory.CLEAR
                        ? BuildStep.placing(pos, category, palette)
                        // Расчистка бывает и под посев: она помнит блок, но не ставит его.
                        : palette % 2 == 0 ? BuildStep.clearing(pos)
                        : BuildStep.clearingFor(pos, palette));
    }

    /** Тесная коробка нарочно: так столкновения по столбцам и слоям случаются часто. */
    private Arbitrary<BlockPos> positions() {
        Arbitrary<Integer> coordinate = Arbitraries.integers().between(0, 3);
        return Combinators.combine(coordinate, coordinate, coordinate).as(BlockPos::new);
    }

    private static List<BuildStep> onePerPosition(List<BuildStep> steps) {
        Map<BlockPos, BuildStep> byPosition = new LinkedHashMap<>();
        for (BuildStep step : steps) {
            byPosition.putIfAbsent(step.pos(), step);
        }
        return List.copyOf(byPosition.values());
    }
}
