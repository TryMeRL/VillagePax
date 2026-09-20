package com.villagepax.sim.faith;

import com.villagepax.block.ModBlocks;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Где в мире стоят алтари и какое здание считается храмом.
 * <p>
 * Храм узнаётся <b>по алтарю в схеме</b>, а не по имени типа и не по
 * отдельному полю в данных. Это то же правило, по которому ратуша
 * узнаётся по роли: мод обязан узнавать здание по тому, чем оно является,
 * а не по тому, как автор датапака его назвал. Народ вправе звать своё
 * святилище часовней, пирамидой или рощей — если внутри алтарь, туда
 * приходят молиться.
 * <p>
 * Отдельной роли {@code temple} нет намеренно. Роль решает <b>права</b>
 * здания в колонии (ратуша даёт ступень, жильё — кровати, мастерская —
 * работу), а храм никаких особых прав не просит: он просто здание, внутри
 * которого стоит блок. Заводить ради него роль значило бы утверждать, что
 * алтарь бывает только в храме, — а он может стоять и в ратуше, и в доме
 * старейшины, если так решит датапак.
 */
public final class Altars {

    private Altars() {
    }

    /** Есть ли в этой схеме алтарь. */
    public static boolean has(Identifier schematic) {
        return SchematicLoader.get(schematic).map(Altars::has).orElse(false);
    }

    public static boolean has(Schematic schematic) {
        return schematic.blocks().stream()
                .anyMatch(block -> schematic.blockAt(block.paletteIndex())
                        .isOf(ModBlocks.ALTAR));
    }

    /**
     * Есть ли алтарь в схеме первого уровня этого типа здания.
     * <p>
     * Первого, а не любого: храм остаётся храмом с того уровня, с которого
     * его можно построить. Здание, обзаводящееся алтарём только на третьем
     * уровне, — это уже другое обещание, и давать его данным молча нельзя.
     */
    public static boolean typeHasAltar(Identifier type) {
        return has(new Identifier(type.getNamespace(), type.getPath() + "_lvl1"));
    }

    /**
     * Где в мире стоит алтарь этого здания.
     * <p>
     * По схеме, а не поиском блока вокруг: схема знает место точно, а поиск
     * нашёл бы и чужой алтарь соседнего храма, стоящего в двух шагах.
     */
    public static Optional<BlockPos> in(Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            if (schematic.blockAt(step.paletteIndex()).isOf(ModBlocks.ALTAR)) {
                return Optional.of(BuildJob.worldPos(building, schematic.size(), step.pos()));
            }
        }
        return Optional.empty();
    }

    /**
     * Какому зданию поселения принадлежит алтарь на этом месте.
     * <p>
     * Сравнением по месту, а не по расстоянию: алтарь — блок схемы, и
     * «примерно там» тут не бывает. Заодно это отвечает на вопрос, чей
     * алтарь трогает игрок, когда две деревни стоят рядом.
     */
    public static Optional<Building> ownerOf(Settlement settlement, BlockPos altar) {
        for (Building building : settlement.buildings()) {
            if (in(building).filter(altar::equals).isPresent()) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }
}
