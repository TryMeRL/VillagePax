package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.VillagePax;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Milestones;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.diplomacy.Citizenship;
import com.villagepax.sim.festival.FestivalCalendar;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Arrival;
import com.villagepax.sim.life.Chronicle;
import com.villagepax.sim.trade.MarketDay;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
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
 * Окно ратуши деревни народа: снимок туда, просьбы обратно.
 * <p>
 * Просьб три: отдать деревне нужное (из руки или всё, что есть в сумке,
 * той же вещи), попроситься жить и взять книгу летописи. Каждая
 * проверяется на сервере заново: клиент присылает только опознаватель
 * деревни и вещь, а близость к ратуше, доверие и нужду считает сервер.
 */
public final class VillageHallNet {

    public static final Identifier OPEN = new Identifier(VillagePax.MOD_ID, "village_hall_open");
    public static final Identifier DONATE = new Identifier(VillagePax.MOD_ID, "village_hall_donate");
    public static final Identifier SETTLE = new Identifier(VillagePax.MOD_ID, "village_hall_settle");
    public static final Identifier CHRONICLE = new Identifier(VillagePax.MOD_ID, "village_hall_chronicle");

    /** Дальше этого от ратуши просить нельзя: окно открывают у неё. */
    static final double REACH = 8.0;

    /** Сколько жителей показывает окно: дальше — книга и пульт не нужны гостю. */
    static final int PEOPLE_SHOWN = 40;

    /** Сколько строк летописи в окне. */
    static final int CHRONICLE_SHOWN = 8;

    /** Вещь «из руки», а не по имени. */
    public static final Identifier HAND = new Identifier("minecraft", "air");

