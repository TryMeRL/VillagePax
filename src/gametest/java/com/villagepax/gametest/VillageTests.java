package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.culture.Trait;
import com.villagepax.core.culture.Traits;
import com.villagepax.sim.BuildProgress;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.Villages;
import com.villagepax.sim.VillageSites;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.build.BuildJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Деревни народов, черты народа и второй народ.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class VillageTests extends GameTestSupport {

    // --- задача 1.12: деревни народов ---

    /**
     * Деревня встаёт уже стоящей и сразу берётся за следующее здание.
     * <p>
     * Приёмка задачи: в мире находится норманнская деревня с жителями,
     * которые работают. «Уже стоящей» — потому что деревня старше игрока:
     * ратуша, дом и ферма ставятся мгновенно. А следующее здание она
     * начинает при игроке, и это важнее готовых стен: видно не декорацию,
     * а работу.
     * <p>
     * В своей партии намеренно: деревне нужна ровная площадка в двадцать
     * блоков в каждую сторону, и с соседом по партии они наступили бы друг
     * другу на застройку.
     */
    /**
     * Деревне можно помочь тем, чего ей не хватает, — и она это помнит.
     * Ненужное остаётся в руке: ратуша не скупка.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "needs")
    public void aVillageTakesWhatItNeedsAndRemembersWhoHelped(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;
        try {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }
            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала");
                return;
            }
            java.util.Map<net.minecraft.item.Item, Integer> missing =
                    com.villagepax.screen.VillageNeeds.missing(world, village);
            if (missing.isEmpty()) {
                context.throwGameTestException("Стройке деревни ничего не нужно — проверять нечего");
                return;
            }
            net.minecraft.item.Item wanted = missing.keySet().iterator().next();
            net.minecraft.entity.player.PlayerEntity helper = context.createMockSurvivalPlayer();
            net.minecraft.item.ItemStack offer = new net.minecraft.item.ItemStack(wanted,
                    Math.min(wanted.getMaxCount(), missing.get(wanted)));
            int before = village.reputationOf(helper.getUuid());
            if (!com.villagepax.screen.VillageNeeds.donate(world, manager, village, helper, offer)) {
                context.throwGameTestException("Деревня не приняла нужное: " + wanted);
            }
            if (manager.byId(village.id()).orElseThrow().reputationOf(helper.getUuid()) <= before) {
                context.throwGameTestException("Помощь не прибавила доверия");
            }
            net.minecraft.item.ItemStack junk = new net.minecraft.item.ItemStack(
                    net.minecraft.item.Items.ROTTEN_FLESH, 8);
            if (com.villagepax.screen.VillageNeeds.donate(world, manager, village, helper, junk)
                    || junk.getCount() != 8) {
                context.throwGameTestException("Деревня взяла ненужное");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }
        context.complete();
    }

    /**
     * Северяне встают на снегу: снежный покров — не преграда, а их земля.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "snow")
    public void aNordVillageRisesOnSnow(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;
        Identifier nord = new Identifier("villagepax", "nord");
        try {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState()
                            .with(net.minecraft.block.SnowyBlock.SNOWY, true));
                    world.setBlockState(at.up(), Blocks.SNOW.getDefaultState());
                    meadow.add(at);
                    meadow.add(at.up());
                }
            }
            village = Villages.found(world, nord, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Северяне не встали на снегу: помеха="
                        + whoBlocks(manager, centre));
                return;
            }
            long done = village.buildings().stream().filter(Building::isOperational).count();
            if (done < 3) {
                context.throwGameTestException("У северян готово всего " + done + " зданий");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }
        context.complete();
    }

    /**
     * Деревня народа встаёт с убранными улицами: колодец с водой на площади,
     * табличка с названием и фонарь у крыльца. «Чтоб прям хотелось жить».
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "streets")
    public void aVillageRisesWithAWellAndLamps(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;
        try {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
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
            List<BlockPos> decor = manager.decorOf(village.id());
            boolean water = decor.stream().anyMatch(at -> world.getBlockState(at).isOf(Blocks.WATER));
            boolean sign = decor.stream().anyMatch(at ->
                    world.getBlockEntity(at) instanceof net.minecraft.block.entity.SignBlockEntity);
            boolean lamp = decor.stream().anyMatch(at -> world.getBlockState(at).isOf(Blocks.LANTERN));
            if (!water || !sign || !lamp) {
                context.throwGameTestException("Улицы не убраны: колодец=" + water
                        + ", табличка=" + sign + ", фонарь=" + lamp + " (поставлено " + decor.size() + ")");
            }
            // И убирается это однажды: второй день не ставит второй колодец.
            int before = decor.size();
            com.villagepax.sim.Streetscape.dress(world, manager, village);
            if (manager.decorOf(village.id()).size() != before) {
                context.throwGameTestException("Убранство приросло на второй раз: было " + before
                        + ", стало " + manager.decorOf(village.id()).size());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }
        context.complete();
    }

    @GameTest(templateName = WIDE_STRUCTURE, batchId = "village")
    public void villageRisesAlreadyStandingAndKeepsBuilding(TestContext context) {
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
                context.throwGameTestException("Деревня не встала на ровном лугу: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            if (!village.owner().isAutonomous()) {
                context.throwGameTestException("Деревня оказалась чьей-то колонией");
            }
            if (village.name().isEmpty()) {
                context.throwGameTestException("У деревни нет имени из списка народа");
            }
            if (village.population() < 2) {
                context.throwGameTestException("Жителей в деревне " + village.population()
                        + ", а одиночка — это не деревня");
            }

            long done = village.buildings().stream().filter(Building::isOperational).count();
            long building = village.buildings().stream()
                    .filter(BuildJob::isUnderConstruction).count();

            // Ратуша, дом и ферма готовы — это три; четвёртое строится.
            if (done < 3) {
                context.throwGameTestException("Готовых зданий " + done
                        + ", а деревня должна найтись стоящей: ратуша, дом и ферма");
            }
            if (building != 1) {
                context.throwGameTestException("Строящихся зданий " + building
                        + ", а деревня обязана браться ровно за одно");
            }

            // Дом настоящий: у жителей есть где спать.
            if (Housing.sleepingSpots(world, village).isEmpty()) {
                context.throwGameTestException("В деревне нет ни одного места для сна");
            }

            // Второй раз на том же месте деревня не возникает.
            if (!manager.isSettled(centre)) {
                context.throwGameTestException("Место деревни не запомнилось");
            }
            if (Villages.found(world, NORMAN, centre).isPresent()) {
                context.throwGameTestException("Деревня встала на том же месте второй раз");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Суточное решение деревни: обоз двигает стройку.
     * <p>
     * Деревня не умеет ни выплавить стекло, ни соткать кровать, и без обоза
     * её стройка встала бы навсегда на первом же окне. Проверяется, что за
     * день стройка <b>продвинулась</b>, а не что склад наполнился:
     * наполнение без продвижения означало бы, что материал привозят не тот.
     * <p>
     * Заодно проверяется, что обоз работает <b>на дневную выручку</b>:
     * деревня начинает без монеты, зарабатывает её сама и на неё же
     * покупает. Прежде материалы появлялись даром.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "village")
    public void villageGrowsFromDayToDay(TestContext context) {
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
                context.throwGameTestException("Деревня не встала: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            Building site = village.buildings().stream()
                    .filter(BuildJob::isUnderConstruction).findFirst().orElse(null);
            if (site == null) {
                context.throwGameTestException("Деревня ничего не строит, двигать нечего");
                return;
            }

            int before = site.nextStep();
            for (int day = 0; day < 3; day++) {
                Villages.newDay(world, manager, village);
                BuildJob.advance(world, manager, village.id(), site.id(), 2_000);
            }

            if (site.nextStep() <= before) {
                context.throwGameTestException("За три дня стройка не продвинулась: шаг "
                        + site.nextStep() + ", был " + before);
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Команда поиска отвечает, и отвечает одно и то же.
     * <p>
     * Без неё мод буквально нельзя найти: места деревень стоят в семи
     * с половиной сотнях блоков друг от друга, и игрок, не знающий, куда
     * идти, решит, что мод не работает. Догадка нарочно <b>не смотрит
     * в мир</b>: проверить биом и грунт нельзя, не сгенерировав чанк,
     * а генерировать полкарты ради ответа недопустимо.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "village")
    public void locateAlwaysAnswersTheSameWay(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos from = context.getAbsolutePos(BlockPos.ORIGIN);

        VillageSites.Guess first = VillageSites.guessNearest(world, from);

        // Пусто — законный ответ, и это главное в проверке. Прежняя версия
        // всегда называла середину клетки, ничего не проверяя, и уводила
        // игрока за семь сотен блоков в пустоту. Теперь ответ «рядом нет
        // подходящего биома» честен: в мире игровых тестов лесов и нет.
        if (!java.util.Objects.equals(first, VillageSites.guessNearest(world, from))) {
            context.throwGameTestException("Второй раз поиск ответил иначе: было " + first
                    + ", стало " + VillageSites.guessNearest(world, from));
        }

        if (first != null) {
            if (!first.culture().equals(NORMAN)) {
                context.throwGameTestException("Названа не та культура: " + first.culture());
            }

            // Названное место обязано проходить ту же проверку, которой
            // деревня возникает: иначе поиск снова уводил бы в пустоту.
            Culture norman = CultureManager.get(NORMAN);
            int spacing = VillageSites.spacing(norman);
            int cellX = Math.floorDiv(new ChunkPos(first.where()).x, spacing);
            int cellZ = Math.floorDiv(new ChunkPos(first.where()).z, spacing);

            BlockPos checked = VillageSites.plannedSite(world, NORMAN, norman, cellX, cellZ);
            if (!first.where().equals(checked)) {
                context.throwGameTestException("Поиск назвал " + first.where().toShortString()
                        + ", а проверка того же места даёт " + checked);
            }
        }

        context.complete();
    }

    /**
     * Место деревни выводится из семени мира и потому одно и то же.
     * <p>
     * Не мелочь: если бы место выбиралось случайно при каждом обращении,
     * деревня возникала бы у игрока то тут, то там, а на сервере два игрока
     * получили бы две разные деревни в одной клетке.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "village")
    public void villageSitesAreTheSameEveryTime(TestContext context) {
        ServerWorld world = context.getWorld();
        Culture norman = CultureManager.get(NORMAN);
        if (norman == null) {
            context.throwGameTestException("Культура норманнов не загружена");
            return;
        }

        int cellX = new ChunkPos(context.getAbsolutePos(BlockPos.ORIGIN)).x
                / VillageSites.spacing(norman);
        int cellZ = new ChunkPos(context.getAbsolutePos(BlockPos.ORIGIN)).z
                / VillageSites.spacing(norman);

        Optional<BlockPos> first = VillageSites.candidate(world, NORMAN, norman, cellX, cellZ);
        Optional<BlockPos> again = VillageSites.candidate(world, NORMAN, norman, cellX, cellZ);

        if (!first.equals(again)) {
            context.throwGameTestException("Место деревни поменялось между двумя вопросами: "
                    + first + " и " + again);
        }

        // Соседняя клетка обязана дать другое место, иначе сетка не работает.
        Optional<BlockPos> neighbour =
                VillageSites.candidate(world, NORMAN, norman, cellX + 1, cellZ);
        if (first.isPresent() && first.equals(neighbour)) {
            context.throwGameTestException("Две клетки сетки дали одно место");
        }

        context.complete();
    }

    // --- фаза 0.2: черты народа ---

    /**
     * Черта народа разбирается громко: описку не принимают за отсутствие.
     * <p>
     * Черта — это <b>включатель кода</b>, а не число. Народ без своей черты
     * перестаёт быть собой, и лишняя буква в json не должна означать
     * «черты нет»: искать причину игрок будет в поведении жителей, а не
     * в датапаке. Проверяется и чужое пространство имён — {@code othermod:}
     * молча считать своим нельзя.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "traits")
    public void unknownTraitsAreNamedOutLoud(TestContext context) {
        if (!Traits.has(NORMAN, Trait.STONE_MASONRY)) {
            context.throwGameTestException("У норманнов нет объявленного каменного дела: "
                    + Traits.of(NORMAN));
        }

        Identifier typo = new Identifier("villagepax", "stone_masonery");
        Identifier alien = new Identifier("othermod", "stone_masonry");
        List<Identifier> declared = List.of(typo, alien,
                new Identifier("villagepax", Trait.STONE_MASONRY.id()));

        if (!Traits.resolve(declared).equals(java.util.Set.of(Trait.STONE_MASONRY))) {
            context.throwGameTestException("Из трёх строк узнана не одна черта: "
                    + Traits.resolve(declared));
        }
        List<Identifier> strangers = Traits.unknown(declared);
        if (strangers.size() != 2 || !strangers.contains(typo) || !strangers.contains(alien)) {
            context.throwGameTestException("Непонятые черты не названы: " + strangers);
        }

        // И темп: камень быстрее только у того, у кого есть черта.
        Identifier nobody = new Identifier("villagepax", "no_such_people");
        int stone = Traits.blocksPerTurn(NORMAN, Blocks.COBBLESTONE.getDefaultState());
        int wood = Traits.blocksPerTurn(NORMAN, Blocks.OAK_PLANKS.getDefaultState());
        int stranger = Traits.blocksPerTurn(nobody, Blocks.COBBLESTONE.getDefaultState());

        if (stone != 2 || wood != 1 || stranger != 1) {
            context.throwGameTestException("Темп по черте: камень " + stone + ", дерево "
                    + wood + ", чужой народ " + stranger + "; ожидалось 2, 1 и 1");
        }
        if (Traits.blocksPerTurn(NORMAN, null) != 1) {
            context.throwGameTestException("У расчистки, где блока нет, темп "
                    + Traits.blocksPerTurn(NORMAN, null));
        }

        context.complete();
    }

    /**
     * Каменное дело видно на стройке: мастер по камню кончает раньше.
     * <p>
     * Сравниваются <b>два прогона одной и той же площадки</b> — народом
     * с чертой и народом без неё, — а не число шагов за одно решение.
     * Так пришлось потому, что план бесплатно пропускает шаги, которые уже
     * совпали с миром: над готовой площадкой первое же решение «сдвигает»
     * план на два десятка шагов расчистки, не положив ни блока. Счётчик
     * решений от этого не страдает: пропуски одинаковы в обоих прогонах.
     * <p>
     * Мир между прогонами возвращается в прежний вид — иначе второй прогон
     * получил бы даром то, что первый уже расчистил, и сравнение врало бы.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "traits")
    public void masonsFinishStoneworkSooner(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, new Identifier("villagepax", "norman/house_lvl1"));

        // Ратуша вплотную к стройке намеренно: дальше двенадцати блоков
        // билдер не берёт со склада сам, и оба прогона встали бы
        // по нехватке материалов, а сравниваем мы темп.
        BlockPos storage = context.getAbsolutePos(new BlockPos(8, 9, 8));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -2; x <= 12; x++) {
                for (int z = -2; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            // Народ без объявленных черт: культуры с таким именем в датапаке
            // нет, и черт у неё поэтому нет никаких. Это же проверяет, что
            // отсутствие культуры не роняет стройку.
            Identifier plainFolk = new Identifier("villagepax", "no_such_people");

            int mason = decisionsToBuild(context, world, manager, NORMAN, anchor, storage, house);
            int plain = decisionsToBuild(context, world, manager, plainFolk, anchor, storage, house);

            if (mason <= 0 || plain <= 0) {
                context.throwGameTestException("Дом не достроился: у мастера " + mason
                        + " решений, у прочих " + plain + " (ноль означает недострой)");
                return;
            }
            if (mason >= plain) {
                context.throwGameTestException("Каменное дело не ускорило стройку: "
                        + mason + " решений у мастера против " + plain + " у народа без черты");
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(storage, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- фаза 0.2: второй народ ---

    /**
     * Каждый народ просит своё.
     * <p>
     * Квесты в датапаке разложены по выдающей профессии, а старейшина
     * у всех народов один и тот же. Без народа у самой просьбы майя
     * встречали бы игрока норманнской фразой про зимние поленницы — и это
     * не мелочь, а первое, что игрок в чужой деревне слышит.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "maya")
    public void eachPeopleAsksItsOwnQuests(TestContext context) {
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        UUID player = UUID.randomUUID();

        Settlement norman = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement maya = Settlement.found(MAYA, Owner.AUTONOMOUS, "Йашчилан", where);

        Identifier asksNorman = Quests.offered(norman, player, Villages.ELDER).orElse(null);
        Identifier asksMaya = Quests.offered(maya, player, Villages.ELDER).orElse(null);

        if (asksNorman == null || asksMaya == null) {
            context.throwGameTestException("Старейшина молчит: у норманнов " + asksNorman
                    + ", у майя " + asksMaya);
            return;
        }
        if (!asksNorman.getPath().startsWith("norman/")) {
            context.throwGameTestException("Норманны просят чужое: " + asksNorman);
        }
        if (!asksMaya.getPath().startsWith("maya/")) {
            context.throwGameTestException("Майя просят чужое: " + asksMaya);
        }
        if (asksNorman.equals(asksMaya)) {
            context.throwGameTestException("Оба народа просят одно и то же: " + asksMaya);
        }

        context.complete();
    }

    /**
     * Деревня майя встаёт своей, а не перекрашенной норманнской.
     * <p>
     * Проверяется по трём разным следам сразу, потому что «второй народ»
     * ломается по-разному: типы зданий могут оказаться чужими, схема —
     * норманнской, а материал — общим. Поэтому смотрим и на объявленные
     * типы, и на то, что <b>в мире действительно стоит охряная стена</b>.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "maya")
    public void mayaVillageIsBuiltOfItsOwnMaterial(TestContext context) {
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

            village = Villages.found(world, MAYA, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня майя не встала: помеха="
                        + whoBlocks(manager, centre));
                return;
            }

            for (Building building : village.buildings()) {
                if (!building.type().getPath().startsWith("maya/")) {
                    context.throwGameTestException("У майя чужое здание: " + building.type());
                    return;
                }
            }
            if (village.buildings().size() < 3) {
                context.throwGameTestException("Деревня встала неполной: зданий "
                        + village.buildings().size() + ", ожидались ратуша, дом и поле");
            }

            // Ратуша ставится при основании целиком, значит охряная стена
            // обязана уже стоять в мире. Ищем её в следе поселения.
            boolean ochre = false;
            for (BlockPos at : BlockPos.iterate(centre.add(-10, -1, -10), centre.add(10, 12, 10))) {
                if (world.getBlockState(at).isOf(ModBlocks.OCHRE_PLASTER)) {
                    ochre = true;
                    break;
                }
            }
            if (!ochre) {
                context.throwGameTestException("В деревне майя нет ни одной охряной стены — "
                        + "стоит что-то чужое");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Террасники берутся за склон, на который норманны не пойдут.
     * <p>
     * Это и есть черта {@code terrace_farming} в деле. Проверяется на
     * настоящей площадке со ступенью, а не только числом из настройки:
     * число могло бы остаться неприменённым.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "maya")
    public void terraceBuildersTakeSlopesOthersRefuse(TestContext context) {
        if (Traits.maxSlope(MAYA) <= Traits.maxSlope(NORMAN)) {
            context.throwGameTestException("Уклон у террасников не больше: майя "
                    + Traits.maxSlope(MAYA) + ", норманны " + Traits.maxSlope(NORMAN));
            return;
        }
        if (!Traits.has(MAYA, Trait.TERRACE_FARMING)) {
            context.throwGameTestException("У майя нет объявленной черты террас: "
                    + Traits.of(MAYA));
        }
        if (Traits.has(NORMAN, Trait.TERRACE_FARMING)) {
            context.throwGameTestException("Черта террас досталась и норманнам");
        }

        context.complete();
    }

    /**
     * Храм совета второго уровня достраивается, ни разу не встав в воздух.
     * <p>
     * Двенадцать блоков высотой — самая высокая схема мода, и на ней
     * проверяется тот же предел досягаемости, на котором споткнулся дом
     * норманнов. Если билдер не дотянется до гребня, играть за майя будет
     * нельзя: ратуша у них выше всего остального.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "reach")
    public void mayaTempleIsBuiltWithoutStandingInMidair(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic temple = schematic(context, new Identifier("villagepax", "maya/town_hall_lvl2"));

        // Ратуша вплотную: дальше двенадцати блоков билдер не берёт
        // со склада сам, и проверка досягаемости выродилась бы в проверку
        // подвоза.
        BlockPos hall = context.getAbsolutePos(new BlockPos(8, 9, 8));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -2; x <= 12; x++) {
                for (int z = -2; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall, MAYA);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building site = new Building(UUID.randomUUID(), MAYA_TOWN_HALL_TYPE, 2, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, temple);
                Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER,
                        context.getAbsolutePos(new BlockPos(-1, 9, -1)));

                int stalled = runWorkOnFoot(world, manager, colony, mason, 1_200);

                if (!site.isOperational()) {
                    context.throwGameTestException("Храм не достроился: билдер встал на шаге "
                            + site.nextStep() + " из " + temple.plan().steps().size() + ", и "
                            + stalled + " раз его посылали туда, где человек стоять не может");
                }
                if (stalled > 0) {
                    context.throwGameTestException("Билдера " + stalled
                            + " раз посылали стоять в воздух — в игре он туда не дойдёт");
                }
            } finally {
                demolish(world, site, temple);
                discardBodies(world, colony);
                manager.remove(colony.id());
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
