package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.StrictCodecs;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Что показывает окно игры: соперник, счёт с ним, ставки — и партия, если идёт.
 * <p>
 * Снимок, а не живая связь, как у затейника: экран получает его при открытии
 * и после каждого хода. Ход считает сервер; клиент только просит и рисует.
 * Отметку армрестлинга клиент ведёт сам — от тика начала по часам своего
 * мира, — и потому снимку хватает начала, а не каждого тика.
 *
 * @param village   чья деревня
 * @param rival     опознаватель соперника
 * @param rivalName имя соперника
 * @param rivalTitle ключ имени его ремесла у народа; пусто — ремесла нет
 * @param nature    нрав соперника: {@code villagepax.nature.<id>}
 * @param won       сколько раз игрок у него выиграл
 * @param lost      сколько раз проиграл
 * @param coins     на монеты ли: в своей колонии — на интерес
 * @param purse     сколько осталось в кошельке соперника на этот вечер
 * @param stakes    какие ставки можно выбрать; у колонии — одна, нулевая
 * @param bout      партия, если идёт или только что кончилась
 */
public record GameView(UUID village, UUID rival, String rivalName, Optional<String> rivalTitle,
                       String nature, int won, int lost, boolean coins, int purse,
                       List<Integer> stakes, Optional<BoutLine> bout) {

    /**
     * Партия глазами игрока.
     *
     * @param kind        во что играют: {@code dice} или {@code arm}
     * @param stake       ставка
     * @param phase       у костей {@code player}, {@code rival}, {@code done};
     *                    у армрестлинга {@code running}, {@code done}
     * @param mine        кости игрока
     * @param theirs      кости соперника
     * @param balance     перевес армрестлинга: +100 — рука соперника легла
     * @param markerStart тик мира, с которого бежит отметка
     * @param zone        доля шкалы под зелёным
     * @param outcome     итог: {@code win}, {@code lose}, {@code push}, {@code draw}; пусто — идёт
     * @param paid        сколько медяков по итогу: выиграно (+) или проиграно (−)
     */
    public record BoutLine(String kind, int stake, String phase, List<Integer> mine,
                           List<Integer> theirs, int balance, long markerStart, double zone,
                           Optional<String> outcome, int paid) {

        public static final Codec<BoutLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(BoutLine::kind),
                Codec.INT.fieldOf("stake").forGetter(BoutLine::stake),
                Codec.STRING.fieldOf("phase").forGetter(BoutLine::phase),
                Codec.INT.listOf().fieldOf("mine").forGetter(BoutLine::mine),
                Codec.INT.listOf().fieldOf("theirs").forGetter(BoutLine::theirs),
                Codec.INT.fieldOf("balance").forGetter(BoutLine::balance),
                Codec.LONG.fieldOf("marker_start").forGetter(BoutLine::markerStart),
                Codec.DOUBLE.fieldOf("zone").forGetter(BoutLine::zone),
                StrictCodecs.optional("outcome", Codec.STRING).forGetter(BoutLine::outcome),
                Codec.INT.fieldOf("paid").forGetter(BoutLine::paid)
        ).apply(instance, BoutLine::new));
    }

    public static final Codec<GameView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("village").forGetter(GameView::village),
            Uuids.STRING_CODEC.fieldOf("rival").forGetter(GameView::rival),
            Codec.STRING.fieldOf("rival_name").forGetter(GameView::rivalName),
            StrictCodecs.optional("rival_title", Codec.STRING).forGetter(GameView::rivalTitle),
            Codec.STRING.fieldOf("nature").forGetter(GameView::nature),
            Codec.INT.fieldOf("won").forGetter(GameView::won),
            Codec.INT.fieldOf("lost").forGetter(GameView::lost),
            Codec.BOOL.fieldOf("coins").forGetter(GameView::coins),
            Codec.INT.fieldOf("purse").forGetter(GameView::purse),
            Codec.INT.listOf().fieldOf("stakes").forGetter(GameView::stakes),
            StrictCodecs.optional("bout", BoutLine.CODEC).forGetter(GameView::bout)
    ).apply(instance, GameView::new));
}
