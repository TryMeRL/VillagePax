package com.villagepax.sim.build;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * Перенос схемы в координаты мира.
 * <p>
 * {@code anchor} задаёт мировую позицию минимального угла следа здания
 * <b>после</b> поворота, поэтому здание всегда занимает ровно
 * {@code [anchor, anchor + rotatedSize)}. Проверка пересечений, границы
 * и голограмма считаются без особых случаев.
 * <p>
 * Альтернатива — якорь в локальном нуле схемы — означала бы, что при повороте
 * на 90° здание уезжает в отрицательные координаты, и каждый расчёт занятого
 * объёма тянул бы за собой поправку на размер.
 * <p>
 * Класс намеренно ничего не знает о мире: поворот — арифметика, и она
 * проверяется тестами без запуска игры.
 */
public final class BuildSite {

    private BuildSite() {
    }

    /** Размер следа после поворота: у четверти оборота стороны меняются местами. */
    public static Vec3i rotatedSize(Vec3i size, BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
            case NONE, CLOCKWISE_180 -> size;
        };
    }

    /**
     * Позиция блока схемы в мире.
     * <p>
     * Поворот идёт внутри следа, а не вокруг начала координат, поэтому
     * результат остаётся в положительной четверти и совпадает с тем, что
     * дал бы структурный блок при той же ориентации.
     */
    public static BlockPos toWorld(BlockPos anchor, Vec3i size, BlockRotation rotation, BlockPos local) {
        int width = size.getX();
        int depth = size.getZ();
        int x = local.getX();
        int z = local.getZ();

        BlockPos turned = switch (rotation) {
            case NONE -> new BlockPos(x, local.getY(), z);
            case CLOCKWISE_90 -> new BlockPos(depth - 1 - z, local.getY(), x);
            case CLOCKWISE_180 -> new BlockPos(width - 1 - x, local.getY(), depth - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, local.getY(), width - 1 - x);
        };

        return anchor.add(turned);
    }

    /**
     * Точка мира в координатах схемы — обратный ход {@link #toWorld}.
     * <p>
     * Нужен тем, кто держит в руках готовую точку мира (вход, метку, клетку
     * следа) и спрашивает у чертежа, что там нарисовано. Без обратного хода
     * такой вопрос решается перебором всех шагов плана с переводом каждого
     * в мир — и дорого, и <b>молча врёт</b>, когда точка пришла не из шагов,
     * а из меток: метки лежат в плане отдельным списком.
     * <p>
     * Поворот обратим ровно потому, что он идёт внутри следа: тот же след,
     * та же четверть оборота, только знак другой.
     */
    public static BlockPos toLocal(BlockPos anchor, Vec3i size, BlockRotation rotation, BlockPos world) {
        BlockPos turned = world.subtract(anchor);
        int width = size.getX();
        int depth = size.getZ();
        int x = turned.getX();
        int z = turned.getZ();

        return switch (rotation) {
            case NONE -> new BlockPos(x, turned.getY(), z);
            case CLOCKWISE_90 -> new BlockPos(z, turned.getY(), depth - 1 - x);
            case CLOCKWISE_180 -> new BlockPos(width - 1 - x, turned.getY(), depth - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(width - 1 - z, turned.getY(), x);
        };
    }

    /** Занимает ли здание эту точку мира. */
    public static boolean covers(BlockPos anchor, Vec3i size, BlockRotation rotation, BlockPos world) {
        Vec3i footprint = rotatedSize(size, rotation);
        BlockPos local = world.subtract(anchor);
        return local.getX() >= 0 && local.getX() < footprint.getX()
                && local.getY() >= 0 && local.getY() < footprint.getY()
                && local.getZ() >= 0 && local.getZ() < footprint.getZ();
    }
}
