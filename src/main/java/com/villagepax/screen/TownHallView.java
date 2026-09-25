package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.ModTags;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.building.BuildingType;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Milestones;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.life.Families;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Bonds;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.trade.Wages;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.faith.Faith;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.faith.God;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Needs;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Снимок колонии для экрана ратуши.
 * <p>
 * Клиент не знает о симуляции: {@code Settlement}, {@code Citizen} и
 * {@code Building} живут на сервере и в клиентский код не попадают. Экран
 * получает эту запись — готовые к показу поля, собранные сервером.
 * <p>
 * Три причины, и все существенные:
 * <ol>
 *   <li><b>Клиент не может пересчитать то, чего не видит.</b> Свободные
 *       кровати, еда на складе и нехватка материалов считаются по блокам
 *       мира, и половина этих блоков в выгруженных чанках.</li>
 *   <li><b>Это запись</b>, значит {@code equals} бесплатен: пересобрал,
 *       сравнил с отправленным, отправил только при отличии. Иначе экран
 *       съедал бы сеть на пустом месте.</li>
 *   <li><b>Проверяется без клиента.</b> {@link #of} — обычный серверный
 *       вызов, и игровой тест сверяет снимок с настоящей колонией.</li>
 * </ol>
 * Слова вместо чисел там, где игрок принимает решение: настроение жителя
 * едет перечислением, а не сытостью, — строку подберёт клиент.
 */
public record TownHallView(
        String name,
        Identifier culture,
        String level,
        int population,
        int maxCitizens,
        Household household,
        Optional<Construction> construction,
        List<BuildingLine> buildings,
        List<CitizenLine> citizens,
        ItemTally stock,
        List<Identifier> offers,
        List<ProfessionLine> professions,
        Optional<String> advice,
        Growth growth,
        FaithView faith,
        Yoke yoke) {

    /**
     * Вера колонии: кому здесь молятся и что уже выпросили.
     *
     * @param gods   пантеон народа с набранным у каждого
     * @param temple стоит ли в колонии достроенный храм
     */
    public record FaithView(List<GodLine> gods, boolean temple) {

        /** Колония народа, у которого нет богов, — законное состояние. */
        public static final FaithView NONE = new FaithView(List.of(), false);

        public FaithView {
            gods = List.copyOf(gods);
        }

        public static final Codec<FaithView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GodLine.CODEC.listOf().fieldOf("gods").forGetter(FaithView::gods),
                Codec.BOOL.fieldOf("temple").forGetter(FaithView::temple)
        ).apply(instance, FaithView::new));
    }

    /**
     * Один бог в пульте.
     * <p>
     * Кнопки здесь <b>не заперты</b>, и это правило, а не лень. Серая
     * кнопка объясняет ровно столько же, сколько молчащая, — то есть
     * ничего; нажатая же отвечает словами, почему нельзя. Поэтому
     * снимок не несёт «можно ли», а несёт только состояние.
     *
     * @param id          опознаватель бога: им и просят
     * @param displayName ключ имени
     * @param domain      домен строкой: по нему зовут благословение
     * @param favour      набрано очков
     * @param tier        ключ названия ступени
     * @param nextAt      сколько очков до следующей ступени; ноль — выше некуда
     * @param blessedDays сколько дней благословения осталось
     * @param alwaysOn    держится ли благословение само (высшая ступень)
     */
    public record GodLine(Identifier id, String displayName, String domain, int favour,
                          String tier, int nextAt, int blessedDays, boolean alwaysOn) {

        public static final Codec<GodLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(GodLine::id),
                Codec.STRING.fieldOf("display_name").forGetter(GodLine::displayName),
                Codec.STRING.fieldOf("domain").forGetter(GodLine::domain),
                Codec.INT.fieldOf("favour").forGetter(GodLine::favour),
                Codec.STRING.fieldOf("tier").forGetter(GodLine::tier),
                Codec.INT.fieldOf("next_at").forGetter(GodLine::nextAt),
                Codec.INT.fieldOf("blessed_days").forGetter(GodLine::blessedDays),
                Codec.BOOL.fieldOf("always_on").forGetter(GodLine::alwaysOn)
        ).apply(instance, GodLine::new));
    }

    /**
     * Рост колонии: где она сейчас, что дальше и что это даст.
     * <p>
     * Ступени были в моде давно и не значили почти ничего — предел
     * населения да радиус границ. Игрок не понимал, зачем расти, потому
     * что <b>награду ему никто не называл</b>. Здесь она названа заранее:
     * подними ратушу до такого-то уровня, и откроется вот это.
     *
     * @param level     ключ названия нынешней ступени
     * @param next      ключ названия следующей, если она есть
     * @param hallLevel уровень ратуши сейчас
     * @param needsHall уровень ратуши, с которого начнётся следующая ступень
     * @param opens     ключи названий того, что откроется на следующей
     * @param reachable есть ли вообще схема ратуши нужного уровня
     */
    public record Growth(String level, Optional<String> next, int hallLevel, int needsHall,
                         List<String> opens, boolean reachable) {

        public static final Codec<Growth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("level").forGetter(Growth::level),
                Codec.STRING.optionalFieldOf("next").forGetter(Growth::next),
                Codec.INT.optionalFieldOf("hall_level", 1).forGetter(Growth::hallLevel),
                Codec.INT.optionalFieldOf("needs_hall", 2).forGetter(Growth::needsHall),
                Codec.STRING.listOf().optionalFieldOf("opens", List.of()).forGetter(Growth::opens),
                Codec.BOOL.optionalFieldOf("reachable", true).forGetter(Growth::reachable)
        ).apply(instance, Growth::new));
    }

    /** Здание, которое строится прямо сейчас, и чего ему не хватает. */
    public record Construction(Identifier type, int level, int step, int steps, ItemTally missing) {

        public static final Codec<Construction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(Construction::type),
                Codec.INT.fieldOf("level").forGetter(Construction::level),
                Codec.INT.fieldOf("step").forGetter(Construction::step),
                Codec.INT.fieldOf("steps").forGetter(Construction::steps),
                ItemTally.CODEC.fieldOf("missing").forGetter(Construction::missing)
        ).apply(instance, Construction::new));
    }

    /**
     * Профессия, которую игрок может дать жителю, и ключ её названия.
     * <p>
     * Ключ едет вместе с именем, потому что профессии — данные датапака:
     * на клиенте, подключённом к выделенному серверу, их файлов нет вовсе,
     * и вывести название по соглашению значило бы завести второе описание
     * там, где уже есть первое.
     * <p>
     * Ремесло в списке пульта — и заперто ли оно ступенью.
     * <p>
     * Запертые показываются, а не прячутся: спрятанное ремесло — это
     * ремесло, о котором игрок не знает и которого потому не хочет.
     * Видимая и недостижимая строчка «Пивовар — откроется в деревне»
     * и есть цель.
     */
    public record ProfessionLine(Identifier id, String displayName, boolean locked,
                                 String opensAt) {

        public static final Codec<ProfessionLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(ProfessionLine::id),
                Codec.STRING.fieldOf("display_name").forGetter(ProfessionLine::displayName),
                Codec.BOOL.optionalFieldOf("locked", false).forGetter(ProfessionLine::locked),
                Codec.STRING.optionalFieldOf("opens_at", "").forGetter(ProfessionLine::opensAt)
        ).apply(instance, ProfessionLine::new));
    }

    public record BuildingLine(UUID id, Identifier type, int level, BuildProgress progress,
                               BlockPos anchor, boolean canUpgrade, int priority) {

        public static final Codec<BuildingLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Uuids.CODEC.fieldOf("id").forGetter(BuildingLine::id),
                Identifier.CODEC.fieldOf("type").forGetter(BuildingLine::type),
                Codec.INT.fieldOf("level").forGetter(BuildingLine::level),
                BuildProgress.CODEC.fieldOf("progress").forGetter(BuildingLine::progress),
                BlockPos.CODEC.fieldOf("anchor").forGetter(BuildingLine::anchor),
                Codec.BOOL.fieldOf("can_upgrade").forGetter(BuildingLine::canUpgrade),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(BuildingLine::priority)
        ).apply(instance, BuildingLine::new));
    }

    /**
     * Житель в списке. {@code leavingSoon} отдельным полем, а не пятым
     * настроением: «уйдёт скоро» — не самочувствие, а предупреждение,
     * и оно бывает при любом настроении.
     * <p>
     * Житель в списке — и его возраст с роднёй.
     * <p>
     * Пора жизни едет <b>ключом</b>, а число дней — числом: клиент
     * подпишет ребёнка ребёнком на своём языке, а «41 день» переводить
     * нечего. Имя супруга строкой, а не опознавателем, по той же причине,
     * по какой строкой едет имя жителя: перевести имя нельзя, оно и есть
     * человек.
     */
    public record CitizenLine(UUID id, String name, Optional<Identifier> profession,
                              boolean housed, Optional<Identifier> workplace,
                              Mood mood, boolean leavingSoon,
                              String stage, int days, String kin, String nature,
                              String friends, String foes) {

        public static final Codec<CitizenLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Uuids.CODEC.fieldOf("id").forGetter(CitizenLine::id),
                Codec.STRING.fieldOf("name").forGetter(CitizenLine::name),
                Identifier.CODEC.optionalFieldOf("profession").forGetter(CitizenLine::profession),
                Codec.BOOL.fieldOf("housed").forGetter(CitizenLine::housed),
                Identifier.CODEC.optionalFieldOf("workplace").forGetter(CitizenLine::workplace),
                Mood.CODEC.fieldOf("mood").forGetter(CitizenLine::mood),
                Codec.BOOL.fieldOf("leaving_soon").forGetter(CitizenLine::leavingSoon),
                Codec.STRING.optionalFieldOf("stage", "villagepax.age.adult")
                        .forGetter(CitizenLine::stage),
                Codec.INT.optionalFieldOf("days", -1).forGetter(CitizenLine::days),
                Codec.STRING.optionalFieldOf("kin", "").forGetter(CitizenLine::kin),
                // Именем характера, а не самим перечислением: клиенту надо
                // характер показать, а не рассуждать о нём, и второй
                // список характеров на той стороне однажды разошёлся бы
                // с первым.
                Codec.STRING.optionalFieldOf("nature", Nature.EVEN.id())
                        .forGetter(CitizenLine::nature),
                // Именами, а не опознавателями, и по той же причине, что
                // и родня: клиенту надо их показать, а искать жителей
                // по опознавателям он не может — списка поселения
                // у него нет и быть не должно.
                Codec.STRING.optionalFieldOf("friends", "").forGetter(CitizenLine::friends),
                Codec.STRING.optionalFieldOf("foes", "").forGetter(CitizenLine::foes)
        ).apply(instance, CitizenLine::new));
    }

    public static final Codec<TownHallView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(TownHallView::name),
            Identifier.CODEC.fieldOf("culture").forGetter(TownHallView::culture),
            Codec.STRING.fieldOf("level").forGetter(TownHallView::level),
            Codec.INT.fieldOf("population").forGetter(TownHallView::population),
            Codec.INT.fieldOf("max_citizens").forGetter(TownHallView::maxCitizens),
            Household.CODEC.fieldOf("household").forGetter(TownHallView::household),
            Construction.CODEC.optionalFieldOf("construction").forGetter(TownHallView::construction),
            BuildingLine.CODEC.listOf().fieldOf("buildings").forGetter(TownHallView::buildings),
            CitizenLine.CODEC.listOf().fieldOf("citizens").forGetter(TownHallView::citizens),
            ItemTally.CODEC.fieldOf("stock").forGetter(TownHallView::stock),
            Identifier.CODEC.listOf().fieldOf("offers").forGetter(TownHallView::offers),
            ProfessionLine.CODEC.listOf().fieldOf("professions").forGetter(TownHallView::professions),
            Codec.STRING.optionalFieldOf("advice").forGetter(TownHallView::advice),
            Growth.CODEC.fieldOf("growth").forGetter(TownHallView::growth),
            FaithView.CODEC.optionalFieldOf("faith", FaithView.NONE).forGetter(TownHallView::faith),
            // Последнее, шестнадцатое поле снимка. Ярмо — состояние
            // колонии, а не событие: о платеже игроку говорят в чат раз
            // в сутки, но «почему у меня каждое утро пропадает серебро»
            // обязано отвечать то же место, где он смотрит всё остальное.
            Yoke.CODEC.optionalFieldOf("yoke", Yoke.NONE).forGetter(TownHallView::yoke)
    ).apply(instance, TownHallView::new));

    /**
     * Под чьим ярмом колония.
     *
     * @param lord чьё — пустое имя значит «ничьё»
     * @param days сколько дней ещё платить
     */
    public record Yoke(String lord, int days) {

        public static final Yoke NONE = new Yoke("", 0);

        public static final Codec<Yoke> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("lord", "").forGetter(Yoke::lord),
                Codec.INT.optionalFieldOf("days", 0).forGetter(Yoke::days)
        ).apply(instance, Yoke::new));

        public boolean paying() {
            return days > 0 && !lord.isBlank();
        }
    }

    /**
     * Быт колонии одной записью: кровати, еда, хранилища.
     * <p>
     * Сгруппировано не для красоты. У кодека Mojang ровно шестнадцать
     * полей в группе, и семнадцатое — совет «что дальше» — в неё
     * не поместилось. Выбор был между хитростью со склейкой кодеков
     * и честной записью; запись вдобавок объясняет, что эти пять чисел
     * об одном: сколько колония может прокормить и уложить спать.
     * <p>
     * Снаружи ничего не изменилось: {@link TownHallView} по-прежнему
     * отвечает на {@code beds()} и {@code meals()} — просто переспрашивает
     * их у быта. Тридцать мест, где экран и проверки зовут эти числа,
     * переписывать ради устройства кодека было бы не улучшением.
     * <p>
     * Быт колонии одной записью: кровати, еда, хранилища и казна.
     * <p>
     * Казна и счёт лежат здесь же, потому что это тот же быт: сколько
     * у колонии есть и на сколько ей хватит. А ставка — потому, что
     * читать её отдельно от счёта бессмысленно: одно число объясняет
     * другое.
     *
     * @param beds       спальных мест всего
     * @param freeBeds   из них свободных
     * @param meals      порций еды на складе
     * @param daysOfFood на сколько дней их хватит
     * @param containers сколько хранилищ у колонии
     * @param coins   сколько монеты в сундуках колонии
     * @param wages   сколько уйдёт завтра на жалование; ноль — деньги
     *                в колонии ещё не ходят
     * @param taxRate какую долю заработанного колония забирает себе
     * @param market  есть ли кому продать своё: без этого монета утекает
     */
    public record Household(int beds, int freeBeds, int meals, int daysOfFood, int containers,
                            int coins, int wages, int taxRate, boolean market) {

        public Household(int beds, int freeBeds, int meals, int daysOfFood, int containers) {
            this(beds, freeBeds, meals, daysOfFood, containers, 0, 0, 0, false);
        }

        public static final Codec<Household> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("beds").forGetter(Household::beds),
                Codec.INT.fieldOf("free_beds").forGetter(Household::freeBeds),
                Codec.INT.fieldOf("meals").forGetter(Household::meals),
                Codec.INT.fieldOf("days_of_food").forGetter(Household::daysOfFood),
                Codec.INT.fieldOf("containers").forGetter(Household::containers),
                Codec.INT.optionalFieldOf("coins", 0).forGetter(Household::coins),
                Codec.INT.optionalFieldOf("wages", 0).forGetter(Household::wages),
                Codec.INT.optionalFieldOf("tax_rate", 0).forGetter(Household::taxRate),
                Codec.BOOL.optionalFieldOf("market", false).forGetter(Household::market)
        ).apply(instance, Household::new));
    }

    public int coins() {
        return household.coins();
    }

    public int wages() {
        return household.wages();
    }

    public int taxRate() {
        return household.taxRate();
    }

    public boolean hasMarket() {
        return household.market();
    }

    public int beds() {
        return household.beds();
    }

    public int freeBeds() {
        return household.freeBeds();
    }

    public int meals() {
        return household.meals();
    }

    public int daysOfFood() {
        return household.daysOfFood();
    }

    public int containers() {
        return household.containers();
    }

    /** Списки копируются: снимок обязан быть неизменяемым, его сравнивают. */
    public TownHallView {
        buildings = List.copyOf(buildings);
        citizens = List.copyOf(citizens);
        offers = List.copyOf(offers);
        professions = List.copyOf(professions);
    }

    /**
     * Собрать снимок колонии.
     * <p>
     * Читает блоки мира — кровати и сундуки, — поэтому зовётся только когда
     * экран открыт: игрок в этот момент стоит у ратуши, и колония загружена.
     */
    public static TownHallView of(ServerWorld world, Settlement settlement) {
        Warehouse warehouse = Warehouse.of(world, settlement);
        ItemTally stock = warehouse.tally();

        return new TownHallView(
                settlement.name(),
                settlement.culture(),
                settlement.level().id(),
                settlement.population(),
                settlement.level().maxCitizens(),
                new Household(
                        Housing.sleepingSpots(world, settlement).size(),
                        Housing.freeSpots(world, settlement),
                        meals(stock),
                        daysOfFood(stock, settlement.population()),
                        warehouse.containerCount(),
                        Wages.treasuryOf(world, settlement),
                        Wages.billOf(settlement),
                        settlement.taxRate(),
                        Wages.hasMarket(settlement)),
                construction(world, settlement, warehouse),
                buildings(settlement),
                citizens(settlement),
                stock,
                offers(settlement),
                knownProfessions(settlement.level()),
                // Совет считается здесь же: ему нужны и склад, и здания,
                // и жители — всё то, что уже собрано этим снимком.
                Advice.nextStep(world, settlement),
                growthOf(settlement),
                faithOf(world, settlement),
                yokeOf(world, settlement));
    }

    /**
     * Под чьим ярмом колония — именем, а не опознавателем.
     * <p>
     * Именем, потому что клиенту надо его <b>показать</b>: имя деревни
     * не переводится, а искать поселение по опознавателю клиент не может —
     * у него нет списка поселений и быть не должно.
     */
    private static Yoke yokeOf(ServerWorld world, Settlement settlement) {
        long today = Schedule.dayOf(world.getTimeOfDay());
        return com.villagepax.sim.diplomacy.Yoke
                .overlord(com.villagepax.sim.SettlementManager.get(world), settlement, today)
                .map(lord -> new Yoke(lord.name(), settlement.tributeDaysLeft(today)))
                .orElse(Yoke.NONE);
    }

    /**
     * Куда колонии расти и что это даст.
     * <p>
     * Уровень ратуши здесь — то же правило, что и в {@link Levels}:
     * ступень колонии равна уровню её ратуши. Дублировать правило нельзя,
     * поэтому нужный уровень считается из порядкового номера ступени,
     * а не из отдельной таблицы.
     */
    private static Growth growthOf(Settlement settlement) {
        SettlementLevel now = settlement.level();
        int hall = settlement.buildings().stream()
                .filter(building -> building.isOperational() && Levels.isTownHallType(building.type()))
                .mapToInt(Building::level)
                .max()
                .orElse(0);

        if (now.isMax()) {
            return new Growth(Milestones.levelKey(now), Optional.empty(), hall, hall,
                    List.of(), true);
        }
        SettlementLevel next = now.next();
        List<String> opens = new ArrayList<>();
        for (Identifier id : ProfessionManager.byHiringPriority()) {
            ProfessionManager.get(id)
                    .filter(craft -> craft.minLevel() == next)
                    .ifPresent(craft -> opens.add(craft.displayName()));
        }
        for (BuildingType kind : BuildingTypes.all().values()) {
            if (kind.minLevel() == next) {
                opens.add(kind.displayName());
            }
        }
        int needsHall = next.ordinal() + 1;
        return new Growth(Milestones.levelKey(now), Optional.of(Milestones.levelKey(next)),
                hall, needsHall, opens, hallExists(settlement, needsHall));
    }

    /**
     * Есть ли в моде ратуша такого уровня.
     * <p>
     * Пульт не имеет права обещать ступень, до которой нельзя дойти.
     * Ровно это и случилось, когда лестница ступеней появилась раньше
     * третьей ратуши: карточка роста звала игрока поднять ратушу
     * до уровня, схемы которого не существовало, а кнопка «Улучшить»
     * отвечала «выше некуда». Хуже, чем молчание.
     */
    private static boolean hallExists(Settlement settlement, int level) {
        Culture culture = CultureManager.get(settlement.culture());
        if (culture == null) {
            return false;
        }
        return culture.townHallBuilding()
                .map(type -> new Identifier(type.getNamespace(),
                        type.getPath() + "_lvl" + level))
                .flatMap(SchematicLoader::get)
                .isPresent();
    }

    /**
     * Кем можно сделать жителя — в порядке нужности из данных, том же,
     * в котором профессии достаются пришедшим сами.
     * <p>
     * Имя не {@code professions()}: у записи с таким полем это уже занятое
     * имя метода доступа, и компилятор отказывается прямо на этом.
     */
    private static List<ProfessionLine> knownProfessions(SettlementLevel level) {
        List<ProfessionLine> lines = new ArrayList<>();
        for (Identifier id : ProfessionManager.byHiringPriority()) {
            ProfessionManager.get(id).ifPresent(known -> lines.add(new ProfessionLine(
                    id, known.displayName(), !known.openTo(level),
                    Milestones.levelKey(known.minLevel()))));
        }
        return lines;
    }

    private static List<BuildingLine> buildings(Settlement settlement) {
        List<BuildingLine> lines = new ArrayList<>();
        // В порядке очереди: список в пульте обязан показывать то же,
        // в каком порядке билдер берётся за дело.
        for (Building building : settlement.byPriority()) {
            lines.add(new BuildingLine(building.id(), building.type(), building.level(),
                    building.progress(), building.anchor(),
                    building.isOperational() && BuildOrders.canUpgrade(building),
                    building.priority()));
        }
        return lines;
    }

    private static List<CitizenLine> citizens(Settlement settlement) {
        List<CitizenLine> lines = new ArrayList<>();
        for (Citizen citizen : settlement.citizens()) {
            lines.add(new CitizenLine(
                    citizen.id(),
                    citizen.fullName(),
                    citizen.profession(),
                    !citizen.isHomeless(),
                    citizen.workplace().flatMap(settlement::building).map(Building::type),
                    moodOf(citizen),
                    // Срок спрашивается у жителя, а не у настройки: ленивый
                    // терпит вдвое дольше, и общий срок обещал бы игроку
                    // уход, которого не будет. Окно и правило обязаны
                    // говорить одно и то же — этот урок мод уже получал
                    // на поручениях.
                    citizen.discontent() >= Needs.warnAfterDays(citizen),
                    Ages.stageOf(citizen).key(),
                    Ages.daysOf(citizen),
                    kinOf(settlement, citizen),
                    Natures.of(citizen).id(),
                    Bonds.namesOfFriends(settlement, citizen),
                    Bonds.namesOfFoes(settlement, citizen)));
        }
        return lines;
    }

    /**
     * Кем этот житель кому приходится — одной строкой.
     * <p>
     * Строкой, а не списком опознавателей, и это решение по смыслу.
     * Клиенту нужно <b>показать</b> родню, а не рассуждать о ней: имена
     * не переводятся, а кто кому муж, сервер знает и так. Список же
     * заставил бы клиента искать жителей по опознавателям — то есть
     * держать у себя половину поселения.
     * <p>
     * Пусто — значит одинок, и это тоже сведение: в колонии, где никто
     * ни с кем не сошёлся, детей не будет.
     */
    private static String kinOf(Settlement settlement, Citizen citizen) {
        String spouse = Families.spouseOf(settlement, citizen)
                .map(Citizen::fullName).orElse("");
        int children = Families.childrenOf(settlement, citizen).size();

        if (spouse.isEmpty()) {
            return children > 0 ? String.valueOf(children) : "";
        }
        return children > 0 ? spouse + " (" + children + ")" : spouse;
    }

    private static Mood moodOf(Citizen citizen) {
        if (citizen.saturation() <= 0) {
            return Mood.STARVING;
        }
        if (Needs.isHungry(citizen)) {
            return Mood.HUNGRY;
        }
        return citizen.isUnhappy() ? Mood.UNHAPPY : Mood.CONTENT;
    }

    /**
     * Текущая стройка — первая незаконченная, та же, за которую берётся
     * билдер. Показывать все сразу незачем: билдер всё равно делает их
     * по одной, и «не хватает» относится к той, что идёт.
     */
    private static Optional<Construction> construction(ServerWorld world, Settlement settlement,
                                                       Warehouse warehouse) {
        for (Building building : settlement.buildings()) {
            if (!BuildJob.isUnderConstruction(building)) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            int steps = schematic.plan().steps().size();
            return Optional.of(new Construction(building.type(), building.level(),
                    building.nextStep(), steps, missing(warehouse, schematic, building, steps)));
        }
        return Optional.empty();
    }

    /**
     * Чего не хватает, чтобы стройка дошла до конца.
     * <p>
     * Считается по всему остатку плана и <b>с учётом склада</b>: игрок
     * действует по этому списку, и «не хватает» для него значит «нет ни
     * у стройки, ни в сундуках», а не «не поднесено к площадке».
     */
    private static ItemTally missing(Warehouse warehouse, Schematic schematic, Building building,
                                     int lookahead) {
        Map<Identifier, Integer> shortfall = new LinkedHashMap<>();

        Materials.shortfall(schematic, building, lookahead).forEach((item, count) -> {
            int elsewhere = warehouse.count(item);
            if (count > elsewhere) {
                shortfall.put(Registries.ITEM.getId(item), count - elsewhere);
            }
        });
        return new ItemTally(shortfall);
    }

    /** Сколько на складе съедобного — в штуках, как игрок его и видит. */
    private static int meals(ItemTally stock) {
        int meals = 0;
        for (Map.Entry<Identifier, Integer> entry : stock.contents().entrySet()) {
            if (food(entry.getKey())) {
                meals += entry.getValue();
            }
        }
        return meals;
    }

    /**
     * На сколько дней хватит еды.
     * <p>
     * Число, по которому игрок действительно решает, ехать ли за хлебом:
     * «14 порций» ничего не говорит, «на два дня» говорит всё.
     */
    static int daysOfFood(ItemTally stock, int population) {
        int nourishment = 0;
        for (Map.Entry<Identifier, Integer> entry : stock.contents().entrySet()) {
            Identifier id = entry.getKey();
            if (food(id)) {
                nourishment += Needs.nourishment(Registries.ITEM.get(id)) * entry.getValue();
            }
        }
        return nourishment / (Needs.DAILY_COST * Math.max(1, population));
    }

    private static boolean food(Identifier item) {
        Item known = Registries.ITEM.get(item);
        return known.getDefaultStack().isIn(ModTags.CITIZEN_FOOD);
    }

    /**
     * Что колония вправе заказать: схемы тех зданий, которые перечислены
     * в её культуре.
     * <p>
     * Список — предложение, а не запрет: он решает, что показать в экране.
     * Целость мира держат проверки {@link BuildOrders}, и на них же стоит
     * отладочная команда, которой позволено больше.
     */
    private static List<Identifier> offers(Settlement settlement) {
        Culture culture = CultureManager.get(settlement.culture());
        if (culture == null) {
            return List.of();
        }

        List<Identifier> offers = new ArrayList<>();
        for (Identifier schematic : SchematicLoader.ids()) {
            // Только первый уровень. Второй и дальше — дело кнопки
            // «Улучшить»: заказать сразу второй уровень значило бы поставить
            // дом, у которого не было первого. А в списке от этого каждое
            // здание показывалось дважды — подпись у уровней одна на тип,
            // и игрок видел «Дом норманнов, Дом норманнов». Ровно это
            // и было сообщено как «в ратуше двоятся здания».
            if (BuildJob.levelOf(schematic).orElse(1) != 1) {
                continue;
            }

            BuildJob.buildingTypeOf(schematic)
                    .filter(type -> culture.buildings().contains(type))
                    // Ратуша у поселения одна: она и есть его середина, и она
                    // уже стоит с основания. Предложи её — и в списке зданий
                    // появится вторая, которую некуда поставить.
                    .filter(type -> !Levels.isTownHallType(type))
                    .ifPresent(type -> offers.add(schematic));
        }
        return offers;
    }

    /**
     * Вера колонии для пульта.
     * <p>
     * Считается при каждом снимке, и это дёшево: богов у народа трое,
     * а всё, что о них надо знать, лежит в самом поселении. Ни одного
     * обращения к миру — в отличие от кроватей и склада.
     */
    private static FaithView faithOf(ServerWorld world, Settlement settlement) {
        long today = Schedule.dayOf(world.getTimeOfDay());
        List<GodLine> lines = new ArrayList<>();

        for (Identifier id : Gods.of(settlement.culture())) {
            God god = Gods.get(id).orElse(null);
            if (god == null) {
                continue;
            }
            int favour = settlement.favourOf(id);
            Faith.Tier tier = Faith.tierOf(favour);
            boolean alwaysOn = tier.reached(Faith.Tier.CHOSEN);

            lines.add(new GodLine(id, god.displayName(), god.domain().id(), favour,
                    tier.key(),
                    tier.next().map(Faith.Tier::from).orElse(0),
                    settlement.blessingDaysLeft(god.domain().id(), today),
                    alwaysOn));
        }

        return new FaithView(lines, Faith.hasTemple(settlement));
    }
}
