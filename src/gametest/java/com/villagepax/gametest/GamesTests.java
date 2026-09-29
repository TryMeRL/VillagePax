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
import com.villagepax.sim.games.Company;
import com.villagepax.sim.games.GameSpot;
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

    /** Деревня народа на своём лугу: трава поверх земли мира, ратуша-блок в углу. */
    private record Meadow(Settlement village, BlockPos hall, List<BlockPos> grass) {
    }

    private static Meadow meadow(TestContext context, ServerWorld world, SettlementManager manager) {
        List<BlockPos> grass = new ArrayList<>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                grass.add(at);
            }
        }
        BlockPos hall = context.getAbsolutePos(new BlockPos(24, 2, 24));
        Settlement village = colonyWithBuilder(world, manager, hall);
        village.setOwner(Owner.AUTONOMOUS);
        return new Meadow(village, hall, grass);
    }

    private static void clearMeadow(ServerWorld world, SettlementManager manager, Meadow meadow) {
        for (BlockPos at : manager.decorOf(meadow.village().id())) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
        discardBodies(world, meadow.village());
        manager.remove(meadow.village().id());
        world.setBlockState(meadow.hall(), Blocks.AIR.getDefaultState());
        meadow.grass().forEach(at -> world.setBlockState(at, Blocks.AIR.getDefaultState()));
    }

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

    /** Что житель сейчас говорит — ключом словаря. */
    private static Optional<String> spoken(CitizenEntity body) {
        return body.speech().map(Text::getContent)
                .filter(TranslatableTextContent.class::isInstance)
                .map(content -> ((TranslatableTextContent) content).getKey());
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
}
