package com.villagepax.sim.build;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Порядок обхода схемы — то, из чего складывается вид стройки.
 * <p>
 * Дизайн-документ описывает его как «расчистка → фундамент → стены → крыша →
 * декор», но геометрически отличить стену от крыши надёжно нельзя: у мансарды
 * крыша и есть стена. Здесь проверяется решение, которое даёт тот же вид без
 * распознавания: расчистка сверху вниз, установка снизу вверх. Фундамент лежит
 * ниже стен, крыша выше — значит обход по возрастанию высоты выкладывает их
 * в нужном порядке сам.
 */
class BuildOrderTest {

    @Test
    void categoriesGoInFixedOrder() {
        List<BuildStep> steps = sorted(List.of(
                BuildStep.placing(new BlockPos(0, 0, 3), BuildCategory.SOWING, 3),
                BuildStep.placing(new BlockPos(0, 0, 0), BuildCategory.DECOR, 1),
                BuildStep.placing(new BlockPos(0, 0, 1), BuildCategory.STRUCTURE, 2),
                BuildStep.clearing(new BlockPos(0, 0, 2))));

        assertEquals(
                List.of(BuildCategory.CLEAR, BuildCategory.STRUCTURE, BuildCategory.DECOR,
                        BuildCategory.SOWING),
                steps.stream().map(BuildStep::category).toList());
    }

    /**
     * Посев — после ламп, даже если лампа висит выше грядки: грядка живёт
     * светом, и сеять её в темноте значит осыпать.
     */
    @Test
    void sowingComesAfterTheLampsAbove() {
        List<BuildStep> steps = sorted(List.of(
                BuildStep.placing(new BlockPos(0, 1, 0), BuildCategory.SOWING, 0),
                BuildStep.placing(new BlockPos(0, 3, 0), BuildCategory.DECOR, 1)));

        assertEquals(List.of(3, 1), heights(steps));
    }

    /** Расчистка под посев помнит посев, но блока не ставит. */
    @Test
    void clearingForABedPlacesNothing() {
        BuildStep step = BuildStep.clearingFor(new BlockPos(1, 2, 3), 4);

        assertEquals(BuildCategory.CLEAR, step.category());
        assertEquals(4, step.paletteIndex());
        assertFalse(step.placesBlock());
    }

    @Test
    void clearingGoesTopDown() {
        List<BuildStep> steps = sorted(List.of(
                BuildStep.clearing(new BlockPos(0, 0, 0)),
                BuildStep.clearing(new BlockPos(0, 5, 0)),
                BuildStep.clearing(new BlockPos(0, 2, 0))));

        assertEquals(List.of(5, 2, 0), heights(steps),
                "снятый снизу блок оставил бы висеть верх");
    }

    @Test
    void structureGoesBottomUp() {
        List<BuildStep> steps = sorted(List.of(
                BuildStep.placing(new BlockPos(0, 4, 0), BuildCategory.STRUCTURE, 0),
                BuildStep.placing(new BlockPos(0, 0, 0), BuildCategory.STRUCTURE, 0),
                BuildStep.placing(new BlockPos(0, 2, 0), BuildCategory.STRUCTURE, 0)));

        assertEquals(List.of(0, 2, 4), heights(steps), "кладка идёт снизу вверх");
    }

    @Test
    void decorGoesBottomUpToo() {
        List<BuildStep> steps = sorted(List.of(
                BuildStep.placing(new BlockPos(0, 3, 0), BuildCategory.DECOR, 0),
                BuildStep.placing(new BlockPos(0, 1, 0), BuildCategory.DECOR, 0)));

        assertEquals(List.of(1, 3), heights(steps));
    }

    /**
     * Ровно то требование, которое родительский план записал в приёмку:
     * список одинаков между запусками. Порядок обязан быть полным, иначе
     * два прогона по одной схеме дадут разные последовательности.
     */
    @Test
    void orderIsTotalSoShufflingChangesNothing() {
        List<BuildStep> original = new ArrayList<>();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    BuildCategory category = (x + y + z) % 3 == 0
                            ? BuildCategory.DECOR
                            : BuildCategory.STRUCTURE;
                    original.add(BuildStep.placing(new BlockPos(x, y, z), category, x + z));
                }
            }
        }

        List<BuildStep> expected = sorted(original);

        for (long seed = 0; seed < 20; seed++) {
            List<BuildStep> shuffled = new ArrayList<>(original);
            Collections.shuffle(shuffled, new Random(seed));
            assertEquals(expected, sorted(shuffled), "сид перетасовки " + seed);
        }
    }

    @Test
    void withinLayerOrderIsByColumn() {
        List<BuildStep> steps = sorted(List.of(
                BuildStep.placing(new BlockPos(1, 0, 1), BuildCategory.STRUCTURE, 0),
                BuildStep.placing(new BlockPos(0, 0, 1), BuildCategory.STRUCTURE, 0),
                BuildStep.placing(new BlockPos(1, 0, 0), BuildCategory.STRUCTURE, 0),
                BuildStep.placing(new BlockPos(0, 0, 0), BuildCategory.STRUCTURE, 0)));

        assertEquals(List.of(
                        new BlockPos(0, 0, 0),
                        new BlockPos(0, 0, 1),
                        new BlockPos(1, 0, 0),
                        new BlockPos(1, 0, 1)),
                steps.stream().map(BuildStep::pos).toList());
    }

    @Test
    void clearingCarriesNoBlockToPlace() {
        BuildStep step = BuildStep.clearing(new BlockPos(1, 2, 3));

        assertEquals(BuildCategory.CLEAR, step.category());
        assertEquals(BuildStep.NO_BLOCK, step.paletteIndex());
        assertTrue(step.category().topDown());
    }

    private static List<BuildStep> sorted(List<BuildStep> steps) {
        return steps.stream().sorted(BuildStep.ORDER).toList();
    }

    private static List<Integer> heights(List<BuildStep> steps) {
        return steps.stream().map(step -> step.pos().getY()).toList();
    }
}
