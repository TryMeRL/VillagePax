package com.villagepax.sim;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ограничения показателей жителя.
 * <p>
 * Через конструктор идёт декодирование из NBT, поэтому ограничения обязаны
 * стоять и в нём. Пока они были только в сеттерах, житель с happiness = 250
 * из правленого руками или мигрированного сохранения навсегда оставался вне
 * диапазона, и {@code isUnhappy} у него всегда возвращал ложь.
 */
class CitizenTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    @Test
    void constructorClampsHappinessAndHealth() {
        Citizen tooHappy = citizen(250, 500.0f);

        assertEquals(SettlementStats.MAX_HAPPINESS, tooHappy.happiness());
        assertEquals(Citizen.MAX_HEALTH, tooHappy.health(), 0.0f);
    }

    @Test
    void constructorClampsNegativeValues() {
        Citizen broken = citizen(-40, -3.0f);

        assertEquals(0, broken.happiness());
        assertEquals(0.0f, broken.health(), 0.0f);
        assertTrue(broken.isUnhappy());
    }

    /** Настоящий путь, на котором это и проявлялось бы: чтение из сохранения. */
    @Test
    void decodingFromNbtClampsToo() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", UUID.randomUUID().toString());
        nbt.putString("first_name", "Rollo");
        nbt.putString("culture", NORMAN.toString());
        nbt.putString("gender", "male");
        nbt.putInt("happiness", 250);
        nbt.putFloat("health", 999.0f);
        nbt.putInt("saturation", -5);

        DataResult<Citizen> decoded = Citizen.CODEC.parse(NbtOps.INSTANCE, nbt);
        Citizen citizen = decoded.result().orElseThrow(() -> new AssertionError(
                "житель не раскодировался: " + decoded.error().map(Object::toString).orElse("?")));

        assertEquals(SettlementStats.MAX_HAPPINESS, citizen.happiness());
        assertEquals(Citizen.MAX_HEALTH, citizen.health(), 0.0f);
        assertEquals(0, citizen.saturation());
        assertFalse(citizen.isUnhappy());
    }

    /**
     * Ограничение не должно менять правильные данные: иначе круговой прогон
     * сохранения перестал бы быть круговым.
     */
    @Test
    void validValuesPassThroughUnchanged() {
        Citizen citizen = citizen(64, 12.5f);

        assertEquals(64, citizen.happiness());
        assertEquals(12.5f, citizen.health(), 0.0f);

        NbtElement encoded = Citizen.CODEC.encodeStart(NbtOps.INSTANCE, citizen).result().orElseThrow();
        Citizen restored = Citizen.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(citizen.happiness(), restored.happiness());
        assertEquals(citizen.health(), restored.health(), 0.0f);
    }

    private static Citizen citizen(int happiness, float health) {
        return new Citizen(UUID.randomUUID(), "Rollo", "de Bayeux", NORMAN, Gender.MALE,
                Citizen.Life.UNKNOWN,
                Optional.empty(), happiness, 20, Optional.empty(), Optional.empty(), Optional.empty(),
                health);
    }
}
