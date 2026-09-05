package com.villagepax.core.culture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Списки имён культуры. Пустой список допустим — тогда используется запасное имя,
 * чтобы недописанный датапак не ронял генерацию поселения.
 */
public record NamePools(List<String> male, List<String> female, List<String> settlement) {

    public static final Codec<NamePools> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("male", List.of()).forGetter(NamePools::male),
            Codec.STRING.listOf().optionalFieldOf("female", List.of()).forGetter(NamePools::female),
            Codec.STRING.listOf().optionalFieldOf("settlement", List.of()).forGetter(NamePools::settlement)
    ).apply(instance, NamePools::new));

    public boolean isEmpty() {
        return male.isEmpty() && female.isEmpty() && settlement.isEmpty();
    }
}
