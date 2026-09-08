package com.villagepax.sim.work;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.Optional;
import java.util.UUID;

/**
 * Стратегическое состояние работы жителя.
 * <p>
 * Живёт в данных, а не в сущности, и это несущее решение: тело жителя
 * исчезает вместе с чанком, а курьер с полными руками обязан после
 * перезахода в мир донести груз, а не начать путь заново.
 * <p>
 * Тактике — то есть цели на сущности — остаётся ровно одно: «дойди до точки
 * и сообщи, что дошёл». Именно поэтому здесь нет ни пути, ни цели навигации:
 * они пересчитываются при появлении тела и ничего не стоят.
 *
 * @param phase    что житель делает прямо сейчас
 * @param building здание, к которому привязана работа
 * @param carried  груз в руках, если несёт
 */
public record JobState(Phase phase, Optional<UUID> building, Optional<Load> carried) {

    /**
     * Фаза работы. Одно перечисление на все профессии: их будет семь,
     * а фаз столько же и останется.
     * <p>
     * Билдер ходит по {@code IDLE → TO_SITE → WORKING}, курьер по
     * {@code IDLE → TO_STORAGE → TO_SITE}.
     */
    public enum Phase implements Named {

        /** Работы нет: житель свободен. */
        IDLE("idle"),

        /** Идёт к хранилищу за материалами. */
        TO_STORAGE("to_storage"),

        /** Идёт к зданию — работать или сдать груз. */
        TO_SITE("to_site"),

        /** На месте и работает. */
        WORKING("working");

        public static final Codec<Phase> CODEC = EnumCodecs.of(values(), "фаза работы");

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** Нужно ли идти: у этих фаз есть цель в мире. */
        public boolean isTravelling() {
            return this == TO_STORAGE || this == TO_SITE;
        }
    }

    /**
     * Груз в руках. Хранится идентификатором, а не {@code ItemStack}:
     * у последнего сравнение по ссылке, из-за чего запись с ним перестала
     * бы правильно сравниваться, и круговой прогон нечем было бы проверить.
     */
    public record Load(Identifier item, int count) {

        public static final Codec<Load> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Load::item),
                Codec.INT.fieldOf("count").forGetter(Load::count)
        ).apply(instance, Load::new));

        public Load {
            if (count <= 0) {
                throw new IllegalArgumentException("груз из " + count + " штук " + item + " — это не груз");
            }
        }
    }

    public static final JobState IDLE = new JobState(Phase.IDLE, Optional.empty(), Optional.empty());

    public static final Codec<JobState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Phase.CODEC.optionalFieldOf("phase", Phase.IDLE).forGetter(JobState::phase),
            Uuids.STRING_CODEC.optionalFieldOf("building").forGetter(JobState::building),
            Load.CODEC.optionalFieldOf("carried").forGetter(JobState::carried)
    ).apply(instance, JobState::new));

    public JobState {
        if (phase == Phase.IDLE) {
            // Приведение к единственному виду: в простое привязка к зданию
            // бессмысленна, а лишняя ссылка мешала бы понять, что груз
            // нести уже некуда.
            building = Optional.empty();
        } else if (building.isEmpty()) {
            throw new IllegalArgumentException(
                    "фаза " + phase.id() + " без здания: работать не над чем");
        }
    }

    /** Взяться за работу над зданием. */
    public static JobState startAt(UUID building, Phase phase) {
        return new JobState(phase, Optional.of(building), Optional.empty());
    }

    /**
     * Переход в другую фазу.
     * <p>
     * Возврат в простой снимает привязку к зданию, но <b>не</b> груз: иначе
     * материалы у курьера в руках исчезали бы, стоило заданию отмениться.
     * Груз без здания и есть повод вернуть его на склад.
     */
    public JobState withPhase(Phase next) {
        return new JobState(next, building, carried);
    }

    public JobState carrying(Identifier item, int count) {
        return new JobState(phase, building, Optional.of(new Load(item, count)));
    }

    public JobState emptyHanded() {
        return new JobState(phase, building, Optional.empty());
    }

    public boolean isCarrying() {
        return carried.isPresent();
    }

    public boolean isIdle() {
        return phase == Phase.IDLE;
    }

    /**
     * Груз, который житель нёс, но донести уже некуда: здание достроено,
     * снесено или задание отменено. Его надо вернуть на склад, а не потерять.
     * <p>
     * Отдельная проверка нужна и после чтения испорченного сохранения:
     * {@code optionalFieldOf} в DFU глотает ошибку вложенного кодека и молча
     * подставляет значение по умолчанию, так что нечитаемая фаза даёт простой.
     * Груз лежит отдельным полем и потому уцелеет — и вернётся на склад.
     */
    public boolean hasStrandedLoad() {
        return isCarrying() && phase == Phase.IDLE;
    }
}
