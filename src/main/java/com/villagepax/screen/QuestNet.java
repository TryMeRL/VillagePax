package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.core.trade.TradeTable;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Trading;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.inventory.Inventory;
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

        ServerPlayNetworking.registerGlobalReceiver(TRADE, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier giver = buf.readIdentifier();
            boolean villageSells = buf.readBoolean();
            Identifier goods = buf.readIdentifier();
            int count = buf.readVarInt();
            server.execute(() -> trade(player, village, giver, villageSells, goods, count));
        });
    }

    /**
     * Собрать снимок разговора.
     * <p>
     * Пусто, если у этой деревни нет ни выдающего, ни доверия к игроку, —
     * то есть говорить не о чем и экран открывать незачем.
     */
    public static Optional<QuestView> viewOf(Settlement village, UUID id, Inventory carried,
                                             Identifier giver, Warehouse wares) {
        int reputation = village.reputationOf(id);
        Standing standing = Standing.of(reputation);

        Optional<QuestView.Offer> offer = Quests.offered(village, id, giver)
                .flatMap(QuestManager::get)
                .map(quest -> offerOf(carried, quest, reputation));

        return Optional.of(new QuestView(village.id(), village.name(), giver,
                standing.displayKey(), reputation, nextThreshold(standing), offer,
                stalls(village, reputation, carried, wares), Trading.purse(wares)));
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
            return carried.count(Trading.COIN) >= price
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
    private static QuestView.Offer offerOf(Inventory carried, Quest quest, int reputation) {
        List<QuestView.Need> needs = new ArrayList<>();
        boolean ready = reputation >= quest.minReputation();

        for (Quest.Objective objective : quest.objectives()) {
            if (objective instanceof Quest.Objective.Deliver deliver) {
                int have = carried.count(deliver.item());
                needs.add(new QuestView.Need(deliver.item(), deliver.count(), have));
                ready = ready && have >= deliver.count();
            }
        }

        List<String> rewards = new ArrayList<>();
        for (Quest.Reward reward : quest.rewards()) {
            if (reward instanceof Quest.Reward.Give give) {
                rewards.add(Registries.ITEM.getId(give.item()) + " x" + give.count());
            } else if (reward instanceof Quest.Reward.Trust trust) {
                rewards.add("+" + trust.amount());
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
                player.getInventory(), wares, side, deal));

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
     * Переслать снимок заново.
     * <p>
     * После любого действия, а не только удачного: отказ тоже меняет
     * картину — доверие могло вырасти за прошлую сделку, а товар на складе
     * кончиться. Экран, показывающий вчерашний прилавок, обманывает.
     */
    private static void refresh(ServerPlayerEntity player, SettlementManager manager, UUID village,
                                Identifier giver) {
        manager.byId(village).ifPresent(fresh -> viewOf(fresh, player.getUuid(),
                        player.getInventory(), giver,
                        Warehouse.of(player.getServerWorld(), fresh))
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
