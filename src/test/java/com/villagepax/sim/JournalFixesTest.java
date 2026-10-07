package com.villagepax.sim;

import com.villagepax.sim.diplomacy.Tribute;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ошибки из журнала, отложенные и исправленные: поручения и дань. */
class JournalFixesTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    @Test
    void yesterdaysErrandsAreForgottenButQuestsAreKept() {
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан", BlockPos.ORIGIN);
        UUID player = UUID.randomUUID();
        Identifier quest = new Identifier("villagepax", "norman/founding_1");
        village.noteQuestDone(player, quest);
        for (long day = 1; day <= 50; day++) {
            village.noteQuestDone(player, new Identifier("villagepax", "errand/farmer_" + day));
            village.noteQuestDone(player, new Identifier("villagepax", "errand/guard_" + day));
        }
        assertTrue(village.questsDone(player).contains(quest), "писаная просьба забылась");
        assertTrue(village.questsDone(player).contains(new Identifier("villagepax", "errand/farmer_50")));
        assertTrue(village.questsDone(player).contains(new Identifier("villagepax", "errand/guard_50")));
        assertEquals(3, village.questsDone(player).size(), village.questsDone(player).toString());
    }

    @Test
    void onlyTheWinnerMayDemandTribute() {
        UUID winner = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан", BlockPos.ORIGIN);
        village.beaten(10, Optional.of(winner));
        Settlement townOfWinner = Settlement.found(NORMAN, Owner.of(winner), "Моя", new BlockPos(500, 64, 0));
        townOfWinner.setLevel(SettlementLevel.TOWN);
        Settlement townOfBystander = Settlement.found(NORMAN, Owner.of(bystander), "Чужая",
                new BlockPos(-500, 64, 0));
        townOfBystander.setLevel(SettlementLevel.TOWN);

        assertEquals(Tribute.Verdict.YES, Tribute.judge(village, townOfWinner, winner, 12));
        assertEquals(Tribute.Verdict.NOT_BEATEN, Tribute.judge(village, townOfBystander, bystander, 12));
        assertFalse(village.beatenBy().isEmpty());
    }
}
