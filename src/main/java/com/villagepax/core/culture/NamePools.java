package com.villagepax.core.culture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Списки имён культуры. Пустой список допустим — тогда используется запасное имя,
 * чтобы недописанный датапак не ронял генерацию поселения.
 */
/**
 * Имена народа — и образец отчества.
 * <p>
 * Отчество лежит здесь, а не в коде, по той же причине, что и сами
 * имена: «fils de Rollo» норманна и «u-mehen Kan» майя — это примета
 * культуры, такая же как цвет стен. Народ, не назвавший образца,
 * обходится без отчества вовсе, и это законно: не у всех оно есть.
 * <p>
 * Образец — со строкой {@code %s} на месте имени отца. Переводить его
 * незачем: имена в этом моде не переводятся, они и есть народ.
 */
public record NamePools(List<String> male, List<String> female, List<String> settlement,
                        String sonOf, String daughterOf) {

    /** Именник на три списка, без отчеств: так его писали до этой правки. */
    public NamePools(List<String> male, List<String> female, List<String> settlement) {
        this(male, female, settlement, "", "");
    }

    public static final Codec<NamePools> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("male", List.of()).forGetter(NamePools::male),
            Codec.STRING.listOf().optionalFieldOf("female", List.of()).forGetter(NamePools::female),
            Codec.STRING.listOf().optionalFieldOf("settlement", List.of()).forGetter(NamePools::settlement)
    ,
            Codec.STRING.optionalFieldOf("son_of", "").forGetter(NamePools::sonOf),
            Codec.STRING.optionalFieldOf("daughter_of", "").forGetter(NamePools::daughterOf)
    ).apply(instance, NamePools::new));

    public boolean isEmpty() {
        return male.isEmpty() && female.isEmpty() && settlement.isEmpty();
    }
}
