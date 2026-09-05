package com.villagepax.core.culture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Где и как часто в мире размечаются места под деревни этой культуры.
 *
 * @param biomes              тег или идентификатор биома, например {@code #minecraft:is_forest}
 * @param weight              вес при выборе культуры для места
 * @param minDistanceChunks   минимальное расстояние до ближайшего другого поселения
 */
public record SpawnSettings(String biomes, int weight, int minDistanceChunks) {

    public static final Codec<SpawnSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("biomes").forGetter(SpawnSettings::biomes),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(SpawnSettings::weight),
            Codec.INT.optionalFieldOf("min_distance_chunks", 48).forGetter(SpawnSettings::minDistanceChunks)
    ).apply(instance, SpawnSettings::new));
}
