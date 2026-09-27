package com.villagepax.sim.festival;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Места состязания и ленты за них.
 * <p>
 * Места — по очкам, и равные делят место, как на настоящих состязаниях:
 * у двух первых следующий — третий, а не второй. Жители занимают места
 * наравне с игроками, и тогда игрок сдвигается: соперник, который не
 * может победить, — не соперник.
 * <p>
 * Без очков мест нет: ленту дают за дело, а не за то, что пришёл.
 */
public final class Standings {

    /** Лент за первое место; второму на одну меньше, третьему ещё на одну. */
    private static final int FIRST_PRIZE = 3;

    /**
     * Место в итогах.
     *
     * @param who   кто
     * @param score сколько очков
     * @param place место: 1 — первое; равные делят
     */
    public record Placing(Contestant who, int score, int place) {
    }

    private Standings() {
    }

    /**
     * Места по очкам; равные делят место (5, 5, 3 → 1, 1, 3); без очков мест нет.
     * <p>
     * Равные идут по имени: итоги читают глазами, и порядок, меняющийся
     * от прогона к прогону, выглядел бы как ошибка счёта.
     */
    public static List<Placing> rank(Map<Contestant, Integer> scores) {
        List<Map.Entry<Contestant, Integer>> scored = scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Contestant, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(entry -> entry.getKey().name())
                        .thenComparing(entry -> entry.getKey().id()))
                .toList();
        List<Placing> placings = new ArrayList<>();
        int place = 0;
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < scored.size(); i++) {
            int score = scored.get(i).getValue();
            if (score != previous) {
                place = i + 1;
                previous = score;
            }
            placings.add(new Placing(scored.get(i).getKey(), score, place));
        }
        return placings;
    }

    /** Лент за место: 3, 2, 1, дальше ничего. */
    public static int ribbonsFor(int place) {
        return place >= 1 && place <= FIRST_PRIZE ? FIRST_PRIZE + 1 - place : 0;
    }
}
