package com.villagepax.sim;

import com.villagepax.VillagePax;
import com.villagepax.core.config.Configs;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.trade.Trading;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
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

    /** Кольца поиска места для нового здания и шаг между ними, в блоках. */
    private static final int PLACE_RINGS = 5;
    private static final int PLACE_STEP = 7;

    /** Насколько неровным может быть след здания: деревня не строит на скале. */
    private static final int MAX_SLOPE = 2;

    /** Профессия, с которой игрок разговаривает. Данными задан только её файл. */
    public static final Identifier ELDER = new Identifier(VillagePax.MOD_ID, "elder");

    private Villages() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Villages::tick);
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
                found(world, site.culture(), site.where());
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

        // Трое сразу: без строителя не встанет ничего, без старейшины
        // не с кем говорить, а один житель на деревню — это не деревня.
        settle(world, village, Founding.firstBuilder(cultureId, culture, random));
        settle(world, village, elder(cultureId, culture, random));
        settle(world, village, Founding.newCitizen(cultureId, culture, random));

        // Дом и ферма уже стоят: деревня старше игрока.
        for (Identifier type : startingBuildings(culture)) {
            raiseNow(world, manager, village, type);
        }
        // А это она строит при игроке.
        planNext(world, manager, village, culture);

        Levels.refresh(village);
        VillagePax.LOGGER.info("Деревня {} народа {} встала на {}",
                village.name(), cultureId, site.toShortString());
        return Optional.of(village);
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

        // Сперва выручка, потом покупки: деревня тратит заработанное
        // сегодня, а не ждёт следующего утра, чтобы им распорядиться.
        Warehouse warehouse = Warehouse.of(world, village);
        earn(world, village, warehouse);
        deliver(world, village, warehouse);
        planNext(world, manager, village, culture);
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
            warehouse.addOrScatter(world, village.center(), new ItemStack(Trading.COIN, coin));
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
            if (!warehouse.take(Trading.COIN, cost)) {
                continue;
            }
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

        for (Identifier type : culture.buildings()) {
            Identifier schematicId = new Identifier(type.getNamespace(), type.getPath() + "_lvl1");
            if (SchematicLoader.get(schematicId).isEmpty() || isTownHall(type)) {
                continue;
            }
            if (place(world, manager, village, schematicId).isPresent()) {
                return;
            }
        }
    }

    /** Здание, которое встаёт сразу и целиком: деревня уже стояла до игрока. */
    private static void raiseNow(ServerWorld world, SettlementManager manager, Settlement village,
                                 Identifier type) {
        Identifier schematicId = new Identifier(type.getNamespace(), type.getPath() + "_lvl1");
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return;
        }

        Building site = place(world, manager, village, schematicId).orElse(null);
        if (site == null) {
            return;
        }

        // Материалы на уже стоящее здание не спрашивают: его построили
        // до прихода игрока, и просить за него было бы не у кого.
        Warehouse warehouse = Warehouse.of(world, village);
        Materials.required(schematic).forEach((item, count) -> {
            int rest = count;
            while (rest > 0) {
                int chunk = Math.min(rest, item.getMaxCount());
                warehouse.addOrScatter(world, village.center(), new ItemStack(item, chunk));
                rest -= chunk;
            }
        });

        BuildJob.advance(world, manager, village.id(), site.id(), Integer.MAX_VALUE);
    }

    /**
     * Место для здания: кольцами от ратуши, первое подходящее.
     * <p>
     * Пригодность спрашивается у тех же правил, по которым размечает игрок
     * ({@link BuildOrders#check}) — границы, наложение следов, имя схемы.
     * Деревня не должна уметь того, чего не умеет игрок, иначе её застройка
     * начнёт выглядеть невозможной.
     */
    private static Optional<Building> place(ServerWorld world, SettlementManager manager,
                                            Settlement village, Identifier schematicId) {
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        for (BlockPos anchor : spots(world, village, schematic)) {
            for (BlockRotation rotation : BlockRotation.values()) {
                if (!(BuildOrders.check(village, schematicId, anchor, rotation)
                        instanceof BuildOrders.Result.Placed)) {
                    continue;
                }
                if (BuildOrders.place(manager, village, schematicId, anchor, rotation)
                        instanceof BuildOrders.Result.Placed placed) {
                    return Optional.of(placed.site());
                }
            }
        }
        return Optional.empty();
    }

    /** Возможные углы застройки: кольца вокруг ратуши по ровной земле. */
    private static List<BlockPos> spots(ServerWorld world, Settlement village, Schematic schematic) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos centre = village.center();

        for (int ring = 1; ring <= PLACE_RINGS; ring++) {
            int reach = ring * PLACE_STEP;
            for (int dx = -reach; dx <= reach; dx += PLACE_STEP) {
                for (int dz = -reach; dz <= reach; dz += PLACE_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != reach) {
                        continue;
                    }
                    BlockPos anchor = surface(world, centre.add(dx, 0, dz));
                    if (anchor != null && isFlatEnough(world, anchor, schematic)) {
                        spots.add(anchor);
                    }
                }
            }
        }
        return spots;
    }

    private static BlockPos surface(ServerWorld world, BlockPos column) {
        if (!world.isChunkLoaded(column)) {
            return null;
        }
        return new BlockPos(column.getX(),
                world.getTopY(Heightmap.Type.WORLD_SURFACE, column.getX(), column.getZ()),
                column.getZ());
    }

    /**
     * Ровность следа. Без этой проверки деревня охотно ставит дом на склон,
     * и половина его висит в воздухе, а другая утоплена в холм.
     */
    private static boolean isFlatEnough(ServerWorld world, BlockPos anchor, Schematic schematic) {
        Vec3i size = schematic.size();

        for (int dx = 0; dx < size.getX(); dx += Math.max(1, size.getX() - 1)) {
            for (int dz = 0; dz < size.getZ(); dz += Math.max(1, size.getZ() - 1)) {
                BlockPos corner = surface(world, anchor.add(dx, 0, dz));
                if (corner == null || Math.abs(corner.getY() - anchor.getY()) > MAX_SLOPE) {
                    return false;
                }
            }
        }
        return true;
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

    private static boolean isTownHall(Identifier type) {
        return Levels.isTownHallType(type);
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

    private static void settle(ServerWorld world, Settlement village, Citizen citizen) {
        citizen.setPosition(Vec3d.ofBottomCenter(village.center().up()));
        village.addCitizen(citizen);
        CitizenSpawner.spawnBody(world, village, citizen);
    }
}
