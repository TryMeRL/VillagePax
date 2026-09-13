package com.villagepax.screen;

import com.villagepax.core.ModTags;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Housing;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Что делать прямо сейчас — одной строкой в пульте.
 * <p>
 * Написано по главной жалобе на моды этого жанра, и своей игрок подтвердил
 * её слово в слово: непонятен не механизм, а <b>следующий шаг</b>. Пульт
 * показывает десяток чисел — жителей, еду, кровати, стройку, — и все они
 * правдивы, но ни одно не говорит, что делать. Игрок стоит над цифрами
 * и не знает, чего колонии не хватает: дома? поля? рук?
 * <p>
 * <b>Один совет за раз, и всегда самый срочный.</b> Список из пяти
 * «неплохо бы» — это тот же десяток чисел, только словами. Лестница
 * причин выстроена по тому, что <b>убивает колонию быстрее</b>: пустая
 * колония → голод → некому строить → негде спать → нечего есть завтра →
 * стройка без материалов → нечего строить. А выше всего — отряд
 * у ворот: остальные беды успеют подождать до завтра, эта — нет.
 * <p>
 * Совет — это ключ перевода, а не готовая строка: считает его сервер,
 * потому что здесь склад и здания, а показывает клиент на своём языке.
 */
public final class Advice {

    /** Ниже этого запаса еды колония живёт одним днём и заслуживает совета. */
    private static final int THIN_FOOD_DAYS = 2;

    private Advice() {
    }

    /**
     * Самое срочное дело колонии — или пусто, если всё идёт своим ходом.
     * <p>
     * Порядок проверок здесь и есть совет: первая сработавшая и побеждает.
     */
    public static Optional<String> nextStep(ServerWorld world, Settlement colony) {
        if (colony.siege().isPresent()) {
            // Выше пустой колонии: отряд у ворот сделает её такой сегодня,
            // а голод — через неделю. И это единственная беда, у которой
            // есть срок: заплатить можно, пока они идут.
            return Optional.of("villagepax.advice.under_siege");
        }
        if (colony.citizens().isEmpty()) {
            return Optional.of("villagepax.advice.deserted");
        }

        Warehouse warehouse = Warehouse.of(world, colony);
        if (warehouse.containerCount() == 0) {
            return Optional.of("villagepax.advice.no_storage");
        }
        if (!warehouse.hasAny(ModTags.CITIZEN_FOOD)) {
            return Optional.of("villagepax.advice.no_food");
        }
        if (colony.citizens().stream().noneMatch(Advice::isBuilder)) {
            return Optional.of("villagepax.advice.no_builder");
        }
        if (Housing.freeSpots(world, colony) <= 0 && colony.hasRoomForCitizen()) {
            return Optional.of("villagepax.advice.no_beds");
        }
        if (!has(colony, FarmJob.FARMER) && standing(colony, "farm").isEmpty()) {
            return Optional.of("villagepax.advice.no_farm");
        }
        if (colony.buildings().stream().noneMatch(BuildJob::isUnderConstruction)) {
            return Optional.of("villagepax.advice.nothing_building");
        }
        return Optional.empty();
    }

    private static boolean isBuilder(Citizen citizen) {
        return citizen.profession().filter(BuildJob.BUILDER::equals).isPresent();
    }

    private static boolean has(Settlement colony, Identifier profession) {
        return colony.citizens().stream()
                .anyMatch(citizen -> citizen.profession().filter(profession::equals).isPresent());
    }

    /**
     * Стоит ли в колонии готовое здание такой роли.
     * <p>
     * По окончанию имени типа, а не по списку: народ вправе назвать своё
     * поле как угодно в своём пространстве имён, но {@code .../farm} у всех
     * значит поле — на этом же соглашении стоит и выбор мастерских.
     */
    private static Optional<Building> standing(Settlement colony, String role) {
        for (Building building : colony.buildings()) {
            if (building.type().getPath().endsWith("/" + role)
                    && !BuildJob.isUnderConstruction(building)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /** Есть ли у культуры такое здание вообще — на случай народа без полей. */
    public static boolean knows(Settlement colony, String role) {
        return BuildingTypes.all().keySet().stream()
                .anyMatch(type -> type.getPath().endsWith("/" + role));
    }
}
