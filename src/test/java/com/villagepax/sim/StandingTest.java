package com.villagepax.sim;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отношение деревни к игроку — чистая арифметика по порогам, и потому
 * проверяется без запуска игры.
 * <p>
 * Пороги важнее, чем кажется: на «друге» игрок получает чертёж ратуши,
 * то есть <b>вход в мод</b>. Съехавший порог означал бы, что цепочка
 * кончается, а награды нет.
 */
class StandingTest {

    @Test
    void thresholdsFollowTheChain() {
        assertEquals(Standing.STRANGER, Standing.of(0));
        assertEquals(Standing.STRANGER, Standing.of(19));
        assertEquals(Standing.KNOWN, Standing.of(20));
        assertEquals(Standing.KNOWN, Standing.of(44));
        assertEquals(Standing.FRIEND, Standing.of(45));
        assertEquals(Standing.FRIEND, Standing.of(79));
        assertEquals(Standing.HONOURED, Standing.of(80));
        assertEquals(Standing.HONOURED, Standing.of(1_000));
    }

    /**
     * Вражда — отдельная механика, а не «минус знакомство». Отрицательное
     * доверие поэтому остаётся чужаком, а не проваливается ниже.
     */
    @Test
    void hostilityIsNotJustLessTrust() {
        assertEquals(Standing.STRANGER, Standing.of(-1));
        assertEquals(Standing.STRANGER, Standing.of(-500));
    }

    /**
     * Стартовая цепочка обязана доводить до друга: на этом пороге отдают
     * чертёж. Пятьдесят — это 15 + 15 + 20 из трёх квестов.
     */
    @Test
    void thefoundingChainReachesFriend() {
        assertTrue(Standing.of(15 + 15 + 20).ordinal() >= Standing.FRIEND.ordinal(),
                "три квеста стартовой цепочки должны доводить до друга");
    }

    @Test
    void settlementRemembersTrustPerPlayer() {
        Settlement village = Settlement.found(new net.minecraft.util.Identifier("villagepax", "norman"),
                Owner.AUTONOMOUS, "Бовуар", net.minecraft.util.math.BlockPos.ORIGIN);

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(0, village.reputationOf(first));
        assertEquals(Standing.STRANGER, village.standingOf(first));

        village.addReputation(first, 15);
        village.addReputation(first, 15);
        village.addReputation(second, 90);

        assertEquals(30, village.reputationOf(first));
        assertEquals(Standing.KNOWN, village.standingOf(first));
        assertEquals(Standing.HONOURED, village.standingOf(second));

        // Доверие одного игрока не задевает другого — на сервере это важно.
        assertEquals(90, village.reputationOf(second));
    }

    @Test
    void settlementRemembersWhatEachPlayerFinished() {
        Settlement village = Settlement.found(new net.minecraft.util.Identifier("villagepax", "norman"),
                Owner.AUTONOMOUS, "Бовуар", net.minecraft.util.math.BlockPos.ORIGIN);

        UUID player = UUID.randomUUID();
        net.minecraft.util.Identifier quest = new net.minecraft.util.Identifier("villagepax", "norman/founding_1");

        assertTrue(village.questsDone(player).isEmpty());
        village.noteQuestDone(player, quest);

        assertEquals(1, village.questsDone(player).size());
        assertTrue(village.questsDone(player).contains(quest));
        assertTrue(village.questsDone(UUID.randomUUID()).isEmpty());
    }
}
