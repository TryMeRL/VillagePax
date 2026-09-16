package com.villagepax.sim;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;

/**
 * Звуки работы колонии.
 * <p>
 * Просьба заказчика, и она из того же ряда, что «блок в руке у билдера»:
 * <b>работа должна быть слышна</b>. Мод менял мир беззвучно — стены
 * вырастали, деревья исчезали, грядки перекапывались, и всё это в полной
 * тишине. Игрок не понимал, идёт ли дело, пока не подходил и не смотрел.
 * <p>
 * Свои звуки не заводятся ни одного: житель кладёт блок тем же звуком, каким
 * его кладёт игрок, и рубит тем же, каким рубит игрок. Так у работы не
 * появляется собственного языка, которому игрока пришлось бы учить, — она
 * звучит знакомо с первой секунды. Причина и техническая: мир меняется
 * через {@code setBlockState}, а он, в отличие от установки блока игроком,
 * не издаёт ни звука и не сыплет осколков.
 */
public final class Sounds {

    /**
     * Колокол слышно дальше обычного: сдача здания — событие всей колонии,
     * а не того угла, где стоит билдер. Громкость в ванили и есть радиус:
     * двойка тянет примерно на тридцать блоков.
     */
    private static final float BELL_CARRY = 2.0f;

    private Sounds() {
    }

    /**
     * Блок поставлен. Громкость и высота считаются по ванильной формуле
     * установки блока — той самой, что в {@code BlockItem}.
     */
    public static void placed(World world, BlockPos pos, BlockState state) {
        BlockSoundGroup group = state.getSoundGroup();
        world.playSound(null, pos, group.getPlaceSound(), SoundCategory.BLOCKS,
                (group.getVolume() + 1.0f) / 2.0f, group.getPitch() * 0.8f);
    }

    /**
     * Блок снесён — со звуком и осколками.
     * <p>
     * Событием мира, а не звуком: то же событие рассылает клиентам и звук
     * поломки, и разлетающиеся частицы блока. Одним вызовом получается
     * ровно то, что игрок видит и слышит, когда ломает блок сам.
     */
    public static void broke(WorldAccess world, BlockPos pos, BlockState state) {
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
    }

    /** Мотыга по земле: фермер перекапывает вытоптанную грядку. */
    public static void tilled(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** Посев. Тот же звук, что у игрока, сажающего семена. */
    public static void sown(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * Котёл: ремесленник довёл дело до конца.
     * <p>
     * Звук у работы обязателен: без него игрок узнаёт о ремесле только
     * по числу на складе, а работа должна быть слышна и видна — это
     * несущее правило мода, а не украшение.
     */
    public static void crafted(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW,
                SoundCategory.BLOCKS, 0.8f, 1.0f);
    }

    /**
     * Колокол: в колонии сдали здание.
     * <p>
     * Звонит в середине поселения, а не на стройке: это объявление, и
     * услышать его игрок должен, даже если смотрел в другую сторону.
     * Норманнам колокол к тому же по чину — он стоит в зале их ратуши.
     */
    public static void buildingDone(World world, BlockPos centre) {
        world.playSound(null, centre, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS,
                BELL_CARRY, 1.0f);
    }
}
