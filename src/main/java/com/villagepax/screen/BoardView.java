package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Доска заданий деревни глазами одного игрока: по листку на ремесло.
 * <p>
 * Листок — то же, что игрок услышал бы от жителя: просьба, сколько уже
 * при себе, награда и готовность. Считает сервер тем же правилом, что
 * и разговор ({@link QuestNet}), — доска не вторая система заданий,
 * а второе место, где их видно разом.
 *
 * @param village    чья доска
 * @param name       название деревни — в шапке
 * @param board      где доска: сдают у неё, а не у жителя
 * @param standing   ключ ступени доверия
 * @param reputation доверие числом
 * @param sheets     листки, старейшина первым
 */
public record BoardView(UUID village, String name, BlockPos board, String standing,
                        int reputation, List<Sheet> sheets) {

    /**
     * Один листок.
     *
     * @param giver     ремесло, от которого просьба: по нему и сдают
     * @param author    имя жителя, повесившего листок
     * @param title     ключ имени ремесла у этого народа; пусто — общее имя
     * @param offer     сама просьба
     * @param trusted   хватает ли доверия, чтобы с игроком об этом говорили
     * @param taken     листок этой просьбы уже сорван и лежит у игрока в сумке
     */
    public record Sheet(Identifier giver, String author, Optional<String> title,
                        QuestView.Offer offer, boolean trusted, boolean taken) {

        public static final Codec<Sheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("giver").forGetter(Sheet::giver),
                Codec.STRING.fieldOf("author").forGetter(Sheet::author),
                Codec.STRING.optionalFieldOf("title").forGetter(Sheet::title),
                QuestView.Offer.CODEC.fieldOf("offer").forGetter(Sheet::offer),
                Codec.BOOL.optionalFieldOf("trusted", true).forGetter(Sheet::trusted),
                Codec.BOOL.optionalFieldOf("taken", false).forGetter(Sheet::taken)
        ).apply(instance, Sheet::new));
    }

    public static final Codec<BoardView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("village").forGetter(BoardView::village),
            Codec.STRING.fieldOf("name").forGetter(BoardView::name),
            BlockPos.CODEC.fieldOf("board").forGetter(BoardView::board),
            Codec.STRING.fieldOf("standing").forGetter(BoardView::standing),
            Codec.INT.fieldOf("reputation").forGetter(BoardView::reputation),
            Sheet.CODEC.listOf().fieldOf("sheets").forGetter(BoardView::sheets)
    ).apply(instance, BoardView::new));
}
