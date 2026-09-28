package com.villagepax.sim.build;

import com.villagepax.core.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Превращает содержимое схемы в план стройки.
 * <p>
 * Единственное место, где блокстейт вообще смотрят: дальше по конвейеру идут
 * только номера в палитре, и потому порядок стройки проверяется тестами
 * без запуска игры.
 */
public final class BuildPlanner {

    /**
     * На сколько шагов от входа расчищается подход.
     * <p>
     * Три клетки — это ровно то место, где житель разворачивается, выходя
     * за порог. Дальше начинается улица, а улицу мод не корчует: дом,
     * прогрызающий просеку в лесу на все стороны, выглядел бы не деревней,
     * а вырубкой.
     */
    private static final int APPROACH = 3;

    /** Насколько далеко смотрят, выбирая сторону «наружу». */
    private static final int LOOK = 4;

    /**
     * Цена улицы при выборе стороны: заведомо дороже любой пустоты внутри.
     * Пустота под крышей стоит не больше {@link #LOOK}, и никакая горница
     * не перевесит выхода наружу.
     */
    private static final int STREET = 100;

    private BuildPlanner() {
    }

    public static BuildPlan plan(Vec3i size, List<BlockState> palette,
                                 List<Schematic.PalettedBlock> blocks,
                                 List<PointOfInterest> markers) {
        List<BuildStep> steps = new ArrayList<>(blocks.size());

        for (Schematic.PalettedBlock block : blocks) {
            BlockState state = palette.get(block.paletteIndex());

            // Пустота структуры — это «не трогать»: так автор схемы оставляет
            // рельеф на месте. Шага не будет вовсе, иначе билдер выбил бы
            // склон, который его специально попросили не касаться.
            if (state.isOf(Blocks.STRUCTURE_VOID)) {
                continue;
            }

            // Воздух в схеме — требование, а не отсутствие требования:
            // здесь должно стать пусто, даже если сейчас гора.
            if (state.isAir()) {
                steps.add(BuildStep.clearing(block.pos()));
                continue;
            }

            BuildCategory category = categoryOf(state);
            if (category == BuildCategory.SOWING) {
                // Грядка пустеет вместе со всем объёмом, а не ждёт посева
                // породой: пашня, легшая под камень, через тик сама
                // становится землёй — так строитель и сдавал гномье поле
                // без единой грядки.
                steps.add(BuildStep.clearingFor(block.pos(), block.paletteIndex()));
            }
            steps.add(BuildStep.placing(block.pos(), category, block.paletteIndex()));
        }

        clearApproaches(size, palette, blocks, markers, steps);
        return new BuildPlan(size, steps, markers);
    }

    /**
     * Расчистить подход к каждому входу — как часть самой стройки.
     * <p>
     * Жалоба заказчика: «построил ферму, а зайти нельзя, дерево блокирует».
     * Дерево росло <b>вплотную к калитке</b>, но за следом здания, а план
     * до сих пор расчищал только след. Крыльцо потом честно клало ступени
     * под стволом, и войти всё равно было нельзя.
     * <p>
     * Чинится это не новым устройством, а <b>теми же шагами плана</b>:
     * к плану добавляются клетки прохода в рост человека на три шага
     * от порога. Отсюда всё сразу: билдер рубит дерево сам, со звуком
     * и с топором в руках; расчищенная клетка стоит ровно ноль (шаг
     * по пустому месту план пропускает); ошибиться негде, потому что
     * нового кода, который мог бы ошибиться, нет.
     * <p>
     * И задолго до крыши: расчистка идёт первой фазой плана, то есть
     * дерево валится <b>до</b> того, как встанут стены.
     */
    private static void clearApproaches(Vec3i size, List<BlockState> palette,
                                        List<Schematic.PalettedBlock> blocks,
                                        List<PointOfInterest> markers, List<BuildStep> steps) {
        Set<BlockPos> taken = new HashSet<>();
        for (BuildStep step : steps) {
            taken.add(step.pos());
        }

        for (Schematic.Entrance door : entrancesOf(size, palette, blocks, markers)) {
            for (int step = 1; step <= APPROACH; step++) {
                BlockPos ahead = door.pos().offset(door.wayOut(), step);
                // В рост: ноги и голова. Ниже — земля, и её равняет крыльцо;
                // выше — крона, а под кроной ходят.
                for (int up = 0; up <= 1; up++) {
                    BlockPos cell = ahead.up(up);
                    if (taken.add(cell)) {
                        steps.add(BuildStep.clearing(cell));
                    }
                }
            }
        }
    }

