package com.villagepax.sim.festival;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Очки стрельбы — решение по игре, и числа здесь написаны руками.
 * <p>
 * Кольца квадратные, как нарисованы на мишени: середина — четверть грани
 * (до 0.125 от центра), среднее кольцо — до пяти восьмых (0.3125), дальше
 * край. Дальность — от черты: ближняя мишень ×1, средняя ×2, дальняя ×3.
 */
class ArcheryScoreTest {

    @Test
    void theMiddleIsWorthFive() {
        assertEquals(5, ArcheryScore.rings(0, 0));
        assertEquals(5, ArcheryScore.rings(0.1, -0.05));
    }

    @Test
    void theInnerRingIsWorthThree() {
        assertEquals(3, ArcheryScore.rings(0.2, 0));
        assertEquals(3, ArcheryScore.rings(0.3, 0.3));
    }

    @Test
    void theEdgeIsWorthOne() {
        assertEquals(1, ArcheryScore.rings(0.4, 0));
        assertEquals(1, ArcheryScore.rings(0.49, 0.49));
    }

    @Test
    void theRangeGrowsFromTheLine() {
        BlockPos line = new BlockPos(1, 64, 11);
        BlockPos near = new BlockPos(2, 65, 7);
        BlockPos middle = new BlockPos(1, 65, 4);
        BlockPos far = new BlockPos(2, 65, 1);
        List<BlockPos> targets = List.of(far, middle, near);
        assertEquals(1, ArcheryScore.range(targets, line, near));
        assertEquals(2, ArcheryScore.range(targets, line, middle));
        assertEquals(3, ArcheryScore.range(targets, line, far));
    }

    /** Точка попадания на южной грани — смещение от её середины по x и по высоте. */
    @Test
    void aHitIsMeasuredFromTheMiddleOfItsFace() {
        BlockPos block = new BlockPos(10, 64, 20);
        double[] onSouth = ArcheryScore.onFace(new Vec3d(10.75, 64.5, 21.0), Direction.SOUTH, block);
        assertArrayEquals(new double[]{0.25, 0.0}, onSouth, 1e-9);
        double[] onEast = ArcheryScore.onFace(new Vec3d(11.0, 64.9, 20.5), Direction.EAST, block);
        assertArrayEquals(new double[]{0.0, 0.4}, onEast, 1e-9);
    }
}
