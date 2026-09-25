package com.villagepax.gametest;

import net.minecraft.server.world.ServerWorld;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.QuestNet;
import net.minecraft.inventory.SimpleInventory;
import com.villagepax.sim.Standing;
import com.villagepax.sim.diplomacy.Gifts;
import com.villagepax.sim.diplomacy.Relations;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Warehouse;
import net.minecraft.nbt.NbtOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Отношения деревень и народов.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class RelationsTests extends GameTestSupport {

    // --- фаза 3: отношения ---

    /**
     * Слух о поступке идёт по всему народу — и к его соседям.
     * <p>
     * Дизайн-документ: «каждое действие меняет несколько уровней матрицы
     * сразу — подарок конкретной деревне слегка поднимает и отношение
     * всего народа». Проверяется ровно это: услугу одной деревне зачтут
     * её своим, а тем, кто на этот народ косится, она слегка не по нраву.
     * <p>
     * И проверяется <b>на трёх деревнях сразу</b>, потому что двумя это
     * правило не отличить от «доверие складывается»: нужны и свои, и чужие.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "diplomacy")
    public void fameSpreadsToKinAndCostsRivals(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement first = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(2, 2, 2)));
        Settlement kin = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан",
                context.getAbsolutePos(new BlockPos(6, 2, 2)));
        Settlement rival = Settlement.found(MAYA, Owner.AUTONOMOUS, "Ушмаль",
                context.getAbsolutePos(new BlockPos(10, 2, 2)));
        manager.add(first);
        manager.add(kin);
        manager.add(rival);

        try {
            List<Relations.Shift> shifts = Relations.deed(manager, first, player, 60);

            if (first.reputationOf(player) != 60) {
                context.throwGameTestException("Своё деревня не получила: "
                        + first.reputationOf(player) + " вместо 60");
            }
            if (kin.reputationOf(player) != 15) {
                context.throwGameTestException("Свои не услышали: у соседа по народу "
                        + kin.reputationOf(player) + " вместо четверти, то есть 15");
            }
            if (rival.reputationOf(player) != -6) {
                context.throwGameTestException("Чужие не заметили: у майя "
                        + rival.reputationOf(player) + " вместо -6");
            }

            boolean toldAboutNormans = shifts.stream()
                    .anyMatch(shift -> shift.culture().equals(NORMAN)
                            && shift.now() == Standing.KNOWN);
            if (!toldAboutNormans) {
                context.throwGameTestException("Народ сменил ступень, а игроку не сказали: "
                        + shifts);
            }
        } finally {
            manager.remove(first.id());
            manager.remove(kin.id());
            manager.remove(rival.id());
        }

        context.complete();
    }

    /**
     * Подарок принимают, если он деревне нужен, и берут ровно сколько надо.
     * <p>
     * Три правила одним тестом, потому что они об одном подарке: дарить
     * надо то, что деревня скупает; за сутки благодарят один раз; и лишнего
     * из рук не забирают. Последнее — не мелочь: молча взять восемь золотых
     * за те же восемь очков было бы надувательством.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "diplomacy")
    public void giftsMustBeSomethingTheVillageWants(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(2, 2, 2)));
        manager.add(village);

        try {
            // Своего хлеба норманнам не надо: они его сами продают.
            ItemStack bread = new ItemStack(Items.BREAD, 64);
            Gifts.Outcome refused = Gifts.give(manager, village, player, bread, 5L);
            if (refused.verdict() != Gifts.Verdict.NOT_WANTED) {
                context.throwGameTestException("Норманнский хлеб норманнам приняли как подарок: "
                        + refused.verdict());
            }
            if (bread.getCount() != 64) {
                context.throwGameTestException("Отвергнутый подарок всё равно забрали");
            }

            // А золото деревне нужно всегда — но одной монеты уже довольно.
            ItemStack gold = new ItemStack(ModItems.GOLD_COIN, 8);
            Gifts.Outcome taken = Gifts.give(manager, village, player, gold, 5L);
            if (!taken.accepted()) {
                context.throwGameTestException("Золото не приняли: " + taken.verdict());
                return;
            }
            if (taken.trust() != Gifts.MOST_PER_DAY) {
                context.throwGameTestException("За золотой дали " + taken.trust()
                        + " доверия вместо суточного предела " + Gifts.MOST_PER_DAY);
            }
            if (gold.getCount() != 7) {
                context.throwGameTestException("Из рук взяли лишнее: осталось "
                        + gold.getCount() + " золотых вместо семи");
            }
            if (village.reputationOf(player) != Gifts.MOST_PER_DAY) {
                context.throwGameTestException("Доверие не выросло: "
                        + village.reputationOf(player));
            }

            // Второй раз в тот же день — нет.
            Gifts.Outcome again = Gifts.give(manager, village, player,
                    new ItemStack(ModItems.GOLD_COIN, 8), 5L);
            if (again.verdict() != Gifts.Verdict.ALREADY_TODAY) {
                context.throwGameTestException("Второй подарок за день приняли: "
                        + again.verdict());
            }

            // А назавтра — снова да.
            Gifts.Outcome tomorrow = Gifts.give(manager, village, player,
                    new ItemStack(ModItems.GOLD_COIN, 8), 6L);
            if (!tomorrow.accepted()) {
                context.throwGameTestException("Назавтра подарок не приняли: "
                        + tomorrow.verdict());
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Народ судит об игроке по всем своим деревням, а не по одной.
     * <p>
     * Взвешенно: слово города весит больше слова хутора. И только по тем,
     * кто игрока знает: деревня, которой он в глаза не видел, мнения не
     * имеет, и подмешивать её ноль значило бы наказывать игрока за
     * существование деревень, до которых он не дошёл.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "diplomacy")
    public void peopleJudgePlayerByAllTheirVillages(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement hamlet = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Хутор",
                context.getAbsolutePos(new BlockPos(2, 2, 6)));
        Settlement town = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан",
                context.getAbsolutePos(new BlockPos(6, 2, 6)));
        Settlement stranger = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Дальняя",
                context.getAbsolutePos(new BlockPos(10, 2, 6)));
        manager.add(hamlet);
        manager.add(town);
        manager.add(stranger);

        try {
            manager.update(town.id(), state -> state.setLevel(SettlementLevel.TOWN));
            manager.update(hamlet.id(), state -> state.addReputation(player, 0));
            manager.update(town.id(), state -> state.addReputation(player, 80));

            // Хутор весит один, город — три: (0 + 240) / 4 = 60.
            int trust = Relations.trustOfPeople(manager, NORMAN, player);
            if (trust != 60) {
                context.throwGameTestException("Средний счёт народа " + trust
                        + " вместо взвешенных 60");
            }
            if (Relations.standingOfPeople(manager, NORMAN, player) != Standing.FRIEND) {
                context.throwGameTestException("Народ, где один город считает другом, "
                        + "а хутор молчит, не признал друга");
            }
            if (stranger.knows(player)) {
                context.throwGameTestException("Незнакомая деревня откуда-то знает игрока");
            }
        } finally {
            manager.remove(hamlet.id());
            manager.remove(town.id());
            manager.remove(stranger.id());
        }

        context.complete();
    }

    /**
     * Экран старейшины показывает и народ, и цену подарка в руке.
     * <p>
     * И проверяется, что снимок <b>кодируется</b>: он едет на клиент, и
     * поле, которое не кодируется, роняет разговор целиком. Однажды так
     * и вышло — с грузом обоза, — и стоило это всех модульных тестов
     * поселения.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "diplomacy")
    public void elderScreenShowsThePeopleAndTheGift(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(2, 2, 10)));
        manager.add(village);

        try {
            QuestView view = QuestNet.viewOf(manager, village, player,
                    new SimpleInventory(36), Villages.ELDER,
                    Warehouse.of(world, village), Optional.empty(),
                    new ItemStack(Items.IRON_INGOT, 64), 4L).orElseThrow();

            if (!"villagepax.culture.norman".equals(view.people().name())) {
                context.throwGameTestException("Народ деревни доехал до экрана как "
                        + view.people().name());
            }
            if (view.people().neighbours().isEmpty()) {
                context.throwGameTestException("Второй народ в мире есть, а в соседях его нет");
            }

            QuestView.Gift gift = view.gift().orElse(null);
            if (gift == null) {
                context.throwGameTestException("Железо в руке, а карточки подарка нет");
                return;
            }
            if (!gift.ready() || gift.trust() != Gifts.MOST_PER_DAY) {
                context.throwGameTestException("Стопка железа стоит " + gift.trust()
                        + " доверия и приговор " + gift.verdict());
            }

            QuestView back = QuestView.CODEC
                    .parse(NbtOps.INSTANCE, QuestView.CODEC
                            .encodeStart(NbtOps.INSTANCE, view).result().orElseThrow())
                    .result().orElse(null);
            if (back == null) {
                context.throwGameTestException("Снимок разговора не пережил кодирования");
                return;
            }
            if (!back.people().standing().equals(view.people().standing())
                    || back.gift().orElseThrow().trust() != gift.trust()) {
                context.throwGameTestException("Снимок вернулся из кодека другим");
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

}
