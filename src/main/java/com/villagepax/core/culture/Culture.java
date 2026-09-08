package com.villagepax.core.culture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Народ целиком описывается данными — это несущее решение всего мода.
 * Новая культура добавляется файлом в датапаке, Java-кода на неё не нужно.
 *
 * @param displayName        ключ локализации, например {@code villagepax.culture.norman}
 * @param kind               исторический народ, фэнтезийный или биомный
 * @param spawn              где размечать деревни этой культуры
 * @param namePools          имена жителей и поселений
 * @param buildings          доступные типы зданий
 * @param road               чем этот народ мостит улицы, в порядке предпочтения
 * @param traits             модификаторы поведения: подземная застройка, террасные фермы и прочее
 * @param diplomacyDefaults  стартовое отношение к другим народам
 */
public record Culture(
        String displayName,
        CultureKind kind,
        SpawnSettings spawn,
        NamePools namePools,
        List<Identifier> buildings,
        List<Identifier> road,
        List<Identifier> traits,
        Map<Identifier, Integer> diplomacyDefaults
) {

    /**
     * Народ без своей мостовой. Улицы у него всё равно появятся — билдер
     * натопчет тропу, для неё материал не нужен.
     */
    public Culture(String displayName, CultureKind kind, SpawnSettings spawn, NamePools namePools,
                   List<Identifier> buildings, List<Identifier> traits,
                   Map<Identifier, Integer> diplomacyDefaults) {
        this(displayName, kind, spawn, namePools, buildings, List.of(), traits, diplomacyDefaults);
    }

    public static final Codec<Culture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(Culture::displayName),
            CultureKind.CODEC.fieldOf("kind").forGetter(Culture::kind),
            SpawnSettings.CODEC.fieldOf("spawn").forGetter(Culture::spawn),
            NamePools.CODEC.optionalFieldOf("name_pools", new NamePools(List.of(), List.of(), List.of()))
                    .forGetter(Culture::namePools),
            Identifier.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(Culture::buildings),
            Identifier.CODEC.listOf().optionalFieldOf("road", List.of()).forGetter(Culture::road),
            Identifier.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(Culture::traits),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("diplomacy_defaults", Map.of())
                    .forGetter(Culture::diplomacyDefaults)
    ).apply(instance, Culture::new));

    /**
     * Тип здания ратуши у этого народа. Ищется по соглашению об именовании,
     * чтобы датапак не обязан был объявлять его отдельным полем.
     */
    public Optional<Identifier> townHallBuilding() {
        return buildings.stream().filter(id -> id.getPath().endsWith("town_hall")).findFirst();
    }

    /**
     * Стартовое отношение к другому народу. Незнакомый народ считается нейтральным,
     * а не враждебным — вражда должна быть заявлена в данных явно.
     */
    public int initialAttitudeTo(Identifier otherCulture) {
        return diplomacyDefaults.getOrDefault(otherCulture, 0);
    }
}
