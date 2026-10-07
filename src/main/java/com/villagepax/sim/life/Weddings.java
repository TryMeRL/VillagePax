package com.villagepax.sim.life;

import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.games.HideAndSeek;
import com.villagepax.sim.games.Lines;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Свадебный вечер: молодые у ратуши, гости вокруг, «Горько!» и угощение.
 * <p>
 * Свадьба в моде была строкой в чате — «такие-то теперь живут вместе», —
 * и ничем больше: игрок, проходивший мимо, не узнал бы о ней вовсе.
 * Теперь вечер того дня, когда двое сошлись, деревня празднует: молодые
 * стоят у ратуши лицом друг к другу, над ними сердечки, гости — на своём
 * вечернем кругу вокруг — кричат «Горько!», а зашедшего игрока молодые
 * угощают сластью своего народа. Деревня народа помнит, кто пришёл
 * на свадьбу: это немного доверия.
 * <p>
 * Помнится только до конца вечера и только в памяти сервера: свадьба —
 * событие одного вечера, хранить её в сохранении незачем.
 */
public final class Weddings {

    /** Слышно и видно праздник на столько блоков от молодых. */
    static final double AUDIENCE = 12;

    /** Гость кричит «Горько!» не чаще раза в столько тиков. */
    static final int CHEER_EVERY = 80;

    /** Доверие деревни народа тому, кто пришёл на свадьбу. */
    static final int GUEST_TRUST = 2;

    /** База реплик гостей. */
    public static final String CHEER = "villagepax.wedding.cheer";

    /** Один вечер одной пары. */
    static final class Feast {
        final RegistryKey<World> world;
        final UUID village;
        final long day;
        final UUID one;
        final UUID other;
        final Set<UUID> treated = new HashSet<>();
        long lastCheer;

        Feast(RegistryKey<World> world, UUID village, long day, UUID one, UUID other) {
            this.world = world;
            this.village = village;
            this.day = day;
            this.one = one;
            this.other = other;
        }

        boolean weds(UUID citizen) {
            return one.equals(citizen) || other.equals(citizen);
        }
    }

    private static final Map<UUID, Feast> FEASTS = new HashMap<>();

    private Weddings() {
    }

    /** Двое сошлись сегодня: вечером — свадьба. */
    public static void held(ServerWorld world, Settlement village, long day, Citizen one, Citizen other) {
        FEASTS.put(village.id(), new Feast(world.getRegistryKey(), village.id(), day, one.id(), other.id()));
        Life.tell(world, village, "villagepax.wedding.invite", one.firstName(), other.firstName());
    }

    /** Чья свадьба в этой деревне сегодня, если есть. */
    public static Optional<UUID[]> tonight(Settlement village, long day) {
        Feast feast = FEASTS.get(village.id());
        return feast == null || feast.day != day ? Optional.empty()
                : Optional.of(new UUID[]{feast.one, feast.other});
    }

    /** Свадебный ли сейчас вечер в этой деревне. */
    public static boolean isFeast(Settlement village, long day, long timeOfDay) {
        return tonight(village, day).isPresent() && Schedule.at(timeOfDay) == Schedule.LEISURE;
    }

    /**
     * Молодым вечером — к ратуше, а не на вечерний круг: они середина
     * праздника. Гостей это не касается — их вечерний круг и так вокруг.
     *
     * @return заняли ли решение жителя
     */
    public static boolean takesOver(WorkContext context, Schedule part, long day) {
        if (part != Schedule.LEISURE) {
            return false;
        }
        Feast feast = FEASTS.get(context.settlement().id());
        UUID me = context.citizen().id();
        if (feast == null || feast.day != day || !feast.weds(me)) {
            return false;
        }
        BlockPos centre = context.settlement().center();
        // Рядом с ратушей, плечом к плечу: один к востоку, другой к западу.
        BlockPos spot = centre.offset(net.minecraft.util.math.Direction.SOUTH, 3)
                .offset(me.equals(feast.one) ? net.minecraft.util.math.Direction.WEST
                        : net.minecraft.util.math.Direction.EAST, 1);
        context.holdNothing();
        context.body().setWorkTarget(context.hasArrivedAt(spot) ? null : spot);
        UUID partner = me.equals(feast.one) ? feast.other : feast.one;
        context.settlement().citizen(partner).flatMap(citizen -> bodyNow(context.world(), citizen))
                .ifPresent(other -> context.body().setWorkFocus(other.getBlockPos()));
        return true;
    }

