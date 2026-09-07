package com.villagepax.sim.build;

import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

/**
 * Точка интереса здания: что тут будет и где именно.
 * <p>
 * Позиция задана относительно якоря схемы, а не в координатах мира: одна
 * и та же схема ставится в разных местах и с разным поворотом, и точки
 * пересчитываются вместе с ней.
 */
public record PointOfInterest(MarkerKind kind, BlockPos pos) {

    /** Устойчивый порядок: без него два разбора схемы дали бы разные списки. */
    public static final Comparator<PointOfInterest> ORDER = Comparator
            .comparingInt((PointOfInterest poi) -> poi.kind.ordinal())
            .thenComparingInt(poi -> poi.pos.getY())
            .thenComparingInt(poi -> poi.pos.getX())
            .thenComparingInt(poi -> poi.pos.getZ());

    public PointOfInterest {
        // BlockPos.Mutable наследует BlockPos, поэтому запись может незаметно
        // получить меняющуюся позицию — обход схемы курсором ровно это и даёт.
        pos = pos.toImmutable();
    }
}
