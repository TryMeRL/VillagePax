package com.villagepax.block.festival;

import com.villagepax.core.festival.TokenKind;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

/**
 * Вещица поиска: крашеное яйцо, омамори, руна, подкова…
 * <p>
 * Живёт минуту — столько идёт поиск, — и в руки не даётся: щелчок
 * засчитывает находку и убирает вещицу. Вещица, которую можно унести,
 * была бы валютой, и за ней ходили бы не на праздник, а в чужую деревню
 * с киркой.
 * <p>
 * Без идущего поиска щелчок просто убирает её: это остаток, праздник
 * за собой не прибрал, и лучше дать игроку убрать его рукой, чем оставить
 * вечное яйцо в траве.
 */
public class FestivalTokenBlock extends Block {

    public static final EnumProperty<TokenKind> KIND = EnumProperty.of("kind", TokenKind.class);

    private static final Map<TokenKind, VoxelShape> SHAPES = new EnumMap<>(TokenKind.class);

    static {
        SHAPES.put(TokenKind.EGG, Block.createCuboidShape(5, 0, 5, 11, 8, 11));
        SHAPES.put(TokenKind.JADE, Block.createCuboidShape(5, 0, 5, 11, 8, 11));
        SHAPES.put(TokenKind.OMAMORI, Block.createCuboidShape(5, 0, 6, 11, 9, 10));
        SHAPES.put(TokenKind.RUNE, Block.createCuboidShape(4, 0, 4, 12, 3, 12));
        SHAPES.put(TokenKind.HORSESHOE, Block.createCuboidShape(3, 0, 3, 13, 2, 13));
        SHAPES.put(TokenKind.GEM, Block.createCuboidShape(4, 0, 4, 12, 9, 12));
        SHAPES.put(TokenKind.FIREFLY, Block.createCuboidShape(5, 3, 5, 11, 9, 11));
    }

    public FestivalTokenBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(KIND, TokenKind.EGG));
    }

    /** Сколько света даёт вещица этого облика: светлячок и самоцвет видны и в сумерках. */
    public static int glow(BlockState state) {
        return switch (state.get(KIND)) {
            case FIREFLY -> 10;
            case GEM -> 6;
            default -> 0;
        };
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPES.get(state.get(KIND));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld server) {
            pickUp(server, pos);
        }
        return ActionResult.success(world.isClient());
    }

    /** Убрать вещицу с блеском и звоном: так видно, что находку засчитали. */
    public static void pickUp(ServerWorld world, BlockPos pos) {
        world.removeBlock(pos, false);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4,
                pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0);
        world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS,
                0.6f, 1.4f);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(KIND) == TokenKind.FIREFLY && random.nextInt(2) == 0) {
            world.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.5 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.005, 0);
        }
    }
}