    /**
     * Ход праздника: раз в секунду из обхода живых реплик.
     * <p>
     * Сердечки над молодыми, «Горько!» гостя, угощение подошедшему игроку.
     * Минувшие свадьбы забываются здесь же.
     */
    public static void tick(ServerWorld world, SettlementManager manager,
                            List<? extends PlayerEntity> players, long day, long timeOfDay,
                            Random random) {
        for (Iterator<Feast> it = FEASTS.values().iterator(); it.hasNext(); ) {
            Feast feast = it.next();
            if (feast.day < day) {
                it.remove();
                continue;
            }
            if (!feast.world.equals(world.getRegistryKey()) || Schedule.at(timeOfDay) != Schedule.LEISURE) {
                continue;
            }
            Settlement village = manager.byId(feast.village).orElse(null);
            if (village == null) {
                it.remove();
                continue;
            }
            CitizenEntity one = village.citizen(feast.one)
                    .flatMap(citizen -> bodyNow(world, citizen)).orElse(null);
            CitizenEntity other = village.citizen(feast.other)
                    .flatMap(citizen -> bodyNow(world, citizen)).orElse(null);
            if (one == null || other == null) {
                continue;
            }
            celebrate(world, feast, village, one, other, players, random);
        }
    }

    private static void celebrate(ServerWorld world, Feast feast, Settlement village, CitizenEntity one,
                                  CitizenEntity other, List<? extends PlayerEntity> players,
                                  Random random) {
        for (CitizenEntity body : List.of(one, other)) {
            world.spawnParticles(ParticleTypes.HEART, body.getX(), body.getY() + 2.2, body.getZ(),
                    2, 0.3, 0.2, 0.3, 0.0);
        }

        long now = world.getTime();
        if (now - feast.lastCheer >= CHEER_EVERY) {
            List<Citizen> guests = new ArrayList<>();
            for (Citizen citizen : village.citizens()) {
                if (!feast.weds(citizen.id()) && !Ages.isChild(citizen)
                        && bodyNow(world, citizen).filter(body -> body.squaredDistanceTo(one) <= AUDIENCE * AUDIENCE)
                        .isPresent()) {
                    guests.add(citizen);
                }
            }
            if (!guests.isEmpty()) {
                Citizen guest = guests.get(random.nextInt(guests.size()));
                bodyNow(world, guest).ifPresent(body -> {
                    if (Lines.sayKey(body, guest, CHEER, false)) {
                        feast.lastCheer = now;
                        world.spawnParticles(ParticleTypes.NOTE, body.getX(), body.getY() + 2.4,
                                body.getZ(), 1, 0.2, 0.1, 0.2, 1.0);
                    }
                });
            }
        }

        Item treat = HideAndSeek.treatOf(CultureManager.get(village.culture()));
        for (PlayerEntity player : players) {
            if (player.isSpectator() || player.squaredDistanceTo(one) > AUDIENCE * AUDIENCE
                    || !feast.treated.add(player.getUuid())) {
                continue;
            }
            player.getInventory().offerOrDrop(new ItemStack(treat));
            player.sendMessage(Text.translatable("villagepax.wedding.treat",
                    village.citizen(feast.one).map(Citizen::firstName).orElse("?"),
                    village.citizen(feast.other).map(Citizen::firstName).orElse("?"),
                    treat.getName()).formatted(Formatting.LIGHT_PURPLE), true);
            world.playSound(null, one.getBlockPos(), SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                    SoundCategory.NEUTRAL, 1.0f, 1.0f);
            if (village.owner().isAutonomous()) {
                SettlementManager.get(world).update(village.id(),
                        state -> state.addReputation(player.getUuid(), GUEST_TRUST));
            }
        }
    }

    private static Optional<CitizenEntity> bodyNow(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid().map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .filter(CitizenEntity::isAlive);
    }

    /** Забыть всё: сервер встаёт. */
    public static void forget() {
        FEASTS.clear();
    }
}
