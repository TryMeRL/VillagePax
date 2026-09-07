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

class WarehouseTest {

    private static final Identifier LOG = new Identifier("minecraft", "oak_log");
    private static final Identifier STONE = new Identifier("minecraft", "cobblestone");

    @Test
    void newWarehouseIsEmpty() {
        Warehouse warehouse = new Warehouse();

        assertTrue(warehouse.isEmpty());
        assertEquals(0, warehouse.count(LOG));
        assertFalse(warehouse.has(LOG, 1));
        assertTrue(warehouse.has(LOG, 0), "нулевого количества хватает всегда");
    }

    @Test
    void addAccumulates() {
        Warehouse warehouse = new Warehouse();
        warehouse.add(LOG, 30);
        warehouse.add(LOG, 34);
        warehouse.add(STONE, 8);

        assertEquals(64, warehouse.count(LOG));
        assertEquals(8, warehouse.count(STONE));
        assertEquals(72, warehouse.total());
    }

    /**
     * Главное правило склада: выдача «всё или ничего». Частичная выдача была бы
     * хуже отказа — билдер получил бы половину нужного, записать это некуда,
     * и материалы просто исчезли бы.
     */
    @Test
    void takeIsAllOrNothing() {
        Warehouse warehouse = new Warehouse();
        warehouse.add(LOG, 10);

        assertFalse(warehouse.take(LOG, 11));
        assertEquals(10, warehouse.count(LOG), "отказ не должен тронуть склад");

        assertTrue(warehouse.take(LOG, 4));
        assertEquals(6, warehouse.count(LOG));
    }

    @Test
    void exhaustedItemLeavesNoEntry() {
        Warehouse warehouse = new Warehouse();
        warehouse.add(LOG, 3);

        assertTrue(warehouse.take(LOG, 3));
        assertTrue(warehouse.isEmpty(), "пустые записи не храним, иначе склад раздувается в NBT");
        assertEquals(Map.of(), warehouse.contents());
    }

    @Test
    void negativeAmountsAreRejected() {
        Warehouse warehouse = new Warehouse();

        assertThrows(IllegalArgumentException.class, () -> warehouse.add(LOG, -1));
        assertThrows(IllegalArgumentException.class, () -> warehouse.take(LOG, -1));
    }

    @Test
    void contentsAreFrozen() {
        Warehouse warehouse = new Warehouse();
        warehouse.add(LOG, 1);

        assertThrows(UnsupportedOperationException.class, () -> warehouse.contents().put(STONE, 5));
    }

    @Test
    void surviveRoundTrip() {
        Warehouse warehouse = new Warehouse();
        warehouse.add(LOG, 64);
        warehouse.add(STONE, 17);

        Warehouse restored = roundTrip(warehouse);

        assertEquals(64, restored.count(LOG));
        assertEquals(17, restored.count(STONE));
        assertEquals(warehouse.contents(), restored.contents());
    }

    @Test
    void emptyWarehouseSurvivesRoundTrip() {
        assertTrue(roundTrip(new Warehouse()).isEmpty());
    }

    /** Мусорные значения из правленого сохранения не должны становиться долгом склада. */
    @Test
    void nonPositiveCountsAreDroppedOnLoad() {
        Warehouse warehouse = new Warehouse(Map.of(LOG, 0, STONE, -5));

        assertTrue(warehouse.isEmpty());
    }

    private static Warehouse roundTrip(Warehouse warehouse) {
        NbtElement encoded = Warehouse.CODEC.encodeStart(NbtOps.INSTANCE, warehouse).result().orElseThrow();
        DataResult<Warehouse> decoded = Warehouse.CODEC.parse(NbtOps.INSTANCE, encoded);
        return decoded.result().orElseThrow(() -> new AssertionError(
                "склад не раскодировался: " + decoded.error().map(Object::toString).orElse("?")));
    }
}
