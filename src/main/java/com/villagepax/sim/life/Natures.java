package com.villagepax.sim.life;

import com.villagepax.core.faith.God;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.faith.Faith;
import com.villagepax.sim.work.GuardJob;
import com.villagepax.sim.work.Needs;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Что характер меняет в жизни колонии.
 * <p>
 * <b>Характер не хранится.</b> У кодека жителя ровно шестнадцать полей,
 * и все шестнадцать заняты — шестнадцатое только что занял
 * {@link Citizen.Life}. Но теснота здесь не главный довод: характер даётся
 * от рождения и не меняется, а значит его не нужно <i>помнить</i> — его
 * нужно каждый раз получать один и тот же. Опознаватель жителя для этого
 * годится целиком: он у жителя уже есть, неизменен и переживает всё —
 * сохранение, перезаход, обновление мода.
 * <p>
 * Цена решения названа прямо: характер нельзя ни сменить, ни задать
 * датапаком. Первое — по замыслу (это характер, а не настроение); второе —
 * потеря, и если народам понадобятся свои склонности, вернуться придётся
 * именно сюда.
 */
public final class Natures {

    /**
     * Во сколько раз ленивый терпит дольше, а честолюбивый меньше.
     * <p>
     * Множителем, а не своими числами в настройках: сроки ухода уже
     * настраиваются, и второй источник правды разошёлся бы с первым
     * в тот день, когда игрок поменяет настройку.
     */
    private static final int PATIENCE = 2;

    private Natures() {
    }

    /** Кто этот житель по характеру. */
    public static Nature of(Citizen citizen) {
        return of(citizen.id());
    }

    /**
     * Характер по опознавателю.
     * <p>
     * Смешивание пишется своё, а не берётся у {@code UUID.hashCode()}:
     * чужой метод — чужое обещание, а от этого числа зависит, кем житель
     * окажется <b>после обновления Java</b>. Здесь оно зависит только
     * от нас.
     */
    public static Nature of(UUID id) {
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        int at = (int) Math.floorMod(bits ^ (bits >>> 32), Nature.values().length);
        return Nature.values()[at];
    }

    /**
     * Работает ли житель вполсилы по характеру или по годам.
     * <p>
     * Обе причины сведены сюда нарочно. В моде уже двое тянут вполсилы —
     * недовольный и старик, — и замедление у них <b>одно</b>: два разных
     * однажды сложились бы, и недовольный старик встал бы на месте.
     * Ленивый входит в то же самое замедление, честолюбивый выходит
     * из возрастной его половины. Ни одного нового множителя скорости
     * не заведено.
     * <p>
     * Честолюбивый старик работает в полную силу не из вежливости
     * к характеру: старость — единственное, что в этом моде отнимает силы
     * и не лечится ничем. Дать ей исключение — значит дать игроку повод
     * посмотреть, кто перед ним, прежде чем списывать старика со счетов.
     */
    public static boolean worksSlowly(Citizen citizen) {
        Nature nature = of(citizen);
        if (nature == Nature.LAZY) {
            return true;
        }
        return Ages.worksSlowly(citizen) && nature != Nature.AMBITIOUS;
    }

    /** Через сколько дней недовольства этот житель уходит навсегда. */
    public static int leaveAfterDays(Citizen citizen) {
        return scaled(citizen, Needs.leaveAfterDays());
    }

    /** На какой день недовольства он жалуется. */
    public static int warnAfterDays(Citizen citizen) {
        return scaled(citizen, Needs.warnAfterDays());
    }

    private static int scaled(Citizen citizen, int days) {
        return switch (of(citizen)) {
            case LAZY -> days * PATIENCE;
            case AMBITIOUS -> Math.max(1, days / PATIENCE);
            default -> days;
        };
    }

