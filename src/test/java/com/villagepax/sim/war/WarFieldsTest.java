package com.villagepax.sim.war;

import com.villagepax.core.war.WarParty;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Каждая правка военного положения трогает своё поле — и только его.
 * <p>
 * Найдено чтением: семь правок собирали положение заново и теряли поход.
 * Колонии, чья стража ушла на деревню, хватало чужого набега у своих
 * ворот или павшего в походе бойца — и запись «в походе» исчезала, а
 * стража у чужих стен снова получала решения «патрулировать дом».
 */
class WarFieldsTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    private static Settlement atWar(UUID ally, UUID lord) {
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Проверка",
                new BlockPos(0, 64, 0));
        colony.makeAlly(ally, 3);
        colony.startTribute(lord, 40);
        colony.beaten(5);
        colony.restFor(6, 2);
        colony.besiege(party(4), 7);
        // Последним: до исправления любая правка выше стёрла бы его.
        colony.marchOn(UUID.randomUUID());
        return colony;
    }

    private static WarParty party(int fighters) {
        return new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN, new BlockPos(10, 64, 10),
                fighters, 8, 9);
    }

    @Test
    void everyEditKeepsTheOtherFields() {
        UUID ally = UUID.randomUUID();
        UUID lord = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Map<String, Consumer<Settlement>> edits = Map.of(
                "restFor", colony -> colony.restFor(10, 4),
                "besiege", colony -> colony.besiege(party(2), 11),
                "updateSiege", colony -> colony.updateSiege(party(1)),
                "liftSiege", Settlement::liftSiege,
                "beaten", colony -> colony.beaten(12),
                "makeAlly", colony -> colony.makeAlly(stranger, 13),
                "breakAlly", colony -> {
                    colony.makeAlly(stranger, 13);
                    colony.breakAlly(stranger);
                });

        for (Map.Entry<String, Consumer<Settlement>> edit : edits.entrySet()) {
            Settlement colony = atWar(ally, lord);
            Settlement.War before = colony.war();
            org.junit.jupiter.api.Assertions.assertTrue(before.marchingOn().isPresent());
            edit.getValue().accept(colony);
            Settlement.War after = colony.war();

            String name = edit.getKey();
            assertEquals(before.marchingOn(), after.marchingOn(), name + " потерял поход");
            assertEquals(before.tributeTo(), after.tributeTo(), name + " потерял дань");
            assertEquals(before.tributeUntil(), after.tributeUntil(), name + " потерял срок дани");
            assertEquals(before.allies().get(ally), after.allies().get(ally), name + " потерял союз");
            if (!name.equals("beaten")) {
                assertEquals(before.beatenOn(), after.beatenOn(), name + " переписал день разгрома");
            }
            if (!name.equals("restFor")) {
                assertEquals(before.truceUntil(), after.truceUntil(), name + " переписал перемирие");
            }
            if (!name.equals("besiege")) {
                assertEquals(before.lastRaid(), after.lastRaid(), name + " переписал день набега");
            }
        }
    }

    @Test
    void marchingSurvivesARaidOnTheColony() {
        Settlement colony = atWar(UUID.randomUUID(), UUID.randomUUID());
        Optional<UUID> target = colony.marchingOn();
        colony.besiege(party(3), 20);
        colony.liftSiege();
        assertEquals(target, colony.marchingOn());
    }
}
