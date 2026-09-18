package com.villagepax.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Печная труба: та самая примета обжитого дома.
 * <p>
 * Дым над крышей — это то, по чему деревня видна живой <b>издалека</b>,
 * с того расстояния, где ни жителей, ни их дел ещё не разглядеть. Ради
 * этого блок и заведён: очаг в доме стоял с самого начала, труба над ним
 * была кольцом камня, и ничего из неё не шло.
 * <p>
 * Дым рисует клиент и только у себя: сервер об этих частицах не знает,
 * и стоят они ровно ничего. Идёт он всегда, а не по расписанию: очаг
 * в доме горит круглые сутки, и гасить его к полудню было бы неправдой
 * и лишним состоянием в блоке.
 */
public class ChimneyBlock extends Block {

    /**
     * Раз в сколько тиков в среднем поднимается клуб дыма.
     * <p>
     * Редко нарочно: венцом трубы служит кольцо из восьми блоков вокруг
     * открытого устья — устье обязано остаться открытым, иначе дыму
     * некуда идти, и это стережёт отдельная проверка. Восемь блоков,
     * дымящих часто, дали бы над домом столб как из печи завода.
     */
    private static final int PUFF_CHANCE = 12;

    public ChimneyBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(PUFF_CHANCE) != 0) {
            return;
        }
        // С лёгким сносом: ровный столб выглядит трубой завода, а не печи.
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
    }
}
