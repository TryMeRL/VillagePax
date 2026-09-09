package com.villagepax.screen;

import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Levels;
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

        /** Здания с таким опознавателем в колонии нет. */
        record NotFound(java.util.UUID building) implements Result {
        }

        /** Улучшать некуда: схемы следующего уровня не существует. */
        record TopLevel(Building building) implements Result {
        }

        /** Здание ещё строится или чинится: улучшать нечего. */
        record Busy(Building building) implements Result {
        }
    }

    /**
     * Докуда игрок вправе отодвинуть голограмму от себя.
     * <p>
     * Место приходит от клиента, поэтому проверяется: «разметить здание
     * на другом конце мира» не должно быть возможно, даже если клиент
     * попросит.
     */
    public static final int PLACEMENT_RANGE = 48;

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
        Result verdict = check(colony, schematicId, anchor, rotation);
        if (!(verdict instanceof Result.Placed allowed)) {
            return verdict;
        }

        Building site = allowed.site();
        manager.update(colony.id(), settlement -> settlement.addBuilding(site));
        return verdict;
    }

    /**
     * Можно ли разметить здесь — <b>ничего не меняя</b>.
     * <p>
     * Нужно голограмме: призрак обязан быть красным там, где строить нельзя,
     * иначе игрок узнаёт об отказе только после подтверждения. Проверка та
     * же самая, что и у заказа, — иначе правила «где можно строить» оказались
     * бы описаны дважды и разошлись бы в первый же день.
     * <p>
     * При успехе возвращается уже собранное здание, но <b>не добавленное
     * в колонию</b>: {@link #place} только кладёт его на место.
     */
    public static Result check(Settlement colony, Identifier schematicId, BlockPos anchor,
                               BlockRotation rotation) {
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
        return new Result.Placed(site, BuildSite.rotatedSize(schematic.size(), rotation),
                schematic.plan().blockCount());
    }

    /** Ключ сообщения об улучшении — для чата. */
    public static String upgradeKey(Result result) {
        if (result instanceof Result.Placed) {
            return "villagepax.screen.upgrade.started";
        }
        if (result instanceof Result.TopLevel) {
            return "villagepax.screen.upgrade.top_level";
        }
        if (result instanceof Result.Busy) {
            return "villagepax.screen.upgrade.busy";
        }
        if (result instanceof Result.Overlaps) {
            return "villagepax.screen.upgrade.no_room";
        }
        return "villagepax.screen.upgrade.gone";
    }

    /**
     * Короткий ключ причины — для подсказки голограммы.
     * <p>
     * Отдельно от сообщений в чат, и намеренно: те несут подстановки
     * (какая схема, какое место, чей след), а подсказка над полосой
     * предметов должна читаться в два слова и без аргументов.
     */
    public static String hologramKey(Result result) {
        if (result instanceof Result.Placed) {
            return "villagepax.hologram.ok";
        }
        if (result instanceof Result.NoSchematic) {
            return "villagepax.hologram.no_schematic";
        }
        if (result instanceof Result.BadName) {
            return "villagepax.hologram.bad_name";
        }
        if (result instanceof Result.OutsideClaim) {
            return "villagepax.hologram.outside";
        }
        return "villagepax.hologram.overlaps";
    }

    /**
     * Улучшить здание до следующего уровня.
     * <p>
     * Улучшение <b>не переносит</b> здание: тот же угол, тот же поворот,
     * план проходится заново по схеме нового уровня. Игроку не приходится
     * выбирать место второй раз, а жители сохраняют привязки — опознаватель
     * здания тот же, и кровати с мастерскими просто раздаются заново, когда
     * стройка кончится.
     * <p>
     * Растёт здание от своего угла, поэтому места ему нужно больше: если
     * рядом уже стоит другое, улучшение отвергается — иначе два здания
     * перетирали бы блоки друг друга бесконечно.
     */
    public static Result upgrade(SettlementManager manager, Settlement colony, UUID buildingId) {
        Building building = colony.building(buildingId).orElse(null);
        if (building == null) {
            return new Result.NotFound(buildingId);
        }
        if (BuildJob.isUnderConstruction(building)) {
            return new Result.Busy(building);
        }

        int next = building.level() + 1;
        Identifier schematicId = new Identifier(building.type().getNamespace(),
                building.type().getPath() + "_lvl" + next);
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return new Result.TopLevel(building);
        }

        // Ратуша переякоряется, остальные растут от своего угла. Разница
        // в том, что у ратуши есть точка, которую держать надо: её блок
        // в середине поселения. Дом такой точки не имеет, и переносить его
        // при улучшении значило бы отобрать у игрока выбор места.
        BlockPos anchor = Levels.isTownHallType(building.type())
                ? centredAnchor(colony.center(), schematic, building.rotation())
                : building.anchor();

        Building clash = overlapping(colony, anchor, building.rotation(), schematic, buildingId);
        if (clash != null) {
            return new Result.Overlaps(clash);
        }

        manager.update(colony.id(), settlement -> settlement.building(buildingId)
                .ifPresent(target -> {
                    target.setLevel(next);
                    target.moveTo(anchor);
                    target.restartBuilding();
                }));

        return new Result.Placed(building, BuildSite.rotatedSize(schematic.size(),
                building.rotation()), schematic.plan().blockCount());
    }

    /**
     * Якорь, при котором заданная точка окажется <b>серединой</b> следа.
     * <p>
     * Нужен ратуше. Её блок стоит в середине поселения, а якорь схемы —
     * это угол: поставь ратушу якорем на свой же блок, и блок окажется
     * в углу здания, а само здание уедет на север-запад от середины
     * деревни. Ровно это игрок и увидел.
     */
    public static BlockPos centredAnchor(BlockPos centre, Schematic schematic,
                                         BlockRotation rotation) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), rotation);
        return centre.add(-(size.getX() / 2), 0, -(size.getZ() / 2));
    }

    /** Есть ли у этого здания следующий уровень — для кнопки в пульте. */
    public static boolean canUpgrade(Building building) {
        Identifier next = new Identifier(building.type().getNamespace(),
                building.type().getPath() + "_lvl" + (building.level() + 1));
        return SchematicLoader.get(next).isPresent();
    }

    /**
     * Наложение следов. Два здания на одном месте перетирали бы блоки друг
     * друга бесконечно, каждое считая, что чинит повреждение.
     */
    private static Building overlapping(Settlement colony, BlockPos anchor, BlockRotation rotation,
                                        Schematic schematic) {
        return overlapping(colony, anchor, rotation, schematic, null);
    }

    private static Building overlapping(Settlement colony, BlockPos anchor, BlockRotation rotation,
                                        Schematic schematic, UUID ignore) {
        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), rotation);

        for (Building existing : colony.buildings()) {
            if (ignore != null && existing.id().equals(ignore)) {
                // Само себя здание не заслоняет: улучшение растёт на месте.
                continue;
            }
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
