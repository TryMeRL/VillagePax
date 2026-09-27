package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.festival.ContestKind;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.festival.HidingPlaces;
import com.villagepax.sim.festival.Hunt;
import com.villagepax.sim.festival.Match;
import com.villagepax.sim.festival.Matches;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Поиск: где прячутся вещицы, кто их находит и как за поиском убирают.
 * <p>
 * Ярмарка стоит на своём лугу — слое травы поверх земли мира: вещицы
 * прячутся на земле, а землю мира уборка не трогает. Поиск прячет вещицы
 * на 24 блока вокруг, то есть и у соседей по партии, поэтому проверка,
 * живущая дольше одного тика, стоит в своей партии.
 */
public class HuntTests extends GameTestSupport {

    private static final Identifier TOKEN = Registries.BLOCK.getId(ModBlocks.FESTIVAL_TOKEN);

    private static Hunt startHunt(TestContext context, ServerWorld world, FairGround ground,
                                  PlayerEntity player) {
        return (Hunt) startContest(context, world, ground, player, ContestKind.HUNT);
    }

    /**
     * Вещицы прячутся там, где встанет человек, — не в доме, не на самой
     * ярмарке и не на улице, не кучей и не дальше 24 блоков от сердца.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hunt")
    public void huntTokensHideWhereOneCanStand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);
        Building house = null;
        List<BlockPos> road = new ArrayList<>();
        try {
            house = standUp(context, world, manager, ground.village(), HOUSE_TYPE,
                    context.getAbsolutePos(new BlockPos(20, 1, 4)));
            for (int x = 0; x < 32; x++) {
                BlockPos tile = context.getAbsolutePos(new BlockPos(x, 1, 22));
                world.setBlockState(tile, Blocks.DIRT_PATH.getDefaultState());
                road.add(tile);
            }
            List<BlockPos> spots = HidingPlaces.find(world, ground.village(), ground.place(), 10,
                    world.getRandom());
            List<String> wrong = new ArrayList<>();
            if (spots.size() < 5) {
                wrong.add("мест " + spots.size() + " — ждали хотя бы пять");
            }
            Schematic fairPlan = schematic(context, NORMAN_FAIRGROUND_PLAN);
            BlockPos heart = ground.place().heart();
            for (BlockPos spot : spots) {
                if (!HidingPlaces.standable(world, spot)) {
                    wrong.add(spot + ": там не встать");
                }
                if (inside(spot, house, housePlan)) {
                    wrong.add(spot + ": в доме");
                }
                if (inside(spot, ground.fair(), fairPlan)) {
                    wrong.add(spot + ": на самой ярмарке");
                }
                if (world.getBlockState(spot.down()).isOf(Blocks.DIRT_PATH)) {
                    wrong.add(spot + ": на улице");
                }
                double dx = spot.getX() - heart.getX();
                double dz = spot.getZ() - heart.getZ();
                if (dx * dx + dz * dz > HidingPlaces.RADIUS * HidingPlaces.RADIUS) {
                    wrong.add(spot + ": дальше 24 блоков от сердца");
                }
                for (BlockPos other : spots) {
                    double ox = spot.getX() - other.getX();
                    double oz = spot.getZ() - other.getZ();
                    if (other != spot && ox * ox + oz * oz < HidingPlaces.APART * HidingPlaces.APART) {
                        wrong.add(spot + " и " + other + ": ближе четырёх блоков");
                    }
                }
            }
            if (!wrong.isEmpty()) {
                context.throwGameTestException("Места поиска:\n  " + String.join("\n  ", wrong));
            }
        } finally {
            if (house != null) {
                demolish(world, house, housePlan);
            }
            road.forEach(tile -> world.setBlockState(tile, Blocks.AIR.getDefaultState()));
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** В следе ли здания эта колонна. */
    private static boolean inside(BlockPos spot, Building building, Schematic plan) {
        BlockPos corner = building.anchor();
        return spot.getX() >= corner.getX() && spot.getX() < corner.getX() + plan.size().getX()
                && spot.getZ() >= corner.getZ() && spot.getZ() < corner.getZ() + plan.size().getZ();
    }

