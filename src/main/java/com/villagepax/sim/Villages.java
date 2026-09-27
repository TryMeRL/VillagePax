package com.villagepax.sim;

import com.villagepax.core.Safely;
import com.villagepax.VillagePax;
import com.villagepax.core.config.Configs;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.build.Ascent;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Footing;
import com.villagepax.sim.build.Galleries;
import com.villagepax.sim.build.Gate;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.diplomacy.Citizenship;
import com.villagepax.sim.diplomacy.Tribute;
import com.villagepax.sim.trade.Caravans;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.Workplaces;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.trade.Trading;
import com.villagepax.core.Profiled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Деревни народов: возникают у пришедшего игрока и растут сами.
 * <p>
 * Работает на том же движке, что и колония игрока, и это несущее решение
 * всего мода: поселение — общая структура данных, а различает их только
 * {@link Owner}. Жители деревни ходят на работу, едят, недовольствуют и
 * уходят тем же кодом, что и жители колонии; здесь добавлены ровно два
 * решения, которых у колонии нет, — <b>где возникнуть</b> и <b>что строить
 * дальше без приказа игрока</b>.
 * <p>
 * Деревня встаёт <b>уже стоящей</b>: ратуша, дом и ферма ставятся мгновенно,
 * потому что деревня старше игрока. А следующее здание она начинает при нём,
 * и это важнее готовых стен: игрок видит не декорацию, а работу.
 */
public final class Villages {

    /** Как часто осматриваются окрестности игроков. Пять раз в секунду не нужно. */
    private static final int EVERY = 100;

    /**
     * Сколько материала обоз довозит за игровой день — из настроек.
     * <p>
     * Это <b>предел телеги</b>, а не подарок: сколько бы монеты у деревни
     * ни было, больше этого за день не привезут. Прежде здесь стояла
     * заглушка — материалы появлялись на складе даром, потому что деревня
     * не умеет ни выплавить стекло, ни соткать кровать. Теперь у привоза
     * есть цена, и платит её деревня из своего кошеля.
     */
    public static int tradePerDay() {
        return Configs.get().villageTradePerDay();
    }

    /**
     * Сколько монеты деревня выручает за день со своих полей и ремёсел.
     * <p>
     * Плоско, а не по числу жителей, и это осознанно: доход по головам
     * пришлось бы объяснять — чем именно занят каждый, — а честно ответить
     * на это можно только настоящим производством, и оно дело следующей
     * фазы. Пока деревня зарабатывает как деревня: понемногу и постоянно.
     */
    public static int incomePerDay() {
        return Configs.get().villageIncomePerDay();
    }

    /**
     * Сколько монеты деревня держит при себе.
     * <p>
     * Предел нужен затем, что деревня без стройки иначе копила бы изумруды
     * годами, и вернувшийся через сто дней игрок продал бы ей всё, что
     * унёс, — деньги из ниоткуда. Излишек уходит своим же: у деревни есть
     * на что тратить и без обоза.
     */
    public static final int PURSE_CAP = 128;



    /** Профессия, с которой игрок разговаривает. Данными задан только её файл. */
    public static final Identifier ELDER = new Identifier(VillagePax.MOD_ID, "elder");

    /**
     * Профессия, которая торгует.
     * <p>
     * Названа в коде затем же, зачем старейшина: по ней тело решает,
     * открывать ли прилавок на щелчок. Деревня ставит купца <b>явно</b>,
     * не полагаясь на приоритет найма, — без него торговать не с кем,
     * а новый житель приходит в деревню не каждый день.
     */
    public static final Identifier MERCHANT = new Identifier(VillagePax.MOD_ID, "merchant");

