package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.ColonyFounder;
import com.villagepax.sim.Founding;
import com.villagepax.sim.FoundingOutcome;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.Looks;
import net.minecraft.util.math.Vec3d;
import com.villagepax.core.war.WarParty;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.diplomacy.Citizenship;
import com.villagepax.sim.diplomacy.Tribute;
import com.villagepax.sim.war.Campaigns;
import com.villagepax.sim.diplomacy.Yoke;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.life.Mortality;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Bonds;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.faith.Faith;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.faith.Domain;
import com.villagepax.sim.war.Siege;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.work.Needs;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.trade.Wages;
import com.villagepax.sim.Villages;
import net.minecraft.block.Block;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Canopy;
import com.villagepax.sim.build.Terrace;
import com.villagepax.sim.build.Hold;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.GuardJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3i;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import net.minecraft.nbt.NbtOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Набеги, стража, осада, союз, дань и поход.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class RaidTests extends GameTestSupport {

    // --- фаза 3: набеги и стража ---

    /**
     * Разбой кончается отрядом у ворот — и не сразу, и не каждый день.
     * <p>
     * Три правила одной проверкой, потому что они об одном решении:
     * терпение у деревни кончается на определённом счёте, второй отряд
     * не посылают, пока стоит первый, и после набега колония отдыхает.
     * Последнее — не поблажка, а условие играбельности: без остывания
     * разбойник получил бы отряд каждое утро и не смог бы ни отстроиться,
     * ни помириться.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "raid")
    public void patienceEndsAndAWarBandIsSent(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(village);
        manager.add(colony);

        try {
            // Земля по кругу: отряд собирается у края колонии, и ему нужно,
            // на чём стоять.
            for (int x = 2; x <= 30; x++) {
                for (int z = 2; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            // Пока доверие терпимое, никто не идёт.
            manager.update(village.id(), state -> state.addReputation(player, -20));
            Raids.sendIfDue(world, manager, village, 10L);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Отряд вышел на доверии -20: терпения нет вовсе");
            }

            // А ниже предела — идут, и числом по обиде.
            manager.update(village.id(), state -> state.addReputation(player, -40));
            Raids.sendIfDue(world, manager, village, 10L);

            WarParty party = manager.byId(colony.id()).orElseThrow().siege().orElse(null);
            if (party == null) {
                context.throwGameTestException("Доверие -60, а отряда нет");
                return;
            }
            if (party.fighters() != Raids.fightersFor(-60)) {
                context.throwGameTestException("Бойцов " + party.fighters() + " вместо "
                        + Raids.fightersFor(-60));
            }
            if (party.arrivesOn() != 11L) {
                context.throwGameTestException("Отряд приходит в день " + party.arrivesOn()
                        + " вместо назавтра: о набеге предупреждают заранее");
            }
            if (!party.home().equals(village.id())) {
                context.throwGameTestException("Отряд не помнит, кто его послал");
            }

            // Второго отряда, пока стоит первый, не бывает.
            Raids.sendIfDue(world, manager, village, 11L);
            if (!manager.byId(colony.id()).orElseThrow().siege().orElseThrow().id()
                    .equals(party.id())) {
                context.throwGameTestException("Пока стоял один отряд, послали второй");
            }

            // И после набега колония отдыхает.
            manager.update(colony.id(), Settlement::liftSiege);
            Raids.sendIfDue(world, manager, village, 12L);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Набег на следующий же день после набега: "
                        + "колония не успевает ни отстроиться, ни помириться");
            }

            Raids.sendIfDue(world, manager, village, 10L + Raids.COOLDOWN_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isEmpty()) {
                context.throwGameTestException("Отдых кончился, а обида осталась — "
                        + "отряд должен был выйти снова");
            }
        } finally {
            manager.byId(colony.id()).flatMap(Settlement::siege)
                    .ifPresent(one -> Raids.bodiesOf(world, one)
                            .forEach(CitizenEntity::discard));
            manager.remove(village.id());
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Отряд встаёт телами в обещанный день — вооружённый и подписанный.
     * <p>
     * До этого дня он только предупреждение в чате, и это то же правило,
     * на котором стоят обозы: нет тела — нет события. Проверяется и то,
     * что тела <b>не</b> появляются раньше срока: иначе предупреждение
     * теряло бы смысл, а игрок просыпался бы уже в бою.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "raid")
    public void warBandStandsUpOnTheDayItPromised(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 2, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(10, 2, 6));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(colony);
        // Отряд объявляется до try: за своими телами убирать надо и тогда,
        // когда проверка упала на первом же утверждении.
        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                musters, 2, 20L, 21L);

        try {
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 10; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(colony.id(), state -> state.besiege(party, 19L));

            // Осада обязана пережить сохранение: она лежит в поселении,
            // а поселение читается с диска. Кодек, который её теряет,
            // проявился бы только у игрока и только после перезахода —
            // отряд исчезал бы за ночь, и никто бы не понял почему.
            Settlement reread = Settlement.CODEC.parse(NbtOps.INSTANCE,
                            Settlement.CODEC.encodeStart(NbtOps.INSTANCE, colony)
                                    .result().orElseThrow())
                    .result().orElse(null);
            WarParty saved = reread == null ? null : reread.siege().orElse(null);
            if (saved == null || !saved.id().equals(party.id())
                    || saved.fighters() != party.fighters()
                    || saved.arrivesOn() != party.arrivesOn()
                    || !saved.musters().equals(party.musters())) {
                context.throwGameTestException("Осада не пережила запись на диск: " + saved);
            }
            if (reread != null && reread.lastRaid() != 19L) {
                context.throwGameTestException("День набега не сохранился: " + reread.lastRaid());
            }

            // Днём раньше — только слово.
            Raids.watch(world, manager, 19L);
            if (!Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Отряд встал раньше обещанного дня");
            }

            Raids.watch(world, manager, 20L);
            List<CitizenEntity> fighters = Raids.bodiesOf(world, party);
            if (fighters.size() != 2) {
                context.throwGameTestException("Тел у отряда " + fighters.size() + " вместо двух");
                return;
            }
            for (CitizenEntity fighter : fighters) {
                if (!fighter.isRaider() || !fighter.isFighter()) {
                    context.throwGameTestException("Боец не считает себя налётчиком");
                }
                if (!fighter.raidHost().filter(colony.id()::equals).isPresent()) {
                    context.throwGameTestException("Боец не знает, к кому пришёл");
                }
                if (fighter.getMainHandStack().isEmpty()) {
                    context.throwGameTestException("Боец пришёл с пустыми руками");
                }
            }

            // Второй осмотр в тот же день новых не поднимает.
            Raids.watch(world, manager, 20L);
            if (Raids.bodiesOf(world, party).size() != 2) {
                context.throwGameTestException("Отряд удвоился на втором осмотре: "
                        + Raids.bodiesOf(world, party).size());
            }

            // А когда срок вышел — уходят, и запись снимается.
            Raids.watch(world, manager, 22L);
            if (!Raids.bodiesOf(world, party).isEmpty()
                    || manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Отряд не ушёл, когда вышел срок");
            }
        } finally {
            Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Павший боец убывает из отряда, а последний снимает осаду.
     * <p>
     * Проверяется <b>настоящей смертью</b>, а не вызовом учёта: тело
     * убирается из мира не там, где его убили, — смерть моба идёт через
     * анимацию, — и весь смысл проверки в том, что путь от удара до
     * записи отряда действительно связан.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "raid", tickLimit = 200)
    public void fallenFighterThinsTheBand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 2, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(9, 2, 6));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 10; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", centre);
        manager.add(colony);

        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                musters, 1, 30L, 31L);
        manager.update(colony.id(), state -> state.besiege(party, 29L));
        Raids.watch(world, manager, 30L);

        List<CitizenEntity> fighters = Raids.bodiesOf(world, party);
        if (fighters.size() != 1) {
            cleanUpRaid(world, manager, colony, party, floor);
            context.throwGameTestException("Отряд не встал: тел " + fighters.size());
            return;
        }
        fighters.get(0).kill();

        context.runAtTick(100, () -> {
            try {
                if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                    context.throwGameTestException("Последний боец пал, а осада всё стоит");
                }
            } finally {
                cleanUpRaid(world, manager, colony, party, floor);
            }
            context.complete();
        });
    }

    /**
     * Страж идёт на налётчика, а без него обходит колонию.
     * <p>
     * Обе половины разом, потому что вторая без первой — просто гуляющий
     * житель с мечом, а первая без второй — часовой, стоящий на месте.
     * И проверяется <b>решение стратегии</b>, а не бой: драться умеет
     * ванильная тактика, и проверять её заново незачем.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "guard")
    public void guardGoesForTheRaiderAndPatrolsOtherwise(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos far = context.getAbsolutePos(new BlockPos(12, 2, 2));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 6; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen watchman = evenNewborn("Turold", "le Veilleur", NORMAN, Gender.MALE);
        watchman.setProfession(Villages.GUARD);
        manager.update(colony.id(), state -> state.addCitizen(watchman));

        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, watchman);
        CitizenEntity raider = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(5, 2, 2)));

        try {
            if (body == null || raider == null) {
                context.throwGameTestException("Тела не появились");
                return;
            }
            UUID watched = UUID.randomUUID();
            raider.linkRaid(colony.id(), watched);
            rememberRaid(manager, colony, watched, raider.getBlockPos(), 1);

            GuardJob guard = new GuardJob();
            WorkContext seen = new WorkContext(world, manager, colony, watchman, body);
            BlockPos goes = guard.tick(seen).orElse(null);

            if (body.getTarget() != raider) {
                context.throwGameTestException("Страж не взял налётчика на прицел: "
                        + body.getTarget());
            }
            if (goes == null || goes.getSquaredDistance(raider.getBlockPos()) > 1) {
                context.throwGameTestException("Страж пошёл не на врага, а в " + goes);
            }
            if (body.getMainHandStack().isEmpty()) {
                context.throwGameTestException("Страж без оружия");
            }

            // Врага убрали — страж отпускает цель и идёт в обход,
            // к самому дальнему зданию колонии.
            raider.discard();
            manager.update(colony.id(), state -> state.addBuilding(
                    Building.planned(TOWN_HALL_TYPE, far, BlockRotation.NONE)));

            BlockPos patrol = guard.tick(seen).orElse(null);
            if (body.getTarget() != null) {
                context.throwGameTestException("Страж гонится за тем, кого нет");
            }
            if (patrol == null || patrol.getSquaredDistance(far) > 1) {
                context.throwGameTestException("Обход ведёт не к дальнему краю, а в " + patrol);
            }
        } finally {
            // Только свои тела, и по ссылке, а не по радиусу. Проверки
            // одного батча делят один мир, и уборка «всё живое в тридцати
            // блоках» однажды уже убрала <b>чужого</b> бойца — того, что
            // умирал в соседней проверке, — и та упала на ровном месте.
            if (body != null) {
                body.discard();
            }
            if (raider != null) {
                raider.discard();
            }
            cleanUpVillage(world, manager, colony, hall, floor);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Налётчик берёт на прицел жителя осаждённой колонии — и только его.
     * <p>
     * Самая важная проверка всего набега: в ней вся его ставка. Отряд,
     * который приходит и стоит, — это декорация, а отряд, который бьёт
     * кого попало, включая своих, — это поломка. Проверяется на живой
     * тактике, прогоном тиков: цели ставит ванильный поиск врага, и
     * убедиться надо именно в том, что <b>предикат</b> в нём написан
     * верно, а не в том, что предикат существует.
     * <p>
     * Своим батчем, а не вместе с остальными набегами: проверки одного
     * батча идут в одном мире одновременно и в десятках блоков друг от
     * друга, а страж чует налётчика на сорок восемь блоков. Соседняя
     * проверка так однажды и увела чужого бойца.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "raid_fight", tickLimit = 200)
    public void raiderGoesForTheColonysPeople(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 6; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen townsman = colony.citizens().get(0);
        CitizenEntity peaceful = CitizenSpawner.spawnBody(world, colony, townsman);
        CitizenEntity raider = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(6, 2, 3)));
        CitizenEntity brother = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(7, 2, 3)));

        if (peaceful == null || raider == null || brother == null) {
            cleanUpFight(world, manager, colony, hall, floor, peaceful, raider, brother);
            context.throwGameTestException("Тела не появились");
            return;
        }
        UUID party = UUID.randomUUID();
        raider.linkRaid(colony.id(), party);
        brother.linkRaid(colony.id(), party);
        // Осаду поселение обязано помнить: тела отряда, о котором нет
        // записи, мод убирает сам — и это не придирка проверки, а правило
        // игры. Состояние «бойцы есть, осады нет» в мире не встречается.
        rememberRaid(manager, colony, party, raider.getBlockPos(), 2);

        context.runAtTick(100, () -> {
            try {
                if (raider.getTarget() != peaceful) {
                    context.throwGameTestException("Налётчик не взял жителя на прицел, а взял "
                            + raider.getTarget());
                }
                if (brother.getTarget() == raider) {
                    context.throwGameTestException("Отряд перерезал сам себя: боец пошёл "
                            + "на своего");
                }
                if (!peaceful.isFighter() && peaceful.getTarget() != null) {
                    context.throwGameTestException("Мирный житель полез в драку: "
                            + peaceful.getTarget());
                }
            } finally {
                cleanUpFight(world, manager, colony, hall, floor, peaceful, raider, brother);
            }
            context.complete();
        });
    }

    /**
     * Чертог вырублен в горе, стоит на одной отметке и выводит наружу.
     * <p>
     * Три утверждения в одной проверке нарочно: поодиночке каждое проходило
     * бы и на сломанном чертоге. Залы на одной отметке без хода наружу —
     * это запечатанная полость; ход наружу без общей отметки — это лестница
     * между случайными ямами. Гномье поселение — это <b>всё три</b> сразу,
     * и разваливается оно тоже целиком.
     * <p>
     * Выход ищется не взглядом на схему хода, а <b>разливом по воздуху</b>
     * от самой ратуши: так проверяется то, что почувствует игрок, — «отсюда
     * можно выйти», — а не то, что билдер честно вызвал нужный метод.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hold", tickLimit = 900)
    public void theHoldIsCutIntoTheMountainAndHasAWayOut(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos foot = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> mountain = raiseMountain(world, foot);
        // Отметка пола считается до try: её же надо забыть в finally, иначе
        // место останется занятым до конца прогона и следующая проверка
        // упрётся в него, не понимая, почему.
        BlockPos floor = Hold.floorUnder(world, foot.getX(), foot.getZ()).orElse(foot);
        Settlement hold = null;

        try {
            hold = Villages.found(world, DWARF, floor).orElse(null);
            if (hold == null) {
                context.throwGameTestException("Чертог не встал в горе: помеха="
                        + whoBlocks(manager, floor));
                return;
            }

            Building hall = hold.buildings().iterator().next();

            // Одна отметка на всё поселение.
            List<String> adrift = new ArrayList<>();
            for (Building building : hold.buildings()) {
                if (building.anchor().getY() != floor.getY()) {
                    adrift.add(building.type().getPath() + " на "
                            + (building.anchor().getY() - floor.getY()));
                }
            }
            if (!adrift.isEmpty()) {
                context.throwGameTestException("Залы чертога разъехались по высоте: "
                        + adrift + " — пол обязан быть один");
            }

            // И в самом чертоге больше одного зала: поселение, у которого
            // встала только ратуша, ничего про разметку в камне не говорит.
            if (hold.buildings().size() < 3) {
                context.throwGameTestException("В горе встало всего "
                        + hold.buildings().size() + " здание: разметка чертога "
                        + "не нашла места под камнем");
            }

            // Галерея рубится, а не мостится: у клетки улицы обязан быть свод.
            // Без этой половины улица под землёй была бы замурованной плитой —
            // пол выложен, а над ним камень, и «готово» означало бы «не пройти».
            Building far = hold.buildings().stream()
                    .filter(building -> !building.anchor().equals(hall.anchor()))
                    .findFirst().orElse(null);
            if (far == null) {
                context.throwGameTestException("В чертоге один зал: галерею вести некуда");
                return;
            }
            BlockPos tile = Roads.route(world, hold, far).stream().findFirst().orElse(null);
            if (tile == null) {
                context.throwGameTestException("Галерея от " + far.type().getPath()
                        + " не проложена вовсе");
                return;
            }
            if (tile.getY() != floor.getY()) {
                context.throwGameTestException("Галерея легла не на отметку пола: "
                        + (tile.getY() - floor.getY()));
            }
            Roads.pave(world, hold, Warehouse.of(world, hold), tile,
                    Roads.paving(hold, Warehouse.of(world, hold)));
            for (int up = 1; up <= Hold.HEADROOM; up++) {
                if (world.getBlockState(tile.up(up)).blocksMovement()) {
                    context.throwGameTestException("Над галереей на " + up
                            + " блоке камень: свод не прорублен, и по улице не пройти");
                }
            }

            BlockPos inside = floor.up();
            if (!escapesToSky(world, inside)) {
                context.throwGameTestException("Из чертога нет выхода к небу: "
                        + "деревня, в которую нельзя войти, — не деревня. Разлив дошёл до " + reach(world, inside));
            }
        } finally {
            cleanUpVillage(world, manager, hold, floor, mountain);
        }

        context.complete();
    }

    /**
     * Из каждого зала чертога можно выйти к небу — сразу, в день основания.
     * <p>
     * Жалоба заказчика: «у гномов их поселение просто зарыто в земле,
     * и никуда не могут выйти». Прежняя проверка спрашивала выход только
     * у ратуши: ворота от неё прорублены, а изба и поле стояли в сплошной
     * толще, и галерея к ним шла по прямой к середине — по диагонали,
     * где клетки касаются лишь углами, и упиралась в стену ратуши, а не
     * в её дверь. Житель, которому дали постель в избе, в неё не попадал,
     * а проснувшийся в ней — не выходил.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hold", tickLimit = 900)
    public void everyHallOfTheHoldLeadsOutside(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos foot = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> mountain = raiseMountain(world, foot);
        BlockPos floor = Hold.floorUnder(world, foot.getX(), foot.getZ()).orElse(foot);
        Settlement hold = null;

        try {
            hold = Villages.found(world, DWARF, floor).orElse(null);
            if (hold == null) {
                context.throwGameTestException("Чертог не встал в горе");
                return;
            }
            List<String> sealed = new ArrayList<>();
            for (Building building : hold.buildings()) {
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElseThrow();
                for (BlockPos door : com.villagepax.sim.build.Access.entrances(building, plan)) {
                    if (!escapesToSky(world, door)) {
                        sealed.add(building.type().getPath() + " у двери " + door.toShortString());
                    }
                }
            }
            if (!sealed.isEmpty()) {
                context.throwGameTestException("Залы чертога замурованы в горе: " + sealed);
            }

            // И к залу, который только размечен, штольня ведёт сразу:
            // иначе билдеру не к чему подойти, чтобы его вырубить.
            Building site = com.villagepax.sim.Raising.placeNear(world, manager, hold,
                    new Identifier("villagepax", "dwarf/warehouse_lvl1")).orElse(null);
            if (site == null) {
                context.throwGameTestException("Склад не нашёл места в горе — проверять нечего");
                return;
            }
            {
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(site)).orElseThrow();
                for (BlockPos door : com.villagepax.sim.build.Access.entrances(site, plan)) {
                    BlockPos step = door.offset(com.villagepax.sim.build.Access.awayFrom(site, plan, door));
                    if (!escapesToSky(world, step)) {
                        context.throwGameTestException("К размеченному складу нет штольни: порог "
                                + step.toShortString() + " замурован");
                    }
                }
            }
        } finally {
            cleanUpVillage(world, manager, hold, floor, mountain);
        }

        context.complete();
    }

    /**
     * Из каждого эльфийского дома в кронах можно спуститься на землю.
     * <p>
     * Та же проверка, что у чертога, и по той же жалобе: прежняя спрашивала
     * спуск только у ратуши, а до остальных помостов мост ещё надо было
     * настлать — и житель, поселённый в дальнем доме, висел над лесом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "grove", tickLimit = 900)
    public void everyHouseOfTheGroveComesDownToTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos soil = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> forest = raiseForest(world, soil);
        BlockPos deck = Canopy.deckOver(world, soil.getX(), soil.getZ()).orElse(soil);
        Settlement grove = null;

        try {
            grove = Villages.found(world, ELF, deck).orElse(null);
            if (grove == null) {
                context.throwGameTestException("Деревня в кронах не встала");
                return;
            }
            int floor = soil.getY() + 2;
            List<String> adrift = new ArrayList<>();
            for (Building building : grove.buildings()) {
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElseThrow();
                for (BlockPos door : com.villagepax.sim.build.Access.entrances(building, plan)) {
                    if (!escapes(world, door, at -> at.getY() <= floor)) {
                        adrift.add(building.type().getPath() + " у двери " + door.toShortString());
                    }
                }
            }
            if (!adrift.isEmpty()) {
                context.throwGameTestException("Дома в кронах, с которых не спуститься: " + adrift);
            }
        } finally {
            cleanUpVillage(world, manager, grove, deck, forest);
        }

        context.complete();
    }

    /**
     * Колония гномов под открытым небом не основывается — и говорит почему.
     * <p>
     * Молчаливый отказ был бы здесь худшим из возможных: ратуша встала бы,
     * а дальше не строилось бы ничего и без объяснений, потому что каждому
     * гномьему залу нужен камень над головой. Правило мода про отказы
     * ровно об этом: <b>отказ обязан говорить причину</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hold")
    public void aDwarfColonyRefusesTheOpenSky(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos meadow = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> grass = new ArrayList<>();
        // Опознаватель игрока держится в руке, а не выдумывается дважды:
        // уборка обязана убрать ту самую колонию, которую создал отказ,
        // если он однажды перестанет отказывать. Проверка, которая
        // за собой не убирает, роняет соседей, а не себя, — и виноватым
        // выглядит кто угодно, кроме неё.
        UUID settler = UUID.randomUUID();
        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = meadow.add(dx, 0, dz);
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    grass.add(at);
                }
            }

            FoundingOutcome outcome = ColonyFounder.foundAt(world, settler,
                    DWARF, meadow.up());
            if (!(outcome instanceof FoundingOutcome.Refused refused)) {
                context.throwGameTestException("Гномья колония встала на лугу: "
                        + "ни одно её здание там не построится");
                return;
            }
            if (!ColonyFounder.KEY_NEEDS_MOUNTAIN.equals(refused.translationKey())) {
                context.throwGameTestException("Отказали, но не за то: "
                        + refused.translationKey());
            }
        } finally {
            for (BlockPos at : grass) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            Founding.colonyOf(manager, settler).ifPresent(colony -> {
                discardBodies(world, colony);
                manager.remove(colony.id());
            });
        }

        context.complete();
    }

    /**
     * Кукла ростом со свой народ, а не со всякого.
     * <p>
     * Написано по настоящей дыре. Рост ставился отдельным вызовом рядом
     * с обликом, и куклы — налётчик, союзник, возница обоза — облик
     * получали, а рост нет: гномий налётчик выходил на голову выше гномов,
     * которых пришёл грабить. Один и тот же народ стоял двух размеров.
     * <p>
     * Проверяется <b>дверь</b>, а не вызывающие стороны: их три, завтра
     * будет четыре, и забыть в четвёртой так же легко. Рост едет вместе
     * с обликом — значит спросить надо с облика.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hold")
    public void aPuppetIsTheSizeOfItsPeople(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            CitizenEntity body = bodyOf(world, colony, colony.citizens().iterator().next());
            Culture underground = CultureManager.get(DWARF);
            if (underground == null || underground.stature() == Culture.PLAIN_STATURE) {
                context.throwGameTestException("У гномов не объявлен свой рост — "
                        + "проверять нечего");
                return;
            }

            body.setLook(Looks.puppet(DWARF, "guard"));
            if (body.stature() != underground.stature()) {
                context.throwGameTestException("Гномья кукла ростом " + body.stature()
                        + " вместо " + underground.stature()
                        + ": один народ стоит двух размеров");
            }

            // И обратно: народ сменился — сменился и рост.
            body.setLook(Looks.puppet(NORMAN, "guard"));
            if (body.stature() != Culture.PLAIN_STATURE) {
                context.throwGameTestException("Норманнская кукла ростом "
                        + body.stature() + " вместо человеческого");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Деревня в кронах висит на одной отметке, ходит по мостам и спускается
     * на землю.
     * <p>
     * Зеркало проверки чертога, и написана она нарочно как зеркало —
     * теми же тремя утверждениями и тем же разливом по воздуху. Если одно
     * и то же место кода умеет и закапывать деревню, и подвешивать её,
     * то оно точно не знает ни слова «гномы», ни слова «эльфы»; а если
     * зеркальная проверка проходит, то не знает их и проверка.
     * <p>
     * Спуск ищется не чтением лестницы, а <b>пешком от палаты до земли</b>:
     * деревня, которую видно с земли и в которую нельзя подняться, —
     * это та же поломка, что запечатанный чертог, только наоборот.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "grove", tickLimit = 900)
    public void theGroveHangsOnOneLevelAndComesDownToTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos soil = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> forest = raiseForest(world, soil);
        BlockPos deck = Canopy.deckOver(world, soil.getX(), soil.getZ()).orElse(soil);
        Settlement grove = null;

        try {
            grove = Villages.found(world, ELF, deck).orElse(null);
            if (grove == null) {
                context.throwGameTestException("Деревня в кронах не встала: помеха="
                        + whoBlocks(manager, deck));
                return;
            }
            Building hall = grove.buildings().iterator().next();

            List<String> adrift = new ArrayList<>();
            for (Building building : grove.buildings()) {
                if (building.anchor().getY() != deck.getY()) {
                    adrift.add(building.type().getPath() + " на "
                            + (building.anchor().getY() - deck.getY()));
                }
            }
            if (!adrift.isEmpty()) {
                context.throwGameTestException("Помосты разъехались по высоте: "
                        + adrift + " — настил обязан быть один");
            }
            if (grove.buildings().size() < 3) {
                context.throwGameTestException("В кронах встало всего "
                        + grove.buildings().size() + " здание: разметка не нашла места");
            }

            // И висит нарочно. Площадка две недели назад научилась
            // «равнять склон, а не строить сваи», и здесь это правило
            // работает наоборот: подсыпь она под настил — и из-под
            // эльфийского дома вышел бы земляной столб в дюжину блоков.
            if (world.getBlockState(deck.down()).isSolidBlock(world, deck.down())) {
                context.throwGameTestException("Под помостом выросла земля: "
                        + "дом в кронах висит нарочно, и подсыпать под него нечего");
            }

            // Мост настилается, а не мостится: под ним воздух, и пока
            // на складе нет досок, моста не бывает вовсе.
            Building far = grove.buildings().stream()
                    .filter(building -> !building.anchor().equals(hall.anchor()))
                    .findFirst().orElseThrow();
            BlockPos tile = Roads.route(world, grove, far).stream().findFirst().orElse(null);
            if (tile == null) {
                context.throwGameTestException("Мост от " + far.type().getPath()
                        + " не проложен вовсе");
                return;
            }
            if (tile.getY() != deck.getY()) {
                context.throwGameTestException("Мост лёг не на отметку настила: "
                        + (tile.getY() - deck.getY()));
            }
            Warehouse store = Warehouse.of(world, grove);
            store.add(new ItemStack(Items.BIRCH_PLANKS, 64));
            Block paving = Roads.paving(grove, store);
            if (paving == Blocks.DIRT_PATH) {
                context.throwGameTestException("Мост собрались топтать лопатой: "
                        + "тропа над пустотой — это не мост");
            }
            Roads.pave(world, grove, store, tile, paving);
            if (!world.getBlockState(tile).isOf(paving)) {
                context.throwGameTestException("Доску моста так и не положили");
            }
            if (world.getBlockState(tile.down()).isSolidBlock(world, tile.down())) {
                context.throwGameTestException("Под мостом выросла опора: "
                        + "подсыпка вывесила бы под каждой доской по два блока земли");
            }

            BlockPos inside = deck.up();
            int floor = soil.getY() + 2;
            if (!escapes(world, inside, at -> at.getY() <= floor)) {
                context.throwGameTestException("С деревьев не спуститься на землю: "
                        + "деревню видно, а войти в неё нельзя. Разлив дошёл до "
                        + reach(world, inside));
            }
        } finally {
            cleanUpVillage(world, manager, grove, deck, forest);
        }

        context.complete();
    }

    /**
     * Колония эльфов на голой земле не основывается — и говорит почему.
     * <p>
     * То же правило, что у гномов, и тот же довод: колония встала бы,
     * палата появилась, а дальше не строилось бы ничего — каждому
     * эльфийскому помосту нужен ствол под настилом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "grove")
    public void anElfColonyRefusesTheBareGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos meadow = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> grass = new ArrayList<>();
        UUID settler = UUID.randomUUID();
        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = meadow.add(dx, 0, dz);
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    grass.add(at);
                }
            }

            FoundingOutcome outcome = ColonyFounder.foundAt(world, settler, ELF, meadow.up());
            if (!(outcome instanceof FoundingOutcome.Refused refused)) {
                context.throwGameTestException("Эльфийская колония встала на лугу: "
                        + "ни один её помост там не построится");
                return;
            }
            if (!ColonyFounder.KEY_NEEDS_FOREST.equals(refused.translationKey())) {
                context.throwGameTestException("Отказали, но не за то: "
                        + refused.translationKey());
            }
        } finally {
            for (BlockPos at : grass) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            Founding.colonyOf(manager, settler).ifPresent(colony -> {
                discardBodies(world, colony);
                manager.remove(colony.id());
            });
        }

        context.complete();
    }

    // ======================= ПЛОЩАДКА, ЗАЗОР И ПОРОГ =======================

    /**
     * Под домом не остаётся пустот — и площадка равняется до стройки.
     * <p>
     * Жалоба заказчика: «пусть строитель строит так, чтобы под ней
     * не было пустот». Прежде опора подводилась после стройки и
     * из излишков, и дом честно оставался на сваях, если камня не хватило.
     * Проверяется именно склон: угол здания висит над ямой в пять блоков,
     * и после стройки под каждой клеткой подошвы обязана быть земля.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "terrace", tickLimit = 600)
    public void theSiteIsLevelledBeforeTheWallsGoUp(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(6, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            // Пол под половиной следа, и обрыв под второй: ровно склон,
            // на котором дом раньше вставал на сваи.
            Vec3i size = BuildSite.rotatedSize(plan.size(), BlockRotation.NONE);
            for (int dx = 0; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    if (dx >= size.getX() / 2) {
                        continue;
                    }
                    BlockPos at = anchor.add(dx, -1, dz);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
                return;
            }

            List<BlockPos> holes = new ArrayList<>();
            for (int dx = 0; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    BlockPos under = anchor.add(dx, -1, dz);
                    if (world.getBlockState(under).isAir()) {
                        holes.add(under);
                    }
                }
            }
            if (!holes.isEmpty()) {
                context.throwGameTestException("Под домом осталось пустот: " + holes.size()
                        + ", первая " + holes.get(0).toShortString());
            }
        } finally {
            demolish(world, site, plan);
            for (int dx = 0; dx < 9; dx++) {
                for (int dz = 0; dz < 9; dz++) {
                    for (int dy = -Terrace.DEEP; dy <= 0; dy++) {
                        world.setBlockState(anchor.add(dx, dy - 1, dz),
                                Blocks.AIR.getDefaultState());
                    }
                }
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Дом, поставленный нарочно на весу, площадка не трогает.
     * <p>
     * Правило площадки — «равнять склон, а не строить сваи». Без этой
     * половины здание, размеченное игроком на помосте или над обрывом,
     * обрастало бы земляным столбом в дюжину блоков, о котором он
     * не просил.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "terrace")
    public void aBuildingOnStiltsIsLeftAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 12, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            int moved = Terrace.level(world, site, plan);
            if (moved != 0) {
                context.throwGameTestException("Под домом на весу насыпали " + moved
                        + " блоков земли: это уже не площадка, а столб");
            }
        } finally {
            colony.removeBuilding(site.id());
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Между зданиями остаётся улица.
     * <p>
     * Жалоба заказчика: «пусть расстояние между строениями будет хотя бы
     * в 2-3 блока». Прежде следы просто не пересекались, то есть дома
     * вставали стена к стене.
     * <p>
     * Проверяется и то, что зазор <b>не распространяется на улучшение</b>:
     * дом, уже стоящий вплотную к соседу, поставил туда игрок, и отнять
     * у него второй уровень задним числом значило бы наказать
     * за вчерашнее правилом, которого вчера не было.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "terrace")
    public void housesStandApartWithAStreetBetween(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        raisedTownHall(colony, hall);

        try {
            Schematic house = SchematicLoader.get(HOUSE_SCHEMATIC).orElseThrow();
            int wide = house.size().getX();
            BlockPos first = hall.add(20, 0, 20);
            if (!(BuildOrders.place(manager, colony, HOUSE_SCHEMATIC, first,
                    BlockRotation.NONE) instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Первый дом не разметился — проверять нечего");
                return;
            }

            // Стена к стене: отказ.
            BlockPos touching = first.add(wide, 0, 0);
            if (!(BuildOrders.check(colony, HOUSE_SCHEMATIC, touching, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Overlaps)) {
                context.throwGameTestException("Второй дом встал вплотную к первому: "
                        + "деревня выйдет штабелем, а не деревней");
            }

            // На два блока — всё ещё тесно.
            BlockPos close = first.add(wide + BuildOrders.GAP - 1, 0, 0);
            if (!(BuildOrders.check(colony, HOUSE_SCHEMATIC, close, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Overlaps)) {
                context.throwGameTestException("Зазор меньше положенного приняли");
            }

            // А на три — улица, и место годится.
            BlockPos apart = first.add(wide + BuildOrders.GAP, 0, 0);
            if (!(BuildOrders.check(colony, HOUSE_SCHEMATIC, apart, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Дом с улицей в три блока не приняли: "
                        + BuildOrders.check(colony, HOUSE_SCHEMATIC, apart, BlockRotation.NONE));
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * За порогом каждой схемы пусто.
     * <p>
     * Жалоба заказчика: «зайти нельзя из-за скамьи». У майя и пони слот
     * убранства стоял <b>прямо за дверью</b>, и когда в него вставала
     * скамья из набора культуры, войти в дом было буквально нельзя.
     * <p>
     * Проверяется <b>каждая схема мода</b>, а не три перестроенных дома:
     * следующее здание напишут через месяц, и правило должно встретить
     * его само. Это то же рассуждение, по которому расчистка подхода
     * живёт в плане, а не в схемах.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "data")
    public void nothingStandsInTheDoorway(TestContext context) {
        List<String> complaints = new ArrayList<>();

        for (Identifier id : SchematicLoader.ids()) {
            Schematic schematic = SchematicLoader.get(id).orElseThrow();
            Map<BlockPos, BlockState> filled = new HashMap<>();
            for (Schematic.PalettedBlock block : schematic.blocks()) {
                filled.put(block.pos(), schematic.blockAt(block.paletteIndex()));
            }

            for (Schematic.Entrance door : schematic.entrances()) {
                BlockPos inside = door.pos().offset(door.wayOut().getOpposite());
                BlockState there = filled.get(inside);
                // Мешает не всё, что стоит, а то, сквозь что не пройти.
                // За калиткой поля растёт морковь, и это не помеха:
                // по грядке ходят. Помеха — скамья, стол, сундук,
                // то есть блок с настоящим объёмом.
                if (there == null || !there.blocksMovement()) {
                    continue;
                }
                complaints.add(id + ": за порогом " + inside.toShortString() + " стоит "
                        + Registries.BLOCK.getId(there.getBlock()));
            }
        }

        if (!complaints.isEmpty()) {
            context.throwGameTestException("В дверь не войти:\n  "
                    + String.join("\n  ", complaints));
        }

        context.complete();
    }

    /**
     * Дом дают другу, и только другу.
     * <p>
     * Отдельная ветка игры, обещанная дизайн-документом: «может ли игрок
     * жить внутри чужой деревни как гражданин… ближе всего к оригинальному
     * Millénaire». Проверяются все отказы разом, потому что порознь
     * каждый согласился бы с «пускать всегда».
     */
    /**
     * Сундук в здании деревни — на замке; свой сундук на свободной земле
     * и сундук в отведённом гражданину доме — нет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "citizenship")
    public void aVillageChestIsLockedButYourOwnIsNot(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(2, 30, 2));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(8, 30, 2));
        Settlement village = villageWithHouse(world, manager, centre, houseAt);
        BlockPos inHouse = houseAt.add(2, 1, 2);
        BlockPos onOpenGround = context.getAbsolutePos(new BlockPos(2, 30, 12));
        world.setBlockState(inHouse, Blocks.CHEST.getDefaultState());
        world.setBlockState(onOpenGround, Blocks.CHEST.getDefaultState());
        net.minecraft.entity.player.PlayerEntity player = context.createMockSurvivalPlayer();

        try {
            if (com.villagepax.sim.VillageLocks.lockedFor(world, player, inHouse).isEmpty()) {
                context.throwGameTestException("Сундук в доме деревни открыт прохожему");
            }
            if (com.villagepax.sim.VillageLocks.lockedFor(world, player, centre).isEmpty()) {
                context.throwGameTestException("Ратушу деревни можно разобрать вместе со складом");
            }
            if (com.villagepax.sim.VillageLocks.lockedFor(world, player, onOpenGround).isPresent()) {
                context.throwGameTestException("Свой сундук на свободной земле деревни заперт");
            }
            Building house = village.buildings().get(0);
            manager.update(village.id(), state ->
                    state.building(house.id()).ifPresent(known -> known.setResident(player.getUuid())));
            if (com.villagepax.sim.VillageLocks.lockedFor(world, player, inHouse).isPresent()) {
                context.throwGameTestException("Гражданину не открыть сундук в своём доме");
            }
        } finally {
            manager.remove(village.id());
            world.setBlockState(inHouse, Blocks.AIR.getDefaultState());
            world.setBlockState(onOpenGround, Blocks.AIR.getDefaultState());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /**
     * Окно ратуши деревни собирается для настоящей деревни и проходит
     * через провод: доверие, нужды и дела с деревней — на месте.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "citizenship")
    public void aVillageHallViewTravels(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(2, 30, 2));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(8, 30, 2));
        Settlement village = villageWithHouse(world, manager, centre, houseAt);
        net.minecraft.entity.player.PlayerEntity player = context.createMockSurvivalPlayer();
        manager.update(village.id(), state -> state.addReputation(player.getUuid(), Standing.FRIEND.from()));
        try {
            Settlement friendly = manager.byId(village.id()).orElseThrow();
            com.villagepax.screen.VillageHallView view =
                    com.villagepax.screen.VillageHallNet.viewOf(world, friendly, player);
            if (view.trust().reputation() != Standing.FRIEND.from()
                    || !view.trust().standing().equals(Standing.FRIEND.displayKey())) {
                context.throwGameTestException("Доверие в окне не то: " + view.trust());
            }
            if (!view.ties().citizenship().equals("yes")) {
                context.throwGameTestException("Другу не предложили дом: " + view.ties());
            }
            var encoded = com.villagepax.screen.VillageHallView.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, view).result().orElseThrow();
            var decoded = com.villagepax.screen.VillageHallView.CODEC
                    .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).result().orElseThrow();
            if (!decoded.equals(view)) {
                context.throwGameTestException("Снимок ратуши деревни не пережил провода");
            }
        } finally {
            manager.remove(village.id());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "citizenship")
    public void aFriendIsGivenAHouse(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(2, 30, 2));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(8, 30, 2));
        Settlement village = villageWithHouse(world, manager, centre, houseAt);
        Settlement mine = Settlement.found(NORMAN, Owner.of(player), "Моя",
                centre.add(3000, 0, 3000));
        manager.add(mine);

        try {
            if (Citizenship.judge(village, player) != Citizenship.Verdict.NOT_A_FRIEND) {
                context.throwGameTestException("Чужаку сразу дали дом: "
                        + Citizenship.judge(village, player));
            }
            if (Citizenship.judge(mine, player) != Citizenship.Verdict.NOT_A_VILLAGE) {
                context.throwGameTestException("В своей колонии просят дом у самих себя");
            }

            manager.update(village.id(), state ->
                    state.addReputation(player, Standing.FRIEND.from()));
            Settlement friendly = manager.byId(village.id()).orElseThrow();
            if (Citizenship.judge(friendly, player) != Citizenship.Verdict.YES) {
                context.throwGameTestException("Другу отказали: "
                        + Citizenship.judge(friendly, player));
            }

            // Занятый дом второй раз не отдают.
            Building house = Citizenship.spare(friendly).orElseThrow();
            manager.update(village.id(), state ->
                    state.building(house.id()).ifPresent(known -> known.setResident(player)));
            Settlement settled = manager.byId(village.id()).orElseThrow();
            if (Citizenship.judge(settled, player) != Citizenship.Verdict.ALREADY) {
                context.throwGameTestException("Дом дают дважды");
            }
            if (!Citizenship.isCitizen(settled, player)) {
                context.throwGameTestException("Дом отведён, а гражданином не считается");
            }

            UUID other = UUID.randomUUID();
            manager.update(village.id(), state ->
                    state.addReputation(other, Standing.FRIEND.from()));
            Settlement crowded = manager.byId(village.id()).orElseThrow();
            if (Citizenship.judge(crowded, other) != Citizenship.Verdict.NO_ROOM) {
                context.throwGameTestException("Второму другу отдали занятый дом: "
                        + Citizenship.judge(crowded, other));
            }
        } finally {
            manager.remove(village.id());
            manager.remove(mine.id());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Кровати гражданина деревня своим не раздаёт.
     * <p>
     * Главная половина затеи. Без неё «свой дом» — это дом, в который
     * в первую же ночь ляжет чужой пахарь, и гражданство читалось бы
     * как поломка.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "citizenship", tickLimit = 600)
    public void theResidentsBedsLeaveTheVillage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Schematic plan = schematic(context, HOUSE_SCHEMATIC);
        BlockPos centre = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", centre);
        world.setBlockState(centre, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(village);
        Building house = plan(village, houseAt, NORMAN_HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, village, plan);
            Citizen builder = evenNewborn("Rollo", "", NORMAN, Gender.MALE);
            builder.setProfession(BuildJob.BUILDER);
            village.addCitizen(builder);
            if (BuildJob.advance(world, manager, village.id(), house.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
                return;
            }

            int spotsBefore = Housing.sleepingSpots(world, village).size();
            if (spotsBefore == 0) {
                context.throwGameTestException("Дом построен, а кроватей в нём нет: "
                        + "проверять нечего");
                return;
            }

            manager.update(village.id(), state ->
                    state.building(house.id()).ifPresent(known -> known.setResident(player)));
            Settlement settled = manager.byId(village.id()).orElseThrow();

            if (Housing.sleepingSpots(world, settled).size() >= spotsBefore) {
                context.throwGameTestException("Кровати гражданина деревня всё ещё раздаёт "
                        + "своим: в первую же ночь в его дом ляжет чужой пахарь");
            }
        } finally {
            demolish(world, house, plan);
            discardBodies(world, village);
            manager.remove(village.id());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Разжалованного выселяют, а почётного слушают.
     * <p>
     * Гражданство держится дружбой, а не записью: упало доверие ниже
     * дружбы — дом отобрали. И вторая половина обещания документа,
     * «дорасти до старейшины»: дом и почёт вместе дают право говорить,
     * а порознь — нет.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "citizenship")
    public void theStrangerIsEvictedAndTheHonouredIsHeard(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(2, 40, 2));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(8, 40, 2));
        Settlement village = villageWithHouse(world, manager, centre, houseAt);

        try {
            manager.update(village.id(), state -> {
                state.addReputation(player, Standing.FRIEND.from());
                state.buildings().get(0).setResident(player);
            });
            Settlement settled = manager.byId(village.id()).orElseThrow();
            if (!Citizenship.isCitizen(settled, player)) {
                context.throwGameTestException("Дом отведён, а гражданином не считается");
            }
            if (Citizenship.isElder(settled, player)) {
                context.throwGameTestException("Друг уже говорит как старейшина: "
                        + "почёт достался даром");
            }

            // Дорос до почёта — и голос появился.
            manager.update(village.id(), state -> state.addReputation(player,
                    Standing.HONOURED.from() - Standing.FRIEND.from()));
            if (!Citizenship.isElder(manager.byId(village.id()).orElseThrow(), player)) {
                context.throwGameTestException("Почётному жителю с домом не дали голоса");
            }

            // А почёт без дома голоса не даёт.
            UUID guest = UUID.randomUUID();
            manager.update(village.id(), state ->
                    state.addReputation(guest, Standing.HONOURED.from()));
            if (Citizenship.isElder(manager.byId(village.id()).orElseThrow(), guest)) {
                context.throwGameTestException("Почётный гость без дома говорит как житель");
            }

            // Доверие упало — дом отобрали.
            manager.update(village.id(), state ->
                    state.addReputation(player, -Standing.HONOURED.from()));
            Citizenship.newDay(world, manager, manager.byId(village.id()).orElseThrow());
            if (Citizenship.isCitizen(manager.byId(village.id()).orElseThrow(), player)) {
                context.throwGameTestException("Доверие упало, а дом остался: "
                        + "гражданство держится записью, а не дружбой");
            }
        } finally {
            manager.remove(village.id());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // ======================= УЗЫ: РОДНЯ, ДРУЖБА, ССОРА =======================

    /**
     * Смерть близкого — горе, и горюют ровно те, кто помнил.
     * <p>
     * Свойства стерегут сам граф: симметрию, предел, забывание. Здесь
     * проверяется то, ради чего граф заведён, — что он <b>что-то меняет
     * в игре</b>. Граф без следствия был бы записью в сохранении, которую
     * игрок никогда не увидит.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bonds")
    public void theDeathOfAFriendIsGrieved(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (Citizen anyone : List.copyOf(colony.citizens())) {
                colony.removeCitizen(anyone.id());
            }
            Citizen one = grownWith(Nature.EVEN, "Роллон", Gender.MALE);
            Citizen other = grownWith(Nature.EVEN, "Аделиза", Gender.FEMALE);
            Citizen stranger = grownWith(Nature.EVEN, "Тибо", Gender.MALE);
            for (Citizen who : List.of(one, other, stranger)) {
                colony.addCitizen(who);
            }
            if (!Bonds.befriend(one, other)) {
                context.throwGameTestException("Двоих не удалось свести");
                return;
            }

            int friendWas = other.happiness();
            int strangerWas = stranger.happiness();
            // Зовётся ровно один раз: второй вызов уже ничего не
            // найдёт — память стёрта, — и проверка обвинила бы код
            // в том, что сделала сама.
            int grieving = Bonds.mourn(world, colony, one);
            if (grieving != 1) {
                context.throwGameTestException("Горюет не один человек, а " + grieving);
            }

            // Число написано от руки: спроси проверка размер горя у того,
            // кого проверяет, она согласилась бы и с нулём.
            int promised = 8;
            if (friendWas - other.happiness() != promised) {
                context.throwGameTestException("Друг потерял "
                        + (friendWas - other.happiness()) + " довольства вместо " + promised);
            }
            if (stranger.happiness() != strangerWas) {
                context.throwGameTestException("Горюет и тот, кто покойного не знал");
            }
            if (other.friends().contains(one.id())) {
                context.throwGameTestException("Умерший остался в памяти: колония будет "
                        + "горевать по нему каждый день");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Родня рядом греет, а ссора студит — и оба считаются в суточных нуждах.
     * <p>
     * Проверяется через нужды, а не через сам подсчёт: число, которое
     * никуда не идёт, проверять незачем. Заодно здесь видно обещание
     * дизайн-документа, невыполненное с первого дня: «счастье складывается
     * из… отношений в семье».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bonds")
    public void kinWarmAndQuarrelsChill(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (Citizen anyone : List.copyOf(colony.citizens())) {
                colony.removeCitizen(anyone.id());
            }
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 64));

            Citizen alone = grownWith(Nature.EVEN, "Одиночка", Gender.MALE);
            colony.addCitizen(alone);
            alone.setSaturation(40);
            alone.setHappiness(50);
            Needs.newDay(world, manager, colony);
            int plain = alone.happiness() - 50;

            // Появился супруг — и тот же день стоит больше.
            Citizen wife = grownWith(Nature.EVEN, "Аделиза", Gender.FEMALE);
            colony.addCitizen(wife);
            alone.marry(wife.id());
            wife.marry(alone.id());
            alone.setSaturation(40);
            alone.setHappiness(50);
            Needs.newDay(world, manager, colony);
            int withKin = alone.happiness() - 50;

            int warmth = 1;
            if (withKin - plain != warmth) {
                context.throwGameTestException("Родня рядом дала " + (withKin - plain)
                        + " очков вместо " + warmth);
            }

            // А теперь ленивый и честолюбивый под одной крышей.
            Citizen idle = grownWith(Nature.LAZY, "Одон", Gender.MALE);
            Citizen proud = grownWith(Nature.AMBITIOUS, "Гийом", Gender.MALE);
            UUID roof = UUID.randomUUID();
            for (Citizen who : List.of(idle, proud)) {
                colony.addCitizen(who);
                who.setHome(roof);
            }
            if (!Bonds.atOdds(idle, proud)) {
                context.throwGameTestException("Ленивый и честолюбивый поладили");
            }
            if (Bonds.foesNear(colony, idle) != 1) {
                context.throwGameTestException("Ссоры под одной крышей не видно: "
                        + Bonds.foesNear(colony, idle));
            }

            // Доотсчёт берётся у того же жителя, что и отсчёт со ссорой:
            // сравнивать прибавки разных людей нельзя — у них разные дом,
            // родня и кровать, и разность перестала бы означать ссору.
            proud.setHome(UUID.randomUUID());
            idle.setSaturation(40);
            idle.setHappiness(50);
            Needs.newDay(world, manager, colony);
            int calm = idle.happiness() - 50;

            proud.setHome(roof);
            idle.setSaturation(40);
            idle.setHappiness(50);
            Needs.newDay(world, manager, colony);
            int quarrelling = idle.happiness() - 50;
            if (calm - quarrelling != 1) {
                context.throwGameTestException("Ссора стоила " + (calm - quarrelling)
                        + " очков вместо одного");
            }

            // И разводятся они руками игрока: развели по домам — ссоры нет.
            proud.setHome(UUID.randomUUID());
            if (Bonds.foesNear(colony, idle) != 0) {
                context.throwGameTestException("Развели по домам, а ссора осталась: "
                        + "прекратить её игрок не может");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Суточный ход сводит тех, кто делит дело, и обходит поссорившихся.
     * <p>
     * Свойства проверяют этот же ход на записи; здесь — на живой колонии
     * с настоящими домами и мастерскими, то есть на том, что решает,
     * кого он вообще сведёт.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bonds")
    public void adayTogetherMakesFriends(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (Citizen anyone : List.copyOf(colony.citizens())) {
                colony.removeCitizen(anyone.id());
            }
            UUID roof = UUID.randomUUID();
            Citizen one = grownWith(Nature.EVEN, "Роллон", Gender.MALE);
            Citizen other = grownWith(Nature.EVEN, "Аделиза", Gender.FEMALE);
            Citizen apart = grownWith(Nature.EVEN, "Тибо", Gender.MALE);
            Citizen idle = grownWith(Nature.LAZY, "Одон", Gender.MALE);
            for (Citizen who : List.of(one, other, apart, idle)) {
                colony.addCitizen(who);
            }
            one.setHome(roof);
            other.setHome(roof);
            idle.setHome(roof);
            apart.setHome(UUID.randomUUID());
            // Один из живущих под общей крышей — честолюбивый: с ним
            // ленивый не сойдётся, а с остальными сойдётся.
            Citizen proud = grownWith(Nature.AMBITIOUS, "Гийом", Gender.MALE);
            colony.addCitizen(proud);
            proud.setHome(roof);

            Bonds.newDay(colony);

            if (!one.friends().contains(other.id())) {
                context.throwGameTestException("Живущие под одной крышей не сошлись");
            }
            if (one.friends().contains(apart.id())) {
                context.throwGameTestException("Сошлись и те, кто друг друга не видит");
            }
            if (idle.friends().contains(proud.id())) {
                context.throwGameTestException("Ленивый сдружился с честолюбивым");
            }
            // Ребёнок ни с кем не сходится: у него ещё нет ни дела, ни круга.
            Citizen child = someoneWith(Nature.EVEN, "Тибо-младший", Gender.MALE);
            child.setLived(0);
            child.setHome(roof);
            colony.addCitizen(child);
            Bonds.newDay(colony);
            if (!child.friends().isEmpty()) {
                context.throwGameTestException("Ребёнок завёл друзей: " + child.friends());
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Супруг освобождается, каким бы способом житель ни покинул колонию.
     * <p>
     * Написана по следам разбора, нашедшего настоящую беду: вдовство
     * снималось ровно в одном из трёх путей — при смерти от старости, —
     * а при гибели от чужой руки и при уходе своими ногами запись о браке
     * оставалась висеть. Вдова с мёртвым мужем в записи выпадает
     * из сватовства, и колония <b>тихо перестаёт расти</b>: в игре видно
     * только то, что детей больше нет.
     * <p>
     * Проверяются все три пути разом. Порознь каждый согласился бы
     * с поломкой: путь старости работал и до правки.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bonds")
    public void everyWayOutFreesTheSpouse(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (Citizen anyone : List.copyOf(colony.citizens())) {
                colony.removeCitizen(anyone.id());
            }

            // Путь первый: старость.
            Citizen husband = grownWith(Nature.EVEN, "Фульк", Gender.MALE);
            Citizen widow = grownWith(Nature.EVEN, "Аделиза", Gender.FEMALE);
            marry(colony, husband, widow);
            husband.setLived(Ages.diesAt());
            Mortality.die(world, colony, husband);
            if (widow.spouse().isPresent()) {
                context.throwGameTestException("Умерший от старости оставил вдову "
                        + "замужем за собой");
            }

            // Путь второй: ушёл своими ногами от голода.
            Citizen leaver = grownWith(Nature.EVEN, "Одон", Gender.MALE);
            marry(colony, leaver, widow);
            leaver.setSaturation(0);
            for (int day = 0; day < Needs.leaveAfterDays(leaver) + 1
                    && colony.citizen(leaver.id()).isPresent(); day++) {
                Needs.newDay(world, manager, colony);
            }
            if (colony.citizen(leaver.id()).isPresent()) {
                context.throwGameTestException("Голодный житель не ушёл — проверять нечего");
                return;
            }
            if (widow.spouse().isPresent()) {
                context.throwGameTestException("Ушедший своими ногами оставил вдову "
                        + "замужем за собой: колония тихо перестанет расти");
            }

            // Путь третий: убит.
            Citizen slain = grownWith(Nature.EVEN, "Гийом", Gender.MALE);
            marry(colony, slain, widow);
            Bonds.mourn(world, colony, slain);
            colony.removeCitizen(slain.id());
            if (widow.spouse().isPresent()) {
                context.throwGameTestException("Убитый оставил вдову замужем за собой");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Жалование уходит из казны, а рынок возвращает свою долю.
     * <p>
     * Весь круг одной проверкой, потому что порознь каждая половина
     * согласилась бы с поломкой: «не возвращается ничего» проходит первую,
     * «возвращается всегда» — вторую. Между ними и лежит смысл рынка:
     * он не приносит дохода, он <b>не даёт монете утечь</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wages")
    public void theMarketKeepsTheCoinAtHome(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = payroll(world, manager, hall, 4, Coins.SILVER * 8);

        try {
            manager.update(colony.id(), state -> state.setTaxRate(50));

            // Без рынка не возвращается ничего, сколько ставку ни задирай.
            Wages.Payroll leaky = Wages.newDay(world, manager, colony);
            if (leaky.paid() != Wages.WAGE * 4) {
                context.throwGameTestException("Заплачено " + leaky.paid() + " вместо "
                        + (Wages.WAGE * 4));
            }
            if (leaky.returned() != 0) {
                context.throwGameTestException("Без рынка вернулось " + leaky.returned()
                        + ": монета обязана утекать, иначе ларёк не нужен");
            }
            if (!leaky.allPaid()) {
                context.throwGameTestException("При полной казне кому-то не заплатили");
            }

            // А с купцом за прилавком — доля по ставке.
            openStall(colony);
            Wages.Payroll closed = Wages.newDay(world, manager, colony);
            int expected = closed.paid() * 50 / 100;
            if (closed.returned() != expected) {
                context.throwGameTestException("С рынком вернулось " + closed.returned()
                        + " вместо " + expected);
            }

            // И ставка решает, сколько именно: ноль возвращает ноль.
            manager.update(colony.id(), state -> state.setTaxRate(0));
            if (Wages.newDay(world, manager, colony).returned() != 0) {
                context.throwGameTestException("При нулевой ставке казна всё равно "
                        + "что-то взяла: налога, которого не задавали, не бывает");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Пустая казна стоит довольства, а хутор не платит никому.
     * <p>
     * Вторая половина важнее первой: денежное обращение начинается
     * с деревни, и хутор из шести человек, которому нечем платить,
     * не должен бунтовать за то, чего игрок не мог предотвратить.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wages")
    public void anEmptyTreasuryCostsContentButAHamletPaysNobody(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = payroll(world, manager, hall, 3, 0);

        try {
            Citizen worker = Wages.earners(colony).get(0);
            int before = worker.happiness();

            Wages.Payroll broke = Wages.newDay(world, manager, colony);
            if (broke.allPaid() || broke.unpaid() != 3) {
                context.throwGameTestException("Из пустой казны кому-то заплатили: "
                        + "без денег остались " + broke.unpaid() + " из трёх");
            }
            // Число написано от руки, а не взято у {@code Wages}: спроси
            // проверка размер удара у того, кого проверяет, она
            // согласилась бы и с нулём. Двенадцать выбраны так, чтобы
            // перевесить самый сытый и уютный день.
            int promised = 12;
            if (before - worker.happiness() != promised) {
                context.throwGameTestException("Неоплаченный потерял "
                        + (before - worker.happiness()) + " довольства вместо " + promised);
            }

            // А на хуторе расчёта нет вовсе.
            manager.update(colony.id(), state -> state.setLevel(SettlementLevel.HAMLET));
            int calm = worker.happiness();
            Wages.Payroll hamlet = Wages.newDay(world, manager, colony);
            if (hamlet.owed() != 0 || worker.happiness() != calm) {
                context.throwGameTestException("Хутор платит жалование: должен "
                        + hamlet.owed() + ", довольство " + worker.happiness());
            }
            if (Wages.billOf(colony) != 0) {
                context.throwGameTestException("Хутору выставили счёт: " + Wages.billOf(colony));
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Высокая ставка бьёт по счастью — и бьёт в тех же сутках.
     * <p>
     * Считается не отдельно, а прямо в суточных нуждах, рядом с прибавкой
     * за сытость: там же, где игрок и увидит их разницу. Проверяется
     * поэтому через нужды, а не через саму ставку — иначе проверка
     * согласилась бы с числом, которое никуда не идёт.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wages")
    public void aHighRateIsPaidForInContent(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = payroll(world, manager, hall, 2, 0);

        try {
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 64));
            Citizen worker = Wages.earners(colony).get(0);

            worker.setSaturation(40);
            worker.setHappiness(50);
            manager.update(colony.id(), state -> state.setTaxRate(0));
            Needs.newDay(world, manager, colony);
            int free = worker.happiness() - 50;

            worker.setSaturation(40);
            worker.setHappiness(50);
            manager.update(colony.id(), state -> state.setTaxRate(100));
            Needs.newDay(world, manager, colony);
            int taxed = worker.happiness() - 50;

            // Число написано от руки: спроси проверка его у самой ставки,
            // она согласилась бы с любой поломкой.
            int promised = 4;
            if (free - taxed != promised) {
                context.throwGameTestException("Полная ставка стоила " + (free - taxed)
                        + " очков довольства вместо " + promised);
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Дом третьего народа встаёт из сырья, которое колония добывает сама.
     * <p>
     * Главная проверка всей затеи, и написана она по следам настоящей
     * ошибки: пони сперва строили из ели, а живут в саванне, где растёт
     * акация. Лесоруб носил бы на склад акацию, билдер ждал бы ели —
     * и стройка встала бы на первом же блоке, ровно как однажды встала
     * на фонаре. Отсюда правило, которому подчиняются все три народа:
     * <b>строят из того, что растёт под боком</b>.
     * <p>
     * Завозится только сырьё: бревно, булыжник, тростник, зерно, песок,
     * шерсть и уголь. Всё остальное — доски, оконницу, кровать, сноп,
     * соломенную ступень — колония складывает сама. Проверка посылки
     * стоит отдельно: готового в завозе нет, иначе она проверяла бы
     * доставку, а не ремесло.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pony", tickLimit = 600)
    public void theThirdPeopleBuildsFromWhatGrowsNearby(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, PONY_HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall, PONY);
        Building site = plan(colony, anchor, PONY_HOUSE_TYPE, BlockRotation.NONE);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);
            raw(world, warehouse, hall, Items.ACACIA_LOG, 128);
            raw(world, warehouse, hall, Items.COBBLESTONE, 64);
            raw(world, warehouse, hall, Items.SAND, 64);
            raw(world, warehouse, hall, Items.GLASS, 16);
            raw(world, warehouse, hall, Items.RED_WOOL, 32);
            // Уголь на очаг и на факел: колония сложит и то и другое,
            // но угля ей взять негде — он в шахте.
            raw(world, warehouse, hall, Items.COAL, 16);

            Warehouse stocked = Warehouse.of(world, colony);
            for (Item ready : List.of(Items.ACACIA_PLANKS, Items.ACACIA_STAIRS,
                    Items.ACACIA_SLAB, Items.GLASS_PANE, Items.RED_BED)) {
                if (stocked.count(ready) > 0) {
                    context.throwGameTestException("Посылка теста не выполнена: на складе "
                            + "уже лежит готовое — " + ready);
                }
            }

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Изба пони не встала из сырья: шаг "
                        + site.nextStep() + " из " + housePlan.plan().steps().size()
                        + ", не хватает " + Materials.shortfall(housePlan, site, 200));
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Третий народ живёт по тем же правилам, что и первые два.
     * <p>
     * Не «пони работают», а <b>ничего для них не написано особо</b>:
     * роли зданий, ремёсла, ступени, пантеон и цепочка квестов у них те же,
     * и проверяется это сравнением с соседями, а не списком ожидаемого.
     * Список пришлось бы править при каждой правке данных, а сравнение
     * ловит ровно то, ради чего затевался третий народ: <b>народ
     * добавляется данными</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pony")
    public void theThirdPeopleNeededNoCode(TestContext context) {
        List<String> complaints = new ArrayList<>();

        Culture pony = CultureManager.get(PONY);
        Culture norman = CultureManager.get(NORMAN);
        if (pony == null || norman == null) {
            context.throwGameTestException("Народа нет в датапаке");
            return;
        }

        // Столько же зданий и те же роли: если у третьего народа роль
        // не нашлась, значит она заведена под первые два.
        Set<String> theirRoles = new HashSet<>();
        Set<String> ourRoles = new HashSet<>();
        for (Identifier type : norman.buildings()) {
            BuildingTypes.get(type).ifPresent(known -> ourRoles.add(known.role().id()));
        }
        for (Identifier type : pony.buildings()) {
            BuildingTypes.get(type).ifPresent(known -> theirRoles.add(known.role().id()));
        }
        if (!theirRoles.equals(ourRoles)) {
            complaints.add("роли зданий разошлись: у пони " + theirRoles
                    + ", у норманнов " + ourRoles);
        }

        // Пантеон: три домена, как и у соседей, и ни одного лишнего.
        Set<Domain> theirs = new HashSet<>();
        for (Identifier god : Gods.of(PONY)) {
            Gods.get(god).ifPresent(known -> theirs.add(known.domain()));
        }
        if (theirs.size() != Domain.values().length) {
            complaints.add("у пони " + theirs.size() + " доменов из "
                    + Domain.values().length + ": молиться будет некому");
        }
        if (Faith.templeType(PONY).isEmpty()) {
            complaints.add("у пони есть боги, а храма нет");
        }

        // Своё имя, свой облик, своя земля: народ, неотличимый от соседа,
        // ничего не доказывает.
        if (pony.namePools().male().isEmpty() || pony.namePools().settlement().isEmpty()) {
            complaints.add("у пони нет своих имён");
        }
        if (Looks.of(PONY, Gender.MALE, Optional.empty())
                .equals(Looks.of(NORMAN, Gender.MALE, Optional.empty()))) {
            complaints.add("пони выглядят норманнами");
        }
        if (pony.spawn().biomes().equals(norman.spawn().biomes())) {
            complaints.add("пони селятся там же, где норманны");
        }

        // И цепочка квестов, которая доводит до своей колонии.
        long chains = QuestManager.all().values().stream()
                .filter(quest -> quest.culture().filter(PONY::equals).isPresent())
                .count();
        if (chains < 6) {
            complaints.add("у пони " + chains + " квестов: цепочки основания нет");
        }

        if (!complaints.isEmpty()) {
            context.throwGameTestException("Третий народ вышел неполным:\n  "
                    + String.join("\n  ", complaints));
        }

        context.complete();
    }

    // ======================= ВАССАЛИТЕТ: ЗАХВАТ, ЯРМО, ПОХОД =======================

    /**
     * Отряд, ушедший без потерь, берёт колонию; проливший кровь — нет.
     * <p>
     * Единственное правило захвата, и проверяются обе его половины разом,
     * потому что порознь каждая согласилась бы с поломкой: «берёт всегда»
     * проходит первую, «не берёт никогда» — вторую.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "conquest")
    public void aWholeWarBandTakesTheColony(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 71, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(9, 71, 6));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 71, 14));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Коба", villageAt);
        manager.add(colony);
        manager.add(village);

        try {
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 70, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            // Первый отряд уходит целым — и колония под данью.
            WarParty whole = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters,
                    2, 20L, 21L);
            manager.update(colony.id(), state -> state.besiege(whole, 19L));
            Raids.watch(world, manager, 20L);
            Raids.watch(world, manager, 22L);

            Settlement after = manager.byId(colony.id()).orElseThrow();
            if (!after.owesTributeTo(village.id(), 22L)) {
                context.throwGameTestException("Отряд ушёл целым, а колония свободна: "
                        + "у войны по-прежнему нет проигрыша");
            }
            if (after.tributeDaysLeft(22L) != Tribute.DAYS) {
                context.throwGameTestException("Дань на " + after.tributeDaysLeft(22L)
                        + " дней вместо " + Tribute.DAYS);
            }
            Raids.bodiesOf(world, whole).forEach(CitizenEntity::discard);
            manager.update(colony.id(), Settlement::stopTribute);

            // Второй теряет бойца — и не берёт ничего.
            WarParty bled = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters,
                    2, 30L, 31L);
            manager.update(colony.id(), state -> state.besiege(bled, 29L));
            Raids.watch(world, manager, 30L);
            manager.update(colony.id(), state ->
                    state.updateSiege(state.siege().orElseThrow().withFighters(1)));
            Raids.watch(world, manager, 32L);

            if (manager.byId(colony.id()).orElseThrow().tributeDaysLeft(32L) > 0) {
                context.throwGameTestException("Отряд потерял бойца и всё равно взял колонию: "
                        + "пролитая кровь ничего не значит");
            }
            Raids.bodiesOf(world, bled).forEach(CitizenEntity::discard);
        } finally {
            cleanUpVillage(world, manager, colony, centre, floor);
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Ярмо платится серебром со склада, а пустая казна обижает.
     * <p>
     * Дань — не число в сохранении, а монета из <b>своего</b> сундука.
     * Вторая половина важнее первой: неуплата роняет доверие, и это
     * единственная настоящая угроза вассала — не заплатил, значит
     * приблизил следующий отряд.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "conquest")
    public void theYokeIsPaidFromTheColonyPurse(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 35, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 35, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(village);

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        world.setBlockState(colonyAt, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(colony);

        try {
            Coins.earn(Warehouse.of(world, colony).coins(), Tribute.RATE * 2);
            manager.update(colony.id(), state -> state.startTribute(village.id(), 100L));

            int theirsBefore = Coins.total(Warehouse.of(world, village).coins());
            for (long day = 1; day <= 2; day++) {
                if (Yoke.pay(world, manager, colony, day) != Tribute.RATE) {
                    context.throwGameTestException("День " + day + ": ярмо не оплачено");
                    return;
                }
            }

            if (Coins.total(Warehouse.of(world, colony).coins()) != 0) {
                context.throwGameTestException("Серебро не ушло со склада колонии: "
                        + Coins.total(Warehouse.of(world, colony).coins()));
            }
            if (Coins.total(Warehouse.of(world, village).coins())
                    != theirsBefore + Tribute.RATE * 2) {
                context.throwGameTestException("На склад деревни пришло не то: "
                        + Coins.total(Warehouse.of(world, village).coins()));
            }

            // Казна пуста: платить нечем, и деревня это запоминает.
            int trustBefore = manager.byId(village.id()).orElseThrow().reputationOf(player);
            if (Yoke.pay(world, manager, colony, 3L) != 0) {
                context.throwGameTestException("Заплатили из пустой казны");
            }
            int trustAfter = manager.byId(village.id()).orElseThrow().reputationOf(player);
            if (trustAfter >= trustBefore) {
                context.throwGameTestException("Неуплата не обидела деревню: доверие было "
                        + trustBefore + ", стало " + trustAfter);
            }
            if (manager.byId(colony.id()).orElseThrow().tributeDaysLeft(3L) <= 0) {
                context.throwGameTestException("Ярмо кончилось от того, что платить нечем: "
                        + "нищета стала способом освободиться");
            }

            // А срок вышел — и запись снимается сама.
            if (Yoke.pay(world, manager, colony, 200L) != 0) {
                context.throwGameTestException("Заплатили после срока");
            }
            if (manager.byId(colony.id()).orElseThrow().tributeTo().isPresent()) {
                context.throwGameTestException("Срок вышел, а запись о дани осталась");
            }
        } finally {
            manager.remove(colony.id());
            manager.remove(village.id());
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Перебитый отряд сюзерена снимает ярмо.
     * <p>
     * Дань держится страхом — там же и кончается. Без этого выхода
     * побеждённому оставалось бы только ждать конца срока, а ждать —
     * это не игра.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "conquest")
    public void beatingTheOverlordThrowsOffTheYoke(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 40, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(9, 40, 6));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 40, 14));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Коба", villageAt);
        manager.add(colony);
        manager.add(village);

        try {
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 39, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(colony.id(), state -> state.startTribute(village.id(), 100L));
            WarParty band = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters,
                    1, 20L, 21L);
            manager.update(colony.id(), state -> state.besiege(band, 19L));
            Raids.watch(world, manager, 20L);

            CitizenEntity fighter = Raids.bodiesOf(world, band).stream().findFirst().orElse(null);
            if (fighter == null) {
                context.throwGameTestException("Отряд не встал телами");
                return;
            }
            Raids.fell(world, fighter, 21L);

            if (manager.byId(colony.id()).orElseThrow().tributeDaysLeft(21L) > 0) {
                context.throwGameTestException("Отряд сюзерена перебит, а дань идёт: "
                        + "выхода из ярма нет");
            }
            fighter.discard();
        } finally {
            cleanUpVillage(world, manager, colony, centre, floor);
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Походу нужен город и двое стражей, и каждый отказ говорит словами.
     * <p>
     * Приговор целиком, а не «можно/нельзя»: серая кнопка без причины —
     * это загадка, а не правило. Отдельной строкой — друг: дружба
     * защищает деревню от игрока ровно так же, как игрока от неё.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "conquest")
    public void theMarchNeedsATownAndTwoSwords(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 50, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 50, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        manager.add(village);
        manager.add(colony);

        try {
            if (Campaigns.judge(null, village, player, 5L) != Campaigns.Verdict.NO_TOWN) {
                context.throwGameTestException("Без колонии поход разрешён");
            }
            if (Campaigns.judge(colony, village, player, 5L) != Campaigns.Verdict.NO_TOWN) {
                context.throwGameTestException("Хутор посылает отряды");
            }

            colony.setLevel(SettlementLevel.TOWN);
            if (Campaigns.judge(colony, village, player, 5L) != Campaigns.Verdict.NO_GUARDS) {
                context.throwGameTestException("Город без стражи послал отряд: "
                        + Campaigns.judge(colony, village, player, 5L));
            }

            Citizen one = evenNewborn("Гийом", "", NORMAN, Gender.MALE);
            one.setProfession(Villages.GUARD);
            colony.addCitizen(one);
            if (Campaigns.judge(colony, village, player, 5L) != Campaigns.Verdict.NO_GUARDS) {
                context.throwGameTestException("Одиночку отпустили воевать");
            }

            Citizen two = evenNewborn("Одон", "", NORMAN, Gender.MALE);
            two.setProfession(Villages.GUARD);
            colony.addCitizen(two);
            if (Campaigns.judge(colony, village, player, 5L) != Campaigns.Verdict.YES) {
                context.throwGameTestException("Городу с двумя стражами отказали: "
                        + Campaigns.judge(colony, village, player, 5L));
            }

            // На друга походом не ходят.
            manager.update(village.id(), state ->
                    state.addReputation(player, Standing.FRIEND.from()));
            if (Campaigns.judge(colony, village, player, 5L) != Campaigns.Verdict.TOO_FRIENDLY) {
                context.throwGameTestException("Другу объявили войну");
            }
            manager.update(village.id(), state ->
                    state.addReputation(player, -Standing.FRIEND.from()));

            // И на свою колонию тоже.
            if (Campaigns.judge(colony, colony, player, 5L)
                    != Campaigns.Verdict.NOT_A_NEIGHBOUR) {
                context.throwGameTestException("Колония пошла походом на себя");
            }
        } finally {
            manager.remove(colony.id());
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Поход стоит у чужих ворот, а не у своей ратуши, — и, полёгши весь,
     * возвращает колонии её стражу.
     * <p>
     * Обе беды жили за пределами проверки выше: там деревня в четырнадцати
     * шагах, внутри границы колонии, и тело вставало где надо случайно.
     * Настоящая деревня — за сотни блоков, а тело, рождённое по общим
     * правилам, не верило позиции за границей и появлялось дома. Отряда
     * у ворот не было, и деревня сдавалась без боя.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "conquest")
    public void aWarBandStandsAtTheEnemyGatesAndFallsThere(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        // Колония далеко: её чанки не нужны, походу нужна только запись.
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(6, 60, 6)).add(400, 0, 0);
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(12, 60, 12));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Дальняя", colonyAt);
        colony.setLevel(SettlementLevel.TOWN);
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Ушмаль", villageAt);
        manager.add(colony);
        manager.add(village);

        try {
            for (int x = 0; x <= 24; x++) {
                for (int z = 0; z <= 24; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 59, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }
            for (String name : List.of("Гийом", "Одон")) {
                Citizen guard = evenNewborn(name, "", NORMAN, Gender.MALE);
                guard.setProfession(Villages.GUARD);
                guard.setPosition(Vec3d.ofBottomCenter(colonyAt));
                colony.addCitizen(guard);
            }

            if (Campaigns.march(world, manager, colony, village, player, 10L)
                    != Campaigns.Verdict.YES) {
                context.throwGameTestException("Городу с двумя стражами не дали выступить");
                return;
            }
            WarParty party = manager.byId(village.id()).orElseThrow().siege().orElseThrow();
            Raids.watch(world, manager, 10L + Campaigns.MARCH_DAYS);

            List<CitizenEntity> band = Raids.bodiesOf(world, party);
            if (band.size() != 2) {
                context.throwGameTestException("У ворот встало " + band.size() + " из двух");
                return;
            }
            for (CitizenEntity fighter : band) {
                if (fighter.getBlockPos().getSquaredDistance(party.musters()) > 16 * 16) {
                    context.throwGameTestException("Боец встал не у ворот: "
                            + fighter.getBlockPos().toShortString()
                            + ", сбор " + party.musters().toShortString());
                }
            }

            // Полегли все — колония больше не в походе. Тело уходит из мира
            // после смертной секунды, поэтому и итог подводится после неё.
            band.forEach(CitizenEntity::kill);
        } catch (RuntimeException failed) {
            manager.remove(colony.id());
            manager.remove(village.id());
            floor.forEach(at -> world.setBlockState(at, Blocks.AIR.getDefaultState()));
            throw failed;
        }

        context.waitAndRun(30, () -> {
            try {
                Settlement home = manager.byId(colony.id()).orElseThrow();
                if (home.marchingOn().isPresent()) {
                    context.throwGameTestException("Поход полёг, а колония всё «в походе»");
                }
                if (manager.byId(village.id()).orElseThrow().siege().isPresent()) {
                    context.throwGameTestException("Отряда нет, а осада стоит");
                }
            } finally {
                manager.remove(colony.id());
                manager.remove(village.id());
                floor.forEach(at -> world.setBlockState(at, Blocks.AIR.getDefaultState()));
            }
            context.complete();
        });
    }

    /**
     * Отряд уходит из дома, берёт деревню и возвращается.
     * <p>
     * Три обещания похода одной проверкой, потому что они об одном:
     * пока отряд в пути, колония <b>без стражи</b>; ушедший целым берёт
     * деревню; вернувшиеся снова дома и снова работают.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "conquest")
    public void theWarBandTakesTheVillageAndComesHome(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(6, 60, 6));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 60, 20));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        colony.setLevel(SettlementLevel.TOWN);
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Коба", villageAt);
        manager.add(colony);
        manager.add(village);

        try {
            for (int x = 0; x <= 24; x++) {
                for (int z = 0; z <= 24; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 59, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            for (String name : List.of("Гийом", "Одон")) {
                Citizen guard = evenNewborn(name, "", NORMAN, Gender.MALE);
                guard.setProfession(Villages.GUARD);
                guard.setPosition(Vec3d.ofBottomCenter(colonyAt));
                colony.addCitizen(guard);
            }

            if (Campaigns.march(world, manager, colony, village, player, 10L)
                    != Campaigns.Verdict.YES) {
                context.throwGameTestException("Городу с двумя стражами не дали выступить");
                return;
            }
            if (manager.byId(colony.id()).orElseThrow().marchingOn().isEmpty()) {
                context.throwGameTestException("Отряд вышел, а колония об этом не знает");
            }
            WarParty party = manager.byId(village.id()).orElseThrow().siege().orElse(null);
            if (party == null || party.fighters() != 2) {
                context.throwGameTestException("У деревни не встал отряд колонии");
                return;
            }

            // Пока отряд в пути, стража дома не работает.
            Settlement marching = manager.byId(colony.id()).orElseThrow();
            for (Citizen guard : Campaigns.guardsOf(marching)) {
                if (!Campaigns.isAway(marching, guard)) {
                    context.throwGameTestException("Страж в походе продолжает служить дома");
                }
            }

            // Дошли, постояли, ушли целыми — деревня платит.
            Raids.watch(world, manager, 10L + Campaigns.MARCH_DAYS);
            if (Raids.bodiesOf(world, party).size() != 2) {
                context.throwGameTestException("У деревни встали не все: "
                        + Raids.bodiesOf(world, party).size());
            }
            Raids.watch(world, manager, 10L + Campaigns.MARCH_DAYS + Campaigns.STAY_DAYS + 1);

            if (!manager.byId(village.id()).orElseThrow()
                    .owesTributeTo(player, 14L)) {
                context.throwGameTestException("Деревня взята, а дани не платит");
            }
            Settlement home = manager.byId(colony.id()).orElseThrow();
            if (home.marchingOn().isPresent()) {
                context.throwGameTestException("Отряд вернулся, а колония всё воюет");
            }
            if (Campaigns.guardsOf(home).size() != 2) {
                context.throwGameTestException("Домой вернулось "
                        + Campaigns.guardsOf(home).size() + " стражей из двух");
            }
            if (!Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Тела остались стоять у чужой деревни");
            }
        } finally {
            cleanUpVillage(world, manager, colony, colonyAt, floor);
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Набег бьёт стены — и разорённое чинится тем же билдером.
     * <p>
     * Проверяется вся дуга обещания разом: отряд выбивает блоки, здание
     * становится повреждённым, билдер восстанавливает его по той же схеме
     * и <b>за материалы со склада</b>. Ломать выгодно ровно тем, что
     * чинить платно.
     * <p>
     * И проверяется, чего не ломают: блок ратуши с его хранилищем стоит
     * как стоял. Сломанный сундук высыпал бы игроку под ноги то, что
     * отряд пришёл унести, а снос ратуши — это уже захват поселения,
     * которого мод не умеет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "siege")
    public void raidWrecksTheWallsAndTheBuilderMendsThem(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));
        BlockPos musters = context.getAbsolutePos(new BlockPos(3, 9, 3));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);
        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                musters, 1, 40L, 41L);

        try {
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не построилось до набега");
                return;
            }

            // Сундуки склада считаем до набега: их обязано остаться столько же.
            int chestsBefore = containersIn(world, site, schematic);
            if (chestsBefore == 0) {
                context.throwGameTestException("В схеме ратуши не оказалось сундуков — "
                        + "проверять «чего не ломают» не на чем");
            }

            manager.update(colony.id(), state -> state.besiege(party, 39L));
            Raids.watch(world, manager, 40L);

            // И спрашиваем правило прямо: в списке «что можно выбить»
            // хранилищ быть не должно. Шесть блоков из сотни почти никогда
            // не попадут именно в сундук, и снятый запрет иначе прошёл бы
            // незамеченным.
            for (BlockPos spot : Siege.breakable(world, site, schematic)) {
                if (world.getBlockEntity(spot) != null) {
                    context.throwGameTestException("Сундук попал в список того, что ломают: "
                            + spot.toShortString());
                    break;
                }
            }

            if (containersIn(world, site, schematic) != chestsBefore) {
                context.throwGameTestException("Отряд разбил сундук: было " + chestsBefore
                        + ", стало " + containersIn(world, site, schematic)
                        + ". Содержимое высыпалось бы игроку под ноги");
            }

            Building after = manager.byId(colony.id()).orElseThrow()
                    .building(site.id()).orElseThrow();
            if (after.progress() != BuildProgress.DAMAGED) {
                context.throwGameTestException("Набег прошёл мимо здания: " + after.progress());
            }
            if (!world.getBlockState(hall).isOf(ModBlocks.TOWN_HALL)) {
                context.throwGameTestException("Отряд сломал ратушу — это уже захват, "
                        + "а не разорение");
            }

            int holes = 0;
            for (BuildStep step : schematic.plan().steps()) {
                if (step.placesBlock() && world.getBlockState(
                        BuildJob.worldPos(site, schematic.size(), step.pos())).isAir()) {
                    holes++;
                }
            }
            if (holes == 0) {
                context.throwGameTestException("Здание помечено разорённым, а стены целы");
            }

            // Чинится тем же билдером и за материалы: завозим ровно на пробоины.
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Разорённое не починилось");
            }
            for (BuildStep step : schematic.plan().steps()) {
                if (step.placesBlock() && world.getBlockState(
                        BuildJob.worldPos(site, schematic.size(), step.pos())).isAir()) {
                    context.throwGameTestException("После ремонта осталась пробоина");
                    break;
                }
            }
        } finally {
            Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            demolish(world, site, schematic);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Уцелевшие уходят с добычей, и добыча доезжает до дома.
     * <p>
     * Иначе набег был бы чистым уроном без приобретения — а деревня
     * посылает людей не затем, чтобы сжечь чужое, и не в последнюю
     * очередь затем, чтобы взять своё.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "siege")
    public void survivorsCarryTheLootHome(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos village = context.getAbsolutePos(new BlockPos(10, 2, 2));
        BlockPos musters = context.getAbsolutePos(new BlockPos(5, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        world.setBlockState(village, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement home = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", village);
        manager.add(home);

        WarParty party = new WarParty(UUID.randomUUID(), home.id(), NORMAN, musters,
                1, 50L, 51L);

        try {
            for (int x = 0; x <= 12; x++) {
                for (int z = 0; z <= 4; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 64));
            int before = Warehouse.of(world, colony).count(Items.BREAD);

            manager.update(colony.id(), state -> state.besiege(party, 49L));
            Raids.watch(world, manager, 50L);
            if (Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Отряд не встал — некому уносить");
                return;
            }

            // Срок вышел: уцелевшие уходят.
            Raids.watch(world, manager, 52L);

            int after = Warehouse.of(world, colony).count(Items.BREAD);
            if (after >= before) {
                context.throwGameTestException("Со склада не унесли ничего: было " + before
                        + ", стало " + after);
            }
            if (Warehouse.of(world, home).count(Items.BREAD) != before - after) {
                context.throwGameTestException("Добыча не доехала до деревни: у неё "
                        + Warehouse.of(world, home).count(Items.BREAD) + ", а унесли "
                        + (before - after));
            }
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Отряд ушёл, а осада осталась");
            }
        } finally {
            Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            manager.remove(home.id());
            cleanUpVillage(world, manager, colony, hall, floor);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            world.setBlockState(village, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
