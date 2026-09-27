package com.villagepax.sim.festival;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * Очки за попадание в мишень — по месту на грани.
 * <p>
 * Кольца квадратные, как рисует мишень: середина — четверть грани, среднее
 * кольцо — до пяти восьмых, остальное — край. Смещения — от середины грани,
 * в долях блока, от −0.5 до 0.5.
 */
public final class ArcheryScore {

    public static final int BULLSEYE = 5;
    public static final int INNER = 3;
    public static final int EDGE = 1;

    /** Полуширина середины: четыре пикселя из шестнадцати. */
    private static final double MIDDLE = 0.125;

    /** Полуширина среднего кольца: десять пикселей из шестнадцати. */
    private static final double RING = 0.3125;

    private ArcheryScore() {
    }

    /** Очки за точку на грани. */
    public static int rings(double u, double v) {
        double off = Math.max(Math.abs(u), Math.abs(v));
        if (off <= MIDDLE) {
            return BULLSEYE;
        }
        return off <= RING ? INNER : EDGE;
    }

    /** Точка попадания на грани — две оси грани от её середины. */
    public static double[] onFace(Vec3d hit, Direction side, BlockPos block) {
        double x = hit.x - (block.getX() + 0.5);
        double y = hit.y - (block.getY() + 0.5);
        double z = hit.z - (block.getZ() + 0.5);
        return switch (side.getAxis()) {
            case X -> new double[]{z, y};
            case Y -> new double[]{x, z};
            case Z -> new double[]{x, y};
        };
    }

    /** Множитель дальности: ближняя 1, средняя 2, дальняя 3 — по расстоянию от черты. */
    public static int range(List<BlockPos> targets, BlockPos line, BlockPos target) {
        List<BlockPos> byDistance = targets.stream()
                .sorted(Comparator.comparingInt(one -> horizontal(one, line)))
                .toList();
        int index = byDistance.indexOf(target);
        return index < 0 ? 1 : Math.min(3, index + 1);
    }

    private static int horizontal(BlockPos one, BlockPos other) {
        int dx = one.getX() - other.getX();
        int dz = one.getZ() - other.getZ();
        return dx * dx + dz * dz;
    }
}
