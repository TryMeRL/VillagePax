package com.villagepax.sim;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.build.Footing;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Основание колонии как операция над миром.
 * <p>
 * Отделено от предмета-чертежа намеренно: предмет остаётся тонкой оболочкой,
 * которая только показывает сообщения и тратит стак, а сама операция
 * вызывается ещё и из игровых тестов, где никакого игрока нет.
 */
public final class ColonyFounder {

    public static final String KEY_BAD_GROUND = "villagepax.found.bad_ground";

    /** Отказ народу из горы: над головой должен быть камень, а не небо. */
    public static final String KEY_NEEDS_MOUNTAIN = "villagepax.found.needs_mountain";

    /** И народу из крон: под ногами должен быть ствол, а не трава. */
    public static final String KEY_NEEDS_FOREST = "villagepax.found.needs_forest";

    /**
     * Сколько камня спрашивается над местом чертога при основании.
     * <p>
     * Одна колонна и высота зала: настоящий след ратуши здесь ещё
     * не известен, а спросить «есть ли тут вообще гора» — или лес —
     * можно и по одной колонне. Точный след проверит разметка, когда
     * дойдёт до каждого здания.
     */
    private static final Vec3i ROOM_OVERHEAD = new Vec3i(1, 5, 1);

    private ColonyFounder() {
    }

    public static FoundingOutcome foundAt(ServerWorld world, UUID player, Identifier cultureId, BlockPos target) {
        if (!isBuildable(world, target)) {
            return FoundingOutcome.Refused.of(KEY_BAD_GROUND);
        }

        Culture culture = CultureManager.get(cultureId);

        // Народ, который живёт не на земле, на земле и не селится —
        // и сказать об этом надо вслух. Молчаливый отказ был бы худшим
        // из возможных: колония встала бы, ратуша появилась, а дальше
        // не строилось бы ничего и без объяснений. Гному нужен камень
        // над головой, эльфу — ствол под ногами, и на лугу нет ни того,
        // ни другого.
        Footing footing = Footing.of(culture);
        if (culture != null && !footing.holds(world, target, ROOM_OVERHEAD)) {
            return FoundingOutcome.Refused.of(footing.refusalKey());
        }

        SettlementManager manager = SettlementManager.get(world);

        // Одна колония на игрока — во всём мире, а не в измерении: менеджер
        // поселений у каждого измерения свой, и в Незере прежде вставала вторая.
        for (ServerWorld other : world.getServer().getWorlds()) {
            if (other != world) {
                Optional<Settlement> elsewhere = Founding.colonyOf(SettlementManager.get(other), player);
                if (elsewhere.isPresent()) {
                    return FoundingOutcome.Refused.of(Founding.KEY_ALREADY_OWNER, elsewhere.get().name());
                }
            }
        }

        FoundingOutcome outcome = Founding.attempt(
                manager, player, cultureId, culture, target, new Random(world.getRandom().nextLong()));

        if (outcome instanceof FoundingOutcome.Founded founded) {
            raiseTownHall(world, target, founded.settlement(), culture, cultureId);
            settleFirstBuilder(world, founded.settlement(), culture, cultureId);
            // В учёт — до первого надела: и разметка, и стройка спрашивают
            // поселение у менеджера по опознавателю, а не у того, кто его
            // только что создал.
            manager.add(founded.settlement());
            grantFirstHolding(world, manager, founded.settlement(), culture);
        }
        return outcome;
    }

    /**
     * Первый надел колонии: дом и поле стоят с первого дня.
     * <p>
     * Решение заказчика, и оно чинит настоящую беду начала. До сих пор
     * колония начиналась ратушей и одним строителем в чистом поле: спать
     * ему негде, есть нечего, а первый дом надо разметить, завезти в него
     * материалы и дождаться стройки — всё это <b>до того</b>, как в моде
     * случится хоть что-нибудь. Игрок в это время смотрит на пустырь.
     * <p>
     * Даётся ровно то, без чего колония не живёт: <b>крыша и еда</b>.
     * Что именно это за здания, говорят данные — те же {@code starting},
     * по которым встаёт деревня народа. Дальше игрок строит сам, и первая
     * же его постройка обходится ему в полную цену.
     * <p>
     * Строится по общим правилам ({@link Raising}): не нашлось ровного
     * места в кольцах вокруг ратуши — надела не будет. Колония, основанная
     * на скале, обязана выглядеть как колония, основанная на скале.
     */
    private static void grantFirstHolding(ServerWorld world, SettlementManager manager,
                                          Settlement colony, Culture culture) {
        for (Identifier type : BuildingTypes.starting(culture.buildings())) {
            Raising.raise(world, manager, colony, type, Raising.CLOSE_RINGS);
        }
    }

    /** Ратуше нужна твёрдая опора и свободное место — иначе колония повиснет в воздухе. */
    public static boolean isBuildable(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isReplaceable()) {
            return false;
        }
        BlockPos below = pos.down();
        return world.getBlockState(below).isSolidBlock(world, below);
    }

    /**
     * Строитель появляется у ратуши сразу и с телом: чанк основания заведомо
     * загружен, а ждать следующей загрузки чанка значило бы, что игрок
     * основал колонию и никого не увидел.
     */
    private static void settleFirstBuilder(ServerWorld world, Settlement settlement,
                                           Culture culture, Identifier cultureId) {
        Citizen builder = Founding.firstBuilder(cultureId, culture, new Random(world.getRandom().nextLong()));
        // Первый строитель стареет, как все: см. Ages.arrivedGrown.
        com.villagepax.sim.life.Ages.arrivedGrown(builder);
        builder.setPosition(CitizenSpawner.arrival(world, settlement));
        settlement.addCitizen(builder);

        CitizenSpawner.spawnBody(world, settlement, builder);
    }

    /**
     * Ратуша как блок и как здание. Открыто наружу, потому что тем же
     * порядком начинается и деревня народа: разница между колонией и
     * деревней — только во владельце.
     */
    public static void raiseTownHall(ServerWorld world, BlockPos pos, Settlement settlement,
                                     Culture culture, Identifier cultureId) {
        world.setBlockState(pos, ModBlocks.TOWN_HALL.getDefaultState());

        if (world.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            townHall.setSettlementId(settlement.id());
        }

        Identifier buildingType = culture.townHallBuilding()
                .orElseGet(() -> new Identifier(cultureId.getNamespace(), cultureId.getPath() + "/town_hall"));

        // Якорь такой, чтобы блок ратуши оказался серединой её следа,
        // а не углом: иначе улучшение до второго уровня уводит здание
        // на север-запад от середины деревни.
        BlockPos anchor = SchematicLoader.get(new Identifier(buildingType.getNamespace(),
                        buildingType.getPath() + "_lvl1"))
                .map(schematic -> BuildOrders.centredAnchor(pos, schematic, BlockRotation.NONE))
                .orElse(pos);

        settlement.addBuilding(new Building(
                UUID.randomUUID(), buildingType, 1, anchor, BlockRotation.NONE,
                BuildProgress.DONE, List.of()));
    }
}
