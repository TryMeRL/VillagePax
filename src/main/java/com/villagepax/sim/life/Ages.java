package com.villagepax.sim.life;

import com.villagepax.core.Named;
import com.villagepax.core.config.Configs;
import com.villagepax.sim.Citizen;

/**
 * Сколько жителю лет и что это меняет.
 * <p>
 * Возраст меряется <b>прожитыми днями</b>, и «прожитыми» здесь не оборот
 * речи: счётчик идёт только в те сутки, которые колония прожила на глазах
 * у игрока. Иначе ушедший на сто дней в шахту возвращался бы к кладбищу —
 * ровно так мод однажды уже поступил с голодом.
 * Днями мыслит игрок («не кормил две ночи»), днями считает голод, приток
 * и дань, и заводить рядом вторую единицу времени значило бы объяснять
 * её в интерфейсе. Год в этом моде — просто большое число дней, и никто
 * его не считает.
 * <p>
 * <b>Возраста может не быть вовсе.</b> Все, кто жил в мире до этой правки,
 * помечены {@link Citizen.Life#UNAGED}.
 * Считать их младенцами нельзя — они работают; стариками тем более.
 * Поэтому у них нет возраста, и старость их не берёт: это честнее, чем
 * выдумать им день рождения задним числом и уморить тех, кто строил
 * колонию с первого дня.
 */
public final class Ages {

    /**
     * Пора жизни.
     * <p>
     * Три, а не пять: каждая обязана <b>что-то менять в игре</b>, иначе
     * это украшение с числом. Ребёнок не работает и не занимает мастерскую,
     * взрослый работает, старик работает медленнее и однажды умирает.
     */
    public enum Stage implements Named {

        /** Не работает, но ест и занимает кровать. */
        CHILD("child"),

        /** Работает, женится, заводит детей. */
        ADULT("adult"),

        /** Работает медленнее и однажды умирает. */
        ELDER("elder");

        private final String id;

        Stage(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        public String key() {
            return "villagepax.age." + id;
        }
    }

    private Ages() {
    }

    /** Со скольких дней житель считается взрослым. */
    public static int grownAt() {
        return Configs.get().childDays();
    }

    /**
     * Со скольких дней он считается стариком.
     * <p>
     * <b>Последняя треть жизни</b>, а не своё число в настройках.
     * У кодека настроек ровно шестнадцать полей, и все они заняты; но
     * дело не только в этом. Два независимых числа — «стареет с 80»
     * и «умирает в 60» — противоречат друг другу молча, а треть
     * противоречить не умеет.
     */
    public static int oldAt() {
        return Math.max(1, diesAt() * 2 / 3);
    }

    /** После скольких дней он умирает от старости. */
    public static int diesAt() {
        return Configs.get().lifeDays();
    }

    /** Сколько дней прожил; отрицательное — «возраста не помнит». */
    public static int daysOf(Citizen citizen) {
        return citizen.lived();
    }

    /**
     * Пора жизни этого жителя.
     * <p>
     * Житель без дня рождения — взрослый, и это решение, а не умолчание
     * от лени: он работает с первого дня мода, и объявить его ребёнком
     * значило бы остановить колонию у всех, кто обновился.
     */
    public static Stage stageOf(Citizen citizen) {
        int days = daysOf(citizen);
        if (days < 0) {
            return Stage.ADULT;
        }
        if (days < grownAt()) {
            return Stage.CHILD;
        }
        return days >= oldAt() ? Stage.ELDER : Stage.ADULT;
    }

    public static boolean isChild(Citizen citizen) {
        return stageOf(citizen) == Stage.CHILD;
    }

    public static boolean isAdult(Citizen citizen) {
        return stageOf(citizen) == Stage.ADULT;
    }

    public static boolean isElder(Citizen citizen) {
        return stageOf(citizen) == Stage.ELDER;
    }

    /**
     * Работает ли житель вполсилы.
     * <p>
     * У старика руки уже не те — ровно как у голодного, и <b>той же</b>
     * механикой: мод уже умеет «вполсилы», и заводить вторую значило бы
     * получить два разных замедления, которые однажды сложатся.
     */
    public static boolean worksSlowly(Citizen citizen) {
        return isElder(citizen);
    }

    /**
     * Пришёл взрослым: записать ему день рождения задним числом.
     * <p>
     * Ровно на грани взросления, а не в середине жизни: пришлый должен
     * работать с первого дня и при этом стареть вместе со всеми. Дать ему
     * {@link Citizen.Life#UNAGED} было бы проще, но тогда колония
     * наполнилась бы бессмертными, и вся эта система свелась бы
     * к рождению детей, которые умирают раньше родителей.
     */
    public static void arrivedGrown(Citizen citizen) {
        citizen.setLived(grownAt());
    }
}