    /**
     * Возьмётся ли этот житель за это ремесло.
     * <p>
     * Правило одно на все способы дать работу — на кнопку в пульте,
     * на раздачу пришедшему и на выросшего ребёнка, — потому что три
     * ответа на один вопрос рано или поздно разойдутся. Так же устроены
     * запертые ремёсла и возраст.
     * <p>
     * Спрашивается <b>логика</b> ремесла, а не его имя: народ, добавивший
     * лучника на ту же {@code guard}, получит правило даром и ничего
     * о нём не узнает. Имя ремесла принадлежит датапаку, логика — моду.
     */
    public static boolean refuses(Citizen citizen, Identifier profession) {
        if (of(citizen) != Nature.COWARD) {
            return false;
        }
        return ProfessionManager.get(profession)
                .filter(craft -> GuardJob.LOGIC.equals(craft.job()))
                .isPresent();
    }

    /** Труса видно телу: он бежит от налётчика раньше прочих. */
    public static boolean isCoward(Citizen citizen) {
        return of(citizen) == Nature.COWARD;
    }

    /**
     * Суточный ход характеров: набожные относят на алтарь по вещи.
     * <p>
     * <b>Зачем.</b> Храм стоит дорого, благосклонность копится медленно,
     * и всё это время игрок должен носить жертвы руками. Набожный житель —
     * единственный способ, которым вера растёт сама, и он же — причина
     * держать храм в колонии, где молиться, в общем-то, некому.
     * <p>
     * <b>Несёт то, без чего колония обойдётся</b> — самое дешёвое из того,
     * что боги берут. Иначе набожный вынес бы на алтарь эль и резной
     * камень, и игрок узнал бы о характере по пропаже, а не по вере.
     * <p>
     * <b>Дневной черёд бога он не занимает.</b> Жертва игрока — одна
     * в сутки на бога, и это несущее правило веры; но суточный ход
     * случается на рассвете, то есть <b>всегда раньше игрока</b>, и,
     * заняв черёд, житель молча отнимал бы у него ход. Правило «одна
     * в день» заведено против сундука пшеницы, высыпанного разом, а не
     * против жителя, несущего камень.
     * <p>
     * <b>Тоски без храма нет.</b> Соблазн был отнимать у набожного
     * довольство в колонии без алтаря. Считать легко, увидеть нельзя:
     * сытый житель прибавляет довольство каждый день, и любая посильная
     * тоска утонула бы в этой прибавке, ни разу не доведя никого
     * до ухода. Правило, которого не видно в игре, в таблице характеров
     * было бы враньём.
     */
    public static void newDay(ServerWorld world, Settlement settlement) {
        if (!Faith.hasTemple(settlement)) {
            return;
        }
        Warehouse warehouse = Warehouse.of(world, settlement);
        for (Citizen citizen : settlement.citizens()) {
            if (of(citizen) != Nature.PIOUS || Ages.isChild(citizen)) {
                continue;
            }
            offerCheapest(settlement, warehouse);
        }
    }

    /**
     * Одна жертва: самое дешёвое из того, что здешние боги берут.
     * <p>
     * Ребёнок сюда не доходит: он ещё не работает и не ходит по делам,
     * и посылать его в храм с чужим добром значило бы завести ему
     * занятие, которого у детей в этом моде нет.
     */
    private static void offerCheapest(Settlement settlement, Warehouse warehouse) {
        Item cheapest = null;
        int worth = Integer.MAX_VALUE;

        for (Map.Entry<Identifier, Integer> stock : warehouse.tally().contents().entrySet()) {
            if (stock.getValue() <= 0) {
                continue;
            }
            Item item = Registries.ITEM.get(stock.getKey());
            God god = Gods.whoTakes(settlement.culture(), item)
                    .flatMap(Gods::get).orElse(null);
            if (god == null) {
                continue;
            }
            int price = god.worthOf(item);
            // Строго дешевле, а при равной цене — кто первый по
            // опознавателю: перебор по набору не обещает порядка, и без
            // этого набожный носил бы то одно, то другое от перезахода
            // к перезаходу.
            if (price < worth || (price == worth && cheapest != null
                    && Registries.ITEM.getId(item).compareTo(Registries.ITEM.getId(cheapest)) < 0)) {
                cheapest = item;
                worth = price;
            }
        }

        if (cheapest == null || !warehouse.take(cheapest, 1)) {
            return;
        }
        int favour = worth;
        Gods.whoTakes(settlement.culture(), cheapest)
                .ifPresent(id -> settlement.addFavour(id, favour));
    }
}
