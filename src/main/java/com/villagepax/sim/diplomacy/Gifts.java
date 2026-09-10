package com.villagepax.sim.diplomacy;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.trade.Trading;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Подарок деревне — первое дипломатическое действие в моде.
 * <p>
 * До него доверие росло только двумя путями: квестами, которые деревня
 * выдаёт сама и когда захочет, и по очку за сделку. Игроку, который хочет
 * подружиться <b>сейчас</b>, делать было нечего — а дизайн-документ ставит
 * подарки первыми в списке дипломатических действий, и не случайно: это
 * единственный способ проявить добрую волю, не дожидаясь, пока о тебе
 * вспомнят.
 * <p>
 * <b>Дарить надо то, что деревне нужно.</b> Цена подарка берётся из её
 * же стола скупки: что деревня готова купить, то ей и нужно. Своё она
 * и так продаёт — норманн, которому принесли норманнского хлеба, вежливо
 * не поймёт. Отсюда и польза правила: оно учит игрока смотреть, чем эта
 * деревня живёт, и делает два народа разными не только на вид.
 * <p>
 * <b>Раз в день.</b> Иначе честь покупается за один сундук: сорок стопок
 * железа — и чужак становится почётным жителем, не сказав ни слова. Один
 * подарок в сутки превращает дружбу в то, чем она и должна быть, — в
 * привычку возвращаться.
 * <p>
 * И берут ровно столько, сколько принято принимать за раз: если в руках
 * больше, чем стоит суточной благодарности, лишнее останется у игрока.
 * Молча забрать стопку железа за те же восемь очков было бы обманом.
 */
public final class Gifts {

    /**
     * Сколько медяков стоит одно очко доверия.
     * <p>
     * Шесть — чтобы вся суточная благодарность стоила около полусотни
     * медяков, то есть примерно стопки железа или одной золотой монеты.
     * Это ощутимо, но не разорительно: дорога от чужака до друга — неделя
     * подарков либо один хороший квест.
     */
    public static final int COPPER_PER_TRUST = 6;

    /** Больше этого за сутки не поблагодарят, сколько ни принеси. */
    public static final int MOST_PER_DAY = 8;

    private Gifts() {
    }

    /**
     * Что деревня даст за один подарок такой цены.
     * <p>
     * Чистая функция: её видно и её можно проверить без запущенной игры.
     * Ниже очка не бывает — принесённое приняли, значит поблагодарили,
     * пусть и вежливым кивком.
     */
    public static int trustFor(int copper) {
        if (copper <= 0) {
            return 0;
        }
        return Math.min(MOST_PER_DAY, Math.max(1, copper / COPPER_PER_TRUST));
    }

    /**
     * Сколько предметов такой цены довольно на суточную благодарность.
     * <p>
     * Больше брать незачем: доверия это не прибавит, а у игрока отнимет.
     *
     * @param price цена сделки из стола скупки
     * @param count сколько предметов идёт за эту цену
     */
    public static int enoughOf(int price, int count) {
        int target = MOST_PER_DAY * COPPER_PER_TRUST;
        // Округление вверх: неполной сделки деревня не считает.
        return Math.max(1, (target * count + price - 1) / price);
    }

    /**
     * Чего стоит для этой деревни столько таких предметов, в медяках.
     * <p>
     * Монета — сама себе цена: её не надо продавать, чтобы понять,
     * сколько она стоит.
     */
    public static int worthOf(Settlement village, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int coin = Coins.worth(stack.getItem());
        if (coin > 0) {
            return coin * stack.getCount();
        }
        return rateOf(village, stack)
                .map(deal -> deal.price() * stack.getCount() / deal.count())
                .orElse(0);
    }

    /** Сколько взять из рук: не больше, чем стоит суточной благодарности. */
    public static int takeableFrom(Settlement village, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int coin = Coins.worth(stack.getItem());
        if (coin > 0) {
            return Math.min(stack.getCount(), enoughOf(coin, 1));
        }
        return rateOf(village, stack)
                .map(deal -> Math.min(stack.getCount(), enoughOf(deal.price(), deal.count())))
                .orElse(0);
    }

