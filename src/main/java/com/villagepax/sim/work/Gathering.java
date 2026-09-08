package com.villagepax.sim.work;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Вечерний сбор: после работы и до сна жители сходятся на площадь.
 * <p>
 * Просьба заказчика — «мелкая жизнь вокруг». Досуг был пустым: цель
 * снималась, и работника забирала прогулка, то есть он расходился куда
 * глаза глядят. Деревня от этого пустела как раз в те часы, когда игрок
 * чаще всего дома и смотрит на неё.
 * <p>
 * Сходятся к <b>ратуше</b>, потому что это единственное место, которое есть
 * у колонии всегда, и оно же середина деревни. Место в круге у каждого своё
 * и <b>устойчивое</b>: считается по опознавателю жителя, а не случайно, —
 * иначе он метался бы между двумя точками от решения к решению.
 * <p>
 * Дойдя, житель <b>отпускает цель</b>. Стоять в строю кругом было бы
 * страннее, чем расходиться: без цели его забирает прогулка, и он топчется
 * у площади сам собой — это и есть та жизнь, которой не хватало.
 */
public final class Gathering {

    /** Мест в круге. Больше населения хутора — хватит и деревне. */
    private static final int SLOTS = 12;

    /** Ближний и дальний радиус круга: ратуша шириной в семь блоков. */
    private static final int NEAR = 5;
    private static final int FAR = 9;

    private Gathering() {
    }

    /**
     * Где этому жителю стоять вечером.
     * <p>
     * След ратуши игрок ставит сам голограммой, и знать заранее, какой угол
     * площади он займёт, нельзя. Поэтому место не вычисляется, а
     * <b>ищется</b>: свой слот в круге, а если он занят стеной — следующий
     * по кругу, и так до дальнего радиуса. В самом плохом случае — центр:
     * житель хотя бы придёт к ратуше.
     */
    public static BlockPos spot(ServerWorld world, Settlement colony, Citizen citizen) {
        int mine = Math.floorMod(citizen.id().hashCode(), SLOTS);

        for (int radius = NEAR; radius <= FAR; radius++) {
            for (int step = 0; step < SLOTS; step++) {
                BlockPos ground = footing(world,
                        inRing(colony.center(), radius, (mine + step) % SLOTS));
                if (ground != null) {
                    return ground;
                }
            }
        }
        return colony.center();
    }

    private static BlockPos inRing(BlockPos centre, int radius, int slot) {
        double angle = slot * (2.0 * Math.PI / SLOTS);
        return centre.add((int) Math.round(Math.cos(angle) * radius), 0,
                (int) Math.round(Math.sin(angle) * radius));
    }

    /**
     * Опора в колонне у площади: ноги на твёрдом, голова в пустоте.
     * Ищется около уровня ратуши — площадь ровная, лазать по склону
     * вечером незачем.
     */
    private static BlockPos footing(ServerWorld world, BlockPos column) {
        for (int level = 1; level >= -2; level--) {
            BlockPos at = column.up(level);
            if (world.getBlockState(at.down()).isSolidBlock(world, at.down())
                    && world.getBlockState(at).getCollisionShape(world, at).isEmpty()
                    && world.getBlockState(at.up()).getCollisionShape(world, at.up()).isEmpty()) {
                return at;
            }
        }
        return null;
    }
}
