package com.villagepax.sim.festival;

import com.villagepax.core.Named;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Villages;
import com.villagepax.sim.work.Schedule;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Optional;

/**
 * Идёт ли у поселения праздник — одно правило на весь мод.
 * <p>
 * Спрашивают его зов на рассвете, пироги, хоровод, состязания, фейерверк,
 * прибавка к довольству и экран затейника. Семь ответов на один вопрос
 * разошлись бы при первой правке: пироги стояли бы на ярмарке без затейника,
 * а экран говорил бы, что праздника нет.
 * <p>
 * Праздник идёт, если сегодня фаза его народа, у поселения готовая ярмарка,
 * у ярмарки есть затейник и поселение не в осаде. Отказ называет причину
 * первой по порядку: сперва «не сегодня», потом «нет ярмарки», «нет
 * затейника», «набег». Игрок, которому сказали «нет затейника» в будний
 * день, пошёл бы искать затейника зря.
 * <p>
 * День — довод, а не спрос у мира: мир игровых проверок общий, и время
 * суток в нём не подвинешь, а правило обязано быть проверяемым в любой день.
 */
public final class FestivalDay {

    /** Закат: с него состязаний уже не начинают, а от сердца праздника бьёт фейерверк. */
    public static final long DUSK = 12_000L;

    /** С какого часа гуляет колония: с послеобеденной работы. */
    private static final long COLONY_FROM = 7_000L;

    /**
     * Прибавка к довольству за день праздника.
     * <p>
     * Больше сытости (+5): это лучший день месяца, и за него колония платит
     * жителем на ярмарке и половиной рабочего дня.
     */
    public static final int CHEER = 6;

    /** Почему праздника нет — или что он идёт. */
    public enum Verdict implements Named {
        ON("on"),
        NO_FESTIVAL("no_festival"),
        NOT_TODAY("not_today"),
        NO_FAIR("no_fair"),
        NO_HOST("no_host"),
        BESIEGED("besieged");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** Ключ объяснения для игрока. */
        public String reasonKey() {
            return "villagepax.festival.reason." + id;
        }
    }

    private FestivalDay() {
    }

    /** Идёт ли у поселения праздник в этот день — и если нет, то почему. */
    public static Verdict today(Settlement settlement, long day) {
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        if (festival == null) {
            return Verdict.NO_FESTIVAL;
        }
        if (!FestivalCalendar.isFestivalDay(day, festival.moonPhase())) {
            return Verdict.NOT_TODAY;
        }
        Fair fair = Fairs.of(settlement).orElse(null);
        if (fair == null) {
            return Verdict.NO_FAIR;
        }
        if (host(settlement, fair).isEmpty()) {
            return Verdict.NO_HOST;
        }
        if (settlement.siege().isPresent()) {
            return Verdict.BESIEGED;
        }
        return Verdict.ON;
    }

    public static boolean isOn(Settlement settlement, long day) {
        return today(settlement, day) == Verdict.ON;
    }

    /** Прибавка к довольству за этот день: {@link #CHEER}, если праздник в тот день шёл, иначе ноль. */
    public static int cheer(Settlement settlement, long day) {
        return isOn(settlement, day) ? CHEER : 0;
    }

    /**
     * Праздник, который ждёт округа.
     *
     * @param days через сколько дней: 0 — сегодня
     */
    public record Upcoming(Settlement settlement, Festival festival, int days) {
    }

    /**
     * Ближайший праздник округи: у поселений с ярмаркой и затейником
     * в этом радиусе — тот, до которого меньше дней, а из равных — ближний.
     * <p>
     * Осаду здесь не спрашивают: она кончится, а день праздника — нет.
     */
    public static Optional<Upcoming> upcoming(Collection<Settlement> settlements, BlockPos pos,
                                              int radius, long day) {
        Upcoming best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Settlement settlement : settlements) {
            double distance = settlement.center().getSquaredDistance(pos);
            if (distance > (double) radius * radius) {
                continue;
            }
            Festival festival = Festivals.of(settlement.culture()).orElse(null);
            Fair fair = Fairs.of(settlement).orElse(null);
            if (festival == null || fair == null || host(settlement, fair).isEmpty()) {
                continue;
            }
            int days = FestivalCalendar.daysUntil(day, festival.moonPhase());
            if (best == null || days < best.days() || days == best.days() && distance < bestDistance) {
                best = new Upcoming(settlement, festival, days);
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Затейник этой ярмарки: ремесло затейника и мастерская — она. */
    public static Optional<Citizen> host(Settlement settlement, Fair fair) {
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(Villages.ENTERTAINER::equals).isPresent()
                    && citizen.workplace().filter(fair.building().id()::equals).isPresent()) {
                return Optional.of(citizen);
            }
        }
        return Optional.empty();
    }

    /**
     * Гуляют ли в эту часть суток.
     * <p>
     * Деревня народа — весь день: игрок, пришедший в любой час, застаёт
     * праздник. Колония — с послеобеденной работы: утро и обед как обычно,
     * и половина рабочего дня — цена праздника. Сон — сон у всех.
     *
     * @param autonomous деревня народа, а не колония игрока
     */
    public static boolean revels(boolean autonomous, Schedule part) {
        if (part == Schedule.SLEEP) {
            return false;
        }
        return autonomous || part == Schedule.DAY_WORK || part == Schedule.LEISURE;
    }

    /** Можно ли в этот час начать состязание: пока гуляют и до заката. */
    public static boolean contestsOpen(boolean autonomous, long timeOfDay) {
        long time = Math.floorMod(timeOfDay, Schedule.DAY_LENGTH);
        return time < DUSK && (autonomous || time >= COLONY_FROM);
    }
}
