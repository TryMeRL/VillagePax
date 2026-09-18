package com.villagepax.sim.trade;

import com.villagepax.VillagePax;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.Building;
import com.villagepax.core.trade.Caravan;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.Looks;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Ground;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.Villages;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.diplomacy.Relations;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Обозы: кто их посылает, где они стоят и когда уходят.
 * <p>
 * Исполнение обещания из плана — «привоз станет настоящим обозом, который
 * можно перехватить или защитить». Деревня народа посылает торговца
 * к колонии игрока: тот стоит день у ратуши, торгует своим товаром и
 * уходит. Так у колонии игрока <b>появляется торговля вообще</b>: своего
 * старейшины у неё нет, и до сих пор продать ей было некому и нечего.
 * <p>
 * <b>Товар обоз берёт из деревни, а не из воздуха.</b> Это несущее решение:
 * иначе торговля печатала бы вещи, и деревня стала бы бездонным сундуком
 * с ногами. Значит, обоз идёт только от той деревни, у которой есть что
 * продать, — а есть у неё то, что вырастили её собственные фермеры.
 * Богатая деревня торгует, бедная сидит дома, и это видно.
 * <p>
 * <b>Дорога — это время.</b> Между деревней и колонией лежат сотни блоков
 * незагруженных чанков; вести по ним живого моба нельзя ни дёшево, ни
 * честно — половины дороги просто не существует, пока туда не придёт
 * игрок. Поэтому обоз в пути — запись с днём прихода, а телом он
 * становится там, где его увидят.
 */
public final class Caravans {

    /** Как часто осматриваются гости. Пять раз в секунду не нужно. */
    private static final int EVERY = 100;

