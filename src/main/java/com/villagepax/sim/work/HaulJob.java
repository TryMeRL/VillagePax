package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
 * <p>
 * Сама переноска живёт в {@link Hauling}: носить умеет и билдер, когда носить
 * больше некому, и повторять это дважды незачем.
 */
public final class HaulJob implements Job {

    public static final Identifier COURIER = new Identifier(VillagePax.MOD_ID, "courier");

    /** Логика переноски. Профессию, которая её выбирает, называет датапак. */
    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "haul");

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        JobState state = context.state();

        // Груз, который нести уже некуда, возвращается на склад.
        if (state.hasStrandedLoad()) {
            Hauling.returnLoad(context);
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

        // Курьер держит в руках то, что несёт. Самый честный показ работы
        // в моде: игрок видит не «житель идёт», а «житель несёт брёвна
        // вон туда». Пустые руки — значит идёт за грузом.
        Hauling.showLoad(context);
        return whereToGo(context);
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
            case TO_STORAGE -> Hauling.whereToFetch(context, site);
            case TO_SITE -> Optional.of(site.anchor());
            case IDLE, WORKING -> Optional.empty();
        };
    }

    private void findWork(WorkContext context) {
        Warehouse warehouse = context.warehouse();

        for (Building site : context.settlement().byPriority()) {
            if (!BuildJob.isUnderConstruction(site) || BuildJob.storageIsNearby(warehouse, site)) {
                continue;
            }
            // Одна заявка — один курьер. Иначе двое несут одно и то же,
            // второй приходит с грузом, который уже не нужен, и уносит его
            // назад; а по дороге они толкаются на одной тропе.
            if (Claims.takenByAnother(context.settlement(), context.citizen(), site.id())) {
                continue;
            }
            if (context.body().isUnreachable(site.anchor())) {
                // До этой стройки курьер уже не смог дойти: пусть несёт
                // другой заявке, а к этой вернётся через полминуты.
                continue;
            }
            if (Hauling.wanted(context, site).isPresent()) {
                context.setState(JobState.startAt(site.id(), JobState.Phase.TO_STORAGE));
                return;
            }
        }
    }

    private void pickUp(WorkContext context, Building site) {
        // Руки полны — идти сдавать, а не стоять у сундука.
        if (context.state().usedSlots() >= Hauling.slots()) {
            context.setState(context.state().withPhase(JobState.Phase.TO_SITE));
            return;
        }
        if (Hauling.wanted(context, site).isEmpty()) {
            // Площадке больше ничего не нужно — или склад опустел. Что уже
            // в руках, всё равно донесём: возвращать это на склад значило бы
            // сходить туда-обратно даром.
            if (context.state().isCarrying()) {
                context.setState(context.state().withPhase(JobState.Phase.TO_SITE));
            } else {
                context.goIdle();
            }
            return;
        }
        if (Hauling.fillUp(context, site)) {
            context.setState(context.state().withPhase(JobState.Phase.TO_SITE));
        }
    }

    private void deliver(WorkContext context, Building site) {
        if (!context.state().isCarrying()) {
            // Пришёл с пустыми руками: значит надо было идти за материалами.
            context.setState(context.state().withPhase(JobState.Phase.TO_STORAGE));
            return;
        }
        if (Hauling.unload(context, site)) {
            context.setState(context.state().withPhase(JobState.Phase.IDLE));
        }
    }
}
