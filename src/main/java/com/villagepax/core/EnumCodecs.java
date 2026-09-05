package com.villagepax.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Кодеки для перечислений мода.
 * <p>
 * Собраны вручную, а не через ванильный {@code StringIdentifiable.createCodec}:
 * тот помечен deprecated, а главное — при опечатке в датапаке даёт невнятную
 * ошибку. Здесь автор пака сразу видит и что написал, и что было можно.
 */
public final class EnumCodecs {

    private EnumCodecs() {
    }

    public static <E extends Enum<E> & Named> Codec<E> of(E[] values, String what) {
        String allowed = Arrays.stream(values).map(Named::id).collect(Collectors.joining(", "));

        return Codec.STRING.comapFlatMap(
                name -> {
                    for (E value : values) {
                        if (value.id().equals(name)) {
                            return DataResult.success(value);
                        }
                    }
                    return DataResult.error(() -> "Неизвестное значение (" + what + "): " + name
                            + ". Допустимые: " + allowed);
                },
                Named::id);
    }
}
