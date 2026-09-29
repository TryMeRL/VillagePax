package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;

import java.util.Optional;

/**
 * Армрестлинг в такт: правила без мира.
 * <p>
 * Перевес — полоса от −100 (рука игрока легла) до +100 (легла рука
 * соперника). По шкале туда и обратно бегает отметка; нажатие в зелёном
 * посередине клонит руку соперника, мимо — свою. Соперник давит сам
 * каждый тик, и чем он сильнее, тем уже зелёное.
 * <p>
 * Время — тики партии, а не часы мира: сервер ведёт отметку от тика начала,
 * клиент её только рисует, и нажатие судится по тому тику, в который пришло.
 * Допуск в тик — на дорогу пакета: иначе в гостях у сервера зелёное было бы
 * уже, чем дома.
 */
public final class ArmWrestle {

    /** Край полосы: рука легла. */
    public static final int EDGE = 100;

    /** От края шкалы до края — тиков. */
    public static final int PASS_TICKS = 24;

    /** Сколько длится партия: тридцать секунд. */
    public static final int LIMIT_TICKS = 600;

    /** Нажатие в зелёном: на столько клонится рука соперника. */
    static final double HIT = 18;

    /** Нажатие мимо: на столько клонится своя. */
    static final double MISS = 12;

    /** Лентяй сдаётся, когда игрок впереди на столько. */
    static final double LAZY_GIVES_UP = 50;

    /** И трус — позже, но тоже не до края. */
    static final double COWARD_GIVES_UP = 70;

    /** Итог глазами игрока. */
    public enum Outcome { WIN, LOSE, DRAW }

    private final double strength;
    private final Nature nature;
    private double balance;
    private long elapsed;
    private long scoredPass = -1;
    private Outcome outcome;

    public ArmWrestle(double strength, Nature nature) {
        this.strength = Math.max(0, Math.min(1, strength));
        this.nature = nature;
    }

    /** Доля шкалы под зелёным: у сильного уже. */
    public double zoneWidth() {
        return 0.30 - 0.15 * strength;
    }

    /** Где отметка в этот тик: 0 — левый край, 1 — правый; туда и обратно. */
    public static double markerAt(long tick) {
        long t = Math.floorMod(tick, 2L * PASS_TICKS);
        return t <= PASS_TICKS ? (double) t / PASS_TICKS : 2.0 - (double) t / PASS_TICKS;
    }

    /**
     * То же для кадра: между тиками отметка бежит плавно.
     * <p>
     * Судит сервер по целому тику ({@link #markerAt(long)}); клиенту же
     * снимок приходит раз в два тика, а рисовать отметку надо каждый кадр.
     */
    public static double markerAt(double tick) {
        double period = 2.0 * PASS_TICKS;
        double t = ((tick % period) + period) % period;
        return t <= PASS_TICKS ? t / PASS_TICKS : 2.0 - t / PASS_TICKS;
    }

    public boolean inZone(double marker) {
        return Math.abs(marker - 0.5) <= zoneWidth() / 2;
    }

    /** Тик партии: соперник давит, время идёт. */
    public void tick() {
        if (outcome != null) {
            return;
        }
        elapsed++;
        balance -= 0.2 + 0.5 * strength;
        settle();
    }

    /**
     * Нажатие игрока.
     * <p>
     * За проход засчитывается одно попадание: частить незачем, и нажатия
     * подряд по зелёному не выигрывают партию быстрее ритма.
     *
     * @return попал ли
     */
    public boolean press() {
        if (outcome != null) {
            return false;
        }
        long pass = elapsed / PASS_TICKS;
        boolean hit = pass != scoredPass
                && (inZone(markerAt(elapsed)) || inZone(markerAt(elapsed - 1)));
        if (hit) {
            balance += HIT;
            scoredPass = pass;
        } else {
            balance -= MISS;
        }
        settle();
        return hit;
    }

    private void settle() {
        balance = Math.max(-EDGE, Math.min(EDGE, balance));
        if (balance >= EDGE) {
            outcome = Outcome.WIN;
        } else if (balance <= -EDGE) {
            outcome = Outcome.LOSE;
        } else if (nature == Nature.LAZY && balance >= LAZY_GIVES_UP
                || nature == Nature.COWARD && balance >= COWARD_GIVES_UP) {
            outcome = Outcome.WIN;
        } else if (elapsed >= LIMIT_TICKS) {
            outcome = Math.abs(balance) < 1 ? Outcome.DRAW
                    : balance > 0 ? Outcome.WIN : Outcome.LOSE;
        }
    }

    public double balance() {
        return balance;
    }

    public long elapsed() {
        return elapsed;
    }

    public Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }
}
