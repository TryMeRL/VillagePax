package com.villagepax.sim.build;

import com.villagepax.core.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Превращает содержимое схемы в план стройки.
 * <p>
 * Единственное место, где блокстейт вообще смотрят: дальше по конвейеру идут
 * только номера в палитре, и потому порядок стройки проверяется тестами
 * без запуска игры.
 */
public final class BuildPlanner {

    private BuildPlanner() {
    }

    public static BuildPlan plan(Vec3i size, List<BlockState> palette,
                                 List<Schematic.PalettedBlock> blocks,
                                 List<PointOfInterest> markers) {
        List<BuildStep> steps = new ArrayList<>(blocks.size());

        for (Schematic.PalettedBlock block : blocks) {
            BlockState state = palette.get(block.paletteIndex());

            // Пустота структуры — это «не трогать»: так автор схемы оставляет
            // рельеф на месте. Шага не будет вовсе, иначе билдер выбил бы
            // склон, который его специально попросили не касаться.
            if (state.isOf(Blocks.STRUCTURE_VOID)) {
                continue;
            }

            // Воздух в схеме — требование, а не отсутствие требования:
            // здесь должно стать пусто, даже если сейчас гора.
            if (state.isAir()) {
                steps.add(BuildStep.clearing(block.pos()));
                continue;
            }

            steps.add(BuildStep.placing(block.pos(), categoryOf(state), block.paletteIndex()));
        }

        return new BuildPlan(size, steps, markers);
    }

    public static BuildCategory categoryOf(BlockState state) {
        return state.isIn(ModTags.BUILD_DECOR) ? BuildCategory.DECOR : BuildCategory.STRUCTURE;
    }

}
