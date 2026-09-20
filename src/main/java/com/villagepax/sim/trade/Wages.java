package com.villagepax.sim.trade;

import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.work.Workplaces;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Жалование, налог и то, куда девается монета.
 * <p>
 * Последний долг прошлых фаз. Решение заказчика записано дословно:
 * «полный экономический контур в колонии: казна платит жителям жалование,
 * игрок задаёт ставку налога (высокая ставка бьёт по счастью). Деньги ходят
 * по кругу: жалование → житель тратит на рынке → покупает продукцию своей же
 * колонии → налог возвращается в казну. Если товара нет своего, житель
 * покупает у каравана и монета утекает — так самодостаточность становится
 * экономической целью, а рынок нужным зданием».
 *
 * <h2>Круг</h2>
 * Каждое утро казна платит каждому работнику по два медяка. Дальше судьба
 * этих денег решается одним вопросом: <b>есть ли в колонии у кого купить</b>.
 * <ul>
 *   <li>Есть купец за прилавком — житель тратит дома, и колония берёт
 *       с покупки свою долю: она возвращается в казну.
 *   <li>Нет — монета уходит с первым же обозом, и не возвращается ничего.
 * </ul>
 * Отсюда и весь смысл рынка: он не приносит дохода сам по себе, он
 * <b>не даёт деньгам утекать</b>. Колония без рынка кормит чужих купцов.
 *
 * <h2>Ставка</h2>
 * Ноль — жители счастливы, казна пуста. Сто — казна не теряет ни медяка,
 * а жители работают даром и это знают. Между ними выбор, ради которого
 * ставка и заведена: <b>монета против довольства</b>, и оба конца плохи.
 *
 * <h2>Почему не с хутора</h2>
 * Денежное обращение начинается со ступени <b>деревни</b>. У хутора
 * из шести человек нет ни монеты, ни рынка, ни соседей, с кем торговать, —
 * платить жалование ему было бы нечем, и первая же неделя игры кончалась бы
 * бунтом за то, чего игрок не мог предотвратить. До деревни колония живёт
 * натуральным хозяйством, и это честно: так живут все, у кого нет денег.
 */
public final class Wages {

    /**
     * Сколько стоит день работника.
     * <p>
     * Два медяка — это цена того, что в колонии живут люди, а не работают
     * механизмы. Деревня из четырнадцати даёт счёт под тридцать медяков
     * в день; серебро — девять медяков, и значит неделя стоит около
     * двадцати серебром. Столько приносит один хороший обоз: контур сходится
     * ровно тогда, когда колония торгует.
     */
    public static final int WAGE = Coins.COPPER * 2;

    /** С какой ступени в колонии появляются деньги. */
    public static final SettlementLevel NEEDS = SettlementLevel.VILLAGE;

    /**
     * Насколько круто ставка бьёт по довольству.
     * <p>
     * Четверть ставки — одно очко в день: половина стоит двух, полная
     * четырёх. Сравнивать это надо с пятью очками, которые даёт сытость:
     * при половине жители ещё прибавляют, при полной — держатся на месте
     * и живут на одной еде. Ровно так «высокая ставка бьёт по счастью»
     * и должна ощущаться: не смертельно, но заметно каждый день.
     */
    public static final int RATE_PER_POINT = 25;

    /**
     * Во что обходится невыплата: столько довольства теряет необслуженный.
     * <p>
     * Двенадцать — это <b>больше самого сытого и уютного дня</b>: пять
     * за еду, три за уют, три за покой от богов. Меньше — и богатая
     * колония не заметила бы пустой казны вовсе: прибавка перекрыла бы
     * убыль, и правило существовало бы только в документе.
     * <p>
     * Счётчик голода при этом <b>не трогается</b>. Он считает дни без еды
     * и по нему житель уходит; невыплата — другая беда, и мешать их
     * значило бы уморить голодом того, кому просто не заплатили.
     * Последствие у неё своё и видимое: работа вполсилы.
     */
    public static final int UNPAID_HIT = 12;

    private Wages() {
    }

    /** Итог суточного расчёта — числами, чтобы его можно было проверить. */
    public record Payroll(int owed, int paid, int returned, int unpaid) {

        public static final Payroll NONE = new Payroll(0, 0, 0, 0);

        /** Хватило ли казны на всех. */
        public boolean allPaid() {
            return unpaid == 0;
        }
    }

