package com.villagepax.sim.build;

import com.villagepax.core.Named;

import java.util.Optional;

/**
 * Род служебного блока схемы.
 * <p>
 * Маркеры — то, чем одна схема описывает и геометрию здания, и его логику:
 * где рабочее место профессии, где кровать жителя, где сундук, где вход
 * для навигации. Билдер ставит на их место воздух и запоминает точку.
 * <p>
 * Отображение «путь блока → род» живёт здесь, а не в {@code ModBlocks},
 * чтобы остаться чистым: реестры блоков для сопоставления строк не нужны,
 * и разбор порядка стройки проверяется без запуска игры.
 */
public enum MarkerKind implements Named {

    /** Рабочее место профессии. */
    WORKSTATION("workstation"),

    /** Место кровати жителя, живущего в этом здании. */
    BED("bed"),

    /** Сундук здания — часть склада поселения. */
    STORAGE("storage"),

    /** Точка входа: от неё считаются пути жителей. */
    DOOR("door"),

    /** Слот под случайный декор — одна схема даёт разные дома. */
    DECOR("decor"),

    /**
     * Клетка загона на ярмарке: здесь бегают зверьки ловли.
     * <p>
     * Каждая клетка отмечена отдельно, а не углами: загон у народов разной
     * формы (у эльфов он на помосте, у гномов — в зале), и зверёк удирает
     * только по этим клеткам. Угол, по которому достраивался бы прямоугольник,
     * выпустил бы его сквозь перила.
     */
    PEN("pen"),

    /** Стрелковая черта: отсюда стреляют по мишеням ярмарки. */
    SHOOTING("shooting");

    private static final String BLOCK_PREFIX = "marker_";

    private final String id;

    MarkerKind(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    /** Путь блока мода, соответствующий этому роду: {@code marker_bed}. */
    public String blockPath() {
        return BLOCK_PREFIX + id;
    }

    /**
     * Род маркера по пути блока, или пусто, если блок маркером не является.
     * <p>
     * Сравнение точное, а не по префиксу: иначе чужой {@code marker_bedrock}
     * стал бы точкой интереса, и билдер выкинул бы блок из схемы.
     */
    public static Optional<MarkerKind> byBlockPath(String blockPath) {
        for (MarkerKind kind : values()) {
            if (kind.blockPath().equals(blockPath)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
