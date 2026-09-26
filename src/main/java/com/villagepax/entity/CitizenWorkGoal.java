package com.villagepax.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Тактика работы: дойти туда, куда указала стратегия.
 * <p>
 * Цель <b>ничего не решает</b>. Точку ей кладёт в тело
 * {@code WorkTicker}, а здесь остаётся только идти. Именно поэтому цель
 * дешёвая: {@link #canStart()} читает поле, а не собирает склад и не
 * перебирает здания — а его спрашивают каждый тик.
 * <p>
 * Путь строится один раз на смену цели и переспрашивается редко: поиск пути
 * в Minecraft считается в главном потоке сервера, и это главный источник
 * просадки в модах с работниками. Здесь на одно здание приходится десяток
 * путей вместо трёхсот — билдер шагает к участку, а не к каждому блоку.
 */
public class CitizenWorkGoal extends Goal {

    /** Как часто перепроверять путь, если цель не менялась. */
    private static final int REPATH_INTERVAL = 20;

    /** Медленнее прогулочного шага: житель идёт по делу, а не бежит. */
    private static final double SPEED = 0.5;

    /**
     * Насколько житель подходит к цели не в упор, а со своей стороны.
     * <p>
     * Без этого двое курьеров, идущих к одному сундуку, метят в один и тот же
     * блок, толкаются на нём и сбивают друг другу путь — игрок видит, как
     * они спотыкаются. Смещение постоянно для каждого жителя (берётся из его
     * опознавателя), поэтому оно не дрожит от тика к тику: у каждого просто
     * своя сторона подхода.
     * <p>
     * Величина согласована с запасом досягаемости билдера
     * ({@code BuildJob.withinReach} с этим же запасом): иначе он вставал бы
     * чуть дальше вытянутой руки и не мог работать вовсе.
     */
    public static final double SPREAD = 0.8;

    private final CitizenEntity body;
    private final double spreadX;
    private final double spreadZ;

    private BlockPos target;
    private int cooldown;

    public CitizenWorkGoal(CitizenEntity body) {
        this.body = body;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));

        double angle = (body.getUuid().hashCode() & 0xFFFF) / 65536.0 * Math.PI * 2;
        this.spreadX = Math.cos(angle) * SPREAD;
        this.spreadZ = Math.sin(angle) * SPREAD;
    }

    @Override
    public boolean canStart() {
        return body.workTarget() != null;
    }

    @Override
    public boolean shouldContinue() {
        return body.workTarget() != null;
    }

    @Override
    public void start() {
        target = null;
        cooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        body.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos next = body.workTarget();
        if (next == null) {
            return;
        }
        // Дремлющий стоит, где лёг: ни шага, ни поворота головы.
        if (body.isDozing()) {
            return;
        }

        if (standsAt(next)) {
            // На месте: путь не нужен, а смотреть надо на дело или на того,
            // кто подошёл. Прежде взгляд шёл туда же, куда шаг, — на клетку,
            // где житель стоит, — и работник у грядки, у ствола, за
            // прилавком смотрел себе под ноги.
            if (!body.getNavigation().isIdle()) {
                body.getNavigation().stop();
            }
            lookAtWorkOrVisitor(next);
            target = next;
            return;
        }

        body.getLookControl().lookAt(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);

        boolean changed = !next.equals(target);
        if (!changed && --cooldown > 0) {
            return;
        }

        // Заново прокладываем путь либо когда цель сменилась, либо когда
        // прошлый путь кончился, а житель так и не дошёл — застрял на углу.
        if (changed || body.getNavigation().isIdle()) {
            target = next;
            body.getNavigation().startMovingTo(next.getX() + 0.5 + spreadX, next.getY(),
                    next.getZ() + 0.5 + spreadZ, SPEED);
        }
        cooldown = REPATH_INTERVAL;
    }

    /**
     * Ближе этого к цели житель уже на месте.
     * <p>
     * Шире смещения подхода ({@link #SPREAD}) с запасом: житель встаёт
     * не в середину клетки, а со своей стороны, и там он тоже на месте.
     */
    private static final double AT_PLACE = SPREAD + 0.5;

    /** Докуда житель на месте замечает подошедшего игрока. */
    private static final double VISITOR = 8.0;

    /** Докуда смотрят на дело: дальше — это уже не то, что в руках. */
    private static final double WORK_IN_SIGHT = 5.0;

    private boolean standsAt(BlockPos spot) {
        double dx = body.getX() - (spot.getX() + 0.5);
        double dz = body.getZ() - (spot.getZ() + 0.5);
        return dx * dx + dz * dz <= AT_PLACE * AT_PLACE && Math.abs(body.getY() - spot.getY()) < 1.0;
    }

    /**
     * Взгляд на месте: на дело, если оно не под ногами, иначе на игрока
     * рядом, иначе — никуда в особенности: голова сама вернётся прямо.
     * Купец смотрит на покупателя, фермер — на грядку, лесоруб — на ствол.
     */
    private void lookAtWorkOrVisitor(BlockPos spot) {
        BlockPos work = body.workFocus();
        if (work != null && !work.equals(spot)
                && body.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(work))
                <= WORK_IN_SIGHT * WORK_IN_SIGHT) {
            body.getLookControl().lookAt(work.getX() + 0.5, work.getY() + 0.5, work.getZ() + 0.5);
            return;
        }
        net.minecraft.entity.player.PlayerEntity visitor =
                body.getWorld().getClosestPlayer(body, VISITOR);
        if (visitor != null && !visitor.isSpectator()) {
            body.getLookControl().lookAt(visitor, 30.0f, 30.0f);
        }
    }
}
