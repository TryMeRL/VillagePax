package com.villagepax.gametest;

import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Hold;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Деревня встаёт такой, как нарисована: при закладке и у строителя ничего
 * не осыпается, а жители появляются не в огне и не в камне.
 * <p>
 * Жалоба заказчика: «морковь у гномов при начальной расстановке выпадает,
 * ибо ты ставишь морковь, потом свет». Посев живёт светом: в темноте
 * грядка при первом же толчке соседа осыпается морковью на пол. Та же
 * природа у лестницы, прислонённой к окну, у пашни под тюком сена
 * и у основателей, появлявшихся в очаге зала, — всё, что ставится
 * раньше того, на чём держится.
 */
public class FoundingTests extends GameTestSupport {

    private static final Identifier DWARF_FARM_TYPE = new Identifier("villagepax", "dwarf/farm");
    private static final Identifier DWARF_FARM_PLAN = new Identifier("villagepax", "dwarf/farm_lvl1");

    /**
     * Сколько тиков камню дают потемнеть. Свет считается не сразу, а отдельным
     * ходом движка; гора, поставленная в тот же тик, что и деревня, ещё
     * «видит» небо, которое только что закрыла, — и проверка прошла бы
     * на том самом поле, которое в игре осыпается.
     */
    private static final int DARKENS = 20;

    /** Сколько ждать после стройки: осыпавшееся успевает упасть. */
    private static final int SETTLES = 20;

    /** Сколько шагов плана строитель делает за тик: стройка идёт, но не за один тик. */
    private static final int STEPS_PER_TICK = 3;

    /** Сколько тиков отпущено строителю на гномье поле. */
    private static final int BUILDS = 150;

    /** Света на грядке, от которого посев растёт: ванильный порог роста. */
    private static final int GROWS = 9;

