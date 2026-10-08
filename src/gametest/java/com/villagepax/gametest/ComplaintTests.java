package com.villagepax.gametest;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.BuildProgress;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Hazards;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.QuestNet;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.life.Mortality;
import com.villagepax.sim.life.Life;
import com.villagepax.sim.life.Families;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.faith.Offering;
import com.villagepax.sim.faith.Miracles;
import com.villagepax.sim.faith.Faith;
import com.villagepax.sim.faith.Blessings;
import com.villagepax.sim.faith.Artifacts;
import com.villagepax.item.ArtifactItem;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.faith.Domain;
import com.villagepax.core.config.Config;
import com.villagepax.core.config.Configs;
import com.villagepax.sim.work.Needs;
import com.villagepax.core.quest.Quest;
import net.minecraft.inventory.SimpleInventory;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.quest.Progress;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.screen.Advice;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.work.Assignments;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.BlockState;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.work.BuilderJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Проверки по следам жалоб из игры: огонь, пустые места и всё, что игрок поймал сам.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class ComplaintTests extends GameTestSupport {

    // --- по следам жалоб из игры: огонь и пустые места ---

    /**
     * Житель выходит из огня, а не сгорает в нём.
     * <p>
     * Из настоящей игры: строитель погиб, перестраивая дом, — сгорел
     * на очаге. Это не невезение, а ванильная ловушка: узел пути в огне
     * стоит шестнадцать шагов, но <b>из</b> него путь почти не строится,
     * и моб, оказавшийся в костре, стоит и горит до смерти.
     * <p>
     * Проверяется ровно это: жителя ставят в горящий костёр и дают
     * рефлексу сработать. Он обязан оказаться <b>не в огне</b> и живым.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire")
    public void citizenStepsOutOfTheFire(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (int x = -2; x <= 4; x++) {
                for (int z = -2; z <= 4; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            BlockPos hearth = context.getAbsolutePos(new BlockPos(3, 2, 3));
            world.setBlockState(hearth, Blocks.CAMPFIRE.getDefaultState());
            floor.add(hearth);

            Citizen citizen = hireWithBody(world, colony, BuildJob.BUILDER, hearth);
            CitizenEntity body = (CitizenEntity) world
                    .getEntity(citizen.entityUuid().orElseThrow());
            body.refreshPositionAndAngles(hearth.getX() + 0.5, hearth.getY(),
                    hearth.getZ() + 0.5, 0f, 0f);

            if (!Hazards.standingHurts(world, body.getBlockPos())) {
                context.throwGameTestException("Тест не поставил жителя в огонь: "
                        + world.getBlockState(body.getBlockPos()));
                return;
            }

            if (!body.stepOutOfTrouble()) {
                context.throwGameTestException("Житель остался в костре: рефлекс не сработал");
                return;
            }
            if (Hazards.standingHurts(world, body.getBlockPos())) {
                context.throwGameTestException("Житель вышел из огня в огонь: "
                        + body.getBlockPos().toShortString());
            }
            if (!body.isAlive()) {
                context.throwGameTestException("Житель не пережил собственного очага");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Блок не ставится в того, кто в этой клетке стоит.
     * <p>
     * Вторая половина той же смерти: костёр из схемы ставится <b>внутрь</b>
     * жителя, и он оказывается в огне, ничего не сделав. Проверяется, что
     * стройка сперва отодвигает своего, а если клетка всё равно занята —
     * ждёт, а не ставит.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "fire")
    public void nothingIsBuiltInsideTheLiving(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, new Identifier("villagepax", "norman/house_lvl1"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(7, 2, 7));
        List<BlockPos> floor = new ArrayList<>();

        try {
            for (int x = -2; x <= 9; x++) {
                for (int z = -2; z <= 9; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 2, 0));
            Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 1, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, house);

                // Житель встаёт ровно туда, где по плану встанет очаг.
                BlockPos hearth = hearthOf(house, site);
                if (hearth == null) {
                    context.throwGameTestException("В схеме дома нет очага — проверять нечего");
                    return;
                }

                Citizen citizen = hireWithBody(world, colony, BuildJob.BUILDER, hearth);
                CitizenEntity body = (CitizenEntity) world
                        .getEntity(citizen.entityUuid().orElseThrow());
                body.refreshPositionAndAngles(hearth.getX() + 0.5, hearth.getY(),
                        hearth.getZ() + 0.5, 0f, 0f);

                // Стройка гонится целиком, без ограничения вытянутой руки.
                BuildJob.advance(world, manager, colony.id(), site.id(), 2_000);

                if (world.getBlockState(hearth).isOf(Blocks.CAMPFIRE)
                        && new net.minecraft.util.math.Box(hearth)
                                .intersects(body.getBoundingBox())) {
                    context.throwGameTestException("Костёр поставлен прямо в жителя: "
                            + hearth.toShortString());
                }
                if (!body.isAlive()) {
                    context.throwGameTestException("Житель не пережил стройку");
                }
            } finally {
                demolish(world, site, house);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Земля под пологом леса находится, а верхушка листвы — не земля.
     * <p>
     * Это причина, по которой деревни <b>не появлялись вовсе</b>. Поиск
     * места считал высоту по генератору, то есть по рельефу без деревьев,
     * а основание деревни спрашивало у мира карту высот, куда входит
     * листва. Игрок приходил по указанным координатам, стоял на месте
     * полминуты — и не видел ничего: опоры на верхушке дерева нет,
     * и деревня молча отказывалась вставать. Ровно так это и выглядело
     * в его логе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire")
    public void groundIsFoundUnderTheCanopy(TestContext context) {
        ServerWorld world = context.getWorld();
        List<BlockPos> planted = new ArrayList<>();

        try {
            BlockPos soil = context.getAbsolutePos(new BlockPos(2, 1, 2));
            world.setBlockState(soil, Blocks.GRASS_BLOCK.getDefaultState());
            planted.add(soil);

            // Полог: листва в шести блоках над землёй, как в джунглях.
            for (int y = 7; y <= 9; y++) {
                BlockPos leaf = context.getAbsolutePos(new BlockPos(2, y, 2));
                world.setBlockState(leaf, Blocks.JUNGLE_LEAVES.getDefaultState());
                planted.add(leaf);
            }

            BlockPos found = Ground.buildableAt(world, soil.getX(), soil.getZ()).orElse(null);
            if (found == null) {
                context.throwGameTestException("Под пологом земля не нашлась вовсе — "
                        + "деревня в лесу не встанет никогда");
                return;
            }
            if (found.getY() != soil.getY() + 1) {
                context.throwGameTestException("Землёй сочтено " + found.toShortString()
                        + " вместо клетки над дёрном " + soil.up().toShortString());
            }

            // А в стволе строить нельзя: там нет ни воздуха, ни опоры.
            BlockPos trunk = context.getAbsolutePos(new BlockPos(4, 1, 4));
            world.setBlockState(trunk, Blocks.GRASS_BLOCK.getDefaultState());
            planted.add(trunk);
            for (int y = 2; y <= 6; y++) {
                BlockPos log = context.getAbsolutePos(new BlockPos(4, y, 4));
                world.setBlockState(log, Blocks.JUNGLE_LOG.getDefaultState());
                planted.add(log);
            }
            BlockPos onTrunk = Ground.buildableAt(world, trunk.getX(), trunk.getZ())
                    .orElse(null);
            if (onTrunk != null && onTrunk.getY() > trunk.getY()) {
                context.throwGameTestException("В стволе дерева нашлась «земля» выше дёрна: "
                        + onTrunk.toShortString() + ", там "
                        + world.getBlockState(onTrunk.down()).getBlock());
            }
        } finally {
            for (BlockPos at : planted) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
        }

        context.complete();
    }

    /**
     * Деревня встаёт под пологом леса и строит по земле, а не по кронам.
     * <p>
     * Это и есть та жалоба целиком: «деревни всё ещё не ищутся». Поиск
     * звал игрока по координатам, тот приходил, стоял полминуты — и видел
     * пустое место. Причина была в двух разных ответах на вопрос «где
     * тут земля»: поиск считал по рельефу без деревьев, а деревня искала
     * опору по карте высот мира, куда входит листва. Под пологом джунглей
     * опоры не находилось ни в одной колонне, и деревня молча отказывалась
     * вставать.
     * <p>
     * Проверяется на настоящем пологе: поляна с листвой в шести блоках
     * над землёй. Деревня обязана встать и разметить здания <b>у земли</b>.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "fire")
    public void villageRisesUnderTheCanopy(TestContext context) {
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

                    // Полог: сплошная листва в шести блоках над землёй.
                    BlockPos leaf = context.getAbsolutePos(new BlockPos(x, 15, z));
                    world.setBlockState(leaf, Blocks.JUNGLE_LEAVES.getDefaultState());
                    meadow.add(leaf);
                }
            }

            // И стволы: в настоящих джунглях полог держится на деревьях,
            // а не висит в воздухе. Ствол — твёрдая колонна, в которой
            // строить нельзя, и её нельзя путать с «здесь нет земли».
            for (int x = -18; x <= 18; x += 4) {
                for (int z = -18; z <= 18; z += 4) {
                    if (Math.abs(x) <= 4 && Math.abs(z) <= 4) {
                        continue;   // серединку оставляем под ратушу
                    }
                    for (int y = 9; y <= 14; y++) {
                        BlockPos log = context.getAbsolutePos(new BlockPos(x, y, z));
                        world.setBlockState(log, Blocks.JUNGLE_LOG.getDefaultState());
                        meadow.add(log);
                    }
                }
            }

            village = Villages.found(world, MAYA, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Под пологом деревня не встала — "
                        + "ровно то, на что жаловался игрок. Помеха="
                        + whoBlocks(manager, centre));
                return;
            }

            int ground = centre.getY();
            for (Building building : village.buildings()) {
                int high = building.anchor().getY() - ground;
                if (high > 2) {
                    context.throwGameTestException("Здание " + building.type()
                            + " размечено на " + high + " блоков выше земли — это крона");
                }
            }
            if (village.buildings().size() < 3) {
                context.throwGameTestException("Под пологом встала неполная деревня: зданий "
                        + village.buildings().size());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Совет не замолкает, когда колония выжила.
     * <p>
     * Прежде лестница кончалась на «нечего строить», и колония, у которой
     * всё построено, получала пустую строку — ровно в тот миг, когда
     * у мода начинается долгая игра. Игрок решал, что мод кончился.
     * <p>
     * Проверяется и <b>порядок</b>: беда всегда перебивает направление.
     * Лестница советов — это и есть сам совет, и перепутанные ступени
     * отправили бы голодную колонию строить храм.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theAdviceNeverFallsSilent(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = colony.owner().player().orElseThrow();

        // Ратуша зданием, а не только блоком: по ней считается и ступень,
        // и последний совет «подними ратушу». Ступень ставится деревней —
        // с неё открывается храм, а без него две ступени лестницы
        // проверка просто не увидела бы.
        raisedTownHall(colony, hall);
        colony.setLevel(SettlementLevel.VILLAGE);

        try {
            // Голодная колония получает совет про еду, а не про соседей:
            // беда перебивает направление.
            Warehouse warehouse = Warehouse.of(world, colony);
            String hungry = Advice.nextStep(world, colony).orElse(null);
            if (!"villagepax.advice.no_food".equals(hungry)) {
                context.throwGameTestException("Голодной колонии советуют не еду, а «"
                        + hungry + "»");
            }

            warehouse.add(new ItemStack(Items.BREAD, 32));

            // Накормлена, но одинока: совет обязан позвать к соседям,
            // а не замолчать.
            String next = Advice.nextStep(world, colony).orElse(null);
            if (next == null) {
                context.throwGameTestException("Совет замолчал у выжившей колонии");
            }
            if (!Advice.keys().contains(next)) {
                context.throwGameTestException("Совет вне лестницы: " + next);
            }

            // И на каждой следующей ступени он тоже что-то говорит:
            // пройдём лестницу до конца, снимая причину за причиной.
            List<String> said = new ArrayList<>();
            for (int step = 0; step < Advice.keys().size(); step++) {
                String advice = Advice.nextStep(world, colony).orElse(null);
                if (advice == null) {
                    break;
                }
                if (said.contains(advice)) {
                    context.throwGameTestException("Совет «" + advice
                            + "» повторился, хотя причину сняли: " + said);
                }
                said.add(advice);
                if (!relieve(world, manager, colony, player, advice)) {
                    break;
                }
            }

            // Долгая игра названа поимённо, и это главное в проверке:
            // без этих ступеней лестница обрывалась ровно там, где у мода
            // начинается вторая половина, и «совет не замолкает» проверялось
            // бы советами про еду и кровати — то есть тем, что работало
            // и раньше.
            for (String rung : List.of("villagepax.advice.empty_purse",
                    "villagepax.advice.coin_leaks", "villagepax.advice.no_neighbours",
                    "villagepax.advice.no_temple", "villagepax.advice.no_faith")) {
                if (!said.contains(rung)) {
                    context.throwGameTestException("Совет «" + rung
                            + "» не прозвучал ни разу. Сказано было: " + said);
                }
            }

            // И последним словом — направление, а не пустота.
            String last = Advice.nextStep(world, colony).orElse(null);
            if (!"villagepax.advice.raise_the_hall".equals(last)) {
                context.throwGameTestException("У здоровой колонии совет замолчал: " + last);
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Двое взрослых сходятся, и у них родится ребёнок.
     * <p>
     * Главная проверка всей затеи: до неё колония <b>набиралась</b>
     * пришлыми, у которых нет ни прошлого, ни родни. Здесь у ребёнка
     * есть отец, мать и прозвище по отцу — то есть он отличим от
     * следующего, а в этом и был весь смысл.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void twoAdultsWedAndHaveAChild(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = household(world, manager, context, hall);

        try {
            if (Families.wed(world, colony).isEmpty()) {
                context.throwGameTestException("Двое свободных взрослых не сошлись");
            }

            Citizen husband = colony.citizens().get(0);
            if (husband.spouse().isEmpty()) {
                context.throwGameTestException("Женился, а записи нет");
            }
            Citizen wife = Families.spouseOf(colony, husband).orElse(null);
            if (wife == null || wife.spouse().isEmpty()) {
                context.throwGameTestException("Запись о браке односторонняя");
                return;
            }

            int before = colony.population();
            Citizen child = Families.birth(world, colony, new java.util.Random(7)).orElse(null);
            if (child == null) {
                context.throwGameTestException("У пары не родился ребёнок");
                return;
            }
            if (colony.population() != before + 1) {
                context.throwGameTestException("Ребёнок родился мимо колонии");
            }
            if (Ages.daysOf(child) != 0 || !Ages.isChild(child)) {
                context.throwGameTestException("Новорождённому " + Ages.daysOf(child)
                        + " дней, и он " + Ages.stageOf(child).id());
            }
            if (child.parents().size() != 2) {
                context.throwGameTestException("У ребёнка не двое родителей: "
                        + child.parents());
            }
            if (child.profession().isPresent()) {
                context.throwGameTestException("Новорождённого сразу взяли на работу: "
                        + child.profession().get());
            }
            // Прозвище по отцу: примета народа, взятая из датапака.
            if (child.lastName().isBlank()) {
                context.throwGameTestException("Ребёнок остался без имени по отцу");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ребёнок вырастает и сам берётся за дело.
     * <p>
     * Без этого вчерашний ребёнок сидел бы без ремесла навсегда: ремесло
     * в моде раздаётся пришедшим извне и рукой игрока, а выросшему
     * не досталось бы ни того, ни другого.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void theChildGrowsUpAndTakesATrade(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = household(world, manager, context, hall);

        try {
            Citizen child = evenNewborn("Тибо", "", NORMAN, Gender.MALE);
            child.setLived(0);
            colony.addCitizen(child);

            if (Assignments.set(world, manager, colony, child.id(),
                    Optional.of(FarmJob.FARMER)) != Assignments.Result.TOO_YOUNG) {
                context.throwGameTestException("Ребёнку дали ремесло");
            }

            // Прожить детство день за днём — ровно так, как это делает мир.
            for (int day = 0; day < Ages.grownAt(); day++) {
                Families.newDay(world, colony, new java.util.Random(day));
            }

            if (!Ages.isAdult(child)) {
                context.throwGameTestException("За " + Ages.grownAt() + " дней не вырос: "
                        + Ages.stageOf(child).id() + ", дней " + Ages.daysOf(child));
            }
            if (child.profession().isEmpty()) {
                context.throwGameTestException("Вырос и остался без дела");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Родня не женится.
     * <p>
     * Правило скучное, но его отсутствие игрок заметил бы сразу —
     * и не так, как хотелось бы. Проверяются обе стороны: и «мои
     * родители», и «общий родитель».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void kinDoNotWed(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen father = colony.citizens().get(0);
            father.setLived(Ages.grownAt());

            Citizen mother = evenNewborn("Аделиза", "", NORMAN, Gender.FEMALE);
            mother.setLived(Ages.grownAt());
            colony.addCitizen(mother);

            Citizen son = evenNewborn("Тибо", "", NORMAN, Gender.MALE);
            son.setLived(Ages.grownAt());
            son.setParents(father.id(), mother.id());
            colony.addCitizen(son);

            Citizen daughter = evenNewborn("Сибилла", "", NORMAN, Gender.FEMALE);
            daughter.setLived(Ages.grownAt());
            daughter.setParents(father.id(), mother.id());
            colony.addCitizen(daughter);

            if (!Families.areKin(son, daughter)) {
                context.throwGameTestException("Брат с сестрой не считаются роднёй");
            }
            if (!Families.areKin(son, mother)) {
                context.throwGameTestException("Сын с матерью не считаются роднёй");
            }

            // Свободных не-родственников в колонии двое: отец и мать.
            // Они и сойдутся, а дети — нет.
            Families.wed(world, colony);
            if (son.spouse().isPresent() && son.spouse().get().equals(daughter.id())) {
                context.throwGameTestException("Брат женился на сестре");
            }
            if (father.spouse().isEmpty() || mother.spouse().isEmpty()) {
                context.throwGameTestException("Неродные друг другу не сошлись");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Роды идут в очередь по младшему ребёнку.
     * <p>
     * Памяти о прошлых родах в моде нет — кодек поселения полон, — и её
     * заменяет сам младший ребёнок: он ходит по колонии и помнит свой
     * возраст. Без этого правила пара рожала бы каждые сутки, и колония
     * упиралась бы в предел населения за неделю.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void birthsWaitForTheYoungestToGrow(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = household(world, manager, context, hall);

        try {
            Families.wed(world, colony);
            Citizen first = Families.birth(world, colony, new java.util.Random(1)).orElse(null);
            if (first == null) {
                context.throwGameTestException("Первый ребёнок не родился");
                return;
            }

            // Крыша над головой в этой проверке не при чём: её стережёт
            // своя проверка, а здесь роды упёрлись бы в неё раньше, чем
            // в очередь по младшему, и проверка подтверждала бы не то.
            colony.citizens().forEach(citizen -> citizen.setBed(null));

            if (Families.birth(world, colony, new java.util.Random(2)).isPresent()) {
                context.throwGameTestException("Второй ребёнок родился в тот же день");
            }

            // Подождём, пока младший подрастёт.
            for (int day = 0; day < Families.BETWEEN_BIRTHS; day++) {
                colony.citizens().forEach(Citizen::liveADay);
            }
            colony.citizens().forEach(citizen -> citizen.setBed(null));
            if (Families.birth(world, colony, new java.util.Random(3)).isEmpty()) {
                context.throwGameTestException("Младший подрос, а второго так и нет");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Без крыши детей не бывает.
     * <p>
     * Ребёнок ест и спит ровно как взрослый, и притворяться, будто он
     * бесплатен, мод не станет: условия родов — те же, по которым
     * в колонию приходит пришлый. Иначе колония рожала бы под открытым
     * небом и упиралась бы в голод вместо предела населения.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void thereAreNoChildrenWithoutARoof(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = household(world, manager, context, hall);

        try {
            Families.wed(world, colony);

            // Занять все места для сна: свободных не осталось. Бездомным
            // в этом моде считается тот, у кого нет КРОВАТИ, а не записи
            // о доме, — и роды спрашивают именно кровати.
            BlockPos somewhere = hall.up();
            colony.citizens().forEach(citizen -> citizen.setBed(somewhere));

            if (Families.birth(world, colony, new java.util.Random(4)).isPresent()) {
                context.throwGameTestException("Ребёнок родился, хотя спать негде");
            }

            colony.citizens().forEach(citizen -> citizen.setBed(null));
            if (Families.birth(world, colony, new java.util.Random(4)).isEmpty()) {
                context.throwGameTestException("Место освободилось, а ребёнка нет");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Умирает старейший, и вдова снова свободна.
     * <p>
     * Вторая половина важнее первой. Оставленная запись о супруге — не
     * мелочь: вдова с мёртвым мужем в записи не выйдет замуж больше
     * никогда, и колония тихо перестанет расти. Такую поломку в игре
     * не видно вовсе — видно только то, что детей больше нет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void theEldestDiesAndTheWidowIsFreed(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = household(world, manager, context, hall);

        try {
            Families.wed(world, colony);
            Citizen husband = colony.citizens().get(0);
            Citizen wife = Families.spouseOf(colony, husband).orElseThrow();

            // Молодой не умирает, даже когда смертность включена.
            if (Mortality.newDay(world, colony).isPresent()) {
                context.throwGameTestException("Умер тот, кто не дожил");
            }

            husband.setLived(Ages.diesAt());
            Citizen gone = Mortality.newDay(world, colony).orElse(null);
            if (gone == null || !gone.id().equals(husband.id())) {
                context.throwGameTestException("Умер не тот: " + gone);
            }
            if (colony.citizen(husband.id()).isPresent()) {
                context.throwGameTestException("Умерший остался в списке жителей");
            }
            if (wife.spouse().isPresent()) {
                context.throwGameTestException("Вдова осталась с записью о муже — "
                        + "замуж больше не выйдет никогда");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Смертность выключается настройкой.
     * <p>
     * Обещано дизайн-документом с первого дня. Заодно это проверка того,
     * что выключатель <b>действительно</b> выключает: настройка, которая
     * ничего не меняет, хуже отсутствующей.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void mortalityCanBeSwitchedOff(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = household(world, manager, context, hall);

        try {
            colony.citizens().forEach(citizen -> citizen.setLived(Ages.diesAt() + 10));

            Configs.override(new Config(true, 0, 1.0,
                    Config.DEFAULT.hungerWarnDays(), Config.DEFAULT.hungerLeaveDays(),
                    Config.DEFAULT.villageTradePerDay(), Config.DEFAULT.villageIncomePerDay(),
                    Config.DEFAULT.roadReserve(), Config.DEFAULT.ticksPerDecision(),
                    true, true, Config.DEFAULT.carrySlots(), true,
                    Config.DEFAULT.childDays(), Config.DEFAULT.lifeDays(), false,
                    Config.DEFAULT.structureDistanceChunks(),
                    Config.DEFAULT.protectColonies()));

            int before = colony.population();
            Life.newDay(world, manager, colony, new java.util.Random(5));
            if (colony.population() < before) {
                context.throwGameTestException("Смертность выключена, а кто-то умер");
            }

            Configs.override(Config.DEFAULT);
            Life.newDay(world, manager, colony, new java.util.Random(6));
            if (colony.population() >= before) {
                context.throwGameTestException("Смертность включена, а никто не умер: "
                        + colony.population() + " из " + before);
            }
        } finally {
            Configs.override(Config.DEFAULT);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Старик работает вполсилы — тем же замедлением, что и недовольный.
     * <p>
     * Одно на двоих нарочно: два разных однажды сложились бы, и
     * недовольный старик встал бы на месте.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "life")
    public void theElderWorksAtHalfStrength(TestContext context) {
        // Ровный нарочно: замедление зависит и от характера, а проверять
        // здесь надо годы. С {@code newborn} житель раз в пять рождался бы
        // ленивым, и проверка мерцала бы, обвиняя старость в чужом.
        Citizen young = grownWith(Nature.EVEN, "Роллон", Gender.MALE);
        if (!Needs.worksAtFullStrength(young)) {
            context.throwGameTestException("Взрослый работает вполсилы ни с того ни с сего");
        }

        young.setLived(Ages.oldAt());
        if (Needs.worksAtFullStrength(young)) {
            context.throwGameTestException("Старик работает в полную силу");
        }

        // А тот, кто возраста не помнит, старым не становится никогда:
        // иначе все, кто строил колонию до этой правки, слегли бы разом.
        Citizen oldTimer = someoneWith(Nature.EVEN, "Фульк", Gender.MALE);
        for (int day = 0; day < Ages.diesAt() * 2; day++) {
            oldTimer.liveADay();
        }
        if (!Needs.worksAtFullStrength(oldTimer)) {
            context.throwGameTestException("Старожил мира состарился, хотя возраста у него нет");
        }

        context.complete();
    }

    /**
     * Трус не возьмёт меча — ни от игрока, ни от колонии.
     * <p>
     * Отказ, а не молчание: кнопка ремесла стоит у каждого жителя, и трус
     * в списке ничем от храбреца не отличается. Вторая половина важнее
     * первой: если бы колония назначала стражу сама, не спрашивая
     * характера, правило существовало бы только для игрока — то есть
     * не существовало бы вовсе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "natures")
    public void theCowardWillNotTakeASword(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen coward = grownWith(Nature.COWARD, "Тибо", Gender.MALE);
            Citizen steady = grownWith(Nature.EVEN, "Роллон", Gender.MALE);
            colony.addCitizen(coward);
            colony.addCitizen(steady);

            if (Assignments.set(world, manager, colony, coward.id(),
                    Optional.of(Villages.GUARD)) != Assignments.Result.WRONG_NATURE) {
                context.throwGameTestException("Трусу дали меч: " + coward.profession());
            }
            if (coward.profession().isPresent()) {
                context.throwGameTestException("Отказали, а ремесло всё же записали: "
                        + coward.profession().get());
            }
            // Тот же приказ тому же ремеслу, но другому жителю: без этого
            // проверка согласилась бы и с колонией, где стражу не дают
            // никому вовсе.
            if (Assignments.set(world, manager, colony, steady.id(),
                    Optional.of(Villages.GUARD)) != Assignments.Result.DONE) {
                context.throwGameTestException("Ровному тоже не дали меча");
            }
            // И мирное ремесло трусу по-прежнему даётся: отказ про меч,
            // а не про характер вообще.
            if (Assignments.set(world, manager, colony, coward.id(),
                    Optional.of(FarmJob.FARMER)) != Assignments.Result.DONE) {
                context.throwGameTestException("Трусу отказали в мотыге");
            }

            // А теперь то же самое рукой колонии. Оставляем незанятой одну
            // стражу: всё, что нужнее её, уже роздано.
            Settlement bare = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Пусто",
                    hall.add(3000, 0, 3000));
            manager.add(bare);
            try {
                for (Identifier craft : ProfessionManager.byHiringPriority()) {
                    if (Villages.GUARD.equals(craft)) {
                        continue;
                    }
                    Citizen filler = evenNewborn("Занято", "", NORMAN, Gender.FEMALE);
                    filler.setProfession(craft);
                    bare.addCitizen(filler);
                }

                Citizen braveOne = someoneWith(Nature.EVEN, "Гийом", Gender.MALE);
                if (!Housing.neededProfession(bare, braveOne)
                        .filter(Villages.GUARD::equals).isPresent()) {
                    context.throwGameTestException("Колонии не нужна стража даже от храбреца: "
                            + Housing.neededProfession(bare, braveOne));
                }
                Citizen scaredOne = someoneWith(Nature.COWARD, "Одон", Gender.MALE);
                if (Housing.neededProfession(bare, scaredOne)
                        .filter(Villages.GUARD::equals).isPresent()) {
                    context.throwGameTestException("Колония назначила трусу стражу сама");
                }
            } finally {
                manager.remove(bare.id());
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Набожный сам носит на алтарь — и несёт то, чего не жалко.
     * <p>
     * Это единственный способ, которым вера в моде растёт без игрока,
     * и он же — причина держать храм. Проверяются обе половины решения:
     * берётся <b>самое дешёвое</b> из того, что боги принимают (иначе
     * набожный вынес бы эль и резной камень), и <b>дневной черёд бога
     * не занимается</b> — иначе суточный ход, случающийся на рассвете,
     * молча отнимал бы у игрока его жертву.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "natures", tickLimit = 600)
    public void thePiousBringOfferingsThemselves(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic chapelPlan = schematic(context, CHAPEL_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building chapel = plan(colony, anchor, CHAPEL_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, chapelPlan);
            if (BuildJob.advance(world, manager, colony.id(), chapel.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Часовня не достроилась");
                return;
            }

            // Колония из одного набожного: строитель родился с каким
            // придётся характером, и второй набожный в ней сделал бы
            // числа в проверке неопределёнными.
            for (Citizen anyone : List.copyOf(colony.citizens())) {
                colony.removeCitizen(anyone.id());
            }
            Citizen pious = grownWith(Nature.PIOUS, "Аделиза", Gender.FEMALE);
            colony.addCitizen(pious);

            Warehouse warehouse = Warehouse.of(world, colony);
            warehouse.add(new ItemStack(Items.COBBLESTONE, 1));
            warehouse.add(new ItemStack(ModItems.ALE, 1));

            long today = Schedule.dayOf(world.getTimeOfDay());
            Natures.newDay(world, colony);

            if (colony.favourOf(MASON) != 1) {
                context.throwGameTestException("Набожный отнёс камень, а благосклонность "
                        + colony.favourOf(MASON));
            }
            if (colony.favourOf(SOWER) != 0) {
                context.throwGameTestException("Набожный вынес эль: у сеятеля "
                        + colony.favourOf(SOWER));
            }
            if (Warehouse.of(world, colony).count(ModItems.ALE) != 1) {
                context.throwGameTestException("Эль со склада всё-таки ушёл");
            }
            if (Warehouse.of(world, colony).count(Items.COBBLESTONE) != 0) {
                context.throwGameTestException("Благосклонность выросла, а камень на месте: "
                        + "жертва взялась из воздуха");
            }
            if (colony.offeredToday(MASON, today)) {
                context.throwGameTestException("Житель занял дневной черёд бога — "
                        + "игроку сегодня положить уже нечего");
            }

            // А без алтаря носить некуда, и ничего не происходит.
            Settlement godless = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Без храма",
                    hall.add(4000, 0, 4000));
            manager.add(godless);
            try {
                godless.addCitizen(grownWith(Nature.PIOUS, "Эмма", Gender.FEMALE));
                Warehouse.of(world, godless).add(new ItemStack(Items.COBBLESTONE, 1));
                Natures.newDay(world, godless);
                if (godless.favourOf(MASON) != 0) {
                    context.throwGameTestException("Молились без алтаря: "
                            + godless.favourOf(MASON));
                }
            } finally {
                manager.remove(godless.id());
            }
        } finally {
            demolish(world, chapel, chapelPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ленивый уходит последним, честолюбивый — первым.
     * <p>
     * Проверяется не арифметика сроков (её держит модульная проверка),
     * а то, что суточный подсчёт спрашивает срок <b>у жителя</b>.
     * Пока он спрашивал его у настройки, все трое уходили в один день,
     * и терпение ленивого было словом в таблице.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "natures", tickLimit = 400)
    public void theIdleEndureAndTheProudLeaveFirst(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (Citizen anyone : List.copyOf(colony.citizens())) {
                colony.removeCitizen(anyone.id());
            }
            Citizen idle = grownWith(Nature.LAZY, "Одон", Gender.MALE);
            Citizen proud = grownWith(Nature.AMBITIOUS, "Гийом", Gender.MALE);
            Citizen plain = grownWith(Nature.EVEN, "Роллон", Gender.MALE);
            for (Citizen citizen : List.of(idle, proud, plain)) {
                citizen.setSaturation(0);
                colony.addCitizen(citizen);
            }

            int idleGone = -1;
            int proudGone = -1;
            int plainGone = -1;
            int warnedInWindow = -1;
            boolean idleWarnedTooEarly = false;
            int patience = Needs.leaveAfterDays() * 2 + 2;
            for (int day = 1; day <= patience; day++) {
                Needs.newDay(world, manager, colony);

                for (TownHallView.CitizenLine line : TownHallView.of(world, colony).citizens()) {
                    if (!line.leavingSoon()) {
                        continue;
                    }
                    if (line.id().equals(proud.id()) && warnedInWindow < 0) {
                        warnedInWindow = day;
                    }
                    if (line.id().equals(idle.id()) && day < Needs.warnAfterDays(idle)) {
                        idleWarnedTooEarly = true;
                    }
                }
                if (idleGone < 0 && colony.citizen(idle.id()).isEmpty()) {
                    idleGone = day;
                }
                if (proudGone < 0 && colony.citizen(proud.id()).isEmpty()) {
                    proudGone = day;
                }
                if (plainGone < 0 && colony.citizen(plain.id()).isEmpty()) {
                    plainGone = day;
                }
            }

            if (proudGone < 0 || plainGone < 0 || idleGone < 0) {
                context.throwGameTestException("За " + patience + " голодных дней ушли не все: "
                        + "честолюбивый " + proudGone + ", ровный " + plainGone
                        + ", ленивый " + idleGone);
                return;
            }
            // И окно говорит то же самое. Строка «скоро уйдёт» считалась
            // по общей настройке, и у ленивого она загоралась за четыре
            // дня до срока, которого ему ещё восемь: окно обещало уход,
            // которого не будет. Окно и правило обязаны совпадать — этот
            // урок мод уже получал на поручениях.
            if (warnedInWindow < 0) {
                context.throwGameTestException("Честолюбивого не предупредили в окне ни разу");
            }
            if (idleWarnedTooEarly) {
                context.throwGameTestException("Окно обещало уход ленивого прежде срока: "
                        + "правило и окно разошлись");
            }

            if (!(proudGone < plainGone && plainGone < idleGone)) {
                context.throwGameTestException("Порядок ухода не тот: честолюбивый "
                        + proudGone + ", ровный " + plainGone + ", ленивый " + idleGone
                        + " — а ждали, что гордый уйдёт первым, ленивый последним");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Цепочка кончилась — разговор нет.
     * <p>
     * Самая заметная беда старой системы: после третьего квеста старейшина
     * говорил «просить больше нечего» <b>навсегда</b>, и деревня из соседа
     * превращалась в лавку. Теперь у него есть поручение на каждый день.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theErrandComesWhenTheChainRunsOut(TestContext context) {
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        UUID player = UUID.randomUUID();

        chainDone(village, player);

        if (Quests.offered(village, player, Villages.ELDER).isPresent()) {
            context.throwGameTestException("Писаная цепочка не кончилась — проверять нечего");
        }

        Quests.Task task = Quests.task(village, player, Villages.ELDER, 7).orElse(null);
        if (task == null) {
            context.throwGameTestException("После цепочки старейшине нечего сказать");
            return;
        }
        if (!task.errand()) {
            context.throwGameTestException("Это не поручение: " + task.id());
        }
        if (task.quest().objectives().isEmpty()) {
            context.throwGameTestException("Поручение ничего не просит");
        }
        if (task.quest().rewards().isEmpty()) {
            context.throwGameTestException("За поручение ничего не дают");
        }

        context.complete();
    }

    /**
     * Поручения у каждого народа свои: норманнский пивовар и пивовар
     * майя просят разного и разными словами, старейшина отдаривается
     * питьём своего народа, а слова каждой просьбы есть в обоих словарях.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void eachPeopleAsksItsOwnErrands(TestContext context) {
        Identifier brewer = new Identifier("villagepax", "brewer");
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement norman = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement maya = Settlement.found(MAYA, Owner.AUTONOMOUS, "Тикаль", where);
        UUID player = UUID.randomUUID();

        java.util.Set<String> normanWords = new java.util.HashSet<>();
        java.util.Set<String> mayaWords = new java.util.HashSet<>();
        for (long day = 40; day < 44; day++) {
            Quest ours = com.villagepax.sim.quest.Errands.forToday(norman, brewer, day).orElseThrow();
            Quest theirs = com.villagepax.sim.quest.Errands.forToday(maya, brewer, day).orElseThrow();
            normanWords.add(ours.dialogue());
            mayaWords.add(theirs.dialogue());
            if (!ours.dialogue().startsWith("villagepax.errand.norman.brewer.")) {
                context.throwGameTestException("Норманнский пивовар говорит общими словами: " + ours.dialogue());
            }
            boolean treat = ours.rewards().stream().anyMatch(reward -> reward instanceof Quest.Reward.Give give
                    && give.item() == com.villagepax.item.ModItems.ALE);
            if (!treat) {
                context.throwGameTestException("Пивовар не отдарился элем: " + ours.rewards());
            }
        }
        if (normanWords.size() < 2) {
            context.throwGameTestException("Четыре дня подряд одна просьба: " + normanWords);
        }
        if (!java.util.Collections.disjoint(normanWords, mayaWords)) {
            context.throwGameTestException("Норманн и майя просят одними словами");
        }

        for (String lang : List.of("ru_ru", "en_us")) {
            com.google.gson.JsonObject words;
            try (java.io.InputStream in = ComplaintTests.class.getResourceAsStream(
                    "/assets/villagepax/lang/" + lang + ".json")) {
                words = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException | RuntimeException broken) {
                context.throwGameTestException("Словарь " + lang + " не читается: " + broken);
                return;
            }
            for (String key : com.villagepax.sim.quest.Errands.dialogueKeys()) {
                if (!words.has(key)) {
                    context.throwGameTestException("В словаре " + lang + " нет слов поручения " + key);
                }
            }
        }
        if (Quests.task(maya, player, Villages.ELDER, 3).isEmpty()) {
            context.throwGameTestException("У старейшины майя нет ни цепочки, ни поручения");
        }

        context.complete();
    }

    /**
     * Сорванный листок уносит просьбу: в нём её опознаватель и слова,
     * доска видит его в сумке и висит обрывком, а сдача листок забирает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void aTornSheetTravelsAndIsTakenAtHandIn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Citizen elder = evenNewborn("Bertrand", "de Caen", NORMAN, Gender.MALE);
        elder.setProfession(Villages.ELDER);
        village.addCitizen(elder);
        manager.add(village);
        UUID player = UUID.randomUUID();

        try {
            chainDone(village, player);
            Quests.Task task = Quests.task(village, player, Villages.ELDER, 30).orElseThrow();
            SimpleInventory hands = new SimpleInventory(9);

            com.villagepax.screen.BoardView before = com.villagepax.screen.BoardNet.viewOf(manager, village,
                    where, player, hands, 30);
            if (before.sheets().isEmpty() || before.sheets().get(0).taken()) {
                context.throwGameTestException("Листок старейшины не висит целым: " + before.sheets());
            }

            ItemStack note = com.villagepax.item.QuestNoteItem.of(com.villagepax.screen.BoardNet.noteOf(manager,
                    village, elder, Villages.ELDER, task, player, hands, 30));
            com.villagepax.item.QuestNoteItem.Note written = com.villagepax.item.QuestNoteItem.read(note)
                    .orElse(null);
            if (written == null || !written.quest().equals(task.id()) || !written.errand()
                    || written.offer().objectives().isEmpty()
                    || !written.offer().dialogue().equals(task.quest().dialogue())) {
                context.throwGameTestException("Листок записал не ту просьбу: " + written);
                return;
            }
            hands.addStack(note);

            com.villagepax.screen.BoardView after = com.villagepax.screen.BoardNet.viewOf(manager, village,
                    where, player, hands, 30);
            if (!after.sheets().get(0).taken()) {
                context.throwGameTestException("Доска не видит сорванного листка в сумке");
            }

            Quest.Objective.Deliver ask = (Quest.Objective.Deliver) task.quest().objectives().get(0);
            hands.addStack(new ItemStack(ask.item(), ask.count()));
            List<ItemStack> paid = new ArrayList<>();
            if (Quests.handIn(manager, village, null, player, Villages.ELDER, hands, 30,
                    paid::add) != Quests.Handover.DONE) {
                context.throwGameTestException("Поручение с листком не приняли");
            }
            if (hands.count(com.villagepax.item.ModItems.QUEST_NOTE) != 0) {
                context.throwGameTestException("Сданный листок остался в сумке");
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Гильдия авантюристов: достроенной гильдии деревня ставит мастера
     * из жителей без дела, и только одного; его контракт — по рангу
     * игрока: чужаку работа новичка за серебро, другу — героя за золото.
     * И бродячий торговец заходит туда, где гильдия, вдвое чаще.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theGuildHiresAMasterAndRanksItsContracts(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Identifier guildType = new Identifier("villagepax", "norman/guild");
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Citizen idle = evenNewborn("Gautier", "le Hardi", NORMAN, Gender.MALE);
        village.addCitizen(idle);
        Building guild = Building.planned(guildType, where.east(4), BlockRotation.NONE);
        guild.setProgress(BuildProgress.DONE);
        village.addBuilding(guild);
        manager.add(village);
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", where.south(4));
        UUID player = UUID.randomUUID();

        try {
            Villages.staffTheGuild(world, village, new java.util.Random(7));
            Villages.staffTheGuild(world, village, new java.util.Random(8));
            long masters = village.citizens().stream()
                    .filter(c -> c.profession().filter(Villages.GUILDMASTER::equals).isPresent()).count();
            if (masters != 1 || idle.profession().filter(Villages.GUILDMASTER::equals).isEmpty()) {
                context.throwGameTestException("Мастеров гильдии: " + masters + ", а бездельник — "
                        + idle.profession());
            }

            Quest novice = Quests.task(village, player, Villages.GUILDMASTER, 5).orElseThrow().quest();
            if (!novice.dialogue().startsWith("villagepax.contract.novice.")
                    || novice.rewards().stream().noneMatch(r -> r instanceof Quest.Reward.Give give
                    && give.item() == com.villagepax.item.ModItems.SILVER_COIN)) {
                context.throwGameTestException("Чужаку не контракт новичка за серебро: " + novice);
            }
            village.addReputation(player, 60);
            Quest hero = Quests.task(village, player, Villages.GUILDMASTER, 5).orElseThrow().quest();
            if (!hero.dialogue().startsWith("villagepax.contract.hero.")
                    || hero.rewards().stream().noneMatch(r -> r instanceof Quest.Reward.Give give
                    && give.item() == com.villagepax.item.ModItems.GOLD_COIN)) {
                context.throwGameTestException("Другу не контракт героя за золото: " + hero);
            }

            int plain = 0;
            for (long day = 0; day < 24; day++) {
                plain += com.villagepax.sim.trade.Peddler.dueAt(colony, day) ? 1 : 0;
            }
            Building own = Building.planned(guildType, where.south(8), BlockRotation.NONE);
            own.setProgress(BuildProgress.DONE);
            colony.addBuilding(own);
            int guilded = 0;
            for (long day = 0; day < 24; day++) {
                guilded += com.villagepax.sim.trade.Peddler.dueAt(colony, day) ? 1 : 0;
            }
            if (guilded != plain * 2) {
                context.throwGameTestException("С гильдией торговец заходит " + guilded
                        + " раз за 24 дня, без неё " + plain + " — ждали вдвое чаще");
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Просьба дня не меняется, пока день тот же.
     * <p>
     * Это не придирка к чистоте, а условие работоспособности: окно
     * показывает одно, а сдача считает другое, и игрок остаётся
     * с отобранным не тем. Поэтому просьба выводится из дня, деревни
     * и ремесла — и больше ни из чего.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theSameDayAlwaysAsksTheSameThing(TestContext context) {
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        UUID player = UUID.randomUUID();
        chainDone(village, player);

        Quest first = Quests.task(village, player, Villages.ELDER, 12).orElseThrow().quest();
        Quest again = Quests.task(village, player, Villages.ELDER, 12).orElseThrow().quest();
        if (!first.objectives().equals(again.objectives())) {
            context.throwGameTestException("За один день просьба изменилась: "
                    + first.objectives() + " и " + again.objectives());
        }

        // А за неделю она обязана хоть раз стать другой: одна и та же
        // просьба каждый день — это не поручение, а оброк.
        boolean varied = false;
        for (long day = 13; day <= 19; day++) {
            Quest later = Quests.task(village, player, Villages.ELDER, day)
                    .orElseThrow().quest();
            varied = varied || !later.objectives().equals(first.objectives());
        }
        if (!varied) {
            context.throwGameTestException("Неделю подряд просят одно и то же");
        }

        context.complete();
    }

    /**
     * Одна просьба в день на ремесло — и назавтра снова.
     * <p>
     * Предел здесь один и он же единственный: день. Без него поручения
     * стали бы бесконечным насосом монеты и доверия, с ним — мелкой
     * услугой, за которую платят мелко.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void oneErrandADayAndNoMore(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        manager.add(village);
        UUID player = UUID.randomUUID();

        try {
            chainDone(village, player);

            Quest today = Quests.task(village, player, Villages.ELDER, 30).orElseThrow().quest();
            Quest.Objective.Deliver ask = (Quest.Objective.Deliver) today.objectives().get(0);

            SimpleInventory hands = new SimpleInventory(9);
            hands.addStack(new ItemStack(ask.item(), ask.count()));
            List<ItemStack> paid = new ArrayList<>();

            if (Quests.handIn(manager, village, null, player, Villages.ELDER, hands, 30,
                    paid::add) != Quests.Handover.DONE) {
                context.throwGameTestException("Поручение не приняли, хотя принесено всё");
            }
            if (paid.isEmpty()) {
                context.throwGameTestException("За поручение не заплатили");
            }

            if (Quests.task(village, player, Villages.ELDER, 30).isPresent()) {
                context.throwGameTestException("Второе поручение в тот же день");
            }
            if (Quests.task(village, player, Villages.ELDER, 31).isEmpty()) {
                context.throwGameTestException("Назавтра просить перестали");
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Купец по-прежнему только торгует.
     * <p>
     * Решение заказчика: «у торговли своё лицо». Экран купца открывается
     * сразу на прилавке — дай ему поручение, и вместо товара игрок увидит
     * вкладки разговора. Проверка стережёт именно это решение: поручения
     * есть у восьми ремёсел и намеренно нет у девятого.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theMerchantStillOnlySells(TestContext context) {
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        UUID player = UUID.randomUUID();

        if (Quests.task(village, player, Villages.MERCHANT, 5).isPresent()) {
            context.throwGameTestException("У купца завелось поручение — "
                    + "экран откроется вкладками вместо товара");
        }
        if (Quests.task(village, player, Villages.ELDER, 5).isEmpty()) {
            context.throwGameTestException("А у старейшины поручения нет вовсе");
        }

        context.complete();
    }

    /**
     * «Поставь у себя мастерскую» смотрит в колонию игрока.
     * <p>
     * Первая цель, ради которой мод и затевался: деревня народа и колония
     * игрока наконец разговаривают. Проверяются обе стороны — и отказ,
     * пока мастерской нет, и приём, когда она встала.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theBuildObjectiveLooksAtTheColony(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos where = context.getAbsolutePos(new BlockPos(6, 1, 6));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = colony.owner().player().orElseThrow();

        try {
            village.noteQuestDone(player, FOUNDING_1);
            village.noteQuestDone(player, FOUNDING_2);
            village.noteQuestDone(player, FOUNDING_3);
            village.addReputation(player, 50);

            SimpleInventory hands = new SimpleInventory(9);
            List<ItemStack> paid = new ArrayList<>();

            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 3,
                    paid::add) != Quests.Handover.NOT_ENOUGH) {
                context.throwGameTestException("Просьбу приняли без мастерской");
            }

            // Размеченный фундамент не считается, и проверяется это ДО
            // готового: после удачной сдачи квест уже другой, и «недострой
            // не годится» подтверждалось бы на чужой просьбе.
            Building hut = new Building(UUID.randomUUID(),
                    new Identifier("villagepax", "norman/lumberjack"), 1, hall,
                    BlockRotation.NONE, BuildProgress.BUILDING, List.of());
            colony.addBuilding(hut);

            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 3,
                    paid::add) != Quests.Handover.NOT_ENOUGH) {
                context.throwGameTestException("Недостроенная мастерская сошла за готовую");
            }

            // А достроенная — считается.
            hut.setProgress(BuildProgress.DONE);
            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 3,
                    paid::add) != Quests.Handover.DONE) {
                context.throwGameTestException("Мастерская стоит, а просьбу не приняли");
            }
            if (paid.stream().noneMatch(stack -> stack.isOf(ModItems.COIN))) {
                context.throwGameTestException("За мастерскую не заплатили: " + paid);
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * «Помолись» смотрит на богов колонии, а награда молится за игрока.
     * <p>
     * Ритуальная цель из дизайн-документа и обратная ей награда: деревня
     * просит намолить у одного бога и сама замолвливает слово перед другим.
     * Это то место, где вера и квесты перестают быть двумя разными модами.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theFavourObjectiveLooksAtTheGods(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos where = context.getAbsolutePos(new BlockPos(6, 1, 6));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = colony.owner().player().orElseThrow();

        try {
            village.noteQuestDone(player, FOUNDING_1);
            village.noteQuestDone(player, FOUNDING_2);
            village.noteQuestDone(player, FOUNDING_3);
            village.noteQuestDone(player, FOUNDING_4);
            village.addReputation(player, 65);

            SimpleInventory hands = new SimpleInventory(9);
            List<ItemStack> paid = new ArrayList<>();

            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 4,
                    paid::add) != Quests.Handover.NOT_ENOUGH) {
                context.throwGameTestException("Просьбу приняли без благосклонности");
            }

            Identifier sower = new Identifier("villagepax", "norman_sower");
            colony.addFavour(sower, 40);

            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 4,
                    paid::add) != Quests.Handover.DONE) {
                context.throwGameTestException("Намолено, а просьбу не приняли");
            }

            // Награда — благосклонность у бога камня: деревня помолилась
            // за игрока, и это видно числом.
            Identifier mason = new Identifier("villagepax", "norman_mason");
            if (colony.favourOf(mason) <= 0) {
                context.throwGameTestException("Деревня обещала помолиться и не помолилась: "
                        + colony.favourOf(mason));
            }
            // А намоленное осталось при нём: подтверждают им, а не платят.
            if (colony.favourOf(sower) != 40) {
                context.throwGameTestException("Благосклонность отобрали в уплату: "
                        + colony.favourOf(sower));
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * «Сходи к соседям» смотрит на чужие деревни, а платит человеком.
     * <p>
     * Дипломатическая цель и лучшая награда мода разом. Переселенец
     * приходит <b>из отпустившего народа</b> — с чужим именем, и это
     * видно в списке жителей до конца игры.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theFriendshipObjectivePaysWithAPerson(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos where = context.getAbsolutePos(new BlockPos(6, 1, 6));
        // Далеко за сеткой площадок: чужие проверки не должны споткнуться
        // о наши захваченные чанки.
        BlockPos faraway = context.getAbsolutePos(new BlockPos(0, 1, 0)).add(3000, 0, 3000);

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement strangers = Settlement.found(MAYA, Owner.AUTONOMOUS, "Йашчилан", faraway);
        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = colony.owner().player().orElseThrow();

        manager.add(strangers);
        try {
            for (Identifier step : List.of(FOUNDING_1, FOUNDING_2, FOUNDING_3,
                    FOUNDING_4, FOUNDING_5)) {
                village.noteQuestDone(player, step);
            }
            village.addReputation(player, 80);

            SimpleInventory hands = new SimpleInventory(9);
            List<ItemStack> paid = new ArrayList<>();
            int before = colony.population();

            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 5,
                    paid::add) != Quests.Handover.NOT_ENOUGH) {
                context.throwGameTestException("Просьбу приняли без знакомства с майя");
            }

            strangers.addReputation(player, 30);

            if (Quests.handIn(manager, village, colony, player, Villages.ELDER, hands, 5,
                    paid::add) != Quests.Handover.DONE) {
                context.throwGameTestException("Знакомство есть, а просьбу не приняли");
            }
            if (colony.population() != before + 1) {
                context.throwGameTestException("Человек не переселился: было " + before
                        + ", стало " + colony.population());
            }

            Citizen newcomer = colony.citizens().get(colony.citizens().size() - 1);
            if (!newcomer.culture().equals(NORMAN)) {
                context.throwGameTestException("Переселенец не из отпустившего народа: "
                        + newcomer.culture());
            }
            if (newcomer.profession().isEmpty()) {
                context.throwGameTestException("Переселенец приехал без ремесла");
            }
        } finally {
            manager.remove(strangers.id());
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Окно и сдача обязаны говорить одно.
     * <p>
     * Ради этого правило «выполнена ли цель» вынесено в {@code Progress}
     * и зовётся из обоих мест. Проверка гоняет по всем шагам цепочки
     * старейшины разом: галочка в окне включена ровно тогда, когда сдача
     * отвечает «принято». Разойдись они — игрок нажмёт готовую кнопку
     * и получит отказ, и это худший род поломки, потому что винить он
     * будет себя.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theWindowAndTheHandoverAgree(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos where = context.getAbsolutePos(new BlockPos(6, 1, 6));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = colony.owner().player().orElseThrow();
        manager.add(village);

        // Соседи нужны последнему шагу цепочки: «сходи к майя» без майя
        // в мире невыполнимо, и проверка застряла бы на пятом шаге из шести.
        // Далеко за сеткой площадок, чтобы не занимать чужие чанки.
        Settlement strangers = Settlement.found(MAYA, Owner.AUTONOMOUS, "Йашчилан",
                context.getAbsolutePos(new BlockPos(0, 1, 0)).add(4000, 0, 4000));
        manager.add(strangers);

        try {
            village.addReputation(player, 100);
            long today = Schedule.dayOf(world.getTimeOfDay());
            SimpleInventory hands = new SimpleInventory(9);
            List<ItemStack> paid = new ArrayList<>();

            // Шесть шагов цепочки, и на каждый уходит два оборота: в первом
            // окно честно говорит «не готово», во втором — сдача. Четырнадцать
            // с запасом, а что все шесть пройдены, проверяется числом ниже:
            // цикл, не дошедший до целей без предмета, подтверждал бы только
            // «принеси зерна» — то есть ровно то, что работало и раньше.
            int done = 0;
            for (int step = 0; step < 14; step++) {
                QuestView view = QuestNet.viewOf(manager, village, player, hands,
                        Villages.ELDER, Warehouse.of(world, village), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElse(null);
                if (view == null || view.quest().isEmpty()) {
                    context.throwGameTestException("На шаге " + step + " разговор пуст");
                    return;
                }

                boolean windowSaysReady = view.quest().orElseThrow().ready();
                // День берётся у мира, как его берёт и окно. Передай сюда
                // своё число — и после писаной цепочки окно с поручением
                // одного дня сверялось бы со сдачей поручения другого:
                // расхождение было бы мнимым, а искали бы его в коде.
                Quests.Handover outcome = Quests.handIn(manager, village, colony, player,
                        Villages.ELDER, hands, today, paid::add);
                boolean handoverSaysDone = outcome == Quests.Handover.DONE;

                if (windowSaysReady != handoverSaysDone) {
                    context.throwGameTestException("Шаг " + step + ": окно говорит «"
                            + windowSaysReady + "», а сдача — «" + outcome + "»");
                }

                if (handoverSaysDone) {
                    done++;
                } else {
                    // Не готово — выполним требуемое и пойдём дальше.
                    satisfy(world, manager, village, colony, player, hands);
                }
            }

            if (done < 6) {
                context.throwGameTestException("Цепочка пройдена только на " + done
                        + " шагов из шести — до целей без предмета проверка не дошла");
            }
        } finally {
            manager.remove(strangers.id());
            manager.remove(village.id());
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Писаные квесты связаны в цепочки без обрывов и колец.
     * <p>
     * Целость данных, а не поведения. Ссылка {@code next} на несуществующий
     * квест обрывает цепочку молча — игрок просто перестаёт получать
     * просьбы и никогда не узнает почему; кольцо же вешало бы обход, и от
     * него в коде стоит ограничитель, который тоже молчит. Двадцать восемь
     * файлов глазами не сверяет никто.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void everyQuestChainIsWholeAndEnds(TestContext context) {
        List<String> complaints = new ArrayList<>();

        QuestManager.all().forEach((id, quest) -> quest.next().ifPresent(next -> {
            if (QuestManager.get(next).isEmpty()) {
                complaints.add(id + " ведёт в никуда: " + next);
            }
        }));

        // Кольца ловятся обходом с ограничителем: длиннее, чем всего
        // квестов, честная цепочка быть не может.
        for (Identifier head : QuestManager.all().keySet()) {
            Identifier at = head;
            for (int step = 0; step <= QuestManager.all().size(); step++) {
                Quest quest = QuestManager.get(at).orElse(null);
                if (quest == null || quest.next().isEmpty()) {
                    at = null;
                    break;
                }
                at = quest.next().get();
            }
            if (at != null) {
                complaints.add(head + ": цепочка не кончается — похоже на кольцо");
            }
        }

        if (QuestManager.all().isEmpty()) {
            context.throwGameTestException("Квестов не загружено вовсе — проверять нечего");
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Цепочки квестов порваны:\n  "
                    + String.join("\n  ", complaints));
        }

        context.complete();
    }

    /**
     * Бог принимает одну жертву в день — и берёт из стопки одну вещь.
     * <p>
     * Это единственное, что держит веру долгой целью. Сундук пшеницы,
     * высыпанный на алтарь разом, купил бы избранничество за минуту,
     * и весь пантеон свёлся бы к «принеси стопку». Проверяются обе
     * половины правила: и «одна в день», и «одна из стопки», — потому
     * что сломать можно каждую по отдельности.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void theGodTakesOneOfferingADay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            ItemStack wheat = new ItemStack(Items.WHEAT, 8);

            Offering.Judgement first = Offering.accept(world, manager, colony, wheat, 10);
            if (first.verdict() != Offering.Verdict.TAKEN) {
                context.throwGameTestException("Зерно не принято: " + first.verdict());
            }
            if (colony.favourOf(SOWER) != 2) {
                context.throwGameTestException("За зерно дали " + colony.favourOf(SOWER)
                        + " благосклонности, а в датапаке два");
            }
            if (wheat.getCount() != 7) {
                context.throwGameTestException("Из стопки ушло " + (8 - wheat.getCount())
                        + " вещей, а жертва — одна");
            }

            Offering.Judgement again = Offering.accept(world, manager, colony, wheat, 10);
            if (again.verdict() != Offering.Verdict.ALREADY_TODAY) {
                context.throwGameTestException("Вторую жертву за день приняли: " + again.verdict());
            }
            if (colony.favourOf(SOWER) != 2 || wheat.getCount() != 7) {
                context.throwGameTestException("Отказ всё-таки что-то изменил: "
                        + colony.favourOf(SOWER) + " очков, в стопке " + wheat.getCount());
            }

            Offering.Judgement tomorrow = Offering.accept(world, manager, colony, wheat, 11);
            if (tomorrow.verdict() != Offering.Verdict.TAKEN || colony.favourOf(SOWER) != 4) {
                context.throwGameTestException("Назавтра жертву не приняли: " + tomorrow.verdict()
                        + ", очков " + colony.favourOf(SOWER));
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Бога выбирает вещь, и чужого бог не берёт.
     * <p>
     * На этом правиле стоит весь алтарь: списка богов на экране нет,
     * и узнаётся пантеон руками. Сломайся оно — и зерно ушло бы
     * каменотёсу, а игрок так и не понял бы, почему растёт не то.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void eachGodTakesOnlyItsOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Offering.Judgement grain = Offering.judge(colony, new ItemStack(Items.WHEAT), 5);
            if (!grain.god().filter(SOWER::equals).isPresent()) {
                context.throwGameTestException("Зерно ушло не Сеятелю: " + grain.god());
            }

            Offering.Judgement stone = Offering.judge(colony, new ItemStack(Items.COBBLESTONE), 5);
            if (!stone.god().filter(MASON::equals).isPresent()) {
                context.throwGameTestException("Камень ушёл не Камнетёсу: " + stone.god());
            }

            // Гнилая плоть не нужна никому, и отказ обязан это сказать.
            Offering.Judgement rot = Offering.judge(colony, new ItemStack(Items.ROTTEN_FLESH), 5);
            if (rot.verdict() != Offering.Verdict.NOT_THEIRS) {
                context.throwGameTestException("Гнилую плоть приняли: " + rot.verdict());
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Внутри народа жертвы не пересекаются.
     * <p>
     * Целостность данных, а не поведения. Два бога, принимающие пшеницу,
     * делают правило «что кладёшь — тому и молишься» неопределённым,
     * и мод вынужден выбирать за игрока. Увидеть это в игре можно только
     * по тому, что благосклонность растёт не у того, — то есть никогда.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void noTwoGodsOfOnePeopleShareAnOffering(TestContext context) {
        Map<Identifier, Map<Identifier, Identifier>> taken = new java.util.LinkedHashMap<>();
        List<String> clashes = new ArrayList<>();

        Gods.all().forEach((id, god) -> {
            Map<Identifier, Identifier> mine =
                    taken.computeIfAbsent(god.culture(), key -> new java.util.LinkedHashMap<>());
            god.offerings().keySet().forEach(item -> {
                Identifier already = mine.put(item, id);
                if (already != null) {
                    clashes.add(god.culture() + ": " + item + " берут и " + already + ", и " + id);
                }
            });
        });

        if (Gods.all().isEmpty()) {
            context.throwGameTestException("Богов не загружено вовсе — проверять нечего");
        }
        if (!clashes.isEmpty()) {
            context.throwGameTestException("Боги делят жертву:\n  "
                    + String.join("\n  ", clashes));
        }

        context.complete();
    }

    /**
     * Обещанный артефакт существует предметом — и тем самым, что нужно.
     * <p>
     * Опознаватель в датапаке проверяется только здесь: в игре его
     * неправильность видна на пятидесятый день, когда бог вручает пустоту
     * тому, кто полсотни дней носил ему хлеб. Сверяется и домен: серп
     * от бога войны был бы опечаткой, которую иначе не поймать.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void everyGodPromisesAnArtifactThatExists(TestContext context) {
        List<String> broken = new ArrayList<>();

        Gods.all().forEach((id, god) -> god.artifact().ifPresent(artifact -> {
            Item item = Registries.ITEM.get(artifact);
            if (item == Items.AIR) {
                broken.add(id + " вручает " + artifact + ", но такого предмета нет");
                return;
            }
            if (!(item instanceof ArtifactItem known)) {
                broken.add(id + " вручает " + artifact + " — это не артефакт");
                return;
            }
            if (known.domain() != god.domain()) {
                broken.add(id + " (" + god.domain().id() + ") вручает артефакт домена "
                        + known.domain().id());
            }
        }));

        if (!broken.isEmpty()) {
            context.throwGameTestException("Обещания богов не исполнимы:\n  "
                    + String.join("\n  ", broken));
        }

        context.complete();
    }

    /**
     * Чужой алтарь отказывает словами, а не молчанием.
     * <p>
     * То же правило, что у чужой ратуши, и оплачено оно той же жалобой:
     * «не могу заказать постройку, не грузит призрак». Благосклонность
     * лежит в поселении, и жертва в чужом храме подняла бы чужое небо —
     * игрок ждал бы своего и не понимал, куда делись очки.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void aForeignAltarRefusesInWords(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            UUID owner = colony.owner().player().orElseThrow();
            UUID stranger = UUID.randomUUID();
            ItemStack wheat = new ItemStack(Items.WHEAT);

            Offering.Judgement mine = Offering.judgeFor(colony, owner, wheat, 3);
            if (mine.verdict() != Offering.Verdict.TAKEN) {
                context.throwGameTestException("Хозяину свой алтарь отказал: " + mine.verdict());
            }

            Offering.Judgement theirs = Offering.judgeFor(colony, stranger, wheat, 3);
            if (theirs.verdict() != Offering.Verdict.NOT_YOURS) {
                context.throwGameTestException("Чужой алтарь принял жертву: " + theirs.verdict());
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Благословение дозора убавляет отряд, но никогда до нуля.
     * <p>
     * Второе правило важнее первого. Набег, который не приходит, —
     * это выключенная механика, а не победа; бог тут ничем не отличается
     * от каменной башни, и предел у них общий.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void theWatchBlessingThinsTheRaidButNeverToNothing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            // Доверие на дне: деревня посылает полный отряд.
            int angry = -120;
            int plain = Raids.fightersFor(angry, Raids.defence(colony, 7));
            if (plain != Raids.MOST_FIGHTERS) {
                context.throwGameTestException("Без благословения пришло " + plain
                        + " бойцов, а злобы хватает на " + Raids.MOST_FIGHTERS);
            }

            colony.bless(Domain.WATCH.id(), 7, Faith.BLESSING_DAYS);
            int blessed = Raids.fightersFor(angry, Raids.defence(colony, 7));
            if (blessed != plain - 1) {
                context.throwGameTestException("Благословение убавило отряд с " + plain
                        + " до " + blessed + ", а должно было на одного");
            }

            // Мягкая злоба и благословение: до нуля всё равно нельзя.
            int barely = Raids.fightersFor(Raids.PATIENCE_ENDS, Raids.defence(colony, 7));
            if (barely < 1) {
                context.throwGameTestException("Набег отменился совсем: бойцов " + barely);
            }

            // А назавтра после срока благословения его уже нет.
            int expired = Raids.fightersFor(angry, Raids.defence(colony, 7 + Faith.BLESSING_DAYS));
            if (expired != plain) {
                context.throwGameTestException("Благословение пережило свой срок: " + expired);
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Благословение камня прибавляет билдеру блок — поверх черты народа.
     * <p>
     * Числа написаны руками, а не выведены той же арифметикой: иначе
     * проверка подтверждала бы код, а не проверяла его. Норманн кладёт
     * один блок дерева и два камня; под благословением — два и три.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void theStoneBlessingSpeedsTheBuilder(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            BlockState wood = Blocks.OAK_PLANKS.getDefaultState();
            BlockState stone = Blocks.COBBLESTONE.getDefaultState();

            if (BuilderJob.pace(colony, wood, 4) != 1 || BuilderJob.pace(colony, stone, 4) != 2) {
                context.throwGameTestException("Без благословения темп "
                        + BuilderJob.pace(colony, wood, 4) + "/"
                        + BuilderJob.pace(colony, stone, 4) + ", а ждали 1/2");
            }

            colony.bless(Domain.STONE.id(), 4, Faith.BLESSING_DAYS);

            if (BuilderJob.pace(colony, wood, 4) != 2 || BuilderJob.pace(colony, stone, 4) != 3) {
                context.throwGameTestException("Под благословением темп "
                        + BuilderJob.pace(colony, wood, 4) + "/"
                        + BuilderJob.pace(colony, stone, 4) + ", а ждали 2/3");
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Утро под благословением урожая поднимает посевы — но не всё поле.
     * <p>
     * Ограничение здесь несущее: поле, поспевшее за ночь целиком, отняло бы
     * у фермера работу, а вместе с ней и вид работы, ради которого в моде
     * вообще есть жители. Проверяется и то, что без благословения не растёт
     * ничего: врезка в суточный перекат обязана молчать, пока её не звали.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith", tickLimit = 400)
    public void theHarvestBlessingGrowsTheCropsAtDawn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма не достроилась");
            }

            if (Faith.growCrops(world, colony, 12) != 0) {
                context.throwGameTestException("Посевы подросли без благословения");
            }

            colony.bless(Domain.HARVEST.id(), 12, Faith.BLESSING_DAYS);
            int grown = Faith.growCrops(world, colony, 12);

            // Восемь написаны здесь ЧИСЛОМ, а не взяты из Faith.BLESSED_GROWTH,
            // и это не небрежность. Взятое из кода число меняется вместе
            // с кодом: убавь щедрость благословения вдвое — и проверка
            // молча согласится, потому что сравнивает константу сама с собой.
            // Сколько грядок поднимает бог — решение по игре, и пинать его
            // должна проверка, а не наоборот.
            int promised = 8;
            if (grown != promised) {
                context.throwGameTestException("Подросло грядок " + grown + ", а обещано "
                        + promised);
            }

            // И подросли они по-настоящему: ровно столько грядок ушло
            // со стадии «только посеяно».
            int sprouted = 0;
            for (BlockPos plot : FarmJob.plots(farm)) {
                BlockState state = world.getBlockState(plot);
                if (state.getBlock() instanceof CropBlock crop && crop.getAge(state) > 0) {
                    sprouted++;
                }
            }
            if (sprouted != promised) {
                context.throwGameTestException("В поле проросло " + sprouted
                        + " грядок, а благословение тронуло " + grown);
            }
        } finally {
            demolish(world, farm, farmPlan);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Артефакт вручается один раз и остаётся после траты.
     * <p>
     * Иначе игрок оказался бы перед выбором «пользоваться подарком или
     * не потерять его», а подарок, которым страшно пользоваться, —
     * не награда, а залог. Проверяется и обратное: до ступени не дают.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void theArtifactComesOnceAndStaysAfterSpending(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            colony.addFavour(SOWER, Artifacts.FROM.from() - 1);
            if (Artifacts.earned(colony, SOWER).isPresent()) {
                context.throwGameTestException("Артефакт дали до ступени «"
                        + Artifacts.FROM.id() + "»");
            }

            colony.addFavour(SOWER, 1);
            Identifier artifact = Artifacts.earned(colony, SOWER).orElse(null);
            if (artifact == null) {
                context.throwGameTestException("На ступени «" + Artifacts.FROM.id()
                        + "» артефакт не полагается");
            }

            colony.noteArtifact(artifact);
            if (Artifacts.earned(colony, SOWER).isPresent()) {
                context.throwGameTestException("Артефакт вручили второй раз");
            }

            // Потратили всё до нуля — подарок остаётся.
            colony.addFavour(SOWER, -10_000);
            if (colony.favourOf(SOWER) != 0) {
                context.throwGameTestException("Благосклонность ушла ниже нуля: "
                        + colony.favourOf(SOWER));
            }
            if (!colony.hasArtifact(artifact)) {
                context.throwGameTestException("Трата отобрала артефакт");
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Храм и услышанные боги греют колонию — до предела.
     * <p>
     * Без этого правила храм окупался бы только к пятидесятому дню,
     * и первые двадцать здание просто занимало бы место. Предел нужен
     * по той же причине, по какой он есть у уюта: иначе шесть богов
     * решали бы настроение целиком, и всё остальное — еда, дом, работа —
     * перестало бы значить что-либо.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith", tickLimit = 600)
    public void theTempleAndTheHeardGodsComfortTheColony(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic chapelPlan = schematic(context, CHAPEL_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building chapel = plan(colony, anchor, CHAPEL_TYPE, BlockRotation.NONE);

        try {
            if (Faith.solace(world, colony) != 0) {
                context.throwGameTestException("Недостроенная часовня уже греет");
            }

            stockFor(world, colony, chapelPlan);
            if (BuildJob.advance(world, manager, colony.id(), chapel.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Часовня не достроилась");
            }

            if (!Faith.hasTemple(colony)) {
                context.throwGameTestException("Часовня построена, а храмом не считается");
            }
            if (Faith.solace(world, colony) != 1) {
                context.throwGameTestException("Храм даёт " + Faith.solace(world, colony)
                        + " очков покоя, а обещал одно");
            }

            colony.addFavour(SOWER, Faith.Tier.HEARD.from());
            if (Faith.solace(world, colony) != 2) {
                context.throwGameTestException("Услышанный бог не прибавил покоя: "
                        + Faith.solace(world, colony));
            }

            colony.addFavour(MASON, Faith.Tier.HEARD.from());
            colony.addFavour(WATCHMAN, Faith.Tier.HEARD.from());
            if (Faith.solace(world, colony) != Faith.MOST_SOLACE) {
                context.throwGameTestException("Покой перевалил за предел: "
                        + Faith.solace(world, colony));
            }
        } finally {
            demolish(world, chapel, chapelPlan);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * У народа с пантеоном есть храм, а у храма — пантеон.
     * <p>
     * Целость в обе стороны, и обе половины уже ломались в этом моде
     * на других системах: культура объявляла здание без схемы, и ступень
     * обещала ратушу, которой нет. Боги без храма — это жертвы, которые
     * некуда положить; храм без богов — здание, в котором нечего делать.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void everyPantheonHasATempleAndEveryTempleAPantheon(TestContext context) {
        List<String> complaints = new ArrayList<>();

        for (Map.Entry<Identifier, Culture> entry : CultureManager.all().entrySet()) {
            Identifier culture = entry.getKey();
            boolean pantheon = !Gods.of(culture).isEmpty();
            Identifier temple = Faith.templeType(culture).orElse(null);

            if (pantheon && temple == null) {
                complaints.add(culture + ": боги есть, а алтарь ставить негде");
            }
            if (!pantheon && temple != null) {
                complaints.add(culture + ": храм " + temple + " есть, а молиться некому");
            }
            if (temple != null && !entry.getValue().buildings().contains(temple)) {
                complaints.add(culture + ": храм " + temple + " не объявлен культуре — "
                        + "его не закажешь");
            }
        }

        if (!complaints.isEmpty()) {
            context.throwGameTestException("Вера объявлена наполовину:\n  "
                    + String.join("\n  ", complaints));
        }

        context.complete();
    }

    /**
     * Чудо отказывает <b>до</b> списания, а не после.
     * <p>
     * Чудо стоит трёх благословений, то есть полусотни игровых дней.
     * «Нажал, а ничего не случилось» тут не мелкая досада: это ровно
     * тот случай, когда молчащая кнопка стоит игроку прохождения.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void theMiracleRefusesBeforeItSpends(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            colony.addFavour(MASON, Faith.MIRACLE_COST + 10);

            // Стройки нет — просить нечего.
            Miracles.Verdict nothing = Miracles.call(world, manager, colony, Domain.STONE, 6);
            if (nothing != Miracles.Verdict.NOTHING_TO_DO) {
                context.throwGameTestException("Чудо на пустом месте: " + nothing);
            }
            if (colony.favourOf(MASON) != Faith.MIRACLE_COST + 10) {
                context.throwGameTestException("Отказ всё-таки списал очки: "
                        + colony.favourOf(MASON));
            }

            // И у бога, которого поселение ещё не услышало, — свой отказ.
            Miracles.Verdict deaf = Miracles.call(world, manager, colony, Domain.WATCH, 6);
            if (deaf != Miracles.Verdict.NOT_HEARD) {
                context.throwGameTestException("Неуслышанный бог сотворил чудо: " + deaf);
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Благословение просят со второй ступени, и оно стоит очков.
     * <p>
     * Плата — единственное, ради чего у благосклонности вообще есть цена:
     * без неё копить было бы незачем, а выбор «помощь сегодня или
     * избранничество когда-нибудь» исчез бы вместе с ней.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "faith")
    public void theBlessingCostsFavourAndNeedsATier(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            if (Blessings.invoke(manager, colony, Domain.HARVEST, 2)
                    != Blessings.Verdict.NOT_NOTICED) {
                context.throwGameTestException("Незамеченная колония выпросила благословение");
            }

            // Ровно на пороге ступени — и ровно на одно благословение.
            // Сорок написаны числом: цена ступени решение по игре,
            // и пинать его должна проверка, а не наоборот.
            colony.addFavour(SOWER, 40);
            int before = colony.favourOf(SOWER);
            if (Blessings.invoke(manager, colony, Domain.HARVEST, 2) != Blessings.Verdict.DONE) {
                context.throwGameTestException("На пороге ступени благословение не легло");
            }
            if (colony.favourOf(SOWER) != 0) {
                context.throwGameTestException("После платы осталось "
                        + colony.favourOf(SOWER) + ", а платили всем, что было (" + before + ")");
            }
            if (!Faith.blessed(colony, Domain.HARVEST, 2)) {
                context.throwGameTestException("Заплатили, а благословения нет");
            }
            if (Faith.blessed(colony, Domain.HARVEST, 2 + Faith.BLESSING_DAYS)) {
                context.throwGameTestException("Благословение не кончается");
            }

            // Избранника не благословляют за деньги: у него держится само.
            colony.addFavour(SOWER, Faith.Tier.CHOSEN.from());
            if (Blessings.invoke(manager, colony, Domain.HARVEST, 99)
                    != Blessings.Verdict.ALWAYS_ON) {
                context.throwGameTestException("С избранника взяли плату за то, что и так есть");
            }
            if (!Faith.blessed(colony, Domain.HARVEST, 99)) {
                context.throwGameTestException("У избранника благословение не держится");
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ни одно здание мода не остаётся без источника света.
     * <p>
     * Темнота в Minecraft — это не настроение, а правило порождения:
     * в клетке с освещённостью ноль ночью встаёт моб. Мастерская, склад
     * и ткацкая стояли глухими коробками без окон и очага, и к утру
     * внутри ждал скелет — того самого жителя, который придёт работать.
     * Поэтому свет здесь не украшение, и проверка не «косметическая».
     * <p>
     * Проверка одна на все схемы нарочно: фонарь вешает генератор сам,
     * по одному правилу на весь мод, и новое здание получит его, не
     * спросив меня. Но правило ищет потолок — у поля и рощи потолка нет,
     * и там свет написан руками на угловых столбах ограды. Забыть его
     * в новом уличном здании легче всего, и ловит это ровно эта строка.
     * <p>
     * Спрашивается яркость <b>состояния блока</b>, а не список имён:
     * список пришлось бы дописывать под каждый новый светильник, и он
     * молча устарел бы на первом же факеле души.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "everything")
    public void noBuildingIsLeftDark(TestContext context) {
        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> dark = new ArrayList<>();
        for (Identifier id : all) {
            Schematic schematic = SchematicLoader.get(id).orElseThrow();
            boolean lit = schematic.blocks().stream()
                    .anyMatch(block -> schematic.blockAt(block.paletteIndex()).getLuminance() > 0);
            if (!lit) {
                dark.add(id.toString());
            }
        }

        if (!dark.isEmpty()) {
            context.throwGameTestException("Здания, в которых ночью нечем светить:\n  "
                    + String.join("\n  ", dark));
        }

        context.complete();
    }

    /**
     * Билдер достраивает <b>каждую</b> схему мода, ни разу не встав в воздух.
     * <p>
     * До сих пор досягаемость проверялась на двух зданиях: доме норманнов
     * второго уровня и храме майя. Обоих я выбирал сам — «самые высокие», —
     * и это ровно та ошибка, за которую игрок меня отругал: я проверял там,
     * где смотрел, а не там, где играют. Складов, мастерских и террасных
     * полей не проверял никто, а схем в моде восемнадцать.
     * <p>
     * Проверяется <b>чистый выбор</b> билдера, без тела в мире: у стройки
     * спрашивается место, где он будет стоять, и оттуда же делается шаг.
     * Так надо по двум причинам. Тело, созданное в этом же тике, мир ещё
     * не отдаёт по опознавателю — тест с телами падал бы не по своей вине.
     * А главное, проверять надо именно решение: «куда стратегия его
     * послала», а не «дошёл ли он», — иначе тест мигает от чужой ходьбы.
     * <p>
     * Проверка нарочно одна на все схемы: добавится девятнадцатая — она
     * проверится сама, и забыть о ней будет нельзя.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "everything")
    public void everySchematicIsBuiltFromStandableSpots(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> complaints = new ArrayList<>();
        List<BlockPos> ground = new ArrayList<>();

        // Площадка с запасом на самую большую схему: поле второго уровня
        // девять на девять, а билдеру надо где стоять вокруг.
        //
        // Ратуша стоит в десяти блоках от угла стройки и вне её следа.
        // Ближе двенадцати — потому что дальше билдер не берёт со склада
        // сам (BuildJob.NEARBY_STORAGE), и проверка досягаемости
        // выродилась бы в проверку подвоза. Вне следа — чтобы схема
        // не накрыла ратушу.
        BlockPos hall = context.getAbsolutePos(new BlockPos(10, 9, 3));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));

        try {
            for (int x = -3; x <= 15; x++) {
                for (int z = -3; z <= 15; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            for (Identifier id : all) {
                Schematic schematic = SchematicLoader.get(id).orElseThrow();
                Identifier type = BuildJob.buildingTypeOf(id).orElse(null);
                if (type == null) {
                    complaints.add(id + ": тип здания не выводится из имени схемы");
                    continue;
                }

                Identifier culture = new Identifier(type.getNamespace(),
                        type.getPath().split("/")[0]);
                Settlement colony = colonyWithBuilder(world, manager, hall, culture);
                Building site = new Building(UUID.randomUUID(), type,
                        BuildJob.levelOf(id).orElse(1), anchor, BlockRotation.NONE,
                        BuildProgress.PLANNED, List.of());
                colony.addBuilding(site);

                try {
                    stockSite(site, schematic);
                    complaints.addAll(raiseFromStandableSpots(world, manager, colony, site,
                            schematic, id));
                } finally {
                    demolish(world, site, schematic);
                    manager.remove(colony.id());
                }
            }

            if (!complaints.isEmpty()) {
                context.throwGameTestException("Схемы, на которых билдер спотыкается:\n  "
                        + String.join("\n  ", complaints));
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
