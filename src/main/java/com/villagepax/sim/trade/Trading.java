package com.villagepax.sim.trade;

import com.villagepax.core.Named;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.core.trade.TradeTables;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Stacks;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Warehouse;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Торг с деревней: монета, склад и «всё или ничего».
 * <p>
 * Кошель деревни — <b>изумруды на её собственном складе</b>, а не число в
 * сохранении. Так решено сознательно: у мода уже есть правило «правда лежит
 * в сундуках», и отдельный счётчик денег его нарушил бы. А заодно кошель
 * становится виден — в пульте, в сундуке ратуши, — и торговля перестаёт быть
 * разговором с воздухом: деревня платит тем, что у неё есть, и это можно
 * посмотреть.
 * <p>
 * Отсюда же следует главное ограничение, и оно же — содержание: деревня не
 * купит того, на что у неё нет монеты, и не продаст того, чего нет на складе.
 * Игрок, который снабжает деревню, богатеет; деревня, у которой скупили всё
 * зерно, не продаст зерна, пока не соберёт новое.
 * <p>
 * <b>Решение отделено от последствий</b>, как в квестах: сделка работает с
 * обычным {@link Inventory} и складом, без игрока и без сети, — поэтому её
 * можно прогнать игровым тестом целиком.
 */
public final class Trading {



    /**
     * Сколько доверия даёт одна сделка и до какой черты.
     * <p>
     * Торговлей можно стать знакомым, но не другом. Иначе чертёж ратуши —
     * награда за цепочку квестов — покупался бы пшеницей в двести приёмов,
     * и вход в мод превратился бы в перекладывание стопок. Знакомство за
     * торг честно: тебя запомнили в лицо.
     */
    private static final int TRUST_PER_TRADE = 1;
    private static final int TRUST_CAP = Standing.KNOWN.from();

    /**
     * Цена вещей, которых нет в столе торга: столько изумрудов за столько
     * штук.
     * <p>
     * Нужна не игроку, а обозу: деревне случается требовать на стройку то,
     * чего автор датапака не оценил, и без запасной цены дом стоял бы
     * недостроенным навсегда из-за забытой строчки. Игроку по этой цене
     * не торгуют — в столе торга такой сделки просто нет.
     */
    private static final int FALLBACK_PRICE = 1;
    private static final int FALLBACK_COUNT = 4;

    /** Кто в сделке отдаёт товар. */
    public enum Side {

        /** Деревня продаёт, игрок платит. */
        VILLAGE_SELLS,

        /** Деревня скупает, игрок получает монету. */
        VILLAGE_BUYS
    }

    /**
     * Чем кончилась попытка сторговаться.
     * <p>
     * Причин много, и каждая — <b>отдельная строка игроку</b>. Одно «нельзя»
     * на все случаи оставляло бы его гадать: не хватает монеты, товара,
     * места в сумке или доверия? Отсюда и {@code id}: по нему собирается
     * ключ объяснения, и забыть перевод одной из причин нельзя — их
     * пересчитывает тест.
     */
    public enum Outcome implements Named {

        /** Сделка состоялась. */
        DONE("done"),

        /** Такой сделки у народа нет: датапак перечитан, а экран старый. */
        NO_DEAL("no_deal"),

        /** Доверия не хватает: эту вещь чужаку не продают. */
        NO_TRUST("no_trust"),

        /** У деревни нет товара. */
        NO_STOCK("no_stock"),

        /** У деревни нет монеты. */
        NO_COIN("no_coin"),

        /** У игрока нет того, чем он собрался платить или торговать. */
        NO_GOODS("no_goods"),

        /** Некуда положить: полна сумка или полон склад. */
        NO_ROOM("no_room");

        private final String id;