    /**
     * Стоит ли в этом поселении рынок.
     * <p>
     * Рынок — не «ларёк побольше», а <b>место, ради которого делают крюк</b>:
     * спрашивается он у данных, а не по имени здания. Народ, назвавший свой
     * рынок иначе, получит то же самое, пока в типе написано «здесь работает
     * купец» и «нужна столица».
     */
    private static boolean hasMarket(Settlement colony) {
        for (Building building : colony.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            if (BuildingTypes.get(building.type())
                    .filter(type -> type.minLevel() == SettlementLevel.CAPITAL)
                    .filter(type -> type.employs(Villages.MERCHANT)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Как далеко деревня посылает обоз.
     * <p>
     * Пятьсот блоков — примерно день пути пешком. Дальше не посылают
     * не из скупости: обоз, идущий неделю, игрок не свяжет с деревней,
     * которую видел когда-то.
     */
    private static final int REACH = 512;

    /** Сколько дней между обозами от одной деревни. */
    private static final int EVERY_DAYS = 3;

    /**
     * А к столице с рынком — каждый день.
     * <p>
     * Это и есть награда за четвёртую ступень, и она из тех, которые
     * видно, не открывая пульта: у ворот стоит чужой обоз, и стоит он
     * там каждое утро. Ступень, дающая только предел населения, наградой
     * не ощущается — это уже проходили.
     */
    private static final int MARKET_DAYS = 1;

    /** Сколько видов товара везёт и по сколько штук каждого. */
    private static final int KINDS = 4;
    private static final int PER_KIND = 16;

    /** Сколько монеты берёт на закупки, в медяках. */
    private static final int PURSE = 48;

    /** На сколько падает доверие деревни, если её торговца убили. */
    private static final int ROBBERY_COSTS = 25;

    private Caravans() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Caravans::tick);
    }

    /**
     * Суточное решение деревни: посылать ли обоз.
     * <p>
     * Зовётся оттуда же, откуда считаются суточные нужды, — на смене дня
     * и ровно один раз за день.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement village) {
        sendIfDue(world, manager, village, Schedule.dayOf(world.getTimeOfDay()));
    }

    /**
     * То же, но с днём числом.
     * <p>
     * День приходит снаружи <b>ради проверяемости</b>: «раз в три дня»
     * иначе никак не проверить — тест не может прождать трое игровых
     * суток, а шесть вызовов в одном тике для мода один и тот же день.
     * Так это и вскрылось: проверка звала «новый день» шесть раз и
     * удивлялась, что обоза нет.
     */
    public static void sendIfDue(ServerWorld world, SettlementManager manager,
                                 Settlement village, long today) {
        if (Trading.tableOf(village).isEmpty()) {
            return;
        }

        Settlement colony = nearestColony(manager, village);
        if (colony == null || alreadyVisiting(colony, village)) {
            return;
        }

        // Срок считается ПОСЛЕ того, как нашлась колония: он зависит от неё.
        // Не каждый день и у каждой деревни свой день — иначе все обозы
        // мира приходили бы одним утром; а к рынку — каждый день.
        int every = hasMarket(colony) ? MARKET_DAYS : EVERY_DAYS;
        if (Math.floorMod(today + village.id().hashCode(), every) != 0) {
            return;
        }

        BlockPos stands = standSpot(world, colony);
        if (stands == null) {
            // Некуда поставить: чанк не загружен или у ратуши негде стоять.
            // Придёт в следующий раз.
            return;
        }

        ItemTally cargo = loadUp(world, village);
        int purse = takePurse(world, village);
        if (cargo.isEmpty() && purse <= 0) {
            // Деревне нечем торговать. Это не сбой, а ответ: обоз идёт
            // от того, у кого есть что продать.
            return;
        }

        Caravan caravan = new Caravan(UUID.randomUUID(), village.id(), village.culture(),
                stands, cargo, purse, today + 1);
        manager.update(colony.id(), state -> state.welcome(caravan));

        announce(world, colony, village);
        VillagePax.LOGGER.info("Обоз из {} пришёл к {}: {} видов товара, {} медяков",
                village.name(), colony.name(), cargo.contents().size(), purse);
    }

    /**
     * Каждые сто тиков: поставить телом тех, кого видно, и проводить
     * тех, чей день прошёл.
     */
    static void tick(ServerWorld world) {
        if (world.getTime() % EVERY != 0) {
            return;
        }

        SettlementManager manager = SettlementManager.get(world);
        // Тем же счётом дней, что и суточные нужды, и набеги: часы в моде
        // должны быть одни. Иначе «сегодня» у обоза и «сегодня» у деревни
        // разойдутся после первой же команды /time set.
        long today = Schedule.dayOf(world.getTimeOfDay());

        for (Settlement settlement : List.copyOf(manager.all())) {
            for (Caravan guest : List.copyOf(settlement.visitors())) {
                if (today > guest.leavesOn() || !guest.hasAnything()) {
                    seeOff(world, manager, settlement, guest);
                    continue;
                }
                if (world.isChunkLoaded(guest.stands()) && bodyOf(world, guest) == null) {
                    spawnMerchant(world, settlement, guest);
                }
            }
        }
    }

    /** Тело торговца этого обоза, если оно есть в мире. */
    public static CitizenEntity bodyOf(ServerWorld world, Caravan guest) {
        Box around = new Box(guest.stands()).expand(24);
        for (CitizenEntity body : world.getEntitiesByClass(CitizenEntity.class, around,
                alive -> guest.id().equals(alive.caravanId()))) {
            return body;
        }
        return null;
    }

    /**
     * Поставить торговца.
     * <p>
     * Тело — <b>не житель</b>, и это важно. Житель принадлежит поселению:
     * его кормят, ему дают кровать, его тикает стратегия и тянет домой
     * привязь. Торговец пришёл на день из деревни за пятьсот блоков —
     * всё это ему не нужно и всё это его бы утащило. Поэтому он кукла:
     * стоит, торгует, уходит.
     */
    private static void spawnMerchant(ServerWorld world, Settlement host, Caravan guest) {
        CitizenEntity body = CitizenSpawner.spawnPuppet(world, guest.stands());
        if (body == null) {
            return;
        }

        body.linkCaravan(host.id(), guest.id());
        // Торговец обоза — курьер своего народа: с сумкой через плечо.
        body.setLook(Looks.puppet(guest.culture(), "courier"));
        // Привязь к телеге, и короткая. Кукла унаследовала от жителя
        // прогулку и без привязи ушла бы гулять по колонии — а игрок
        // пришёл бы к телеге и не нашёл торговца.
        body.setPositionTarget(guest.stands(), 4);
        body.setCustomName(Text.translatable("villagepax.caravan.merchant",
                Text.translatable("villagepax.culture." + guest.culture().getPath())));
        body.setCustomNameVisible(true);
        body.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,
                new ItemStack(ModItems.COIN));
    }

    /** Проводить обоз: тело убрать, запись забыть. */
    public static void seeOff(ServerWorld world, SettlementManager manager, Settlement host,
                              Caravan guest) {
        CitizenEntity body = bodyOf(world, guest);
        if (body != null) {
            body.discard();
        }
        manager.update(host.id(), state -> state.seeOff(guest.id()));
    }

    /**
     * Торговца убили: товар и монета рассыпаются, а деревня это запомнит.
     * <p>
     * Это и есть «перехватить» из плана. Грабёж возможен и наказуем:
     * товар достаётся грабителю, но доверие той деревни падает, и её
     * старейшина перестанет и говорить, и торговать.
     */
    public static void robbed(ServerWorld world, CitizenEntity body, UUID killer) {
        SettlementManager manager = SettlementManager.get(world);
        Settlement host = body.caravanHost().flatMap(manager::byId).orElse(null);
        UUID caravanId = body.caravanId();
        if (host == null || caravanId == null) {
            return;
        }

        Caravan guest = host.visitor(caravanId).orElse(null);
        if (guest == null) {
            return;
        }

        guest.cargo().contents().forEach((item, count) -> {
            int left = count;
            net.minecraft.item.Item what = Registries.ITEM.get(item);
            while (left > 0) {
                int chunk = Math.min(left, what.getMaxCount());
                net.minecraft.util.ItemScatterer.spawn(world, body.getX(), body.getY(),
                        body.getZ(), new ItemStack(what, chunk));
                left -= chunk;
            }
        });
        if (guest.purse() > 0) {
            net.minecraft.inventory.SimpleInventory dropped =
                    new net.minecraft.inventory.SimpleInventory(9);
            Coins.earn(dropped, guest.purse());
            net.minecraft.util.ItemScatterer.spawn(world, body.getBlockPos(), dropped);
        }

        manager.update(host.id(), state -> state.seeOff(caravanId));
        if (killer != null) {
            // Через поступок, а не правкой доверия на месте: об ограблении
            // узнают и свои пославшей деревни, и её соседи. Для тех, кто
            // на этот народ косится, чужая беда — не беда: формула эха
            // разворачивает знак сама.
            manager.byId(guest.home()).ifPresent(home -> {
                List<Relations.Shift> shifts =
                        Relations.deed(manager, home, killer, -ROBBERY_COSTS);
                ServerPlayerEntity thief = world.getServer().getPlayerManager()
                        .getPlayer(killer);
                if (thief != null) {
                    Relations.tell(thief, shifts);
                }
            });
        }
        VillagePax.LOGGER.info("Обоз из {} разграблен у {}", guest.culture(), host.name());
    }

    // --- сборы в дорогу ---

    /**
     * Что деревня даёт обозу: то, что она продаёт, и то, что у неё есть.
     * <p>
     * Берётся со склада по-настоящему. Обоз, груженный из воздуха,
     * означал бы бесконечный товар, и продавать деревне стало бы
     * незачем — у неё и так всё есть.
     */
    private static ItemTally loadUp(ServerWorld world, Settlement village) {
        Warehouse warehouse = Warehouse.reach(world, village);
        ItemTally cargo = new ItemTally();
        int kinds = 0;

        for (TradeTable.Deal deal : Trading.dealsOn(village, Trading.Side.VILLAGE_SELLS)) {
            if (kinds >= KINDS) {
                break;
            }
            int have = warehouse.count(deal.item());
            int take = Math.min(PER_KIND, have - deal.count());
            if (take <= 0) {
                // Последнее не отдают: деревне нужно чем торговать и дома.
                continue;
            }
            if (warehouse.take(deal.item(), take)) {
                cargo.add(Registries.ITEM.getId(deal.item()), take);
                kinds++;
            }
        }
        return cargo;
    }

    /** И сколько монеты: тоже настоящей, из её кошеля. */
    private static int takePurse(ServerWorld world, Settlement village) {
        Warehouse warehouse = Warehouse.reach(world, village);
        int purse = Math.min(PURSE, Coins.total(warehouse.coins()) / 2);
        if (purse <= 0) {
            return 0;
        }
        Coins.pay(warehouse.coins(), purse);
        return purse;
    }

    /** Ближайшая колония игрока в пределах дневного пути. */
    private static Settlement nearestColony(SettlementManager manager, Settlement village) {
        Settlement best = null;
        double bestAway = (double) REACH * REACH;

        for (Settlement candidate : manager.all()) {
            if (candidate.owner().isAutonomous() || candidate.id().equals(village.id())) {
                continue;
            }
            double away = candidate.center().getSquaredDistance(village.center());
            if (away <= bestAway) {
                bestAway = away;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean alreadyVisiting(Settlement colony, Settlement village) {
        return colony.visitors().stream().anyMatch(guest -> guest.home().equals(village.id()));
    }

    /**
     * Где обоз встанет: у ратуши, но не в ней.
     * <p>
     * Кольцами от середины колонии, первое место, где может стоять
     * человек. В самой ратуше торговцу стоять негде — там блок. Поиск
     * общий с отрядами набега: правило «где может встать человек»
     * должно быть одно, иначе однажды разойдётся.
     */
    private static BlockPos standSpot(ServerWorld world, Settlement colony) {
        return Ground.spotNear(world, colony.center(), 2, 5);
    }

    private static void announce(ServerWorld world, Settlement colony, Settlement village) {
        colony.owner().player().ifPresent(owner -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) {
                player.sendMessage(Text.translatable("villagepax.caravan.arrived",
                        Text.literal(village.name()),
                        Text.translatable("villagepax.culture."
                                + village.culture().getPath())), false);
            }
        });
    }
}
