package com.villagepax.sim;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenSpawner;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Основание колонии как операция над миром.
 * <p>
 * Отделено от предмета-чертежа намеренно: предмет остаётся тонкой оболочкой,
 * которая только показывает сообщения и тратит стак, а сама операция
 * вызывается ещё и из игровых тестов, где никакого игрока нет.
 */
public final class ColonyFounder {

    public static final String KEY_BAD_GROUND = "villagepax.found.bad_ground";

    private ColonyFounder() {
    }

    public static FoundingOutcome foundAt(ServerWorld world, UUID player, Identifier cultureId, BlockPos target) {
        if (!isBuildable(world, target)) {
            return FoundingOutcome.Refused.of(KEY_BAD_GROUND);
        }

        Culture culture = CultureManager.get(cultureId);
        SettlementManager manager = SettlementManager.get(world);

        FoundingOutcome outcome = Founding.attempt(
                manager, player, cultureId, culture, target, new Random(world.getRandom().nextLong()));

        if (outcome instanceof FoundingOutcome.Founded founded) {
            raiseTownHall(world, target, founded.settlement(), culture, cultureId);
            settleFirstBuilder(world, founded.settlement(), culture, cultureId);
            manager.add(founded.settlement());
        }
        return outcome;
    }

    /** Ратуше нужна твёрдая опора и свободное место — иначе колония повиснет в воздухе. */
    public static boolean isBuildable(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isReplaceable()) {
            return false;
        }
        BlockPos below = pos.down();
        return world.getBlockState(below).isSolidBlock(world, below);
    }

    /**
     * Строитель появляется у ратуши сразу и с телом: чанк основания заведомо
     * загружен, а ждать следующей загрузки чанка значило бы, что игрок
     * основал колонию и никого не увидел.
     */
    private static void settleFirstBuilder(ServerWorld world, Settlement settlement,
                                           Culture culture, Identifier cultureId) {
        Citizen builder = Founding.firstBuilder(cultureId, culture, new Random(world.getRandom().nextLong()));
        builder.setPosition(Vec3d.ofBottomCenter(settlement.center().up()));
        settlement.addCitizen(builder);

        CitizenSpawner.spawnBody(world, settlement, builder);
    }

    /**
     * Ратуша как блок и как здание. Открыто наружу, потому что тем же
     * порядком начинается и деревня народа: разница между колонией и
     * деревней — только во владельце.
     */
    public static void raiseTownHall(ServerWorld world, BlockPos pos, Settlement settlement,
                                     Culture culture, Identifier cultureId) {
        world.setBlockState(pos, ModBlocks.TOWN_HALL.getDefaultState());

        if (world.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            townHall.setSettlementId(settlement.id());
        }

        Identifier buildingType = culture.townHallBuilding()
                .orElseGet(() -> new Identifier(cultureId.getNamespace(), cultureId.getPath() + "/town_hall"));

        settlement.addBuilding(new Building(
                UUID.randomUUID(), buildingType, 1, pos, BlockRotation.NONE, BuildProgress.DONE, List.of()));
    }
}
