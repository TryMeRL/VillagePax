package com.villagepax.sim.war;

import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.trade.Coins;
import com.mojang.serialization.Codec;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Откуп: война кончается монетой.
 * <p>
 * До сих пор у набега не было <b>конца</b>. Деревня, чьё терпение вышло,
 * слала отряд каждые пять дней, и единственным выходом было медленно
 * выправлять доверие подарками — по восемь очков в сутки, под набегом,
 * идя дарить в ту самую деревню. Война, из которой нет выхода, — это
 * не наказание, а поломка: игрок перестаёт играть, а не исправляется.
 * <p>
 * <b>Перемирие — это время, и покупается оно двумя способами.</b> Кровью:
 * каждый павший боец стоит деревне дней траура ({@link Raids#fell}).
 * Или монетой: заплати за поход, которого не будет, — и отряд уйдёт.
 * Число в поселении одно на оба случая, и различать их незачем.
 * <p>
 * <b>Доверия откуп не меняет ни на очко</b>, и это главное решение здесь.
 * Деньгами покупается тишина, а не дружба: деревня берёт плату и уходит,
 * продолжая считать игрока разбойником. Зато десять дней тишины — это
 * ровно то, чего ему не хватало: время починить отношения делами.
 * Иначе кошелёк отменял бы всю дипломатию разом.
 */
public final class Peace {

    /**
     * Плата за каждого бойца, которого деревня не пошлёт, — четыре серебра.
     * <p>
     * Цена привязана к <b>размеру отряда</b>, а не к глубине обиды напрямую:
     * так игрок видит, за что платит, — за тех самых людей, что стоят у его
     * ворот. И растёт она ступенями вместе с отрядом: обидел сильнее —
     * платишь дороже.
     */
    public static final int COIN_PER_FIGHTER = Coins.SILVER * 4;

    /**
     * Сколько дней тишины стоит откуп.
     * <p>
     * Десять дней — это вдвое дольше, чем деревня остывает между набегами,
     * и этого хватает, чтобы подарками выбраться из любой обиды: восемь
     * очков в сутки против сорока, за которые терпение кончилось. Меньше —
     * и откуп был бы данью, которую платишь снова и снова.
     */
    public static final int TRUCE_DAYS = 10;

    /**
     * Сколько дней тишины стоит деревне один павший боец.
     * <p>
     * Отбитый набег в четыре меча — это больше двух недель покоя. Не потому
     * что деревня подобрела: мёртвые не ходят в походы, а новых надо
     * вырастить.
     */
    public static final int MOURNING_DAYS = 4;

    private Peace() {
    }

    /** Во сколько обойдётся мир при таком доверии. */
    public static int price(int trust) {
        return Raids.fightersFor(trust) * COIN_PER_FIGHTER;
    }

    /**
     * Что будет, если предложить деревне монету, — до того, как предложил.
     * <p>
     * Тот же приём, что у подарка: приговор считает сервер и показывает
     * заранее, а не отказывает молчащей кнопкой.
     */
    public static Verdict judge(Settlement village, UUID player, Inventory carried, long today) {
        if (!village.owner().isAutonomous()) {
            return Verdict.NOT_AT_WAR;
        }
        if (Raids.fightersFor(village.reputationOf(player)) <= 0) {
            return Verdict.NOT_AT_WAR;
        }
        if (village.atTruce(today)) {
            return Verdict.ALREADY;
        }
        if (!Coins.has(carried, price(village.reputationOf(player)))) {
            return Verdict.NO_COIN;
        }
        return Verdict.YES;
    }

    /**
     * Заплатить за мир.
     * <p>
     * Монета уходит в кошель деревни — тот же сундук, из которого она
     * торгует. Отряд, если он уже стоит у ворот, уводит {@link Raids}:
     * здесь только данные, тела — там.
     */
    public static Outcome buy(ServerWorld world, SettlementManager manager, Settlement village,
                              UUID player, Inventory carried, long today,
                              Consumer<ItemStack> spill) {
        Verdict verdict = judge(village, player, carried, today);
        if (verdict != Verdict.YES) {
            return new Outcome(verdict, 0, 0);
        }

        int price = price(village.reputationOf(player));
        Inventory purse = Warehouse.of(world, village).coins();
        if (!Coins.room(purse, price)) {
            // Деревне некуда положить: отказать честнее, чем взять монету
            // и рассыпать её по земле у ног старейшины.
            return new Outcome(Verdict.NO_ROOM, price, 0);
        }

        Coins.pay(carried, price).forEach(spill);
        Coins.earn(purse, price).forEach(spill);
        manager.update(village.id(), state -> state.restFor(today, TRUCE_DAYS));

        return new Outcome(Verdict.YES, price, TRUCE_DAYS);
    }

    /**
     * Чем кончился разговор об откупе.
     *
     * @param verdict взяли ли монету, и если нет — почему
     * @param price   сколько просили
     * @param days    сколько дней тишины куплено
     */
    public record Outcome(Verdict verdict, int price, int days) {

        public boolean bought() {
            return verdict == Verdict.YES;
        }
    }

    /**
     * Приговор откупу.
     * <p>
     * Причина названа вслух по той же причине, что и у торга: «кнопка
     * серая» игроку ничего не объясняет, а «перемирие уже идёт» —
     * объясняет всё.
     */
    public enum Verdict implements Named {

        /** Берут. */
        YES("yes"),

        /** Этот народ и так не воюет: платить не за что. */
        NOT_AT_WAR("not_at_war"),

        /** Перемирие уже идёт — второй раз не продают. */
        ALREADY("already"),

        /** У игрока нет столько монеты. */
        NO_COIN("no_coin"),

        /** Деревне некуда положить плату. */
        NO_ROOM("no_room");

        public static final Codec<Verdict> CODEC = EnumCodecs.of(values(), "приговор откупу");

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
            return this == YES ? Optional.empty() : Optional.of("villagepax.peace.reason." + id);
        }
    }
}
