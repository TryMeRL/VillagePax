package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.sim.work.JobState;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Житель как данные.
 * <p>
 * Это несущее решение по производительности: житель существует всегда, а его
 * тело — сущность в мире — появляется только при загруженном чанке и исчезает
 * при выгрузке. Всё, что должно пережить выгрузку, лежит здесь, поэтому
 * лесоруб после перезахода в мир продолжит с того же шага, а не начнёт заново.
 * Существующие моды такого класса ломаются именно на том, что держат состояние
 * в сущности, и сотни сущностей душат сервер.
 */
public class Citizen {

    /** Ниже этого порога житель работает вполсилы, а затем уходит из колонии. */
    public static final int UNHAPPY_THRESHOLD = 30;

    public static final float MAX_HEALTH = 20.0f;

    public static final Codec<Citizen> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(Citizen::id),
            Codec.STRING.fieldOf("first_name").forGetter(Citizen::firstName),
            Codec.STRING.optionalFieldOf("last_name", "").forGetter(Citizen::lastName),
            Identifier.CODEC.fieldOf("culture").forGetter(Citizen::culture),
            Gender.CODEC.fieldOf("gender").forGetter(Citizen::gender),
            Life.CODEC.optionalFieldOf("life", Life.UNKNOWN).forGetter(Citizen::life),
            Identifier.CODEC.optionalFieldOf("profession").forGetter(Citizen::profession),
            Codec.INT.optionalFieldOf("happiness", 70).forGetter(Citizen::happiness),
            Codec.INT.optionalFieldOf("saturation", 20).forGetter(Citizen::saturation),
            Uuids.STRING_CODEC.optionalFieldOf("home").forGetter(Citizen::home),
            Uuids.STRING_CODEC.optionalFieldOf("workplace").forGetter(Citizen::workplace),
            Vec3d.CODEC.optionalFieldOf("position").forGetter(Citizen::position),
            Codec.FLOAT.optionalFieldOf("health", MAX_HEALTH).forGetter(Citizen::health),
            JobState.CODEC.optionalFieldOf("job", JobState.IDLE).forGetter(Citizen::jobState),
            BlockPos.CODEC.optionalFieldOf("bed").forGetter(Citizen::bed),
            Codec.INT.optionalFieldOf("discontent", 0).forGetter(Citizen::discontent)
    ).apply(instance, Citizen::new));

    /**
     * Жизнь жителя: когда родился, с кем живёт, чей он сын.
     * <p>
     * Запись, а не три поля, по той же причине, что война и вера
     * у поселения: у кодека Mojang ровно шестнадцать полей в группе,
     * и все шестнадцать были заняты. Место нашлось под {@code age_ticks} —
     * полем, которое <b>никто никогда не читал</b>: у него были геттер
     * и прибавлялка, и ни одного вызова во всём моде. Заглушка уступила
     * место тому, ради чего её когда-то завели.
     * <p>
     * <b>Прожитые дни, а не день рождения.</b> Соблазн был записать день
     * мира и вычитать — одно число вместо ежедневной записи. Но тогда
     * возраст шёл бы и в те сутки, когда колонию никто не видит: игрок
     * ушёл на сто дней в шахту — вернулся к кладбищу. Мод это уже
     * проходил с голодом, который считался везде, а поесть житель мог
     * только рядом с игроком; вывод записан: <b>жизнь идёт там, где
     * на неё смотрят</b>. Поэтому здесь счётчик, и растёт он только
     * в те сутки, которые колония прожила на глазах.
     *
     * <b>Друзья лежат здесь, а враги — нет</b>, и это не небрежность.
     * Вражда в этом моде <b>выводится</b>: ссорятся те, чьи характеры
     * не сходятся, и только пока они делят дело. Выведенному хранилище
     * не нужно, у него не бывает рассинхрона между концами, и развести
     * поссорившихся игрок может руками — переназначив одному ремесло.
     * Дружба же обязана переживать перевод в другую мастерскую: иначе
     * это не дружба, а соседство по верстаку.
     *
     * @param lived   сколько дней прожито; {@link #UNAGED} — «возраста
     *                не помнит»
     * @param spouse  супруг, если есть
     * @param parents отец и мать; пусто у всех, кто не родился в колонии
     * @param friends с кем сошёлся; не больше {@link #MOST_FRIENDS} —
     *                человек помнит немногих
     */
    public record Life(int lived, Optional<UUID> spouse, List<UUID> parents,
                       List<UUID> friends) {

        /**
         * «Возраста не помнит».
         * <p>
         * Так помечены все, кто был в мире до этой правки. Считать их
         * младенцами нельзя — они работают; стариками тем более. Значит,
         * возраста у них нет вовсе, и старость их не берёт. Это честнее,
         * чем выдумать им годы задним числом и уморить тех, кто строил
         * колонию с первого дня.
         */
        public static final int UNAGED = -1;

        /**
         * Скольких помнят.
         * <p>
         * Четверо — не скупость памяти, а цена горя: смерть друга стоит
         * довольства каждому, кто его помнил, и колония из тридцати,
         * где все дружат со всеми, хоронила бы каждого разом всем
         * поселением. Круг близких у человека и в жизни невелик.
         */
        public static final int MOST_FRIENDS = 4;

        public static final Life UNKNOWN = new Life(UNAGED, Optional.empty(), List.of(),
                List.of());

        public Life(int lived, Optional<UUID> spouse, List<UUID> parents) {
            this(lived, spouse, parents, List.of());
        }

        public Life {
            parents = List.copyOf(parents);
            // Предел кладётся здесь, а не в том, кто дружит: запись
            // приходит и из NBT, в том числе из правленого руками, и
            // список на сорок друзей оттуда стоил бы колонии суток горя.
            friends = friends.size() > MOST_FRIENDS
                    ? List.copyOf(friends.subList(0, MOST_FRIENDS)) : List.copyOf(friends);
        }

        public static final Codec<Life> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("lived", UNAGED).forGetter(Life::lived),
                Uuids.STRING_CODEC.optionalFieldOf("spouse").forGetter(Life::spouse),
                Uuids.STRING_CODEC.listOf().optionalFieldOf("parents", List.of())
                        .forGetter(Life::parents),
                Uuids.STRING_CODEC.listOf().optionalFieldOf("friends", List.of())
                        .forGetter(Life::friends)
        ).apply(instance, Life::new));
    }

    private final UUID id;
    private String firstName;
    private String lastName;
    private final Identifier culture;
    private final Gender gender;
    private Life life;
    private Optional<Identifier> profession;
    private int happiness;
    private int saturation;
    private Optional<UUID> home;
    private Optional<UUID> workplace;
    private Optional<Vec3d> position;
    private float health;

    /**
     * Чем житель занят. Живёт здесь, а не в теле: курьер с полными руками
     * обязан после перезахода в мир донести груз, а не начать путь заново.
     */
    private JobState jobState;

    /**
     * Место, где житель спит: настоящая кровать или отмеченная маркером
     * подстилка. Хранится позицией, а не ссылкой на здание, потому что идти
     * жителю надо именно туда.
     */
    private Optional<BlockPos> bed;

    /**
     * Сколько игровых дней подряд житель голоден или несчастен.
     * <p>
     * В днях, а не в тиках, намеренно: игрок мыслит днями. «Не кормил две
     * ночи» — понятная причина ухода, «12400 тиков неудовлетворённости» — нет.
     */
    private int discontent;

    /**
     * Живое тело жителя, если оно сейчас есть в мире.
     * <p>
     * Намеренно не сохраняется: сущности жителей не пишутся в чанк, поэтому
     * после перезапуска сервера любой такой идентификатор был бы протухшим.
     */
    private UUID entityUuid;

    public Citizen(UUID id, String firstName, String lastName, Identifier culture, Gender gender,
                   Life life, Optional<Identifier> profession, int happiness, int saturation,
                   Optional<UUID> home, Optional<UUID> workplace, Optional<Vec3d> position,
                   float health) {
        this(id, firstName, lastName, culture, gender, life, profession, happiness, saturation,
                home, workplace, position, health, JobState.IDLE, Optional.empty(), 0);
    }

    public Citizen(UUID id, String firstName, String lastName, Identifier culture, Gender gender,
                   Life life, Optional<Identifier> profession, int happiness, int saturation,
                   Optional<UUID> home, Optional<UUID> workplace, Optional<Vec3d> position,
                   float health, JobState jobState) {
        this(id, firstName, lastName, culture, gender, life, profession, happiness, saturation,
                home, workplace, position, health, jobState, Optional.empty(), 0);
    }

    public Citizen(UUID id, String firstName, String lastName, Identifier culture, Gender gender,
                   Life life, Optional<Identifier> profession, int happiness, int saturation,
                   Optional<UUID> home, Optional<UUID> workplace, Optional<Vec3d> position,
                   float health, JobState jobState, Optional<BlockPos> bed, int discontent) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.culture = culture;
        this.gender = gender;
        this.life = life;
        this.profession = profession;
        // Через конструктор идёт декодирование из NBT, поэтому ограничения
        // стоят здесь, а не только в сеттерах: житель с happiness = 250 из
        // правленого руками сохранения иначе навсегда остался бы вне диапазона,
        // и isUnhappy у него всегда возвращал бы ложь.
        this.happiness = clampHappiness(happiness);
        this.saturation = clampSaturation(saturation);
        this.home = home;
        this.workplace = workplace;
        this.position = position;
        this.health = clampHealth(health);
        this.jobState = jobState;
        this.bed = bed;
        this.discontent = Math.max(0, discontent);
    }

    private static int clampHappiness(int value) {
        return Math.max(0, Math.min(SettlementStats.MAX_HAPPINESS, value));
    }

    private static int clampSaturation(int value) {
        return Math.max(0, value);
    }

    private static float clampHealth(float value) {
        return Math.max(0.0f, Math.min(MAX_HEALTH, value));
    }

    public static Citizen newborn(String firstName, String lastName, Identifier culture, Gender gender) {
        return new Citizen(UUID.randomUUID(), firstName, lastName, culture, gender, Life.UNKNOWN,
                Optional.empty(), 70, 20, Optional.empty(), Optional.empty(), Optional.empty(), MAX_HEALTH);
    }

    public UUID id() {
        return id;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    /**
     * Дать прозвище.
     * <p>
     * Нужно ровно при родах: ребёнок получает имя по отцу, и получить
     * его заранее он не мог — отца тогда не знали. Больше прозвище
     * не меняется никогда.
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String fullName() {
        return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }

    public void rename(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Identifier culture() {
        return culture;
    }

    public Gender gender() {
        return gender;
    }

    // --- жизнь ---

    public Life life() {
        return life;
    }

    /** Сколько дней прожито; {@link Life#UNAGED} — «возраста не помнит». */
    public int lived() {
        return life.lived();
    }

    /**
     * Поставить возраст.
     * <p>
     * Зовётся дважды за жизнь: ноль новорождённому и грань взрослости
     * тому, кто пришёл в колонию уже работником. Дальше его двигает
     * только суточный ход.
     */
    public void setLived(int days) {
        this.life = new Life(Math.max(Life.UNAGED, days), life.spouse(), life.parents(),
                life.friends());
    }

    /**
     * Прожить ещё сутки.
     * <p>
     * Тот, у кого возраста нет, его и не наживает: {@link Life#UNAGED}
     * значит «не считаем», а не «ноль». Иначе старожилы мира начали бы
     * стареть с нуля и пережили бы собственных внуков.
     */
    public void liveADay() {
        if (life.lived() != Life.UNAGED) {
            this.life = new Life(life.lived() + 1, life.spouse(), life.parents(),
                    life.friends());
        }
    }

    public Optional<UUID> spouse() {
        return life.spouse();
    }

    public void marry(UUID other) {
        this.life = new Life(life.lived(), Optional.of(other), life.parents(), life.friends());
    }

    /** Супруга не стало: запись снимается с обоих, а не остаётся висеть. */
    public void widow() {
        this.life = new Life(life.lived(), Optional.empty(), life.parents(), life.friends());
    }

    /** Отец и мать; пусто у всех, кто не родился в колонии. */
    public List<UUID> parents() {
        return life.parents();
    }

    public void setParents(UUID father, UUID mother) {
        this.life = new Life(life.lived(), life.spouse(), List.of(father, mother),
                life.friends());
    }

    /** С кем этот житель сошёлся. */
    public List<UUID> friends() {
        return life.friends();
    }

    /**
     * Запомнить друга.
     * <p>
     * Обе стороны записывает не эта запись, а тот, кто знакомит: здесь
     * известен только один конец. Симметрию держит {@code Bonds}, и она же
     * проверяется свойством — вручную такое не усмотреть.
     *
     * @return {@code false}, если дружба уже записана, память полна или
     *         житель пытается подружиться сам с собой
     */
    public boolean befriend(UUID other) {
        if (other == null || other.equals(id) || life.friends().contains(other)
                || life.friends().size() >= Life.MOST_FRIENDS) {
            return false;
        }
        List<UUID> wider = new java.util.ArrayList<>(life.friends());
        wider.add(other);
        this.life = new Life(life.lived(), life.spouse(), life.parents(), List.copyOf(wider));
        return true;
    }

    /**
     * Забыть: друга не стало.
     * <p>
     * Зовётся, когда житель уходит из колонии — своими ногами или вперёд
     * ногами. Оставленная запись означала бы, что колония горюет по тому,
     * кого в ней давно нет, и горевала бы вечно.
     */
    public boolean forget(UUID other) {
        if (!life.friends().contains(other)) {
            return false;
        }
        List<UUID> fewer = new java.util.ArrayList<>(life.friends());
        fewer.remove(other);
        this.life = new Life(life.lived(), life.spouse(), life.parents(), List.copyOf(fewer));
        return true;
    }

    /** Ребёнок ли это <b>вот этих</b> двоих. */
    public boolean isChildOf(UUID one, UUID other) {
        return life.parents().contains(one) && life.parents().contains(other);
    }

    public Optional<Identifier> profession() {
        return profession;
    }

    public void setProfession(Identifier profession) {
        this.profession = Optional.ofNullable(profession);
    }

    public int happiness() {
        return happiness;
    }

    public void setHappiness(int value) {
        this.happiness = clampHappiness(value);
    }

    public boolean isUnhappy() {
        return happiness < UNHAPPY_THRESHOLD;
    }

    public int saturation() {
        return saturation;
    }

    public void setSaturation(int value) {
        this.saturation = clampSaturation(value);
    }

    public Optional<UUID> home() {
        return home;
    }

    public void setHome(UUID building) {
        this.home = Optional.ofNullable(building);
    }

    public Optional<UUID> workplace() {
        return workplace;
    }

    public void setWorkplace(UUID building) {
        this.workplace = Optional.ofNullable(building);
    }

    public Optional<Vec3d> position() {
        return position;
    }

    public void setPosition(Vec3d position) {
        this.position = Optional.ofNullable(position);
    }

    /**
     * Здоровье хранится в данных, а не только в теле: иначе раненый житель
     * полностью исцелялся бы каждый раз, когда игрок отходит и возвращается,
     * и осада из фазы 3 перестала бы работать как механика.
     */
    public float health() {
        return health;
    }

    public void setHealth(float health) {
        this.health = clampHealth(health);
    }

    public JobState jobState() {
        return jobState;
    }

    public void setJobState(JobState jobState) {
        this.jobState = jobState;
    }

    public Optional<BlockPos> bed() {
        return bed;
    }

    public void setBed(BlockPos bed) {
        this.bed = Optional.ofNullable(bed);
    }

    public boolean isHomeless() {
        return bed.isEmpty();
    }

    public int discontent() {
        return discontent;
    }

    /** Ещё один день без еды или в тоске. */
    public void addDiscontent() {
        discontent++;
    }

    public void contented() {
        discontent = 0;
    }

    public Optional<UUID> entityUuid() {
        return Optional.ofNullable(entityUuid);
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public boolean isUnemployed() {
        return workplace.isEmpty();
    }
}
