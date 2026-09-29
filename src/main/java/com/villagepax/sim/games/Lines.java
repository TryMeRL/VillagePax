package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Что житель говорит за игрой: ключ словаря по случаю и нраву.
 * <p>
 * Ключи — {@code villagepax.games.say.<случай>.<нрав>.<n>} с запасом
 * {@code villagepax.games.say.<случай>.<n>}. Счёт идёт по словарю, а не
 * по числу в коде: новая фраза дописывается строкой, и мод её подхватывает.
 * <p>
 * Считает сервер по своему словарю — Fabric грузит в него {@code en_us}
 * модов. Клиент показывает тот же ключ на своём языке, и номер там
 * найдётся: наборы ключей у языков одинаковы, это держит {@code LangTest}.
 */
public final class Lines {

    private static final String PREFIX = "villagepax.games.say.";

    private Lines() {
    }

    /**
     * Ключ фразы: сперва запас нрава, потом общий.
     *
     * @param known  есть ли такой ключ в словаре
     * @param choose случай: по границе — номер от нуля до неё
     */
    static String pick(Say say, Nature nature, Predicate<String> known, IntUnaryOperator choose) {
        String base = PREFIX + say.id();
        String own = base + "." + nature.id();
        int mine = count(own, known);
        if (mine > 0) {
            return own + "." + (1 + choose.applyAsInt(mine));
        }
        int common = count(base, known);
        return common > 0 ? base + "." + (1 + choose.applyAsInt(common)) : base + ".1";
    }

    /** Сколько номеров подряд с единицы: пропуск кончает запас. */
    private static int count(String prefix, Predicate<String> known) {
        int n = 0;
        while (known.test(prefix + "." + (n + 1))) {
            n++;
        }
        return n;
    }

    /** Сказать над головой фразу по нраву этого жителя. @return встала ли фраза */
    public static boolean say(CitizenEntity body, Citizen citizen, Say say, Object... args) {
        return body.say(line(body, citizen, say, args), false);
    }

    /** То же — поверх недавней фразы: итог партии не ждёт, пока отзвучит «Шесть!». */
    public static boolean sayNow(CitizenEntity body, Citizen citizen, Say say, Object... args) {
        return body.say(line(body, citizen, say, args), true);
    }

    private static Text line(CitizenEntity body, Citizen citizen, Say say, Object... args) {
        String key = pick(say, Natures.of(citizen), Language.getInstance()::hasTranslation,
                bound -> body.getRandom().nextInt(bound));
        return Text.translatable(key, args);
    }
}