    /**
     * Кто в этом поселении стоит за прилавком.
     * <p>
     * Купец, если он есть, и старейшина, если купца нет. Второе — не
     * поблажка, а <b>страховка от тупика</b>: купца может унести набег,
     * а нового нанимают не в тот же день, и деревня без прилавка перестала
     * бы торговать насовсем. Мод, который молча перестаёт делать то, что
     * делал, выглядит сломанным — это уже проходили с пустой полкой.
     * <p>
     * Спрашивают отсюда все: и тело жителя, решающее, отвечать ли на
     * щелчок, и экран, решающий, показывать ли товар. Два ответа на один
     * вопрос означали бы, что игрок щёлкает по тому, кто открывает пустой
     * прилавок.
     */
    public static Identifier counterKeeper(Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(MERCHANT::equals).isPresent()) {
                return MERCHANT;
            }
        }
        return ELDER;
    }

    /**
     * Профессия, которая дерётся.
     * <p>
     * Названа в коде затем же, зачем и старейшина: по ней тело узнаёт,
     * кому ставить боевую цель. Данными задан её файл, а не сам факт
     * существования стражи — драться умеет только логика в коде.
     */
    public static final Identifier GUARD = new Identifier(VillagePax.MOD_ID, "guard");

    /**
     * Профессия, которая ведёт праздник.
     * <p>
     * Названа в коде по той же причине, что купец: по ней тело решает,
     * открывать ли на щелчок экран праздника, а правило «идёт ли праздник»
     * спрашивает, есть ли у ярмарки затейник. Праздника без затейника
     * не бывает, как без купца не бывает торга.
     */
    public static final Identifier ENTERTAINER = new Identifier(VillagePax.MOD_ID, "entertainer");

    private Villages() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("villages", Villages::tick));
    }

    private static void tick(ServerWorld world) {
        if (!Configs.get().autonomousVillages()) {
            // Кому нужна только своя колония, тот выключает деревни целиком.
            return;
        }
        if (world.getTime() % EVERY != 0 || CultureManager.all().isEmpty()) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            for (VillageSites.Site site : VillageSites.near(world, player.getBlockPos())) {
                Safely.run(site.culture() + " у " + site.where().toShortString(),
                        "Основание деревни", () -> found(world, site.culture(), site.where()));
            }
        }
    }

    /**
     * Поставить деревню на месте, если её там ещё нет.
     * <p>
     * Место запоминается <b>и при отказе</b>: иначе клетка, где деревне не
     * ужиться с соседом, проверялась бы заново каждые пять секунд, пока
     * игрок стоит рядом.
     */
    public static Optional<Settlement> found(ServerWorld world, Identifier cultureId, BlockPos site) {
        SettlementManager manager = SettlementManager.get(world);
        if (manager.isSettled(site)) {
            return Optional.empty();
        }

        Culture culture = CultureManager.get(cultureId);
        if (culture == null) {
            return Optional.empty();
        }

        Random random = new Random(world.getSeed() ^ site.asLong());
        Settlement village = Settlement.found(cultureId, Owner.AUTONOMOUS,
                Founding.pickName(culture, random), site);

        manager.remember(site);
        if (manager.conflictWith(village).isPresent()) {
            return Optional.empty();
        }

        ColonyFounder.raiseTownHall(world, site, village, culture, cultureId);
        manager.add(village);

        // Четверо сразу: без строителя не встанет ничего, без старейшины
        // не с кем говорить, без купца не с кем торговать, а один житель
        // на деревню — это не деревня.
        settle(world, village, Founding.firstBuilder(cultureId, culture, random));
        settle(world, village, elder(cultureId, culture, random));
        settle(world, village, tradesman(cultureId, culture, random));
        settle(world, village, hand(village, cultureId, culture, random));

        // Чертог вскрывается уже при жителях, а не сразу за ратушей:
        // зал рубят гномы, и пустому месту стройка не по силам — движок
        // так и отвечает, «некому строить».
        openTheWay(world, manager, village);

        // Дом и ферма уже стоят: деревня старше игрока.
        for (Identifier type : startingBuildings(culture)) {
            Raising.raise(world, manager, village, type);
        }
        raiseStall(world, manager, village, culture);
        // Залы чертога соединяются штольнями с порогом ратуши: без них
        // изба и поле стояли бы замурованными в толще горы.
        Galleries.cutAll(world, village);
        // Места раздаются тут же, а не на первой суточной смене: купец,
        // которому ларёк достанется только завтра, сегодня бродит по
        // деревне, и игрок ищет его по всей улице. Тот же урок, что
        // с домом, достроенным в полдень.
        Workplaces.assign(world, village);
        openForBusiness(world, village);
        // А это она строит при игроке.
        planNext(world, manager, village, culture);
        // И улицы уже убраны: колодец, фонари, цветы — деревня старше игрока.
        Streetscape.dress(world, manager, village);

        Levels.refresh(village);
        VillagePax.LOGGER.info("Деревня {} народа {} встала на {}",
                village.name(), cultureId, site.toShortString());
        return Optional.of(village);
    }

    /**
     * Поставить ратушу по-настоящему и открыть к ней дорогу.
     * <p>
     * Касается только тех, кто живёт не на земле. На поверхности ратуша —
     * это <b>блок</b>, который стоит на виду и ничего вокруг себя
     * не требует: сам зал появится, когда игрок закажет улучшение.
     * В горе тот же блок оказывается замурован в породу, а в кронах висит
     * в воздухе над лесом, — и «ратуша деревни» превращается в метку
     * неизвестно на чём. Поэтому у обоих народов зал ставится сразу:
     * это не поблажка, а то же самое «деревня старше игрока», сказанное
     * про камень и про дерево.
     * <p>
     * И сразу за залом — дорога к нему. Гномам ворота наружу, эльфам
     * всход на землю. Без них выходят запечатанная в горе полость
     * и висящая в воздухе деревня: обе видны, и ни в одну не войти.
     */
    private static void openTheWay(ServerWorld world, SettlementManager manager,
                                   Settlement settlement) {
        Footing footing = Footing.of(settlement);
        if (footing.levelsTheGround()) {
            return;
        }
        Settlement hold = settlement;
        // Ратуша ищется по объявленной роли, а не берётся первой попавшейся:
        // правило мода, и здесь оно особенно дорого. Появись у поселения
        // в этот миг второе здание — ворота прорубились бы от двери склада,
        // а зал ратуши остался бы замурован.
        Building hall = hold.buildings().stream()
                .filter(building -> BuildingTypes.isTownHall(building.type()))
                .findFirst().orElse(null);
        Schematic plan = hall == null
                ? null : SchematicLoader.get(BuildJob.schematicId(hall)).orElse(null);
        if (hall == null || plan == null) {
            return;
        }

        // Материал — в саму стройку, а не на склад: тем же доводом, что
        // и подарок колонии. Платить за него некому, деревня в свой
        // первый миг пуста.
        hall.restartBuilding();
        Materials.required(plan).forEach((item, count) ->
                hall.stock().add(Registries.ITEM.getId(item), count));
        BuildJob.advance(world, manager, hold.id(), hall.id(), Integer.MAX_VALUE);

        if (footing == Footing.HOLD) {
            Gate.carve(world, hold, hall);
        } else {
            Ascent.build(world, hold, hall);
        }
    }

    /**
     * Деревня встречает игрока с полным прилавком.
     * <p>
     * До этого первая встреча с торговлей была <b>тупиком</b>, и это
     * стоило моду половины впечатления. Игрок находил деревню, открывал
     * разговор со старейшиной и видел прилавок, на котором нельзя ни
     * купить (монеты у него ещё нет), ни продать (монеты нет у деревни:
     * кошель наполнялся только на суточной смене). Обе стороны разводили
     * руками, и мод выглядел сломанным — при том что работал ровно так,
     * как написан.
     * <p>
     * Починка идёт по той же мысли, по какой у деревни сразу стоят дом
     * и поле: <b>деревня старше игрока</b>. Она торгует не первый год,
     * и у неё есть и выручка, и товар на полке. Немного: половина
     * предела кошеля и по две сделки каждого товара — этого хватает,
     * чтобы первый разговор был живым, и мало, чтобы деревня заменила
     * собой игру.
     */
    private static void openForBusiness(ServerWorld world, Settlement village) {
        Warehouse warehouse = Warehouse.of(world, village);
        Coins.earn(warehouse.coins(), PURSE_CAP / 2).forEach(left ->
                ItemScatterer.spawn(world, village.center().getX(), village.center().getY(),
                        village.center().getZ(), left));

        for (TradeTable.Deal deal : Trading.dealsOn(village, Trading.Side.VILLAGE_SELLS)) {
            // Вдвое против сделки: одну продать, одну оставить себе.
            // Правило «последнее не отдают» иначе оставило бы полку пустой.
            warehouse.add(new ItemStack(deal.item(), deal.count() * 2));
        }
    }

    /**
     * Суточное решение деревни: чем заняться дальше.
     * <p>
     * Зовётся оттуда же, откуда считаются суточные нужды, — на смене дня
     * и ровно один раз за день, даже если игрок промотал сотню командой.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement village) {
        Culture culture = CultureManager.get(village.culture());
        if (culture == null) {
            return;
        }

        // Хозяйство — только под присмотром. Не потому что деревня без
        // игрока не работает, а потому что работать ей нечем: и выручка,
        // и покупки, и разметка следующего дома идут через блоки, а
        // спрашивать их в выгруженном чанке — значит заставлять мир
        // грузить его здесь и сейчас, каждый день и у каждой деревни.
        boolean seen = world.isChunkLoaded(village.center());
        if (seen) {
            // Сперва выручка, потом покупки: деревня тратит заработанное
            // сегодня, а не ждёт следующего утра, чтобы им распорядиться.
            Warehouse warehouse = Warehouse.of(world, village);
            earn(world, village, warehouse);
            // Дань платится ПОСЛЕ выручки и до покупок: деревня отдаёт
            // из заработанного сегодня, а не из того, что откладывала
            // на стройку. И только под присмотром — платят сундуком,
            // а сундук в выгруженном чанке не прочитать.
            Tribute.pay(world, manager, village, Schedule.dayOf(world.getTimeOfDay()));
            // Гражданину — паёк, и в тот же день деревня смотрит, друг ли
            // он ещё. После дани нарочно: деревня кормит своих из того,
            // что осталось, а не из того, что должна соседу.
            Citizenship.newDay(world, manager, village);
            deliver(world, village, warehouse);
            // Чертоги, основанные до штолен, достают их сами на рассвете:
            // иначе гномы в старых мирах так и сидели бы замурованными.
            Galleries.cutAll(world, village);
        }

        // И обоз к колонии игрока, если ей есть что предложить. Это та
        // половина обещания про караваны, которую игрок может встретить.
        Caravans.newDay(world, manager, village);

        // А если терпение вышло — не обоз, а отряд. Порядок тут ничего
        // не решает: деревня, которая игрока ненавидит, с ним и не торгует,
        // потому что старейшина с ним не разговаривает.
        Raids.newDay(world, manager, village);

        if (seen) {
            planNext(world, manager, village, culture);
            // Достроенный за день дом получает свой фонарь и цветы.
            Streetscape.dress(world, manager, village);
        }
    }

    /**
     * Дневная выручка деревни: монета со своих полей и ремёсел.
     * <p>
     * Кладётся на склад изумрудами, потому что кошель деревни — это и есть
     * её склад: у мода одно правило про имущество, и деньги ему не
     * исключение. Заодно кошель становится виден игроку — в пульте и в
     * сундуке, — и торговля перестаёт быть разговором с воздухом.
     */
    private static void earn(ServerWorld world, Settlement village, Warehouse warehouse) {
        int coin = Math.min(incomePerDay(), PURSE_CAP - Trading.purse(warehouse));
        if (coin > 0) {
            Coins.earn(warehouse.coins(), coin).forEach(left ->
                    ItemScatterer.spawn(world, village.center().getX(), village.center().getY(),
                            village.center().getZ(), left));
        }
    }

    /**
     * Обоз: довозит то, чего не хватает стройке, — <b>за деньги</b>.
     * <p>
     * Три предела разом, и каждый значит своё. Телега — не больше
     * {@link #tradePerDay()} штук за день, поэтому дом растёт несколько
     * дней и это видно. Кошель — не больше, чем деревня может заплатить,
     * поэтому бедная деревня строит медленно, а игрок, продавший ей
     * материалы, ускоряет стройку по-настоящему. Заявка — только то, что
     * нужно <b>текущей</b> стройке, поэтому склад не превращается в
     * бездонный сундук.
     * <p>
     * Не по карману один товар — смотрим следующий, а не прекращаем возить:
     * дубовая доска дешевле стекла, и остановиться на стекле значило бы
     * не привезти ничего.
     */
    private static void deliver(ServerWorld world, Settlement village, Warehouse warehouse) {
        Building site = underConstruction(village).orElse(null);
        if (site == null) {
            return;
        }
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return;
        }

        int left = tradePerDay();

        // Окно заявки — весь план: привозят под всю стройку, а не под
        // ближайшие шаги. Integer.MAX_VALUE тут нельзя: в shortfall окно
        // складывается с номером шага, и сумма молча уходит в минус.
        int wholePlan = schematic.plan().steps().size();

        for (Map.Entry<Item, Integer> want : Materials.shortfall(schematic, site, wholePlan)
                .entrySet()) {
            if (left <= 0) {
                break;
            }
            Item goods = want.getKey();
            TradeTable.Deal rate = Trading.rate(village, goods);

            int bring = Math.min(Math.min(left, want.getValue()),
                    Math.min(goods.getMaxCount(),
                            Trading.affordable(rate, Trading.purse(warehouse))));
            if (bring <= 0) {
                continue;
            }

            int cost = Trading.costOf(rate, bring);
            if (!Coins.has(warehouse.coins(), cost)) {
                continue;
            }
            Coins.pay(warehouse.coins(), cost).forEach(change ->
                    ItemScatterer.spawn(world, village.center().getX(), village.center().getY(),
                            village.center().getZ(), change));
            warehouse.addOrScatter(world, village.center(), new ItemStack(goods, bring));
            left -= bring;
        }
    }

    /**
     * Разметить следующее здание, если деревня ничего не строит.
     * <p>
     * Одно за раз: два одновременно означали бы, что оба стоят без
     * материалов, и игрок видел бы две недостроенные коробки вместо дома.
     */
    private static void planNext(ServerWorld world, SettlementManager manager, Settlement village,
                                 Culture culture) {
        if (underConstruction(village).isPresent()) {
            return;
        }

        // По нуждам, а не по списку: см. VillagePlanner. Первое желание,
        // которому нашлось место, и строится; не нашлось ни одному — деревня
        // подождёт до завтра.
        int beds = com.villagepax.sim.work.Housing.sleepingSpots(world, village).size();
        for (Identifier type : VillagePlanner.wishes(village, culture.buildings(), beds,
                com.villagepax.core.building.BuildingTypes::get,
                type -> com.villagepax.core.building.BuildingTypes.employs(type,
                        com.villagepax.sim.work.FarmJob.FARMER))) {
            Identifier schematicId = new Identifier(type.getNamespace(), type.getPath() + "_lvl1");
            if (SchematicLoader.get(schematicId).isEmpty()) {
                continue;
            }
            if (Raising.placeNear(world, manager, village, schematicId).isPresent()) {
                return;
            }
        }
    }

    private static Optional<Building> underConstruction(Settlement village) {
        return village.buildings().stream().filter(BuildJob::isUnderConstruction).findFirst();
    }

    /**
     * С чего деревня начинает — из данных типа здания.
     * <p>
     * Прежде выбирали по окончанию пути: {@code /house} и {@code /farm}.
     * Работало это до первого народа, у которого жильё называется иначе,
     * — а под крышей спят и с поля едят у всех, и без того и другого
     * жители разбегутся на третий день.
     */
    private static List<Identifier> startingBuildings(Culture culture) {
        return BuildingTypes.starting(culture.buildings());
    }

    /**
     * Старейшина. Ставится <b>явно</b>, а не через приоритет найма: у деревни
     * он обязан быть с первого дня, потому что это единственный, с кем игрок
     * может заговорить. Колонии игрока он, наоборот, не нужен — некому
     * выдавать квесты самому себе, — и поэтому приоритет найма у него ноль.
     */
    private static Citizen elder(Identifier cultureId, Culture culture, Random random) {
        Citizen elder = Founding.newCitizen(cultureId, culture, random);
        elder.setProfession(ELDER);
        return elder;
    }

    /**
     * Купец. Ставится <b>явно</b>, как и старейшина, и по той же причине:
     * это второе из двух лиц, ради которых игрок вообще подходит к деревне.
     * Ждать, пока его наймут по приоритету, значило бы, что первая
     * встреченная деревня ничем не торгует.
     */
    private static Citizen tradesman(Identifier cultureId, Culture culture, Random random) {
        Citizen merchant = Founding.newCitizen(cultureId, culture, random);
        merchant.setProfession(MERCHANT);
        return merchant;
    }

    /**
     * Четвёртый основатель — работник: самое нужное ремесло, тем же
     * правилом, каким его получает пришлый.
     * <p>
     * Прежде он вставал в деревню без ремесла, и навсегда: ремесло
     * раздаётся пришедшим извне и выросшим детям, а основатель не был
     * ни тем, ни другим. В сохранениях заказчика такой стоял в каждой
     * деревне — без дела его забирала прогулка, и он бродил по склонам
     * в двадцати блоках от дома.
     */
    private static Citizen hand(Settlement village, Identifier cultureId, Culture culture,
                                Random random) {
        Citizen hand = Founding.newCitizen(cultureId, culture, random);
        com.villagepax.sim.work.Housing.neededProfession(village, hand)
                .ifPresent(hand::setProfession);
        return hand;
    }

    /**
     * Ларёк — и тоже сразу готовым.
     * <p>
     * Не через {@code starting}, хотя соблазн был. {@code starting} — это
     * «без чего поселение не живёт», и по этому списку колония игрока
     * получает свой первый надел. Ларёк же нужен не поселению, а
     * <b>игроку</b>: это прилавок, за которым с ним будут торговать.
     * Колонии он в первый день не нужен — она торгует не сама с собой, —
     * а деревне нужен с первого мига: иначе первая встреча с торговлей
     * упрётся в «приходите через пару дней, мы строимся».
     */
    private static void raiseStall(ServerWorld world, SettlementManager manager,
                                   Settlement village, Culture culture) {
        BuildingTypes.workplaceOf(culture.buildings(), MERCHANT)
                .ifPresent(stall -> Raising.raise(world, manager, village, stall));
    }

    private static void settle(ServerWorld world, Settlement village, Citizen citizen) {
        citizen.setPosition(Vec3d.ofBottomCenter(village.center().up()));
        village.addCitizen(citizen);
        CitizenSpawner.spawnBody(world, village, citizen);
    }
}
