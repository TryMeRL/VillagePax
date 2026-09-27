package com.villagepax.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Необязательное поле, которое не глотает ошибку.
 * <p>
 * {@code optionalFieldOf} в DFU этой версии снисходителен: поле, которое
 * не прочиталось, он считает отсутствующим. Описка {@code "critter": "goat"}
 * становится «зверька нет», и ловля молча не начинается — ровно так мод
 * уже дважды попадался (см. память проекта). Прежде лечилось тем, что поле
 * делали обязательным; но зверёк нужен только ловле, а вещица только поиску,
 * и обязательными они быть не могут.
 * <p>
 * Здесь правило честное: <b>нет поля — умолчание, есть — читается всерьёз</b>,
 * и ошибка внутри уходит наверх вместе с именем поля.
 */
public final class StrictCodecs {

    private StrictCodecs() {
    }

    /** Поле, которого может не быть; написанное — обязано прочитаться. */
    public static <A> MapCodec<Optional<A>> optional(String name, Codec<A> codec) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                if (value == null) {
                    return DataResult.success(Optional.empty());
                }
                return codec.parse(ops, value)
                        .mapError(error -> "поле «" + name + "»: " + error)
                        .map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops,
                                               RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, codec.encodeStart(ops, input.get()))
                        : prefix;
            }

            @Override
            public String toString() {
                return "StrictOptional[" + name + " " + codec + "]";
            }
        };
    }

    /** То же, но с умолчанием вместо пустоты. */
    public static <A> MapCodec<A> optional(String name, Codec<A> codec, A fallback) {
        return optional(name, codec).xmap(value -> value.orElse(fallback), Optional::of);
    }
}
