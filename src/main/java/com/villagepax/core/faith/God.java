package com.villagepax.core.faith;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Бог — данные, как культура, профессия и тип здания.
 * <p>
 * Пантеон принадлежит <b>народу</b>, а не моду: у норманнов свои трое,
 * у майя свои трое, и чужим богам в чужом храме не молятся. Народ без
 * пантеона — законное умолчание: племя, которое никому не молится, тоже
 * содержание, и придумывать ему богов за автора датапака мод не станет.
 *
 * @param culture     чей это бог
 * @param displayName ключ локализации имени
 * @param domain      что он делает: выбор из закрытого списка {@link Domain}
 * @param offerings   что он принимает и сколько благосклонности за это даёт
 * @param artifact    что он вручает достигшему высшей ступени; пусто — ничего
 */
public record God(Identifier culture, String displayName, Domain domain,
                  Map<Identifier, Integer> offerings, Optional<Identifier> artifact) {

    public God {
        offerings = Map.copyOf(offerings);
    }

    public static final Codec<God> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("culture").forGetter(God::culture),
            Codec.STRING.fieldOf("display_name").forGetter(God::displayName),
            // Домен обязателен, и это то же решение, что с ролью здания.
            // У необязательного поля с умолчанием DFU глотает ошибку
            // вложенного кодека: описка «harvst» молча сделала бы бога
            // богом урожая, и автор пака искал бы, почему его бог войны
            // растит пшеницу.
            Domain.CODEC.fieldOf("domain").forGetter(God::domain),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("offerings")
                    .forGetter(God::offerings),
            Identifier.CODEC.optionalFieldOf("artifact").forGetter(God::artifact)
    ).apply(instance, God::new));

    /**
     * Сколько благосклонности даёт эта вещь. Ноль — не его жертва.
     * <p>
     * Спрашивается по предмету, а не по тегу, и это осознанно: тег сказал бы
     * «любое зерно», но тогда бог урожая принимал бы семена, которые стоят
     * в поле копейки, наравне с хлебом. Вера должна стоить труда, а труд
     * в моде измеряется именно конкретной вещью.
     */
    public int worthOf(Item item) {
        return offerings.getOrDefault(Registries.ITEM.getId(item), 0);
    }

    /** Принимает ли он это вообще. */
    public boolean accepts(Item item) {
        return worthOf(item) > 0;
    }
}
