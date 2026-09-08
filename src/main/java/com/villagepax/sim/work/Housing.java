package com.villagepax.sim.work;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.ModTags;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Жильё: где жители спят и откуда берутся новые.
 * <p>
 * Место для сна — это <b>или настоящая кровать, или маркер</b>. Кровать
 * в Minecraft занимает две позиции, а маркер схемы одну, поэтому в схемах
 * норманнов стоят настоящие кровати. Маркер при этом не стал бесполезным:
 * место без кровати тоже считается местом для сна — походной подстилкой,
 * и автор датапака может обойтись одной позицией.
 */
public final class Housing {

    /** Приток: один житель за игровой день, если есть куда и чем кормить. */
    public static final int ARRIVALS_PER_DAY = 1;

    private Housing() {
    }

    /**
     * Все места для сна в поселении.
     * <p>
     * Кандидаты берутся из плана схемы, а не обходом объёма зданий: обход
     * пятиста позиций на каждое здание ради двух кроватей — расточительство,
     * а план и так посчитан. Мир при этом всё равно спрашивается — кровать
     * могли сломать.
     */
    public static List<BlockPos> sleepingSpots(ServerWorld world, Settlement settlement) {
        List<BlockPos> spots = new ArrayList<>();

        for (Building building : settlement.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            collectBeds(world, building, schematic, spots);
            collectBedrolls(world, building, schematic, spots);
        }
        return spots;
    }

    private static void collectBeds(ServerWorld world, Building building, Schematic schematic,
                                    List<BlockPos> spots) {
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockState planned = schematic.blockAt(step.paletteIndex());

            // Только головная половина: иначе одна кровать дала бы два места.
            // Поворот здания меняет направление, но не половину.
            if (!planned.isIn(BlockTags.BEDS)
                    || !planned.contains(BedBlock.PART)
                    || planned.get(BedBlock.PART) != BedPart.HEAD) {
                continue;
            }

            BlockPos where = BuildJob.worldPos(building, schematic.size(), step.pos());
            if (world.getBlockState(where).isIn(BlockTags.BEDS)) {
                spots.add(where);
            }
        }
    }

    private static void collectBedrolls(ServerWorld world, Building building, Schematic schematic,
                                        List<BlockPos> spots) {
        for (BlockPos marker : BuildJob.pointsOfInterest(building, schematic, MarkerKind.BED)) {
            if (world.getBlockState(marker).isAir()) {
                spots.add(marker);
            }
        }
    }

    /**
     * Раздать бездомным жителям свободные места.
     * <p>
     * Заодно отбирает места у тех, чья кровать исчезла: иначе житель ходил бы
     * спать в воздух и числился бы устроенным.
     */
    public static void assignBeds(ServerWorld world, Settlement settlement) {
        List<BlockPos> spots = sleepingSpots(world, settlement);
        Set<BlockPos> taken = new HashSet<>();

        for (Citizen citizen : settlement.citizens()) {
            BlockPos bed = citizen.bed().orElse(null);
            if (bed != null && spots.contains(bed) && taken.add(bed)) {
                continue;
            }
            citizen.setBed(null);
        }

        for (Citizen citizen : settlement.citizens()) {
            if (!citizen.isHomeless()) {
                continue;
            }
            for (BlockPos spot : spots) {
                if (taken.add(spot)) {
                    citizen.setBed(spot);
                    break;
                }
            }
        }
    }

    public static int freeSpots(ServerWorld world, Settlement settlement) {
        int occupied = 0;
        for (Citizen citizen : settlement.citizens()) {
            if (!citizen.isHomeless()) {
                occupied++;
            }
        }
        return Math.max(0, sleepingSpots(world, settlement).size() - occupied);
    }

    /**
     * Приток жителей: один за игровой день, если есть свободное место и еда.
     * <p>
     * Решение заказчика. Еда в условии не для строгости, а чтобы колония
     * не росла в голод: приходящий житель сразу начал бы голодать и уходить,
     * и игрок видел бы вереницу людей, приходящих умирать.
     */
    public static Optional<Citizen> welcomeNewcomer(ServerWorld world, Settlement settlement,
                                                    Random random) {
        if (!settlement.hasRoomForCitizen() || freeSpots(world, settlement) <= 0) {
            return Optional.empty();
        }
        if (!Warehouse.of(world, settlement).hasAny(ModTags.CITIZEN_FOOD)) {
            return Optional.empty();
        }

        Culture culture = CultureManager.get(settlement.culture());
        Citizen newcomer = culture == null
                ? Citizen.newborn("Пришлый", "", settlement.culture(), com.villagepax.sim.Gender.MALE)
                : Founding.newCitizen(settlement.culture(), culture, random);

        neededProfession(settlement).ifPresent(newcomer::setProfession);
        newcomer.setPosition(Vec3d.ofBottomCenter(settlement.center().up()));
        settlement.addCitizen(newcomer);

        assignBeds(world, settlement);
        CitizenSpawner.spawnBody(world, settlement, newcomer);
        return Optional.of(newcomer);
    }

    /**
     * Кем станет пришедший. Заглушка до задачи 1.9, где профессии станут
     * данными: сперва строитель, потом курьер, дальше без профессии.
     */
    private static Optional<Identifier> neededProfession(Settlement settlement) {
        boolean hasBuilder = false;
        boolean hasCourier = false;

        for (Citizen citizen : settlement.citizens()) {
            Identifier profession = citizen.profession().orElse(null);
            hasBuilder |= BuildJob.BUILDER.equals(profession);
            hasCourier |= HaulJob.COURIER.equals(profession);
        }

        if (!hasBuilder) {
            return Optional.of(BuildJob.BUILDER);
        }
        if (!hasCourier) {
            return Optional.of(HaulJob.COURIER);
        }
        return Optional.empty();
    }
}