    private VillageHallNet() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(DONATE, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            Identifier item = buf.readIdentifier();
            server.execute(() -> donate(player, village, item));
        });
        ServerPlayNetworking.registerGlobalReceiver(SETTLE, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            server.execute(() -> settle(player, village));
        });
        ServerPlayNetworking.registerGlobalReceiver(CHRONICLE, (server, player, handler, buf, sender) -> {
            UUID village = buf.readUuid();
            server.execute(() -> chronicle(player, village));
        });
    }

    /** Открыть окно ратуши деревни. */
    public static void open(ServerPlayerEntity player, Settlement village) {
        send(player, viewOf(player.getServerWorld(), village, player));
    }

    /** Снимок для этого игрока. */
    public static VillageHallView viewOf(ServerWorld world, Settlement village,
                                         net.minecraft.entity.player.PlayerEntity player) {
        long day = Schedule.dayOf(world.getTimeOfDay());
        UUID uuid = player.getUuid();

        int reputation = village.reputationOf(uuid);
        Standing standing = Standing.of(reputation);
        Standing priced = Standing.of(MarketDay.tradeTrust(village, reputation, day));
        Standing[] ladder = Standing.values();
        Optional<Standing> next = standing.ordinal() + 1 < ladder.length
                ? Optional.of(ladder[standing.ordinal() + 1]) : Optional.empty();
        VillageHallView.Trust trust = new VillageHallView.Trust(reputation, standing.displayKey(),
                next.map(Standing::displayKey), next.map(Standing::from).orElse(standing.from()),
                standing.from(), priced.buyPercent(), priced.sellPercent(), priced != standing);

        Optional<Festival> festival = Festivals.of(village.culture());
        VillageHallView.Calendar calendar = new VillageHallView.Calendar(day,
                MarketDay.daysUntil(village.culture(), day),
                village.level().ordinal() >= SettlementLevel.VILLAGE.ordinal(),
                festival.map(Festival::name),
                festival.map(f -> FestivalCalendar.daysUntil(day, f.moonPhase())).orElse(0));

        Warehouse warehouse = Warehouse.of(world, village);
        int foodDays = TownHallView.daysOfFood(warehouse.tally(), village.population());
        Map<Identifier, Integer> missing = new LinkedHashMap<>();
        Map<Identifier, Integer> carried = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> lack : VillageNeeds.missing(world, village).entrySet()) {
            Identifier id = Registries.ITEM.getId(lack.getKey());
            missing.put(id, lack.getValue());
            carried.put(id, player.getInventory().count(lack.getKey()));
        }
        Optional<Identifier> site = village.buildings().stream().filter(BuildJob::isUnderConstruction)
                .findFirst().map(Building::type);
        VillageHallView.Needs needs = new VillageHallView.Needs(site, missing, foodDays,
                foodDays < VillageNeeds.HUNGRY_DAYS, Housing.freeSpots(world, village), carried);

        List<String> news = new ArrayList<>();
        for (Text line : Arrival.news(village, day)) {
            news.add(Text.Serializer.toJson(line));
        }

        List<VillageHallView.Person> people = new ArrayList<>();
        village.citizens().stream()
                .sorted(Comparator.comparing((Citizen c) -> !c.profession().filter(Villages.ELDER::equals)
                                .isPresent())
                        .thenComparing(c -> c.profession().isEmpty())
                        .thenComparing(c -> c.profession().map(Identifier::toString).orElse(""))
                        .thenComparing(Citizen::fullName))
                .limit(PEOPLE_SHOWN)
                .forEach(c -> people.add(new VillageHallView.Person(c.fullName(), c.profession(),
                        Ages.stageOf(c).key(), c.spouse().isPresent())));

        List<VillageHallView.House> houses = new ArrayList<>();
        for (Building building : village.buildings()) {
            houses.add(new VillageHallView.House(building.type(), building.level(),
                    !BuildJob.isUnderConstruction(building)));
        }

        List<SettlementManager.ChronicleEntry> entries =
                SettlementManager.get(world).chronicleOf(village.id());
        List<String> story = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && story.size() < CHRONICLE_SHOWN; i--) {
            story.add(Text.Serializer.toJson(Chronicle.line(entries.get(i))));
        }

        VillageHallView.Ties ties = new VillageHallView.Ties(village.isAllyOf(uuid),
                village.owesTributeTo(uuid, day) ? village.tributeDaysLeft(day) : 0,
                village.truceDaysLeft(day), village.siege().isPresent(),
                Citizenship.judge(village, uuid).id());

        SettlementLevel level = village.level();
        return new VillageHallView(village.id(), village.name(), village.culture(),
                Milestones.levelKey(level),
                level.isMax() ? Optional.empty() : Optional.of(Milestones.levelKey(level.next())),
                village.center(), village.population(), level.maxCitizens(),
                trust, calendar, needs, news, people, houses, story, ties);
    }

    // --- просьбы ---

    /** Деревня, у ратуши которой стоит игрок; иначе ничего. */
    private static Optional<Settlement> near(ServerPlayerEntity player, UUID id) {
        Settlement village = SettlementManager.get(player.getServerWorld()).byId(id).orElse(null);
        if (village == null || !village.owner().isAutonomous()) {
            return Optional.empty();
        }
        if (player.squaredDistanceTo(Vec3d.ofCenter(village.center())) > REACH * REACH) {
            player.sendMessage(Text.translatable("villagepax.quest.too_far"), true);
            return Optional.empty();
        }
        return Optional.of(village);
    }

    private static void donate(ServerPlayerEntity player, UUID id, Identifier item) {
        Settlement village = near(player, id).orElse(null);
        if (village == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        if (item.equals(HAND)) {
            VillageNeeds.donate(world, manager, village, player, player.getMainHandStack());
        } else {
            Item wanted = Registries.ITEM.get(item);
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (!stack.isOf(wanted)) {
                    continue;
                }
                Settlement fresh = manager.byId(id).orElse(village);
                if (!VillageNeeds.donate(world, manager, fresh, player, stack)) {
                    break;
                }
            }
        }
        manager.byId(id).ifPresent(fresh -> open(player, fresh));
    }

    private static void settle(ServerPlayerEntity player, UUID id) {
        Settlement village = near(player, id).orElse(null);
        if (village == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        SettlementManager manager = SettlementManager.get(world);
        Citizenship.Verdict verdict = Citizenship.settle(world, manager, village, player);
        verdict.reasonKey().ifPresent(key -> player.sendMessage(Text.translatable(key), true));
        manager.byId(id).ifPresent(fresh -> open(player, fresh));
    }

    private static void chronicle(ServerPlayerEntity player, UUID id) {
        Settlement village = near(player, id).orElse(null);
        if (village == null) {
            return;
        }
        player.getInventory().offerOrDrop(Chronicle.book(village,
                SettlementManager.get(player.getServerWorld()).chronicleOf(village.id())));
        player.sendMessage(Text.translatable("villagepax.chronicle.given", village.name()), true);
    }

    // --- провод ---

    public static void send(ServerPlayerEntity player, VillageHallView view) {
        DataResult<?> encoded = VillageHallView.CODEC.encodeStart(NbtOps.INSTANCE, view);
        NbtCompound nbt = (NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "ратуша деревни не кодируется: " + encoded.error().map(Object::toString).orElse("?")));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);
        ServerPlayNetworking.send(player, OPEN, buf);
    }

    /** Битый снимок не роняет клиент: окно просто не откроется. */
    public static Optional<VillageHallView> read(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return Optional.empty();
        }
        return VillageHallView.CODEC.parse(NbtOps.INSTANCE, nbt)
                .resultOrPartial(error -> VillagePax.LOGGER.warn("Снимок ратуши деревни не прочитан: {}", error));
    }

    /** Ратуша этой деревни стоит здесь. */
    public static boolean isHallOf(Settlement village, BlockPos pos) {
        return village.center().equals(pos);
    }
}
