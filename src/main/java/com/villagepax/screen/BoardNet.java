package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.block.ModBlocks;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Villages;
import com.villagepax.sim.quest.Progress;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сеть доски заданий: листки вниз, «отдаю по этому листку» вверх.
 * <p>
 * Сдают <b>у доски</b>, а не у жителя: доска для того и висит, чтобы
 * не искать лесоруба по всей деревне. Проверка та же, что у разговора, —
 * расстояние, его не подделать: до доски, а не до выдающего.
 */
public final class BoardNet {

    public static final Identifier OPEN = new Identifier(VillagePax.MOD_ID, "board_open");
    public static final Identifier HAND_IN = new Identifier(VillagePax.MOD_ID, "board_hand_in");

    /** Насколько близко к доске надо стоять, чтобы сдать по листку. */
    static final double REACH = 8.0;

    private BoardNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(HAND_IN, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            BlockPos board = buf.readBlockPos();
            Identifier giver = buf.readIdentifier();
            server.execute(() -> handIn(player, village, board, giver));
        });
    }

    /** Щелчок по доске: открыть её, если она чья-то. */
    public static void open(ServerPlayerEntity player, BlockPos board) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement village = villageAt(manager, board).orElse(null);
        if (village == null) {
            // Доска в колонии или в поле: просить с неё некому.
            player.sendMessage(Text.translatable("villagepax.board.nobody"), true);
            return;
        }
        send(player, viewOf(manager, village, board, player.getUuid(), player.getInventory(),
                Schedule.dayOf(world.getTimeOfDay())));
    }

    /** Деревня народа, на чьей земле висит доска; ближайшая, если земли спорят. */
    public static Optional<Settlement> villageAt(SettlementManager manager, BlockPos board) {
        return manager.all().stream()
                .filter(settlement -> settlement.owner().isAutonomous() && settlement.claims(board))
                .min(Comparator.comparingDouble(settlement ->
                        settlement.center().getSquaredDistance(board)));
    }

    /**
     * Листки доски: по одному на ремесло деревни, у которого сегодня есть
     * просьба. Старейшина — первым: его цепочка ведёт в мод, и его листок
     * висит посередине глаз.
     */
    public static BoardView viewOf(SettlementManager manager, Settlement village, BlockPos board,
                                   UUID player, Inventory carried, long today) {
        int reputation = village.reputationOf(player);
        Progress.Seeker seeker = Progress.Seeker.of(player, carried,
                QuestNet.colonyOf(manager, player), manager);

        Map<Identifier, Citizen> givers = new LinkedHashMap<>();
        village.citizens().stream()
                .filter(citizen -> citizen.profession().isPresent())
                .sorted(Comparator.comparing((Citizen citizen) ->
                                !citizen.profession().get().equals(Villages.ELDER))
                        .thenComparing(citizen -> citizen.profession().get().toString()))
                .forEach(citizen -> givers.putIfAbsent(citizen.profession().get(), citizen));

        List<BoardView.Sheet> sheets = new ArrayList<>();
        givers.forEach((giver, author) -> Quests.task(village, player, giver, today)
                .ifPresent(task -> sheets.add(new BoardView.Sheet(giver, author.fullName(),
                        CitizenEntity.titleKeyOf(author),
                        QuestNet.offerOf(seeker, task.quest(), reputation),
                        reputation >= task.quest().minReputation()))));

        return new BoardView(village.id(), village.name(), board,
                Standing.of(reputation).displayKey(), reputation, sheets);
    }

    /** Сдать по листку: у доски, тем же разговором, что и у жителя. */
    private static void handIn(ServerPlayerEntity player, UUID villageId, BlockPos board,
                               Identifier giver) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Settlement village = manager.byId(villageId).orElse(null);
        // Место прислал клиент: сперва дёшево и без мира — граница и расстояние, —
        // и только потом блок. Иначе подложенный пакет заставлял сервер грузить
        // и порождать чанк где угодно.
        if (village == null || !village.claims(board)) {
            return;
        }
        if (player.squaredDistanceTo(Vec3d.ofCenter(board)) > REACH * REACH) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return;
        }
        if (!world.isChunkLoaded(board) || !world.getBlockState(board).isOf(ModBlocks.NOTICE_BOARD)) {
            return;
        }
        Citizen author = village.citizens().stream()
                .filter(citizen -> citizen.profession().filter(giver::equals).isPresent())
                .findFirst().orElse(null);
        if (author == null) {
            // Листок висел, а жителя не стало: просить больше некому.
            return;
        }
        Quests.talk(manager, village, player, author);
        manager.byId(villageId).ifPresent(fresh -> send(player, viewOf(manager, fresh, board,
                player.getUuid(), player.getInventory(), Schedule.dayOf(world.getTimeOfDay()))));
    }

    public static void send(ServerPlayerEntity player, BoardView view) {
        DataResult<?> encoded = BoardView.CODEC.encodeStart(NbtOps.INSTANCE, view);
        NbtCompound nbt = (NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "доска не кодируется: " + encoded.error().map(Object::toString).orElse("?")));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, OPEN, buf);
    }

    /** Битая доска не роняет клиент: окно просто не откроется. */
    public static Optional<BoardView> read(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return Optional.empty();
        }
        DataResult<BoardView> decoded = BoardView.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.error().ifPresent(error -> VillagePax.LOGGER.warn("Доска не читается: {}", error.message()));
        return decoded.result();
    }
}
