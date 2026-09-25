package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Owner;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.QuestNet;
import com.villagepax.core.config.Config;
import com.villagepax.core.config.Configs;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.trade.Caravan;
import com.villagepax.core.trade.TradeTable;
import net.minecraft.inventory.SimpleInventory;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Caravans;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.trade.Trading;
import com.villagepax.item.ModItems;
import com.villagepax.item.PurseItem;
import com.villagepax.sim.Villages;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Монета, торг, кошель, обозы, квесты и доверие.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class EconomyTests extends GameTestSupport {

    // --- задача 1.13: квесты и репутация ---

    /**
     * Цепочка проходится по порядку и кончается.
     * <p>
     * Порядок задан не списком, а ссылками {@code next}: каждый квест
     * называет следующий. Проверяется именно проход по ссылкам, потому что
     * кольцевая или оборванная ссылка в датапаке — это игрок, у которого
     * цепочка не идёт дальше первого шага.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void questChainWalksInOrderAndEnds(TestContext context) {
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        UUID player = UUID.randomUUID();

        if (!QuestManager.get(FOUNDING_1).isPresent()) {
            context.throwGameTestException("Стартовая цепочка не загружена. Загружены: "
                    + QuestManager.ids());
            return;
        }

        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(FOUNDING_1))) {
            context.throwGameTestException("Первым предложен не первый квест: "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        village.noteQuestDone(player, FOUNDING_1);
        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(FOUNDING_2))) {
            context.throwGameTestException("После первого квеста не предложен второй: "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        village.noteQuestDone(player, FOUNDING_2);
        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(FOUNDING_3))) {
            context.throwGameTestException("После второго квеста не предложен третий: "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        // После чертежа разговор НЕ кончается: у старейшины есть ещё три
        // шага — своё дерево, свой храм и знакомство с соседями. Раньше он
        // замолкал здесь навсегда, и это было самое заметное место, где
        // у мода кончался разговор.
        village.noteQuestDone(player, FOUNDING_3);
        Identifier fourth = new Identifier("villagepax", "norman/founding_4");
        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(fourth))) {
            context.throwGameTestException("После чертежа цепочка оборвалась: предложено "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        // Пройти её до конца и убедиться, что конец всё-таки есть:
        // кольцевая ссылка в датапаке вешала бы обход.
        for (String step : List.of("norman/founding_4", "norman/founding_5",
                "norman/founding_6")) {
            village.noteQuestDone(player, new Identifier("villagepax", step));
        }
        if (Quests.offered(village, player, Villages.ELDER).isPresent()) {
            context.throwGameTestException("Написанная цепочка не кончилась: предложено "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        // Другому игроку та же деревня предлагает цепочку с начала.
        if (!Quests.offered(village, UUID.randomUUID(), Villages.ELDER)
                .equals(Optional.of(FOUNDING_1))) {
            context.throwGameTestException("Второму игроку цепочка не начинается заново");
        }

        manager.remove(village.id());
        context.complete();
    }

    /**
     * Цепочка кончается чертежом ратуши, и это вход в мод.
     * <p>
     * Решение дизайна: игрок находит деревню, выполняет цепочку, дорастает
     * до друга и получает чертёж. Крафт остался подстраховкой на неудачный
     * сид. Поэтому награда последнего квеста проверяется прямо: без неё
     * весь вход в мод обрывается, а понять это можно было бы только
     * доиграв цепочку до конца руками.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void foundingChainRewardsTheBlueprint(TestContext context) {
        Quest last = QuestManager.get(FOUNDING_3).orElse(null);
        if (last == null) {
            context.throwGameTestException("Последнего квеста цепочки нет. Загружены: "
                    + QuestManager.ids());
            return;
        }

        boolean givesBlueprint = last.rewards().stream()
                .anyMatch(reward -> reward instanceof Quest.Reward.Give give
                        && give.item() == ModItems.TOWN_HALL_BLUEPRINT);
        if (!givesBlueprint) {
            context.throwGameTestException("Цепочка не выдаёт чертёж ратуши: " + last.rewards());
        }

        int trust = 0;
        for (Identifier id : List.of(FOUNDING_1, FOUNDING_2, FOUNDING_3)) {
            Quest quest = QuestManager.get(id).orElseThrow();
            for (Quest.Reward reward : quest.rewards()) {
                if (reward instanceof Quest.Reward.Trust up) {
                    trust += up.amount();
                }
            }
            if (!quest.giver().equals(Villages.ELDER)) {
                context.throwGameTestException("Квест " + id + " выдаёт не старейшина: "
                        + quest.giver());
            }
            if (quest.objectives().isEmpty()) {
                context.throwGameTestException("У квеста " + id + " нет ни одной цели");
            }
        }

        if (Standing.of(trust).ordinal() < Standing.FRIEND.ordinal()) {
            context.throwGameTestException("Вся цепочка даёт доверия " + trust
                    + " — это " + Standing.of(trust).id() + ", а чертёж отдают другу");
        }

        context.complete();
    }

    /**
     * Снимок разговора показывает то, что игрок должен видеть.
     * <p>
     * Решение заказчика: чата было мало. Экран обязан говорить, сколько
     * доверия набрано, сколько до следующей ступени, что просят и сколько
     * из этого уже в сумке, — и включать кнопку только когда принесено всё.
     * <p>
     * Считает всё сервер, и проверяется тоже сервер: клиенту достаются
     * готовые числа, иначе правило «сколько считается принесённым» жило бы
     * в двух местах и разошлось бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void questScreenShowsWhatIsAskedAndWhatIsBrought(TestContext context) {
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);

        QuestView empty = QuestNet.viewOf(SettlementManager.get(context.getWorld()), village, player, hands,
                Villages.ELDER,
                Warehouse.of(context.getWorld(), village), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElse(null);
        if (empty == null) {
            context.throwGameTestException("Разговор не собрался вовсе");
            return;
        }
        if (!empty.villageName().equals("Бовуар") || !empty.village().equals(village.id())) {
            context.throwGameTestException("Снимок не про эту деревню: " + empty.villageName());
        }
        if (empty.reputation() != 0 || empty.nextAt().isEmpty()) {
            context.throwGameTestException("У чужака доверие " + empty.reputation()
                    + ", а до следующей ступени " + empty.nextAt());
        }

        QuestView.Offer offer = empty.quest().orElse(null);
        if (offer == null) {
            context.throwGameTestException("Старейшина ничего не просит у чужака — "
                    + "тогда непонятно, что делать");
            return;
        }
        if (offer.objectives().isEmpty()) {
            context.throwGameTestException("В квесте нет требований");
        }
        if (offer.ready()) {
            context.throwGameTestException("Кнопка «Отдать» включена с пустыми руками");
        }

        QuestView.Need need = offer.objectives().get(0);
        if (need.have() != 0 || need.enough()) {
            context.throwGameTestException("С пустыми руками принесено " + need.have());
        }

        // Принесли ровно столько, сколько просят: кнопка обязана включиться.
        // Требование теперь бывает и без предмета, поэтому вещь спрашивается
        // явно: у цели «поставь склад» её нет, и падать здесь проверка
        // должна словами, а не NoSuchElementException.
        hands.addStack(new ItemStack(need.item().orElseThrow(() -> new AssertionError(
                "первое требование входного квеста обязано быть вещью")), need.need()));

        QuestView full = QuestNet.viewOf(SettlementManager.get(context.getWorld()), village, player, hands,
                Villages.ELDER,
                Warehouse.of(context.getWorld(), village), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElseThrow();
        QuestView.Offer ready = full.quest().orElseThrow();
        if (!ready.ready()) {
            context.throwGameTestException("Принесено всё, а кнопка выключена");
        }
        if (!ready.objectives().get(0).enough()) {
            context.throwGameTestException("Требование не считается выполненным: "
                    + ready.objectives().get(0).have() + " из "
                    + ready.objectives().get(0).need());
        }
        if (ready.rewards().isEmpty()) {
            context.throwGameTestException("Награда не показана — за что тогда нести");
        }

        context.complete();
    }

    /**
     * Полный проход входа в мод: цепочка сдаётся и кончается чертежом.
     * <p>
     * <b>Приёмочный тест всей версии 0.1</b> в той части, которую можно
     * проверить без игрока: найти деревню, сдать три квеста, получить
     * чертёж ратуши. Игрока в игровом тесте нет, поэтому вместо его рук —
     * обычный склад, а вместо инвентаря награды — список. Ровно теми же
     * вызовами работает щелчок по старейшине: {@code talk} — тонкая
     * обёртка над {@code handIn}.
     * <p>
     * Проверяется и то, что <b>не</b> отдают: принёс мало — не забрали
     * ничего. Отобранная половина даром была бы хуже отказа.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void wholeFoundingChainPaysOutTheBlueprint(TestContext context) {
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        UUID player = UUID.randomUUID();

        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> paid = new ArrayList<>();

        // Пустые руки: сдавать нечего, но и отбирать нечего.
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.NOT_ENOUGH) {
            context.throwGameTestException("С пустыми руками квест приняли");
        }
        if (!village.questsDone(player).isEmpty() || village.reputationOf(player) != 0) {
            context.throwGameTestException("Отказ всё-таки что-то изменил");
        }

        // Принёс меньше нужного — тоже отказ, и брёвна остались при игроке.
        hands.addStack(new ItemStack(Items.OAK_LOG, 15));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.NOT_ENOUGH) {
            context.throwGameTestException("Приняли пятнадцать брёвен вместо шестнадцати");
        }
        if (hands.count(Items.OAK_LOG) != 15) {
            context.throwGameTestException("У игрока отобрали недостающее: осталось "
                    + hands.count(Items.OAK_LOG));
        }

        // Донёс шестнадцатое — приняли ровно шестнадцать.
        hands.addStack(new ItemStack(Items.OAK_LOG, 1));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.DONE) {
            context.throwGameTestException("Шестнадцать брёвен не приняли");
        }
        if (hands.count(Items.OAK_LOG) != 0) {
            context.throwGameTestException("Забрали не всё нужное или лишнее: осталось "
                    + hands.count(Items.OAK_LOG));
        }
        if (village.reputationOf(player) != 15) {
            context.throwGameTestException("Доверие после первого квеста "
                    + village.reputationOf(player) + ", а обещали пятнадцать");
        }

        // Второй и третий шаги цепочки.
        hands.addStack(new ItemStack(Items.COBBLESTONE, 24));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.DONE) {
            context.throwGameTestException("Второй квест не приняли");
        }

        hands.addStack(new ItemStack(Items.BREAD, 8));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.DONE) {
            context.throwGameTestException("Третий квест не приняли: доверия "
                    + village.reputationOf(player));
        }

        // Чертёж выдан, и деревня считает игрока другом.
        boolean gotBlueprint = paid.stream()
                .anyMatch(stack -> stack.isOf(ModItems.TOWN_HALL_BLUEPRINT));
        if (!gotBlueprint) {
            context.throwGameTestException("Чертёж ратуши не выдан. Выдано: " + paid);
        }
        if (village.standingOf(player).ordinal() < Standing.FRIEND.ordinal()) {
            context.throwGameTestException("После всей цепочки игрок всё ещё "
                    + village.standingOf(player).id());
        }

        // А дальше старейшина просит то, чего бездомному не сделать, —
        // и говорит об этом словами, а не молчит. Это и есть проверка
        // правила «отказ обязан назвать причину»: с пустыми руками
        // и без колонии ответ обязан отличаться от «принесено не всё».
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.NO_COLONY) {
            context.throwGameTestException("Просьба к бездомному не названа невыполнимой: "
                    + Quests.handIn(village, player, Villages.ELDER, hands, paid::add));
        }

        context.complete();
    }

    /**
     * В деревне есть с кем заговорить.
     * <p>
     * Старейшина ставится явно при основании, а не через приоритет найма:
     * без неё деревня — набор домов, в котором игроку нечего делать.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "quests")
    public void villageHasAnElderToTalkTo(TestContext context) {
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

            Citizen elder = village.citizens().stream()
                    .filter(citizen -> citizen.profession().filter(Villages.ELDER::equals).isPresent())
                    .findFirst().orElse(null);
            if (elder == null) {
                context.throwGameTestException("В деревне нет старейшины: "
                        + village.citizens().stream().map(citizen -> citizen.profession()
                                .map(Identifier::toString).orElse("без дела")).toList());
                return;
            }

            // И ей есть что предложить пришедшему.
            if (Quests.offered(village, UUID.randomUUID(), Villages.ELDER).isEmpty()) {
                context.throwGameTestException("Старейшине нечего предложить игроку");
            }

            // Приток жителей старейшину не назначает: в колонии игрока
            // выдавать квесты некому.
            Settlement colony = colonyWithBuilder(world, manager,
                    context.getAbsolutePos(new BlockPos(0, 40, 0)));
            try {
                for (int newcomer = 0; newcomer < 4; newcomer++) {
                    Housing.welcomeNewcomer(world, colony, new java.util.Random(newcomer));
                }
                boolean hiredElder = colony.citizens().stream()
                        .anyMatch(citizen -> citizen.profession()
                                .filter(Villages.ELDER::equals).isPresent());
                if (hiredElder) {
                    context.throwGameTestException("Колония игрока наняла старейшину сама собой");
                }
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(context.getAbsolutePos(new BlockPos(0, 40, 0)),
                        Blocks.AIR.getDefaultState());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    // --- фаза 0.2: монета и торг вместо заглушки «привоз со стороны» ---

    /**
     * Торг двигает и товар, и монету — в обе стороны.
     * <p>
     * Проверяется целиком, потому что <b>сделка обязана быть «всё или
     * ничего»</b>: половина сделки — это либо товар из ниоткуда, либо
     * монеты в никуда. Считается по обеим сторонам сразу: что убыло у
     * игрока, что прибыло на складе, и наоборот.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void tradeMovesGoodsAndCoinBothWays(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        // Сдача, которой не нашлось места, — признак ошибки: в тесте
        // инвентарь заведомо просторен, и список обязан остаться пустым.
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bread = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BREAD, 6).orElse(null);
            if (bread == null) {
                context.throwGameTestException("Норманны не продают хлеб — "
                        + "стол торга не загрузился");
                return;
            }

            TradeTable.Deal glass = Trading.find(colony, Trading.Side.VILLAGE_BUYS,
                    Items.GLASS_PANE, 8).orElseThrow();

            // У деревни хлеб, у игрока монета и стекло. Кошель пуст.
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 12));
            hands.addStack(new ItemStack(ModItems.COIN, 3));
            hands.addStack(new ItemStack(Items.GLASS_PANE, 8));

            // Деревня без монеты не покупает. Отказ — это содержание, не сбой.
            Trading.Outcome poor = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_BUYS, glass,
                    spilled::add);
            if (poor != Trading.Outcome.NO_COIN) {
                context.throwGameTestException("Деревня без монеты купила стекло: " + poor);
            }
            if (hands.count(Items.GLASS_PANE) != 8 || Coins.total(hands) != 3) {
                context.throwGameTestException("Отказ тронул сумку: стекла "
                        + hands.count(Items.GLASS_PANE) + ", монет " + Coins.total(hands));
            }

            // Деревня продаёт. Чужак платит полторы цены: хлеб по цене
            // датапака стоит один изумруд, а с него берут два.
            Trading.Outcome bought = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bread,
                    spilled::add);
            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Покупка хлеба отказана: " + bought);
            }
            if (hands.count(Items.BREAD) != 6 || Coins.total(hands) != 1) {
                context.throwGameTestException("У игрока после покупки хлеба "
                        + hands.count(Items.BREAD) + " хлеба и " + Coins.total(hands)
                        + " монет, ожидалось 6 и 1");
            }
            Warehouse after = Warehouse.of(world, colony);
            if (after.count(Items.BREAD) != 6 || Coins.total(after.coins()) != 2) {
                context.throwGameTestException("На складе после продажи хлеба "
                        + after.count(Items.BREAD) + " хлеба и " + Coins.total(after.coins())
                        + " монет, ожидалось 6 и 2");
            }

            // Теперь кошель не пуст, и стекло деревня берёт. Чужаку платят
            // половину: цена датапака — две монеты, чужаку одна.
            Trading.Outcome sold = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_BUYS, glass,
                    spilled::add);
            if (sold != Trading.Outcome.DONE) {
                context.throwGameTestException("Продажа стекла отказана: " + sold);
            }
            if (hands.count(Items.GLASS_PANE) != 0 || Coins.total(hands) != 2) {
                context.throwGameTestException("У игрока после продажи стекла "
                        + hands.count(Items.GLASS_PANE) + " стекла и " + Coins.total(hands)
                        + " монет, ожидалось 0 и 2");
            }
            Warehouse paid = Warehouse.of(world, colony);
            if (paid.count(Items.GLASS_PANE) != 8 || Coins.total(paid.coins()) != 1) {
                context.throwGameTestException("На складе после покупки стекла "
                        + paid.count(Items.GLASS_PANE) + " стекла и " + Coins.total(paid.coins())
                        + " монет, ожидалось 8 и 1");
            }

            if (!spilled.isEmpty()) {
                context.throwGameTestException("Сдача не влезла в просторный инвентарь: "
                        + spilled);
            }

            // Две удачные сделки — два очка знакомства. Торговлей тебя
            // запоминают в лицо, и это единственное, что она даёт.
            if (colony.reputationOf(player) != 2) {
                context.throwGameTestException("Доверие за две сделки: "
                        + colony.reputationOf(player) + ", ожидалось 2");
            }
        } finally {
            Warehouse.of(world, colony).take(Items.BREAD, 64);
            Warehouse.of(world, colony).take(Items.GLASS_PANE, 64);
            Coins.pay(Warehouse.of(world, colony).coins(),
                    Coins.total(Warehouse.of(world, colony).coins()));
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Доверие решает, что вообще выложат на прилавок, — и торгом его выше
     * знакомства не поднять.
     * <p>
     * Оба правила проверяются вместе, потому что вместе они и работают:
     * колокол норманны отдают только другу, а другом за покупки не
     * становятся. Без верхней черты чертёж ратуши — награда за цепочку
     * квестов — покупался бы хлебом в двести приёмов, и вход в мод
     * превратился бы в перекладывание стопок.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void trustGatesTheGoodsAndTradeStopsAtAcquaintance(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bell = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BELL, 1).orElse(null);
            if (bell == null) {
                context.throwGameTestException("Норманны не продают колокол — "
                        + "стол торга не загрузился");
                return;
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BELL, 1));
            hands.addStack(new ItemStack(ModItems.COIN, 48));

            // Чужаку колокол не продают, даже когда монета при нём.
            Trading.Outcome stranger = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bell,
                    spilled::add);
            if (stranger != Trading.Outcome.NO_TRUST) {
                context.throwGameTestException("Колокол продан чужаку: " + stranger);
            }

            // На один шаг ниже порога — по-прежнему нет.
            colony.addReputation(player, bell.minReputation() - 1);
            if (Trading.trade(colony, player, hands, Warehouse.of(world, colony),
                    Trading.Side.VILLAGE_SELLS, bell, spilled::add)
                    != Trading.Outcome.NO_TRUST) {
                context.throwGameTestException("Порог доверия сдвинут: "
                        + colony.reputationOf(player) + " хватило при пороге "
                        + bell.minReputation());
            }

            colony.addReputation(player, 1);
            int trusted = colony.reputationOf(player);
            Trading.Outcome friend = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bell,
                    spilled::add);
            if (friend != Trading.Outcome.DONE) {
                context.throwGameTestException("Другу колокол не продали: " + friend);
            }
            if (hands.count(Items.BELL) != 1 || Coins.total(hands) != 24) {
                context.throwGameTestException("После покупки колокола у игрока "
                        + hands.count(Items.BELL) + " колоколов и " + Coins.total(hands)
                        + " монет, ожидалось 1 и 24");
            }
            if (colony.reputationOf(player) != trusted) {
                context.throwGameTestException("Торг поднял доверие выше знакомства: было "
                        + trusted + ", стало " + colony.reputationOf(player));
            }
        } finally {
            Warehouse.of(world, colony).take(Items.BELL, 8);
            Coins.pay(Warehouse.of(world, colony).coins(),
                    Coins.total(Warehouse.of(world, colony).coins()));
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Обоз возит за деньги, а не даром.
     * <p>
     * Это и есть замена <b>заглушки</b>, которая жила в моде до сих пор:
     * материалы появлялись на складе деревни из ниоткуда. Проверяется
     * прямо: деревня без монеты не получает ничего, та же деревня с
     * монетой — получает. Если однажды привоз снова станет подарком,
     * первая половина теста упадёт.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "trade")
    public void caravanBringsNothingWithoutCoin(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            // Выручка выключена: иначе деревня заработает монету в том же
            // дне и проверка «без монеты» проверяла бы не то.
            Configs.override(new Config(true, 0, 1.0,
                    Config.DEFAULT.hungerWarnDays(), Config.DEFAULT.hungerLeaveDays(),
                    Config.DEFAULT.villageTradePerDay(), 0,
                    Config.DEFAULT.roadReserve(), Config.DEFAULT.ticksPerDecision(),
                    true, true, Config.DEFAULT.carrySlots(), true,
                    Config.DEFAULT.childDays(), Config.DEFAULT.lifeDays(),
                    Config.DEFAULT.mortality(),
                    Config.DEFAULT.structureDistanceChunks()));

            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: помеха="
                        + whoBlocks(manager, centre));
                return;
            }
            if (village.buildings().stream().noneMatch(BuildJob::isUnderConstruction)) {
                context.throwGameTestException("Деревня ничего не строит — возить нечего");
                return;
            }

            // Никаких колоний поблизости: суточная смена деревни — это
            // не только привоз, но и обоз, а обоз УВОЗИТ товар, и счёт
            // предметов уходит в минус. Появилось это, когда деревни стали
            // встречать игрока полным прилавком: возить стало что, и
            // проверка начала мигать через раз, потому что день обоза
            // зависит от опознавателя деревни.
            for (Settlement colony : List.copyOf(manager.all())) {
                if (!colony.owner().isAutonomous()) {
                    manager.remove(colony.id());
                }
            }

            Warehouse warehouse = Warehouse.of(world, village);
            // Опустошить кошель целиком: монета бывает трёх достоинств,
            // и вычитать её штуками одного вида было бы неверно.
            Coins.pay(warehouse.coins(), Coins.total(warehouse.coins()));

            int beggarly = Warehouse.of(world, village).totalItems();
            Villages.newDay(world, manager, village);
            int afterEmptyDay = Warehouse.of(world, village).totalItems();
            if (afterEmptyDay != beggarly) {
                context.throwGameTestException("Без монеты обоз всё равно привёз "
                        + (afterEmptyDay - beggarly) + " предметов — привоз снова даровой");
            }

            // А с монетой — привозит, и монета убывает.
            Warehouse.of(world, village).add(new ItemStack(ModItems.COIN, 32));
            int withPurse = Warehouse.of(world, village).totalItems();
            Villages.newDay(world, manager, village);

            Warehouse rich = Warehouse.of(world, village);
            if (rich.totalItems() <= withPurse) {
                context.throwGameTestException("С монетой обоз ничего не привёз: было "
                        + withPurse + ", стало " + rich.totalItems());
            }
            if (Coins.total(rich.coins()) >= 32) {
                context.throwGameTestException("Обоз привёз бесплатно: монеты осталось "
                        + Coins.total(rich.coins()) + " из 32");
            }

            // Округление цены: вверх при покупке, вниз при подсчёте, сколько
            // по карману. Наоборот — и деревня возила бы себе даром.
            TradeTable.Deal rate = new TradeTable.Deal(Items.OAK_PLANKS, 4, 1, 0);
            if (Trading.costOf(rate, 5) != 2 || Trading.costOf(rate, 4) != 1) {
                context.throwGameTestException("Цена пяти штук по «1 за 4»: "
                        + Trading.costOf(rate, 5) + ", ожидалось 2");
            }
            if (Trading.affordable(rate, 3) != 12 || Trading.affordable(rate, 0) != 0) {
                context.throwGameTestException("На три монеты по «1 за 4» доступно "
                        + Trading.affordable(rate, 3) + ", ожидалось 12");
            }
        } finally {
            Configs.override(Config.DEFAULT);
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Прилавок доезжает до экрана — вместе с причиной, почему нельзя.
     * <p>
     * Причина важнее самого прилавка. Серая кнопка без объяснения — это
     * загадка: игрок не знает, идти ему за монетой, за доверием или просто
     * прийти назавтра. Порядок причин тоже проверяется: недоверие
     * называется <b>раньше</b> безденежья, иначе игрок пойдёт искать
     * изумруды под товар, который ему всё равно не продадут.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void stallsReachTheScreenWithTheReasonWhyNot(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);

        try {
            QuestView view = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElseThrow();
            if (!view.trades() || view.stalls().isEmpty()) {
                context.throwGameTestException("Прилавок не доехал до экрана");
                return;
            }
            if (view.purse() != 0) {
                context.throwGameTestException("Кошель пустой деревни: " + view.purse());
            }

            // Пустой склад: продавать нечего, покупать не на что. А заодно
            // проверяется, что сервер находит у себя <b>каждую</b> сделку,
            // которую сам же показал: клиент присылает её назад именно так.
            for (QuestView.Stall stall : view.stalls()) {
                Trading.Side side = stall.villageSells()
                        ? Trading.Side.VILLAGE_SELLS : Trading.Side.VILLAGE_BUYS;
                TradeTable.Deal named = Trading.find(colony, side, stall.item(),
                        stall.count()).orElse(null);
                if (named == null) {
                    context.throwGameTestException("Показанная сделка не находится обратно: "
                            + Registries.ITEM.getId(stall.item()));
                    return;
                }

                QuestView.Ready expected = named.minReputation() > 0
                        ? QuestView.Ready.NO_TRUST : QuestView.Ready.VILLAGE_CANT;
                if (stall.ready() != expected) {
                    context.throwGameTestException("У пустой деревни сделка "
                            + Registries.ITEM.getId(stall.item()) + " в состоянии "
                            + stall.ready() + ", ожидалось " + expected);
                }
                if (stall.ready().reasonKey().isEmpty()) {
                    context.throwGameTestException("Отказ без объяснения: "
                            + Registries.ITEM.getId(stall.item()));
                }
            }

            // Товар на складе есть, монеты у игрока нет: теперь дело в нём.
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 12));
            QuestView stocked = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElseThrow();
            QuestView.Stall bread = stocked.stalls().stream()
                    .filter(stall -> stall.villageSells() && stall.item() == Items.BREAD)
                    .findFirst().orElseThrow();
            if (bread.ready() != QuestView.Ready.PLAYER_CANT) {
                context.throwGameTestException("Хлеб на складе, монеты нет, а причина: "
                        + bread.ready());
            }

            hands.addStack(new ItemStack(ModItems.COIN, 4));
            QuestView ready = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElseThrow();
            QuestView.Stall now = ready.stalls().stream()
                    .filter(stall -> stall.villageSells() && stall.item() == Items.BREAD)
                    .findFirst().orElseThrow();
            if (now.ready() != QuestView.Ready.YES || now.ready().reasonKey().isPresent()) {
                context.throwGameTestException("Сделка сходится, а кнопка не включена: "
                        + now.ready());
            }
            if (ready.purse() != 0) {
                context.throwGameTestException("Кошель деревни изменился без сделки: "
                        + ready.purse());
            }

            // Колокол по-прежнему заперт доверием, хотя монета уже при игроке:
            // недоверие называется раньше безденежья.
            hands.addStack(new ItemStack(ModItems.COIN, 48));
            QuestView rich = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony), Schedule.dayOf(context.getWorld().getTimeOfDay())).orElseThrow();
            QuestView.Stall bell = rich.stalls().stream()
                    .filter(stall -> stall.item() == Items.BELL)
                    .findFirst().orElseThrow();
            if (bell.ready() != QuestView.Ready.NO_TRUST) {
                context.throwGameTestException("Колокол чужаку показан доступным: "
                        + bell.ready());
            }
        } finally {
            Warehouse.of(world, colony).take(Items.BREAD, 64);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Цена зависит от доверия: другу продают дешевле, а покупают у него
     * дороже.
     * <p>
     * Решение плана, и оно важнее, чем кажется: без него доверие остаётся
     * строкой в экране и порогом у пары товаров, а с ним — тем, что видно
     * кошельком на каждой сделке. Цена в датапаке — цена <b>для друга</b>.
     * <p>
     * Проверяется вся лестница целиком, а не пара чисел: ступень, добавленную
     * когда-нибудь без цены, поймает именно этот обход.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void pricesFollowStanding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            TradeTable.Deal bell = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BELL, 1).orElse(null);
            TradeTable.Deal shelf = Trading.find(colony, Trading.Side.VILLAGE_BUYS,
                    Items.BOOKSHELF, 2).orElse(null);
            if (bell == null || shelf == null) {
                context.throwGameTestException("Стол торга норманнов не загрузился");
                return;
            }

            // Округление названо числами: чужак платит полторы цены
            // колокола, почётный житель — три четверти, и обе округлены
            // против него.
            int forStranger = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS, 0);
            int forFriend = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS,
                    Standing.FRIEND.from());
            int forHonoured = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS,
                    Standing.HONOURED.from());
            if (forStranger != 36 || forFriend != 24 || forHonoured != 18) {
                context.throwGameTestException("Колокол по цене 24 обходится в "
                        + forStranger + " чужаку, " + forFriend + " другу и "
                        + forHonoured + " почётному; ожидалось 36, 24 и 18");
            }
            if (forFriend != bell.price()) {
                context.throwGameTestException("Цена датапака — не цена для друга: "
                        + bell.price() + " против " + forFriend);
            }

            // Лестница целиком: чем больше доверия, тем дешевле покупка
            // и тем дороже продажа. Ни одна цена не ноль — бесплатный
            // товар это уже не торговля.
            int cheapest = Integer.MAX_VALUE;
            int dearest = 0;
            for (Standing standing : Standing.values()) {
                int pay = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS, standing.from());
                int get = Trading.priceFor(shelf, Trading.Side.VILLAGE_BUYS, standing.from());

                if (pay > cheapest) {
                    context.throwGameTestException("У ступени " + standing.id()
                            + " покупка дороже, чем у предыдущей: " + pay + " против " + cheapest);
                }
                if (get < dearest) {
                    context.throwGameTestException("У ступени " + standing.id()
                            + " продажа дешевле, чем у предыдущей: " + get + " против " + dearest);
                }
                if (pay < 1 || get < 1) {
                    context.throwGameTestException("Даром: ступень " + standing.id()
                            + " платит " + pay + ", получает " + get);
                }
                cheapest = pay;
                dearest = get;
            }

            // Обоз при этом торгует по цене датапака: у телеги нет доверия.
            if (Trading.rate(colony, Items.GLASS_PANE).price() != 2) {
                context.throwGameTestException("Обоз берёт за стекло не цену датапака: "
                        + Trading.rate(colony, Items.GLASS_PANE).price());
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- монета мода и кошель ---

    /**
     * Монета размениваетcя сама: заплатить пять медяков золотым можно.
     * <p>
     * Это главное в своей монете и единственное, что в ней непросто.
     * Игрок с одним золотым в кармане обязан иметь возможность купить
     * хлеб за два медяка — иначе крупная монета становится обузой,
     * а не богатством. Сдача при этом должна быть <b>ровной</b>: не
     * «примерно семьдесят шесть», а именно восемь серебряков и четыре
     * медяка.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "coins")
    public void coinsBreakIntoChangeWhenPaying(TestContext context) {
        SimpleInventory pocket = new SimpleInventory(36);
        pocket.addStack(new ItemStack(ModItems.GOLD_COIN, 1));

        if (Coins.total(pocket) != Coins.GOLD) {
            context.throwGameTestException("Золотой стоит " + Coins.total(pocket)
                    + " медяков, а должен " + Coins.GOLD);
            return;
        }
        if (Coins.has(pocket, Coins.GOLD + 1)) {
            context.throwGameTestException("На один золотой хватает больше, чем на золотой");
        }

        List<ItemStack> change = Coins.pay(pocket, 5);
        if (!change.isEmpty()) {
            context.throwGameTestException("Сдача не влезла в пустой инвентарь: " + change);
        }
        if (Coins.total(pocket) != Coins.GOLD - 5) {
            context.throwGameTestException("После уплаты пяти осталось "
                    + Coins.total(pocket) + " вместо " + (Coins.GOLD - 5));
        }
        if (pocket.count(ModItems.SILVER_COIN) != 8 || pocket.count(ModItems.COIN) != 4
                || pocket.count(ModItems.GOLD_COIN) != 0) {
            context.throwGameTestException("Сдача неровная: золотых "
                    + pocket.count(ModItems.GOLD_COIN) + ", серебряков "
                    + pocket.count(ModItems.SILVER_COIN) + ", медяков "
                    + pocket.count(ModItems.COIN) + "; ожидалось 0, 8 и 4");
        }

        // И обратно: выдача идёт крупным вперёд, а не горстью медяков.
        SimpleInventory paid = new SimpleInventory(36);
        Coins.earn(paid, Coins.GOLD + Coins.SILVER * 2 + 3);
        if (paid.count(ModItems.GOLD_COIN) != 1 || paid.count(ModItems.SILVER_COIN) != 2
                || paid.count(ModItems.COIN) != 3) {
            context.throwGameTestException("Выдано мелочью: золотых "
                    + paid.count(ModItems.GOLD_COIN) + ", серебряков "
                    + paid.count(ModItems.SILVER_COIN) + ", медяков "
                    + paid.count(ModItems.COIN));
        }

        context.complete();
    }

    /**
     * Кошель — это деньги, а не сундук: торг заглядывает в него сам.
     * <p>
     * Иначе игрок, убравший монету в кошель, не смог бы ничего купить,
     * пока не вытряхнет его на землю, — и кошель из удобства стал бы
     * помехой. Проверяется покупкой при <b>пустых руках</b>: вся монета
     * лежит в кошеле.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "coins")
    public void purseCountsAsMoneyWhenTrading(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bread = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BREAD, 6).orElse(null);
            if (bread == null) {
                context.throwGameTestException("Стол торга норманнов не загрузился");
                return;
            }

            // setStack, а не addStack: SimpleInventory кладёт КОПИЮ стопки,
            // и тест держал бы в руках не тот кошель, который списывают.
            ItemStack purse = new ItemStack(ModItems.PURSE);
            PurseItem.setValue(purse, Coins.SILVER);
            hands.setStack(0, purse);
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 12));

            if (Coins.total(hands) != Coins.SILVER) {
                context.throwGameTestException("Монета в кошеле не считается: "
                        + Coins.total(hands) + " вместо " + Coins.SILVER);
            }
            if (Coins.loose(hands) != 0) {
                context.throwGameTestException("В руках нашлась россыпь, которой нет");
            }

            int price = Trading.priceFor(bread, Trading.Side.VILLAGE_SELLS, 0);
            Trading.Outcome bought = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bread,
                    spilled::add);

            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Покупка из кошеля отказана: " + bought);
                return;
            }
            if (PurseItem.valueOf(purse) != Coins.SILVER - price) {
                context.throwGameTestException("Из кошеля списано неверно: осталось "
                        + PurseItem.valueOf(purse) + " вместо " + (Coins.SILVER - price));
            }
            if (Coins.loose(hands) != 0) {
                context.throwGameTestException("Плата развалилась на россыпь в руках: "
                        + Coins.loose(hands));
            }
            if (hands.count(Items.BREAD) != bread.count()) {
                context.throwGameTestException("Хлеба получено " + hands.count(Items.BREAD));
            }
            if (!spilled.isEmpty()) {
                context.throwGameTestException("Что-то просыпалось: " + spilled);
            }
        } finally {
            Coins.pay(Warehouse.of(world, colony).coins(),
                    Coins.total(Warehouse.of(world, colony).coins()));
            Warehouse.of(world, colony).take(Items.BREAD, 64);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Кошель наполняется и вытряхивается, и больше своего не берёт.
     * <p>
     * Предел нужен не для баланса: кошель без предела — это банк
     * в кармане, и он обесценил бы сундук. Проверяется и он, и то, что
     * вытряхнутое возвращается <b>крупной монетой</b>, а не горстью.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "coins")
    public void purseStowsAndShakesOut(TestContext context) {
        SimpleInventory pocket = new SimpleInventory(36);
        // setStack, а не addStack: SimpleInventory кладёт копию стопки.
        ItemStack purse = new ItemStack(ModItems.PURSE);
        pocket.setStack(0, purse);
        pocket.addStack(new ItemStack(ModItems.COIN, 12));

        int stowed = Coins.fillPurse(pocket, purse);
        if (stowed != 12 || PurseItem.valueOf(purse) != 12 || Coins.loose(pocket) != 0) {
            context.throwGameTestException("Убрано " + stowed + ", в кошеле "
                    + PurseItem.valueOf(purse) + ", в руках " + Coins.loose(pocket)
                    + "; ожидалось 12, 12 и 0");
        }

        int taken = Coins.emptyPurse(pocket, purse);
        if (taken != 12 || PurseItem.valueOf(purse) != 0 || Coins.loose(pocket) != 12) {
            context.throwGameTestException("Вынуто " + taken + ", в кошеле "
                    + PurseItem.valueOf(purse) + ", в руках " + Coins.loose(pocket));
        }
        if (pocket.count(ModItems.SILVER_COIN) != 1 || pocket.count(ModItems.COIN) != 3) {
            context.throwGameTestException("Вытряхнуто мелочью: серебряков "
                    + pocket.count(ModItems.SILVER_COIN) + ", медяков "
                    + pocket.count(ModItems.COIN) + "; ожидались 1 и 3");
        }

        // Предел: больше своего кошель не возьмёт, а остаток вернёт.
        int over = PurseItem.put(purse, PurseItem.CAPACITY + 100);
        if (PurseItem.valueOf(purse) != PurseItem.CAPACITY || over != 100) {
            context.throwGameTestException("Предел кошеля не держит: внутри "
                    + PurseItem.valueOf(purse) + " при пределе " + PurseItem.CAPACITY
                    + ", отказано " + over);
        }

        context.complete();
    }

    // --- фаза 0.2: обозы ---

    /**
     * Обоз приходит к колонии игрока и привозит настоящий товар деревни.
     * <p>
     * Исполнение обещания из плана: «привоз станет настоящим обозом,
     * который можно перехватить». Заодно это <b>единственная торговля
     * колонии игрока</b>: своего старейшины у неё нет, и до сих пор
     * продать ей было некому.
     * <p>
     * Проверяется главное: товар обоз берёт <b>со склада деревни</b>,
     * а не из воздуха. Иначе торговля печатала бы вещи, и деревня стала
     * бы бездонным сундуком с ногами.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "caravan")
    public void caravanBringsTheVillageGoodsNotThinAir(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(20, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        try {
            for (int x = -2; x <= 26; x++) {
                for (int z = -4; z <= 8; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
            world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
            manager.add(village);

            Settlement colony = colonyWithBuilder(world, manager, colonyAt);

            // У деревни есть чем торговать: хлеб и монета.
            Warehouse store = Warehouse.of(world, village);
            store.add(new ItemStack(Items.BREAD, 40));
            Coins.earn(store.coins(), 64);

            int breadBefore = Warehouse.of(world, village).count(Items.BREAD);
            int coinBefore = Coins.total(Warehouse.of(world, village).coins());

            // Обоз ходит не каждый день, и у каждой деревни свой день:
            // перебираем дни числом, потому что шесть вызовов в одном тике
            // для мода — один и тот же день.
            Caravan guest = null;
            for (long day = 0; day < 6 && guest == null; day++) {
                Caravans.sendIfDue(world, manager, village, day);
                guest = manager.byId(colony.id()).orElseThrow().visitors()
                        .stream().findFirst().orElse(null);
            }

            if (guest == null) {
                context.throwGameTestException("Деревня с хлебом и монетой не послала обоз "
                        + "ни за шесть дней");
                return;
            }

            if (guest.cargo().isEmpty()) {
                context.throwGameTestException("Обоз пришёл пустым");
            }
            int breadAfter = Warehouse.of(world, village).count(Items.BREAD);
            int coinAfter = Coins.total(Warehouse.of(world, village).coins());
            if (breadAfter >= breadBefore || coinAfter >= coinBefore) {
                context.throwGameTestException("Обоз гружён из воздуха: хлеба на складе было "
                        + breadBefore + ", стало " + breadAfter + "; монеты было " + coinBefore
                        + ", стало " + coinAfter);
            }
            if (guest.purse() <= 0) {
                context.throwGameTestException("Обоз без монеты: покупать ему нечем");
            }
            if (!guest.home().equals(village.id())) {
                context.throwGameTestException("Обоз не помнит, кто его послал");
            }
        } finally {
            manager.all().stream().map(Settlement::id).toList().forEach(manager::remove);
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * У обоза покупают из его телеги, а не со склада деревни.
     * <p>
     * Это то, ради чего обоз и нужен: у него можно скупить всё, и тогда
     * торговать станет нечем до следующего раза. Проверяется, что после
     * сделки товар убыл <b>из телеги</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "caravan")
    public void caravanSellsFromItsOwnCart(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        manager.add(village);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bread = Trading.find(village, Trading.Side.VILLAGE_SELLS,
                    Items.BREAD, 6).orElse(null);
            if (bread == null) {
                context.throwGameTestException("Стол торга норманнов не загрузился");
                return;
            }

            // Телега: хлеб и ничего больше. Склад деревни при этом пуст.
            SimpleInventory cart = new SimpleInventory(27);
            cart.addStack(new ItemStack(Items.BREAD, 12));
            Coins.earn(hands, 8);

            Trading.Outcome bought = Trading.trade(village, player, hands,
                    Warehouse.over(villageAt, cart), Trading.Side.VILLAGE_SELLS, bread,
                    spilled::add);

            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Покупка у обоза отказана: " + bought);
                return;
            }
            if (cart.count(Items.BREAD) != 6) {
                context.throwGameTestException("Из телеги убыло не то: хлеба осталось "
                        + cart.count(Items.BREAD) + " из 12, ожидалось 6");
            }
            if (hands.count(Items.BREAD) != 6) {
                context.throwGameTestException("Игрок не получил хлеба: "
                        + hands.count(Items.BREAD));
            }
            if (Coins.total(cart) <= 0) {
                context.throwGameTestException("Монета за хлеб не легла в телегу");
            }
            if (!spilled.isEmpty()) {
                context.throwGameTestException("Сдача просыпалась: " + spilled);
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Убитый торговец рассыпает товар, и деревня это помнит.
     * <p>
     * Это «перехватить» из плана. Грабёж возможен и наказуем: товар
     * достаётся грабителю, но доверие пославшей деревни падает — и её
     * старейшина перестанет и говорить, и торговать.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "caravan")
    public void robbingTheCaravanCostsTrust(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos standsAt = context.getAbsolutePos(new BlockPos(10, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", standsAt);
        // Сосед по народу: об ограблении обязаны услышать и свои.
        Settlement kin = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан",
                context.getAbsolutePos(new BlockPos(13, 2, 2)));
        manager.add(village);
        manager.add(colony);
        manager.add(kin);
        UUID thief = UUID.randomUUID();

        try {
            for (int x = 0; x <= 14; x++) {
                for (int z = 0; z <= 4; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            ItemTally cart = new ItemTally();
            cart.add(Registries.ITEM.getId(Items.BREAD), 8);
            Caravan guest = new Caravan(UUID.randomUUID(), village.id(), NORMAN, standsAt,
                    cart, 27, 999);
            manager.update(colony.id(), state -> state.welcome(guest));

            CitizenEntity merchant = CitizenSpawner.spawnPuppet(world, standsAt);
            if (merchant == null) {
                context.throwGameTestException("Торговец не появился");
                return;
            }
            merchant.linkCaravan(colony.id(), guest.id());

            int trustBefore = village.reputationOf(thief);
            Caravans.robbed(world, merchant, thief);

            if (!manager.byId(colony.id()).orElseThrow().visitors().isEmpty()) {
                context.throwGameTestException("Разграбленный обоз всё ещё гостит");
            }
            if (village.reputationOf(thief) >= trustBefore) {
                context.throwGameTestException("Грабёж не стоил доверия: было " + trustBefore
                        + ", стало " + village.reputationOf(thief));
            }

            boolean loot = !world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(standsAt).expand(4), any -> true).isEmpty();
            if (!loot) {
                context.throwGameTestException("Товар не рассыпался: грабить нечего");
            }

            // Грабёж — поступок, а поступки слышат не только там, где
            // случились: свои пославшей деревни обязаны обидеться тоже.
            if (kin.reputationOf(thief) >= 0) {
                context.throwGameTestException("Свои ограбленной деревни не услышали: у соседа "
                        + kin.reputationOf(thief));
            }
        } finally {
            world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                            new net.minecraft.util.math.Box(standsAt).expand(6), any -> true)
                    .forEach(net.minecraft.entity.ItemEntity::discard);
            world.getEntitiesByClass(CitizenEntity.class,
                            new net.minecraft.util.math.Box(standsAt).expand(6), any -> true)
                    .forEach(CitizenEntity::discard);
            manager.remove(village.id());
            manager.remove(colony.id());
            manager.remove(kin.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
        }

        context.complete();
    }

}
