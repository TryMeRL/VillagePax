package com.villagepax.sim;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTallyTest {

    private static final Identifier LOG = new Identifier("minecraft", "oak_log");
    private static final Identifier STONE = new Identifier("minecraft", "cobblestone");

    @Test
    void newTallyIsEmpty() {
        ItemTally tally = new ItemTally();

        assertTrue(tally.isEmpty());
        assertEquals(0, tally.count(LOG));
        assertFalse(tally.has(LOG, 1));
        assertTrue(tally.has(LOG, 0), "нулевого количества хватает всегда");
    }

    @Test
    void addAccumulates() {
        ItemTally tally = new ItemTally();
        tally.add(LOG, 30);
        tally.add(LOG, 34);
        tally.add(STONE, 8);

        assertEquals(64, tally.count(LOG));
        assertEquals(8, tally.count(STONE));
        assertEquals(72, tally.total());
    }

    /**
     * Главное правило запаса: выдача «всё или ничего». Частичная выдача была бы
     * хуже отказа — билдер получил бы половину нужного, записать это некуда,
     * и материалы просто исчезли бы.
     */
    @Test
    void takeIsAllOrNothing() {
        ItemTally tally = new ItemTally();
        tally.add(LOG, 10);

        assertFalse(tally.take(LOG, 11));
        assertEquals(10, tally.count(LOG), "отказ не должен тронуть запас");

        assertTrue(tally.take(LOG, 4));
        assertEquals(6, tally.count(LOG));
    }

    @Test
    void exhaustedItemLeavesNoEntry() {
        ItemTally tally = new ItemTally();
        tally.add(LOG, 3);

        assertTrue(tally.take(LOG, 3));
        assertTrue(tally.isEmpty(), "пустые записи не храним, иначе запас раздувается в NBT");
        assertEquals(Map.of(), tally.contents());
    }

    @Test
    void negativeAmountsAreRejected() {
        ItemTally tally = new ItemTally();

        assertThrows(IllegalArgumentException.class, () -> tally.add(LOG, -1));
        assertThrows(IllegalArgumentException.class, () -> tally.take(LOG, -1));
    }

    @Test
    void contentsAreFrozen() {
        ItemTally tally = new ItemTally();
        tally.add(LOG, 1);

        assertThrows(UnsupportedOperationException.class, () -> tally.contents().put(STONE, 5));
    }

    @Test
    void surviveRoundTrip() {
        ItemTally tally = new ItemTally();
        tally.add(LOG, 64);
        tally.add(STONE, 17);

        ItemTally restored = roundTrip(tally);

        assertEquals(64, restored.count(LOG));
        assertEquals(17, restored.count(STONE));
        assertEquals(tally.contents(), restored.contents());
    }

    @Test
    void emptyItemTallySurvivesRoundTrip() {
        assertTrue(roundTrip(new ItemTally()).isEmpty());
    }

    /** Мусорные значения из правленого сохранения не должны становиться долгом запаса. */
    @Test
    void nonPositiveCountsAreDroppedOnLoad() {
        ItemTally tally = new ItemTally(Map.of(LOG, 0, STONE, -5));

        assertTrue(tally.isEmpty());
    }

    private static ItemTally roundTrip(ItemTally tally) {
        NbtElement encoded = ItemTally.CODEC.encodeStart(NbtOps.INSTANCE, tally).result().orElseThrow();
        DataResult<ItemTally> decoded = ItemTally.CODEC.parse(NbtOps.INSTANCE, encoded);
        return decoded.result().orElseThrow(() -> new AssertionError(
                "запас не раскодировался: " + decoded.error().map(Object::toString).orElse("?")));
    }
}
