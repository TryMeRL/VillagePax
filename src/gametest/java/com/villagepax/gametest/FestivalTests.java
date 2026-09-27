package com.villagepax.gametest;

import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.festival.Fair;
import com.villagepax.sim.festival.Fairs;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.festival.Heralds;
import com.villagepax.sim.festival.Feast;
import com.villagepax.sim.festival.Fireworks;
import com.villagepax.block.ModBlocks;
import com.villagepax.block.wonder.Wonders;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.work.WorkTicker;
import com.villagepax.sim.work.Workplaces;
import net.minecraft.block.Blocks;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Праздник на ярмарке: места праздника, затейник, день праздника,
 * состязания и призы.
 */
public class FestivalTests extends GameTestSupport {

    /**
     * У каждого народа есть ярмарка, и у ярмарки — все места праздника.
     * <p>
     * Места спрашиваются у схемы, а не у мира, поэтому проверка обходится
     * без единого блока: {@link Fairs} читает сердце, загон, черту, мишени
     * и столы по плану стройки. Числа — решение по игре: загон не меньше
     * пяти на пять (трём зверькам есть где удирать), ровно три мишени
     * (ближняя, средняя, дальняя), хоть одна черта и один стол.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "festival")
    public void everyPeopleHasAFairWithAllItsPlaces(TestContext context) {
        List<String> complaints = new ArrayList<>();
        for (Identifier culture : CultureManager.all().keySet()) {
            Identifier type = new Identifier(culture.getNamespace(), culture.getPath() + "/fairground");
            if (BuildingTypes.get(type).isEmpty()) {
                complaints.add(culture + ": нет ярмарки");
                continue;
            }
            Building site = Building.planned(type, BlockPos.ORIGIN, BlockRotation.NONE);
            Fair fair = Fairs.of(site).orElse(null);
            if (fair == null) {
                complaints.add(culture + ": у ярмарки нет сердца или прилавка");
                continue;
            }
            if (fair.pen().size() < 25) {
                complaints.add(culture + ": загон меньше 5x5 — клеток " + fair.pen().size());
            }
            if (fair.targets().size() != 3) {
                complaints.add(culture + ": мишеней " + fair.targets().size());
            }
            if (fair.shooting().isEmpty()) {
                complaints.add(culture + ": негде встать стрелку");
            }
            if (fair.tables().isEmpty()) {
                complaints.add(culture + ": некуда ставить пироги");
            }
            if (!fair.area().contains(fair.heart().toCenterPos())) {
                complaints.add(culture + ": сердце праздника вне ярмарки");
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Ярмарки народов:\n  " + String.join("\n  ", complaints));
        }
        context.complete();
    }

    static final Identifier NORMAN_FAIR = new Identifier("villagepax", "norman/fairground");
    static final Identifier NORMAN_FAIR_PLAN = new Identifier("villagepax", "norman/fairground_lvl1");

    /**
     * Затейник стоит у своего прилавка на ярмарке, и в руке у него мячики.
     * <p>
     * Мячики — вывеска ремесла, как монета у купца: по ним затейника
     * узнают с другого конца деревни, не читая подписи. Ярмарка ставится
     * в воздухе — так проверка не трогает землю соседей по партии.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "festival")
    public void anEntertainerKeepsHisCounterWithBallsInHand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building fair = null;
        try {
            fair = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            BlockPos counter = Workplaces.stations(fair).get(0);
            Citizen jester = hireWithBody(world, colony, Villages.ENTERTAINER, counter);
            Workplaces.assign(world, colony);
            if (!jester.workplace().equals(Optional.of(fair.id()))) {
                context.throwGameTestException("Затейник не получил ярмарку своей мастерской");
            }
            CitizenEntity body = bodyOf(world, colony, jester);
            WorkTicker.decide(world, manager, colony, jester, Schedule.MORNING_WORK);
            if (body.workTarget() == null
                    || body.workTarget().getSquaredDistance(counter) > 2.25) {
                context.throwGameTestException("Затейник не у прилавка: цель " + body.workTarget()
                        + ", прилавок " + counter);
            }
            if (!body.getMainHandStack().isOf(ModFestivalItems.JUGGLING_BALLS)) {
                context.throwGameTestException("В руке у затейника не мячики, а "
                        + body.getMainHandStack());
            }
        } finally {
            if (fair != null) {
                demolish(world, fair, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /**
     * Руки и туловище показывают, чем житель занят: пляшущий пляшет, даже
     * держа топор, спящий спит, даже если праздник, а мячики подбрасывают
     * стоя — на ходу затейник их просто несёт.
     * <p>
     * Выбор дорожки спрашивается у тех же функций, по которым рисует
     * клиент ({@link CitizenEntity#armsTrack}, {@link CitizenEntity#bodyTrack}):
     * глазом на клиенте этого не проверить.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "festival")
    public void theArmsShowWhatTheCitizenDoes(TestContext context) {
        ItemStack balls = new ItemStack(ModFestivalItems.JUGGLING_BALLS);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        List<String> wrong = new ArrayList<>();
        expect(wrong, "пляшущий с топором", "dance",
                CitizenEntity.armsTrack(false, true, true, false, axe, false));
        expect(wrong, "спящий в праздник", "sleep",
                CitizenEntity.armsTrack(true, true, false, false, ItemStack.EMPTY, false));
        expect(wrong, "затейник у прилавка", "juggle",
                CitizenEntity.armsTrack(false, false, false, false, balls, false));
        expect(wrong, "затейник на ходу", "stride",
                CitizenEntity.armsTrack(false, false, false, false, balls, true));
        expect(wrong, "лесоруб за работой", "chop",
                CitizenEntity.armsTrack(false, false, true, false, axe, false));
        expect(wrong, "курьер с бревном", "carry",
                CitizenEntity.armsTrack(false, false, false, false, new ItemStack(Items.OAK_LOG), false));
        expect(wrong, "пляшущий туловищем", "sway", CitizenEntity.bodyTrack(false, true, true));
        expect(wrong, "спящий туловищем", "doze", CitizenEntity.bodyTrack(true, true, false));
        expect(wrong, "бегущий", "lean", CitizenEntity.bodyTrack(false, false, true));
        if (!wrong.isEmpty()) {
            context.throwGameTestException("Не та дорожка:\n  " + String.join("\n  ", wrong));
        }
        context.complete();
    }

    private static void expect(List<String> wrong, String who, String track, String got) {
        if (!track.equals(got)) {
            wrong.add(who + ": ждали " + track + ", а " + got);
        }
    }

    /**
     * Новая деревня встаёт сразу с ярмаркой и затейником при ней.
     * <p>
     * Деревня старше игрока, как с ларьком и купцом: игрок, пришедший
     * к деревне в день её праздника, должен застать праздник, а не
     * «приходите через неделю, мы строим ярмарку». Затейник ставится явно,
     * пятым основателем: пришлые приходят раз в день, и ремесло с малым
     * приоритетом досталось бы затейнику последним.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "fairfound", tickLimit = 200)
    public void aNewVillageHasItsFairAndItsEntertainer(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;
        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }
            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала на ровном лугу");
                return;
            }
            Fair fair = Fairs.of(village).orElse(null);
            if (fair == null) {
                context.throwGameTestException("Деревня встала без ярмарки");
                return;
            }
            Optional<Citizen> jester = village.citizens().stream()
                    .filter(citizen -> citizen.profession().filter(Villages.ENTERTAINER::equals)
                            .isPresent())
                    .findFirst();
            if (jester.isEmpty()) {
                context.throwGameTestException("В деревне нет затейника");
                return;
            }
            if (!jester.get().workplace().equals(Optional.of(fair.building().id()))) {
                context.throwGameTestException("Затейник не при ярмарке: мастерская "
                        + jester.get().workplace());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }
        context.complete();
    }

    /**
     * Праздник идёт, если сегодня фаза народа, ярмарка готова, у неё есть
     * затейник и поселение не в осаде, — и отказ называет первую причину.
     * <p>
     * У норманнов праздник в полнолуние: день 8 — праздник, день 9 — нет.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "festival")
    public void theFestivalNeedsAFairAHostAndPeace(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building fair = null;
        try {
            fair = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            Citizen jester = hireWithBody(world, colony, Villages.ENTERTAINER,
                    Workplaces.stations(fair).get(0));
            Workplaces.assign(world, colony);
            List<String> wrong = new ArrayList<>();
            verdict(wrong, "полнолуние", FestivalDay.Verdict.ON, FestivalDay.today(colony, 8));
            verdict(wrong, "день после", FestivalDay.Verdict.NOT_TODAY, FestivalDay.today(colony, 9));

            jester.setWorkplace(null);
            verdict(wrong, "затейник без ярмарки", FestivalDay.Verdict.NO_HOST,
                    FestivalDay.today(colony, 8));
            jester.setWorkplace(fair.id());

            fair.setProgress(BuildProgress.DAMAGED);
            verdict(wrong, "разорённая ярмарка", FestivalDay.Verdict.NO_FAIR,
                    FestivalDay.today(colony, 8));
            fair.setProgress(BuildProgress.DONE);

            rememberRaid(manager, colony, java.util.UUID.randomUUID(), hall, 2);
            verdict(wrong, "набег", FestivalDay.Verdict.BESIEGED, FestivalDay.today(colony, 8));
            verdict(wrong, "набег, но не праздник", FestivalDay.Verdict.NOT_TODAY,
                    FestivalDay.today(colony, 9));
            if (!wrong.isEmpty()) {
                context.throwGameTestException("Правило праздника:\n  " + String.join("\n  ", wrong));
            }
        } finally {
            if (fair != null) {
                demolish(world, fair, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    private static void verdict(List<String> wrong, String when, FestivalDay.Verdict expected,
                                FestivalDay.Verdict got) {
        if (expected != got) {
            wrong.add(when + ": ждали " + expected + ", а " + got);
        }
    }

    /**
     * Деревня зовёт на праздник в его день и накануне — и молчит, когда
     * праздника не будет.
     * <p>
     * Зов без затейника был бы враньём: игрок пришёл бы на ярмарку, где
     * некому начать состязание.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "festival")
    public void theVillageCallsOnItsDayAndTheDayBefore(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building fair = null;
        try {
            fair = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            Citizen jester = hireWithBody(world, colony, Villages.ENTERTAINER,
                    Workplaces.stations(fair).get(0));
            Workplaces.assign(world, colony);
            List<String> wrong = new ArrayList<>();
            call(wrong, "полнолуние", Optional.of("villagepax.festival.today"),
                    Heralds.lineFor(colony, 8));
            call(wrong, "накануне", Optional.of("villagepax.festival.tomorrow"),
                    Heralds.lineFor(colony, 7));
            call(wrong, "будни", Optional.empty(), Heralds.lineFor(colony, 3));
            jester.setWorkplace(null);
            call(wrong, "без затейника", Optional.empty(), Heralds.lineFor(colony, 8));
            if (!wrong.isEmpty()) {
                context.throwGameTestException("Зов праздника:\n  " + String.join("\n  ", wrong));
            }
        } finally {
            if (fair != null) {
                demolish(world, fair, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    private static void call(List<String> wrong, String when, Optional<String> expected,
                             Optional<String> got) {
        if (!expected.equals(got)) {
            wrong.add(when + ": ждали " + expected + ", а " + got);
        }
    }

    /**
     * Пироги стоят на столе ярмарки в день праздника и уходят наутро —
     * кроме того, что на их месте уже чужое.
     * <p>
     * Игрок съел пирог и поставил на его место горшок с маком: уборка
     * на другой день горшок не трогает. Убирается только то, что там всё
     * ещё наше. Горшок, а не мак: живой цветок на столе не держится
     * и осыпается сам — проверка поймала бы игру, а не уборку.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "feast")
    public void piesStandOnTheirDayAndLeaveTheNext(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building fair = null;
        try {
            fair = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            hireWithBody(world, colony, Villages.ENTERTAINER, Workplaces.stations(fair).get(0));
            Workplaces.assign(world, colony);
            List<BlockPos> spots = Fairs.of(colony).orElseThrow().tables().stream()
                    .map(BlockPos::up).toList();

            Feast.tend(world, manager, colony, 8);
            for (BlockPos spot : spots) {
                if (!world.getBlockState(spot).isOf(ModBlocks.FEAST_PIE)) {
                    context.throwGameTestException("В праздник на столе нет пирога: " + spot);
                }
            }
            BlockPos flower = spots.get(0);
            world.setBlockState(flower, Blocks.POTTED_POPPY.getDefaultState());

            Feast.tend(world, manager, colony, 9);
            for (BlockPos spot : spots.subList(1, spots.size())) {
                if (!world.getBlockState(spot).isAir()) {
                    context.throwGameTestException("Наутро пирог остался на столе: " + spot);
                }
            }
            if (!world.getBlockState(flower).isOf(Blocks.POTTED_POPPY)) {
                context.throwGameTestException("Уборка сняла чужой горшок с места пирога");
            }
            if (manager.festiveOf(colony.id()).map(memory -> !memory.placed().isEmpty())
                    .orElse(false)) {
                context.throwGameTestException("Праздник помнит блоки, которых уже нет");
            }
        } finally {
            if (fair != null) {
                demolish(world, fair, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /** Стол накрывают раз в праздник: съеденный пирог в тот же день не появляется снова. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "feast")
    public void aFeastIsLaidOnceADay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building fair = null;
        try {
            fair = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            hireWithBody(world, colony, Villages.ENTERTAINER, Workplaces.stations(fair).get(0));
            Workplaces.assign(world, colony);
            BlockPos spot = Fairs.of(colony).orElseThrow().tables().get(0).up();

            Feast.tend(world, manager, colony, 16);
            if (!world.getBlockState(spot).isOf(ModBlocks.FEAST_PIE)) {
                context.throwGameTestException("Стол праздника не накрыт");
            }
            world.setBlockState(spot, Blocks.AIR.getDefaultState());
            Feast.tend(world, manager, colony, 16);
            if (!world.getBlockState(spot).isAir()) {
                context.throwGameTestException("Съеденный пирог появился снова в тот же день");
            }
        } finally {
            if (fair != null) {
                demolish(world, fair, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /** Сколько жителей выпустить на праздник, и чьими ремёслами. */
    private static List<Citizen> revellers(ServerWorld world, Settlement settlement, Fair fair,
                                           int many) {
        List<Citizen> out = new ArrayList<>();
        BlockPos floor = new BlockPos(fair.heart().getX() - 3, fair.standingY(), fair.heart().getZ() - 3);
        for (int i = 0; i < many; i++) {
            out.add(hireWithBody(world, settlement, new Identifier("villagepax", "farmer"), floor));
        }
        return out;
    }

