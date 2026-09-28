package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

/**
 * «Очко» на костях: правила без мира.
 * <p>
 * Кость подставная: в игре её бросает случай мира, в проверке — список.
 * Игрок бросает, сколько хочет; соперник бросает вторым, видя счёт игрока,
 * и добирает, пока не обгонит, — так и сидят за столом: кто бросает вторым,
 * тот знает, до чего добирать. На равном решает нрав: трус и лентяй встают,
 * ровный — с восемнадцати, честолюбивый рискует всегда.
 */
public final class Ochko {

    /** Больше — перебор; ровно — «очко». */
    public static final int LIMIT = 21;

    /** С какого счёта ровный встаёт на равном. */
    static final int EVEN_STOPS_AT = 18;

    public enum Phase { PLAYER, RIVAL, DONE }

    /** Итог глазами игрока. */
    public enum Outcome { WIN, LOSE, PUSH }

    private final Nature rival;
    private final IntSupplier dice;
    private final List<Integer> mine = new ArrayList<>();
    private final List<Integer> theirs = new ArrayList<>();
    private Phase phase = Phase.PLAYER;
    private Outcome outcome;

    public Ochko(Nature rival, IntSupplier dice) {
        this.rival = rival;
        this.dice = dice;
    }

    /** Бросок игрока. Перебор — конец; ровно двадцать одно — ход сам переходит к сопернику. */
    public int roll() {
        if (phase != Phase.PLAYER) {
            throw new IllegalStateException("бросает не игрок: " + phase);
        }
        int face = face();
        mine.add(face);
        if (myTotal() > LIMIT) {
            end(Outcome.LOSE);
        } else if (myTotal() == LIMIT) {
            phase = Phase.RIVAL;
        }
        return face;
    }

    /** «Хватит» — только после первого броска: без броска нечего и хватать. */
    public void stand() {
        if (phase != Phase.PLAYER || mine.isEmpty()) {
            throw new IllegalStateException("встать можно после броска, а сейчас " + phase);
        }
        phase = Phase.RIVAL;
    }

    /**
     * Один шаг соперника: бросок, если он бросает, иначе итог.
     *
     * @return выпавшее, или пусто, если соперник встал и партия кончена
     */
    public OptionalInt rivalStep() {
        if (phase != Phase.RIVAL) {
            throw new IllegalStateException("бросает не соперник: " + phase);
        }
        if (!rivalGoesOn()) {
            end(theirTotal() > myTotal() ? Outcome.LOSE : Outcome.PUSH);
            return OptionalInt.empty();
        }
        int face = face();
        theirs.add(face);
        if (theirTotal() > LIMIT) {
            end(Outcome.WIN);
        }
        return OptionalInt.of(face);
    }

    /** Бросит ли соперник ещё: позади — бросает, впереди — встаёт, на равном — по нраву. */
    boolean rivalGoesOn() {
        int his = theirTotal();
        int yours = myTotal();
        if (his == LIMIT || his > yours) {
            return false;
        }
        if (his < yours) {
            return true;
        }
        return switch (rival) {
            case COWARD, LAZY -> false;
            case AMBITIOUS -> true;
            case EVEN, PIOUS -> his < EVEN_STOPS_AT;
        };
    }

    /** Во сколько ставок итог: ничья — ноль, «очко» победителя — две, иначе одна. */
    public int stakes() {
        if (outcome == null || outcome == Outcome.PUSH) {
            return 0;
        }
        int winner = outcome == Outcome.WIN ? myTotal() : theirTotal();
        return winner == LIMIT ? 2 : 1;
    }

    public Phase phase() {
        return phase;
    }

    public Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public List<Integer> mine() {
        return List.copyOf(mine);
    }

    public List<Integer> theirs() {
        return List.copyOf(theirs);
    }

    public int myTotal() {
        return mine.stream().mapToInt(Integer::intValue).sum();
    }

    public int theirTotal() {
        return theirs.stream().mapToInt(Integer::intValue).sum();
    }

    private int face() {
        int face = dice.getAsInt();
        if (face < 1 || face > 6) {
            throw new IllegalArgumentException("у кости нет грани " + face);
        }
        return face;
    }

    private void end(Outcome result) {
        outcome = result;
        phase = Phase.DONE;
    }
}
