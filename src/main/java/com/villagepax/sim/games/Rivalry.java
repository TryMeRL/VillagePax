package com.villagepax.sim.games;

/**
 * Счёт жителя с одним игроком — глазами жителя.
 * <p>
 * Из него живой соперник: проигравший зовёт отыграться, выигравший
 * хвалится, а проигравший трижды за вечер встаёт из-за стола.
 *
 * @param won       сколько раз житель выиграл
 * @param lost      сколько раз проиграл
 * @param streak    серия: больше нуля — житель выиграл подряд, меньше — проиграл подряд
 * @param day       день последней партии
 * @param lostToday проигрышей подряд в этот день: из них и обида
 */
public record Rivalry(int won, int lost, int streak, long day, int lostToday) {

    /** Незнакомец: партий не было. */
    public static final Rivalry NONE = new Rivalry(0, 0, 0, Long.MIN_VALUE, 0);

    /** После скольких поражений подряд за вечер житель встаёт из-за стола. */
    public static final int SULK_AFTER = 3;

    /** Исход партии глазами жителя. */
    public enum Result { WON, LOST, EVEN }

    /** Счёт после ещё одной партии. Ничья не прибавляет и не обрывает: «подряд» считают проигрыши. */
    public Rivalry after(Result result, long today) {
        int evening = day == today ? lostToday : 0;
        return switch (result) {
            case WON -> new Rivalry(won + 1, lost, Math.max(streak, 0) + 1, today, 0);
            case LOST -> new Rivalry(won, lost + 1, Math.min(streak, 0) - 1, today, evening + 1);
            case EVEN -> new Rivalry(won, lost, streak, today, evening);
        };
    }

    /** Обижен ли сегодня: назавтра обида проходит сама. */
    public boolean sulks(long today) {
        return day == today && lostToday >= SULK_AFTER;
    }
}