    /**
     * Суточный расчёт. Зовётся раз в сутки и только под присмотром:
     * монета переезжает из сундука в сундук, а сундуки бывают только
     * в загруженных чанках.
     */
    public static Payroll newDay(ServerWorld world, SettlementManager manager,
                                 Settlement colony) {
        if (colony.owner().isAutonomous()) {
            // Деревня народа считает свои деньги сама (см. Villages):
            // у неё нет ни хозяина, ни ставки, ни пульта, в котором её
            // задать. Жалование — это разговор колонии с игроком.
            return Payroll.NONE;
        }
        if (colony.level().ordinal() < NEEDS.ordinal()) {
            return Payroll.NONE;
        }

        List<Citizen> hired = earners(colony);
        if (hired.isEmpty()) {
            return Payroll.NONE;
        }

        Warehouse warehouse = Warehouse.of(world, colony);
        Inventory purse = warehouse.coins();

        int owed = WAGE * hired.size();
        int paid = 0;
        int unpaid = 0;
        for (Citizen worker : hired) {
            if (Coins.has(purse, WAGE)) {
                Coins.pay(purse, WAGE).forEach(change ->
                        warehouse.addOrScatter(world, colony.center(), change));
                paid += WAGE;
            } else {
                // Не заплатили — и это не мелочь: работник помнит.
                worker.setHappiness(worker.happiness() - UNPAID_HIT);
                unpaid++;
            }
        }

        int returned = spendAtHome(world, colony, warehouse, paid);
        if (unpaid > 0) {
            tell(world, colony, "villagepax.wages.unpaid",
                    Text.literal(String.valueOf(unpaid)), Formatting.RED);
        }
        return new Payroll(owed, paid, returned, unpaid);
    }

    /**
     * Кому платят.
     * <p>
     * Тому, у кого есть ремесло и годы. Ребёнок не работает — значит
     * и не получает; старик работает вполсилы, но получает полностью,
     * и это решение: колония, которая платит старику меньше, — это
     * колония, из которой старики уходят, а вместе с ними и внуки.
     */
    public static List<Citizen> earners(Settlement colony) {
        List<Citizen> found = new ArrayList<>();
        for (Citizen citizen : colony.citizens()) {
            if (citizen.profession().isPresent() && !Ages.isChild(citizen)) {
                found.add(citizen);
            }
        }
        return found;
    }

    /**
     * Житель тратит заработанное — и часть возвращается налогом.
     * <p>
     * Только если тратить есть у кого. Прилавок без купца не торгует,
     * и считать его рынком значило бы обещать игроку круг, которого нет:
     * он построил бы ларёк, монета продолжала бы утекать, и понять почему
     * было бы неоткуда.
     */
    private static int spendAtHome(ServerWorld world, Settlement colony, Warehouse warehouse,
                                   int paid) {
        if (paid <= 0 || !hasMarket(colony)) {
            return 0;
        }
        int back = paid * colony.taxRate() / 100;
        if (back <= 0) {
            return 0;
        }
        Coins.earn(warehouse.coins(), back).forEach(left ->
                warehouse.addOrScatter(world, colony.center(), left));
        return back;
    }

    /**
     * Есть ли в колонии у кого купить.
     * <p>
     * Торгует <b>человек</b>, а не прилавок: нужен и купец, и достроенное
     * место, где он стоит.
     */
    public static boolean hasMarket(Settlement colony) {
        for (Citizen citizen : colony.citizens()) {
            if (citizen.profession().filter(Villages.MERCHANT::equals).isEmpty()) {
                continue;
            }
            Building stall = Workplaces.of(colony, citizen).orElse(null);
            if (stall != null && stall.isOperational()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Сколько довольства в день стоит нынешняя ставка.
     * <p>
     * Считается отдельно от расчёта и вычитается в суточных нуждах, рядом
     * с прибавкой за сытость: там же, где игрок и увидит их разницу.
     */
    public static int discontentOf(Settlement colony) {
        if (colony.owner().isAutonomous() || colony.level().ordinal() < NEEDS.ordinal()) {
            return 0;
        }
        return colony.taxRate() / RATE_PER_POINT;
    }

    /** Сколько колония должна выплатить завтра — для пульта. */
    public static int billOf(Settlement colony) {
        if (colony.owner().isAutonomous() || colony.level().ordinal() < NEEDS.ordinal()) {
            return 0;
        }
        return WAGE * earners(colony).size();
    }

    /** Сколько монеты лежит в казне колонии. */
    public static int treasuryOf(ServerWorld world, Settlement colony) {
        return Coins.total(Warehouse.of(world, colony).coins());
    }

    private static void tell(ServerWorld world, Settlement colony, String key, Text arg,
                             Formatting colour) {
        UUID player = colony.owner().player().orElse(null);
        if (player == null) {
            return;
        }
        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(player);
        if (who != null) {
            who.sendMessage(Text.translatable(key, arg).formatted(colour), false);
        }
    }
}
