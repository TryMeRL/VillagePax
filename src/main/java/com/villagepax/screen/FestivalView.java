package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.StrictCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Что показывает экран затейника: праздник, состязания и лавка.
 * <p>
 * Снимок, а не живая связь: экран не спрашивает сервер на каждом кадре,
 * а получает снимок при открытии и после каждого действия. Предмет назван
 * опознавателем, а не кодеком предмета, — снимок читается проверкой без
 * запущенной игры.
 *
 * @param village     чья ярмарка
 * @param villageName имя поселения
 * @param host        имя затейника
 * @param hostTitle   ключ имени ремесла у народа: жонглёр, скальд, менестрель
 * @param festival    ключ названия праздника
 * @param daysUntil   сколько дней до праздника; 0 — сегодня
 * @param closed      ключ причины, почему сейчас не играют; пусто — играют
 * @param contests    состязания праздника, в его порядке
 * @param ribbons     сколько праздничных лент у игрока
 * @param stallOpen   открыта ли лавка: весь день праздника, и после заката тоже
 * @param prizes      товар лавки
 * @param running     ключ названия идущего сейчас состязания
 */
public record FestivalView(UUID village, String villageName, String host, String hostTitle,
                           String festival, int daysUntil, Optional<String> closed,
                           List<ContestLine> contests, int ribbons, boolean stallOpen,
                           List<PrizeLine> prizes, Optional<String> running) {

    /**
     * Строка состязания.
     *
     * @param name    ключ названия
     * @param kind    вид: по нему экран берёт правило одной строкой
     * @param awarded взял ли игрок приз за него в этот праздник
     * @param refusal ключ причины, почему «Начать» сейчас нельзя; пусто — можно
     */
    public record ContestLine(String name, String kind, boolean awarded, Optional<String> refusal) {

        public static final Codec<ContestLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(ContestLine::name),
                Codec.STRING.fieldOf("kind").forGetter(ContestLine::kind),
                Codec.BOOL.fieldOf("awarded").forGetter(ContestLine::awarded),
                StrictCodecs.optional("refusal", Codec.STRING).forGetter(ContestLine::refusal)
        ).apply(instance, ContestLine::new));
    }

    /**
     * Товар лавки.
     *
     * @param item       что даётся
     * @param count      сколько штук за раз
     * @param price      сколько лент стоит
     * @param affordable хватает ли лент у игрока
     */
    public record PrizeLine(Identifier item, int count, int price, boolean affordable) {

        public static final Codec<PrizeLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(PrizeLine::item),
                Codec.INT.fieldOf("count").forGetter(PrizeLine::count),
                Codec.INT.fieldOf("price").forGetter(PrizeLine::price),
                Codec.BOOL.fieldOf("affordable").forGetter(PrizeLine::affordable)
        ).apply(instance, PrizeLine::new));
    }

    public static final Codec<FestivalView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("village").forGetter(FestivalView::village),
            Codec.STRING.fieldOf("village_name").forGetter(FestivalView::villageName),
            Codec.STRING.fieldOf("host").forGetter(FestivalView::host),
            Codec.STRING.fieldOf("host_title").forGetter(FestivalView::hostTitle),
            Codec.STRING.fieldOf("festival").forGetter(FestivalView::festival),
            Codec.INT.fieldOf("days_until").forGetter(FestivalView::daysUntil),
            StrictCodecs.optional("closed", Codec.STRING).forGetter(FestivalView::closed),
            ContestLine.CODEC.listOf().fieldOf("contests").forGetter(FestivalView::contests),
            Codec.INT.fieldOf("ribbons").forGetter(FestivalView::ribbons),
            Codec.BOOL.fieldOf("stall_open").forGetter(FestivalView::stallOpen),
            PrizeLine.CODEC.listOf().fieldOf("prizes").forGetter(FestivalView::prizes),
            StrictCodecs.optional("running", Codec.STRING).forGetter(FestivalView::running)
    ).apply(instance, FestivalView::new));
}
