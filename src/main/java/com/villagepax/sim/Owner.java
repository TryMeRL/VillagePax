package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.util.Optional;
import java.util.UUID;

/**
 * Кто принимает решения в поселении — это единственное, чем колония игрока
 * отличается от деревни народа. Всё остальное у них общее.
 */
public record Owner(Optional<UUID> player) {

    public static final Owner AUTONOMOUS = new Owner(Optional.empty());

    public static final Codec<Owner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.optionalFieldOf("player").forGetter(Owner::player)
    ).apply(instance, Owner::new));

    public static Owner of(UUID player) {
        return new Owner(Optional.of(player));
    }

    public boolean isAutonomous() {
        return player.isEmpty();
    }

    public boolean isOwnedBy(UUID candidate) {
        return player.isPresent() && player.get().equals(candidate);
    }
}
