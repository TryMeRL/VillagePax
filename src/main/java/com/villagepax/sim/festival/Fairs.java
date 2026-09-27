package com.villagepax.sim.festival;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.ModTags;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ярмарка поселения и её места: где сердце праздника, где загон, откуда
 * стреляют, где мишени и столы.
 * <p>
 * Всё берётся <b>из схемы</b>, а не ищется в мире: схема знает место точно,
 * а поиск вокруг нашёл бы и мишень соседней ярмарки, стоящей в двух шагах.
 * Тем же правилом храм узнаётся по алтарю ({@code Altars}). Мир в расчётах
 * не нужен вовсе — поэтому места ярмарки известны и тогда, когда её чанк
 * выгружен, и проверка спрашивает их без единого блока в мире.
 * <p>
 * Ярмарка — здание, в котором работает затейник, а не здание с таким
 * именем: народ из датапака вправе назвать свою ярмарку как угодно.
 */
public final class Fairs {

    private Fairs() {
    }

    /** Готовая ярмарка поселения. */
    public static Optional<Fair> of(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (building.isOperational()
                    && BuildingTypes.employs(building.type(), Villages.ENTERTAINER)) {
                Optional<Fair> fair = of(building);
                if (fair.isPresent()) {
                    return fair;
                }
            }
        }
        return Optional.empty();
    }

    /** Места одной ярмарки по её схеме. Пусто — у схемы нет сердца или прилавка. */
    public static Optional<Fair> of(Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }
        List<BlockPos> hearts = blocks(building, schematic,
                state -> state.isIn(ModTags.FESTIVAL_HEARTS));
        List<BlockPos> counters = BuildJob.pointsOfInterest(building, schematic,
                MarkerKind.WORKSTATION);
        if (hearts.isEmpty() || counters.isEmpty()) {
            return Optional.empty();
        }
        BlockPos heart = hearts.stream().min(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        Box area = new Box(anchor, anchor.add(size));
        return Optional.of(new Fair(building, heart,
                BuildJob.pointsOfInterest(building, schematic, MarkerKind.PEN),
                BuildJob.pointsOfInterest(building, schematic, MarkerKind.SHOOTING),
                blocks(building, schematic, state -> state.isOf(ModBlocks.ARCHERY_TARGET)),
                blocks(building, schematic, state -> state.isOf(ModBlocks.TABLE)),
                counters.get(0), area));
    }

    /** Где в мире стоят блоки схемы, отвечающие условию. */
    private static List<BlockPos> blocks(Building building, Schematic schematic,
                                         Predicate<BlockState> wanted) {
        List<BlockPos> found = new ArrayList<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (step.placesBlock() && wanted.test(schematic.blockAt(step.paletteIndex()))) {
                found.add(BuildJob.worldPos(building, schematic.size(), step.pos()));
            }
        }
        return found;
    }
}
