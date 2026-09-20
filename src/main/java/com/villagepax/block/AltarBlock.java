package com.villagepax.block;

import com.villagepax.sim.faith.Offering;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
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

    /** Тумба с плитой поверху: у жертвы должно быть куда лечь. */
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(2, 0, 2, 14, 12, 14),
            createCuboidShape(0, 12, 0, 16, 16, 16));

    public AltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE;
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