    /**
     * Приговор подарку: примут ли, и если нет — почему.
     * <p>
     * Причина названа затем же, зачем она названа у сделки: серая кнопка
     * игроку ничего не объясняет. «Этому здесь не рады» — подсказка идти
     * смотреть стол скупки; «сегодня уже дарили» — подсказка прийти завтра.
     */
    public enum Verdict implements Named {

        /** Примут с благодарностью. */
        YES("yes"),

        /** В руках ничего нет. */
        EMPTY_HANDED("empty_handed"),

        /** Этого деревне не нужно: своего хватает. */
        NOT_WANTED("not_wanted"),

        /** Нужно, но этого слишком мало, чтобы считаться подарком. */
        TOO_LITTLE("too_little"),

        /** Сегодня уже дарили. */
        ALREADY_TODAY("already_today");

        /** Приговор уезжает на клиент в снимке разговора: экран его показывает. */
        public static final Codec<Verdict> CODEC = EnumCodecs.of(values(), "подарок");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** Ключ объяснения. Принятому подарку объяснять нечего. */
        public Optional<String> reasonKey() {
            return this == YES ? Optional.empty()
                    : Optional.of("villagepax.gift.reason." + id);
        }
    }

    /**
     * Что будет, если подарить вот это, — без последствий.
     * <p>
     * Считает сервер и присылает решением: клиенту иначе понадобились бы
     * и стол скупки деревни, и память о подарках, и правило цены — то есть
     * вся механика, только второй раз и с шансом разойтись.
     */
    public static Verdict judge(Settlement village, UUID player, ItemStack held, long today) {
        if (held.isEmpty()) {
            return Verdict.EMPTY_HANDED;
        }
        if (village.giftedOn(player) == today) {
            return Verdict.ALREADY_TODAY;
        }
        if (Coins.worth(held.getItem()) <= 0 && rateOf(village, held).isEmpty()) {
            return Verdict.NOT_WANTED;
        }
        return trustFor(worthOf(village, held.copyWithCount(takeableFrom(village, held)))) > 0
                ? Verdict.YES : Verdict.TOO_LITTLE;
    }

    /**
     * Подарить.
     * <p>
     * Через {@link Relations#deed}, а не правкой доверия на месте: подарок
     * — это ровно тот поступок, о котором дизайн-документ говорит «слегка
     * поднимает и отношение всего народа». Свои услышат, чужие заметят.
     *
     * @return сколько дали доверия и что об этом сказали народы; пусто, если
     *         подарок не приняли
     */
    public static Outcome give(SettlementManager manager, Settlement village, UUID player,
                               ItemStack held, long today) {
        Verdict verdict = judge(village, player, held, today);
        if (verdict != Verdict.YES) {
            return new Outcome(verdict, 0, ItemStack.EMPTY, List.of());
        }

        int take = takeableFrom(village, held);
        int trust = trustFor(worthOf(village, held.copyWithCount(take)));

        // Доверие сперва, вещи потом. Порядок важен: если поселения
        // в менеджере почему-то не окажется, доверие тихо потеряется, —
        // и терять вместе с ним ещё и подарок игрока было бы вдвойне
        // несправедливо. Из рук берётся последним делом.
        manager.update(village.id(), state -> state.noteGift(player, today));
        List<Relations.Shift> shifts = Relations.deed(manager, village, player, trust);
        return new Outcome(Verdict.YES, trust, held.split(take), shifts);
    }

    /**
     * Чем кончился подарок.
     *
     * @param verdict приняли или нет
     * @param trust   сколько доверия дали
     * @param given   что забрали из рук
     * @param shifts  какие народы сменили ступень
     */
    public record Outcome(Verdict verdict, int trust, ItemStack given,
                          List<Relations.Shift> shifts) {

        public boolean accepted() {
            return verdict == Verdict.YES;
        }
    }

    /**
     * Строка стола скупки для этого предмета — или ничего.
     * <p>
     * Именно {@link TradeTable#buyRate}, а не {@link Trading#rate}: тот
     * знает цену <b>чему угодно</b>, чтобы обоз мог хоть как-то оценить
     * любой груз, и с ним подарком стало бы всё на свете, включая грязь.
     */
    private static Optional<TradeTable.Deal> rateOf(Settlement village, ItemStack stack) {
        return Trading.tableOf(village).flatMap(table -> table.buyRate(stack.getItem()));
    }
}