    /** Зритель — на круге в четырёх шагах от сердца: мерка по кругу, а не по квадрату. */
    private static boolean watching(CitizenEntity body, Fair fair) {
        BlockPos target = body.workTarget();
        if (target == null) {
            return false;
        }
        double away = Math.hypot(target.getX() - fair.heart().getX(),
                target.getZ() - fair.heart().getZ());
        return away >= 3.5 && away <= 4.5 && Math.abs(target.getY() - fair.standingY()) <= 1;
    }

    private static boolean inRing(CitizenEntity body, Fair fair, double from, double to) {
        BlockPos target = body.workTarget();
        if (target == null) {
            return false;
        }
        double dx = target.getX() - fair.heart().getX();
        double dz = target.getZ() - fair.heart().getZ();
        double away = Math.max(Math.abs(dx), Math.abs(dz));
        return away >= from && away <= to && Math.abs(target.getY() - fair.standingY()) <= 1;
    }

    /**
     * В свой праздник деревня народа пляшет у сердца ярмарки — и днём,
     * и в любой другой час гулянья, — а затейник стоит у прилавка.
     * <p>
     * У норманнов праздник в полнолуние: день 0 — праздник, день 1 — нет.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "revels")
    public void villagersDanceAroundTheHeartOnTheirDay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement village = colonyWithBuilder(world, manager, hall);
        village.setOwner(Owner.AUTONOMOUS);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building building = null;
        try {
            building = standUp(context, world, manager, village, NORMAN_FAIR, anchor);
            BlockPos counter = Workplaces.stations(building).get(0);
            Citizen jester = hireWithBody(world, village, Villages.ENTERTAINER, counter);
            Workplaces.assign(world, village);
            Fair fair = Fairs.of(village).orElseThrow();
            List<Citizen> people = revellers(world, village, fair, 3);

            for (Citizen citizen : people) {
                WorkTicker.decide(world, manager, village, citizen, Schedule.DAY_WORK, 0);
                CitizenEntity body = bodyOf(world, village, citizen);
                if (!inRing(body, fair, 1.5, 2.5) || !body.isDancing()) {
                    context.throwGameTestException(citizen.fullName() + " не в хороводе: цель "
                            + body.workTarget() + ", сердце " + fair.heart() + ", пляшет "
                            + body.isDancing());
                }
            }
            WorkTicker.decide(world, manager, village, jester, Schedule.DAY_WORK, 0);
            CitizenEntity host = bodyOf(world, village, jester);
            if (host.workTarget() == null || host.workTarget().getSquaredDistance(counter) > 2.25
                    || host.isDancing()) {
                context.throwGameTestException("Затейник в праздник не у прилавка: " + host.workTarget());
            }
            for (Citizen citizen : people) {
                WorkTicker.decide(world, manager, village, citizen, Schedule.DAY_WORK, 1);
                if (bodyOf(world, village, citizen).isDancing()) {
                    context.throwGameTestException("На другой день " + citizen.fullName()
                            + " всё ещё пляшет");
                }
            }
        } finally {
            if (building != null) {
                demolish(world, building, plan);
            }
            discardBodies(world, village);
            manager.remove(village.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /** Колония в свой праздник работает до обеда и гуляет после. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "revels")
    public void aColonyWorksTillLunchOnItsFestival(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building building = null;
        try {
            building = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            hireWithBody(world, colony, Villages.ENTERTAINER, Workplaces.stations(building).get(0));
            Workplaces.assign(world, colony);
            Fair fair = Fairs.of(colony).orElseThrow();
            Citizen farmer = revellers(world, colony, fair, 1).get(0);
            CitizenEntity body = bodyOf(world, colony, farmer);

            WorkTicker.decide(world, manager, colony, farmer, Schedule.MORNING_WORK, 0);
            if (body.isDancing() || inRing(body, fair, 0, 2.5)) {
                context.throwGameTestException("Колония пляшет с утра, а не работает");
            }
            WorkTicker.decide(world, manager, colony, farmer, Schedule.DAY_WORK, 0);
            if (!body.isDancing() || !inRing(body, fair, 1.5, 2.5)) {
                context.throwGameTestException("После обеда колония не гуляет: цель "
                        + body.workTarget());
            }
        } finally {
            if (building != null) {
                demolish(world, building, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /**
     * В хороводе восемь мест: девятый и десятый стоят вокруг и смотрят,
     * и все — на сердце праздника.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "revels")
    public void nineDancersAndTheTenthWatches(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement village = colonyWithBuilder(world, manager, hall);
        village.setOwner(Owner.AUTONOMOUS);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building building = null;
        try {
            building = standUp(context, world, manager, village, NORMAN_FAIR, anchor);
            hireWithBody(world, village, Villages.ENTERTAINER, Workplaces.stations(building).get(0));
            Workplaces.assign(world, village);
            Fair fair = Fairs.of(village).orElseThrow();
            List<Citizen> people = revellers(world, village, fair, 10);
            int dancing = 0;
            int watching = 0;
            for (Citizen citizen : people) {
                WorkTicker.decide(world, manager, village, citizen, Schedule.LEISURE, 0);
                CitizenEntity body = bodyOf(world, village, citizen);
                if (!fair.heart().equals(body.workFocus())) {
                    context.throwGameTestException(citizen.fullName() + " смотрит не на сердце: "
                            + body.workFocus());
                }
                if (body.isDancing() && inRing(body, fair, 1.5, 2.5)) {
                    dancing++;
                } else if (!body.isDancing() && watching(body, fair)) {
                    watching++;
                }
            }
            if (dancing != 8 || watching != 2) {
                context.throwGameTestException("В хороводе " + dancing + ", смотрят " + watching
                        + " — ждали восемь и двое");
            }
        } finally {
            if (building != null) {
                demolish(world, building, plan);
            }
            discardBodies(world, village);
            manager.remove(village.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /**
     * Ракета праздника — в цветах народа: у норманнов красный с золотом
     * и звезда.
     * <p>
     * Взлетает она над сердцем праздника, на два блока выше всего, что
     * стоит над ним, и под открытым небом. Ракета, задевшая по пути жителя
     * или свод, рвётся тут же и ранит всех в пяти блоках, — поэтому под
     * навесом она встаёт над навесом, а под высоким сводом, где неба
     * не видно, не взлетает вовсе.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "fireworks")
    public void aRocketCarriesItsPeoplesColours(TestContext context) {
        Festival festival = Festivals.of(NORMAN).orElseThrow();
        NbtCompound fireworks = Fireworks.rocket(festival, 1).getSubNbt("Fireworks");
        if (fireworks == null || fireworks.getByte("Flight") != 1) {
            context.throwGameTestException("У ракеты нет полёта в один: " + fireworks);
            return;
        }
        NbtList explosions = fireworks.getList("Explosions", NbtElement.COMPOUND_TYPE);
        if (explosions.size() != 1) {
            context.throwGameTestException("Вспышек у ракеты " + explosions.size() + " — ждали одну");
            return;
        }
        NbtCompound burst = explosions.getCompound(0);
        if (!Arrays.equals(burst.getIntArray("Colors"), new int[]{0xC0392B, 0xE0B040})
                || burst.getByte("Type") != 2) {
            context.throwGameTestException("Не норманнская ракета: " + burst);
        }

        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building building = null;
        List<FireworkRocketEntity> launched = new ArrayList<>();
        BlockPos canopy = null;
        try {
            building = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            Fair fair = Fairs.of(colony).orElseThrow();
            BlockPos heart = fair.heart();

            FireworkRocketEntity open = Fireworks.launch(world, fair, festival, world.getRandom())
                    .orElse(null);
            if (open == null) {
                context.throwGameTestException("Над открытой ярмаркой ракета не взлетела");
                return;
            }
            launched.add(open);
            checkPad(context, world, fair, open, "над открытой ярмаркой");
            if (!Arrays.equals(open.getStack().getSubNbt("Fireworks").getList("Explosions",
                    NbtElement.COMPOUND_TYPE).getCompound(0).getIntArray("Colors"),
                    new int[]{0xC0392B, 0xE0B040})) {
                context.throwGameTestException("Взлетела не норманнская ракета");
            }

            canopy = heart.up(7);
            world.setBlockState(canopy, Blocks.STONE.getDefaultState());
            FireworkRocketEntity roofed = Fireworks.launch(world, fair, festival, world.getRandom())
                    .orElse(null);
            if (roofed == null) {
                context.throwGameTestException("Под невысоким навесом ракета не взлетела");
                return;
            }
            launched.add(roofed);
            checkPad(context, world, fair, roofed, "под навесом");
            if (roofed.getY() < canopy.getY() + 1) {
                context.throwGameTestException("Ракета под навесом, а не над ним: " + roofed.getPos());
            }

            world.setBlockState(canopy, Blocks.AIR.getDefaultState());
            canopy = heart.up(13);
            world.setBlockState(canopy, Blocks.STONE.getDefaultState());
            Fireworks.launch(world, fair, festival, world.getRandom()).ifPresent(vaulted -> {
                launched.add(vaulted);
                context.throwGameTestException("Под сводом в дюжину блоков ракета взлетела: "
                        + vaulted.getPos());
            });
        } finally {
            launched.forEach(FireworkRocketEntity::discard);
            if (canopy != null) {
                world.setBlockState(canopy, Blocks.AIR.getDefaultState());
            }
            if (building != null) {
                demolish(world, building, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /** Ракета в мире, над сердцем, на два блока выше верха столба и под небом. */
    private static void checkPad(TestContext context, ServerWorld world, Fair fair,
                                 FireworkRocketEntity rocket, String where) {
        BlockPos heart = fair.heart();
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, heart.getX(), heart.getZ());
        if (world.getEntity(rocket.getUuid()) == null) {
            context.throwGameTestException("Ракета " + where + " не в мире");
        }
        if (Math.abs(rocket.getX() - heart.getX() - 0.5) > 0.5
                || Math.abs(rocket.getZ() - heart.getZ() - 0.5) > 0.5) {
            context.throwGameTestException("Ракета " + where + " не над сердцем: " + rocket.getPos()
                    + ", сердце " + heart);
        }
        if (rocket.getY() < top + 2) {
            context.throwGameTestException("Ракета " + where + " ниже двух блоков над верхом: "
                    + rocket.getY() + ", верх " + top);
        }
    }

    /**
     * Праздник веселит колонию: наутро после праздника довольство сытого
     * жителя выросло на шесть больше, чем у соседей без праздника. Без
     * затейника или без ярмарки праздника нет — нет и прибавки.
     * <p>
     * У норманнов праздник в день 0, так что утро после него — день 1.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "cheer")
    public void aFestivalCheersTheColony(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        List<BlockPos> halls = List.of(context.getAbsolutePos(new BlockPos(1, 1, 1)),
                context.getAbsolutePos(new BlockPos(1, 1, 30)),
                context.getAbsolutePos(new BlockPos(30, 1, 30)));
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        List<Settlement> colonies = new ArrayList<>();
        List<Citizen> builders = new ArrayList<>();
        List<Building> fairs = new ArrayList<>();
        try {
            for (BlockPos hall : halls) {
                Settlement colony = colonyWithBuilder(world, manager, hall);
                colonies.add(colony);
                builders.add(colony.citizens().get(0));
            }
            Building festive = standUp(context, world, manager, colonies.get(0), NORMAN_FAIR,
                    context.getAbsolutePos(new BlockPos(4, 8, 4)));
            fairs.add(festive);
            fairs.add(standUp(context, world, manager, colonies.get(1), NORMAN_FAIR,
                    context.getAbsolutePos(new BlockPos(4, 8, 18))));
            hireWithBody(world, colonies.get(0), Villages.ENTERTAINER,
                    Workplaces.stations(festive).get(0));
            colonies.forEach(colony -> Workplaces.assign(world, colony));

            int[] gains = new int[3];
            for (int i = 0; i < 3; i++) {
                Citizen builder = builders.get(i);
                builder.setSaturation(30);
                builder.setHappiness(50);
                Needs.newDay(world, manager, colonies.get(i), 1);
                gains[i] = builder.happiness() - 50;
            }
            if (gains[0] - gains[2] != 6) {
                context.throwGameTestException("Праздник прибавил " + (gains[0] - gains[2])
                        + " к довольству — ждали 6 (прибавки " + Arrays.toString(gains) + ")");
            }
            if (gains[1] != gains[2]) {
                context.throwGameTestException("Без затейника праздник всё равно веселит: прибавки "
                        + Arrays.toString(gains));
            }
        } finally {
            for (Building fair : fairs) {
                demolish(world, fair, plan);
            }
            for (int i = 0; i < colonies.size(); i++) {
                discardBodies(world, colonies.get(i));
                manager.remove(colonies.get(i).id());
                world.setBlockState(halls.get(i), Blocks.AIR.getDefaultState());
            }
        }
        context.complete();
    }

    /**
     * Камень майя знает ближайший праздник округи — у поселения с ярмаркой
     * и затейником. Без затейника праздника нет, и камень о нём молчит.
     * <p>
     * Строка ищется по ключу перевода, а не по тексту: текст — дело языка.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "calendar")
    public void theCalendarStoneKnowsTheNearestFestival(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        BlockPos stone = context.getAbsolutePos(new BlockPos(24, 1, 24));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building building = null;
        try {
            building = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            Workplaces.assign(world, colony);
            if (tellsOfAFestival(Wonders.MayaCalendar.reading(world, stone))) {
                context.throwGameTestException("Камень знает праздник ярмарки без затейника");
            }
            hireWithBody(world, colony, Villages.ENTERTAINER, Workplaces.stations(building).get(0));
            Workplaces.assign(world, colony);
            if (!tellsOfAFestival(Wonders.MayaCalendar.reading(world, stone))) {
                context.throwGameTestException("Камень молчит о празднике колонии в двух шагах: "
                        + Wonders.MayaCalendar.reading(world, stone));
            }
        } finally {
            if (building != null) {
                demolish(world, building, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    private static boolean tellsOfAFestival(List<Text> lines) {
        return lines.stream().anyMatch(line -> line.getContent() instanceof TranslatableTextContent content
                && content.getKey().equals("villagepax.maya_calendar.festival"));
    }
}
