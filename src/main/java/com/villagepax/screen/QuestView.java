package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Снимок разговора со старейшиной — то, что игрок видит в экране квестов.
 * <p>
 * Экран ничего не считает: сколько принесено, хватает ли доверия и что
 * откроется дальше — решает сервер, потому что там лежат и квесты датапака,
 * и репутация. Клиенту достаются готовые строки и числа.
 *
 * @param village    опознаватель деревни: с ним поедет намерение отдать
 * @param villageName имя деревни, каким его увидит игрок
 * @param giver      профессия выдающего — тоже нужна намерению
 * @param standing   ключ названия отношения: чужак, знакомый, друг, почётный
 * @param reputation сколько доверия набрано
 * @param nextAt     с какого числа начинается следующая ступень, если она есть
 * @param quest      предложенный квест, если он есть
 * @param stalls     чем деревня торгует, с уже решённым «можно ли сейчас»
 * @param purse      сколько монеты в кошеле деревни
 * @param caravan    обоз, если разговор идёт с ним, а не с деревней
 */
public record QuestView(UUID village, String villageName, Identifier giver, String standing,
                        int reputation, Optional<Integer> nextAt, Optional<Offer> quest,
                        List<Stall> stalls, int purse, Optional<UUID> caravan) {

    /**
     * Предложенный квест.
     *
     * @param dialogue   ключ слов, которыми выдающий просит
     * @param objectives что требуется, с уже посчитанным «сколько есть»
     * @param rewards    что за это дадут, готовыми строками
     * @param ready      всё ли принесено: по этому включается кнопка
     */
    public record Offer(String dialogue, List<Need> objectives, List<String> rewards,
                        boolean ready) {

        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dialogue").forGetter(Offer::dialogue),
                Need.CODEC.listOf().fieldOf("objectives").forGetter(Offer::objectives),
                Codec.STRING.listOf().optionalFieldOf("rewards", List.of()).forGetter(Offer::rewards),
                Codec.BOOL.fieldOf("ready").forGetter(Offer::ready)
        ).apply(instance, Offer::new));
    }

    /**
     * Одно требование: что, сколько надо и сколько уже в руках.
     * <p>
     * «Сколько есть» считает сервер и присылает числом. Клиент мог бы
     * посчитать сам по своему инвентарю — но тогда правило «сколько
     * считается принесённым» жило бы в двух местах и разошлось бы.
     */
    public record Need(Item item, int need, int have) {

        public static final Codec<Need> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Registries.ITEM.getCodec().fieldOf("item").forGetter(Need::item),
                Codec.INT.fieldOf("need").forGetter(Need::need),
                Codec.INT.fieldOf("have").forGetter(Need::have)
        ).apply(instance, Need::new));

        public boolean enough() {
            return have >= need;
        }
    }

    /**
     * Одна сделка на столе торга, уже пригодная к показу.
     * <p>
     * Можно ли сторговаться <b>решает сервер</b> и присылает решением, а не
     * данными для расчёта. Клиент иначе обязан был бы знать и склад деревни,
     * и её кошель, и порог доверия — то есть половину механики, — а разошлись
     * бы они при первом же расхождении версий.
     *
     * @param item         чем торгуют
     * @param count        сколько за сделку
     * @param price        сколько изумрудов
     * @param villageSells деревня продаёт (иначе — скупает)
     * @param ready        можно ли сторговаться сейчас, и если нет — почему
     */
    public record Stall(Item item, int count, int price, boolean villageSells, Ready ready) {

        public static final Codec<Stall> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Registries.ITEM.getCodec().fieldOf("item").forGetter(Stall::item),
                Codec.INT.fieldOf("count").forGetter(Stall::count),
                Codec.INT.fieldOf("price").forGetter(Stall::price),
                Codec.BOOL.fieldOf("village_sells").forGetter(Stall::villageSells),
                Ready.CODEC.fieldOf("ready").forGetter(Stall::ready)
        ).apply(instance, Stall::new));
    }

    /**
     * Можно ли сторговаться — и если нет, то чья это забота.
     * <p>
     * Причина названа затем, что «кнопка серая» игроку ничего не объясняет.
     * «У деревни нет монеты» — это подсказка: принеси другое или подожди
     * дня. «Тебе пока не доверяют» — тоже: сходи с квестом.
     */
    public enum Ready implements Named {

        /** Всё сходится. */
        YES("yes"),

        /** Доверия не хватает: эту вещь чужаку не отдадут. */
        NO_TRUST("no_trust"),

        /** У деревни нет ни товара, ни монеты на покупку. */
        VILLAGE_CANT("village_cant"),

        /** У игрока нет товара или монеты. */
        PLAYER_CANT("player_cant");

        public static final Codec<Ready> CODEC = EnumCodecs.of(values(), "готовность сделки");

        private final String id;

        Ready(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** Ключ объяснения. У сходящейся сделки объяснять нечего. */
        public Optional<String> reasonKey() {
            return this == YES ? Optional.empty()
                    : Optional.of("villagepax.trade.reason." + id);
        }
    }

    public static final Codec<QuestView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("village").forGetter(QuestView::village),
            Codec.STRING.fieldOf("village_name").forGetter(QuestView::villageName),
            Identifier.CODEC.fieldOf("giver").forGetter(QuestView::giver),
            Codec.STRING.fieldOf("standing").forGetter(QuestView::standing),
            Codec.INT.fieldOf("reputation").forGetter(QuestView::reputation),
            Codec.INT.optionalFieldOf("next_at").forGetter(QuestView::nextAt),
            Offer.CODEC.optionalFieldOf("quest").forGetter(QuestView::quest),
            Stall.CODEC.listOf().optionalFieldOf("stalls", List.of()).forGetter(QuestView::stalls),
            Codec.INT.optionalFieldOf("purse", 0).forGetter(QuestView::purse),
            Uuids.STRING_CODEC.optionalFieldOf("caravan").forGetter(QuestView::caravan)
    ).apply(instance, QuestView::new));

    /** Торгует ли эта деревня вообще: по этому решается, есть ли вкладка торга. */
    public boolean trades() {
        return !stalls.isEmpty();
    }
}