    /** Нашёл все вещицы — поиск кончился, вещиц нет, а у игрока три ленты. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hunt")
    public void everyTokenFoundEndsTheHuntWithRibbons(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            PlayerEntity player = playerAt(context, ground.place().counter());
            Hunt hunt = startHunt(context, world, ground, player);
            List<BlockPos> tokens = List.copyOf(hunt.tokens());
            if (tokens.isEmpty() || tokens.stream().anyMatch(at ->
                    !world.getBlockState(at).isOf(ModBlocks.FESTIVAL_TOKEN))) {
                context.throwGameTestException("Вещицы не стоят: " + tokens);
            }
            pastTheCountdown(world);
            tokens.forEach(at -> Matches.collect(world, at, player));
            if (hunt.scoreOf(player.getUuid()) != tokens.size()) {
                context.throwGameTestException("Счёт " + hunt.scoreOf(player.getUuid()) + " за "
                        + tokens.size() + " вещиц");
            }
            Matches.tick(world);
            if (Matches.at(ground.village().id()).isPresent()) {
                context.throwGameTestException("Всё найдено, а поиск идёт");
            }
            if (tokens.stream().anyMatch(at -> world.getBlockState(at).isOf(ModBlocks.FESTIVAL_TOKEN))) {
                context.throwGameTestException("Найденные вещицы остались в мире");
            }
            int ribbons = player.getInventory().count(ModFestivalItems.FESTIVAL_RIBBON);
            if (ribbons != 3) {
                context.throwGameTestException("Первому — три ленты, а у него " + ribbons);
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /**
     * Ребёнок-соперник замечает вещицу в шести блоках, идёт к ней и находит.
     * <p>
     * Стоит он в пяти блоках — видно, но не дотянуться: находка — это шаги.
     * Живёт двести с лишним тиков — поэтому в своей партии.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hunt_child", tickLimit = 400)
    public void aChildFindsATokenToo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        Citizen child = Citizen.newborn("Мари", "", NORMAN, Gender.FEMALE);
        Hunt hunt;
        PlayerEntity player;
        CitizenEntity body;
        BlockPos token;
        try {
            child.setPosition(Vec3d.ofBottomCenter(ground.place().counter()));
            ground.village().addCitizen(child);
            CitizenSpawner.spawnBody(world, ground.village(), child);
            player = playerAt(context, ground.place().counter());
            hunt = startHunt(context, world, ground, player);
            if (!hunt.isRival(child.id())) {
                context.throwGameTestException("Ребёнок у ярмарки не стал соперником поиска");
            }
            // Вещица и ребёнок — посреди своего луга и на одной высоте: луг лежит
            // на блок выше земли мира, край его — уступ в два блока, и вещица
            // за краем была бы проверкой уступа, а не поиска.
            BlockPos found = null;
            BlockPos near = null;
            for (BlockPos candidate : hunt.tokens()) {
                if (!onTheMeadow(context, candidate) || !world.shouldTickEntity(candidate)) {
                    continue;
                }
                for (int[] step : new int[][]{{5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
                    BlockPos at = candidate.add(step[0], 0, step[1]);
                    // Туда, где сущности живут: шагнув телом в чанк, где они
                    // не живут, житель теряет связь с записью (снятие с учёта
                    // возвращает тело в запись), и игры больше не видит.
                    if (near == null && onTheMeadow(context, at) && HidingPlaces.standable(world, at)
                            && !ground.place().area().contains(at.toCenterPos())
                            && world.shouldTickEntity(at)) {
                        found = candidate;
                        near = at;
                    }
                }
            }
            token = found;
            body = (CitizenEntity) world.getEntity(child.entityUuid().orElseThrow());
            if (near == null || body == null) {
                context.throwGameTestException("Ни одной вещицы посреди луга, у которой встать: "
                        + hunt.tokens());
                return;
            }
            body.refreshPositionAndAngles(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, 0, 0);
        } catch (RuntimeException failed) {
            // Проверка живёт дольше тика: упав на подготовке, она обязана
            // убрать деревню сама — иначе ярмарка осталась бы в мире,
            // и соседние основания деревень падали бы о неё.
            clearFairGround(context, world, manager, ground);
            throw failed;
        }
        context.runAtTick(Match.COUNTDOWN + 200, () -> {
            try {
                if (hunt.scoreOf(child.id()) < 1) {
                    context.throwGameTestException("Ребёнок в пяти шагах от вещицы " + token
                            + " не нашёл её: он у " + body.getBlockPos() + ", цель " + body.workTarget()
                            + "; поиск " + (hunt.isOver() ? "кончился" : "идёт")
                            + ", вещица в списке " + hunt.tokens().contains(token)
                            + ", на месте " + world.getBlockState(token).getBlock()
                            + ", вещиц осталось " + hunt.tokens().size()
                            + ", счёт игрока " + hunt.scoreOf(player.getUuid())
                            + "; тело снято " + body.isRemoved()
                            + ", нынешнее тело " + child.entityUuid().map(world::getEntity)
                            .map(one -> one.getBlockPos() + " цель " + ((CitizenEntity) one).workTarget())
                            .orElse("нет")
                            + ", соперник " + hunt.isRival(child.id())
                            + ", состязание в реестре " + Matches.at(ground.village().id()).isPresent());
                }
            } finally {
                clearFairGround(context, world, manager, ground);
            }
            context.complete();
        });
    }

    /**
     * Посреди ли своего луга: на его высоте и не ближе четырёх блоков к краю.
     * <p>
     * Своя разность, а не {@code getRelativePos}: в 1.20.1 тот поворачивает
     * точку на пол-оборота и у неповёрнутой проверки.
     */
    private static boolean onTheMeadow(TestContext context, BlockPos at) {
        BlockPos local = at.subtract(context.getAbsolutePos(BlockPos.ORIGIN));
        return local.getY() == 2 && local.getX() >= 4 && local.getX() <= 27
                && local.getZ() >= 4 && local.getZ() <= 27;
    }

