package com.villagepax.core.culture;

import com.mojang.serialization.DataResult;
import net.minecraft.util.StringIdentifiable;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Род культуры. На геймплей влияет мало, но задаёт ожидания по арту
 * и позволяет фильтровать народы в настройках мира.
 */
public enum CultureKind implements StringIdentifiable {
    HISTORICAL("historical"),
    FANTASY("fantasy"),
    BIOME("biome");

    /**
     * Тип кодека выписан полностью не для красоты: у StringIdentifiable есть
     * вложенный тип с именем Codec, и внутри этого перечисления короткое имя
     * разрешается в него, а не в мозанговский.
     * <p>
     * Сам кодек собран вручную вместо StringIdentifiable.createCodec: тот помечен
     * deprecated и при опечатке в датапаке даёт невнятную ошибку. Здесь автор пака
     * сразу видит и что написал, и что было можно.
     */
    public static final com.mojang.serialization.Codec<CultureKind> CODEC =
            com.mojang.serialization.Codec.STRING.comapFlatMap(
                    CultureKind::byId,
                    CultureKind::asString);

    private final String id;

    CultureKind(String id) {
        this.id = id;
    }

    private static DataResult<CultureKind> byId(String name) {
        for (CultureKind kind : values()) {
            if (kind.id.equals(name)) {
                return DataResult.success(kind);
            }
        }
        String allowed = Arrays.stream(values())
                .map(CultureKind::asString)
                .collect(Collectors.joining(", "));
        return DataResult.error(() -> "Неизвестный род культуры: " + name + ". Допустимые: " + allowed);
    }

    @Override
    public String asString() {
        return id;
    }
}
