package com.villagepax.core.profession;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

/**
 * Профессия жителя — данные, а не код.
 * <p>
 * Дизайн-документ говорит прямо: профессия — это данные плюс {@code JobLogic}
 * в коде. Файл профессии <b>выбирает</b> логику по имени, а не приносит свою:
 * логик семь на весь мод, и они всегда были кодом. Иначе пришлось бы пускать
 * в мод чужой исполняемый код.
 *
 * @param displayName    ключ локализации
 * @param job            логика работы: {@code villagepax:gather}, {@code villagepax:haul} и прочие
 * @param needsWorkplace нужна ли жителю мастерская, чтобы взяться за дело
 * @param hiringPriority кого нанимать первым: больше — нужнее
 */
public record Profession(String displayName, Identifier job, boolean needsWorkplace,
                         int hiringPriority) {

    public static final Codec<Profession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(Profession::displayName),
            Identifier.CODEC.fieldOf("job").forGetter(Profession::job),
            Codec.BOOL.optionalFieldOf("needs_workplace", false).forGetter(Profession::needsWorkplace),
            Codec.INT.optionalFieldOf("hiring_priority", 0).forGetter(Profession::hiringPriority)
    ).apply(instance, Profession::new));
}
