package com.villagepax.block.festival;

import com.villagepax.block.FurnitureBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Сердца праздника, которые не столбы: барабан ямато, йольский костёр
 * северян, горн гномов и светящееся деревце эльфов.
 * <p>
 * У каждого народа сердце своё, и по нему ярмарку узнают издалека, как
 * деревню — по кровле. Дело у всех одно — стоять в середине хоровода, —
 * а живут они по-разному: барабан бьёт, костёр дымит, горн тлеет,
 * деревце роняет светлячков.
 */
public final class FestivalHearts {

    private FestivalHearts() {
    }

    /** Барабан тайко на помосте ягуры: щелчок — удар, как на бон-одори. */
    public static class Taiko extends FurnitureBlock {

        public Taiko(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            if (!world.isClient()) {
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(),
                        SoundCategory.RECORDS, 2.0f, 0.6f);
                ((net.minecraft.server.world.ServerWorld) world).spawnParticles(ParticleTypes.NOTE,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
            return ActionResult.success(world.isClient());
        }
    }

    /**
     * Йольский костёр: сруб из поленьев с пламенем и дымом.
     * <p>
     * Не жжётся нарочно: вокруг него пляшут, и настоящий огонь в середине
     * хоровода сжёг бы первого же, кого толкнули. Ванильный поиск пути
     * к тому же считает горящий костёр опасным и обходил бы его — хоровод
     * распался бы на тех, кто боится подойти.
     */
    public static class YuleFire extends Block {

        private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 7, 16);

        public YuleFire(Settings settings) {
            super(settings);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                          ShapeContext context) {
            return SHAPE;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (random.nextInt(3) == 0) {
                world.addImportantParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        pos.getX() + 0.5 + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                        pos.getY() + 0.8, pos.getZ() + 0.5
                                + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                        0.0, 0.07, 0.0);
            }
            if (random.nextInt(8) == 0) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS,
                        0.6f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f, false);
            }
        }
    }

    /** Праздничный горн гномов: тлеющие угли и искры над ними. */
    public static class Forge extends Block {

        private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 11, 15);

        public Forge(Settings settings) {
            super(settings);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                          ShapeContext context) {
            return SHAPE;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            world.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.75, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
            if (random.nextInt(4) == 0) {
                world.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.75,
                        pos.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    /** Светящееся деревце эльфов: в кроне живут светлячки. */
    public static class GlowTree extends Block {

        private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 16, 13);

        public GlowTree(Settings settings) {
            super(settings);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                          ShapeContext context) {
            return SHAPE;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (random.nextInt(2) == 0) {
                world.addParticle(ParticleTypes.GLOW, pos.getX() + random.nextDouble(),
                        pos.getY() + 0.6 + random.nextDouble() * 0.6, pos.getZ() + random.nextDouble(),
                        0, 0.01, 0);
            }
        }
    }
}
