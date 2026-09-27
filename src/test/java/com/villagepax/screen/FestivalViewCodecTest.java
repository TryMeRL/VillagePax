package com.villagepax.screen;

import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Снимок экрана затейника проходит кодек туда и обратно без потерь.
 * <p>
 * Снимок — единственное, что клиент знает о празднике: потерянное поле
 * значило бы кнопку «Начать» у закрытого состязания или цену без товара.
 */
class FestivalViewCodecTest {

    @Test
    void aViewSurvivesTheWire() {
        FestivalView view = new FestivalView(
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "Бовуар", "Жан",
                "villagepax.title.norman.entertainer", "villagepax.festival.norman", 3,
                Optional.of("villagepax.festival.reason.not_today"),
                List.of(new FestivalView.ContestLine("villagepax.contest.norman.chase", "chase", true,
                                Optional.empty()),
                        new FestivalView.ContestLine("villagepax.contest.norman.hunt", "hunt", false,
                                Optional.of("villagepax.contest.reason.busy"))),
                5, true,
                List.of(new FestivalView.PrizeLine(new Identifier("villagepax", "norman_wreath"), 1, 8, false),
                        new FestivalView.PrizeLine(new Identifier("minecraft", "firework_rocket"), 3, 1, true)),
                Optional.of("villagepax.contest.norman.archery"));

        FestivalView back = FestivalView.CODEC.parse(JsonOps.INSTANCE,
                FestivalView.CODEC.encodeStart(JsonOps.INSTANCE, view).result().orElseThrow())
                .result().orElseThrow();
        assertEquals(view, back);
    }

    /** Пустые необязательные поля — пустые и после кодека, а не «отсутствие ошибки». */
    @Test
    void anOpenFestivalHasNoReason() {
        FestivalView view = new FestivalView(UUID.randomUUID(), "Колония", "Ода", "villagepax.profession.entertainer",
                "villagepax.festival.maya", 0, Optional.empty(), List.of(), 0, false, List.of(), Optional.empty());
        FestivalView back = FestivalView.CODEC.parse(JsonOps.INSTANCE,
                FestivalView.CODEC.encodeStart(JsonOps.INSTANCE, view).result().orElseThrow())
                .result().orElseThrow();
        assertEquals(view, back);
    }
}
