package com.villagepax.block;

import com.villagepax.block.entity.RopeBlockEntity;
import com.villagepax.core.ModTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Бельевая верёвка: на неё вешают и с неё снимают.
 * <p>
 * Заказчик потребовал дважды. Сперва: «текстуру измени у белья, криво
 * как-то» — нарисованные рубахи висели наклейкой и у всех одинаково.
 * Потом: «добавь полезную функцию, пусть это будет просто верёвка, но
 * на которую можно будет вешать кожаные вещи, кожу и тканевую одежду».
 * <p>
 * Так и сделано. Модель — <b>только верёвка с прищепками</b>, а висит
 * на ней то, что повесили: кожа, шкура, шерсть, кожаная одежда — всё,
 * что лежит в теге {@code villagepax:hangable}. Тегом, а не списком
 * в коде: одежду из ткани мод добавит позже, и верёвка примет её, не
 * зная о ней ничего.
 * <p>
 * Четыре места и по вещи за раз. Верёвка — не сундук: складом она быть
 * не должна, иначе колония получит хранилище в обход склада.
 */
public class LaundryBlock extends Block implements BlockEntityProvider {

    /** Раз в сколько тиков в среднем срывается капля с мокрого. */
    private static final int DRIP_CHANCE = 6;

    /** След верёвки: сама бечева поверху и место под вещами. */
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(0, 14, 7, 16, 16, 9),
            createCuboidShape(1, 4, 7, 15, 14, 9));

    public LaundryBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RopeBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE;
    }

    /**
     * Щелчок: с вещью в руке — повесить, с пустой — снять последнее.
     * <p>
     * Без экрана и без слотов: верёвка на четыре вещи, и открывать ради
     * неё окно — всё равно что заводить меню у крючка. Тот же язык, что
     * у ванильной витрины, и его не надо объяснять.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof RopeBlockEntity rope)) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);
        if (!held.isEmpty() && held.isIn(ModTags.HANGABLE)) {
            if (world.isClient()) {
                return rope.freeSpot() >= 0 ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            if (!rope.hang(held.copy())) {
                return ActionResult.PASS;
            }
            if (!player.isCreative()) {
                held.decrement(1);
            }
            world.playSound(null, pos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS,
                    0.8f, 1.1f);
            return ActionResult.CONSUME;
        }

        if (!held.isEmpty()) {
            // В руке что-то, чего не вешают: пусть щёлкнет по верёвке
            // и поставит это рядом, а не удивляется отказу.
            return ActionResult.PASS;
        }

        if (world.isClient()) {
            return rope.isEmpty() ? ActionResult.PASS : ActionResult.SUCCESS;
        }
        ItemStack taken = rope.takeDown();
        if (taken.isEmpty()) {
            return ActionResult.PASS;
        }
        if (!player.giveItemStack(taken)) {
            player.dropItem(taken, false);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.BLOCKS, 0.8f, 1.0f);
        return ActionResult.CONSUME;
    }

    /**
     * Сломали верёвку — повешенное падает наземь.
     * <p>
     * Иначе игрок теряет вещи молча, а это худший способ узнать
     * об устройстве блока.
     */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState,
                                boolean moved) {
        if (!state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof RopeBlockEntity rope) {
            for (ItemStack stack : rope.hung()) {
                if (!stack.isEmpty()) {
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /**
     * Капли с мокрого.
     * <p>
     * Только когда на верёвке что-то висит: пустая бечева не капает,
     * и это ровно та мелочь, по которой видно, что блок живой.
     */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(DRIP_CHANCE) != 0
                || !(world.getBlockEntity(pos) instanceof RopeBlockEntity rope)
                || rope.isEmpty()) {
            return;
        }
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 0.25 + random.nextDouble() * 0.2;
        double z = pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.FALLING_WATER, x, y, z, 0.0, 0.0, 0.0);
    }
}
