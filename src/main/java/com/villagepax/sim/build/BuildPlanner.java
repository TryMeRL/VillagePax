package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import com.villagepax.core.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                                 List<Schematic.PalettedBlock> blocks) {
        List<BuildStep> steps = new ArrayList<>(blocks.size());
        List<PointOfInterest> pois = new ArrayList<>();

        for (Schematic.PalettedBlock block : blocks) {
            BlockState state = palette.get(block.paletteIndex());

            // Пустота структуры — это «не трогать»: так автор схемы оставляет
            // рельеф на месте. Шага не будет вовсе, иначе билдер выбил бы
            // склон, который его специально попросили не касаться.
            if (state.isOf(Blocks.STRUCTURE_VOID)) {
                continue;
            }

            Optional<MarkerKind> marker = markerKind(state);
            if (marker.isPresent()) {
                // Маркер — служебный блок: билдер оставляет на его месте воздух
                // и запоминает точку. Так одна схема описывает и геометрию
                // здания, и его логику.
                pois.add(new PointOfInterest(marker.get(), block.pos()));
                steps.add(BuildStep.clearing(block.pos()));
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

        return new BuildPlan(size, steps, pois);
    }

    public static BuildCategory categoryOf(BlockState state) {
        return state.isIn(ModTags.BUILD_DECOR) ? BuildCategory.DECOR : BuildCategory.STRUCTURE;
    }

    /**
     * Род маркера по блоку. Сопоставление идёт через путь в реестре, чтобы
     * таблица «блок → род» не дублировала {@link MarkerKind}: там она чистая
     * и проверена без игры.
     */
    public static Optional<MarkerKind> markerKind(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return VillagePax.MOD_ID.equals(id.getNamespace())
                ? MarkerKind.byBlockPath(id.getPath())
                : Optional.empty();
    }
}
