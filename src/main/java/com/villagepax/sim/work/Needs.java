package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.core.ModTags;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Нужды жителя: голод, довольство и уход из колонии.
 * <p>
 * Здесь колония перестаёт быть механизмом и становится живой системой:
 * игрок начинает не только строить, но и содержать. Счастье — главный
 * регулятор сложности всего мода, и это его первая настоящая работа.
 */
public final class Needs {

    /** Ниже этого житель идёт есть, вместо того чтобы работать. */
    public static final int HUNGRY_BELOW = 12;

    /** Сколько сытости уходит за игровой день. */
    public static final int DAILY_COST = 8;

    public static final int MAX_SATURATION = 40;

    /** На второй день недовольства житель жалуется, на третий уходит. */
    public static final int WARN_AFTER_DAYS = 2;
    public static final int LEAVE_AFTER_DAYS = 3;

    private static final int HAPPINESS_STARVING = 20;
    private static final int HAPPINESS_HUNGRY = 8;
    private static final int HAPPINESS_FED = 5;

    private Needs() {
    }

    public static boolean isHungry(Citizen citizen) {
        return citizen.saturation() < HUNGRY_BELOW;
    }

    /**
     * Голодный житель идёт к еде. Возвращает {@code true}, если поел.
     * <p>
     * Еда берётся со склада колонии, а не из воздуха: пока игрок не наладит
     * хозяйство, кормить придётся своими руками. Ферма приходит в задаче 1.9.
     */
    public static boolean goEat(WorkContext context) {
        Warehouse.Container source = context.warehouse()
                .nearestWithTag(context.body().getBlockPos(), ModTags.CITIZEN_FOOD)
                .orElse(null);

        if (source == null) {
            // Еды нет вовсе: идти некуда, и суточный подсчёт это заметит.
            context.body().setWorkTarget(null);
            return false;
        }

        context.body().setWorkTarget(source.pos());
        if (!context.hasArrivedAt(source.pos())) {
            return false;
        }

        Item eaten = Warehouse.takeTagged(source, ModTags.CITIZEN_FOOD).orElse(null);
        if (eaten == null) {
            return false;
        }

        Citizen citizen = context.citizen();
        citizen.setSaturation(Math.min(MAX_SATURATION, citizen.saturation() + nourishment(eaten)));
        citizen.contented();
        return true;
    }

    /**
     * Насколько сытно. Берётся из ванильной еды и удваивается: сутки стоят
     * восемь, а хлеб даёт пять — иначе один житель съедал бы каравай в день.
     */
    public static int nourishment(Item item) {
        return item.getFoodComponent() == null ? 2 : item.getFoodComponent().getHunger() * 2;
    }

    /**
     * Суточный подсчёт: голод, довольство, уход.
     * <p>
     * Считается днями, а не тиками, потому что игрок мыслит днями: «не кормил
     * две ночи» — понятная причина ухода, «12400 тиков неудовлетворённости» —
     * нет. Обрабатывается ровно один день за раз, даже если игрок промотал
     * сотню: голодная смерть всей колонии от команды {@code /time add} была
     * бы наказанием без предупреждения.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement settlement) {
        List<Citizen> leaving = new ArrayList<>();

        for (Citizen citizen : settlement.citizens()) {
            citizen.setSaturation(citizen.saturation() - DAILY_COST);

            if (citizen.saturation() <= 0) {
                citizen.setHappiness(citizen.happiness() - HAPPINESS_STARVING);
                citizen.addDiscontent();
            } else if (isHungry(citizen)) {
                citizen.setHappiness(citizen.happiness() - HAPPINESS_HUNGRY);
                citizen.addDiscontent();
            } else {
                citizen.setHappiness(citizen.happiness() + HAPPINESS_FED);
                citizen.contented();
            }

            if (citizen.discontent() >= LEAVE_AFTER_DAYS) {
                leaving.add(citizen);
            } else if (citizen.discontent() == WARN_AFTER_DAYS) {
                tell(world, settlement, "villagepax.citizen.hungry", citizen.fullName());
            }
        }

        for (Citizen citizen : leaving) {
            leave(world, settlement, citizen);
        }

        Housing.assignBeds(world, settlement);
        Housing.welcomeNewcomer(world, settlement, new java.util.Random(world.getRandom().nextLong()))
                .ifPresent(newcomer -> tell(world, settlement,
                        "villagepax.citizen.arrived", newcomer.fullName()));
    }

    /**
     * Житель уходит навсегда — решение заказчика: сперва предупреждение,
     * потом полсилы, потом уход. Потеря больная, но заслуженная.
     */
    private static void leave(ServerWorld world, Settlement settlement, Citizen citizen) {
        citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .ifPresent(body -> body.discard());

        settlement.removeCitizen(citizen.id());
        tell(world, settlement, "villagepax.citizen.left", citizen.fullName());
        VillagePax.LOGGER.info("Житель {} ушёл из поселения {}: {} дней недовольства",
                citizen.fullName(), settlement.name(), citizen.discontent());
    }

    /** Работает ли житель в полную силу. Недовольный тянет вполсилы. */
    public static boolean worksAtFullStrength(Citizen citizen) {
        return !citizen.isUnhappy();
    }

    /** Сообщение хозяину колонии. Автономная деревня никому не жалуется. */
    private static void tell(ServerWorld world, Settlement settlement, String key, Object... args) {
        Optional<java.util.UUID> owner = settlement.owner().player();
        if (owner.isEmpty()) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner.get());
        if (player != null) {
            player.sendMessage(Text.translatable(key, args), false);
        }
    }
}
