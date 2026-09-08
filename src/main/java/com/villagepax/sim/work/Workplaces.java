package com.villagepax.sim.work;

import com.villagepax.core.profession.Profession;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Рабочие места: кто в каком здании работает.
 * <p>
 * Мастерская нужна не всем — это сказано в данных профессии. У билдера
 * и курьера зданий-мастерских ещё нет, и жёсткое требование остановило бы
 * работу, которая уже идёт. Лесорубу и фермеру место нужно: там его роща
 * или поле.
 * <p>
 * Здание находится <b>по соглашению об именовании</b>: профессия
 * {@code villagepax:lumberjack} работает в здании, чей тип кончается
 * на {@code lumberjack}. Это заглушка до типов зданий из датапака — тот же
 * приём, которым культура находит свою ратушу. Когда появится
 * {@code BuildingType}, здание само объявит, кто в нём работает.
 */
public final class Workplaces {

    private Workplaces() {
    }

    /** Рабочие места здания в координатах мира. */
    public static List<BlockPos> stations(Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        return schematic == null
                ? List.of()
                : BuildJob.pointsOfInterest(building, schematic, MarkerKind.WORKSTATION);
    }

    /** Здание, в котором житель работает, если оно ещё существует и готово. */
    public static Optional<Building> of(Settlement settlement, Citizen citizen) {
        return citizen.workplace()
                .flatMap(settlement::building)
                .filter(Building::isOperational);
    }

    public static boolean needsWorkplace(Citizen citizen) {
        return citizen.profession()
                .flatMap(ProfessionManager::get)
                .map(Profession::needsWorkplace)
                .orElse(false);
    }

    /**
     * Раздать жителям мастерские.
     * <p>
     * Существующие привязки сохраняются, пока законны: иначе лесоруб каждый
     * день менял бы рощу и ни одну не довёл бы до конца. Незаконные снимаются —
     * здание могли снести или переназначить.
     */
    public static void assign(ServerWorld world, Settlement settlement) {
        Map<UUID, Integer> capacity = new HashMap<>();
        for (Building building : settlement.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            int slots = stations(building).size();
            if (slots > 0) {
                capacity.put(building.id(), slots);
            }
        }

        Map<UUID, Integer> taken = new HashMap<>();

        for (Citizen citizen : settlement.citizens()) {
            UUID place = citizen.workplace().orElse(null);
            if (place != null && isStillValid(settlement, citizen, place, capacity, taken)) {
                taken.merge(place, 1, Integer::sum);
            } else {
                citizen.setWorkplace(null);
            }
        }

        for (Citizen citizen : settlement.citizens()) {
            if (citizen.workplace().isPresent() || !needsWorkplace(citizen)) {
                continue;
            }
            for (Building building : settlement.buildings()) {
                Integer slots = capacity.get(building.id());
                if (slots == null || taken.getOrDefault(building.id(), 0) >= slots) {
                    continue;
                }
                if (!serves(building, citizen)) {
                    continue;
                }
                citizen.setWorkplace(building.id());
                taken.merge(building.id(), 1, Integer::sum);
                break;
            }
        }
    }

    private static boolean isStillValid(Settlement settlement, Citizen citizen, UUID place,
                                        Map<UUID, Integer> capacity, Map<UUID, Integer> taken) {
        Integer slots = capacity.get(place);
        if (slots == null || taken.getOrDefault(place, 0) >= slots) {
            return false;
        }
        return settlement.building(place).filter(building -> serves(building, citizen)).isPresent();
    }

    /**
     * Обслуживает ли здание эту профессию.
     * <p>
     * Имя места берётся из данных профессии, а не выводится из её имени:
     * фермер работает на <b>ферме</b>, а не на «фермере». Совпадающие
     * имена в датапаке писать не нужно.
     * <p>
     * Сопоставление по имени — заглушка до типов зданий из датапака: когда
     * появится {@code BuildingType}, здание объявит это само.
     */
    private static boolean serves(Building building, Citizen citizen) {
        Identifier profession = citizen.profession().orElse(null);
        if (profession == null) {
            return false;
        }
        String place = ProfessionManager.get(profession)
                .map(known -> known.workplaceOf(profession))
                .orElse(profession.getPath());

        String type = building.type().getPath();
        return type.equals(place) || type.endsWith("/" + place);
    }
}
