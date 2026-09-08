package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Карта колонии для клиента: подписи над зданиями и граница владений.
 * <p>
 * Снимок, а не подписка на изменения: он собирается заново раз в две
 * секунды и рассылается тем, кто рядом. Подписка потребовала бы следить
 * за каждым изменением поселения и рассылать разницу — а колония меняется
 * редко, зато способов забыть уведомление много. Здесь забыть нечего:
 * следующий снимок всё равно приедет, и он всегда верен.
 * <p>
 * Клиент по этому снимку ничего не решает: место подписи считает сервер,
 * потому что размер здания знает схема, а схемы у клиента нет.
 *
 * @param id     опознаватель колонии: снимков может приехать несколько
 * @param name   имя колонии
 * @param centre блок ратуши — от него отмеряется граница
 * @param radius радиус владений в чанках
 * @param signs  подписи зданий
 */
public record ColonyMap(UUID id, String name, BlockPos centre, int radius, List<Sign> signs) {

    /**
     * Подпись над зданием.
     *
     * @param type  тип здания — из него собирается ключ перевода
     * @param level уровень: у второго и дальше он показывается
     * @param at    где висит подпись: сервер уже посчитал середину крыши
     * @param done  готово или ещё строится
     */
    public record Sign(Identifier type, int level, BlockPos at, boolean done) {

        public static final Codec<Sign> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(Sign::type),
                Codec.INT.fieldOf("level").forGetter(Sign::level),
                BlockPos.CODEC.fieldOf("at").forGetter(Sign::at),
                Codec.BOOL.fieldOf("done").forGetter(Sign::done)
        ).apply(instance, Sign::new));
    }

    public static final Codec<ColonyMap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(ColonyMap::id),
            Codec.STRING.fieldOf("name").forGetter(ColonyMap::name),
            BlockPos.CODEC.fieldOf("centre").forGetter(ColonyMap::centre),
            Codec.INT.fieldOf("radius").forGetter(ColonyMap::radius),
            Sign.CODEC.listOf().optionalFieldOf("signs", List.of()).forGetter(ColonyMap::signs)
    ).apply(instance, ColonyMap::new));
}
