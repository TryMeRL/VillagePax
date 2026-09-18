package com.villagepax.sim.build;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перенос схемы в мир: поворот следа и сдвиг к якорю.
 * <p>
 * Якорь задаёт минимальный угол следа <b>после</b> поворота, поэтому здание
 * всегда занимает ровно {@code [anchor, anchor + rotatedSize)}. Иначе при
 * повороте на 90° оно уезжало бы в отрицательные координаты, и каждый расчёт
 * занятого объёма тянул бы поправку на размер.
 */
class BuildSiteTest {

    /** Несимметричный след намеренно: на квадрате половина ошибок поворота не видна. */
    private static final Vec3i SIZE = new Vec3i(4, 3, 7);
    private static final BlockPos ANCHOR = new BlockPos(100, 64, -50);

    @Test
    void noRotationIsPlainOffset() {
        assertEquals(ANCHOR, BuildSite.toWorld(ANCHOR, SIZE, BlockRotation.NONE, BlockPos.ORIGIN));
        assertEquals(ANCHOR.add(3, 2, 6),
                BuildSite.toWorld(ANCHOR, SIZE, BlockRotation.NONE, new BlockPos(3, 2, 6)));
    }

    @Test
    void rotationSwapsFootprintSides() {
        assertEquals(SIZE, BuildSite.rotatedSize(SIZE, BlockRotation.NONE));
        assertEquals(new Vec3i(7, 3, 4), BuildSite.rotatedSize(SIZE, BlockRotation.CLOCKWISE_90));
        assertEquals(SIZE, BuildSite.rotatedSize(SIZE, BlockRotation.CLOCKWISE_180));
        assertEquals(new Vec3i(7, 3, 4), BuildSite.rotatedSize(SIZE, BlockRotation.COUNTERCLOCKWISE_90));
    }

    /**
     * Северо-западный угол при повороте по часовой обязан стать северо-восточным.
     * Это та проверка, на которой ловится перепутанный знак.
     */
    @Test
    void clockwiseTurnMovesNorthWestCornerToNorthEast() {
        BlockPos rotated = BuildSite.toWorld(ANCHOR, SIZE, BlockRotation.CLOCKWISE_90, BlockPos.ORIGIN);

        Vec3i footprint = BuildSite.rotatedSize(SIZE, BlockRotation.CLOCKWISE_90);
        assertEquals(ANCHOR.add(footprint.getX() - 1, 0, 0), rotated);
    }

    @Test
    void heightIsNeverTouched() {
        for (BlockRotation rotation : BlockRotation.values()) {
            for (int y = 0; y < SIZE.getY(); y++) {
                BlockPos world = BuildSite.toWorld(ANCHOR, SIZE, rotation, new BlockPos(1, y, 2));
                assertEquals(ANCHOR.getY() + y, world.getY(), "поворот " + rotation);
            }
        }
    }

    /** Поворот — биекция следа: ни один блок не потерялся и два не легли в одну точку. */
    @Test
    void everyRotationIsABijectionOfTheFootprint() {
        for (BlockRotation rotation : BlockRotation.values()) {
            Vec3i footprint = BuildSite.rotatedSize(SIZE, rotation);
            Set<BlockPos> seen = new HashSet<>();

            for (int x = 0; x < SIZE.getX(); x++) {
                for (int y = 0; y < SIZE.getY(); y++) {
                    for (int z = 0; z < SIZE.getZ(); z++) {
                        BlockPos world = BuildSite.toWorld(ANCHOR, SIZE, rotation, new BlockPos(x, y, z));
                        assertTrue(seen.add(world),
                                "поворот " + rotation + ": две позиции легли в " + world.toShortString());

                        BlockPos local = world.subtract(ANCHOR);
                        assertTrue(local.getX() >= 0 && local.getX() < footprint.getX()
                                        && local.getZ() >= 0 && local.getZ() < footprint.getZ(),
                                "поворот " + rotation + ": " + local.toShortString() + " вне следа " + footprint);
                    }
                }
            }

            assertEquals(SIZE.getX() * SIZE.getY() * SIZE.getZ(), seen.size(), "поворот " + rotation);
        }
    }

    @Test
    void fourQuarterTurnsComeBackToStart() {
        BlockPos local = new BlockPos(1, 0, 5);
        Vec3i size = SIZE;
        BlockPos current = local;

        for (int turn = 0; turn < 4; turn++) {
            current = BuildSite.toWorld(BlockPos.ORIGIN, size, BlockRotation.CLOCKWISE_90, current);
            size = BuildSite.rotatedSize(size, BlockRotation.CLOCKWISE_90);
        }

        assertEquals(local, current, "четыре поворота по часовой обязаны дать тождество");
        assertEquals(SIZE, size);
    }

    /**
     * Обратный ход возвращает ровно то, с чего начали, — при любом повороте
     * и в любой клетке следа.
     * <p>
     * Крыльцо спрашивает у чертежа, что нарисовано вокруг входа, а вход
     * оно получает уже в координатах мира. Ошибись обратный ход знаком —
     * и сторона «наружу» возьмётся от чужой клетки: ступени лягут в стену,
     * причём молча и только у повёрнутых зданий, то есть у половины деревни.
     */
    @Test
    void backAndForthIsIdentity() {
        for (BlockRotation rotation : BlockRotation.values()) {
            for (int x = 0; x < SIZE.getX(); x++) {
                for (int z = 0; z < SIZE.getZ(); z++) {
                    BlockPos local = new BlockPos(x, 1, z);
                    BlockPos world = BuildSite.toWorld(ANCHOR, SIZE, rotation, local);

                    assertEquals(local, BuildSite.toLocal(ANCHOR, SIZE, rotation, world),
                            "поворот " + rotation + " у клетки " + local.toShortString());
                }
            }
        }
    }

    @Test
    void oppositeTurnsCancelOut() {
        BlockPos local = new BlockPos(2, 1, 4);

        BlockPos clockwise = BuildSite.toWorld(BlockPos.ORIGIN, SIZE, BlockRotation.CLOCKWISE_90, local);
        BlockPos back = BuildSite.toWorld(BlockPos.ORIGIN,
                BuildSite.rotatedSize(SIZE, BlockRotation.CLOCKWISE_90),
                BlockRotation.COUNTERCLOCKWISE_90, clockwise);

        assertEquals(local, back);
    }
}
