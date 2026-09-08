package com.villagepax.entity;

import com.villagepax.core.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.util.math.BlockPos;

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
 */
public class CitizenPathNodeMaker extends LandPathNodeMaker {

    /**
     * Во сколько блоков пути обходится шаг по бездорожью.
     * <p>
     * Единица значит «крюк длиной до одного блока ради дороги оправдан,
     * длиннее — нет».
     */
    public static final float OFF_ROAD_PENALTY = 1.0f;

    @Override
    public int getSuccessors(PathNode[] successors, PathNode node) {
        int count = super.getSuccessors(successors, node);

        for (int index = 0; index < count; index++) {
            PathNode successor = successors[index];
            if (!isRoad(successor)) {
                successor.penalty += OFF_ROAD_PENALTY;
            }
        }
        return count;
    }

    /** Дорога — то, по чему житель идёт, а не то, на что он смотрит. */
    private boolean isRoad(PathNode node) {
        BlockPos under = new BlockPos(node.x, node.y - 1, node.z);
        BlockState state = cachedWorld.getBlockState(under);

        return state.isIn(ModTags.PREFERRED_PATH);
    }
}