        Outcome(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private Trading() {
    }

    /** Стол торга этого поселения, если его народ торгует. */
    public static Optional<TradeTable> tableOf(Settlement settlement) {
        return TradeTables.of(settlement.culture());
    }

    public static List<TradeTable.Deal> dealsOn(Settlement settlement, Side side) {
        return tableOf(settlement)
                .map(table -> side == Side.VILLAGE_SELLS ? table.sells() : table.buys())
                .orElse(List.of());
    }

    /**
     * Сколько монеты в кошеле деревни, в медяках.
     * <p>
     * Кошель деревни — монета на её складе. Отдельного хранилища для
     * денег у неё нет: правда лежит в сундуках.
     */
    public static int purse(Warehouse warehouse) {
        return Coins.total(warehouse.coins());
    }

    /**
     * Найти сделку в столе торга по тому, что назвал клиент.
     * <p>
     * Именно так, а не «собрать сделку из присланного»: и цена, и порог
     * доверия обязаны приходить из датапака, иначе подложенный пакет
     * назначит их сам. Клиент называет только <b>что и сколько</b>.
     * <p>
     * Цену клиент не называет намеренно, хотя и знает её: она зависит от
     * доверия, а доверие меняется от сделки к сделке. Присланная цена
     * расходилась бы с пересчитанной на первом же очке знакомства, и торг
     * отказывал бы без причины.
     */
    public static Optional<TradeTable.Deal> find(Settlement village, Side side, Item item,
                                                 int count) {
        return dealsOn(village, side).stream()
                .filter(deal -> deal.item() == item && deal.count() == count)
                .findFirst();
    }

    /**
     * Во сколько эта сделка обойдётся <b>этому</b> игроку.
     * <p>
     * Решение плана: «от отношений зависят не курсы размена, а цены —
     * друзья продают дешевле и покупают дороже». Цена в датапаке — это
     * цена <b>для друга</b>; чужак платит полторы и получает половину.
     * Так у доверия появляется смысл, который видно кошельком, а не только
     * строкой в экране.
     * <p>
     * Округление всегда <b>против игрока</b>: платит — вверх, получает —
     * вниз, но не меньше одного изумруда. Иначе дешёвый товар у почётного
     * жителя стал бы бесплатным, а бесплатный товар — это уже не торговля.
     */
    public static int priceFor(TradeTable.Deal deal, Side side, int reputation) {
        Standing standing = Standing.of(reputation);
        int percent = side == Side.VILLAGE_SELLS ? standing.buyPercent() : standing.sellPercent();
        int base = deal.price() * percent;

        int price = side == Side.VILLAGE_SELLS ? (base + 99) / 100 : base / 100;
        return Math.max(1, price);
    }

    /**
     * Сторговаться.
     * <p>
     * Порядок жёсткий: сперва <b>все</b> проверки, потом все перемещения.
     * Иначе на полном инвентаре получилась бы сделка, где монеты списаны,
     * а товара нет, — и объяснить это игроку было бы нечем.
     * <p>
     * Про место спрашивается до расчёта, поэтому проверка чуть строже
     * нужного: слот, который освободится уплаченной монетой, не считается
     * свободным. Ошибка направлена в безопасную сторону — «не влезет» вместо
     * пропавшего товара, — и совет игроку от неё не меняется: освободи руки.
     *
     * @param village  с кем торгуют: у него и народ, и доверие
     * @param player   кто торгует
     * @param carried  сумка игрока
     * @param wares    склад деревни: он же её кошель
     * @param side     кто отдаёт товар
     * @param deal     сделка из стола торга этого народа
     */
    public static Outcome trade(Settlement village, UUID player, Inventory carried,
                                Warehouse wares, Side side, TradeTable.Deal deal,
                                Consumer<ItemStack> spill) {
        if (!dealsOn(village, side).contains(deal)) {
            return Outcome.NO_DEAL;
        }
        if (!deal.open(village.reputationOf(player))) {
            return Outcome.NO_TRUST;
        }

        Item goods = deal.item();
        int count = deal.count();
        int price = priceFor(deal, side, village.reputationOf(player));

        Inventory chest = wares.coins();

        if (side == Side.VILLAGE_SELLS) {
            if (!wares.has(goods, count)) {
                return Outcome.NO_STOCK;
            }
            if (!Coins.has(carried, price)) {
                return Outcome.NO_GOODS;
            }
            if (!Stacks.room(carried, goods, count) || !Coins.room(chest, price)) {
                return Outcome.NO_ROOM;
            }

            Coins.pay(carried, price).forEach(spill);
            wares.take(goods, count);
            Stacks.insert(carried, new ItemStack(goods, count));
            Coins.earn(chest, price).forEach(spill);
        } else {
            if (carried.count(goods) < count) {
                return Outcome.NO_GOODS;
            }
            if (!Coins.has(chest, price)) {
                return Outcome.NO_COIN;
            }
            if (!Coins.room(carried, price) || !wares.room(goods, count)) {
                return Outcome.NO_ROOM;
            }

            Stacks.take(carried, goods, count);
            Coins.pay(chest, price).forEach(spill);
            Coins.earn(carried, price).forEach(spill);
            wares.add(new ItemStack(goods, count));
        }

        reward(village, player);
        return Outcome.DONE;
    }

    /** Доверие за сделку — до знакомства, не выше. */
    private static void reward(Settlement village, UUID player) {
        if (village.reputationOf(player) < TRUST_CAP) {
            village.addReputation(player, TRUST_PER_TRADE);
        }
    }

    /**
     * По какой цене деревня скупает этот предмет — для обоза.
     * <p>
     * Цена та же, по которой деревня скупает у игрока: две разные цены на
     * одну вещь игрок счёл бы обманом, и был бы прав.
     *
     * @return сколько штук за сколько изумрудов
     */
    public static TradeTable.Deal rate(Settlement village, Item item) {
        return tableOf(village)
                .flatMap(table -> table.buyRate(item))
                .orElseGet(() -> new TradeTable.Deal(item, FALLBACK_COUNT, FALLBACK_PRICE, 0));
    }

    /**
     * Сколько штук деревня может себе позволить, имея столько монеты.
     * <p>
     * Округление вниз, и это не мелочь: при цене «1 изумруд за 4 штуки» и
     * одном изумруде в кошеле привезти надо четыре штуки, а не одну по
     * четверти изумруда. Дробных изумрудов не бывает.
     */
    public static int affordable(TradeTable.Deal rate, int purse) {
        return purse / rate.price() * rate.count();
    }

    /**
     * Во сколько обойдётся столько штук по этой цене.
     * <p>
     * Округление <b>вверх</b>: пять штук по цене «1 за 4» стоят два
     * изумруда, а не один. Вниз — и деревня возила бы себе половину товара
     * бесплатно, чего никакой обоз не потерпит.
     */
    public static int costOf(TradeTable.Deal rate, int amount) {
        return Math.max(1, (amount * rate.price() + rate.count() - 1) / rate.count());
    }
}
