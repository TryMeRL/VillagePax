package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.entity.CitizenWorkGoal;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Строитель: подходит к участку и кладёт всё, до чего дотянулся.
 * <p>
 * Решение заказчика — шагать к участку, а не к каждому блоку. Разница не
 * косметическая: поиск пути считается в главном потоке сервера, и триста
 * путей на одно здание — это просадка, а десяток — нет. Выглядит при этом
 * так же: подошёл, поработал, перешёл.
 * <p>
 * Самой стройкой по-прежнему занимается {@link BuildJob}; здесь только
 * решение, где стоять и когда переходить.
 */
public final class BuilderJob implements Job {

    /** Логика стройки. Профессию, которая её выбирает, называет датапак. */
    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "build");

    /** Докуда искать место, где встать. */
    private static final int STAND_RADIUS = 4;

    /** На сколько выше уровня земли стройки билдер согласен подняться. */
    private static final int STAND_CLIMB = 2;

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    /**
     * Цель — место, с которого достаётся следующий блок плана, и в фазе
     * работы тоже.
     * <p>
     * Отпускать цель на время работы нельзя: без точки цель навигации
     * останавливается, управление забирает прогулка, билдер уходит — и
     * следующее решение гонит его обратно. Получаются качели. Пока точка
     * задана, он уже рядом с ней и просто стоит.
     */
    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        JobState state = context.state();

        if (state.isIdle()) {
            findWork(context);
            return whereToStand(context);
        }

        Building site = context.site().orElse(null);
        if (site == null || !BuildJob.isUnderConstruction(site)) {
            context.goIdle();
            return Optional.empty();
        }

        BuildJob.Outcome outcome = BuildJob.advance(context.world(), context.manager(),
                context.settlement().id(), site.id(), 1, context.position());

        switch (outcome) {
            // Дошли до блока за пределами вытянутой руки: перейти к нему.
            case OUT_OF_REACH -> context.setState(state.withPhase(JobState.Phase.TO_SITE));

            // Работа идёт — или ждём материалов, стоя на месте: уходить нельзя,
            // иначе билдер начнёт бегать кругами, пока курьер несёт брёвна.
            case ADVANCED, WAITING_FOR_MATERIALS -> context.setState(state.withPhase(JobState.Phase.WORKING));

            // Готовое здание обязано заработать сразу. Раздача кроватей
            // и мастерских иначе ждёт рассвета, и достроенный в полдень дом
            // стоит пустым ровно ту ночь, в которую он готов, — а игрок
            // видит мод, который не работает.
            case FINISHED -> {
                Housing.assignBeds(context.world(), context.settlement());
                Workplaces.assign(context.world(), context.settlement());
                context.goIdle();
            }

            // Стройки больше нет по другой причине.
            case ALREADY_DONE, NO_SCHEMATIC, NOT_LOADED, NOT_FOUND, NO_BUILDER ->
                    context.goIdle();
        }

        return whereToStand(context);
    }

    private static Optional<BlockPos> whereToStand(WorkContext context) {
        if (context.state().isIdle()) {
            return Optional.empty();
        }
        return context.site().flatMap(site -> standingSpot(context.world(), site));
    }

    /**
     * Одна стройка — один билдер.
     * <p>
     * Иначе все билдеры колонии берутся за первую же стройку, идут к одному
     * блоку и толкаются на нём: путь у каждого сбивается о соседа, никто
     * не доходит, стройка встаёт. Со стороны это ровно «работники повисли».
     */
    private void findWork(WorkContext context) {
        for (Building site : context.settlement().buildings()) {
            if (!BuildJob.isUnderConstruction(site) || nextStepPosition(site).isEmpty()) {
                continue;
            }
            if (Claims.takenByAnother(context.settlement(), context.citizen(), site.id())) {
                continue;
            }
            context.setState(JobState.startAt(site.id(), JobState.Phase.TO_SITE));
            return;
        }
    }

    /**
     * Где стоять: на земле у стройки, а не на самой стройке.
     * <p>
     * Раньше целью был сам блок плана, и билдер лез на недоделанную стену.
     * Стоя на ней, он ломает себе путь: навигация ведёт вниз, решение гонит
     * наверх, шаг за шагом он топчется и сбивается — это и видно в игре как
     * «путь сбивается».
     * <p>
     * Место ищется кольцами от колонны блока: сперва снаружи следа здания,
     * потом внутри — по готовому полу стоять можно, оно ровное. Если ни одно
     * место не годится, целью снова становится сам блок: стройка обязана
     * идти, пусть и некрасиво.
     */
    private static Optional<BlockPos> standingSpot(ServerWorld world, Building site) {
        BlockPos target = nextStepPosition(site).orElse(null);
        if (target == null) {
            return Optional.empty();
        }

        BlockPos best = null;
        boolean bestOutside = false;

        for (int radius = 1; radius <= STAND_RADIUS; radius++) {
            for (BlockPos column : ring(target, radius)) {
                BlockPos spot = footing(world, site, column, target);
                if (spot == null) {
                    continue;
                }

                boolean outside = !footprintContains(site, spot);
                if (best == null || (outside && !bestOutside)) {
                    best = spot;
                    bestOutside = outside;
                }
                if (outside) {
                    return Optional.of(spot);
                }
            }
        }
        return Optional.of(best == null ? target : best);
    }

    /**
     * Опора в колонне: ближайшая к уровню земли стройки высота, на которой
     * можно стоять и с которой достаётся блок.
     */
    private static BlockPos footing(ServerWorld world, Building site, BlockPos column,
                                    BlockPos target) {
        int ground = site.anchor().getY();

        for (int y = ground - 1; y <= Math.min(target.getY(), ground + STAND_CLIMB); y++) {
            BlockPos spot = new BlockPos(column.getX(), y, column.getZ());
            if (canStandAt(world, spot)
                    && BuildJob.withinReach(spot, target, CitizenWorkGoal.SPREAD)) {
                return spot;
            }
        }
        return null;
    }

    /** Ноги на твёрдом, голова в пустоте — и то и другое обязательно. */
    private static boolean canStandAt(ServerWorld world, BlockPos spot) {
        return world.getBlockState(spot.down()).isSolidBlock(world, spot.down())
                && world.getBlockState(spot).getCollisionShape(world, spot).isEmpty()
                && world.getBlockState(spot.up()).getCollisionShape(world, spot.up()).isEmpty();
    }

    private static boolean footprintContains(Building site, BlockPos pos) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return false;
        }

        Vec3i size = BuildSite.rotatedSize(schematic.size(), site.rotation());
        BlockPos anchor = site.anchor();

        return pos.getX() >= anchor.getX() && pos.getX() < anchor.getX() + size.getX()
                && pos.getZ() >= anchor.getZ() && pos.getZ() < anchor.getZ() + size.getZ();
    }

    /** Кольцо колонн вокруг блока — по порядку, чтобы выбор был устойчив. */
    private static List<BlockPos> ring(BlockPos centre, int radius) {
        List<BlockPos> columns = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    columns.add(centre.add(dx, 0, dz));
                }
            }
        }
        return columns;
    }

    /** Мировая позиция следующего шага плана — туда билдер и идёт. */
    private static Optional<BlockPos> nextStepPosition(Building site) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        List<BuildStep> steps = schematic.plan().steps();
        if (site.nextStep() >= steps.size()) {
            return Optional.empty();
        }

        return Optional.of(BuildJob.worldPos(site, schematic.size(), steps.get(site.nextStep()).pos()));
    }
}
