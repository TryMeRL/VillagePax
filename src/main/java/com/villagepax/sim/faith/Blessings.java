package com.villagepax.sim.faith;

import com.villagepax.core.faith.Domain;
import com.villagepax.core.faith.Gods;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Благословение: помощь, за которую платят благосклонностью.
 * <p>
 * Это единственное место, где у благосклонности появляется <b>цена</b>,
 * и без него весь пантеон был бы растущим числом. Плата же создаёт тот
 * самый выбор, о котором просил заказчик: помощь сегодня или избранничество
 * когда-нибудь. Копить до верхней ступени и звать благословение каждые три
 * дня одновременно нельзя — и это не наказание, а вся игра в веру.
 * <p>
 * Действуют благословения врезками в три места мода: билдер, поле и набег.
 * Спрашивать их надо через {@link Faith#blessed}, а не через поселение
 * напрямую, — иначе верхняя ступень, при которой благословение держится
 * само, пришлось бы проверять в каждой врезке заново.
 */
public final class Blessings {

    /** Чем кончилась просьба о благословении. */
    public enum Verdict {

        /** Благословлено. */
        DONE("done"),

        /** У этого народа нет бога такого домена. */
        NO_GOD("no_god"),

        /** Бог ещё не заметил поселения. */
        NOT_NOTICED("not_noticed"),

        /**
         * Избранников не благословляют за деньги.
         * <p>
         * У верхней ступени благословение держится само, и списать за него
         * очки значило бы продать игроку то, что у него уже есть. Отказ
         * здесь — защита игрока от собственной кнопки.
         */
        ALWAYS_ON("always_on"),

        /** Чужая колония. */
        NOT_YOURS("not_yours");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        public String key() {
            return "villagepax.faith.blessing." + id;
        }

        public boolean isDone() {
            return this == DONE;
        }
    }

    private Blessings() {
    }

    /**
     * Можно ли просить — и почему нет.
     * <p>
     * Чистая функция, как и суд над жертвой: ни мира, ни записи. Проверять
     * отказы надо именно так, иначе «отказ обязан говорить причину»
     * проверяется глазами.
     */
    public static Verdict judge(Settlement colony, Domain domain, long today) {
        Identifier god = Gods.inDomain(colony.culture(), domain).orElse(null);
        if (god == null) {
            return Verdict.NO_GOD;
        }

        Faith.Tier tier = Faith.tierOf(colony, god);
        if (tier.reached(Faith.Tier.CHOSEN)) {
            return Verdict.ALWAYS_ON;
        }
        // Отдельного отказа «не хватает благосклонности» здесь нет,
        // и это не упущение. Цена равна порогу ступени: дошёл до ступени —
        // значит, хватает, а не дошёл — об этом и сказано. Вердикт,
        // который не может сработать никогда, — та же заглушка, только
        // незаметная, и в этом моде их уже выпалывали.
        if (!tier.reached(Faith.Tier.NOTICED)) {
            return Verdict.NOT_NOTICED;
        }
        return Verdict.DONE;
    }

    /**
     * Позвать благословение по-настоящему.
     * <p>
     * Очки списываются <b>до</b> наложения дней, а не после: если что-то
     * пойдёт не так между ними, лучше потерять очки, чем выдать бесплатное
     * благословение. Впрочем, между ними ничего и нет — обе правки идут
     * одним обновлением поселения.
     */
    public static Verdict invoke(SettlementManager manager, Settlement colony, Domain domain,
                                 long today) {
        Verdict verdict = judge(colony, domain, today);
        if (!verdict.isDone()) {
            return verdict;
        }

        Identifier god = Gods.inDomain(colony.culture(), domain).orElseThrow();
        manager.update(colony.id(), state -> {
            state.addFavour(god, -Faith.BLESSING_COST);
            state.bless(domain.id(), today, Faith.BLESSING_DAYS);
        });
        return Verdict.DONE;
    }

    /** Бог этого домена у этого народа, если он есть. */
    public static Optional<Identifier> godOf(Settlement colony, Domain domain) {
        return Gods.inDomain(colony.culture(), domain);
    }

    /**
     * Насколько благословение камня ускоряет билдера.
     * <p>
     * Прибавка, а не замена: она складывается с каменным делом норманнов,
     * и это не оплошность. Народ, который умеет с камнем, под рукой своего
     * бога кладёт втрое — так черта народа и благословение остаются двумя
     * разными вещами, а не спорят за одно число.
     */
    public static int buildBonus(Settlement colony, long today) {
        return Faith.blessed(colony, Domain.STONE, today) ? 1 : 0;
    }

    /**
     * На скольких бойцов благословение дозора убавляет отряд.
     * <p>
     * Считается вместе с башнями и по тому же правилу — никогда до нуля.
     * Набег, который не приходит, это выключенная механика, и бог тут
     * ничем не отличается от каменной башни.
     */
    public static int watchBonus(Settlement colony, long today) {
        return Faith.blessed(colony, Domain.WATCH, today) ? 1 : 0;
    }
}
