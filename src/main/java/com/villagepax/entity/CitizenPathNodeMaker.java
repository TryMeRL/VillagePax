package com.villagepax.entity;

import com.villagepax.core.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Житель предпочитает дорогу.
 * <p>
 * Ванильный поиск пути ведёт мобов по прямой: трава и мостовая для него
 * одинаковы, и все работники колонии идут одной и той же линией через
 * газон, толкаясь друг о друга. Здесь у шага <b>вне дороги</b> появляется
 * небольшая надбавка к стоимости, и путь сам ложится на мостовую, если она
 * есть. Если её нет, надбавка одинакова для всех направлений и ничего
 * не меняет — житель идёт как прежде.
 * <p>
 * Что считать дорогой, решает тег {@code villagepax:preferred_path}: игрок
 * может замостить деревню чем захочет и добавить свой блок в датапак, не
 * трогая код.
 * <p>
 * Надбавка мала намеренно. Большая заставляла бы жителя делать круг через
 * полдеревни, чтобы ступить на камень, — а дорога должна быть удобством,
 * а не лабиринтом.
 * <p>
 * Верх заборов здесь не запрещается, и это проверено: ваниль и так
 * не ведёт путь по забору (проверка {@code citizenNeverWalksAlongFenceTops}
 * ставит жителя с дальней стороны ограды, где через неё вдвое короче).
 * Оказаться на заборе житель всё же может — в толкотне у калитки, — и
 * с него его снимает рефлекс тела ({@code CitizenEntity#stepOutOfTrouble}).
 * Запрет здесь стоял страховкой, выключенной и забытой; выключенная
 * страховка с описанием, будто она работает, хуже никакой.
 */
public class CitizenPathNodeMaker extends LandPathNodeMaker {

    /**
     * Во сколько обходится шаг по бездорожью.
     * <p>
     * Надбавка идёт на <b>каждый</b> шаг, поэтому это не «крюк в один блок»,
     * а множитель: путь по мостовой выигрывает, пока он не длиннее прямого
     * примерно на восемьдесят процентов.
     * <p>
     * Величина подобрана опытом, а не расчётом, и это важно записать.
     * Арифметика говорит, что хватит и 0.4 — по стоимости шагов мостовая
     * там уже выигрывает. На деле не хватает: ванильный поиск пути
     * взвешивает оценку расстояния до цели и останавливается на первом
     * же пути, который до неё дотянулся, поэтому прямая линия побеждает,
     * пока надбавка меньше примерно половины блока. 0.8 — наименьшее
     * значение, при котором игровой тест видит переход на мостовую;
     * больше делать незачем: чем выше надбавка, тем больше узлов
     * обходит алгоритм.
     */
    public static final float OFF_ROAD_PENALTY = 0.8f;

    /**
     * Узлы, уже получившие надбавку в этом поиске.
     * <p>
     * Узел у поиска один на клетку и приходит соседом от каждого из своих
     * соседей; надбавка, прибавляемая при каждой встрече, копилась до пяти-
     * шести блоков на клетку луга. Путь по открытому месту тогда петлял,
     * а поиск обходил вдвое больше узлов.
     */
    private final Set<PathNode> charged = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public int getSuccessors(PathNode[] successors, PathNode node) {
        int count = super.getSuccessors(successors, node);
        for (int index = 0; index < count; index++) {
            if (charged.add(successors[index]) && !isRoad(successors[index])) {
                successors[index].penalty += OFF_ROAD_PENALTY;
            }
        }
        return count;
    }

    @Override
    public void init(ChunkCache cachedWorld, MobEntity entity) {
        charged.clear();
        super.init(cachedWorld, entity);
    }

    @Override
    public void clear() {
        charged.clear();
        super.clear();
    }

    /** Дорога — то, по чему житель идёт, а не то, на что он смотрит. */
    private boolean isRoad(PathNode node) {
        BlockPos under = new BlockPos(node.x, node.y - 1, node.z);
        BlockState state = cachedWorld.getBlockState(under);

        return state.isIn(ModTags.PREFERRED_PATH);
    }
}
