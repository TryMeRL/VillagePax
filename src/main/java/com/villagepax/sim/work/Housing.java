package com.villagepax.sim.work;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.ModTags;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Natures;
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
     * <p>
     * <b>Первому кровать не нужна.</b> Это не поблажка, а выход из тупика,
     * в который попал заказчик: в его сохранении нашлись колонии с нулём
     * жителей и вечно недостроенным домом. Умерли все — строить кровать
     * стало некому, а без кровати никто не приходил. Колония превращалась
     * в руину навсегда, и игрок сказал про это «строить здания не могу».
     * <p>
     * Тот же случай, что и при основании: первый житель приходит на пустое
     * место и ночует в ратуше. Дальше правило прежнее — кровать нужна.
     */
    public static Optional<Citizen> welcomeNewcomer(ServerWorld world, Settlement settlement,
                                                    Random random) {
        boolean deserted = settlement.citizens().isEmpty();
        if (!settlement.hasRoomForCitizen()
                || (!deserted && freeSpots(world, settlement) <= 0)) {
            return Optional.empty();
        }
        if (!Warehouse.of(world, settlement).hasAny(ModTags.CITIZEN_FOOD)) {
            return Optional.empty();
        }

        Culture culture = CultureManager.get(settlement.culture());
        Citizen newcomer = culture == null
                ? Citizen.newborn("Пришлый", "", settlement.culture(), com.villagepax.sim.Gender.MALE)
                : Founding.newCitizen(settlement.culture(), culture, random);

        // Пришёл работником, а не младенцем: возраст ставится на грань
        // взросления. Оставить его без возраста было бы проще, но тогда
        // колония наполнилась бы бессмертными пришлыми, и вся жизнь
        // свелась бы к детям, умирающим раньше родителей.
        Ages.arrivedGrown(newcomer);
        neededProfession(settlement, newcomer).ifPresent(newcomer::setProfession);
        newcomer.setPosition(Vec3d.ofBottomCenter(settlement.center().up()));
        settlement.addCitizen(newcomer);

        assignBeds(world, settlement);
        Workplaces.assign(world, settlement);
        CitizenSpawner.spawnBody(world, settlement, newcomer);
        return Optional.of(newcomer);
    }

    /**
     * Кем станет пришедший: самая нужная незанятая профессия по приоритету
     * из датапака.
     * <p>
     * В задаче 1.8 это была лестница в коде — «сперва строитель, потом
     * курьер», — и она упиралась в третьего жителя. Теперь порядок задают
     * данные, и новая профессия встраивается в него одним файлом.
     */
    /**
     * Открыт наружу с тех пор, как дети вырастают: выросшему нужно то же
     * самое ремесло, что и пришедшему извне, и считать его вторым способом
     * значило бы завести два разных ответа на один вопрос.
     */
    public static Optional<Identifier> neededProfession(Settlement settlement, Citizen who) {
        Set<Identifier> filled = new HashSet<>();
        for (Citizen citizen : settlement.citizens()) {
            citizen.profession().ifPresent(filled::add);
        }

        for (Identifier profession : ProfessionManager.byHiringPriority()) {
            if (filled.contains(profession)) {
                continue;
            }
            // Нулевой приоритет значит «сама собой не нанимается»: такова
            // старейшина, которую деревня ставит явно, а колонии игрока
            // не нужна вовсе.
            if (ProfessionManager.get(profession)
                    .filter(known -> known.hiringPriority() <= 0).isPresent()) {
                continue;
            }
            // Характер спрашивается тем же правилом, что и кнопка игрока:
            // трус, которому колония назначила стражу автоматически, стоял
            // бы с мечом и бегал от налётчиков — то есть правило
            // существовало бы только для игрока, а это не правило.
            if (Natures.refuses(who, profession)) {
                continue;
            }
            return Optional.of(profession);
        }
        return Optional.empty();
    }
}
