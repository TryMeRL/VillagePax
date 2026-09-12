package com.villagepax.core.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Отряд, вышедший наказать игрока.
 * <p>
 * Дизайн-документ: «формируются {@code WarParty} — отряды с целью; отряд —
 * это данные, энтити спавнятся при подходе к загруженной зоне». Здесь ровно
 * это, и по той же причине, по которой обоз в пути — запись: между деревней
 * и колонией лежат сотни незагруженных чанков, и вести по ним живых мобов
 * нельзя ни дёшево, ни честно.
 * <p>
 * <b>Причина у набега одна, и она в руках игрока.</b> Деревня посылает людей
 * не по броску кубика, а когда доверие к игроку упало ниже всякого терпения:
 * ограбленные обозы, убитые жители. Это и есть «агрессия игрока» из списка
 * причин войны — та единственная, которую мод уже умеет считать. Спор за
 * границу и требование дани приедут вместе с дипломатическими действиями.
 * <p>
 * Хранится <b>у осаждаемого</b>, как и обоз хранится у принимающего:
 * спрашивают об этом здесь — «кто у моих ворот».
 *
 * @param id        опознаватель: по нему тела находят свой отряд
 * @param home      деревня, которая его послала
 * @param culture   её народ — на случай, если деревню снесли
 * @param musters   где отряд собирается: там и появляются тела
 * @param fighters  сколько бойцов ещё живо
 * @param wrecked   сколько зданий уже разорено: по одному на бойца
 * @param arrivesOn день, в который они придут: о набеге предупреждают заранее
 * @param leavesOn  день, в который уйдут ни с чем
 */
public record WarParty(UUID id, UUID home, Identifier culture, BlockPos musters,
                       int fighters, int wrecked, long arrivesOn, long leavesOn) {

    /** Отряд, ещё ничего не разоривший. */
    public WarParty(UUID id, UUID home, Identifier culture, BlockPos musters,
                    int fighters, long arrivesOn, long leavesOn) {
        this(id, home, culture, musters, fighters, 0, arrivesOn, leavesOn);
    }

    public static final Codec<WarParty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(WarParty::id),
            Uuids.STRING_CODEC.fieldOf("home").forGetter(WarParty::home),
            Identifier.CODEC.fieldOf("culture").forGetter(WarParty::culture),
            BlockPos.CODEC.fieldOf("musters").forGetter(WarParty::musters),
            Codec.INT.optionalFieldOf("fighters", 0).forGetter(WarParty::fighters),
            Codec.INT.optionalFieldOf("wrecked", 0).forGetter(WarParty::wrecked),
            Codec.LONG.fieldOf("arrives_on").forGetter(WarParty::arrivesOn),
            Codec.LONG.fieldOf("leaves_on").forGetter(WarParty::leavesOn)
    ).apply(instance, WarParty::new));

    /** Тот же отряд, поредевший. Заменой, а не правкой: запись неизменяема. */
    public WarParty withFighters(int left) {
        return new WarParty(id, home, culture, musters, Math.max(0, left), wrecked,
                arrivesOn, leavesOn);
    }

    /** Тот же отряд, разоривший ещё один дом. */
    public WarParty withWrecked(int done) {
        return new WarParty(id, home, culture, musters, fighters, done, arrivesOn, leavesOn);
    }

    /**
     * Есть ли ещё кому разорять.
     * <p>
     * По дому на живого бойца — и счёт живых падает вместе с ними. Отсюда
     * простая цена спешки: чем быстрее игрок перебьёт пришедших, тем
     * меньше домов будет разорено.
     */
    public boolean canWreck() {
        return wrecked < fighters;
    }

    /** Пришли ли уже. До этого дня отряд — только предупреждение в чате. */
    public boolean hasArrived(long today) {
        return today >= arrivesOn;
    }

    /** Пора ли уходить: либо срок вышел, либо бить больше некем. */
    public boolean isOver(long today) {
        return fighters <= 0 || today > leavesOn;
    }
}
