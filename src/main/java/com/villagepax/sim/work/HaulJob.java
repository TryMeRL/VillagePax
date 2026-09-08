package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Optional;

/**
 * Курьер: носит материалы со склада на стройплощадку.
 * <p>
 * Нужен там, где склад далеко: стройка под боком у ратуши идёт сама, потому
 * что билдер дотягивается до сундука. Вынесенная за околицу — требует людей.
 * Так выбор места становится решением игрока, а не декорацией.
 * <p>
 * Цикл: {@code IDLE → TO_STORAGE → TO_SITE → IDLE}. Что именно нести,
 * пересчитывается на месте, а не запоминается: пока курьер шёл, заявка могла
 * измениться, и лучше принести нужное сейчас, чем нужное десять секунд назад.
 */
public final class HaulJob implements Job {

    public static final Identifier COURIER = new Identifier(VillagePax.MOD_ID, "courier");

    /** Сколько курьер несёт за раз. Полстопки: ходка должна быть заметной, но не вечной. */
    public static final int CARRY = 32;

    /**
     * На сколько шагов плана вперёд смотрит заявка.
     * <p>
     * Считать нужду до конца схемы — это обход трёхсот шагов на каждое
     * решение курьера. Окна хватает: курьер принесёт то, что понадобится
     * скоро, и вернётся снова.
     */
    public static final int LOOKAHEAD = 64;

    /** Логика переноски. Профессию, которая её выбирает, называет датапак. */
    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "haul");

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        JobState state = context.state();

        // Груз, который нести уже некуда, возвращается на склад. Иначе
        // материалы навсегда остаются в руках жителя, и игрок не поймёт,
        // куда девались тридцать брёвен.
        if (state.hasStrandedLoad()) {
            returnLoad(context);
            return whereToGo(context);
        }

        if (state.isIdle()) {
            findWork(context);
            return whereToGo(context);
        }

        Building site = context.site().orElse(null);
        if (site == null || !BuildJob.isUnderConstruction(site)) {
            context.goIdle();
            return whereToGo(context);
        }

        switch (state.phase()) {
            case TO_STORAGE -> pickUp(context, site);
            case TO_SITE -> deliver(context, site);
            case IDLE, WORKING -> context.goIdle();
        }

        showLoad(context);
        return whereToGo(context);
    }

    /**
     * Курьер держит в руках то, что несёт.
     * <p>
     * Самый честный показ работы в моде: игрок видит не «житель идёт»,
     * а «житель несёт двадцать брёвен вон туда». Пустые руки — значит
     * идёт за грузом.
     */
    private static void showLoad(WorkContext context) {
        context.hold(context.state().carried()
                .map(load -> new ItemStack(Registries.ITEM.get(load.item()), load.count()))
                .orElse(ItemStack.EMPTY));
    }

    private Optional<BlockPos> whereToGo(WorkContext context) {
        JobState state = context.state();

        if (state.hasStrandedLoad()) {
            return context.warehouse().nearest(context.body().getBlockPos())
                    .map(Warehouse.Container::pos);
        }

        Building site = context.site().orElse(null);
        if (site == null) {
            return Optional.empty();
        }

        return switch (state.phase()) {
            case TO_STORAGE -> wanted(context, site)
                    .flatMap(request -> context.warehouse()
                            .nearestWith(context.body().getBlockPos(), request.item(), 1))
                    .map(Warehouse.Container::pos);
            case TO_SITE -> Optional.of(site.anchor());
            case IDLE, WORKING -> Optional.empty();
        };
    }

    private void findWork(WorkContext context) {
        Warehouse warehouse = context.warehouse();

        for (Building site : context.settlement().buildings()) {
            if (!BuildJob.isUnderConstruction(site) || BuildJob.storageIsNearby(warehouse, site)) {
                continue;
            }
            // Одна заявка — один курьер. Иначе двое несут одно и то же,
            // второй приходит с грузом, который уже не нужен, и уносит его
            // назад; а по дороге они толкаются на одной тропе.
            if (Claims.takenByAnother(context.settlement(), context.citizen(), site.id())) {
                continue;
            }
            if (shortfallCoveredByStorage(context, site, warehouse).isPresent()) {
                context.setState(JobState.startAt(site.id(), JobState.Phase.TO_STORAGE));
                return;
            }
        }
    }

    private void pickUp(WorkContext context, Building site) {
        Request request = wanted(context, site).orElse(null);
        if (request == null) {
            // Площадке больше ничего не нужно — или склад опустел.
            context.goIdle();
            return;
        }

        Item item = request.item();
        Warehouse.Container source = context.warehouse()
                .nearestWith(context.body().getBlockPos(), item, 1)
                .orElse(null);
        if (source == null) {
            context.goIdle();
            return;
        }
        if (!context.hasArrivedAt(source.pos())) {
            return;
        }

        // Берём не больше, чем площадке нужно: остатки возвращаются на склад
        // при сдаче здания, но лишняя ходка туда-обратно — работа ради работы.
        int taken = Warehouse.takeFrom(source, item, Math.min(CARRY, request.count()));
        if (taken <= 0) {
            context.goIdle();
            return;
        }

        context.setState(context.state()
                .carrying(Registries.ITEM.getId(item), taken)
                .withPhase(JobState.Phase.TO_SITE));
    }

    private void deliver(WorkContext context, Building site) {
        JobState.Load load = context.state().carried().orElse(null);
        if (load == null) {
            // Пришёл с пустыми руками: значит надо было идти за материалами.
            context.setState(context.state().withPhase(JobState.Phase.TO_STORAGE));
            return;
        }
        if (!context.hasArrivedAt(site.anchor())) {
            return;
        }

        site.stock().add(load.item(), load.count());
        context.setState(context.state().emptyHanded().withPhase(JobState.Phase.IDLE));
    }

    private void returnLoad(WorkContext context) {
        JobState.Load load = context.state().carried().orElseThrow();
        Warehouse warehouse = context.warehouse();
        BlockPos where = context.body().getBlockPos();

        Warehouse.Container target = warehouse.nearest(where).orElse(null);
        if (target != null && !context.hasArrivedAt(target.pos())) {
            return;
        }

        Item item = Registries.ITEM.get(load.item());
        // Если хранилищ нет вовсе, груз всё равно не пропадает: он ляжет
        // под ноги, и игрок его увидит.
        warehouse.addOrScatter(context.world(), where, new ItemStack(item, load.count()));
        context.setState(JobState.IDLE);
    }

    /** Что и сколько нести. */
    private record Request(Item item, int count) {
    }

    /** Что нести: первое из нужного площадке, чего на складе действительно есть. */
    private Optional<Request> wanted(WorkContext context, Building site) {
        return shortfallCoveredByStorage(context, site, context.warehouse());
    }

    private Optional<Request> shortfallCoveredByStorage(WorkContext context, Building site,
                                                        Warehouse warehouse) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        // Порядок обхода — порядок плана, поэтому выбор устойчив: иначе
        // курьер метался бы между двумя видами блоков от решения к решению.
        Map<Item, Integer> shortfall = Materials.shortfall(schematic, site, LOOKAHEAD);
        for (Map.Entry<Item, Integer> entry : shortfall.entrySet()) {
            if (warehouse.has(entry.getKey(), 1)) {
                return Optional.of(new Request(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.empty();
    }
}
