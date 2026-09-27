package com.villagepax.block.festival;

import net.minecraft.block.Block;
import com.villagepax.sim.festival.ArcheryScore;
import com.villagepax.sim.festival.Matches;
import net.minecraft.block.BlockState;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/**
 * Мишень стрельбища на ярмарке: соломенный щит в кольцах.
 * <p>
 * Своя, а не ванильная, и не из прихоти. Ванильной мишени нужны красный
 * камень и сноп сена, а красного камня у колонии нет и взять его негде:
 * ярмарка стояла бы недостроенной, ожидая игрока с рудником. Эта
 * складывается из досок и палки.
 * <p>
 * Кольца нарисованы квадратными и ровно такими, какими их считает счёт
 * стрельбы: середина — четверть грани, среднее кольцо — до пяти восьмых.
 * Иначе стрелок видел бы попадание в середину, а получал очки за край.
 */
public class ArcheryTargetBlock extends Block {

    public ArcheryTargetBlock(Settings settings) {
        super(settings);
    }

    /** Попадание: очки по месту на грани, если на этой ярмарке идёт стрельба. */
    @Override
    public void onProjectileHit(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
        if (world instanceof ServerWorld server) {
            double[] at = ArcheryScore.onFace(hit.getPos(), hit.getSide(), hit.getBlockPos());
            Matches.hit(server, hit.getBlockPos(), projectile, ArcheryScore.rings(at[0], at[1]));
        }
    }
}
