package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.festival.FestivalCalendar;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.festival.Heralds;
import com.villagepax.sim.festival.Matches;
import com.villagepax.sim.festival.PrizeStall;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Разговор с затейником: снимок праздника и две просьбы — начать и взять.
 * <p>
 * Клиент только просит, решает сервер: право начать считает
 * {@link Matches#check}, а расстояние — до <b>живого тела</b> затейника,
 * как у старейшины. Кнопка «Начать» на экране, открытом на другом конце
 * карты, иначе стала бы пультом праздника откуда угодно.
 */
public final class FestivalNet {

    public static final Identifier OPEN = new Identifier(VillagePax.MOD_ID, "festival_open");
    public static final Identifier START = new Identifier(VillagePax.MOD_ID, "festival_start");
    public static final Identifier BUY = new Identifier(VillagePax.MOD_ID, "festival_buy");

    /** Как у разговора со старейшиной: чуть больше вытянутой руки. */
    private static final double TALK_RANGE = 8.0;

    private FestivalNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(START, (server, player, handler, buf, sender) -> {
            // Читать надо здесь: за пределами обработчика буфер освобождён.
            UUID village = buf.readUuid();
            int index = buf.readVarInt();
            server.execute(() -> start(player, village, index));
        });
        ServerPlayNetworking.registerGlobalReceiver(BUY, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            int index = buf.readVarInt();
            server.execute(() -> buy(player, village, index));
        });
    }

    /** Открыть экран затейника игроку. */
    public static void open(ServerPlayerEntity player, ServerWorld world, Settlement settlement, Citizen host) {
        long time = world.getTimeOfDay();
        viewOf(SettlementManager.get(world), settlement, host, player, Schedule.dayOf(time), time)
                .ifPresent(view -> send(player, view));
    }

    /**
     * Снимок экрана для этого игрока в этот день и час.
     * <p>
     * День и час — доводы, как у всего праздника: мир проверок общий.
     *
     * @return пусто, если у народа нет праздника — говорить не о чем
     */
    public static Optional<FestivalView> viewOf(SettlementManager manager, Settlement settlement, Citizen host,
                                                PlayerEntity player, long day, long timeOfDay) {
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        if (festival == null) {
            return Optional.empty();
        }
        FestivalDay.Verdict today = FestivalDay.today(settlement, day);
        Optional<String> closed = today != FestivalDay.Verdict.ON ? Optional.of(today.reasonKey())
                : FestivalDay.contestsOpen(settlement.owner().isAutonomous(), timeOfDay) ? Optional.empty()
                : Optional.of(Matches.Verdict.CLOSED.reasonKey());
        List<FestivalView.ContestLine> contests = new ArrayList<>();
        for (int index = 0; index < festival.contests().size(); index++) {
            Festival.Contest contest = festival.contests().get(index);
            Matches.Verdict verdict = Matches.check(player, settlement, index, day, timeOfDay);
            contests.add(new FestivalView.ContestLine(contest.name(), contest.kind().id(),
                    manager.awarded(settlement.id(), day, player.getUuid(), index),
                    verdict == Matches.Verdict.YES ? Optional.empty() : Optional.of(verdict.reasonKey())));
        }
        int ribbons = player.getInventory().count(ModFestivalItems.FESTIVAL_RIBBON);
        List<FestivalView.PrizeLine> prizes = festival.prizes().stream()
                .map(prize -> new FestivalView.PrizeLine(prize.item(), prize.count(), prize.price(),
                        ribbons >= prize.price()))
                .toList();
        return Optional.of(new FestivalView(settlement.id(), settlement.name(), host.fullName(),
                Heralds.titleKey(settlement), festival.name(),
                FestivalCalendar.daysUntil(day, festival.moonPhase()), closed, contests, ribbons,
                today == FestivalDay.Verdict.ON, prizes,
                Matches.at(settlement.id()).map(match -> match.contest().name())));
    }

    public static void send(ServerPlayerEntity player, FestivalView view) {
        DataResult<?> encoded = FestivalView.CODEC.encodeStart(NbtOps.INSTANCE, view);
        NbtCompound nbt = (NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "снимок затейника не кодируется: "
                        + encoded.error().map(Object::toString).orElse("причина неизвестна")));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, OPEN, buf);
    }

    /** Битый снимок не роняет клиент: экран просто не откроется, а в логе останется причина. */
    public static Optional<FestivalView> read(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return Optional.empty();
        }
        DataResult<FestivalView> decoded = FestivalView.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.error().ifPresent(error ->
                VillagePax.LOGGER.warn("Экран затейника не читается: {}", error.message()));
        return decoded.result();
    }

    /** «Начать»: право и место считает сервер; отказ — строкой над рукой. */
    private static void start(ServerPlayerEntity player, UUID village, int index) {
        ServerWorld world = player.getServerWorld();
        Settlement settlement = SettlementManager.get(world).byId(village).orElse(null);
        if (settlement == null) {
            return;
        }
        if (nearbyHost(player, settlement) == null) {
            refuse(player, Matches.Verdict.TOO_FAR);
            return;
        }
        long time = world.getTimeOfDay();
        Matches.Verdict verdict = Matches.start(world, player, settlement, index, Schedule.dayOf(time), time);
        if (verdict != Matches.Verdict.YES) {
            refuse(player, verdict);
        }
    }

    /** «Взять»: у живого затейника, за ленты; свежий снимок — в любом случае. */
    private static void buy(ServerPlayerEntity player, UUID village, int index) {
        ServerWorld world = player.getServerWorld();
        Settlement settlement = SettlementManager.get(world).byId(village).orElse(null);
        if (settlement == null) {
            return;
        }
        Citizen host = nearbyHost(player, settlement);
        if (host == null) {
            refuse(player, Matches.Verdict.TOO_FAR);
            return;
        }
        PrizeStall.Verdict verdict = PrizeStall.buy(world, player, settlement, index,
                Schedule.dayOf(world.getTimeOfDay()));
        if (verdict == PrizeStall.Verdict.YES) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 0.6f, 1.2f);
        } else {
            player.sendMessage(Text.translatable(verdict.reasonKey()).formatted(Formatting.RED), true);
        }
        open(player, world, settlement, host);
    }

    private static void refuse(ServerPlayerEntity player, Matches.Verdict verdict) {
        player.sendMessage(Text.translatable(verdict.reasonKey()).formatted(Formatting.RED), true);
    }

    /** Живой затейник этой деревни рядом с игроком. */
    static Citizen nearbyHost(ServerPlayerEntity player, Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(Villages.ENTERTAINER::equals).isPresent()
                    && CitizenSpawner.whereNow(player.getServerWorld(), citizen)
                    .filter(where -> where.squaredDistanceTo(player.getPos()) <= TALK_RANGE * TALK_RANGE)
                    .isPresent()) {
                return citizen;
            }
        }
        return null;
    }
}
