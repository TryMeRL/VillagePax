package com.villagepax.core.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Квест — данные, как культура и профессия.
 * <p>
 * Цепочка задаётся не списком, а ссылками: каждый квест называет следующий.
 * Так цепочку можно ветвить, дописывать датапаком и обрывать, не трогая ни
 * одного файла, кроме двух соседних.
 *
 * @param giver          профессия, которая выдаёт квест
 * @param culture        народ, у которого эта просьба; пусто — у любого
 * @param minReputation  порог доверия: ниже него о квесте не заговорят
 * @param objectives     что требуется сделать
 * @param rewards        что за это дают
 * @param dialogue       ключ локализации: этими словами житель просит
 * @param next           квест, который открывается после этого
 */
public record Quest(Identifier giver, Optional<Identifier> culture, int minReputation,
                    List<Objective> objectives, List<Reward> rewards, String dialogue,
                    Optional<Identifier> next) {

    /**
     * Просят ли эту просьбу в деревне этого народа.
     * <p>
     * Квест без народа — общий: так можно написать просьбу, с которой
     * к игроку обращается кто угодно. Но <b>цепочка входа в мод у каждого
     * народа своя</b>, и без этого поля старейшина майя просил бы дров
     * на норманнскую зиму: квесты в датапаке разложены по выдающей
     * профессии, а профессия у старейшин одна на всех.
     */
    public boolean fitsCulture(Identifier settlementCulture) {
        return culture.isEmpty() || culture.get().equals(settlementCulture);
    }

    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("giver").forGetter(Quest::giver),
            Identifier.CODEC.optionalFieldOf("culture").forGetter(Quest::culture),
            Codec.INT.optionalFieldOf("min_reputation", 0).forGetter(Quest::minReputation),
            Objective.CODEC.listOf().fieldOf("objectives").forGetter(Quest::objectives),
            Reward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
            Codec.STRING.fieldOf("dialogue").forGetter(Quest::dialogue),
            Identifier.CODEC.optionalFieldOf("next").forGetter(Quest::next)
    ).apply(instance, Quest::new));

    /**
     * Цель квеста.
     * <p>
     * В 0.1 она одна — «принести». Дизайн называет ещё семь родов: доставить
     * в другую деревню, убить, построить, разведать, сопроводить, помирить,
     * принести жертву. Поэтому род <b>разбирается по полю {@code type}</b>
     * с самого начала: добавить второй род будет правкой в одном месте,
     * а не переделкой формата, который к тому времени уже разойдётся
     * по датапакам игроков.
     */
    public sealed interface Objective {

        Codec<Objective> CODEC = Codec.STRING.partialDispatch("type",
                objective -> DataResult.success(objective.type()), Objective::codecOf);

        String type();

        /** Что показать игроку как требование. */
        String describeKey();

        /**
         * Неизвестный род — ошибка, а не молчаливая подстановка. Опечатка
         * в датапаке должна называться в логе, иначе игрок будет искать,
         * почему его квест ведёт себя не так, как написан.
         */
        private static DataResult<? extends Codec<? extends Objective>> codecOf(String type) {
            return Deliver.TYPE.equals(type)
                    ? DataResult.success(Deliver.CODEC)
                    : DataResult.error(() -> "неизвестный род цели квеста: " + type);
        }

        /** Принести предмет своими руками. */
        record Deliver(Item item, int count) implements Objective {

            public static final String TYPE = "deliver";

            public static final Codec<Deliver> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Registries.ITEM.getCodec().fieldOf("item").forGetter(Deliver::item),
                    Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(Deliver::count)
            ).apply(instance, Deliver::new));

            @Override
            public String type() {
                return TYPE;
            }

            @Override
            public String describeKey() {
                return "villagepax.quest.objective.deliver";
            }
        }
    }

    /**
     * Награда.
     * <p>
     * Разбирается по {@code type} по той же причине, что и цель: родов
     * наград будет больше, а формат уже уйдёт к игрокам.
     */
    public sealed interface Reward {

        Codec<Reward> CODEC = Codec.STRING.partialDispatch("type",
                reward -> DataResult.success(reward.type()), Reward::codecOf);

        String type();

        private static DataResult<? extends Codec<? extends Reward>> codecOf(String type) {
            return switch (type) {
                case Give.TYPE -> DataResult.success(Give.CODEC);
                case Trust.TYPE -> DataResult.success(Trust.CODEC);
                default -> DataResult.error(() -> "неизвестный род награды: " + type);
            };
        }

        /** Выдать предмет. Так игрок и получает чертёж ратуши. */
        record Give(Item item, int count) implements Reward {

            public static final String TYPE = "item";

            public static final Codec<Give> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Registries.ITEM.getCodec().fieldOf("item").forGetter(Give::item),
                    Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(Give::count)
            ).apply(instance, Give::new));

            @Override
            public String type() {
                return TYPE;
            }
        }

        /** Поднять доверие деревни к игроку. */
        record Trust(int amount) implements Reward {

            public static final String TYPE = "reputation";

            public static final Codec<Trust> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("amount").forGetter(Trust::amount)
            ).apply(instance, Trust::new));

            @Override
            public String type() {
                return TYPE;
            }
        }
    }
}
