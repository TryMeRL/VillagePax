package com.villagepax.core.profession;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.sim.SettlementLevel;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Профессия жителя — данные, а не код.
 * <p>
 * Дизайн-документ говорит прямо: профессия — это данные плюс {@code JobLogic}
 * в коде. Файл профессии <b>выбирает</b> логику по имени, а не приносит свою:
 * логик восемь на весь мод, и они всегда были кодом. Иначе пришлось бы пускать
 * в мод чужой исполняемый код.
 *
 * @param displayName    ключ локализации
 * @param job            логика работы: {@code villagepax:gather}, {@code villagepax:haul} и прочие
 * @param needsWorkplace нужна ли жителю мастерская, чтобы взяться за дело
 * @param hiringPriority кого нанимать первым: больше — нужнее
 * @param workplace      окончание типа здания, в котором эта профессия работает;
 *                       пусто — совпадает с именем самой профессии
 * @param minLevel       с какой ступени колонии это ремесло вообще доступно
 */
public record Profession(String displayName, Identifier job, boolean needsWorkplace,
                         int hiringPriority, Optional<String> workplace,
                         SettlementLevel minLevel) {

    public static final Codec<Profession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(Profession::displayName),
            Identifier.CODEC.fieldOf("job").forGetter(Profession::job),
            Codec.BOOL.optionalFieldOf("needs_workplace", false).forGetter(Profession::needsWorkplace),
            Codec.INT.optionalFieldOf("hiring_priority", 0).forGetter(Profession::hiringPriority),
            Codec.STRING.optionalFieldOf("workplace").forGetter(Profession::workplace),
            // Умолчание — хутор: молчание датапака означает «ремесло
            // доступно сразу», и это безопасно. Обратное умолчание
            // заперло бы пахаря в только что основанной колонии.
            SettlementLevel.CODEC.optionalFieldOf("min_level", SettlementLevel.HAMLET)
                    .forGetter(Profession::minLevel)
    ).apply(instance, Profession::new));

    /**
     * Открыто ли это ремесло колонии такой ступени.
     * <p>
     * Ступень — это не украшение счётчика, а <b>ключ к новому делу</b>.
     * До сих пор уровень колонии давал только предел населения и границы,
     * то есть больше того же самого; расти было незачем. Теперь за ступень
     * колония получает ремесло, которого раньше не было, — и это ответ
     * на «играть скучно»: у роста появилась цель, которую видно.
     */
    public boolean openTo(SettlementLevel level) {
        return level.ordinal() >= minLevel.ordinal();
    }

    /**
     * В каком здании работает эта профессия.
     * <p>
     * Поле нужно потому, что имена не всегда совпадают: фермер работает
     * на <b>ферме</b>, а не на «фермере». Совпадающие имена по-прежнему
     * писать не нужно.
     */
    public String workplaceOf(Identifier professionId) {
        return workplace.orElse(professionId.getPath());
    }
}
