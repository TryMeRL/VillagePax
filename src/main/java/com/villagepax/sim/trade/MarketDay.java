package com.villagepax.sim.trade;

import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.Standing;
import net.minecraft.util.Identifier;

/**
 * Рыночный день: раз в неделю деревня народа торгует со всеми, как с друзьями.
 * <p>
 * Торг до сих пор был закрыт доверием наглухо: чужаку — горстка товара
 * по худшей цене, и пока игрок не наработал дружбу, лавка деревни была
 * для него витриной. Рыночный день — это день, когда к деревне съезжаются
 * все: в этот день открыт весь товар, который деревня продаёт друзьям,
 * и по дружеской цене. Тот, кто уже друг или выше, своё и так имеет —
 * ему рынок не меняет ничего.
 * <p>
 * Рынок — у деревни, а не у хутора: хутору съезжаться некуда. День недели
 * у каждого народа свой ({@link #weekday}), и игрок, знающий календарь,
 * объезжает рынки соседей по очереди.
 */
public final class MarketDay {

    /** Неделя рынков — семь дней, как у людей. */
    public static final int WEEK = 7;

    private MarketDay() {
    }

    /**
     * В какой день недели у народа рынок: остаток от деления дня на неделю.
     * Народы разведены по разным дням, чтобы рынок был всегда где-нибудь.
     */
    public static int weekday(Identifier culture) {
        return switch (culture.getPath()) {
            case "norman" -> 1;
            case "maya" -> 2;
            case "pony" -> 3;
            case "nord" -> 4;
            case "yamato" -> 5;
            case "dwarf" -> 6;
            case "elf" -> 0;
            default -> Math.floorMod(culture.hashCode(), WEEK);
        };
    }

    /** Рынок ли сегодня у этого поселения. */
    public static boolean isOn(Settlement settlement, long day) {
        return settlement.owner().isAutonomous()
                && settlement.level().ordinal() >= SettlementLevel.VILLAGE.ordinal()
                && Math.floorMod(day, WEEK) == weekday(settlement.culture());
    }

    /** Через сколько дней ближайший рынок народа: 0 — сегодня. */
    public static int daysUntil(Identifier culture, long day) {
        return Math.floorMod(weekday(culture) - day, WEEK);
    }

    /**
     * С каким доверием торговать в этот день: в рыночный — не ниже дружбы.
     * Обида при этом не прощается: тот, кому деревня враждебна, на рынок
     * не пущен, — рынок для всех, кто пришёл с миром.
     */
    public static int tradeTrust(Settlement settlement, int reputation, long day) {
        if (!isOn(settlement, day) || reputation < 0) {
            return reputation;
        }
        return Math.max(reputation, Standing.FRIEND.from());
    }
}
