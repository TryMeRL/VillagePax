package com.villagepax.block;

import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.VillageSites;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.List;

/**
 * Верстовой столб: где ближайшая деревня каждого народа и в какой стороне.
 * <p>
 * Команда {@code /villagepax locate} отвечает на тот же вопрос, но её
 * надо знать, а в одиночной игре без читов о ней и не догадаешься.
 * Столб ставят у дороги, и любой, кто по ней пройдёт, узнает, куда
 * идти, — без команды, без экрана и без карты. На развилке колонии
 * такой столб — первое, что делает мир обжитым.
 * <p>
 * Спрашивает тот же поиск по генератору, что и команда, — а он теперь
 * помнит ответы, поэтому щелчок ничего не стоит серверу.
 */
public class SignpostBlock extends FurnitureBlock {

    private static final String[] COMPASS = {"north", "northeast", "east", "southeast",
            "south", "southwest", "west", "northwest"};

    public SignpostBlock(VoxelShape shape, Settings settings) {
        super(shape, settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!(world instanceof ServerWorld server)) {
            return ActionResult.SUCCESS;
        }
        List<VillageSites.Guess> guesses = VillageSites.guessEach(server, pos);
        if (guesses.isEmpty()) {
            player.sendMessage(Text.translatable("villagepax.signpost.nothing"), false);
            return ActionResult.CONSUME;
        }
        player.sendMessage(Text.translatable("villagepax.signpost.head")
                .formatted(Formatting.GOLD), false);
        for (VillageSites.Guess guess : guesses) {
            int dx = guess.where().getX() - pos.getX();
            int dz = guess.where().getZ() - pos.getZ();
            int away = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            String people = CultureManager.get(guess.culture()) == null
                    ? guess.culture().toString()
                    : CultureManager.get(guess.culture()).displayName();
            player.sendMessage(Text.translatable("villagepax.signpost.line",
                    Text.translatable(people), away,
                    Text.translatable("villagepax.compass." + compass(dx, dz))), false);
        }
        world.playSound(null, pos, BlockSoundGroup.WOOD.getHitSound(), SoundCategory.BLOCKS,
                0.8f, 1.2f);
        return ActionResult.CONSUME;
    }

    /** Сторона света по смещению: север — к отрицательному z, как в игре. */
    static String compass(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int octant = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return COMPASS[octant];
    }
}
