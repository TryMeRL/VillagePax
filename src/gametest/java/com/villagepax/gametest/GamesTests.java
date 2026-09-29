package com.villagepax.gametest;

import com.villagepax.block.FurnitureBlock;
import com.villagepax.block.ModBlocks;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Streetscape;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Schematic;
import com.villagepax.item.ModItems;
import com.villagepax.screen.GamesNet;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.games.ArmWrestle;
import com.villagepax.sim.games.Bout;
import com.villagepax.sim.games.Bouts;
import com.villagepax.sim.games.Company;
import com.villagepax.sim.games.Games;
import com.villagepax.sim.games.GameSpot;
import com.villagepax.sim.games.GamesLedger;
import com.villagepax.sim.games.HideAndSeek;
import com.villagepax.sim.games.Passersby;
import com.villagepax.sim.games.Rivalry;
import com.villagepax.sim.trade.Coins;
import net.minecraft.item.ItemStack;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Игры с жителями: слова над головой, вечерняя компания, партия за столом.
 * <p>
 * Заказчик: «улучшай восприятие» — чтобы жители за игрой ощущались живыми
 * людьми, а не автоматами. Живой соперник говорит: соглашается, досадует,
 * хвалится, — и говорит так, что видно, кто именно.
 */
public class GamesTests extends GameTestSupport {

    /**
     * Фраза встаёт над головой и через три секунды гаснет сама.
     * Вторая фраза раньше двух секунд не встаёт: иначе компания тараторила бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "games_speech", tickLimit = 100)
    public void aLineShowsOverTheHeadAndFades(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen talker = hireWithBody(world, colony, BuildJob.BUILDER,
                context.getAbsolutePos(new BlockPos(4, 1, 4)));
        CitizenEntity body = (CitizenEntity) world.getEntity(talker.entityUuid().orElseThrow());
        if (!body.say(Text.literal("Шесть!"))) {
            context.throwGameTestException("Первая фраза не встала");
        }
        if (body.say(Text.literal("Ещё!"))) {
            context.throwGameTestException("Вторая фраза встала раньше двух секунд");
        }
        if (!body.speech().map(Text::getString).equals(Optional.of("Шесть!"))) {
            context.throwGameTestException("Над головой не та фраза: " + body.speech());
        }
        context.runAtTick(70, () -> {
            try {
                if (body.speech().isPresent()) {
                    context.throwGameTestException("Фраза не погасла через три секунды: "
                            + body.speech());
                }
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    // --- место игры ---

    private static final Identifier NORMAN_BREWERY = new Identifier("villagepax", "norman/brewery");
    private static final Identifier NORMAN_BREWERY_PLAN =
            new Identifier("villagepax", "norman/brewery_lvl1");

    /** Место игры — у игорного стола, если он есть: за ним и собираются. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_spot")
    public void theSpotIsTheTableFirst(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            BlockPos table = context.getAbsolutePos(new BlockPos(18, 2, 18));
            world.setBlockState(table, ModBlocks.GAME_TABLE.getDefaultState());
            manager.recordDecor(ground.village().id(), table);
            BlockPos spot = GameSpot.of(world, manager, ground.village());
            if (!spot.equals(table)) {
                context.throwGameTestException("Место игры не у стола " + table.toShortString()
                        + ", а в " + spot.toShortString());
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** Стола нет — у двери достроенной пивной: там и пьют, и бросают кости. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_spot")
    public void thenTheBreweryDoor(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        Building brewery = null;
        Schematic plan = schematic(context, NORMAN_BREWERY_PLAN);
        try {
            brewery = standUp(context, world, manager, ground.village(), NORMAN_BREWERY,
                    context.getAbsolutePos(new BlockPos(2, 1, 2)));
            BlockPos door = Access.entrances(brewery, plan).get(0);
            BlockPos front = door.offset(Access.awayFrom(brewery, plan, door));
            BlockPos spot = GameSpot.of(world, manager, ground.village());
            if (!spot.equals(front)) {
                context.throwGameTestException("Место игры не перед дверью пивной "
                        + front.toShortString() + ", а в " + spot.toShortString());
            }
        } finally {
            if (brewery != null) {
                demolish(world, brewery, plan);
                clearSkirt(world, brewery, plan);
            }
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** Ни стола, ни пивной — у ратуши: у неё и так собираются по вечерам. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_spot")
    public void thenTheTownHall(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            BlockPos spot = GameSpot.of(world, manager, ground.village());
            if (!spot.equals(ground.village().center())) {
                context.throwGameTestException("Место игры не у ратуши, а в " + spot.toShortString());
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** Колонии стол не ставят: у своих решает игрок, и чужая мебель у его ратуши ни к чему. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_spot")
    public void aColonyGetsNoGameTable(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            ground.village().setOwner(Owner.of(UUID.randomUUID()));
            Streetscape.dress(world, manager, ground.village());
            if (!manager.decorOf(ground.village().id()).isEmpty()) {
                context.throwGameTestException("Колонии поставили убранство: "
                        + manager.decorOf(ground.village().id()));
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /**
     * Деревня встаёт с игорным столом: в трёх–пяти шагах от ратуши, на траве,
     * лицом к площади, с лавками по бокам — и второй раз его не ставят.
     * <p>
     * В своей партии: деревне нужна площадка шире проверки, и соседей
     * по партии её застройка задела бы.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_table")
    public void aVillageGetsAGameTableOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> grass = new ArrayList<>();
        Settlement village = null;
        try {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    grass.add(at);
                }
            }
            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала на ровном лугу");
                return;
            }
            List<BlockPos> tables = gameTables(world, manager, village);
            if (tables.size() != 1) {
                context.throwGameTestException("Игорных столов в убранстве " + tables.size()
                        + ", ждали один");
            }
            BlockPos table = tables.get(0);
            Direction face = world.getBlockState(table).get(FurnitureBlock.FACING);
            for (Direction side : List.of(face.rotateYClockwise(), face.rotateYCounterclockwise())) {
                BlockPos bench = table.offset(side);
                BlockState seat = world.getBlockState(bench);
                if (!seat.isOf(ModBlocks.BENCH) || !manager.decorOf(village.id()).contains(bench)
                        || seat.get(FurnitureBlock.FACING) != side.getOpposite()) {
                    context.throwGameTestException("Сбоку от стола (" + side + ") не лавка лицом"
                            + " к нему: " + seat);
                }
            }
            if (!world.getBlockState(table.down()).isOf(Blocks.GRASS_BLOCK)) {
                context.throwGameTestException("Стол не на природной земле: под ним "
                        + world.getBlockState(table.down()));
            }
            int away = GameSpot.hallDistance(village, table);
            if (away < 3 || away > 5) {
                context.throwGameTestException("Стол в " + away + " шагах от ратуши, ждали 3–5");
            }
            int before = manager.decorOf(village.id()).size();
            Streetscape.dress(world, manager, village);
            if (gameTables(world, manager, village).size() != 1
                    || manager.decorOf(village.id()).size() != before) {
                context.throwGameTestException("Второй обход поставил ещё: столов "
                        + gameTables(world, manager, village).size());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, grass);
        }
        context.complete();
    }

    private static List<BlockPos> gameTables(ServerWorld world, SettlementManager manager,
                                             Settlement village) {
        return manager.decorOf(village.id()).stream()
                .filter(at -> world.getBlockState(at).isOf(ModBlocks.GAME_TABLE))
                .toList();
    }

    // --- вечерняя компания ---

    /** Вечер третьего дня: досуг, не праздник. */
    private static final long EVENING_DAY = 3;
    private static final long EVENING = 11_500;

