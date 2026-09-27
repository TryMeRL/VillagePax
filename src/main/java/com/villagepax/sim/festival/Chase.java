package com.villagepax.sim.festival;

import com.villagepax.core.festival.Critter;
import com.villagepax.core.festival.Festival;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.festival.FestivalCritters;
import com.villagepax.entity.festival.PenRunner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ловля: в загоне носятся зверьки, лови руками.
 * <p>
 * Зверьки выходят в загон на старте, удирают от всякого, кто ближе шести
 * блоков, и из загона не выходят. Игрок ловит щелчком, житель — подобравшись
 * вплотную. Честолюбивый житель бегает быстрее, ленивый трусцой: видно,
 * кто есть кто, и ленивого соперника игрок обгоняет.
 * <p>
 * Загон засыпан или сломан — стоячих клеток меньше девяти, трём зверькам
 * негде бегать, — и ловля недоступна.
 */
public final class Chase extends Match {

    /** Меньше стоячих клеток — загон не загон. */
    public static final int FEWEST_CELLS = 9;

    /**
     * Подобрался на столько — поймал.
     * <p>
     * Как у поиска и у всякой работы жителя ({@link WorkContext#ARRIVAL_REACH}):
     * вплотную к цели житель не встаёт.
     */
    public static final double CATCH = WorkContext.ARRIVAL_REACH;

    private final List<UUID> critters = new ArrayList<>();

    /** Кому ловля дала скорость или медлительность — снять в конце только своё. */
    private final Map<UUID, StatusEffect> paced = new HashMap<>();

    Chase(ServerWorld world, UUID id, Settlement settlement, Fair fair, Festival festival, int index,
          long day) {
        super(world, id, settlement, fair, festival, index, day);
    }

    /** Ещё не пойманные зверьки. */
    public List<MobEntity> critters(ServerWorld world) {
        List<MobEntity> alive = new ArrayList<>();
        for (UUID id : critters) {
            if (world.getEntity(id) instanceof MobEntity critter && critter.isAlive()) {
                alive.add(critter);
            }
        }
        return alive;
    }

    boolean owns(Entity entity) {
        return entity instanceof PenRunner runner && runner.pen().match().filter(id()::equals).isPresent();
    }

    @Override
    protected boolean prepare(ServerWorld world, Random random) {
        Critter kind = contest().critter().orElse(null);
        if (kind == null) {
            return false;
        }
        List<BlockPos> cells = fair.pen().stream()
                .filter(cell -> HidingPlaces.standable(world, cell))
                .toList();
        if (cells.size() < FEWEST_CELLS) {
            return false;
        }
        for (int i = 0; i < contest().pieces(); i++) {
            MobEntity critter = FestivalCritters.spawn(world, kind, cells.get(random.nextInt(cells.size())),
                    cells, id());
            if (critter != null) {
                critters.add(critter.getUuid());
            }
        }
        return !critters.isEmpty();
    }

    /** Щелчок игрока по зверьку этой ловли. */
    void grab(ServerWorld world, MobEntity critter, PlayerEntity player) {
        if (!isPlayer(player.getUuid())) {
            player.sendMessage(Text.translatable("villagepax.contest.chase.not_yours"), true);
            return;
        }
        if (!isRunning()) {
            player.sendMessage(Text.translatable("villagepax.contest.chase.wait"), true);
            return;
        }
        caught(world, critter, player.getUuid());
    }

    @Override
    protected void began(ServerWorld world) {
        Settlement home = SettlementManager.get(world).byId(settlement()).orElse(null);
        if (home == null) {
            return;
        }
        for (UUID rival : rivals()) {
            Citizen citizen = home.citizen(rival).orElse(null);
            CitizenEntity body = citizen == null ? null : bodyOf(world, citizen);
            if (body == null) {
                continue;
            }
            Nature nature = Natures.of(citizen);
            StatusEffect pace = nature == Nature.AMBITIOUS ? StatusEffects.SPEED
                    : nature == Nature.LAZY ? StatusEffects.SLOWNESS : null;
            if (pace != null && !body.hasStatusEffect(pace)) {
                body.addStatusEffect(new StatusEffectInstance(pace, ticksLeft(), 0, false, false));
                paced.put(rival, pace);
            }
        }
    }

    @Override
    protected void steer(ServerWorld world, CitizenEntity body, Citizen rival) {
        MobEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (MobEntity critter : critters(world)) {
            double distance = body.squaredDistanceTo(critter);
            if (distance < best) {
                best = distance;
                nearest = critter;
            }
        }
        if (nearest == null) {
            return;
        }
        if (best <= CATCH * CATCH) {
            caught(world, nearest, rival.id());
            return;
        }
        body.setWorkTarget(nearest.getBlockPos());
        body.setWorkFocus(nearest.getBlockPos());
    }

    @Override
    protected boolean exhausted(ServerWorld world) {
        return critters(world).isEmpty();
    }

    @Override
    protected void clear(ServerWorld world) {
        for (MobEntity critter : critters(world)) {
            FestivalCritters.vanish(world, critter);
        }
        critters.clear();
        Settlement home = SettlementManager.get(world).byId(settlement()).orElse(null);
        paced.forEach((rival, pace) -> {
            Citizen citizen = home == null ? null : home.citizen(rival).orElse(null);
            CitizenEntity body = citizen == null ? null : bodyOf(world, citizen);
            if (body != null) {
                body.removeStatusEffect(pace);
            }
        });
        paced.clear();
    }

    private void caught(ServerWorld world, MobEntity critter, UUID who) {
        if (!critters.remove(critter.getUuid())) {
            return;
        }
        score(who, 1);
        FestivalCritters.vanish(world, critter);
    }
}