    /**
     * Чертог, заложенный в тёмной горе, держит всё поле: морковь стоит
     * на каждой грядке, и на полу поля ничего не валяется.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fields_hold", tickLimit = 200)
    public void aHoldFoundedInTheDarkKeepsItsField(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos foot = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> mountain = raiseMountain(world, foot);
        BlockPos floor = Hold.floorUnder(world, foot.getX(), foot.getZ()).orElse(foot);
        Settlement[] hold = new Settlement[1];

        context.runAtTick(DARKENS, () -> {
            if (world.getLightLevel(LightType.SKY, floor.up()) > 0) {
                cleanUpVillage(world, manager, null, floor, mountain);
                context.throwGameTestException("Гора не потемнела за " + DARKENS
                        + " тиков — темноты, в которой осыпается посев, проверка не воспроизводит");
            }
            hold[0] = Villages.found(world, DWARF, floor).orElse(null);
            if (hold[0] == null) {
                cleanUpVillage(world, manager, null, floor, mountain);
                context.throwGameTestException("Чертог не встал в горе: помеха="
                        + whoBlocks(manager, floor));
            }
        });

        context.runAtTick(DARKENS + SETTLES, () -> {
            try {
                Building farm = hold[0].buildings().stream()
                        .filter(building -> building.type().equals(DWARF_FARM_TYPE))
                        .findFirst().orElse(null);
                if (farm == null) {
                    context.throwGameTestException("При закладке чертога поле не встало: "
                            + hold[0].buildings().stream().map(b -> b.type().getPath()).toList());
                    return;
                }
                List<String> bare = bareBeds(world, farm);
                Map<String, Integer> dropped = droppedOn(world, farm);
                if (!bare.isEmpty() || !dropped.isEmpty()) {
                    context.throwGameTestException("Поле чертога осыпалось при закладке: пустых грядок "
                            + bare.size() + " " + bare.stream().limit(4).toList()
                            + ", на полу " + dropped);
                }
            } finally {
                cleanUpVillage(world, manager, hold[0], floor, mountain);
            }
            context.complete();
        });
    }

    /**
     * Строитель, вырубающий поле в тёмной горе, сдаёт его целым: пашня
     * осталась пашней, морковь — на каждой грядке, и светло ей так, что она
     * растёт.
     * <p>
     * Это не та же проверка, что закладка: деревня растёт при игроке, и поле
     * здесь встаёт по шагу за раз, со светом, успевающим посчитаться между
     * шагами. Ломалось оно тут по-другому: пашня ложилась под нетронутый
     * камень будущей грядки и через тик сама становилась землёй.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fields_builder", tickLimit = 300)
    public void aBuilderCarvesTheHoldFieldWhole(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        Schematic plan = SchematicLoader.get(DWARF_FARM_PLAN).orElseThrow();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 2, 6));
        // Порода со всех сторон, и спереди — толще, чем расчищает подход:
        // пробей он наружу, небо заглянуло бы в поле через дверь.
        List<BlockPos> rock = new ArrayList<>();
        for (BlockPos at : BlockPos.iterate(anchor.add(-2, -1, -6),
                anchor.add(plan.size().getX() + 1, plan.size().getY() + 1, plan.size().getZ() + 1))) {
            world.setBlockState(at, Blocks.STONE.getDefaultState());
            rock.add(at.toImmutable());
        }
        // Ратуша далеко: добыча с расчистки ложится в запас стройки,
        // а не в сундук, и на полу валяться нечему, кроме осыпавшегося.
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 30));
        Settlement colony = colonyWithBuilder(world, manager, hall, DWARF);
        Building site = new Building(UUID.randomUUID(), DWARF_FARM_TYPE, 1, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);
        Materials.required(plan).forEach((item, count) ->
                site.stock().add(Registries.ITEM.getId(item), count));

        Runnable cleanUp = () -> {
            demolish(world, site, plan);
            for (BlockPos at : rock) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            manager.remove(colony.id());
        };

        context.runAtTick(DARKENS, () -> {
            if (world.getLightLevel(LightType.SKY, anchor.up()) > 0) {
                cleanUp.run();
                context.throwGameTestException("Камень не потемнел за " + DARKENS + " тиков");
            }
        });
        for (int tick = DARKENS + 1; tick <= DARKENS + BUILDS; tick++) {
            context.runAtTick(tick, () -> BuildJob.advance(world, manager, colony.id(), site.id(),
                    STEPS_PER_TICK));
        }
        context.runAtTick(DARKENS + BUILDS + SETTLES, () -> {
            try {
                if (site.progress() != BuildProgress.DONE) {
                    context.throwGameTestException("Поле не достроилось за " + BUILDS
                            + " тиков: шаг " + site.nextStep() + " из " + plan.plan().steps().size());
                }
                List<String> bare = bareBeds(world, site);
                List<String> soured = new ArrayList<>();
                List<String> dim = new ArrayList<>();
                for (BuildStep step : plan.plan().steps()) {
                    if (!step.placesBlock()) {
                        continue;
                    }
                    BlockPos where = BuildJob.worldPos(site, plan.size(), step.pos());
                    if (plan.blockAt(step.paletteIndex()).isOf(Blocks.FARMLAND)
                            && !world.getBlockState(where).isOf(Blocks.FARMLAND)) {
                        soured.add(where.toShortString() + " " + world.getBlockState(where).getBlock());
                    }
                    if (plan.blockAt(step.paletteIndex()).getBlock() instanceof CropBlock
                            && world.getBaseLightLevel(where, 0) < GROWS) {
                        dim.add(where.toShortString() + " свет " + world.getBaseLightLevel(where, 0));
                    }
                }
                Map<String, Integer> dropped = droppedOn(world, site);
                if (!bare.isEmpty() || !soured.isEmpty() || !dim.isEmpty() || !dropped.isEmpty()) {
                    context.throwGameTestException("Строитель сдал гномье поле не целым: пустых грядок "
                            + bare.size() + " " + bare.stream().limit(3).toList()
                            + ", пашни не стало на " + soured.size() + " " + soured.stream().limit(3).toList()
                            + ", темно для роста на " + dim.size() + " " + dim.stream().limit(3).toList()
                            + ", на полу " + dropped);
                }
            } finally {
                cleanUp.run();
            }
            context.complete();
        });
    }

    /**
     * Каждое здание каждого народа, поставленное разом, стоит так, как
     * нарисовано: каждый блок плана на месте и вправе там стоять, а на полу
     * ничего не валяется.
     * <p>
     * Сеть на весь род ошибок, к которому принадлежит осыпавшаяся морковь:
     * блок, поставленный без опоры, света или не на своей земле, при первом
     * толчке соседа отваливается — сразу или погодя, — и здание стоит
     * с дырой, а под ним предмет. «Вправе стоять» спрашивается у самого
     * блока: посев на голой земле или пашня под тюком сена ещё стоят, но
     * первым же толчком осыпятся. Схем больше сотни, и новая ошибка порядка
     * придёт с новой схемой, поэтому проверяется каждая.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "drawn", tickLimit = 200)
    public void everyBuildingStandsAsDrawn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        // Высоко над землёй, как и проверка входов: соседей по высоте нет.
        BlockPos hall = context.getAbsolutePos(new BlockPos(30, 139, 30));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 141, 0));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = -3; x <= 24; x++) {
            for (int z = -3; z <= 24; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 140, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        List<String> complaints = new ArrayList<>();
        try {
            for (Identifier id : all) {
                Schematic schematic = SchematicLoader.get(id).orElseThrow();
                Identifier type = BuildJob.buildingTypeOf(id).orElse(null);
                if (type == null) {
                    continue;
                }
                Identifier culture = new Identifier(type.getNamespace(), type.getPath().split("/")[0]);
                Settlement colony = colonyWithBuilder(world, manager, hall, culture);
                Building site = new Building(UUID.randomUUID(), type, BuildJob.levelOf(id).orElse(1),
                        anchor, BlockRotation.NONE, BuildProgress.PLANNED, List.of());
                colony.addBuilding(site);
                Materials.required(schematic).forEach((item, count) ->
                        site.stock().add(Registries.ITEM.getId(item), count));
                Set<UUID> lying = new java.util.HashSet<>();
                itemsOn(world, site).forEach(item -> lying.add(item.getUuid()));
                try {
                    BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(),
                            40_000);
                    if (outcome != BuildJob.Outcome.FINISHED) {
                        complaints.add(id + ": не достроилось (" + outcome + ")");
                        continue;
                    }
                    List<String> wrong = new ArrayList<>();
                    for (BuildStep step : schematic.plan().steps()) {
                        if (!step.placesBlock()) {
                            continue;
                        }
                        BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                        net.minecraft.block.BlockState planned = schematic.blockAt(step.paletteIndex());
                        net.minecraft.block.BlockState actual = world.getBlockState(where);
                        if (!actual.isOf(planned.getBlock())) {
                            wrong.add(step.pos().toShortString() + " " + actual.getBlock().getName().getString()
                                    + " вместо " + planned.getBlock().getName().getString());
                        } else if (actual.isIn(com.villagepax.core.ModTags.BUILD_SOWING)
                                ? !world.getBlockState(where.down()).isOf(Blocks.FARMLAND)
                                // Свет посева в такой проверке не спрашивается: здания
                                // встают одно за другим за один тик, и свет от прежнего
                                // ещё не пересчитан. Ровно поэтому посев и ложится
                                // без толчка соседям — а пашня под ним обязана быть.
                                : !actual.canPlaceAt(world, where)) {
                            wrong.add(step.pos().toShortString() + " " + actual.getBlock().getName().getString()
                                    + " не вправе стоять: снизу " + world.getBlockState(where.down())
                                    .getBlock().getName().getString() + ", сверху "
                                    + world.getBlockState(where.up()).getBlock().getName().getString());
                        }
                    }
                    Map<String, Integer> dropped = new TreeMap<>();
                    for (ItemEntity item : itemsOn(world, site)) {
                        if (!lying.contains(item.getUuid())) {
                            dropped.merge(Registries.ITEM.getId(item.getStack().getItem()).getPath(),
                                    item.getStack().getCount(), Integer::sum);
                        }
                    }
                    if (!wrong.isEmpty() || !dropped.isEmpty()) {
                        complaints.add(id + ": " + wrong.size() + " " + wrong.stream().limit(3).toList()
                                + (dropped.isEmpty() ? "" : ", на полу " + dropped));
                    }
                } finally {
                    itemsOn(world, site).forEach(net.minecraft.entity.Entity::discard);
                    demolish(world, site, schematic);
                    manager.remove(colony.id());
                }
            }
            if (!complaints.isEmpty()) {
                context.throwGameTestException("Здания встают не так, как нарисованы:\n  "
                        + String.join("\n  ", complaints));
            }
        } finally {
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /** Предметы на полу здания и вокруг него, в шаге от следа. */
    private static List<ItemEntity> itemsOn(ServerWorld world, Building building) {
        Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElseThrow();
        Vec3i size = BuildSite.rotatedSize(plan.size(), building.rotation());
        Box around = new Box(building.anchor(), building.anchor().add(size)).expand(1);
        return world.getEntitiesByClass(ItemEntity.class, around, entity -> true);
    }

