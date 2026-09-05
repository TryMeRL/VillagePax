package com.villagepax.sim;

import com.villagepax.core.culture.Culture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Правила основания колонии.
 * <p>
 * Намеренно отделены от предмета-чертежа и ничего не знают о мире: сюда входят
 * только те проверки, которые можно прогнать без запущенной игры, и потому они
 * покрыты обычными тестами. Проверка грунта требует мира и потому осталась
 * в предмете, её проверяет игровой тест.
 */
public final class Founding {

    public static final String KEY_UNKNOWN_CULTURE = "villagepax.found.unknown_culture";
    public static final String KEY_ALREADY_OWNER = "villagepax.found.already_owner";
    public static final String KEY_TOO_CLOSE = "villagepax.found.too_close";

    /** Запасное имя, если у культуры не заполнен список названий поселений. */
    public static final String FALLBACK_NAME = "Безымянное";

    private Founding() {
    }

    public static FoundingOutcome attempt(SettlementManager manager,
                                          UUID player,
                                          Identifier cultureId,
                                          Culture culture,
                                          BlockPos center,
                                          Random random) {
        if (culture == null) {
            return FoundingOutcome.Refused.of(KEY_UNKNOWN_CULTURE, cultureId.toString());
        }

        Optional<Settlement> existing = colonyOf(manager, player);
        if (existing.isPresent()) {
            return FoundingOutcome.Refused.of(KEY_ALREADY_OWNER, existing.get().name());
        }

        Settlement candidate = Settlement.found(cultureId, Owner.of(player), pickName(culture, random), center);

        Optional<Settlement> conflict = manager.conflictWith(candidate);
        if (conflict.isPresent()) {
            return FoundingOutcome.Refused.of(KEY_TOO_CLOSE, conflict.get().name());
        }

        return new FoundingOutcome.Founded(candidate);
    }

    /**
     * Одна колония на игрока. Ограничение стоит здесь, а не в конфиге, потому что
     * конфига пока нет; когда появится, снимать его надо будет в одном месте.
     */
    public static Optional<Settlement> colonyOf(SettlementManager manager, UUID player) {
        return manager.all().stream()
                .filter(settlement -> settlement.owner().isOwnedBy(player))
                .findFirst();
    }

    public static String pickName(Culture culture, Random random) {
        List<String> pool = culture.namePools().settlement();
        return pool.isEmpty() ? FALLBACK_NAME : pool.get(random.nextInt(pool.size()));
    }
}
