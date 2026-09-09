package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Sounds;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.entity.CitizenWorkGoal;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Roads;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * <p>
 * Когда строить нечего, билдер <b>мостит улицы</b> — решение заказчика.
 * Тем же порядком: подошёл, положил тайл, перешёл. Работа эта вечная только
 * на вид: готовая улица не даёт тайлов, и билдер снова свободен.
 */
public final class BuilderJob implements Job {

    /** Логика стройки. Профессию, которая её выбирает, называет датапак. */
    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "build");

    /**
     * Докуда искать место, где встать.
     * <p>
     * Шире вытянутой руки намеренно: на склоне и в лесу опора рядом с целью
     * может не найтись вовсе, а в семи блоках — найдётся. Место всё равно
     * отбирается по вытянутой руке, так что дальше нужного билдер не встанет.
     */
    private static final int STAND_RADIUS = 7;

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
        if (site == null) {
            context.goIdle();
            return Optional.empty();
        }
        if (!BuildJob.isUnderConstruction(site)) {
            // Здание готово, но привязка осталась: значит, билдер занят
            // его улицей.
            return paveStreet(context, site);
        }

        BuildJob.Outcome outcome = BuildJob.advance(context.world(), context.manager(),
                context.settlement().id(), site.id(), 1, context.position());

        // В руке — тот блок, который он сейчас ставит. Работа должна быть
        // видна: без этого билдер машет пустыми руками, и понять, что
        // происходит, можно только по растущей стене.
        context.hold(nextBlockInHand(site));
        if (outcome == BuildJob.Outcome.ADVANCED) {
            context.swing();
        }

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
                // Уровень колонии равен уровню ратуши, и пересчитать его надо
                // здесь: достроили ратушу второго уровня — колония стала
                // деревней, и предел населения вырос тут же.
                Levels.refresh(context.settlement());

                // Колокол на всю колонию: здание сдано. Игрок мог смотреть
                // в другую сторону — объявление обязано его догнать.
                Sounds.buildingDone(context.world(), context.settlement().center());

                Housing.assignBeds(context.world(), context.settlement());
                Workplaces.assign(context.world(), context.settlement());
                context.holdNothing();
                context.goIdle();
            }

            // Стройки больше нет по другой причине.
            case ALREADY_DONE, NO_SCHEMATIC, NOT_LOADED, NOT_FOUND, NO_BUILDER -> {
                context.holdNothing();
                context.goIdle();
            }
        }

        return whereToStand(context);
    }

    private static Optional<BlockPos> whereToStand(WorkContext context) {
        if (context.state().isIdle()) {
            return Optional.empty();
        }

        Building site = context.site().orElse(null);
        if (site == null) {
            return Optional.empty();
        }
        if (!BuildJob.isUnderConstruction(site)) {
            // Улица: цель — место над тайлом, работу сделает следующее
            // решение. Класть блоки отсюда нельзя: это ответ на вопрос
            // «куда идти», а не «что делать».
            Block paving = Roads.paving(context.settlement(), context.warehouse());
            return Roads.nextTile(context.world(), context.settlement(), site, paving,
                    reachable(context)).map(BlockPos::up);
        }
        return standingSpot(context.world(), site);
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
            if (standingSpot(context.world(), site)
                    .filter(spot -> context.body().isUnreachable(spot)).isPresent()) {
                // К этому блоку билдер уже не смог подойти. Стройка при этом
                // остаётся свободной: другой билдер может стоять удачнее.
                continue;
            }
            context.setState(JobState.startAt(site.id(), JobState.Phase.TO_SITE));
            return;
        }

        findStreetWork(context);
    }

    /**
     * Стройки нет — значит, пора мостить.
     * <p>
     * Улица привязывается к тому зданию, от двери которого она идёт, и этим
     * же опознавателем делится между билдерами: занятость считает
     * {@link Claims}, и двое не возьмутся за одну дорожку.
     */
    private static void findStreetWork(WorkContext context) {
        Block paving = Roads.paving(context.settlement(), context.warehouse());

        for (Building done : context.settlement().buildings()) {
            if (BuildJob.isUnderConstruction(done)
                    || Claims.takenByAnother(context.settlement(), context.citizen(), done.id())) {
                continue;
            }
            if (Roads.nextTile(context.world(), context.settlement(), done, paving,
                    reachable(context)).isEmpty()) {
                continue;
            }
            context.setState(JobState.startAt(done.id(), JobState.Phase.TO_SITE));
            return;
        }
    }

    /**
     * Один тайл улицы за решение — тем же темпом, что и стройка: улица
     * должна расти на глазах, а не появляться разом.
     */
    private static Optional<BlockPos> paveStreet(WorkContext context, Building site) {
        Block paving = Roads.paving(context.settlement(), context.warehouse());
        BlockPos tile = Roads.nextTile(context.world(), context.settlement(), site, paving,
                reachable(context)).orElse(null);

        if (tile == null) {
            context.holdNothing();
            context.goIdle();
            return Optional.empty();
        }

        // В руке — то, чем мостит: лопата у натоптанной тропы, камень
        // или гравий у настоящей мостовой.
        context.hold(Roads.inHand(paving));

        if (!BuildJob.withinReach(context.position(), tile)) {
            context.setState(context.state().withPhase(JobState.Phase.TO_SITE));
            return Optional.of(tile.up());
        }

        if (Roads.pave(context.world(), context.warehouse(), tile, paving)) {
            context.swing();
        }
        context.setState(context.state().withPhase(JobState.Phase.WORKING));

        // Цель — место над тайлом: по улице ходят, а не стоят в ней.
        return Optional.of(tile.up());
    }

    /**
     * До тайла, к которому житель так и не смог подойти, улица не встаёт.
     * Без этого одна недостижимая точка держала бы дорожку навсегда.
     */
    private static java.util.function.Predicate<BlockPos> reachable(WorkContext context) {
        return tile -> !context.body().isUnreachable(tile.up());
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
     * потом внутри — по готовому полу стоять можно, оно ровное.
     * <p>
     * <b>В воздух билдера не посылают никогда.</b> Раньше, если подходящего
     * места не нашлось, целью становился сам блок плана — а он на крыше,
     * то есть в пустоте. Билдер шёл к нему, не доходил, следующее решение
     * гнало его снова, и стройка вставала: игрок видел работника, который
     * «пытается дотянуться и не может». Теперь в этом случае берётся
     * ближайшая опора, до которой человек хотя бы дойдёт, — пусть с неё
     * и не достать, зато следующий шаг плана он поставит.
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
        return Optional.of(best != null ? best : anyFooting(world, site, target));
    }

    /**
     * Хоть какая-нибудь земля у цели, без проверки на вытянутую руку.
     * <p>
     * Нужна ровно затем, чтобы <b>не назвать целью пустоту</b>. Дойдя сюда,
     * билдер до блока может и не достать — но он окажется у стройки, а не
     * будет ходить кругами под точкой в небе. И стоит он при этом на земле,
     * то есть путь себе не ломает.
     */
    private static BlockPos anyFooting(ServerWorld world, Building site, BlockPos target) {
        int ground = site.anchor().getY();

        for (int radius = 1; radius <= STAND_RADIUS; radius++) {
            for (BlockPos column : ring(target, radius)) {
                for (int y = ground + STAND_CLIMB; y >= ground - 2; y--) {
                    BlockPos spot = new BlockPos(column.getX(), y, column.getZ());
                    if (canStandAt(world, spot)) {
                        return spot;
                    }
                }
            }
        }

        // Земли нет вообще — стройка висит в пустоте. Тогда уж угол здания:
        // туда билдер по крайней мере не полезет наверх.
        return site.anchor();
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

    /**
     * Блок, который билдер держит: следующий по плану.
     * <p>
     * У расчистки предмета нет — там он машет кайлом, а не кладёт блок,
     * — и в руке тогда пусто. Пусто и у блоков без предмета: настенный
     * факел из воздуха не выложишь, но и показать «воздух» нельзя.
     */
    private static ItemStack nextBlockInHand(Building site) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return ItemStack.EMPTY;
        }

        List<BuildStep> steps = schematic.plan().steps();
        if (site.nextStep() >= steps.size()) {
            return ItemStack.EMPTY;
        }

        BuildStep step = steps.get(site.nextStep());
        if (!step.placesBlock()) {
            // Расчистка: в руке кайло. Не то, которым считается добыча —
            // там алмазное, чтобы руда падала, — а обычное железное:
            // алмазный инструмент у крестьянина смотрелся бы странно.
            return new ItemStack(Items.IRON_PICKAXE);
        }
        return Materials.itemFor(schematic.blockAt(step.paletteIndex()))
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
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
