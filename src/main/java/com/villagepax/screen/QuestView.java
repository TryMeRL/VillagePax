package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 */
public record QuestView(UUID village, String villageName, Identifier giver, String standing,
                        int reputation, Optional<Integer> nextAt, Optional<Offer> quest) {

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

    public static final Codec<QuestView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("village").forGetter(QuestView::village),
            Codec.STRING.fieldOf("village_name").forGetter(QuestView::villageName),
            Identifier.CODEC.fieldOf("giver").forGetter(QuestView::giver),
            Codec.STRING.fieldOf("standing").forGetter(QuestView::standing),
            Codec.INT.fieldOf("reputation").forGetter(QuestView::reputation),
            Codec.INT.optionalFieldOf("next_at").forGetter(QuestView::nextAt),
            Offer.CODEC.optionalFieldOf("quest").forGetter(QuestView::quest)
    ).apply(instance, QuestView::new));
}
