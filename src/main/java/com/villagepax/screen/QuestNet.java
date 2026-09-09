package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.quest.Quests;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
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
    }

    /**
     * Собрать снимок разговора.
     * <p>
     * Пусто, если у этой деревни нет ни выдающего, ни доверия к игроку, —
     * то есть говорить не о чем и экран открывать незачем.
     */
    public static Optional<QuestView> viewOf(Settlement village, UUID id, Inventory carried,
                                             Identifier giver) {
        int reputation = village.reputationOf(id);
        Standing standing = Standing.of(reputation);

        Optional<QuestView.Offer> offer = Quests.offered(village, id, giver)
                .flatMap(QuestManager::get)
                .map(quest -> offerOf(carried, quest, reputation));

        return Optional.of(new QuestView(village.id(), village.name(), giver,
                standing.displayKey(), reputation, nextThreshold(standing), offer));
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
        viewOf(colony, player.getUuid(), player.getInventory(), giver)
                .ifPresent(fresh -> send(player, fresh));
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
