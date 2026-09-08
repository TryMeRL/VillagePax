package com.villagepax.sim.work;

import com.villagepax.sim.Building;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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

    @Override
    public Identifier profession() {
        return BuildJob.BUILDER;
    }

    /**
     * Цель — всегда следующий блок плана, и в фазе работы тоже.
     * <p>
     * Отпускать цель на время работы нельзя: без точки цель навигации
     * останавливается, управление забирает прогулка, билдер уходит — и
     * следующее решение гонит его обратно. Получаются качели. Пока точка
     * задана, он уже рядом с ней и просто стоит.
     */
    @Override
    public Optional<BlockPos> destination(WorkContext context) {
        if (context.state().isIdle()) {
            return Optional.empty();
        }
        return context.site().flatMap(BuilderJob::nextStepPosition);
    }

    @Override
    public void tick(WorkContext context) {
        JobState state = context.state();

        if (state.isIdle()) {
            findWork(context);
            return;
        }

        Building site = context.site().orElse(null);
        if (site == null || !BuildJob.isUnderConstruction(site)) {
            context.goIdle();
            return;
        }

        BuildJob.Outcome outcome = BuildJob.advance(context.world(), context.manager(),
                context.settlement().id(), site.id(), 1, context.position());

        switch (outcome) {
            // Дошли до блока за пределами вытянутой руки: перейти к нему.
            case OUT_OF_REACH -> context.setState(state.withPhase(JobState.Phase.TO_SITE));

            // Работа идёт — или ждём материалов, стоя на месте: уходить нельзя,
            // иначе билдер начнёт бегать кругами, пока курьер несёт брёвна.
            case ADVANCED, WAITING_FOR_MATERIALS -> context.setState(state.withPhase(JobState.Phase.WORKING));

            // Стройки больше нет — по любой причине.
            case FINISHED, ALREADY_DONE, NO_SCHEMATIC, NOT_LOADED, NOT_FOUND, NO_BUILDER ->
                    context.goIdle();
        }
    }

    private void findWork(WorkContext context) {
        for (Building site : context.settlement().buildings()) {
            if (BuildJob.isUnderConstruction(site) && nextStepPosition(site).isPresent()) {
                context.setState(JobState.startAt(site.id(), JobState.Phase.TO_SITE));
                return;
            }
        }
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