    /**
     * Основатели чертога появляются не в очаге и не вплотную к нему.
     * <p>
     * Над блоком ратуши у гномов горит очаг зала, а жители появлялись ровно
     * над блоком ратуши: их сносило на шаг к самому огню, и курьера в толкотне
     * вдавливало в костёр — в каждом прогоне настоящего рельефа он сгорал
     * в первую минуту деревни.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fields_arrival", tickLimit = 200)
    public void aHoldsFoundersArriveClearOfTheHearth(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos foot = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> mountain = raiseMountain(world, foot);
        BlockPos floor = Hold.floorUnder(world, foot.getX(), foot.getZ()).orElse(foot);
        Settlement[] hold = new Settlement[1];

        context.runAtTick(DARKENS, () -> {
            hold[0] = Villages.found(world, DWARF, floor).orElse(null);
            try {
                if (hold[0] == null) {
                    context.throwGameTestException("Чертог не встал в горе: помеха="
                            + whoBlocks(manager, floor));
                    return;
                }
                List<String> burning = new ArrayList<>();
                for (com.villagepax.sim.Citizen founder : hold[0].citizens()) {
                    com.villagepax.entity.CitizenEntity body = founder.entityUuid()
                            .map(world::getEntity)
                            .filter(com.villagepax.entity.CitizenEntity.class::isInstance)
                            .map(com.villagepax.entity.CitizenEntity.class::cast).orElse(null);
                    if (body == null) {
                        burning.add(founder.fullName() + " без тела");
                        continue;
                    }
                    BlockPos feet = body.getBlockPos();
                    boolean fire = false;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            fire |= com.villagepax.sim.Hazards.standingHurts(world, feet.add(dx, 0, dz));
                        }
                    }
                    boolean walled = !world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
                    if (fire || walled) {
                        burning.add(founder.fullName() + " в " + feet.toShortString()
                                + (fire ? " у огня" : "") + (walled ? " в камне" : ""));
                    }
                }
                if (!burning.isEmpty()) {
                    context.throwGameTestException("Основатели чертога появились в опасном месте: "
                            + burning);
                }
            } finally {
                cleanUpVillage(world, manager, hold[0], floor, mountain);
            }
            context.complete();
        });
    }

    /**
     * В ратуше любого народа и уровня житель появляется на свободном полу,
     * не в огне и не рядом с ним.
     * <p>
     * Над блоком ратуши у пони, гномов и эльфов горит очаг, у норманнов
     * и майя лежит ковёр, у северян — камень очага. Пришлый, рождённый
     * и вернувшийся из похода появлялись ровно над блоком ратуши — то есть
     * в огне или в камне, как только колония поднимала ратушу.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "fields_townhalls", tickLimit = 100)
    public void everyTownHallHasAPlaceToArrive(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(12, 141, 12));
        List<String> complaints = new ArrayList<>();
        List<Identifier> halls = SchematicLoader.ids().stream()
                .filter(id -> id.getPath().contains("/town_hall_lvl"))
                .sorted(java.util.Comparator.comparing(Identifier::toString)).toList();
        for (Identifier id : halls) {
            Identifier type = BuildJob.buildingTypeOf(id).orElseThrow();
            Identifier culture = new Identifier(type.getNamespace(), type.getPath().split("/")[0]);
            Schematic first = SchematicLoader.get(new Identifier(type.getNamespace(),
                    type.getPath() + "_lvl1")).orElseThrow();
            Schematic schematic = SchematicLoader.get(id).orElseThrow();
            BlockPos anchor = com.villagepax.screen.BuildOrders.centredAnchor(centre, first,
                    BlockRotation.NONE);
            Settlement colony = colonyWithBuilder(world, manager, centre, culture);
            Building site = new Building(UUID.randomUUID(), type, BuildJob.levelOf(id).orElse(1),
                    anchor, BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);
            Materials.required(schematic).forEach((item, count) ->
                    site.stock().add(Registries.ITEM.getId(item), count));
            try {
                if (BuildJob.advance(world, manager, colony.id(), site.id(), 40_000)
                        != BuildJob.Outcome.FINISHED) {
                    complaints.add(id + ": не достроилась");
                    continue;
                }
                BlockPos feet = BlockPos.ofFloored(com.villagepax.entity.CitizenSpawner.arrival(world, colony));
                boolean fire = false;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        fire |= com.villagepax.sim.Hazards.standingHurts(world, feet.add(dx, 0, dz));
                    }
                }
                boolean walled = !world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                        || !world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty();
                boolean floating = !world.getBlockState(feet.down()).isSolidBlock(world, feet.down());
                if (fire || walled || floating) {
                    complaints.add(id + ": житель появится в " + feet.subtract(centre).toShortString()
                            + " от ратуши" + (fire ? ", у огня" : "") + (walled ? ", в стене" : "")
                            + (floating ? ", над пустотой" : ""));
                }
            } finally {
                demolish(world, site, schematic);
                manager.remove(colony.id());
                world.setBlockState(centre, Blocks.AIR.getDefaultState());
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Ратуши, где житель появляется в опасном месте:\n  "
                    + String.join("\n  ", complaints));
        }
        context.complete();
    }

    /**
     * Житель обходит рыхлый снег, а не идёт по нему.
     * <p>
     * В настоящем прогоне курьер пони замёрз насмертью в двух шагах от избы:
     * рыхлый снег выглядит сугробом, ваниль считает клетку над ним обычной
     * дорогой, а провалившийся изнутри пути уже не строит. Поперёк площадки
     * лежит полоса снега с проходом у края — путь обязан лечь в обход.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "snow")
    public void aCitizenWalksAroundPowderSnow(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos ground = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        List<BlockPos> laid = new ArrayList<>();
        try {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos at = ground.add(dx, 0, dz);
                    boolean snow = dz == 0 && dx <= 2;
                    world.setBlockState(at, snow ? Blocks.POWDER_SNOW.getDefaultState()
                            : Blocks.STONE.getDefaultState());
                    world.setBlockState(at.down(), Blocks.STONE.getDefaultState());
                    laid.add(at);
                    laid.add(at.down());
                }
            }
            com.villagepax.sim.Citizen walker = hireWithBody(world, colony, BuildJob.BUILDER,
                    ground.add(0, 1, -3));
            com.villagepax.entity.CitizenEntity body = (com.villagepax.entity.CitizenEntity)
                    world.getEntity(walker.entityUuid().orElseThrow());
            // Только что появившееся тело ещё не стоит на земле, и навигация
            // без опоры пути не ищет вовсе.
            body.setOnGround(true);
            net.minecraft.entity.ai.pathing.Path path = body.getNavigation()
                    .findPathTo(ground.add(0, 1, 3), 0);
            if (path == null || !path.reachesTarget()) {
                context.throwGameTestException("Путь в обход снега не найден: " + path);
                return;
            }
            for (int i = 0; i < path.getLength(); i++) {
                BlockPos node = path.getNodePos(i);
                if (world.getBlockState(node.down()).isOf(Blocks.POWDER_SNOW)
                        || world.getBlockState(node).isOf(Blocks.POWDER_SNOW)) {
                    context.throwGameTestException("Путь ведёт по рыхлому снегу через "
                            + node.subtract(ground).toShortString());
                }
            }
        } finally {
            for (BlockPos at : laid) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /**
     * Провалившийся в рыхлый снег выбирается наверх, на твёрдый край.
     * <p>
     * Рефлекс «выйти из беды» искал опору на своей высоте и на блок ниже:
     * с забора сходят вниз. Из снега выходят вверх — утонувшему по пояс
     * твёрдый край ямы приходится на блок выше ног, и рефлекс его не видел.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "snow")
    public void aCitizenClimbsOutOfPowderSnow(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos ground = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        List<BlockPos> laid = new ArrayList<>();
        try {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos at = ground.add(dx, 0, dz);
                    boolean pit = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                    world.setBlockState(at, pit ? Blocks.POWDER_SNOW.getDefaultState()
                            : Blocks.STONE.getDefaultState());
                    world.setBlockState(at.down(), Blocks.STONE.getDefaultState());
                    laid.add(at);
                    laid.add(at.down());
                }
            }
            com.villagepax.sim.Citizen sunk = hireWithBody(world, colony, BuildJob.BUILDER,
                    ground.add(0, 1, 0));
            com.villagepax.entity.CitizenEntity body = (com.villagepax.entity.CitizenEntity)
                    world.getEntity(sunk.entityUuid().orElseThrow());
            body.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5,
                    0f, 0f);
            if (!world.getBlockState(body.getBlockPos()).isOf(Blocks.POWDER_SNOW)) {
                context.throwGameTestException("Посылка теста не выполнена: житель не в снегу, а в "
                        + world.getBlockState(body.getBlockPos()).getBlock());
            }
            if (!body.stepOutOfTrouble()) {
                context.throwGameTestException("Житель остался тонуть в рыхлом снегу");
            }
            BlockPos feet = body.getBlockPos();
            if (com.villagepax.sim.Hazards.standingHurts(world, feet)
                    || !world.getBlockState(feet.down()).isSolidBlock(world, feet.down())) {
                context.throwGameTestException("Житель выбрался, но не на твёрдое: под ним "
                        + world.getBlockState(feet.down()).getBlock());
            }
        } finally {
            for (BlockPos at : laid) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /**
     * Дом, поднятый разом на пасущихся курах, их не замуровывает.
     * <p>
     * После закладки норманнской деревни на холмах на земле лежали перья
     * и куриные тушки: стройка отодвигала от кладки жителей, а скотину —
     * нет, и курица, оказавшаяся в стене, задыхалась.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "flock", tickLimit = 100)
    public void aBuildingRaisedOnAFlockBuriesNoHen(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = SchematicLoader.get(HOUSE_SCHEMATIC).orElseThrow();
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 12));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = new Building(UUID.randomUUID(), NORMAN_HOUSE_TYPE, 1, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);
        Materials.required(plan).forEach((item, count) ->
                site.stock().add(Registries.ITEM.getId(item), count));
        List<net.minecraft.entity.passive.ChickenEntity> flock = new ArrayList<>();
        for (int x = 0; x < plan.size().getX(); x += 2) {
            for (int z = 0; z < plan.size().getZ(); z += 2) {
                net.minecraft.entity.passive.ChickenEntity hen = net.minecraft.entity.EntityType.CHICKEN
                        .create(world);
                hen.refreshPositionAndAngles(anchor.getX() + x + 0.5, anchor.getY() + 1,
                        anchor.getZ() + z + 0.5, 0f, 0f);
                world.spawnEntity(hen);
                flock.add(hen);
            }
        }
        if (BuildJob.advance(world, manager, colony.id(), site.id(), 40_000)
                != BuildJob.Outcome.FINISHED) {
            flock.forEach(net.minecraft.entity.Entity::discard);
            demolish(world, site, plan);
            manager.remove(colony.id());
            context.throwGameTestException("Дом не достроился");
            return;
        }
        context.runAtTick(SETTLES, () -> {
            try {
                List<String> buried = new ArrayList<>();
                for (net.minecraft.entity.passive.ChickenEntity hen : flock) {
                    BlockPos at = hen.getBlockPos();
                    if (!hen.isAlive() || hen.isInsideWall()
                            || !world.getBlockState(at).getCollisionShape(world, at).isEmpty()) {
                        buried.add(at.subtract(anchor).toShortString()
                                + (hen.isAlive() ? " в " + world.getBlockState(at).getBlock().getName().getString()
                                : " погибла"));
                    }
                }
                if (!buried.isEmpty()) {
                    context.throwGameTestException("Дом замуровал кур: " + buried.size() + " из "
                            + flock.size() + " " + buried.stream().limit(4).toList());
                }
            } finally {
                flock.forEach(net.minecraft.entity.Entity::discard);
                world.getEntitiesByClass(ItemEntity.class, new Box(anchor).expand(12), item -> true)
                        .forEach(net.minecraft.entity.Entity::discard);
                demolish(world, site, plan);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Деревня, заложенная посреди пасущейся стаи, не замуровывает ни одной
     * курицы — ни домом, ни колодцем, ни фонарём, ни откосом.
     * <p>
     * Дом отводит скотину из кладки сам; всё прочее, что закладка кладёт
     * вокруг, — улица, колодец, крыльцо, откос — клало блоки прямо на кур.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "flock_village", tickLimit = 100)
    public void aVillageFoundedAmidAFlockBuriesNoHen(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 1, 16));
        List<net.minecraft.entity.passive.ChickenEntity> flock = new ArrayList<>();
        for (int x = -30; x <= 46; x += 3) {
            for (int z = -30; z <= 46; z += 3) {
                BlockPos column = context.getAbsolutePos(new BlockPos(x, 0, z));
                int top = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,
                        column.getX(), column.getZ());
                net.minecraft.entity.passive.ChickenEntity hen = net.minecraft.entity.EntityType.CHICKEN
                        .create(world);
                hen.refreshPositionAndAngles(column.getX() + 0.5, top, column.getZ() + 0.5, 0f, 0f);
                world.spawnEntity(hen);
                flock.add(hen);
            }
        }
        // Кого мир видит сейчас, до закладки: курица в невидимой секции чанка
        // для деревни не существует, и вывести её из-под земли некому.
        Set<java.util.UUID> seen = new java.util.HashSet<>();
        world.getEntitiesByClass(net.minecraft.entity.passive.ChickenEntity.class,
                new Box(centre).expand(48, 16, 48), hen -> true).forEach(hen -> seen.add(hen.getUuid()));
        Settlement village = Villages.found(world, NORMAN, centre).orElse(null);
        if (village == null) {
            flock.forEach(net.minecraft.entity.Entity::discard);
            context.throwGameTestException("Деревня не встала: помеха=" + whoBlocks(manager, centre));
            return;
        }
        context.runAtTick(SETTLES, () -> {
            try {
                List<String> buried = new ArrayList<>();
                for (net.minecraft.entity.passive.ChickenEntity hen : flock) {
                    // Курица, которой мир не видел до закладки, — в секции
                    // чанка, где сущности не ведутся: увидеть и отвести её
                    // закладка не может, и спрашивать с неё нечего. Прежде
                    // проверка считала и их — и падала, когда поле ложилось
                    // на край прогрузки.
                    if (!seen.contains(hen.getUuid())) {
                        continue;
                    }
                    if (!hen.isAlive() || hen.isInsideWall()) {
                        BlockPos at = hen.getBlockPos();
                        buried.add(at.subtract(centre).toShortString() + " "
                                + (hen.isAlive() ? world.getBlockState(at).getBlock().getName().getString()
                                : "погибла"));
                    }
                }
                if (!buried.isEmpty()) {
                    context.throwGameTestException("Деревня замуровала кур: " + buried.size() + " из "
                            + flock.size() + " (видел мир " + seen.size() + ") "
                            + buried.stream().limit(5).toList());
                }
            } finally {
                flock.forEach(net.minecraft.entity.Entity::discard);
                cleanUpVillage(world, manager, village, centre, List.of());
                world.getEntitiesByClass(ItemEntity.class, new Box(centre).expand(60, 20, 60),
                        item -> true).forEach(net.minecraft.entity.Entity::discard);
            }
            context.complete();
        });
    }

    /** Грядки поля, на которых посева нет, — в координатах мира. */
    private static List<String> bareBeds(ServerWorld world, Building farm) {
        Schematic plan = SchematicLoader.get(BuildJob.schematicId(farm)).orElseThrow();
        List<String> bare = new ArrayList<>();
        for (BuildStep step : plan.plan().steps()) {
            if (!step.placesBlock() || !(plan.blockAt(step.paletteIndex()).getBlock() instanceof CropBlock)) {
                continue;
            }
            BlockPos where = BuildJob.worldPos(farm, plan.size(), step.pos());
            if (!(world.getBlockState(where).getBlock() instanceof CropBlock)) {
                bare.add(where.toShortString() + " " + world.getBlockState(where).getBlock()
                        + " на " + world.getBlockState(where.down()).getBlock());
            }
        }
        return bare;
    }

    /**
     * Что валяется на полу самого поля: предмет и сколько штук.
     * <p>
     * Только в следе: мир проверок общий, и вокруг лежит чужое — яблоки
     * с листвы, осыпающейся после соседней проверки, чужие доспехи.
     */
    private static Map<String, Integer> droppedOn(ServerWorld world, Building farm) {
        Schematic plan = SchematicLoader.get(BuildJob.schematicId(farm)).orElseThrow();
        Vec3i size = BuildSite.rotatedSize(plan.size(), farm.rotation());
        Box footprint = new Box(farm.anchor(), farm.anchor().add(size));
        Map<String, Integer> dropped = new TreeMap<>();
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, footprint, entity -> true)) {
            dropped.merge(Registries.ITEM.getId(item.getStack().getItem()).getPath(),
                    item.getStack().getCount(), Integer::sum);
        }
        return dropped;
    }
}
