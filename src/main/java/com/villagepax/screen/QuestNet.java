package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.core.trade.Caravan;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.diplomacy.Alliance;
import com.villagepax.sim.diplomacy.Tribute;
import com.villagepax.sim.war.Campaigns;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.diplomacy.Gifts;
import com.villagepax.sim.diplomacy.Relations;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.quest.Progress;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.war.Peace;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.trade.Trading;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сеть экрана квестов: снимок разговора вниз, «отдаю» вверх.
 * <p>
 * Экран открывается <b>без контейнера</b>, в отличие от пульта ратуши.
 * Контейнер там нужен затем, что пульт умеет отдавать приказы колонии, и
 * сервер обязан проверять, что экран открыт. Здесь проверка другая и проще:
 * отдать квест можно только <b>стоя рядом с выдающим</b>. Расстояние
 * подделать нельзя, а контейнер ради одной кнопки был бы лишней машинерией.
 */
public final class QuestNet {

    public static final Identifier OPEN = new Identifier(VillagePax.MOD_ID, "quest_open");
    public static final Identifier HAND_IN = new Identifier(VillagePax.MOD_ID, "quest_hand_in");

    /**
     * «Торгую вот этим». Живёт здесь, а не в своём классе торга, потому что
     * проверка у него та же самая — <b>стоять рядом с выдающим</b>, — и
     * повторять правило близости в двух местах значило бы однажды его
     * разойти.
     */
    public static final Identifier TRADE = new Identifier(VillagePax.MOD_ID, "quest_trade");

    /**
     * «Дарю то, что в руке».
     * <p>
     * Ни предмета, ни числа в пакете нет, и это не экономия. Дарится
     * <b>то, что в руке</b>, а руку сервер видит сам: присланный предмет
     * пришлось бы искать в инвентаре, проверять, что он там есть, и
     * решать, какую из двух стопок брать, — три новых способа ошибиться
     * ради того, чтобы клиент сообщил серверу известное.
     */
    public static final Identifier GIFT = new Identifier(VillagePax.MOD_ID, "quest_gift");

    /**
     * «Плачу за мир».
     * <p>
     * Цены в пакете нет: её считает сервер по своей же формуле. Прислать
     * её значило бы дать клиенту назвать сумму — а это ровно тот случай,
     * когда доверять клиенту нельзя ни в одной игре.
     */
    public static final Identifier PEACE = new Identifier(VillagePax.MOD_ID, "quest_peace");
    public static final Identifier PACT = new Identifier(VillagePax.MOD_ID, "quest_pact");
    public static final Identifier LEVY = new Identifier(VillagePax.MOD_ID, "quest_levy");
    public static final Identifier MARCH = new Identifier(VillagePax.MOD_ID, "quest_march");

    /**
     * Насколько близко надо стоять, чтобы отдать.
     * <p>
     * Чуть больше вытянутой руки: игрок щёлкнул по жителю и мог сделать
     * полшага, пока читал. Меньше — и кнопка отказывала бы «ни с того
     * ни с сего».
     */
    private static final double TALK_RANGE = 8.0;

