package com.villagepax.entity.festival;

import com.villagepax.entity.CitizenEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Носиться по загону: удирать от ловцов и не выходить за борт.
 * <p>
 * Раз в пять тиков: ловец ближе шести блоков — бежать к клетке загона,
 * дальней от всех ловцов (одной из трёх лучших, наугад — иначе зверёк
 * бегал бы по одной дорожке, и его ловили бы на ней); никого — раз
 * в три секунды шагом к случайной клетке; оказался вне загона — к ближней
 * клетке. Ловец — игрок не в зрителях или житель.
 */
public class RunAroundThePenGoal extends Goal {

    /** Ловец ближе этого — пора удирать. */
    public static final double FRIGHT = 6;

    private static final int EVERY = 5;

    /** Шагом к новой клетке раз в три секунды: в решениях, раз в пять тиков. */
    private static final int STROLL_EVERY = 60 / EVERY;

    private static final int BEST = 3;

    private final PathAwareEntity critter;
    private final PenRunner runner;
    private int wait;
    private int strollIn;

    public <T extends PathAwareEntity & PenRunner> RunAroundThePenGoal(T critter) {
        this.critter = critter;
        this.runner = critter;
        setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return !runner.pen().cells().isEmpty();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--wait > 0) {
            return;
        }
        wait = EVERY;
        List<BlockPos> cells = runner.pen().cells();
        if (!runner.pen().holds(critter.getBlockPos())) {
            BlockPos nearest = cells.stream()
                    .min(Comparator.comparingDouble(cell -> critter.squaredDistanceTo(Vec3d.ofBottomCenter(cell))))
                    .orElseThrow();
            moveTo(nearest, runner.dash());
            return;
        }
        List<Vec3d> chasers = chasers();
        if (!chasers.isEmpty()) {
            List<BlockPos> far = new ArrayList<>(cells);
            far.sort(Comparator.comparingDouble((BlockPos cell) -> -safety(cell, chasers)));
            moveTo(far.get(critter.getRandom().nextInt(Math.min(BEST, far.size()))), runner.dash());
            strollIn = STROLL_EVERY;
            return;
        }
        if (--strollIn <= 0) {
            strollIn = STROLL_EVERY;
            moveTo(cells.get(critter.getRandom().nextInt(cells.size())), 1.0);
        }
    }

    /** Кто гонится: игроки не в зрителях и жители ближе испуга. */
    private List<Vec3d> chasers() {
        List<Vec3d> found = new ArrayList<>();
        for (LivingEntity near : critter.getWorld().getEntitiesByClass(LivingEntity.class,
                critter.getBoundingBox().expand(FRIGHT), near -> near != critter
                        && (near instanceof PlayerEntity player && !player.isSpectator()
                        || near instanceof CitizenEntity))) {
            if (near.squaredDistanceTo(critter) <= FRIGHT * FRIGHT) {
                found.add(near.getPos());
            }
        }
        return found;
    }

    /** Насколько клетка далека от ближнего ловца. */
    private static double safety(BlockPos cell, List<Vec3d> chasers) {
        Vec3d at = Vec3d.ofBottomCenter(cell);
        return chasers.stream().mapToDouble(chaser -> chaser.squaredDistanceTo(at)).min().orElse(0);
    }

    private void moveTo(BlockPos cell, double speed) {
        critter.getNavigation().startMovingTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, speed);
    }
}
