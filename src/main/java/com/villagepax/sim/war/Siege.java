package com.villagepax.sim.war;

import com.villagepax.VillagePax;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Что набег делает с добром колонии: бьёт стены и уносит со склада.
 * <p>
 * Дизайн-документ обещает осаду: «атака на здания, {@code DAMAGED}-состояние,
 * гибель жителей, разграбление склада». Гибель жителей приехала вместе с
 * отрядом; здесь — остальные три четверти.
 * <p>
 * <b>Ломать выгодно тем, что чинить платно.</b> Выбитые блоки не падают
 * предметами: их нет, и билдер поставит новые <b>со склада</b>. Ремонт
 * при этом уже написан и проверен — повреждённое здание чинится по той же
 * схеме, пропуская целые стены и задерживаясь на пробоинах. Набегу
 * осталось только сделать пробоины.
 * <p>
 * <b>Чего не ломают.</b> Сундуки, станки и сам блок ратуши — всё, у чего
 * есть блок-сущность. Причины две, и обе несущие. Сломанный сундук высыпал
 * бы своё содержимое под ноги — то есть отдал бы игроку то, что отряд
 * пришёл унести. А ратуша — это уже не разорение, а <b>захват поселения</b>:
 * отдельная механика с другим исходом (дань, смена владельца), и подменять
 * её ломом было бы обманом. Двести часов работы не должны исчезать от
 * одного набега.
 * <p>
 * <b>Разоряют по дому на бойца, и только пока боец жив.</b> Отсюда простая
 * и честная цена спешки: чем быстрее игрок перебьёт пришедших, тем меньше
 * домов будет разорено.
 */
public final class Siege {

    /** Сколько блоков выбивают из одного здания. */
    private static final int HOLES = 6;

    /** Сколько вещей уносит один уцелевший боец. */
    private static final int LOOT_PER_FIGHTER = 16;

    /** Как далеко от бойца ищется здание, которое он разорит. */
    private static final int WRECK_REACH = 12;

    private Siege() {
    }

    /**
     * Разорить здание рядом с бойцом.
     *
     * @return {@code true}, если здание разорено — тогда счёт отряда растёт
     */
    public static boolean wreck(ServerWorld world, SettlementManager manager, Settlement colony,
                                CitizenEntity fighter) {
        Building target = nearestStanding(colony, fighter.getBlockPos());
        if (target == null) {
            return false;
        }

        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(target)).orElse(null);
        if (schematic == null) {
            return false;
        }

        List<BlockPos> holes = breakable(world, target, schematic);
        if (holes.isEmpty()) {
            return false;
        }

        int knocked = 0;
        // Через одну: шесть подряд выбитых блоков — это аккуратная дыра,
        // а разорённый дом должен выглядеть разорённым.
        int step = Math.max(1, holes.size() / HOLES);
        for (int index = 0; index < holes.size() && knocked < HOLES; index += step) {
            world.setBlockState(holes.get(index), Blocks.AIR.getDefaultState());
            knocked++;
        }
        if (knocked == 0) {
            return false;
        }

        manager.update(colony.id(), state -> state.building(target.id())
                .ifPresent(building -> building.setProgress(BuildProgress.DAMAGED)));

        tell(world, colony, "villagepax.raid.wrecked",
                Text.translatable(BuildingTypes.displayName(target.type())));
        VillagePax.LOGGER.info("Набег разорил {} в {}: выбито {} блоков",
                target.type(), colony.name(), knocked);
        return true;
    }

    /**
     * Унести со склада.
     * <p>
     * Уносят <b>уцелевшие</b>: перебитый отряд не уносит ничего. Берут что
     * попало и сколько унесут — это грабёж, а не торг, и выбирать добычу
     * по ценности отряду некогда.
     *
     * @return что унесли: это же и довезут домой
     */
    public static List<ItemStack> plunder(ServerWorld world, Settlement colony, int survivors) {
        if (survivors <= 0) {
            return List.of();
        }

        Warehouse warehouse = Warehouse.of(world, colony);
        ItemTally have = warehouse.tally();
        if (have.isEmpty()) {
            return List.of();
        }

        int quota = LOOT_PER_FIGHTER * survivors;
        List<ItemStack> taken = new ArrayList<>();

        for (Map.Entry<net.minecraft.util.Identifier, Integer> kind : have.contents().entrySet()) {
            if (quota <= 0) {
                break;
            }
            Item item = Registries.ITEM.get(kind.getKey());
            int amount = Math.min(quota, kind.getValue());
            if (amount > 0 && warehouse.take(item, amount)) {
                taken.add(new ItemStack(item, amount));
                quota -= amount;
            }
        }

        int carried = taken.stream().mapToInt(ItemStack::getCount).sum();
        if (carried <= 0) {
            return List.of();
        }

        tell(world, colony, "villagepax.raid.plundered",
                Text.literal(String.valueOf(carried)));
        VillagePax.LOGGER.info("Набег унёс из {} вещей: {}", colony.name(), carried);
        return taken;
    }

    /**
     * Довезти добычу до дома.
     * <p>
     * Только если дом загружен: ставить блоки и класть вещи в незагруженный
     * чанк значит заставить мир сгенерировать его здесь и сейчас — правило,
     * на котором держится вся производительность мода. Не довезли — значит
     * пропало, и это честнее, чем телепорт.
     */
    public static void bringHome(ServerWorld world, Settlement home, List<ItemStack> loot) {
        if (loot.isEmpty() || !world.isChunkLoaded(home.center())) {
            return;
        }
        Warehouse warehouse = Warehouse.of(world, home);
        loot.forEach(warehouse::add);
    }

    /** Ближайшее целое здание колонии — то, которое и разорят. */
    private static Building nearestStanding(Settlement colony, BlockPos from) {
        Building best = null;
        double bestAway = (double) WRECK_REACH * WRECK_REACH;

        for (Building building : colony.buildings()) {
            if (building.progress() != BuildProgress.DONE) {
                // Недостроенное и уже разорённое не трогают: пробоина
                // в пробоине не видна, а стройку и без того видно.
                continue;
            }
            double away = building.anchor().getSquaredDistance(from);
            if (away <= bestAway) {
                bestAway = away;
                best = building;
            }
        }
        return best;
    }

    /**
     * Блоки здания, которые можно выбить.
     * <p>
     * Берутся <b>из схемы</b>, а не из мира по коробке: так выбитое будет
     * ровно тем, что билдер обязан восстановить, и ремонт сойдётся блок
     * в блок. Всё, у чего есть блок-сущность, пропускается — см. описание
     * класса.
     * <p>
     * Открыт затем, что проверка спрашивает его <b>прямо</b>. Иначе
     * правило «сундуки не ломают» держалось бы на удаче: выбивают шесть
     * блоков из сотни, и попасть проверкой именно в сундук почти
     * невозможно — то есть снятый запрет никто бы не заметил.
     */
    public static List<BlockPos> breakable(ServerWorld world, Building building,
                                           Schematic schematic) {
        List<BlockPos> spots = new ArrayList<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockPos where = BuildJob.worldPos(building, schematic.size(), step.pos());
            if (world.getBlockState(where).isAir() || world.getBlockEntity(where) != null) {
                continue;
            }
            spots.add(where);
        }
        return spots;
    }

    private static void tell(ServerWorld world, Settlement colony, String key, Text what) {
        colony.owner().player().ifPresent(owner -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) {
                player.sendMessage(Text.translatable(key, what).formatted(Formatting.RED), false);
            }
        });
    }
}