    private QuestNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(HAND_IN, (server, player, handler, buf, sender) -> {
            // Читать надо здесь: за пределами обработчика буфер освобождён.
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> handIn(player, village, giver));
        });

        ServerPlayNetworking.registerGlobalReceiver(GIFT, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> gift(player, village, giver));
        });

        ServerPlayNetworking.registerGlobalReceiver(PEACE, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> peace(player, village, giver));
        });

        ServerPlayNetworking.registerGlobalReceiver(PACT, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> pact(player, village, giver));
        });

        ServerPlayNetworking.registerGlobalReceiver(LEVY, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> levy(player, village, giver));
        });

        ServerPlayNetworking.registerGlobalReceiver(MARCH, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> march(player, village, giver));
        });

        ServerPlayNetworking.registerGlobalReceiver(TRADE, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            boolean villageSells = buf.readBoolean();
            Identifier goods = buf.readIdentifier();
            int count = buf.readVarInt();
            Optional<UUID> caravan = buf.readOptional(PacketByteBuf::readUuid);
            server.execute(() -> {
                if (caravan.isPresent()) {
                    tradeWithCaravan(player, caravan.get(), village, villageSells, goods, count);
                } else {
                    trade(player, village, giver, villageSells, goods, count);
                }
            });
        });
    }

    /**
     * Собрать снимок разговора.
     * <p>
     * Пусто, если у этой деревни нет ни выдающего, ни доверия к игроку, —
     * то есть говорить не о чем и экран открывать незачем.
     */
    /**
     * Профессия, от имени которой говорит обоз.
     * <p>
     * Квестов у неё нет и быть не должно: обоз пришёл торговать, а не
     * просить. Опознаватель нужен только затем, что снимок разговора
     * устроен вокруг «выдающего», и обозу тоже надо кем-то быть.
     */
    public static final Identifier MERCHANT = new Identifier(VillagePax.MOD_ID, "merchant");

    /**
     * Открыть торг с обозом.
     * <p>
     * Доверие и цены берутся у <b>пославшей деревни</b>: торговец её
     * человек, и грабить его — портить отношения с ней. А товар и монета
     * — у самой телеги: у обоза можно скупить всё, и тогда торговать
     * станет нечем до следующего раза.
     */
    public static void openCaravan(ServerPlayerEntity player, ServerWorld world,
                                   UUID hostId, UUID caravanId) {
        SettlementManager manager = SettlementManager.get(world);
        Settlement host = hostId == null ? null : manager.byId(hostId).orElse(null);
        Caravan guest = host == null ? null : host.visitor(caravanId).orElse(null);
        if (guest == null) {
            return;
        }

        Settlement home = manager.byId(guest.home()).orElse(null);
        if (home == null) {
            // Деревню снесли, пока обоз гостил. Торговать не с кем.
            player.sendMessage(Text.translatable("villagepax.caravan.homeless"), true);
            return;
        }

        SimpleInventory cart = cartOf(guest);
        viewOf(manager, home, player.getUuid(), player.getInventory(), MERCHANT,
                Warehouse.over(guest.stands(), cart), Optional.of(caravanId),
                ItemStack.EMPTY, Settlement.UNSEEN_DAY)
                .ifPresent(view -> send(player, view));
    }

    /**
     * Телега обоза как обычное хранилище: товар и монета вместе.
     * <p>
     * Монета кладётся стопками, а не числом, чтобы торг работал тем же
     * кодом, что и с деревней: ему всё равно, чей это склад.
     */
    private static SimpleInventory cartOf(Caravan guest) {
        SimpleInventory cart = new SimpleInventory(27);
        guest.cargo().contents().forEach((item, count) -> {
            net.minecraft.item.Item what = Registries.ITEM.get(item);
            int left = count;
            while (left > 0) {
                int chunk = Math.min(left, what.getMaxCount());
                cart.addStack(new ItemStack(what, chunk));
                left -= chunk;
            }
        });
        Coins.earn(cart, guest.purse());
        return cart;
    }

    /** И обратно: что осталось в телеге после сделки. */
    private static Caravan restocked(Caravan guest, SimpleInventory cart) {
        ItemTally left = new ItemTally();
        for (int slot = 0; slot < cart.size(); slot++) {
            ItemStack stack = cart.getStack(slot);
            if (!stack.isEmpty() && !Coins.isCoin(stack.getItem())) {
                left.add(Registries.ITEM.getId(stack.getItem()), stack.getCount());
            }
        }
        return guest.withCargo(left, Coins.total(cart));
    }

    /**
     * Разговор без подарка и без откупа — но <b>со днём</b>.
     * <p>
     * День был необязательным ровно до тех пор, пока от него зависела
     * только оценка подарка: не знаешь дня — не показывай подарок, беды
     * нет. Теперь от дня зависит само предложение: после писаной цепочки
     * деревня просит по поручению, а поручение выводится из дня. Короткий
     * вызов подставлял сюда {@link Settlement#UNSEEN_DAY}, и окно честно
     * показывало просьбу дня, которого не бывает, — а сдача принимала
     * просьбу сегодняшнего. Поймано проверкой «окно и сдача говорят одно».
     */
    public static Optional<QuestView> viewOf(SettlementManager manager, Settlement village,
                                             UUID id, Inventory carried, Identifier giver,
                                             Warehouse wares, long today) {
        return viewOf(manager, village, id, carried, giver, wares, Optional.empty(),
                ItemStack.EMPTY, today);
    }

    /**
     * Собрать снимок разговора целиком.
     * <p>
     * Менеджер нужен затем, что доверие <b>народа</b> считается по всем его
     * деревням, а не по той, у которой игрок стоит. Рука и день — затем, что
     * подарок оценивается до того, как его отдали: игрок должен видеть,
     * сколько возьмут и чего это будет стоить.
     */
    public static Optional<QuestView> viewOf(SettlementManager manager, Settlement village,
                                             UUID id, Inventory carried, Identifier giver,
                                             Warehouse wares, Optional<UUID> caravan,
                                             ItemStack held, long today) {
        int reputation = village.reputationOf(id);
        Standing standing = Standing.of(reputation);

        // Сегодняшний разговор: писаный квест, а когда цепочка пройдена —
        // суточное поручение. Через Quests.task, а не Quests.offered,
        // потому что поручения в датапаке нет: оно выводится из дня.
        Progress.Seeker seeker = Progress.Seeker.of(id, carried,
                colonyOf(manager, id), manager);
        Optional<QuestView.Offer> offer = Quests.task(village, id, giver, today)
                .map(task -> offerOf(seeker, task.quest(), reputation));

        // Прилавок собирается только тому, кто за ним стоит.
        //
        // Решение заказчика: «разделим обязанности». До сих пор старейшина
        // делал всё — давал квесты, принимал подарки, мирился и торговал, —
        // и деревня выглядела одним человеком с четырьмя руками. Теперь
        // товар у купца, а разговор у старейшины, и за каждым делом игрок
        // идёт к своему лицу.
        //
        // Кто именно держит прилавок, решает поселение: пока купца нет,
        // это по-прежнему старейшина. Правило «разговор не упирается
        // в тупик» старше разделения обязанностей.
        boolean keeper = caravan.isPresent() || giver.equals(Villages.counterKeeper(village));
        List<QuestView.Stall> stalls = keeper
                ? stalls(village, reputation, carried, wares) : List.of();

        return Optional.of(new QuestView(village.id(), village.name(), giver,
                standing.displayKey(), reputation, nextThreshold(standing), offer,
                stalls, Trading.purse(wares), caravan,
                peopleOf(manager, village, id),
                // У обоза подарка не берут: дарят в глаза деревне, а торговец
                // — гость на день, и доверие ему не его.
                caravan.isPresent() ? Optional.empty() : giftOf(village, id, held, today),
                // У обоза мира не просят по той же причине, что не дарят:
                // торговец пришёл торговать, а воюет деревня.
                caravan.isPresent() ? Optional.empty() : truceOf(village, id, carried, today),
                // Купцу нечего сказать, кроме цены: экран открывается
                // сразу на торге и лишних вкладок не показывает. А если
                // датапак однажды даст купцу квест, разговор вернётся —
                // молча потерять его нельзя.
                caravan.isEmpty() && giver.equals(Villages.MERCHANT) && offer.isEmpty(),
                caravan.isPresent() ? Optional.empty() : pactOf(manager, village, id, carried),
                caravan.isPresent() ? Optional.empty() : levyOf(manager, village, id, today)));
    }

    /**
     * Дань: идёт, доступна или названа целью.
     * <p>
     * Карточки нет вовсе у деревни, с которой не воевали: «дань: сперва
     * разбей их отряд» в разговоре с мирным соседом — это не цель,
     * а подсказка грабить. Зато у разбитой она появляется сразу, и у той,
     * что уже платит, — тоже: игрок должен видеть, сколько ему ещё несут.
     */
    private static Optional<QuestView.Levy> levyOf(SettlementManager manager, Settlement village,
                                                   UUID player, long today) {
        if (!village.owner().isAutonomous()) {
            return Optional.empty();
        }
        int days = village.owesTributeTo(player, today) ? village.tributeDaysLeft(today) : 0;
        boolean beaten = village.beatenOn() != Settlement.UNSEEN_DAY
                && today - village.beatenOn() <= Tribute.MEMORY;

        Settlement colony = colonyOf(manager, player);
        QuestView.March march = new QuestView.March(Campaigns.fightersFor(colony),
                Campaigns.judge(colony, village, player, today));

        // Карточка появляется и ради похода: деревню, которую ещё не били,
        // обобрать нельзя — но на неё можно пойти, и узнать об этом игрок
        // должен здесь же, а не из документации.
        if (days <= 0 && !beaten && !march.worthShowing()) {
            return Optional.empty();
        }
        return Optional.of(new QuestView.Levy(days,
                Tribute.judge(village, colony, player, today), march));
    }

    /**
     * Союз: заключён, предложен или назван целью.
     * <p>
     * Карточки нет вовсе, пока игрок деревне чужой: «союз: сперва
     * подружиться» в первом же разговоре с первой же деревней — это шум.
     * А знакомому она уже цель, и потому показывается даже тогда, когда
     * заключить его нельзя: запертая ступень, которую видно, — это то,
     * ради чего растут.
     */
    private static Optional<QuestView.Pact> pactOf(SettlementManager manager, Settlement village,
                                                   UUID player, Inventory carried) {
        if (!village.owner().isAutonomous()) {
            return Optional.empty();
        }
        if (!village.isAllyOf(player)
                && village.reputationOf(player) < Standing.KNOWN.from()) {
            return Optional.empty();
        }
        Alliance.Verdict verdict = Alliance.judge(village, colonyOf(manager, player),
                player, carried);
        return Optional.of(new QuestView.Pact(Alliance.PRICE, verdict));
    }

    /** Колония этого игрока, если она у него есть. */
    private static Settlement colonyOf(SettlementManager manager, UUID player) {
        for (Settlement candidate : manager.all()) {
            if (candidate.owner().isOwnedBy(player)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Народ деревни: как он смотрит на игрока и как — на соседей.
     * <p>
     * Считается здесь, а не на клиенте, по той же причине, что и цены:
     * культуры живут в датапаке <b>сервера</b>, и клиент про них не знает
     * ни имён, ни отношений.
     */
    private static QuestView.People peopleOf(SettlementManager manager, Settlement village,
                                             UUID player) {
        Identifier home = village.culture();
        Culture culture = CultureManager.get(home);
        String name = culture == null ? home.toString() : culture.displayName();

        List<QuestView.Neighbour> neighbours = new ArrayList<>();
        for (Identifier other : CultureManager.ids()) {
            Culture theirs = CultureManager.get(other);
            if (other.equals(home) || theirs == null) {
                continue;
            }
            neighbours.add(new QuestView.Neighbour(theirs.displayName(),
                    Relations.attitudeLadder(home, other).displayKey()));
        }

        int trust = Relations.trustOfPeople(manager, home, player);
        return new QuestView.People(name, Standing.of(trust).displayKey(), trust, neighbours);
    }

    /** Что выйдет, если подарить то, что в руках, — до того, как отдал. */
    private static Optional<QuestView.Gift> giftOf(Settlement village, UUID player,
                                                   ItemStack held, long today) {
        Gifts.Verdict verdict = Gifts.judge(village, player, held, today);
        if (verdict == Gifts.Verdict.EMPTY_HANDED) {
            // Пустая рука — не отказ, а нечего показывать: карточка подарка
            // в этом случае молчит, а не краснеет.
            return Optional.empty();
        }
        int take = Math.max(1, Gifts.takeableFrom(village, held));
        int trust = Gifts.trustFor(Gifts.worthOf(village, held.copyWithCount(take)));
        return Optional.of(new QuestView.Gift(held.getItem(), take, trust, verdict));
    }

    /**
     * Война с этим народом, какой её видит игрок, — или пусто, если её нет.
     * <p>
     * Пусто — это и «мы не воюем», и «деревня не воюет ни с кем»: карточка
     * должна появляться <b>только когда есть о чём говорить</b>.
     */
    private static Optional<QuestView.Truce> truceOf(Settlement village, UUID player,
                                                     Inventory carried, long today) {
        if (!village.owner().isAutonomous()) {
            return Optional.empty();
        }
        int trust = village.reputationOf(player);
        int fighters = Raids.fightersFor(trust);
        int left = village.truceDaysLeft(today);
        if (fighters <= 0 && left <= 0) {
            return Optional.empty();
        }
        int price = Peace.price(trust);
        return Optional.of(new QuestView.Truce(price, Coins.has(carried, price), left, fighters));
    }

    /**
     * Заплатить за мир.
     * <p>
     * Проверка та же, что у подарка и у квеста: <b>стоять рядом</b>.
     * Мириться приходят в деревню, и дорога туда под набегом — часть цены.
     */
    private static void peace(ServerPlayerEntity player, UUID village, Identifier giver) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(village).orElse(null);
        if (home == null) {
            return;
        }
        if (nearbyGiver(player, home, giver) == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Peace.Outcome outcome = Peace.buy(world, manager, home, player.getUuid(),
                player.getInventory(), Schedule.dayOf(world.getTimeOfDay()),
                // Сдача, которой не нашлось места, падает под ноги: терять
                // деньги игрока молча нельзя.
                left -> player.getInventory().offerOrDrop(left));

        if (outcome.bought()) {
            player.sendMessage(Text.translatable("villagepax.peace.bought",
                    Text.literal(home.name()), Coins.spell(outcome.price()),
                    Text.literal(String.valueOf(outcome.days()))), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
            // Отряд, если он уже под воротами, разворачивается сейчас же:
            // «мир куплен, а эти пусть добьют» было бы издевательством.
            Raids.callOff(world, manager, player.getUuid(), village);
        } else {
            outcome.verdict().reasonKey().ifPresent(key ->
                    player.sendMessage(Text.translatable(key), true));
        }
        refresh(player, manager, village, giver);
    }

    /**
     * Потребовать дань.
     * <p>
     * Ни монеты, ни подарка: платят здесь не игроку, а <b>с игрока</b> —
     * союзом, который разрывается, и доверием, которое будет падать
     * с каждым платежом. Поэтому кнопка ничего не тратит из сумки,
     * и поэтому же нажимать её стоит с открытыми глазами.
     */
    private static void levy(ServerPlayerEntity player, UUID village, Identifier giver) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(village).orElse(null);
        if (home == null) {
            return;
        }
        if (nearbyGiver(player, home, giver) == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Tribute.Outcome outcome = Tribute.demand(manager, home,
                colonyOf(manager, player.getUuid()), player.getUuid(),
                Schedule.dayOf(world.getTimeOfDay()));

        if (outcome.taken()) {
            player.sendMessage(Text.translatable("villagepax.tribute.taken",
                    Text.literal(home.name()),
                    Text.literal(String.valueOf(outcome.days()))), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
        } else {
            outcome.verdict().reasonKey().ifPresent(key ->
                    player.sendMessage(Text.translatable(key), true));
        }
        refresh(player, manager, village, giver);
    }

    /**
     * Отдать приказ о походе.
     * <p>
     * Тем же порядком, что и требование дани, и рядом с ним: это одна
     * и та же дорога. Разница в том, что дань берут словом, а поход
     * стоит людей, — и потому приговор считает сервер, а не кнопка.
     */
    private static void march(ServerPlayerEntity player, UUID village, Identifier giver) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(village).orElse(null);
        if (home == null) {
            return;
        }
        if (nearbyGiver(player, home, giver) == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Campaigns.Verdict verdict = Campaigns.march(world, manager,
                colonyOf(manager, player.getUuid()), home, player.getUuid(),
                Schedule.dayOf(world.getTimeOfDay()));

        if (verdict.ready()) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO,
                    SoundCategory.NEUTRAL, 1.0f, 0.7f);
        } else {
            verdict.reasonKey().ifPresent(key ->
                    player.sendMessage(Text.translatable(key), true));
        }
        refresh(player, manager, village, giver);
    }

    /**
     * Заключить союз.
     * <p>
     * Тем же порядком, что и откуп: проверить, что старейшина рядом,
     * отдать дар, сказать словами, обновить экран. Разница одна — за союз
     * не воюют, поэтому и разворачивать некого.
     */
    private static void pact(ServerPlayerEntity player, UUID village, Identifier giver) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(village).orElse(null);
        if (home == null) {
            return;
        }
        if (nearbyGiver(player, home, giver) == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Alliance.Outcome outcome = Alliance.forge(world, manager, home,
                colonyOf(manager, player.getUuid()), player.getUuid(), player.getInventory(),
                Schedule.dayOf(world.getTimeOfDay()),
                left -> player.getInventory().offerOrDrop(left));

        if (outcome.forged()) {
            player.sendMessage(Text.translatable("villagepax.pact.forged",
                    Text.literal(home.name()), Coins.spell(outcome.price())), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
        } else {
            outcome.verdict().reasonKey().ifPresent(key ->
                    player.sendMessage(Text.translatable(key), true));
        }
        refresh(player, manager, village, giver);
    }

    /**
     * Подарить то, что в руке.
     * <p>
     * Проверка та же, что у сдачи квеста: <b>стоять рядом с выдающим</b>.
     * Дарят в глаза, а не почтой.
     */
    private static void gift(ServerPlayerEntity player, UUID village, Identifier giver) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(village).orElse(null);
        if (home == null) {
            return;
        }
        if (nearbyGiver(player, home, giver) == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Gifts.Outcome outcome = Gifts.give(manager, home, player.getUuid(),
                player.getMainHandStack(), Schedule.dayOf(world.getTimeOfDay()));

        if (outcome.accepted()) {
            player.sendMessage(Text.translatable("villagepax.gift.thanks",
                    Text.literal(home.name()), outcome.given().getName(),
                    Text.literal("+" + outcome.trust())), false);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
            Relations.tell(player, outcome.shifts());
        } else {
            outcome.verdict().reasonKey().ifPresent(key ->
                    player.sendMessage(Text.translatable(key), true));
        }
        refresh(player, manager, village, giver);
    }

    /**
     * Стол торга, каким его увидит игрок.
     * <p>
     * Порядок сохраняется тот, что в датапаке: автор выложил товар в
     * осмысленном порядке, и переставлять его по цене или по достатку
     * означало бы, что прилавок меняется на глазах.
     */
    private static List<QuestView.Stall> stalls(Settlement village, int reputation,
                                                Inventory carried, Warehouse wares) {
        List<QuestView.Stall> stalls = new ArrayList<>();
        int purse = Trading.purse(wares);

        for (Trading.Side side : Trading.Side.values()) {
            boolean sells = side == Trading.Side.VILLAGE_SELLS;
            for (TradeTable.Deal deal : Trading.dealsOn(village, side)) {
                // Цена — уже с наценкой по доверию: экран показывает ровно
                // то, что случится с кошельком, а не цену из датапака.
                int price = Trading.priceFor(deal, side, reputation);
                stalls.add(new QuestView.Stall(deal.item(), deal.count(), price, sells,
                        readiness(deal, sells, reputation, price, carried, wares, purse)));
            }
        }
        return stalls;
    }

    /**
     * Почему сделка не идёт — если не идёт.
     * <p>
     * Порядок причин важнее, чем кажется: <b>недоверие называется первым</b>.
     * Иначе игроку сообщили бы «у тебя нет монеты» про товар, который ему
     * всё равно не продадут, и он пошёл бы искать изумруды напрасно.
     */
    private static QuestView.Ready readiness(TradeTable.Deal deal, boolean villageSells,
                                             int reputation, int price, Inventory carried,
                                             Warehouse wares, int purse) {
        if (!deal.open(reputation)) {
            return QuestView.Ready.NO_TRUST;
        }
        if (villageSells) {
            if (!wares.has(deal.item(), deal.count())) {
                return QuestView.Ready.VILLAGE_CANT;
            }
            return Coins.has(carried, price)
                    ? QuestView.Ready.YES : QuestView.Ready.PLAYER_CANT;
        }
        if (purse < price) {
            return QuestView.Ready.VILLAGE_CANT;
        }
        return carried.count(deal.item()) >= deal.count()
                ? QuestView.Ready.YES : QuestView.Ready.PLAYER_CANT;
    }

    /**
     * Требования с уже посчитанным «сколько есть» и награды готовыми
     * строками: клиент не должен знать ни про инвентарь, ни про датапак.
     */
    private static QuestView.Offer offerOf(Progress.Seeker seeker, Quest quest, int reputation) {
        List<QuestView.Need> needs = new ArrayList<>();
        boolean ready = reputation >= quest.minReputation();

        // Через то же правило, каким считает сдача. Раньше экран считал
        // «сколько принесено» сам, и пока цель была одна, это сходилось;
        // с целями «поставь склад» и «подружись с майя» разошлось бы
        // в первый же день — галочка в окне, отказ при сдаче.
        for (Quest.Objective objective : quest.objectives()) {
            Progress.Step step = Progress.of(objective, seeker);
            needs.add(new QuestView.Need(step.key(), step.item(), step.what(),
                    step.need(), step.have()));
            ready = ready && step.enough();
        }

        if (seeker.colony().isEmpty() && Progress.needsAColony(quest)) {
            ready = false;
        }

        List<QuestView.Prize> rewards = new ArrayList<>();
        for (Quest.Reward reward : quest.rewards()) {
            if (reward instanceof Quest.Reward.Give give) {
                rewards.add(QuestView.Prize.goods(give.item(), give.count()));
            } else if (reward instanceof Quest.Reward.Trust trust) {
                rewards.add(QuestView.Prize.trust(trust.amount()));
            } else if (reward instanceof Quest.Reward.Settler) {
                // Человек — не вещь и не доверие, и подписан он своим
                // ключом: «к тебе переселится человек».
                rewards.add(new QuestView.Prize(QuestView.Prize.SETTLER, Optional.empty(), 1));
            } else if (reward instanceof Quest.Reward.Grace grace) {
                rewards.add(new QuestView.Prize(QuestView.Prize.GRACE, Optional.empty(),
                        grace.amount()));
            }
        }

        return new QuestView.Offer(quest.dialogue(), needs, rewards, ready);
    }

    /** С какого числа начинается следующая ступень доверия. */
    private static Optional<Integer> nextThreshold(Standing standing) {
        Standing[] ladder = Standing.values();
        int next = standing.ordinal() + 1;
        return next < ladder.length ? Optional.of(ladder[next].from()) : Optional.empty();
    }

    public static void send(ServerPlayerEntity player, QuestView view) {
        DataResult<?> encoded = QuestView.CODEC.encodeStart(NbtOps.INSTANCE, view);
        NbtCompound nbt = (NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "снимок разговора не кодируется: "
                        + encoded.error().map(Object::toString).orElse("причина неизвестна")));

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, OPEN, buf);
    }

    /**
     * Битый снимок — не причина ронять клиент: экран просто не откроется,
     * а в логе останется причина.
     */
    public static Optional<QuestView> read(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return Optional.empty();
        }

        DataResult<QuestView> decoded = QuestView.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.error().ifPresent(error ->
                VillagePax.LOGGER.warn("Разговор не читается: {}", error.message()));
        return decoded.result();
    }

    /**
     * Отдать принесённое.
     * <p>
     * Проверяется <b>расстояние до живого выдающего</b>, а не открытый экран:
     * пакет от клиента, чей игрок стоит на другом конце карты, отвергается
     * молча. Иначе кнопка в интерфейсе превратилась бы в способ сдавать
     * квесты откуда угодно.
     */
    private static void handIn(ServerPlayerEntity player, UUID village, Identifier giver) {
        SettlementManager manager = SettlementManager.get(player.getServerWorld());
        Settlement colony = manager.byId(village).orElse(null);
        if (colony == null) {
            return;
        }

        Citizen elder = nearbyGiver(player, colony, giver);
        if (elder == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Quests.talk(manager, colony, player, elder);
        refresh(player, manager, village, giver);
    }

    /**
     * Сторговаться.
     * <p>
     * Сделку присылает клиент, но <b>верит сервер только столу торга</b>:
     * присланные предмет и число лишь ищут строку в датапаке, а цену
     * сервер считает сам — по ней же и по доверию. Иначе подложенный пакет
     * назначал бы цену сам, и бревно стоило бы деревне шестьдесят четыре
     * изумруда.
     */
    private static void trade(ServerPlayerEntity player, UUID village, Identifier giver,
                              boolean villageSells, Identifier goods, int count) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement colony = manager.byId(village).orElse(null);
        if (colony == null) {
            return;
        }
        if (nearbyGiver(player, colony, giver) == null) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Trading.Side side = villageSells
                ? Trading.Side.VILLAGE_SELLS : Trading.Side.VILLAGE_BUYS;
        TradeTable.Deal deal = Trading
                .find(colony, side, Registries.ITEM.get(goods), count)
                .orElse(null);
        if (deal == null) {
            // Датапак перечитали, пока экран был открыт: сделки больше нет.
            player.sendMessage(Text.translatable("villagepax.trade.gone"), true);
            refresh(player, manager, village, giver);
            return;
        }

        Warehouse wares = Warehouse.of(world, colony);
        Trading.Outcome[] outcome = new Trading.Outcome[1];
        // Через update: доверие за сделку — состояние поселения, и его надо
        // сохранить. Склад сохраняет себя сам, он в блок-энтити.
        manager.update(village, state -> outcome[0] = Trading.trade(state, player.getUuid(),
                player.getInventory(), wares, side, deal,
                // Сдача, которой не нашлось места, падает под ноги: терять
                // деньги игрока молча нельзя.
                left -> player.getInventory().offerOrDrop(left)));

        if (outcome[0] == Trading.Outcome.DONE) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_TRADE,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
        } else {
            player.sendMessage(Text.translatable("villagepax.trade.refused."
                    + outcome[0].id()), true);
        }
        refresh(player, manager, village, giver);
    }

    /**
     * Сделка с обозом.
     * <p>
     * Проверка близости здесь своя: у обоза нет жителя, к которому можно
     * подойти, — есть телега на земле. Расстояние до неё подделать
     * так же нельзя, как и расстояние до старейшины.
     */
    private static void tradeWithCaravan(ServerPlayerEntity player, UUID caravanId,
                                         UUID homeId, boolean villageSells, Identifier goods,
                                         int count) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);

        Settlement foundHost = null;
        Caravan foundGuest = null;
        for (Settlement candidate : manager.all()) {
            Caravan found = candidate.visitor(caravanId).orElse(null);
            if (found != null) {
                foundHost = candidate;
                foundGuest = found;
                break;
            }
        }
        // Дальше — только неизменяемые: их читают лямбды.
        final Settlement host = foundHost;
        final Caravan guest = foundGuest;
        if (guest == null) {
            player.sendMessage(Text.translatable("villagepax.caravan.left"), true);
            return;
        }
        if (player.squaredDistanceTo(Vec3d.ofCenter(guest.stands())) > TALK_RANGE * TALK_RANGE) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }

        Settlement home = manager.byId(homeId).orElse(null);
        if (home == null) {
            player.sendMessage(Text.translatable("villagepax.caravan.homeless"), true);
            return;
        }

        Trading.Side side = villageSells
                ? Trading.Side.VILLAGE_SELLS : Trading.Side.VILLAGE_BUYS;
        TradeTable.Deal deal = Trading
                .find(home, side, Registries.ITEM.get(goods), count).orElse(null);
        if (deal == null) {
            player.sendMessage(Text.translatable("villagepax.trade.gone"), true);
            return;
        }

        SimpleInventory cart = cartOf(guest);
        Trading.Outcome[] outcome = new Trading.Outcome[1];
        manager.update(homeId, state -> outcome[0] = Trading.trade(state, player.getUuid(),
                player.getInventory(), Warehouse.over(guest.stands(), cart), side, deal,
                left -> player.getInventory().offerOrDrop(left)));

        if (outcome[0] == Trading.Outcome.DONE) {
            Caravan fresh = restocked(guest, cart);
            manager.update(host.id(), state -> state.restock(fresh));
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_TRADE,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
        } else {
            player.sendMessage(Text.translatable("villagepax.trade.refused."
                    + outcome[0].id()), true);
        }

        openCaravan(player, world, host.id(), caravanId);
    }

    /**
     * Переслать снимок заново.
     * <p>
     * После любого действия, а не только удачного: отказ тоже меняет
     * картину — доверие могло вырасти за прошлую сделку, а товар на складе
     * кончиться. Экран, показывающий вчерашний прилавок, обманывает.
     */
    private static void refresh(ServerPlayerEntity player, SettlementManager manager, UUID village,
                                Identifier giver) {
        manager.byId(village).ifPresent(fresh -> viewOf(manager, fresh, player.getUuid(),
                        player.getInventory(), giver,
                        Warehouse.of(player.getServerWorld(), fresh), Optional.empty(),
                        player.getMainHandStack(),
                        Schedule.dayOf(player.getServerWorld().getTimeOfDay()))
                .ifPresent(view -> send(player, view)));
    }

    /** Выдающий этой деревни, стоящий рядом с игроком. */
    private static Citizen nearbyGiver(ServerPlayerEntity player, Settlement village,
                                       Identifier giver) {
        for (Citizen citizen : village.citizens()) {
            if (citizen.profession().filter(giver::equals).isEmpty()) {
                continue;
            }
            if (citizen.position()
                    .filter(where -> where.squaredDistanceTo(player.getPos())
                            <= TALK_RANGE * TALK_RANGE)
                    .isPresent()) {
                return citizen;
            }
        }
        return null;
    }
}
