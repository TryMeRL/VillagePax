package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.games.ArmWrestle;
import com.villagepax.sim.games.Bout;
import com.villagepax.sim.games.Bouts;
import com.villagepax.sim.games.GamesLedger;
import com.villagepax.sim.games.Ochko;
import com.villagepax.sim.games.Purse;
import com.villagepax.sim.games.Rivalry;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Разговор за игорным столом: снимок окна игры и просьбы — начать, бросить,
 * хватит, нажать, встать.
 * <p>
 * Клиент только просит, решает сервер: право сесть считает {@link Bouts#check},
 * ход — сама партия. Расстояние — до живого тела соперника, как у затейника:
 * окно, открытое на другом конце деревни, иначе стало бы пультом игры
 * откуда угодно.
 */
public final class GamesNet {

    public static final Identifier OPEN = new Identifier(VillagePax.MOD_ID, "games_open");
    public static final Identifier CLOSE = new Identifier(VillagePax.MOD_ID, "games_close");
    public static final Identifier START = new Identifier(VillagePax.MOD_ID, "games_start");
    public static final Identifier ROLL = new Identifier(VillagePax.MOD_ID, "games_roll");
    public static final Identifier STAND = new Identifier(VillagePax.MOD_ID, "games_stand");
    public static final Identifier PRESS = new Identifier(VillagePax.MOD_ID, "games_press");
    public static final Identifier LEAVE = new Identifier(VillagePax.MOD_ID, "games_leave");

    private GamesNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(START, (server, player, handler, buf, sender) -> {
            // Читать надо здесь: за пределами обработчика буфер освобождён.
            UUID rival = buf.readUuid();
            String kind = buf.readString();
            int stake = buf.readVarInt();
            server.execute(() -> start(player, rival, kind, stake));
        });
        ServerPlayNetworking.registerGlobalReceiver(ROLL, (server, player, handler, buf, sender) ->
                server.execute(() -> withBout(player, bout -> bout.roll(player.getServerWorld()))));
        ServerPlayNetworking.registerGlobalReceiver(STAND, (server, player, handler, buf, sender) ->
                server.execute(() -> withBout(player, bout -> bout.stand(player.getServerWorld()))));
        ServerPlayNetworking.registerGlobalReceiver(PRESS, (server, player, handler, buf, sender) ->
                server.execute(() -> withBout(player, bout -> bout.press(player.getServerWorld()))));
        ServerPlayNetworking.registerGlobalReceiver(LEAVE, (server, player, handler, buf, sender) ->
                server.execute(() -> withBout(player, bout -> bout.leave(player.getServerWorld()))));
        Bouts.watch(new Bouts.Watcher() {
            @Override
            public void changed(ServerWorld world, Bout bout) {
                push(world, bout);
            }

            @Override
            public void ended(ServerWorld world, Bout bout, Optional<String> reasonKey) {
                if (reasonKey.isEmpty()) {
                    // Итог: окно показывает его и «Ещё партию».
                    push(world, bout);
                } else if (bout.playerOf(world) instanceof ServerPlayerEntity player) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(reasonKey.get());
                    ServerPlayNetworking.send(player, CLOSE, buf);
                }
            }
        });
    }

    /** Открыть окно игры с этим жителем. */
    public static void open(ServerPlayerEntity player, Settlement settlement, Citizen rival) {
        ServerWorld world = player.getServerWorld();
        long time = world.getTimeOfDay();
        send(player, viewOf(world, player, settlement, rival, Schedule.dayOf(time), time));
    }

    /**
     * Снимок окна для этого игрока в этот день и час.
     * <p>
     * День и час — доводы, как у всех игр: мир проверок общий.
     */
    public static GameView viewOf(ServerWorld world, PlayerEntity player, Settlement settlement,
                                  Citizen rival, long day, long timeOfDay) {
        Optional<Bout> bout = Bouts.of(player.getUuid()).filter(b -> b.rival().equals(rival.id()));
        return viewOf(world, player, settlement, rival, day, bout);
    }

    private static GameView viewOf(ServerWorld world, PlayerEntity player, Settlement settlement,
                                   Citizen rival, long day, Optional<Bout> bout) {
        Rivalry record = GamesLedger.get(world).rivalry(rival.id(), player.getUuid());
        boolean coins = Bouts.forCoins(settlement);
        int purse = coins ? Math.max(0, Bouts.purseLeft(world, rival, day)) : 0;
        List<Integer> stakes = coins
                ? Purse.allowed(purse, Coins.total(player.getInventory()))
                : List.of(0);
        return new GameView(settlement.id(), rival.id(), rival.fullName(), CitizenEntity.titleKeyOf(rival),
                Natures.of(rival).id(), record.lost(), record.won(), coins, purse, stakes,
                bout.map(GamesNet::lineOf));
    }

    private static GameView.BoutLine lineOf(Bout bout) {
        int paid = bout.settled().orElse(0);
        if (bout.dice().isPresent()) {
            Ochko dice = bout.dice().get();
            return new GameView.BoutLine(bout.kind().id(), bout.stake(),
                    dice.phase().name().toLowerCase(java.util.Locale.ROOT), dice.mine(), dice.theirs(),
                    0, 0L, 0.0, dice.outcome().map(o -> o.name().toLowerCase(java.util.Locale.ROOT)), paid);
        }
        ArmWrestle arm = bout.arm().orElseThrow();
        return new GameView.BoutLine(bout.kind().id(), bout.stake(),
                arm.outcome().isPresent() ? "done" : "running", List.of(), List.of(),
                (int) Math.round(arm.balance()), bout.markerStart(), arm.zoneWidth(),
                arm.outcome().map(o -> o.name().toLowerCase(java.util.Locale.ROOT)), paid);
    }

    /** Свежий снимок партии её игроку — если он сетевой: подставному слать некуда. */
    private static void push(ServerWorld world, Bout bout) {
        if (!(bout.playerOf(world) instanceof ServerPlayerEntity player)) {
            return;
        }
        SettlementManager.get(world).byId(bout.village()).ifPresent(settlement ->
                settlement.citizen(bout.rival()).ifPresent(rival -> send(player,
                        viewOf(world, player, settlement, rival, Schedule.dayOf(world.getTimeOfDay()),
                                Optional.of(bout)))));
    }

    public static void send(ServerPlayerEntity player, GameView view) {
        DataResult<?> encoded = GameView.CODEC.encodeStart(NbtOps.INSTANCE, view);
        NbtCompound nbt = (NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "снимок игры не кодируется: "
                        + encoded.error().map(Object::toString).orElse("причина неизвестна")));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, OPEN, buf);
    }

    /** Битый снимок не роняет клиент: окно просто не откроется, а в логе останется причина. */
    public static Optional<GameView> read(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return Optional.empty();
        }
        DataResult<GameView> decoded = GameView.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.error().ifPresent(error ->
                VillagePax.LOGGER.warn("Окно игры не читается: {}", error.message()));
        return decoded.result();
    }

    /** «Сыграть»: у живого соперника рядом, по праву, которое считает сервер. */
    private static void start(ServerPlayerEntity player, UUID rivalId, String kindId, int stake) {
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        for (Settlement settlement : manager.all()) {
            Citizen rival = settlement.citizen(rivalId).orElse(null);
            if (rival == null) {
                continue;
            }
            Bouts.Kind kind = kindId.equals(Bouts.Kind.ARM.id()) ? Bouts.Kind.ARM : Bouts.Kind.DICE;
            long time = world.getTimeOfDay();
            Bouts.Verdict verdict = Bouts.start(world, player, settlement, rival, kind, stake,
                    Schedule.dayOf(time), time);
            if (verdict != Bouts.Verdict.YES) {
                player.sendMessage(Text.translatable(verdict.reasonKey()).formatted(Formatting.RED), true);
                open(player, settlement, rival);
            }
            return;
        }
    }

    private static void withBout(ServerPlayerEntity player, Consumer<Bout> act) {
        Bouts.of(player.getUuid()).ifPresent(act);
    }
}
