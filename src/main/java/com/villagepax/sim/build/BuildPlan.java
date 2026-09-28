package com.villagepax.sim.build;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Готовый план стройки: билдеру остаётся идти по списку сверху вниз.
 * <p>
 * План считается <b>один раз при загрузке датапака</b> и дальше только
 * читается. Это и есть кэш, которого требует приёмка задачи: ленивый кэш
 * с инвалидацией был бы источником ошибок, а здесь инвалидировать нечего —
 * {@code /reload} пересчитывает всё заново.
 * <p>
 * План принимает шаги в любом порядке и приводит их к одному. Иначе порядок
 * стройки зависел бы от того, как разбиратель схемы обошёл палитру, а это
 * деталь реализации: сменится она — сменится и вид стройки.
 *
 * @param size  размер схемы в блоках
 * @param steps шаги в порядке выполнения
 * @param pois  точки интереса, найденные по маркерам схемы
 */
public record BuildPlan(Vec3i size, List<BuildStep> steps, List<PointOfInterest> pois) {

    public static final BuildPlan EMPTY = new BuildPlan(Vec3i.ZERO, List.of(), List.of());

    public BuildPlan {
        steps = ordered(steps);
        pois = pois.stream().sorted(PointOfInterest.ORDER).toList();
    }

    /**
     * Позиции шагов намеренно <b>не</b> проверяются на попадание в {@link #size}:
     * в задаче 1.6 билдеру понадобятся шаги ниже нуля, чтобы подвести опоры
     * под здание на склоне.
     * <p>
     * На одну клетку — не больше одной расчистки и одной укладки. Вместе они
     * встречаются у грядки: её расчищают со всем объёмом, а сеют последней.
     * Расчистка при этом всегда раньше: её фаза первая.
     */
    private static List<BuildStep> ordered(List<BuildStep> steps) {
        Set<BlockPos> cleared = new HashSet<>();
        Set<BlockPos> laid = new HashSet<>();
        for (BuildStep step : steps) {
            if (!(step.placesBlock() ? laid : cleared).add(step.pos())) {
                throw new IllegalArgumentException(
                        "два шага стройки на одну позицию: " + step.pos().toShortString());
            }
        }
        return steps.stream().sorted(BuildStep.ORDER).toList();
    }

    /** Сколько блоков предстоит поставить — по этому числу считается заявка на материалы. */
    public int blockCount() {
        return (int) steps.stream().filter(BuildStep::placesBlock).count();
    }

    public List<BlockPos> positionsOf(MarkerKind kind) {
        return pois.stream().filter(poi -> poi.kind() == kind).map(PointOfInterest::pos).toList();
    }

    public boolean isEmpty() {
        return steps.isEmpty() && pois.isEmpty();
    }
}
