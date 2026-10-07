package com.villagepax.sim;

import com.villagepax.sim.festival.FestivalCalendar;
import com.villagepax.sim.trade.MarketDay;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Календарь народов: какой сегодня день, где рынок, у кого праздник.
 * <p>
 * Рынок раз в семь дней, праздник раз в восемь, и у каждого народа свой
 * день того и другого — полный круг повторяется через пятьдесят шесть
 * дней. Держать это в голове игрок не обязан: справочник в
 * {@code docs/handbook.md} даёт таблицу, а {@code /villagepax calendar} —
 * то же самое на сегодня, прямо в игре.
 */
public final class Almanac {

    /** Праздник народа, как его видит календарь: имя и фаза луны. */
    public record Feast(String nameKey, int moonPhase) {
    }

    private Almanac() {
    }

    /**
     * Строки календаря на этот день. Чистое правило: мира не спрашивает.
     *
     * @param feasts праздники народов; народ без праздника — без записи
     */
    public static List<Text> lines(long day, List<Identifier> cultures, Map<Identifier, Feast> feasts) {
        List<Text> lines = new ArrayList<>();
        int phase = FestivalCalendar.moonPhase(day);
        lines.add(Text.translatable("villagepax.almanac.today", day,
                Text.translatable("villagepax.almanac.moon." + phase)));

        List<Identifier> today = new ArrayList<>();
        List<Identifier> tomorrow = new ArrayList<>();
        for (Identifier culture : cultures) {
            int until = MarketDay.daysUntil(culture, day);
            if (until == 0) {
                today.add(culture);
            } else if (until == 1) {
                tomorrow.add(culture);
            }
        }
        lines.add(Text.translatable("villagepax.almanac.market", peoples(today), peoples(tomorrow)));

        List<Identifier> festive = new ArrayList<>(cultures);
        festive.removeIf(culture -> !feasts.containsKey(culture));
        festive.sort(Comparator.comparingInt((Identifier culture) ->
                FestivalCalendar.daysUntil(day, feasts.get(culture).moonPhase()))
                .thenComparing(Identifier::toString));
        for (Identifier culture : festive) {
            Feast feast = feasts.get(culture);
            int until = FestivalCalendar.daysUntil(day, feast.moonPhase());
            Text name = Text.translatable(feast.nameKey());
            Text people = people(culture);
            lines.add(switch (until) {
                case 0 -> Text.translatable("villagepax.almanac.festival_today", name, people);
                case 1 -> Text.translatable("villagepax.almanac.festival_tomorrow", name, people);
                default -> Text.translatable("villagepax.almanac.festival_in", name, people, until);
            });
        }
        return lines;
    }

    private static Text people(Identifier culture) {
        return Text.translatable("villagepax.culture." + culture.getPath());
    }

    /** Народы через запятую, а если никого — «нигде». */
    private static Text peoples(List<Identifier> cultures) {
        if (cultures.isEmpty()) {
            return Text.translatable("villagepax.almanac.nowhere");
        }
        List<Identifier> sorted = new ArrayList<>(cultures);
        sorted.sort(Comparator.comparing(Identifier::toString));
        MutableText out = Text.empty();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(people(sorted.get(i)));
        }
        return out;
    }
}
