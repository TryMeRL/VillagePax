package com.villagepax.sim.build;

import com.villagepax.sim.Ground;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.Optional;

/**
 * Кроны: поселение, поднятое над лесом.
 * <p>
 * Зеркало чертога и написано нарочно как зеркало. У гномов пол вырублен
 * <b>в</b> толще, у эльфов настлан <b>над</b> ней; у гномов годность места
 * решает свод над головой, у эльфов — лес под настилом; гномы прорубают
 * ход наружу, эльфы спускают лестницу вниз. Общего у них ровно одно
 * и главное: <b>отметка пола едина на всё поселение</b> ({@link Footing}).
 *
 * <h2>Дом на сваях — здесь замысел, а не поломка</h2>
 * Две недели назад мод чинил ровно обратное: «пусть строитель строит так,
 * чтобы под ней не было пустот». Площадка тогда получила правило
 * «равнять склон, а не строить сваи» — дом, не касающийся земли нигде,
 * она не трогает.
 * <p>
 * Это правило писалось про помосты и скалы, а сработало здесь: дом
 * в кронах — это и есть дом на весу, и подсыпать под него землю значило бы
 * вывести из-под эльфийского жилья столб грунта в дюжину блоков. Площадка
 * молчит сама, а {@link Footing#levelsTheGround()} говорит об этом вслух —
 * потому что молчание по счастливой случайности и молчание по правилу
 * читаются одинаково ровно до первой правки.
 *
 * <h2>Почему девять</h2>
 * Настил лежит на девять блоков выше земли. Ниже — подлесок: дом стоял бы
 * среди кустов и выглядел бы обычным домом на сваях. Выше — небо: дубы
 * и берёзы кончаются, и деревня висела бы ни на чём. Девять — это высота,
 * на которой в ванильном лесу проходят кроны, и дом среди них читается
 * как дом <b>в</b> лесу, а не над ним.
 */
public final class Canopy {

    /**
     * На сколько блоков настил поднят над землёй.
     * <p>
     * Одна отметка на поселение, как и у чертога, и по той же причине:
     * лестницы между домами превратили бы деревню в головоломку,
     * а путь жителя — в подъём с перекладины на перекладину.
     */
    public static final int LIFT = 9;

    /**
     * Насколько глубоко ищется опора под настилом.
     * <p>
     * Шестнадцать: настил висит на девяти, и ствол под ним может уходить
     * в овраг. Глубже искать незачем — там уже не лес, а склон, на котором
     * эльфийская деревня висела бы над пропастью.
     */
    public static final int DEEP = 16;

    private Canopy() {
    }

    /**
     * Где над этой колонной ляжет настил — или пусто, если леса тут нет.
     * <p>
     * Лес спрашивается прямо: под будущим настилом должен стоять ствол.
     * Без этой проверки эльфы поставили бы деревню над лугом, на девяти
     * блоках пустоты, и выглядело бы это не деревней в кронах,
     * а ошибкой генерации.
     */
    public static Optional<BlockPos> deckOver(ServerWorld world, int x, int z) {
        Optional<BlockPos> soil = Ground.buildableAt(world, x, z);
        if (soil.isEmpty()) {
            return Optional.empty();
        }

        BlockPos deck = new BlockPos(x, soil.get().getY() + LIFT, z);
        if (deck.getY() >= world.getTopY() - 1) {
            return Optional.empty();
        }
        return forestAround(world, deck) ? Optional.of(deck) : Optional.empty();
    }

    /**
     * Держит ли лес настил такого размера.
     * <p>
     * Два условия, и оба нужны. <b>Сверху свободно</b>: дом расчистит
     * листву сам — воздух в схеме это умеет, — но в склон холма или
     * в чужую стену настил не вложить. <b>Снизу лес</b>: хотя бы одна
     * колонна следа должна опираться на ствол.
     * <p>
     * Второе условие намеренно мягкое — <b>хотя бы одна</b>, а не все
     * четыре. Лес не растёт по углам квадрата семь на семь, и требовать
     * ствол под каждым углом значило бы не поставить ни одного дома
     * ни в одном ванильном лесу. Это то же рассуждение, по которому
     * площадка спрашивает «касается ли подошва земли где-нибудь».
     */
    public static boolean isBorne(ServerWorld world, BlockPos anchor, Vec3i size) {
        int acrossX = Math.max(1, size.getX() - 1);
        int acrossZ = Math.max(1, size.getZ() - 1);

        // Свободно — спрашивается у четырёх углов: столько же спрашивает
        // о своём своде чертог, и по той же причине.
        for (int dx = 0; dx < size.getX(); dx += acrossX) {
            for (int dz = 0; dz < size.getZ(); dz += acrossZ) {
                BlockPos corner = new BlockPos(anchor.getX() + dx, anchor.getY(),
                        anchor.getZ() + dz);
                for (int up = 0; up < size.getY(); up++) {
                    if (!isOpen(world, corner.up(up))) {
                        return false;
                    }
                }
            }
        }

        // А лес — у всего следа через клетку. Углами тут не обойтись:
        // деревья стоят там, где выросли, и ровно под четырьмя углами
        // квадрата семь на семь ствола не бывает почти никогда.
        for (int dx = 0; dx < size.getX(); dx += 2) {
            for (int dz = 0; dz < size.getZ(); dz += 2) {
                if (standsOnWood(world, anchor.add(dx, 0, dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Свободно ли здесь — то есть можно ли настелить.
     * <p>
     * Листва свободна: её дом снимет теми же шагами расчистки, какими
     * на лугу валит дерево. Бревно — нет: ствол, прошедший сквозь горницу,
     * это не колонна, это дерево в комнате.
     */
    public static boolean isOpen(ServerWorld world, BlockPos at) {
        BlockState state = world.getBlockState(at);
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir() || state.isIn(BlockTags.LEAVES) || state.isReplaceable();
    }

    /**
     * Есть ли лес под настилом — здесь или в двух шагах.
     * <p>
     * Спрашивается о соседях, а не об одной колонне, и это не послабление,
     * а исправление настоящей нелепицы: колонна, в которой стоит ствол,
     * землёй не считается вовсе — ствол не земля, и поиск опоры её честно
     * отвергает. Требуй мы ствол ровно под серединой деревни, эльфы
     * не поселились бы нигде: им нужна была бы колонна, которая
     * одновременно и дерево, и не дерево.
     */
    private static boolean forestAround(ServerWorld world, BlockPos deck) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (standsOnWood(world, deck.add(dx, 0, dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Стоит ли эта колонна на дереве.
     * <p>
     * Ищется <b>ствол</b>, а не что попало твёрдое. Настил, опёртый
     * на случайный валун, — это дом на камне посреди леса; настил
     * на стволе — это дом на дереве, ради которого весь народ и заведён.
     */
    public static boolean standsOnWood(ServerWorld world, BlockPos deck) {
        for (int down = 1; down <= DEEP; down++) {
            BlockPos at = deck.down(down);
            if (at.getY() <= world.getBottomY()) {
                return false;
            }
            if (world.getBlockState(at).isIn(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }
}