    /** Шестеро взрослых у ратуши — разного нрава, и набожный среди них. */
    private static final List<Nature> SIX = List.of(Nature.EVEN, Nature.AMBITIOUS, Nature.LAZY,
            Nature.COWARD, Nature.PIOUS, Nature.AMBITIOUS);

    private static List<Citizen> sixAdults(TestContext context, ServerWorld world, Settlement village) {
        List<Citizen> adults = new ArrayList<>();
        for (int i = 0; i < SIX.size(); i++) {
            adults.add(hireWithBody(world, village, FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(16 + i, 2, 18)), SIX.get(i)));
        }
        return adults;
    }

    /**
     * Вечером у ратуши собирается компания: четверо, честолюбивые первыми,
     * без набожного, — и встаёт кольцом в двух шагах, лицом к месту игры.
     * Прочие идут на обычный вечерний сбор.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company")
    public void theCompanyGathersAtTheTownHallInTheEvening(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            Settlement village = ground.village();
            List<Citizen> adults = sixAdults(context, world, village);
            List<Citizen> company = Company.of(world, village, EVENING_DAY, Schedule.LEISURE, EVENING,
                    List.of());
            if (company.size() != 4) {
                context.throwGameTestException("В компании " + company.size() + ", ждали четверых");
            }
            if (company.stream().anyMatch(c -> Natures.of(c) == Nature.PIOUS)) {
                context.throwGameTestException("Набожный сел за кости");
            }
            if (Natures.of(company.get(0)) != Nature.AMBITIOUS
                    || Natures.of(company.get(1)) != Nature.AMBITIOUS) {
                context.throwGameTestException("Честолюбивые не первыми: " + company.stream()
                        .map(c -> Natures.of(c).id()).toList());
            }
            BlockPos centre = village.center();
            for (Citizen citizen : adults) {
                WorkTicker.decide(world, manager, village, citizen, Schedule.LEISURE, EVENING_DAY);
                CitizenEntity body = (CitizenEntity) world.getEntity(citizen.entityUuid().orElseThrow());
                boolean member = company.contains(citizen);
                if (member) {
                    BlockPos target = body.workTarget();
                    int away = target == null ? -1 : Math.max(Math.abs(target.getX() - centre.getX()),
                            Math.abs(target.getZ() - centre.getZ()));
                    if (away != 2 || !centre.equals(body.workFocus())) {
                        context.throwGameTestException(Natures.of(citizen).id() + " из компании стоит не"
                                + " кольцом у ратуши: цель " + target + ", взгляд " + body.workFocus());
                    }
                } else if (centre.equals(body.workFocus())) {
                    context.throwGameTestException(Natures.of(citizen).id()
                            + " не из компании, а смотрит на место игры");
                }
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /**
     * Старик — тоже взрослый: «старый Рено» за столом — часть вечера.
     * Пора жизни у старика своя, и отбор «только взрослые» его бы потерял.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company")
    public void anElderSitsAtTheTableToo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            Citizen elder = hireWithBody(world, ground.village(), FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(18, 2, 18)));
            elder.setLived(com.villagepax.sim.life.Ages.oldAt());
            hireWithBody(world, ground.village(), FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(19, 2, 18)));
            if (!Company.of(world, ground.village(), EVENING_DAY, Schedule.LEISURE, EVENING, List.of())
                    .contains(elder)) {
                context.throwGameTestException("Старика не позвали за стол");
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** Днём компании нет: работают. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company")
    public void noCompanyByDay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            sixAdults(context, world, ground.village());
            List<Citizen> company = Company.of(world, ground.village(), EVENING_DAY, Schedule.DAY_WORK,
                    8_000, List.of());
            if (!company.isEmpty()) {
                context.throwGameTestException("Днём за столом " + company.size());
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** В праздник компании нет: все на ярмарке. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company_fair")
    public void noCompanyOnAFestival(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            for (int i = 0; i < 3; i++) {
                hireWithBody(world, ground.village(), FarmJob.FARMER,
                        context.getAbsolutePos(new BlockPos(20 + i, 2, 26)));
            }
            List<Citizen> company = Company.of(world, ground.village(), FAIR_DAY, Schedule.LEISURE,
                    EVENING, List.of());
            if (!company.isEmpty()) {
                context.throwGameTestException("В праздник за столом " + company.size());
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Одному за столом не компания: он идёт на обычный сбор. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company")
    public void aLoneAdultIsNoCompany(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            Citizen alone = hireWithBody(world, ground.village(), FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(18, 2, 18)));
            if (!Company.of(world, ground.village(), EVENING_DAY, Schedule.LEISURE, EVENING, List.of())
                    .isEmpty()) {
                context.throwGameTestException("Один — уже компания");
            }
            WorkTicker.decide(world, manager, ground.village(), alone, Schedule.LEISURE, EVENING_DAY);
            CitizenEntity body = (CitizenEntity) world.getEntity(alone.entityUuid().orElseThrow());
            if (ground.village().center().equals(body.workFocus())) {
                context.throwGameTestException("Одиночка встал к месту игры, а не на сбор");
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** При госте у стола засиживаются — до двух часов ночи по часам мира, не дольше. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company")
    public void aGuestKeepsThemUpLate(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            Settlement village = ground.village();
            sixAdults(context, world, village);
            PlayerEntity guest = playerAt(context, context.getAbsolutePos(new BlockPos(27, 2, 24)));
            if (Company.of(world, village, EVENING_DAY, Schedule.SLEEP, 13_500, List.of(guest)).isEmpty()) {
                context.throwGameTestException("При госте в 13 500 компания разошлась");
            }
            if (!Company.of(world, village, EVENING_DAY, Schedule.SLEEP, 13_500, List.of()).isEmpty()) {
                context.throwGameTestException("Без гостя в 13 500 компания не разошлась спать");
            }
            if (!Company.of(world, village, EVENING_DAY, Schedule.SLEEP, 14_500, List.of(guest)).isEmpty()) {
                context.throwGameTestException("И при госте в 14 500 всё ещё за столом");
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /**
     * Компания играет сама с собой, когда есть кому смотреть: один бросает,
     * другой отвечает. Пустой деревне это ни к чему.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_company", tickLimit = 100)
    public void theCompanyPlaysAmongThemselves(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        Settlement village = ground.village();
        List<Citizen> adults = sixAdults(context, world, village);
        List<CitizenEntity> bodies = adults.stream()
                .map(c -> (CitizenEntity) world.getEntity(c.entityUuid().orElseThrow())).toList();
        Company.tick(world, manager, village, EVENING_DAY, EVENING, List.of());
        if (bodies.stream().anyMatch(b -> b.speech().isPresent())) {
            clearMeadow(world, manager, ground);
            context.throwGameTestException("Без зрителя компания заговорила");
        }
        PlayerEntity watcher = playerAt(context, context.getAbsolutePos(new BlockPos(14, 2, 24)));
        Company.tick(world, manager, village, EVENING_DAY, EVENING, List.of(watcher));
        List<CitizenEntity> threw = bodies.stream()
                .filter(b -> spoken(b).filter(key -> key.contains(".ambient_throw.")).isPresent())
                .toList();
        if (threw.size() != 1) {
            clearMeadow(world, manager, ground);
            context.throwGameTestException("Бросил и сказал не один, а " + threw.size());
        }
        context.runAtTick(30, () -> {
            try {
                long replied = bodies.stream().filter(b -> b != threw.get(0))
                        .filter(b -> spoken(b).filter(key -> key.contains(".ambient_reply.")).isPresent())
                        .count();
                if (replied != 1) {
                    context.throwGameTestException("Ответил не один, а " + replied);
                }
            } finally {
                clearMeadow(world, manager, ground);
            }
            context.complete();
        });
    }

    // --- партия ---

    /** Стол для партии: деревня на лугу, соперник-фермер (кошелёк 4) и игрок с десятью медяками. */
    private record Table(Meadow ground, Citizen rival, CitizenEntity body, PlayerEntity player) {
    }

    private static Table table(TestContext context, ServerWorld world, SettlementManager manager) {
        Meadow ground = meadow(context, world, manager);
        Citizen rival = hireWithBody(world, ground.village(), FarmJob.FARMER,
                context.getAbsolutePos(new BlockPos(20, 2, 24)));
        CitizenEntity body = (CitizenEntity) world.getEntity(rival.entityUuid().orElseThrow());
        PlayerEntity player = playerAt(context, context.getAbsolutePos(new BlockPos(22, 2, 24)));
        // Не в руку: щелчок пустой рукой — это отказ вслух, а с монетами в руке — нет.
        player.getInventory().setStack(9, new ItemStack(ModItems.COIN, 10));
        return new Table(ground, rival, body, player);
    }

    private static void clearTable(ServerWorld world, SettlementManager manager, Table table) {
        Bouts.of(table.player().getUuid()).ifPresent(bout -> Bouts.cancel(world, bout));
        clearMeadow(world, manager, table.ground());
    }

    private static IntSupplier dice(int... faces) {
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int face : faces) {
            queue.add(face);
        }
        return queue::removeFirst;
    }

    private static Bout begin(TestContext context, ServerWorld world, Table table, Bouts.Kind kind,
                              int stake, IntSupplier faces) {
        Bouts.Verdict verdict;
        try (Bouts.Loaded ignored = Bouts.withDice(faces)) {
            verdict = Bouts.start(world, table.player(), table.ground().village(), table.rival(), kind,
                    stake, EVENING_DAY, EVENING);
        }
        if (verdict != Bouts.Verdict.YES) {
            context.throwGameTestException("Партия не началась: " + verdict);
        }
        return Bouts.of(table.player().getUuid()).orElseThrow();
    }

    private static int coins(PlayerEntity player) {
        return Coins.total(player.getInventory());
    }

    /**
     * Партия в кости до выплаты: игрок встал на семнадцати, соперник добирал
     * и перебрал — игроку два медяка из кошелька соперника.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout", tickLimit = 120)
    public void aDiceBoutPaysTheWinner(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        Bout bout = begin(context, world, table, Bouts.Kind.DICE, 2, dice(6, 6, 5, 6, 6, 4, 6));
        bout.roll(world);
        bout.roll(world);
        bout.roll(world);
        bout.stand(world);
        context.runAtTick(80, () -> {
            try {
                if (coins(table.player()) != 12) {
                    context.throwGameTestException("У победителя " + coins(table.player())
                            + " медяков, ждали 12");
                }
                int spent = GamesLedger.get(world).spent(table.rival().id(), EVENING_DAY);
                if (spent != 2) {
                    context.throwGameTestException("Из кошелька соперника ушло " + spent + ", ждали 2");
                }
                if (spoken(table.body()).filter(key -> key.contains(".bust.")).isEmpty()) {
                    context.throwGameTestException("Перебравший промолчал: " + spoken(table.body()));
                }
                if (!bout.settled().equals(java.util.OptionalInt.of(2))) {
                    context.throwGameTestException("Итог партии не +2: " + bout.settled());
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Проигрыш уходит на склад деревни: житель потратит его на рынке. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout", tickLimit = 120)
    public void aLostBoutFillsTheVillageStore(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        int before = Warehouse.of(world, table.ground().village()).count(ModItems.COIN);
        Bout bout = begin(context, world, table, Bouts.Kind.DICE, 2, dice(6, 6, 6, 6, 1));
        bout.roll(world);
        bout.roll(world);
        bout.stand(world);
        context.runAtTick(80, () -> {
            try {
                if (coins(table.player()) != 8) {
                    context.throwGameTestException("У проигравшего " + coins(table.player())
                            + " медяков, ждали 8");
                }
                int stored = Warehouse.of(world, table.ground().village()).count(ModItems.COIN) - before;
                if (stored != 2) {
                    context.throwGameTestException("На склад деревни легло " + stored + ", ждали 2");
                }
                if (spoken(table.body()).filter(key -> key.contains(".win.")).isEmpty()) {
                    context.throwGameTestException("Выигравший промолчал: " + spoken(table.body()));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Ровно двадцать одно — ставка вдвойне. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout", tickLimit = 120)
    public void twentyOnePaysDouble(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        Bout bout = begin(context, world, table, Bouts.Kind.DICE, 2, dice(6, 6, 5, 4, 6, 6, 6, 4));
        bout.roll(world);
        bout.roll(world);
        bout.roll(world);
        bout.roll(world);
        context.runAtTick(80, () -> {
            try {
                if (coins(table.player()) != 14) {
                    context.throwGameTestException("За очко у игрока " + coins(table.player())
                            + " медяков, ждали 14");
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Пустой кошелёк — «Продулся, приходи завтра». */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout")
    public void aBrokeRivalRefuses(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        try {
            GamesLedger.get(world).spend(table.rival().id(), EVENING_DAY, 4);
            Bouts.Verdict verdict = Bouts.check(world, table.player(), table.ground().village(),
                    table.rival(), EVENING_DAY, EVENING);
            if (verdict != Bouts.Verdict.BROKE) {
                context.throwGameTestException("Продувшийся ответил " + verdict);
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    /** Три поражения подряд за вечер — обида до завтра. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout")
    public void threeLossesAndHeSulks(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        try {
            for (int i = 0; i < 3; i++) {
                GamesLedger.get(world).record(table.rival().id(), table.player().getUuid(),
                        Rivalry.Result.LOST, EVENING_DAY);
            }
            Bouts.Verdict tonight = Bouts.check(world, table.player(), table.ground().village(),
                    table.rival(), EVENING_DAY, EVENING);
            Bouts.Verdict tomorrow = Bouts.check(world, table.player(), table.ground().village(),
                    table.rival(), EVENING_DAY + 1, EVENING);
            if (tonight != Bouts.Verdict.SULK || tomorrow != Bouts.Verdict.YES) {
                context.throwGameTestException("Обида: сегодня " + tonight + ", завтра " + tomorrow);
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    /** С одним соперником — одна партия, и у игрока одна партия. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout")
    public void oneRivalOneBout(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        PlayerEntity second = playerAt(context, context.getAbsolutePos(new BlockPos(19, 2, 25)));
        second.getInventory().setStack(9, new ItemStack(ModItems.COIN, 10));
        Citizen other = hireWithBody(world, table.ground().village(), FarmJob.FARMER,
                context.getAbsolutePos(new BlockPos(21, 2, 26)));
        try {
            begin(context, world, table, Bouts.Kind.DICE, 1, dice(3));
            Bouts.Verdict taken = Bouts.check(world, second, table.ground().village(), table.rival(),
                    EVENING_DAY, EVENING);
            Bouts.Verdict busy = Bouts.check(world, table.player(), table.ground().village(), other,
                    EVENING_DAY, EVENING);
            if (taken != Bouts.Verdict.PLAYING || busy != Bouts.Verdict.PLAYING) {
                context.throwGameTestException("Занятому сопернику: " + taken + ", занятому игроку: "
                        + busy);
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    /** Отошёл дальше восьми блоков — партия сдана: иначе уход был бы бесплатным отказом от проигрыша. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout", tickLimit = 100)
    public void walkingAwayForfeits(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        begin(context, world, table, Bouts.Kind.DICE, 2, dice(3));
        BlockPos away = context.getAbsolutePos(new BlockPos(20, 2, 12));
        table.player().setPosition(net.minecraft.util.math.Vec3d.ofBottomCenter(away));
        context.runAtTick(45, () -> {
            try {
                if (Bouts.of(table.player().getUuid()).isPresent()) {
                    context.throwGameTestException("Ушедший всё ещё за столом");
                }
                if (coins(table.player()) != 8) {
                    context.throwGameTestException("Сдавший партию не заплатил: " + coins(table.player()));
                }
                if (spoken(table.body()).filter(key -> key.contains(".win.")).isEmpty()) {
                    context.throwGameTestException("Соперник не порадовался: " + spoken(table.body()));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Игрок пропал посреди партии — партия снята без выплаты, соперник свободен. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout", tickLimit = 40)
    public void aBoutEndsWhenThePlayerIsGone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        begin(context, world, table, Bouts.Kind.DICE, 2, dice(3));
        table.player().discard();
        context.runAtTick(5, () -> {
            try {
                if (Bouts.of(table.player().getUuid()).isPresent()
                        || Bouts.rivalOf(table.rival().id()).isPresent()) {
                    context.throwGameTestException("Партия пережила ушедшего игрока");
                }
                if (coins(table.player()) != 10) {
                    context.throwGameTestException("Снятая партия двинула монеты: " + coins(table.player()));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Соперник пропал посреди партии — снята без выплаты. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout", tickLimit = 40)
    public void aBoutEndsWhenTheRivalIsGone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        begin(context, world, table, Bouts.Kind.DICE, 2, dice(3));
        table.body().discard();
        context.runAtTick(5, () -> {
            try {
                if (Bouts.of(table.player().getUuid()).isPresent()) {
                    context.throwGameTestException("Партия пережила пропавшего соперника");
                }
                if (coins(table.player()) != 10) {
                    context.throwGameTestException("Снятая партия двинула монеты: " + coins(table.player()));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** За партией соперник стоит на месте и смотрит на игрока, а не бродит по площади. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_bout")
    public void theRivalFacesThePlayerAndStands(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        try {
            begin(context, world, table, Bouts.Kind.DICE, 1, dice(3));
            WorkTicker.decide(world, manager, table.ground().village(), table.rival(), Schedule.LEISURE,
                    EVENING_DAY);
            CitizenEntity body = table.body();
            if (!body.getBlockPos().equals(body.workTarget())
                    || !table.player().getBlockPos().up().equals(body.workFocus())) {
                context.throwGameTestException("Соперник за партией: цель " + body.workTarget()
                        + " (стоит на " + body.getBlockPos() + "), взгляд " + body.workFocus());
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    // --- армрестлинг ---

    /** Соперник для армрестлинга: по ремеслу — сила, по нраву — упорство. */
    private static Table armTable(TestContext context, ServerWorld world, SettlementManager manager,
                                  Identifier craft, Nature nature) {
        Meadow ground = meadow(context, world, manager);
        Citizen rival = hireWithBody(world, ground.village(), craft,
                context.getAbsolutePos(new BlockPos(20, 2, 24)), nature);
        CitizenEntity body = (CitizenEntity) world.getEntity(rival.entityUuid().orElseThrow());
        PlayerEntity player = playerAt(context, context.getAbsolutePos(new BlockPos(22, 2, 24)));
        player.getInventory().setStack(9, new ItemStack(ModItems.COIN, 10));
        return new Table(ground, rival, body, player);
    }

    /** Нажимать посреди каждого прохода: на двенадцатом тике отметка — в середине зелёного. */
    private static void pressInTime(TestContext context, ServerWorld world, Bout bout, int passes) {
        for (int pass = 0; pass < passes; pass++) {
            context.runAtTick(12 + 24L * pass, () -> bout.press(world));
        }
    }

    /**
     * Нажатия в такт кладут руку купца: сила 0,4 — давление 0,4 за тик,
     * за проход +18 − 9,6 = +8,4, край к двенадцатому нажатию. Соперник всю
     * партию борется телом, а по итогу отпускает руку.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_arm", tickLimit = 400)
    public void pressingInTheGreenWinsTheArm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = armTable(context, world, manager, Villages.MERCHANT, Nature.EVEN);
        Bout bout = begin(context, world, table, Bouts.Kind.ARM, 1, dice(3));
        pressInTime(context, world, bout, 15);
        context.runAtTick(2, () -> {
            if (!table.body().isWrestling()) {
                clearTable(world, manager, table);
                context.throwGameTestException("Соперник за армрестлингом не борется телом");
            }
        });
        context.runAtTick(360, () -> {
            try {
                if (!bout.arm().flatMap(ArmWrestle::outcome).equals(Optional.of(ArmWrestle.Outcome.WIN))) {
                    context.throwGameTestException("Нажатия в такт не положили руку: " + bout.arm()
                            .map(arm -> arm.outcome() + " при " + arm.balance()).orElse("нет борьбы"));
                }
                if (coins(table.player()) != 11 || table.body().isWrestling()) {
                    context.throwGameTestException("После победы: монет " + coins(table.player())
                            + ", всё ещё борется " + table.body().isWrestling());
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Без нажатий рука игрока ложится: 100 / 0,4 = 250 тиков. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_arm", tickLimit = 320)
    public void aSilentPlayerLosesTheArm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = armTable(context, world, manager, Villages.MERCHANT, Nature.EVEN);
        Bout bout = begin(context, world, table, Bouts.Kind.ARM, 1, dice(3));
        context.runAtTick(270, () -> {
            try {
                if (!bout.arm().flatMap(ArmWrestle::outcome).equals(Optional.of(ArmWrestle.Outcome.LOSE))
                        || coins(table.player()) != 9) {
                    context.throwGameTestException("Молчащий игрок: итог " + bout.arm()
                            .flatMap(ArmWrestle::outcome) + ", монет " + coins(table.player()));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /**
     * Ленивый сдаётся, не дожидаясь края: фермер, сила 0,6 — давление 0,5,
     * после восьмого нажатия перевес 6 × 8 + 6 = 54 ≥ 50.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_arm", tickLimit = 300)
    public void theLazyRivalGivesUp(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = armTable(context, world, manager, FarmJob.FARMER, Nature.LAZY);
        Bout bout = begin(context, world, table, Bouts.Kind.ARM, 1, dice(3));
        pressInTime(context, world, bout, 10);
        context.runAtTick(240, () -> {
            try {
                double balance = bout.arm().map(ArmWrestle::balance).orElse(0.0);
                if (!bout.arm().flatMap(ArmWrestle::outcome).equals(Optional.of(ArmWrestle.Outcome.WIN))
                        || balance >= 100) {
                    context.throwGameTestException("Ленивый не сдался раньше края: итог "
                            + bout.arm().flatMap(ArmWrestle::outcome) + " при " + balance);
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    // --- прохожий ---

    /** Утро: житель не спит и окликает. */
    private static final long MORNING = 1_000;

    /** Проигравший последнюю партию окликает «отыграться» — и не тараторит об этом. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_rivals", tickLimit = 140)
    public void theLoserCallsForARematch(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        GamesLedger.get(world).record(table.rival().id(), table.player().getUuid(), Rivalry.Result.LOST,
                EVENING_DAY);
        Passersby.tick(world, manager, EVENING_DAY + 1, MORNING, List.of(table.player()));
        if (spoken(table.body()).filter(key -> key.contains(".rematch.")).isEmpty()) {
            clearTable(world, manager, table);
            context.throwGameTestException("Проигравший не окликнул: " + spoken(table.body()));
        }
        context.runAtTick(100, () -> {
            try {
                Passersby.tick(world, manager, EVENING_DAY + 1, MORNING, List.of(table.player()));
                if (table.body().speech().isPresent()) {
                    context.throwGameTestException("Окликнул второй раз через пять секунд: "
                            + spoken(table.body()));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Выигравший — хвалится. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_rivals")
    public void theWinnerBoasts(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        try {
            GamesLedger.get(world).record(table.rival().id(), table.player().getUuid(),
                    Rivalry.Result.WON, EVENING_DAY);
            Passersby.tick(world, manager, EVENING_DAY + 1, MORNING, List.of(table.player()));
            if (spoken(table.body()).filter(key -> key.contains(".boast.")).isEmpty()) {
                context.throwGameTestException("Выигравший не похвалился: " + spoken(table.body()));
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    /** С незнакомцем житель молчит; ночью молчат все. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_rivals")
    public void aStrangerIsNotHailed(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        try {
            Passersby.tick(world, manager, EVENING_DAY + 1, MORNING, List.of(table.player()));
            if (table.body().speech().isPresent()) {
                context.throwGameTestException("Незнакомца окликнули: " + spoken(table.body()));
            }
            GamesLedger.get(world).record(table.rival().id(), table.player().getUuid(),
                    Rivalry.Result.LOST, EVENING_DAY);
            Passersby.tick(world, manager, EVENING_DAY + 1, 15_000, List.of(table.player()));
            if (table.body().speech().isPresent()) {
                context.throwGameTestException("Окликнули ночью: " + spoken(table.body()));
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    // --- колония ---

    /**
     * В своей колонии — на интерес: ставок нет, монеты не двигаются,
     * а колонист благодарит хозяина за вечер и наутро будет бодрее.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_colony", tickLimit = 120)
    public void aColonyPlaysForFun(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        table.ground().village().setOwner(Owner.of(table.player().getUuid()));
        com.villagepax.screen.GameView view = GamesNet.viewOf(world, table.player(), table.ground().village(),
                table.rival(), EVENING_DAY, EVENING);
        if (view.coins() || !view.stakes().equals(List.of(0))) {
            clearTable(world, manager, table);
            context.throwGameTestException("В своей колонии на монеты: " + view.coins() + ", ставки "
                    + view.stakes());
        }
        Bout bout = begin(context, world, table, Bouts.Kind.DICE, 0, dice(6, 6, 6, 6, 1));
        bout.roll(world);
        bout.roll(world);
        bout.stand(world);
        context.runAtTick(80, () -> {
            try {
                if (coins(table.player()) != 10) {
                    context.throwGameTestException("Игра на интерес двинула монеты: " + coins(table.player()));
                }
                if (spoken(table.body()).filter(key -> key.contains(".thanks.")).isEmpty()) {
                    context.throwGameTestException("Колонист не поблагодарил: " + spoken(table.body()));
                }
                if (!GamesLedger.get(world).cheered(table.rival().id(), EVENING_DAY)) {
                    context.throwGameTestException("Вечер с хозяином не запомнился");
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Вечер с хозяином — наутро на три бодрее, чем у того, кто не играл. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_colony")
    public void anEveningWithTheOwnerCheersUp(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        try {
            Settlement colony = ground.village();
            colony.setOwner(Owner.of(UUID.randomUUID()));
            Citizen glad = hireWithBody(world, colony, FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(18, 2, 18)));
            Citizen plain = hireWithBody(world, colony, FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(19, 2, 18)));
            for (Citizen citizen : List.of(glad, plain)) {
                citizen.setSaturation(40);
                citizen.setHappiness(50);
            }
            GamesLedger.get(world).markCheer(glad.id(), EVENING_DAY);
            com.villagepax.sim.work.Needs.newDay(world, manager, colony, EVENING_DAY + 1);
            int difference = glad.happiness() - plain.happiness();
            if (difference != 3) {
                context.throwGameTestException("Игравший с хозяином бодрее на " + difference + ", ждали 3");
            }
        } finally {
            clearMeadow(world, manager, ground);
        }
        context.complete();
    }

    /** Гость в чужой колонии тоже играет на интерес: колония — не казино. И бодрости за это нет. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_colony", tickLimit = 120)
    public void aGuestPlaysForFunInAColonyToo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        table.ground().village().setOwner(Owner.of(UUID.randomUUID()));
        if (GamesNet.viewOf(world, table.player(), table.ground().village(), table.rival(), EVENING_DAY,
                EVENING).coins()) {
            clearTable(world, manager, table);
            context.throwGameTestException("Гость в колонии играет на монеты");
        }
        Bout bout = begin(context, world, table, Bouts.Kind.DICE, 0, dice(6, 6, 6, 6, 1));
        bout.roll(world);
        bout.roll(world);
        bout.stand(world);
        context.runAtTick(80, () -> {
            try {
                if (coins(table.player()) != 10
                        || GamesLedger.get(world).cheered(table.rival().id(), EVENING_DAY)) {
                    context.throwGameTestException("Гость: монет " + coins(table.player()) + ", бодрость "
                            + GamesLedger.get(world).cheered(table.rival().id(), EVENING_DAY));
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    // --- разговор ---

    /**
     * Щелчок по члену компании вечером — окно игры со ставками по кошельку
     * соперника; днём пустой рукой — отказ вслух с причиной; с предметом
     * в руке — щелчок не съедается: он остаётся предмету.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_talk")
    public void aClickOpensTheTableOrSaysWhyNot(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        Citizen other = hireWithBody(world, table.ground().village(), FarmJob.FARMER,
                context.getAbsolutePos(new BlockPos(21, 2, 26)));
        CitizenEntity otherBody = (CitizenEntity) world.getEntity(other.entityUuid().orElseThrow());
        try {
            Settlement village = table.ground().village();
            Games.Answer evening = Games.answer(world, table.player(), village, table.rival(), false,
                    EVENING_DAY, EVENING);
            if (evening != Games.Answer.WINDOW) {
                context.throwGameTestException("Вечером щелчок по игроку компании: " + evening);
            }
            List<Integer> stakes = GamesNet.viewOf(world, table.player(), village, table.rival(),
                    EVENING_DAY, EVENING).stakes();
            if (!stakes.equals(List.of(1, 2))) {
                context.throwGameTestException("Ставки у фермера с кошельком 4: " + stakes);
            }
            Games.Answer day = Games.answer(world, table.player(), village, table.rival(), false,
                    EVENING_DAY, 8_000);
            if (day != Games.Answer.REFUSED
                    || spoken(table.body()).filter(key -> key.contains(".busy.")).isEmpty()) {
                context.throwGameTestException("Днём пустой рукой: " + day + ", сказал "
                        + spoken(table.body()));
            }
            table.player().getInventory().setStack(table.player().getInventory().selectedSlot,
                    new ItemStack(net.minecraft.item.Items.OAK_PLANKS));
            Games.Answer busyHands = Games.answer(world, table.player(), village, other, false,
                    EVENING_DAY, 8_000);
            if (busyHands != Games.Answer.PASS || otherBody.speech().isPresent()) {
                context.throwGameTestException("С доской в руке днём: " + busyHands + ", сказал "
                        + spoken(otherBody));
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    /**
     * У старейшины своё дело по щелчку — квесты: обычный щелчок остаётся ему,
     * а сыграть с ним — щелчок с Shift.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_talk")
    public void anEldersClickStaysHisOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Table table = table(context, world, manager);
        Citizen elder = hireWithBody(world, table.ground().village(), Villages.ELDER,
                context.getAbsolutePos(new BlockPos(21, 2, 26)));
        try {
            Settlement village = table.ground().village();
            Games.Answer plain = Games.answer(world, table.player(), village, elder, true,
                    EVENING_DAY, EVENING);
            table.player().setSneaking(true);
            Games.Answer crouched = Games.answer(world, table.player(), village, elder, true,
                    EVENING_DAY, EVENING);
            if (plain != Games.Answer.PASS || crouched != Games.Answer.WINDOW) {
                context.throwGameTestException("Щелчок по занятому делом: обычный " + plain
                        + ", с Shift " + crouched);
            }
        } finally {
            clearTable(world, manager, table);
        }
        context.complete();
    }

    /**
     * Монеты только в кошеле-предмете: ставку видно, и платят из кошеля —
     * ничего не теряется.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "games_talk", tickLimit = 120)
    public void stakesArePaidFromAPurseToo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        Citizen merchant = hireWithBody(world, ground.village(), Villages.MERCHANT,
                context.getAbsolutePos(new BlockPos(20, 2, 24)));
        PlayerEntity player = playerAt(context, context.getAbsolutePos(new BlockPos(22, 2, 24)));
        ItemStack purse = new ItemStack(ModItems.PURSE);
        com.villagepax.item.PurseItem.put(purse, 20);
        // Не в руку: рука игрока в проверках разговора должна быть пустой.
        player.getInventory().setStack(9, purse);
        Table table = new Table(ground, merchant,
                (CitizenEntity) world.getEntity(merchant.entityUuid().orElseThrow()), player);
        List<Integer> stakes = GamesNet.viewOf(world, player, ground.village(), merchant,
                EVENING_DAY, EVENING).stakes();
        if (!stakes.equals(List.of(1, 2, 5, 9))) {
            clearTable(world, manager, table);
            context.throwGameTestException("Ставки из кошеля у купца: " + stakes);
        }
        Bout bout = begin(context, world, table, Bouts.Kind.DICE, 5, dice(6, 6, 6, 6, 1));
        bout.roll(world);
        bout.roll(world);
        bout.stand(world);
        context.runAtTick(80, () -> {
            try {
                int left = purseValue(player);
                if (coins(player) != 15 || left != 15) {
                    context.throwGameTestException("После проигрыша пяти: всего " + coins(player)
                            + ", в кошеле " + left + ", ждали 15");
                }
            } finally {
                clearTable(world, manager, table);
            }
            context.complete();
        });
    }

    /** Сколько медяков в кошелях игрока. */
    private static int purseValue(PlayerEntity player) {
        int value = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ModItems.PURSE)) {
                value += com.villagepax.item.PurseItem.valueOf(stack);
            }
        }
        return value;
    }

    // --- лакмус ---

    /**
     * Вечер и день в одной деревне — вся игра с жителями разом.
     * <p>
     * Вечером у места игры компания из четырёх, при игроке она бросает сама;
     * игрок выигрывает в кости у фермера и проигрывает армрестлинг строителю.
     * Наутро проигравший окликает «отыграться», выигравший хвалится. Днём
     * ребёнок зовёт в прятки, игрок находит обоих и получает гостинец.
     * В конце сходятся монеты: у игрока — начальные, плюс выигрыш, минус
     * проигрыш, плюс два медяка гостинца; на складе деревни — проигрыш.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "litmus_games", tickLimit = 700)
    public void anEveningAndADayInAVillage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow ground = meadow(context, world, manager);
        Settlement village = ground.village();
        Citizen farmer = hireWithBody(world, village, FarmJob.FARMER,
                context.getAbsolutePos(new BlockPos(20, 2, 24)), Nature.EVEN);
        Citizen builder = hireWithBody(world, village, BuildJob.BUILDER,
                context.getAbsolutePos(new BlockPos(22, 2, 26)), Nature.EVEN);
        List<Citizen> others = new ArrayList<>();
        for (Nature nature : List.of(Nature.AMBITIOUS, Nature.LAZY, Nature.COWARD, Nature.PIOUS)) {
            others.add(hireWithBody(world, village, FarmJob.FARMER,
                    context.getAbsolutePos(new BlockPos(14 + others.size(), 2, 20)), nature));
        }
        Citizen mother = others.get(0);
        List<Citizen> kids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Citizen kid = someoneWith(Nature.EVEN, "Дитя", com.villagepax.sim.Gender.MALE);
            kid.setLived(0);
            kid.setParents(UUID.randomUUID(), mother.id());
            kid.setPosition(net.minecraft.util.math.Vec3d.ofBottomCenter(
                    context.getAbsolutePos(new BlockPos(16 + i, 2, 12))));
            village.addCitizen(kid);
            com.villagepax.entity.CitizenSpawner.spawnBody(world, village, kid);
            kids.add(kid);
        }
        PlayerEntity player = playerAt(context, context.getAbsolutePos(new BlockPos(21, 2, 25)));
        player.getInventory().setStack(9, new ItemStack(ModItems.COIN, 10));
        Table withFarmer = new Table(ground, farmer,
                (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow()), player);
        Table withBuilder = new Table(ground, builder,
                (CitizenEntity) world.getEntity(builder.entityUuid().orElseThrow()), player);
        int storeBefore = Warehouse.of(world, village).count(ModItems.COIN);

        // Вечер: компания из четырёх, и при игроке она бросает сама.
        List<Citizen> company = Company.of(world, village, EVENING_DAY, Schedule.LEISURE, EVENING, List.of(player));
        if (company.size() != 4) {
            clearMeadow(world, manager, ground);
            context.throwGameTestException("Вечером за столом " + company.size() + ", ждали четверых");
            return;
        }
        Company.tick(world, manager, village, EVENING_DAY, EVENING, List.of(player));
        long threw = village.citizens().stream().flatMap(c -> c.entityUuid().stream())
                .map(world::getEntity).filter(CitizenEntity.class::isInstance).map(CitizenEntity.class::cast)
                .filter(body -> spoken(body).filter(key -> key.contains(".ambient_throw.")).isPresent())
                .count();
        if (threw != 1) {
            clearMeadow(world, manager, ground);
            context.throwGameTestException("Компания при игроке не бросила сама: " + threw);
            return;
        }

        // Кости с фермером: встал на семнадцати, фермер перебрал — +2.
        Bout dice = begin(context, world, withFarmer, Bouts.Kind.DICE, 2, dice(6, 6, 5, 6, 6, 4, 6));
        dice.roll(world);
        dice.roll(world);
        dice.roll(world);
        dice.stand(world);

        // Армрестлинг со строителем, молча: рука ложится — −1.
        context.runAtTick(70, () -> {
            if (!dice.settled().equals(java.util.OptionalInt.of(2))) {
                clearMeadow(world, manager, ground);
                context.throwGameTestException("Кости с фермером: " + dice.settled());
            }
            // Игрок подходит к строителю туда, где тот стоит: за минуту он мог отойти.
            player.setPosition(withBuilder.body().getPos().add(1, 0, 0));
            begin(context, world, withBuilder, Bouts.Kind.ARM, 1, dice(3));
        });

        // Утро: проигравший фермер окликает, выигравший строитель хвалится.
        context.runAtTick(320, () -> {
            CitizenEntity farmerBody = withFarmer.body();
            CitizenEntity builderBody = withBuilder.body();
            // Мир проверок живёт своим временем, и за день оба могли отойти:
            // ставим их у дороги, по которой идёт игрок.
            farmerBody.refreshPositionAndAngles(player.getX() + 1, player.getY(), player.getZ(), 0f, 0f);
            builderBody.refreshPositionAndAngles(player.getX() - 1, player.getY(), player.getZ(), 0f, 0f);
            Passersby.tick(world, manager, EVENING_DAY + 1, 1_000, List.of(player));
            if (spoken(farmerBody).filter(key -> key.contains(".rematch.")).isEmpty()
                    || spoken(builderBody).filter(key -> key.contains(".boast.")).isEmpty()) {
                clearMeadow(world, manager, ground);
                context.throwGameTestException("Наутро: фермер " + spoken(farmerBody) + ", строитель "
                        + spoken(builderBody));
            }
        });
        context.runAtTick(330, () -> {
            // Днём игрок подходит к детям — туда, где они есть: за день они
            // успели убежать от места, где появились.
            CitizenEntity kidBody = (CitizenEntity) world.getEntity(kids.get(0).entityUuid().orElseThrow(() ->
                    new IllegalStateException("у ребёнка нет тела")));
            player.setPosition(kidBody.getPos().add(2, 0, 0));
            if (!HideAndSeek.invite(world, village, EVENING_DAY + 1, 3_000, List.of(player))) {
                clearMeadow(world, manager, ground);
                context.throwGameTestException("Днём дети не позвали в прятки");
            }
            HideAndSeek.clicked(world, player, village, kids.get(0), EVENING_DAY + 1, 3_000);
        });
        context.runAtTick(330 + HideAndSeek.COUNTDOWN + 10, () -> {
            for (Citizen kid : kids) {
                HideAndSeek.clicked(world, player, village, kid, EVENING_DAY + 1, 3_000);
            }
        });
        context.runAtTick(330 + HideAndSeek.COUNTDOWN + 25, () -> {
            try {
                int coins = Coins.total(player.getInventory());
                int stored = Warehouse.of(world, village).count(ModItems.COIN) - storeBefore;
                if (coins != 10 + 2 - 1 + 2 || stored != 1
                        || player.getInventory().count(net.minecraft.item.Items.COOKIE) != 1) {
                    context.throwGameTestException("Итог дня: у игрока " + coins + " (ждали 13), на складе +"
                            + stored + " (ждали 1), печенья "
                            + player.getInventory().count(net.minecraft.item.Items.COOKIE));
                }
            } finally {
                HideAndSeek.at(village.id()).ifPresent(session -> HideAndSeek.stop(world, session));
                Bouts.of(player.getUuid()).ifPresent(bout -> Bouts.cancel(world, bout));
                clearMeadow(world, manager, ground);
            }
            context.complete();
        });
    }
}
