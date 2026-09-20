package com.villagepax.core.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.faith.Domain;
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
            return switch (type) {
                case Deliver.TYPE -> DataResult.success(Deliver.CODEC);
                case Build.TYPE -> DataResult.success(Build.CODEC);
                case Favour.TYPE -> DataResult.success(Favour.CODEC);
                case Friendship.TYPE -> DataResult.success(Friendship.CODEC);
                default -> DataResult.error(() -> "неизвестный род цели квеста: " + type);
            };
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

        /**
         * Поставить у себя мастерскую такого-то ремесла.
         * <p>
         * Первая цель, которая смотрит не в сумку, а <b>в колонию игрока</b>.
         * Ради этого мод и затевался: до сих пор деревня народа и колония
         * игрока жили в одном движке и не разговаривали — деревня просила
         * брёвна, а что игрок с ними делает, её не касалось. Теперь
         * касается, и это сразу превращает соседей в соседей.
         * <p>
         * Названа <b>ремеслом</b>, а не типом здания, и это не мелочь.
         * Норманнский страж просит поставить сторожевую башню — а колония
         * у игрока может быть майяской, и {@code villagepax:norman/watchtower}
         * ей не построить никогда. Просьба, невыполнимая по причине,
         * которой игрок не выбирал, хуже отсутствующей. Ремесло же есть
         * у обоих народов, здания разные.
         * <p>
         * Проверяется <b>в момент сдачи</b>, и потому не требует ни следа,
         * ни счётчика: здание либо стоит достроенным, либо нет. Кодек
         * поселения полон, и новой памяти мод себе позволить не может.
         *
         * @param workplace чья это мастерская
         * @param level     какого уровня и выше
         */
        record Build(Identifier workplace, int level) implements Objective {

            public static final String TYPE = "build";

            public static final Codec<Build> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("workplace").forGetter(Build::workplace),
                    Codec.intRange(1, 8).optionalFieldOf("level", 1).forGetter(Build::level)
            ).apply(instance, Build::new));

            @Override
            public String type() {
                return TYPE;
            }

            @Override
            public String describeKey() {
                return "villagepax.quest.objective.build";
            }
        }

        /**
         * Набрать благосклонности у бога такого-то домена.
         * <p>
         * Ритуальная цель из дизайн-документа, и названа она <b>доменом</b>,
         * а не богом. Иначе норманнский старейшина просил бы молиться
         * норманнскому богу, а у игрока колония майя — и просьба стала бы
         * невыполнимой по причине, которой игрок не выбирал. Домен есть
         * у обоих народов, имена разные.
         *
         * @param domain что бог ведает: harvest, stone, watch
         * @param amount сколько благосклонности набрать
         */
        record Favour(Domain domain, int amount) implements Objective {

            public static final String TYPE = "favour";

            public static final Codec<Favour> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Domain.CODEC.fieldOf("domain").forGetter(Favour::domain),
                    Codec.intRange(1, 10_000).fieldOf("amount").forGetter(Favour::amount)
            ).apply(instance, Favour::new));

            @Override
            public String type() {
                return TYPE;
            }

            @Override
            public String describeKey() {
                return "villagepax.quest.objective.favour";
            }
        }

        /**
         * Завести знакомство у другого народа.
         * <p>
         * Дипломатическая цель: она выгоняет игрока из своей долины.
         * Считается по <b>лучшей</b> деревне того народа, а не по средней
         * и не по каждой: «подружись с майя» — это про один настоящий
         * разговор, а не про обход всех их деревень.
         *
         * @param culture с кем знакомиться
         * @param trust   до какого доверия
         */
        record Friendship(Identifier culture, int trust) implements Objective {

            public static final String TYPE = "friendship";

            public static final Codec<Friendship> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("culture").forGetter(Friendship::culture),
                    Codec.INT.fieldOf("trust").forGetter(Friendship::trust)
            ).apply(instance, Friendship::new));

            @Override
            public String type() {
                return TYPE;
            }

            @Override
            public String describeKey() {
                return "villagepax.quest.objective.friendship";
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
                case Settler.TYPE -> DataResult.success(Settler.CODEC);
                case Grace.TYPE -> DataResult.success(Grace.CODEC);
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

        /**
         * Человек. Житель чужого народа переселяется в колонию игрока.
         * <p>
         * Лучшая награда, какую этот мод может дать, и вот почему. Монету
         * игрок и так заработает, вещь — скрафтит или купит; а человека
         * взять негде: жители приходят сами, по одному в день, под
         * свободную кровать. Переселенец же приходит <b>из чужого народа</b>,
         * с чужим именем и своим ремеслом, и его видно в списке колонии
         * до конца игры.
         * <p>
         * И главное: это превращает деревню в соседа. Лавка отдаёт товар,
         * сосед отпускает человека.
         *
         * @param profession чем он будет заниматься; пусто — без дела
         */
        record Settler(Optional<Identifier> profession) implements Reward {

            public static final String TYPE = "settler";

            public static final Codec<Settler> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC.optionalFieldOf("profession").forGetter(Settler::profession)
            ).apply(instance, Settler::new));

            @Override
            public String type() {
                return TYPE;
            }
        }

        /**
         * «Мы помолимся за тебя»: благосклонность бога такого-то домена.
         * <p>
         * Награда, которой нет цены в монете: благосклонность иначе
         * набирается только жертвами, по одной в день. Квест, дающий
         * её сразу, стоит недели — и потому даётся только в конце цепочки.
         */
        record Grace(Domain domain, int amount) implements Reward {

            public static final String TYPE = "favour";

            public static final Codec<Grace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Domain.CODEC.fieldOf("domain").forGetter(Grace::domain),
                    Codec.intRange(1, 10_000).fieldOf("amount").forGetter(Grace::amount)
            ).apply(instance, Grace::new));

            @Override
            public String type() {
                return TYPE;
            }
        }
    }
}
