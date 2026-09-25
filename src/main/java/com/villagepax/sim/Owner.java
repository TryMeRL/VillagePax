package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Кто принимает решения в поселении — это единственное, чем колония игрока
 * отличается от деревни народа. Всё остальное у них общее.
 * <p>
 * Рядом с хозяином — <b>доверенные</b>: те, кому он позволил строить
 * и ломать на своей земле. Решений колонии они не принимают — пульт,
 * налог и ремёсла остаются за хозяином, — но и гостями не считаются:
 * защита земли их не держит. Так играют вдвоём на одном сервере, не
 * передавая колонию из рук в руки.
 *
 * @param player  хозяин; пусто у деревни народа
 * @param trusted кому хозяин доверил свою землю
 */
public record Owner(Optional<UUID> player, List<UUID> trusted) {

    public static final Owner AUTONOMOUS = new Owner(Optional.empty());

    public static final Codec<Owner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.optionalFieldOf("player").forGetter(Owner::player),
            Uuids.STRING_CODEC.listOf().optionalFieldOf("trusted", List.of())
                    .forGetter(Owner::trusted)
    ).apply(instance, Owner::new));

    public Owner {
        trusted = List.copyOf(trusted);
    }

    public Owner(Optional<UUID> player) {
        this(player, List.of());
    }

    public static Owner of(UUID player) {
        return new Owner(Optional.of(player));
    }

    public boolean isAutonomous() {
        return player.isEmpty();
    }

    public boolean isOwnedBy(UUID candidate) {
        return player.isPresent() && player.get().equals(candidate);
    }

    /** Вправе ли этот игрок строить и ломать на земле поселения. */
    public boolean mayBuild(UUID candidate) {
        return isOwnedBy(candidate) || trusted.contains(candidate);
    }

    /** Тот же хозяин, и ещё один доверенный. Хозяину себе доверять незачем. */
    public Owner trusting(UUID candidate) {
        if (mayBuild(candidate)) {
            return this;
        }
        List<UUID> more = new ArrayList<>(trusted);
        more.add(candidate);
        return new Owner(player, more);
    }

    /** Тот же хозяин, но без этого доверенного. */
    public Owner distrusting(UUID candidate) {
        if (!trusted.contains(candidate)) {
            return this;
        }
        List<UUID> fewer = new ArrayList<>(trusted);
        fewer.remove(candidate);
        return new Owner(player, fewer);
    }
}
