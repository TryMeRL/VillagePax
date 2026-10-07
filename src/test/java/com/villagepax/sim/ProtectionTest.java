package com.villagepax.sim;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Кто вправе трогать землю колонии.
 */
class ProtectionTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static SettlementManager withColony(Owner owner) {
        SettlementManager manager = new SettlementManager();
        manager.add(Settlement.found(NORMAN, owner, "Бовуар", new BlockPos(0, 64, 0)));
        return manager;
    }

    @Test
    void theStrangerIsStoppedAtTheColonyAndOnlyThere() {
        SettlementManager manager = withColony(Owner.of(OWNER));

        assertTrue(Protection.guardedAgainst(manager, STRANGER, new BlockPos(5, 64, 5)).isPresent());
        // Далеко за границей — ничья земля.
        assertTrue(Protection.guardedAgainst(manager, STRANGER, new BlockPos(500, 64, 500)).isEmpty());
    }

    @Test
    void theOwnerAndTheTrustedBuildFreely() {
        SettlementManager manager = withColony(Owner.of(OWNER).trusting(FRIEND));

        assertTrue(Protection.guardedAgainst(manager, OWNER, new BlockPos(5, 64, 5)).isEmpty());
        assertTrue(Protection.guardedAgainst(manager, FRIEND, new BlockPos(5, 64, 5)).isEmpty());
    }

    /**
     * Выросшие границы делят общие чанки по близости: ближе к ратуше
     * колонии — её земля, и защищена она и там, где деревня в списке раньше.
     */
    @Test
    void sharedLandBelongsToTheNearerCentre() {
        SettlementManager manager = new SettlementManager();
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан", new BlockPos(160, 64, 0));
        village.setLevel(SettlementLevel.CAPITAL);
        manager.add(village);
        Settlement colony = Settlement.found(NORMAN, Owner.of(OWNER), "Бовуар", new BlockPos(0, 64, 0));
        colony.setLevel(SettlementLevel.TOWN);
        manager.add(colony);

        assertTrue(Protection.guardedAgainst(manager, STRANGER, new BlockPos(40, 64, 0)).isPresent(),
                "земля у самой колонии досталась деревне");
        assertTrue(Protection.guardedAgainst(manager, STRANGER, new BlockPos(120, 64, 0)).isEmpty(),
                "земля у самой деревни досталась колонии");
    }

    /** Деревни народов берут набегом, и порчей это не считается. */
    @Test
    void aVillageOfThePeoplesIsNotGuarded() {
        SettlementManager manager = withColony(Owner.AUTONOMOUS);
        assertTrue(Protection.guardedAgainst(manager, STRANGER, new BlockPos(5, 64, 5)).isEmpty());
    }

    @Test
    void trustIsGivenOnceAndTakenBack() {
        Owner owner = Owner.of(OWNER).trusting(FRIEND).trusting(FRIEND);
        assertEquals(List.of(FRIEND), owner.trusted(), "доверие не удваивается");
        assertSame(owner, owner.trusting(OWNER), "хозяину себе доверять незачем");

        Owner after = owner.distrusting(FRIEND);
        assertFalse(after.mayBuild(FRIEND));
        assertTrue(after.mayBuild(OWNER), "хозяина доверие не касается");
    }

    /** Колония из прошлой версии, где доверенных ещё не было, читается как прежде. */
    @Test
    void anOwnerWrittenBeforeTrustStillReads() {
        Owner old = Owner.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"player\": \"" + OWNER + "\"}"))
                .result().orElseThrow();
        assertEquals(Owner.of(OWNER), old);
        assertTrue(old.trusted().isEmpty());
    }

    @Test
    void trustSurvivesSaving() {
        Owner owner = Owner.of(OWNER).trusting(FRIEND);
        Owner back = Owner.CODEC.parse(JsonOps.INSTANCE,
                Owner.CODEC.encodeStart(JsonOps.INSTANCE, owner).result().orElseThrow())
                .result().orElseThrow();
        assertEquals(owner, back);
    }
}