    /**
     * Уборка трогает только своё: на место вещицы игрок поставил сундук —
     * сундук и остаётся.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hunt")
    public void aForeignBlockOnATokenSpotStays(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        BlockPos chest = null;
        try {
            Hunt hunt = startHunt(context, world, ground, playerAt(context, ground.place().counter()));
            List<BlockPos> tokens = List.copyOf(hunt.tokens());
            chest = tokens.get(0);
            world.setBlockState(chest, Blocks.CHEST.getDefaultState());
            hunt.cancel(world, "villagepax.contest.cancel.over");
            if (!world.getBlockState(chest).isOf(Blocks.CHEST)) {
                context.throwGameTestException("Уборка поиска сняла чужой сундук");
            }
            if (tokens.stream().skip(1).anyMatch(at -> world.getBlockState(at).isOf(ModBlocks.FESTIVAL_TOKEN))) {
                context.throwGameTestException("Вещицы остались после отмены");
            }
        } finally {
            if (chest != null) {
                world.setBlockState(chest, Blocks.AIR.getDefaultState());
            }
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Набег посреди поиска: состязание снято, вещиц нет, лент нет. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hunt")
    public void aSiegeCallsTheHuntOff(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            PlayerEntity player = playerAt(context, ground.place().counter());
            Hunt hunt = startHunt(context, world, ground, player);
            List<BlockPos> tokens = List.copyOf(hunt.tokens());
            pastTheCountdown(world);
            Matches.collect(world, tokens.get(0), player);
            rememberRaid(manager, ground.village(), UUID.randomUUID(), ground.hall(), 2);
            Matches.tick(world);
            if (Matches.at(ground.village().id()).isPresent()) {
                context.throwGameTestException("Набег, а поиск идёт");
            }
            if (tokens.stream().anyMatch(at -> world.getBlockState(at).isOf(ModBlocks.FESTIVAL_TOKEN))) {
                context.throwGameTestException("После набега вещицы остались");
            }
            if (player.getInventory().count(ModFestivalItems.FESTIVAL_RIBBON) != 0) {
                context.throwGameTestException("Прерванный набегом поиск дал ленты");
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /**
     * Сервер упал посреди поиска: вещица стоит и записана в памяти праздника,
     * а состязания нет. После запуска её убирают; пирог стола остаётся —
     * он живёт до утра.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hunt")
    public void leftoverTokensVanishAfterARestart(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        BlockPos token = context.getAbsolutePos(new BlockPos(20, 2, 20));
        BlockPos pie = context.getAbsolutePos(new BlockPos(22, 2, 20));
        try {
            world.setBlockState(token, ModBlocks.FESTIVAL_TOKEN.getDefaultState());
            world.setBlockState(pie, ModBlocks.FEAST_PIE.getDefaultState());
            manager.recordFestive(ground.village().id(), FAIR_DAY, token, TOKEN);
            manager.recordFestive(ground.village().id(), FAIR_DAY, pie, Registries.BLOCK.getId(ModBlocks.FEAST_PIE));
            Matches.recover(world);
            if (!world.getBlockState(token).isAir()) {
                context.throwGameTestException("Вещица прошлого запуска осталась");
            }
            if (!world.getBlockState(pie).isOf(ModBlocks.FEAST_PIE)) {
                context.throwGameTestException("Вместе с вещицей убрали и пирог стола");
            }
        } finally {
            world.setBlockState(token, Blocks.AIR.getDefaultState());
            world.setBlockState(pie, Blocks.AIR.getDefaultState());
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }
}
