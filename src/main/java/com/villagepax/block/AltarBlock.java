package com.villagepax.block;

import com.villagepax.sim.faith.Offering;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Алтарь: единственное место в моде, где игрок разговаривает с небом.
 * <p>
 * Вещь кладётся <b>из рук</b>, а не берётся со склада, и это решение по
 * смыслу. Возьми мод жертву со склада — жертвовала бы колония, а игрок
 * только нажимал бы кнопку; вера же в этом моде — то немногое, что делает
 * он сам. За всё остальное (стройку, поле, обоз) у него есть работники.
 * <p>
 * <b>Бога выбирает вещь, а не кнопка.</b> Положил зерно — молишься
 * тому, кто отвечает за урожай; положил камень — тому, кто держит стену.
 * Это избавляет и от списка богов на экране, и от вопроса «а кому именно»:
 * пантеон узнаётся руками, а не чтением. Поэтому же два бога одного народа
 * не вправе принимать одну и ту же вещь, и за этим следит загрузчик.
 * <p>
 * Пустая рука — не ошибка, а вопрос: алтарь рассказывает, кто здесь
 * слушает и что берёт. Молчащий блок неотличим от сломанного, и это
 * правило мод уже оплатил однажды — чужой ратушей, которая открывала
 * пульт, где ни одна кнопка не работала.
 */
public class AltarBlock extends Block {

    /**
     * Цоколь, тумба и стол с чашей — по той же модели, что рисуется.
     * <p>
     * Свечи и рога в очертания не входят: за них не должен цепляться
     * взгляд, когда игрок целится в сам алтарь.
     */
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 2, 15),
            createCuboidShape(2.5, 2, 2.5, 13.5, 11, 13.5),
            createCuboidShape(0.5, 11, 0.5, 15.5, 14.5, 15.5),
            createCuboidShape(5, 14.5, 5, 11, 15.75, 11));

    /**
     * Где горят фитили — те же места, что у свечей в модели
     * ({@code tools/make-furniture.py}, {@code CANDLES}), в долях блока.
     */
    private static final double[][] WICKS = {{2 / 16.0, 2 / 16.0}, {14 / 16.0, 14 / 16.0}};

    /** Высота огонька над полом клетки: верх фитиля. */
    private static final double FLAME = 18.6 / 16.0;

    public AltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE;
    }

    /**
     * Огоньки свечей и редкая искра над углями.
     * <p>
     * Свечи на алтаре ванильные, и огонёк у них ванильный же — частица,
     * а не картинка: нарисованное пламя не мерцало бы. Рисует клиент
     * и только у себя, сервер об этом не знает ничего.
     */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        for (double[] wick : WICKS) {
            if (random.nextInt(3) == 0) {
                world.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + wick[0],
                        pos.getY() + FLAME, pos.getZ() + wick[1], 0.0, 0.0, 0.0);
            }
        }
        if (random.nextInt(8) == 0) {
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0,
                    pos.getZ() + 0.5, 0.0, 0.02, 0.0);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        // Решает сервер: и жертва, и рассказ о пантеоне спрашивают
        // поселение, а поселения на клиенте нет.
        if (world.isClient || !(player instanceof ServerPlayerEntity server)) {
            return ActionResult.SUCCESS;
        }

        ItemStack held = player.getStackInHand(hand);
        Offering.atAltar((ServerWorld) world, server, pos, held);
        return ActionResult.CONSUME;
    }
}