    /**
     * Входы схемы: двери, калитки и метки входа.
     * <p>
     * По одному на столбец — <b>нижний</b>. Дверь высотой в два блока даёт
     * в столбце две клетки, а вход у неё один: крыльцо, померенное от
     * верхней половины, выкладывало ступень на высоте головы.
     */
    public static List<Schematic.Entrance> entrancesOf(Vec3i size, List<BlockState> palette,
                                                       List<Schematic.PalettedBlock> blocks,
                                                       List<PointOfInterest> markers) {
        Set<BlockPos> filled = new HashSet<>();
        for (Schematic.PalettedBlock block : blocks) {
            BlockState state = palette.get(block.paletteIndex());
            if (!state.isAir() && !state.isOf(Blocks.STRUCTURE_VOID)) {
                filled.add(block.pos());
            }
        }

        Map<Long, BlockPos> lowest = new LinkedHashMap<>();
        for (PointOfInterest marker : markers) {
            if (marker.kind() == MarkerKind.DOOR) {
                keepLowest(lowest, marker.pos());
            }
        }
        for (Schematic.PalettedBlock block : blocks) {
            BlockState state = palette.get(block.paletteIndex());
            if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.FENCE_GATES)) {
                keepLowest(lowest, block.pos());
            }
        }

        List<Schematic.Entrance> doors = new ArrayList<>(lowest.size());
        for (BlockPos door : lowest.values()) {
            doors.add(new Schematic.Entrance(door, wayOut(size, door, filled)));
        }
        return List.copyOf(doors);
    }

    private static void keepLowest(Map<Long, BlockPos> lowest, BlockPos door) {
        long column = ((long) door.getX() << 32) ^ (door.getZ() & 0xFFFFFFFFL);
        BlockPos known = lowest.get(column);
        if (known == null || door.getY() < known.getY()) {
            lowest.put(column, door);
        }
    }

    /**
     * В какую сторону от входа улица.
     * <p>
     * Спрашивается сам чертёж: наружу — туда, где след здания кончается
     * раньше всего. Догадка по середине следа ошибалась там, где след
     * не квадратный: у хижины лесоруба к дому пристроена роща, середина
     * уезжает в неё, и крыльцо упиралось в сруб.
     * <p>
     * Улица бьёт любую пустоту под крышей, и это не мелочь счёта: у двери
     * в южной стене на юг след кончается сразу, а на север лежат четыре
     * пустые клетки горницы. Мерка «где дольше пусто» давала поровну,
     * и ничья доставалась тому, кого первым назовёт перебор сторон.
     */
    public static Direction wayOut(Vec3i size, BlockPos door, Set<BlockPos> filled) {
        Direction best = Direction.NORTH;
        int bestScore = Integer.MIN_VALUE;

        for (Direction way : Direction.Type.HORIZONTAL) {
            int score = 0;
            for (int step = 1; step <= LOOK; step++) {
                BlockPos at = door.offset(way, step);
                boolean outside = at.getX() < 0 || at.getZ() < 0
                        || at.getX() >= size.getX() || at.getZ() >= size.getZ();
                if (outside) {
                    // Улица найдена, и чем ближе она, тем вернее сторона.
                    score = STREET - step;
                    break;
                }
                if (filled.contains(at)) {
                    break;
                }
                // Пусто, но всё ещё под крышей: это горница, а не улица.
                score = step;
            }
            if (score > bestScore) {
                bestScore = score;
                best = way;
            }
        }
        return best;
    }

    public static BuildCategory categoryOf(BlockState state) {
        if (state.isIn(ModTags.BUILD_SOWING)) {
            return BuildCategory.SOWING;
        }
        return state.isIn(ModTags.BUILD_DECOR) ? BuildCategory.DECOR : BuildCategory.STRUCTURE;
    }

}
