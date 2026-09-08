package com.villagepax.sim.work;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Всё, что нужно логике профессии на один шаг стратегии.
 * <p>
 * Тело здесь обязательно: работа идёт только там, где житель есть в мире.
 * Незагруженное поселение не должно стоить сервера ничего — это несущее
 * решение по производительности, и оно держится именно на этом.
 */
public final class WorkContext {

    /** Насколько близко надо подойти, чтобы считаться пришедшим. */
    public static final double ARRIVAL_REACH = 2.5;

    private final ServerWorld world;
    private final SettlementManager manager;
    private final Settlement settlement;
    private final Citizen citizen;
    private final CitizenEntity body;

    /**
     * Склад за одно решение собирается один раз.
     * <p>
     * Без этого логика профессии собирает его по три-четыре раза на шаг —
     * при обходе всех зданий и всех точек интереса. Именно на такой мелочи
     * моды с работниками и садятся: поиск пути и сборка видов идут в главном
     * потоке сервера, и лишние обходы складываются в просадку.
     * <p>
     * Кэш живёт ровно один шаг стратегии: следующий шаг соберёт склад заново,
     * потому что сундук могли сломать.
     */
    private Warehouse warehouse;

    public WorkContext(ServerWorld world, SettlementManager manager, Settlement settlement,
                       Citizen citizen, CitizenEntity body) {
        this.world = world;
        this.manager = manager;
        this.settlement = settlement;
        this.citizen = citizen;
        this.body = body;
    }

    public ServerWorld world() {
        return world;
    }

    public SettlementManager manager() {
        return manager;
    }

    public Settlement settlement() {
        return settlement;
    }

    public Citizen citizen() {
        return citizen;
    }

    public CitizenEntity body() {
        return body;
    }

    public JobState state() {
        return citizen.jobState();
    }

    public void setState(JobState state) {
        citizen.setJobState(state);
    }

    public Vec3d position() {
        return body.getPos();
    }

    public Warehouse warehouse() {
        if (warehouse == null) {
            warehouse = Warehouse.of(world, settlement);
        }
        return warehouse;
    }

    /** Здание, к которому привязана работа, если оно ещё существует. */
    public Optional<Building> site() {
        return state().building().flatMap(settlement::building);
    }

    public boolean hasArrivedAt(BlockPos target) {
        return position().squaredDistanceTo(Vec3d.ofCenter(target)) <= ARRIVAL_REACH * ARRIVAL_REACH;
    }

    /**
     * Что житель держит в руке.
     * <p>
     * Работа должна быть видна: строитель с блоком, который ставит,
     * лесоруб с топором, курьер с брёвнами. Одинаковый предмет заново
     * не выдаётся — смена снаряжения уходит в сеть всем, кто видит
     * жителя, и делать это каждое решение незачем.
     */
    public void hold(ItemStack tool) {
        if (!ItemStack.areEqual(body.getMainHandStack(), tool)) {
            body.equipStack(EquipmentSlot.MAINHAND, tool);
        }
    }

    public void holdNothing() {
        hold(ItemStack.EMPTY);
    }

    /** Замахнуться: удар кайлом, взмах топором, движение при посадке. */
    public void swing() {
        body.swingHand(Hand.MAIN_HAND);
    }

    /** Отпустить работу. Груз в руках при этом сохраняется — его вернут на склад. */
    public void goIdle() {
        setState(state().withPhase(JobState.Phase.IDLE));
    }
}
