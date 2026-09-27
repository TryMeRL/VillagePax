package com.villagepax.sim.build;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отображение «блок схемы → род точки интереса».
 * <p>
 * Живёт здесь, а не в {@code ModBlocks}, чтобы остаться чистым и проверяемым
 * без запуска игры: реестры блоков для сопоставления путей не нужны.
 */
class MarkerKindTest {

    @Test
    void recognisesEveryMarkerBlock() {
        assertEquals(Optional.of(MarkerKind.WORKSTATION), MarkerKind.byBlockPath("marker_workstation"));
        assertEquals(Optional.of(MarkerKind.BED), MarkerKind.byBlockPath("marker_bed"));
        assertEquals(Optional.of(MarkerKind.STORAGE), MarkerKind.byBlockPath("marker_storage"));
        assertEquals(Optional.of(MarkerKind.DOOR), MarkerKind.byBlockPath("marker_door"));
        assertEquals(Optional.of(MarkerKind.DECOR), MarkerKind.byBlockPath("marker_decor"));
        // Места праздника на ярмарке: загон для ловли и стрелковая черта.
        assertEquals(Optional.of(MarkerKind.PEN), MarkerKind.byBlockPath("marker_pen"));
        assertEquals(Optional.of(MarkerKind.SHOOTING), MarkerKind.byBlockPath("marker_shooting"));
    }

    @Test
    void everyKindHasABlockPath() {
        for (MarkerKind kind : MarkerKind.values()) {
            assertEquals(Optional.of(kind), MarkerKind.byBlockPath(kind.blockPath()),
                    "род " + kind.id() + " обязан находиться по своему же пути блока");
        }
    }

    @Test
    void ignoresBlocksThatAreNotMarkers() {
        assertTrue(MarkerKind.byBlockPath("town_hall").isEmpty());
        assertTrue(MarkerKind.byBlockPath("oak_planks").isEmpty());
        assertTrue(MarkerKind.byBlockPath("marker_").isEmpty());
        assertTrue(MarkerKind.byBlockPath("").isEmpty());
    }

    /** Похожее имя не должно проходить: иначе чужой блок стал бы точкой интереса. */
    @Test
    void doesNotMatchOnPrefixAlone() {
        assertTrue(MarkerKind.byBlockPath("marker_bedrock").isEmpty());
        assertTrue(MarkerKind.byBlockPath("marker_doorbell").isEmpty());
        assertTrue(MarkerKind.byBlockPath("marker_penguin").isEmpty());
    }
}
