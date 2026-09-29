package com.villagepax.screen;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Снимок окна игры проходит кодек туда и обратно без потерь.
 * <p>
 * Снимок — всё, что клиент знает о партии: потерянное поле значило бы
 * кнопку «Бросить» у соперника в ходу или ставку, которой нет.
 */
class GameViewCodecTest {

    private static GameView roundTrip(GameView view) {
        return GameView.CODEC.parse(JsonOps.INSTANCE,
                GameView.CODEC.encodeStart(JsonOps.INSTANCE, view).result().orElseThrow())
                .result().orElseThrow();
    }

    @Test
    void aViewSurvivesTheWire() {
        GameView view = new GameView(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                UUID.fromString("11111111-2222-3333-4444-555555555555"), "Гийом",
                Optional.of("villagepax.profession.farmer"), "ambitious", 2, 1, true, 4, List.of(1, 2),
                Optional.of(new GameView.BoutLine("dice", 2, "rival", List.of(6, 5, 4), List.of(5, 6),
                        0, 0L, 0.0, Optional.empty(), 0)));
        assertEquals(view, roundTrip(view));
    }

    /** Окно без партии и без ремесла — пусто и после кодека, а не «отсутствие ошибки». */
    @Test
    void aViewWithoutABoutStaysEmpty() {
        GameView view = new GameView(UUID.randomUUID(), UUID.randomUUID(), "Ода", Optional.empty(),
                "even", 0, 0, false, 0, List.of(0), Optional.empty());
        assertEquals(view, roundTrip(view));
    }

    @Test
    void anArmBoutKeepsItsClock() {
        GameView view = new GameView(UUID.randomUUID(), UUID.randomUUID(), "Рено",
                Optional.of("villagepax.profession.builder"), "lazy", 0, 3, true, 3, List.of(1),
                Optional.of(new GameView.BoutLine("arm", 1, "done", List.of(), List.of(), 55,
                        123_456L, 0.18, Optional.of("win"), 1)));
        assertEquals(view, roundTrip(view));
    }
}
