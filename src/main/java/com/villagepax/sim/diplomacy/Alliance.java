package com.villagepax.sim.diplomacy;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.trade.Coins;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Союз: деревня встаёт за игрока с оружием.
 * <p>
 * Лестница ступеней обещала это с первой недели — «город: союзы и право
 * требовать дань», — и обещание висело пустым: дипломатия умела ссориться
 * ({@code Raids}) и мириться ({@code Peace}), но не дружить <b>по делу</b>.
 * Доверие росло, а менялись от него только цены на прилавке.
 * <p>
 * <b>Союз — это мечи, а не строка в пульте.</b> Пришёл набег — союзная
 * деревня шлёт своих бойцов, и они дерутся за колонию. Всё остальное,
 * что можно было дать за союз (скидка, подарки, слова), у мода уже есть
 * в лестнице доверия; новое здесь одно: за тебя вступаются.
 * <p>
 * Три условия, и каждое — про то, что союз заключают <b>равные</b>:
 * <ul>
 *   <li><b>дружба</b>: деревня зовёт в союзники того, кого знает и кому
 *       верит, а не первого встречного с монетой;</li>
 *   <li><b>город</b>: у хутора нечего защищать и нечем ответить взаимностью —
 *       это та самая ступень, которую лестница и обещала;</li>
 *   <li><b>монета</b>: союз скрепляют даром, и дар этот не мелкий.
 *       Бесплатный союз со всеми деревнями разом — не дипломатия,
 *       а список галочек.</li>
 * </ul>
 * <p>
 * И держится он <b>дружбой</b>, а не записью: упадёт доверие ниже дружбы —
 * союзники перестанут приходить, хотя запись цела. Расторгать его отдельной
 * кнопкой поэтому не нужно: он расторгается делами, как и завоёвывался.
 */
public final class Alliance {

    /**
     * Дар, которым скрепляют союз, — золотой.
     * <p>
     * Дороже любого откупа: откуп покупает тишину на десять дней, а союз —
     * людей, которые придут умирать за чужую колонию. Цена должна быть
     * решением, а не мелочью в кармане.
     */
    public static final int PRICE = Coins.GOLD;

    /** С какой ступени поселения игроку вообще есть что предложить соседу. */
    public static final SettlementLevel NEEDS = SettlementLevel.TOWN;

    /** Сколько бойцов шлёт союзная деревня на один набег. */
    public static final int FIGHTERS = 2;

    private Alliance() {
    }

    /**
     * Что выйдет, если предложить союз, — до того, как предложил.
     * <p>
     * Тот же приём, что у подарка и откупа: приговор считает сервер и
     * показывает заранее, а не отказывает молчащей кнопкой.
     */
    public static Verdict judge(Settlement village, Settlement colony, UUID player,
                                Inventory carried) {
        if (!village.owner().isAutonomous()) {
            return Verdict.NOT_A_NEIGHBOUR;
        }
        if (village.isAllyOf(player)) {
            return Verdict.ALREADY;
        }
        if (village.reputationOf(player) < Standing.FRIEND.from()) {
            return Verdict.NOT_FRIENDS;
        }
        if (colony == null || colony.level().ordinal() < NEEDS.ordinal()) {
            return Verdict.NO_TOWN;
        }
        if (!Coins.has(carried, PRICE)) {
            return Verdict.NO_COIN;
        }
        return Verdict.YES;
    }

    /**
     * Заключить союз.
     * <p>
     * Дар уходит в кошель деревни — тот же сундук, из которого она торгует.
     * Доверия он <b>не прибавляет</b>: союз не покупается, а скрепляется.
     * Деревня, которой игрок не друг, не станет им за золото, — это то же
     * правило, по которому откуп не мирит.
     */
    public static Outcome forge(ServerWorld world, SettlementManager manager, Settlement village,
                                Settlement colony, UUID player, Inventory carried, long today,
                                Consumer<ItemStack> spill) {
        Verdict verdict = judge(village, colony, player, carried);
        if (verdict != Verdict.YES) {
            return new Outcome(verdict, PRICE);
        }

        Inventory purse = Warehouse.of(world, village).coins();
        if (!Coins.room(purse, PRICE)) {
            // Деревне некуда положить: отказать честнее, чем взять дар
            // и рассыпать его по земле у ног старейшины.
            return new Outcome(Verdict.NO_ROOM, PRICE);
        }

        Coins.pay(carried, PRICE).forEach(spill);
        Coins.earn(purse, PRICE).forEach(spill);
        manager.update(village.id(), state -> state.makeAlly(player, today));

        return new Outcome(Verdict.YES, PRICE);
    }

    /**
     * Чем кончился разговор о союзе.
     *
     * @param verdict приняли ли дар, и если нет — почему
     * @param price   сколько просили
     */
    public record Outcome(Verdict verdict, int price) {

        public boolean forged() {
            return verdict == Verdict.YES;
        }
    }

    /**
     * Приговор союзу.
     * <p>
     * Причина названа вслух по той же причине, что и у торга: «кнопка
     * серая» игроку ничего не объясняет, а «сперва город» — объясняет всё
     * и вдобавок называет цель.
     */
    public enum Verdict implements Named {

        /** Согласны. */
        YES("yes"),

        /** Это своя колония: союз с самим собой не заключают. */
        NOT_A_NEIGHBOUR("not_a_neighbour"),

        /** Союз уже есть. */
        ALREADY("already"),

        /** Деревня ещё не считает игрока другом. */
        NOT_FRIENDS("not_friends"),

        /** У игрока нет города: предложить нечего. */
        NO_TOWN("no_town"),

        /** Нет золотого при себе. */
        NO_COIN("no_coin"),

        /** Деревне некуда положить дар. */
        NO_ROOM("no_room");

        public static final Codec<Verdict> CODEC = EnumCodecs.of(values(), "приговор союзу");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** Ключ объяснения. Согласию объяснять нечего. */
        public Optional<String> reasonKey() {
            return this == YES ? Optional.empty() : Optional.of("villagepax.pact.reason." + id);
        }
    }
}
