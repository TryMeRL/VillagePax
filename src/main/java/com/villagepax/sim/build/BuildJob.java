package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Двигатель стройки: билдер идёт по плану и ставит блоки по одному.
 * <p>
 * Прогресс живёт в {@link Building#nextStep()}, то есть в данных поселения.
 * Билдер может выгрузиться вместе с чанком, погибнуть или сменить работу —
 * стройка продолжится с того же места, а не начнётся заново.
 * <p>
 * Темп задаётся снаружи: {@link #advance} выполняет до {@code maxSteps} шагов
 * и возвращает, чем всё кончилось. Тикер вызывает с единицей раз в
 * {@link #TICKS_PER_STEP}, игровой тест — с большим числом сразу. Иначе
 * приёмочный тест либо занял бы полторы минуты игрового времени, либо
 * проверял бы не то, что работает в игре.
 */
public final class BuildJob {

    /** Один блок за полсекунды: стройка должна быть видна как процесс. */
    public static final int TICKS_PER_STEP = 10;

    /** Профессия, без которой стройка не идёт. Данными станет в задаче 1.9. */
    public static final Identifier BUILDER = new Identifier(VillagePax.MOD_ID, "builder");

    /**
     * Инструмент билдера. Нужен только для расчёта добычи с расчистки: без
     * инструмента таблицы добычи ванили не отдают ни булыжника, ни руды,
     * и расчистка каменистой площадки не приносила бы ничего.
     * <p>
     * Каждый раз новый: {@code ItemStack} изменяем, а общего изменяемого
     * состояния в моде быть не должно.
     */
    private static ItemStack tool() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    public enum Outcome {
        /** Поселения или здания с такими идентификаторами нет. */
        NOT_FOUND,
        /** Для типа и уровня здания не нашлось схемы в датапаках. */
        NO_SCHEMATIC,
        /** Чанк здания не загружен: ставить блоки в него нельзя. */
        NOT_LOADED,
        /** В поселении нет ни одного строителя. */
        NO_BUILDER,
        /** Не хватает материалов на складе. Билдер ждёт. */
        WAITING_FOR_MATERIALS,
        /** Шаги выполнены, стройка продолжается. */
        ADVANCED,
        /** Здание достроено на этом обращении. */
        FINISHED,
        /** Здание уже готово, делать нечего. */
        ALREADY_DONE
    }

    private BuildJob() {
    }

    /** Схема для здания: по соглашению об именовании, пока нет типов зданий из датапака. */
    public static Identifier schematicId(Building building) {
        return new Identifier(building.type().getNamespace(),
                building.type().getPath() + "_lvl" + building.level());
    }

    /** Обратное соглашение: {@code norman/town_hall_lvl1} → тип {@code norman/town_hall}. */
    public static Optional<Identifier> buildingTypeOf(Identifier schematic) {
        String path = schematic.getPath();
        int marker = path.lastIndexOf("_lvl");
        return marker < 0
                ? Optional.empty()
                : Optional.of(new Identifier(schematic.getNamespace(), path.substring(0, marker)));
    }

    public static Optional<Integer> levelOf(Identifier schematic) {
        String path = schematic.getPath();
        int marker = path.lastIndexOf("_lvl");
        if (marker < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(path.substring(marker + "_lvl".length())));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    public static boolean isUnderConstruction(Building building) {
        return building.progress() == BuildProgress.PLANNED
                || building.progress() == BuildProgress.BUILDING
                || building.progress() == BuildProgress.DAMAGED;
    }

    /**
     * Продвинуть стройку. Изменения идут через {@link SettlementManager#apply},
     * поэтому состояние помечается грязным и переживает перезаход в мир.
     */
    public static Outcome advance(ServerWorld world, SettlementManager manager,
                                  UUID settlementId, UUID buildingId, int maxSteps) {
        return manager.apply(settlementId, settlement -> settlement.building(buildingId)
                        .map(building -> run(world, settlement, building, maxSteps))
                        .orElse(Outcome.NOT_FOUND))
                .orElse(Outcome.NOT_FOUND);
    }

    private static Outcome run(ServerWorld world, Settlement settlement, Building building, int maxSteps) {
        if (!isUnderConstruction(building)) {
            return Outcome.ALREADY_DONE;
        }
        if (!hasBuilder(settlement)) {
            return Outcome.NO_BUILDER;
        }

        Schematic schematic = SchematicLoader.get(schematicId(building)).orElse(null);
        if (schematic == null) {
            return Outcome.NO_SCHEMATIC;
        }

        if (!footprintIsLoaded(world, building, schematic)) {
            // Ставить блоки в незагруженный чанк — значит принудительно его
            // загрузить. Проверяется весь след, а не чанк якоря: здание 7x7
            // спокойно ложится на два чанка. Далёкие поселения тикают
            // упрощённо, это задача 1.12.
            return Outcome.NOT_LOADED;
        }

        List<BuildStep> steps = schematic.plan().steps();

        // Повреждённое здание чинится по той же схеме, и план обязан пройтись
        // заново: у него nextStep стоит в конце с прошлой стройки, иначе
        // ремонт мгновенно "завершился" бы, не поставив ни блока.
        if (building.progress() == BuildProgress.DAMAGED) {
            building.restartBuilding();
        }
        building.setProgress(BuildProgress.BUILDING);

        int done = 0;
        while (done < maxSteps && building.nextStep() < steps.size()) {
            if (!perform(world, settlement, building, schematic, steps.get(building.nextStep()))) {
                return Outcome.WAITING_FOR_MATERIALS;
            }
            building.advanceStep();
            done++;
        }

        if (building.nextStep() >= steps.size()) {
            building.setProgress(BuildProgress.DONE);
            return Outcome.FINISHED;
        }
        return Outcome.ADVANCED;
    }

    /**
     * Один шаг. Возвращает {@code false}, если не хватило материала — тогда
     * индекс не двигается и билдер попробует снова на следующем обращении.
     */
    private static boolean perform(ServerWorld world, Settlement settlement, Building building,
                                   Schematic schematic, BuildStep step) {
        BlockPos where = worldPos(building, schematic.size(), step.pos());

        if (!step.placesBlock()) {
            // Пустое место уже пустое: лишняя запись в мир тянула бы за собой
            // каскад уведомлений соседей на каждом шаге расчистки, а при
            // ремонте почти вся расчистка — как раз пустая.
            if (!world.getBlockState(where).isAir()) {
                salvage(world, settlement.warehouse(), where);
                world.setBlockState(where, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            return true;
        }

        BlockState planned = schematic.blockAt(step.paletteIndex()).rotate(building.rotation());

        // Нужный блок уже на месте — ни материала, ни установки. Без этого
        // ремонт повреждённого здания списывал бы со склада всю схему целиком,
        // хотя починить надо пару блоков.
        if (world.getBlockState(where).isOf(planned.getBlock())) {
            return true;
        }

        Optional<Identifier> material = Materials.itemFor(planned);
        if (material.isPresent() && !settlement.warehouse().take(material.get(), 1)) {
            return false;
        }

        salvage(world, settlement.warehouse(), where);

        // Состояние досчитывается по окружению до установки, а соседей
        // уведомляем после: иначе стёкла и заборы встают несоединёнными —
        // setBlockState, в отличие от установки блока игроком, окружение
        // не смотрит.
        world.setBlockState(where, Block.postProcessState(planned, world, where), Block.NOTIFY_ALL);
        return true;
    }

    /**
     * Снести то, что мешает, и сдать добычу на склад.
     * <p>
     * Решение заказчика: расчистка приносит материалы. Поэтому выбор места —
     * экономическое решение, а не только эстетическое: стройка в лесу дороже
     * по времени, но выгоднее по брёвнам.
     */
    private static void salvage(ServerWorld world, Warehouse warehouse, BlockPos pos) {
        BlockState existing = world.getBlockState(pos);
        if (existing.isAir()) {
            return;
        }

        BlockEntity blockEntity = existing.hasBlockEntity() ? world.getBlockEntity(pos) : null;
        for (ItemStack drop : Block.getDroppedStacks(existing, world, pos, blockEntity, null, tool())) {
            if (!drop.isEmpty()) {
                warehouse.add(Registries.ITEM.getId(drop.getItem()), drop.getCount());
            }
        }
    }

    private static boolean footprintIsLoaded(ServerWorld world, Building building, Schematic schematic) {
        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        BlockPos far = anchor.add(footprint.getX() - 1, 0, footprint.getZ() - 1);
        return world.isChunkLoaded(anchor)
                && world.isChunkLoaded(far)
                && world.isChunkLoaded(new BlockPos(anchor.getX(), anchor.getY(), far.getZ()))
                && world.isChunkLoaded(new BlockPos(far.getX(), anchor.getY(), anchor.getZ()));
    }

    public static BlockPos worldPos(Building building, Vec3i size, BlockPos local) {
        return BuildSite.toWorld(building.anchor(), size, building.rotation(), local);
    }

    /** Точки интереса готового здания в координатах мира. */
    public static List<BlockPos> pointsOfInterest(Building building, Schematic schematic, MarkerKind kind) {
        return schematic.plan().positionsOf(kind).stream()
                .map(local -> worldPos(building, schematic.size(), local))
                .toList();
    }

    private static boolean hasBuilder(Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(BUILDER::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
