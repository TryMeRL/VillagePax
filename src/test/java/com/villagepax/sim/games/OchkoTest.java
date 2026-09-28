package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * «Очко» на костях — решение по игре, и числа здесь написаны руками.
 * <p>
 * Игрок бросает первым; соперник видит его счёт и добирает, пока не обгонит.
 * На равном решает нрав: трус и лентяй встают, ровный — с восемнадцати,
 * честолюбивый рискует. Ровно двадцать одно платится вдвойне.
 */
class OchkoTest {

    private static IntSupplier dice(int... faces) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int face : faces) {
            queue.add(face);
        }
        return queue::removeFirst;
    }

    private static Ochko play(Nature rival, int[] mine, int... theirs) {
        int[] all = new int[mine.length + theirs.length];
        System.arraycopy(mine, 0, all, 0, mine.length);
        System.arraycopy(theirs, 0, all, mine.length, theirs.length);
        Ochko game = new Ochko(rival, dice(all));
        for (int i = 0; i < mine.length; i++) {
            game.roll();
        }
        if (game.phase() == Ochko.Phase.PLAYER) {
            game.stand();
        }
        while (game.phase() == Ochko.Phase.RIVAL) {
            game.rivalStep();
        }
        return game;
    }

    @Test
    void overTwentyOneLosesAtOnceAndTheRivalDoesNotRoll() {
        Ochko game = new Ochko(Nature.EVEN, dice(6, 6, 6, 5));
        game.roll();
        game.roll();
        game.roll();
        game.roll();
        assertEquals(Ochko.Phase.DONE, game.phase());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
        assertEquals(List.of(), game.theirs());
        assertEquals(1, game.stakes());
    }

    @Test
    void standingNeedsARollFirst() {
        Ochko game = new Ochko(Nature.EVEN, dice());
        assertThrows(IllegalStateException.class, game::stand);
    }

    @Test
    void theRivalRollsUntilHeIsAhead() {
        Ochko game = play(Nature.EVEN, new int[]{6, 5, 4}, 5, 6, 6);
        assertEquals(15, game.myTotal());
        assertEquals(17, game.theirTotal());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
        assertEquals(1, game.stakes());
    }

    @Test
    void theRivalBustsAndPays() {
        Ochko game = play(Nature.EVEN, new int[]{6, 6, 6}, 6, 6, 5, 6);
        assertEquals(Optional.of(Ochko.Outcome.WIN), game.outcome());
        assertEquals(23, game.theirTotal());
    }

    @Test
    void aCowardStopsOnATie() {
        Ochko game = play(Nature.COWARD, new int[]{5, 5}, 6, 4);
        assertEquals(Optional.of(Ochko.Outcome.PUSH), game.outcome());
        assertEquals(0, game.stakes());
    }

    @Test
    void aLazyOneStopsOnATieToo() {
        assertEquals(Optional.of(Ochko.Outcome.PUSH),
                play(Nature.LAZY, new int[]{5, 5}, 6, 4).outcome());
    }

    @Test
    void theAmbitiousRollsOnATie() {
        Ochko game = play(Nature.AMBITIOUS, new int[]{5, 5}, 6, 4, 3);
        assertEquals(13, game.theirTotal());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
    }

    @Test
    void anEvenOneStopsOnATieFromEighteen() {
        assertEquals(Optional.of(Ochko.Outcome.PUSH),
                play(Nature.EVEN, new int[]{6, 6, 6}, 6, 6, 6).outcome());
        assertEquals(20, play(Nature.EVEN, new int[]{6, 6, 5}, 6, 6, 5, 3).theirTotal());
    }

    @Test
    void twentyOnePaysDouble() {
        Ochko game = play(Nature.EVEN, new int[]{6, 6, 5, 4}, 6, 6, 6, 4);
        assertEquals(21, game.myTotal());
        assertEquals(Optional.of(Ochko.Outcome.WIN), game.outcome());
        assertEquals(2, game.stakes());
    }

    /** У игрока двадцать: ровный добирает с восемнадцати и выбрасывает ровно очко. */
    @Test
    void theRivalsTwentyOneCostsDouble() {
        Ochko game = play(Nature.EVEN, new int[]{6, 6, 6, 2}, 6, 6, 6, 3);
        assertEquals(21, game.theirTotal());
        assertEquals(Optional.of(Ochko.Outcome.LOSE), game.outcome());
        assertEquals(2, game.stakes());
    }

    /** Оба с очком — ничья: на двадцати одном никто больше не бросает, даже честолюбивый. */
    @Test
    void bothWithTwentyOneIsATie() {
        Ochko game = play(Nature.AMBITIOUS, new int[]{6, 6, 5, 4}, 6, 6, 6, 3);
        assertEquals(Optional.of(Ochko.Outcome.PUSH), game.outcome());
        assertEquals(List.of(6, 6, 6, 3), game.theirs());
    }

    @Test
    void reachingTwentyOneStandsByItself() {
        Ochko game = new Ochko(Nature.EVEN, dice(6, 6, 6, 3));
        game.roll();
        game.roll();
        game.roll();
        game.roll();
        assertEquals(Ochko.Phase.RIVAL, game.phase());
    }

    @Test
    void aStoppedRivalReturnsNoFace() {
        Ochko game = new Ochko(Nature.EVEN, dice(3, 6));
        game.roll();
        game.stand();
        assertEquals(OptionalInt.of(6), game.rivalStep());
        assertEquals(OptionalInt.empty(), game.rivalStep());
        assertEquals(Ochko.Phase.DONE, game.phase());
    }
}
