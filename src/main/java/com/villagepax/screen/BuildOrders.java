package com.villagepax.screen;

import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.List;
import java.util.UUID;

/**
 * Заказ здания: единственный путь, которым стройка попадает в колонию.
 * <p>
 * Проверки живут здесь, а не в команде, потому что заказчиков стало двое:
 * отладочная команда и экран ратуши. Кнопка в интерфейсе, проверяющая
 * меньше команды, была бы дыркой в обход неё, а два списка проверок
 * разошлись бы в первый же день.
 * <p>
 * Причины отказа — записи, а не текст: экрану их надо показать
 * локализованно, команде — написать в чат, и каждой нужны свои
 * подробности. Строку собирает вызывающий, а не источник отказа.
 */
public final class BuildOrders {

    /** Имена поворотов — общий словарь команды, экрана и сети. */
    public static final List<String> ROTATIONS = List.of("none", "cw90", "cw180", "ccw90");

    private BuildOrders() {
    }

    /**
     * Чем кончился заказ.
     * <p>
     * Закрытый интерфейс, а не перечисление с полями: у каждого отказа свои
     * подробности, и записи позволяют не хранить пустые поля «на случай
     * другой ветки».
     */
    public sealed interface Result {

        /** Стройка размечена. */
        record Placed(Building site, Vec3i footprint, int blocks) implements Result {
        }

        /** Такой схемы нет — опечатка или чужой датапак. */
        record NoSchematic(Identifier schematic) implements Result {
        }

        /** Имя схемы не кончается на {@code _lvl<число>}, тип здания не вывести. */
        record BadName(Identifier schematic) implements Result {
        }

        /** Место вне границ колонии. */
        record OutsideClaim(BlockPos anchor) implements Result {
        }

        /** След пересекается с уже размеченным зданием. */
        record Overlaps(Building clash) implements Result {
        }
    }

    /**
     * Разметить стройку. При отказе колония не меняется.
     * <p>
     * Культура здесь не проверяется намеренно: список зданий культуры решает,
     * что <b>предложить</b> в экране, а не что физически допустимо. Норманнский
     * дом в колонии майя не ломает ничего, кроме вида, и запрещать это должен
     * интерфейс, а не движок.
     */
    public static Result place(SettlementManager manager, Settlement colony, Identifier schematicId,
                               BlockPos anchor, BlockRotation rotation) {
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return new Result.NoSchematic(schematicId);
        }

        Identifier type = BuildJob.buildingTypeOf(schematicId).orElse(null);
        if (type == null) {
            return new Result.BadName(schematicId);
        }
        int level = BuildJob.levelOf(schematicId).orElse(1);

        if (!colony.claims(anchor)) {
            return new Result.OutsideClaim(anchor);
        }

        Building clash = overlapping(colony, anchor, rotation, schematic);
        if (clash != null) {
            return new Result.Overlaps(clash);
        }

        Building site = new Building(UUID.randomUUID(), type, level, anchor, rotation,
                BuildProgress.PLANNED, List.of());
        manager.update(colony.id(), settlement -> settlement.addBuilding(site));

        return new Result.Placed(site, BuildSite.rotatedSize(schematic.size(), rotation),
                schematic.plan().blockCount());
    }

    /**
     * Наложение следов. Два здания на одном месте перетирали бы блоки друг
     * друга бесконечно, каждое считая, что чинит повреждение.
     */
    private static Building overlapping(Settlement colony, BlockPos anchor, BlockRotation rotation,
                                        Schematic schematic) {
        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), rotation);

        for (Building existing : colony.buildings()) {
            Schematic other = SchematicLoader.get(BuildJob.schematicId(existing)).orElse(null);
            if (other == null) {
                continue;
            }
            Vec3i otherFootprint = BuildSite.rotatedSize(other.size(), existing.rotation());
            if (boxesOverlap(anchor, footprint, existing.anchor(), otherFootprint)) {
                return existing;
            }
        }
        return null;
    }

    private static boolean boxesOverlap(BlockPos a, Vec3i sizeA, BlockPos b, Vec3i sizeB) {
        return a.getX() < b.getX() + sizeB.getX() && b.getX() < a.getX() + sizeA.getX()
                && a.getY() < b.getY() + sizeB.getY() && b.getY() < a.getY() + sizeA.getY()
                && a.getZ() < b.getZ() + sizeB.getZ() && b.getZ() < a.getZ() + sizeA.getZ();
    }

    /** Поворот по имени из словаря. Неизвестное имя — без поворота. */
    public static BlockRotation rotation(String name) {
        return switch (name) {
            case "cw90" -> BlockRotation.CLOCKWISE_90;
            case "cw180" -> BlockRotation.CLOCKWISE_180;
            case "ccw90" -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    public static String nameOf(BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> "cw90";
            case CLOCKWISE_180 -> "cw180";
            case COUNTERCLOCKWISE_90 -> "ccw90";
            default -> "none";
        };
    }
}
