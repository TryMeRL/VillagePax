package com.villagepax.sim.work;

import com.villagepax.sim.Hazards;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Где человек может стоять.
 * <p>
 * Одно правило на весь мод, и заведено оно по следам двух жалоб.
 * Первая — «строитель тупит, пытается дотянуться и не может»: билдеру
 * целью ставили <b>сам блок плана</b>, а он на стене или на крыше.
 * Вторая — смерть жителя на очаге: стоять в огне тоже нельзя, а
 * стратегия об этом не знала.
 * <p>
 * Билдеру это починили отдельно, а курьер, фермер и лесоруб остались
 * с прежним приёмом: цель — сам сундук, сам угол стройки, сама грядка.
 * Курьера при проверке <b>послали внутрь сундука</b> — стоять там нельзя,
 * и вся надежда была на то, что ванильная навигация остановится рядом
 * сама. Иногда останавливается. Иногда топчется.
 * <p>
 * Поэтому правило общее: <b>идти надо к месту рядом с делом</b>, а не
 * в само дело. Если рядом стоять негде — возвращается сама цель, потому
 * что «никуда не идти» хуже, чем «идти приблизительно».
 */
public final class Standing {

    /**
     * Насколько далеко от дела искать место.
     * <p>
     * Два блока: сундук и грядку достают с соседней клетки, а дальше
     * искать вредно — житель встанет так далеко, что не дотянется.
     */
    private static final int NEAR = 2;

    /**
     * И насколько глубоко искать опору под работой, повисшей в воздухе.
     * <p>
     * Четыре блока — это вытянутая рука: с земли житель достаёт бревно,
     * висящее над ним, и валит его. Глубже искать бессмысленно, оттуда
     * не дотянуться.
     */
    private static final int UNDER = 4;

    private Standing() {
    }

    /** Ноги на твёрдом, голова в пустоте, и ничего не жжётся. */
    public static boolean canStandAt(ServerWorld world, BlockPos spot) {
        return world.getBlockState(spot.down()).isSolidBlock(world, spot.down())
                && world.getBlockState(spot).getCollisionShape(world, spot).isEmpty()
                && world.getBlockState(spot.up()).getCollisionShape(world, spot.up()).isEmpty()
                && !Hazards.standingHurts(world, spot);
    }

    /**
     * Место рядом с делом, откуда до него дотянуться.
     * <p>
     * Сперва проверяется сама цель — на грядке и на дорожке стоять можно,
     * и уводить оттуда жителя незачем. Потом кольца вокруг: своя высота,
     * на блок выше и на блок ниже, потому что сундук может стоять
     * в подвале или на помосте.
     */
    public static Optional<BlockPos> nextTo(ServerWorld world, BlockPos target) {
        if (canStandAt(world, target)) {
            return Optional.of(target);
        }

        for (int radius = 1; radius <= NEAR; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos spot = target.add(dx, dy, dz);
                        if (canStandAt(world, spot)) {
                            return Optional.of(spot);
                        }
                    }
                    BlockPos above = target.add(dx, 1, dz);
                    if (canStandAt(world, above)) {
                        return Optional.of(above);
                    }
                }
            }
        }

        // Работа висит в воздухе — бревно на весу, блок на крыше. Тогда
        // становимся под ней: с земли до неё рукой дотянуться можно,
        // а стоять в воздухе нельзя вовсе.
        for (int down = 1; down <= UNDER; down++) {
            BlockPos below = target.down(down);
            if (canStandAt(world, below)) {
                return Optional.of(below);
            }
        }
        return Optional.empty();
    }

    /** Как далеко искать, куда отойти со стройки: шире самого широкого следа. */
    private static final int OFF_SITE = 16;

    /**
     * Ближайшая клетка, где можно стоять, — за пределами стройки.
     * <p>
     * Куда отойти тому, кто стоит там, где сейчас ляжет блок. Не на шаг
     * в сторону, а прочь со следа: курицу, отведённую на соседнюю клетку,
     * ставило на верх растущей стены или под будущий стол, и накрывало
     * снова. Сперва — по своей высоте и ниже, вокруг целиком, и только
     * потом на блок выше.
     *
     * @param building клетки самой стройки: туда отходить нельзя
     */
    public static Optional<BlockPos> freeOutside(ServerWorld world, BlockPos taken,
                                                 java.util.function.Predicate<BlockPos> building) {
        for (int radius = 1; radius <= OFF_SITE; radius++) {
            for (int dy : new int[]{0, -1, 1}) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos spot = taken.add(dx, dy, dz);
                        if (!building.test(spot) && canStandAt(world, spot)) {
                            return Optional.of(spot);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Вывести на свободное место всякого, кого замуровало.
     * <p>
     * Страховка после всего, что кладёт блоки вокруг зданий разом: откоса,
     * площадки, крыльца, улицы, колодца. Дом отводит скотину из своей
     * кладки сам, а каждый из этих путей класть блок «с оглядкой» не умеет —
     * и курица, на которую лёг булыжник улицы, задыхалась. Проще и вернее
     * один раз после работы спросить мир, кто застрял в блоке.
     *
     * @return сколько вывели
     */
    public static int rescueBuried(ServerWorld world, net.minecraft.util.math.Box area) {
        int rescued = 0;
        for (net.minecraft.entity.mob.MobEntity mob : world.getEntitiesByClass(
                net.minecraft.entity.mob.MobEntity.class, area,
                mob -> mob.isAlive() && mob.isInsideWall())) {
            BlockPos at = mob.getBlockPos();
            Optional<BlockPos> free = canStandAt(world, at.up()) ? Optional.of(at.up())
                    : freeOutside(world, at, spot -> false);
            if (free.isPresent()) {
                BlockPos spot = free.get();
                mob.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                        mob.getYaw(), mob.getPitch());
                rescued++;
            }
        }
        return rescued;
    }

    /**
     * То же, но с оговоркой: не нашлось места — идём к самой цели.
     * <p>
     * «Идти приблизительно» хуже, чем «идти точно», но лучше, чем
     * «не идти»: житель, которому не назвали цели, стоит на месте,
     * и игрок видит поломку.
     */
    public static BlockPos besideOrAt(ServerWorld world, BlockPos target) {
        return nextTo(world, target).orElse(target);
    }
}
