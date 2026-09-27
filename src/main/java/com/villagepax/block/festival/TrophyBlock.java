package com.villagepax.block.festival;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Кубок первого места — блок-память.
 * <p>
 * Его не купить и не скрафтить: кубок даёт только праздник, и только тому,
 * кто победил. Поэтому у него надпись — что, где, когда и кто. Кубок без
 * надписи был бы просто золотой вещью; с надписью он — история, которую
 * игрок ставит у себя в доме.
 * <p>
 * Надпись переживает всё: её держит блок-сущность, в предмет её переносит
 * таблица добычи, из предмета обратно — ванильная передача
 * {@code BlockEntityTag}. Щелчок по стоящему кубку читает надпись вслух.
 */
public class TrophyBlock extends Block implements BlockEntityProvider {

    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 13, 12);

    public TrophyBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrophyBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof TrophyBlockEntity trophy) {
            for (Text line : TrophyBlockEntity.lines(trophy.engraving())) {
                player.sendMessage(line, false);
            }
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS,
                    0.8f, 1.2f);
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip,
                              TooltipContext options) {
        NbtCompound entity = BlockItem.getBlockEntityNbt(stack);
        NbtCompound engraving = entity == null ? new NbtCompound()
                : entity.getCompound(TrophyBlockEntity.ENGRAVING);
        tooltip.addAll(TrophyBlockEntity.lines(engraving));
    }
}
