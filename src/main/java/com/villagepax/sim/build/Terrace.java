package com.villagepax.sim.build;

import com.villagepax.sim.Building;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * Площадка: земля под здание ровняется <b>до</b> того, как встанет стена.
 * <p>
 * Жалоба заказчика, и не первая по этому поводу: «пусть строитель строит
 * так, чтобы под ней не было пустот, — пусть берёт точку, ровняет все
 * блоки под неё и только тогда строит».
 *
 * <h2>Что было и почему не работало</h2>
 * Опора под дом подводилась <b>после</b> стройки и <b>из излишков</b>:
 * «не хватило — дом просто стоит на сваях, а пустоту доложит следующий
 * завоз». Рассуждение было про экономию камня и звучало разумно, но
 * в игре давало ровно то, на что жалуются: дом на сваях, под ним дыра,
 * и следующего завоза может не быть никогда.
 * <p>
 * Это та же ошибка, которую мод уже признал у крыльца: «вход, в который
 * не войти, — это не экономия, а поломка, и откладывать её до следующего
 * завоза нельзя». Дыра под домом — такая же поломка, и лечится тем же
 * решением: <b>даром и сразу</b>.
 *
 * <h2>Почему даром</h2>
 * Потому что это не стройка, а земляные работы. Билдер не тратит камень
 * со склада — он <b>переносит землю</b>: срывает бугор и подсыпает яму
 * тем, что под ней лежит. Брать за это материалы значило бы, что колония
 * покупает собственный грунт.
 * <p>
 * И подсыпает он <b>тем, что нашёл в этой же колонне</b>: под лугом
 * окажется земля, под пляжем песок, под скалой камень. Один и тот же
 * булыжник везде выдавал бы серую подошву посреди песков.
 *
 * <h2>Один раз, в начале</h2>
 * Зовётся в тот миг, когда билдер впервые берётся за здание, а не каждым
 * шагом: рельеф под следом меняется ровно однажды. Ремонт проходит план
 * заново и площадку тоже — за годы под домом успевает появиться и яма
 * от крипера.
 */
public final class Terrace {

    /**
     * Насколько глубоко досыпать.
     * <p>
     * Двенадцать блоков — это склон, на котором дом ещё уместен. Глубже
     * начинается обрыв, и подсыпать его столбом земли значило бы строить
     * башню, о которой игрок не просил: лучше оставить дыру видимой,
     * чем закопать пол-оврага.
     */
    public static final int DEEP = 12;

    /**
     * Сколько блоков земли билдер трогает за одну стройку.
     * <p>
     * След 9x9 на склоне в двенадцать блоков — это тысяча клеток, и все
     * они ставятся в один тик. Предел не про баланс, а про то, чтобы
     * сервер не встал: стройка, из-за которой игра замирает на секунду,
     * читается как зависание.
     */
    private static final int BUDGET = 1200;

    private Terrace() {
    }

    /**
     * Сровнять площадку под зданием.
     *
     * @return сколько блоков земли перенесено
     */
    public static int level(ServerWorld world, Building building, Schematic schematic) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        if (!restsOnGround(world, anchor, size)) {
            return 0;
        }

        int moved = 0;
        for (int dx = 0; dx < size.getX() && moved < BUDGET; dx++) {
            for (int dz = 0; dz < size.getZ() && moved < BUDGET; dz++) {
                moved += fillUnder(world, anchor.add(dx, 0, dz), BUDGET - moved);
            }
        }
        return moved;
    }

    /**
     * Опирается ли здание на землю хотя бы одним углом.
     * <p>
     * <b>Площадка равняет склон, а не строит сваи.</b> Дом, под которым
     * земли нет вовсе — поставленный нарочно на весу, на скале, над
     * пропастью или на помосте, — засыпать нельзя: вместо ровной подошвы
     * вышел бы земляной столб в дюжину блоков, о котором игрок не просил.
     * <p>
     * Различаются эти два случая одним вопросом: <b>касается ли подошва
     * земли где-нибудь</b>. На склоне касается — верхним углом; у дома
     * на весу не касается нигде, и тогда равнять нечего.
     */
    static boolean restsOnGround(ServerWorld world, BlockPos anchor, Vec3i size) {
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                BlockPos under = anchor.add(dx, -1, dz);
                if (!isHollow(world, under)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Досыпать одну колонну: от пола вниз, пока не встретится твёрдое.
     * <p>
     * Вода и лава считаются пустотой и вытесняются: дом, стоящий в озере
     * на сваях, ничем не лучше дома над ямой, а залитый подпол — хуже
     * обоих.
     */
    private static int fillUnder(ServerWorld world, BlockPos floor, int budget) {
        BlockState earth = null;
        int placed = 0;

        for (int depth = 1; depth <= DEEP && placed < budget; depth++) {
            BlockPos under = floor.down(depth);
            if (under.getY() <= world.getBottomY()) {
                break;
            }
            if (!isHollow(world, under)) {
                break;
            }
            if (earth == null) {
                earth = earthUnder(world, under);
            }
            world.setBlockState(under, earth, Block.NOTIFY_ALL);
            placed++;
        }
        return placed;
    }

    /**
     * Пустота ли это — то есть можно ли сюда сыпать.
     * <p>
     * Список <b>белый</b>, а не чёрный, и это исправление по следам
     * настоящей поломки: первая версия сыпала всюду, где нет твёрдого
     * блока, и закапывала сундуки. Сундук твёрдым не считается, лестница
     * тоже, чужая стена из ступеней — тоже; засыпать их землёй значило бы,
     * что стройка одного дома молча съедает соседний и весь склад
     * колонии в придачу.
     * <p>
     * Сыпем в воздух, в воду с лавой и в то, что ваниль и так сносит
     * при постановке блока: траву, снег, цветы. Всё остальное — чужое,
     * и там площадка кончается.
     */
    private static boolean isHollow(ServerWorld world, BlockPos at) {
        BlockState state = world.getBlockState(at);
        if (state.hasBlockEntity()) {
            return false;
        }
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return true;
        }
        return state.isReplaceable();
    }

    /**
     * Годится ли это в опору.
     * <p>
     * Листва не годится: она твёрдой не считается и так, но бревно
     * считается — а дом, стоящий на верхушке дуба, это та же свая,
     * только зелёная. Тот же урок, что и у поиска земли.
     */
    private static boolean isFooting(ServerWorld world, BlockPos at, BlockState state) {
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return false;
        }
        return state.isSolidBlock(world, at) && state.getFluidState().isEmpty();
    }

    /**
     * Чем засыпать: тем, что лежит в этой колонне ниже.
     * <p>
     * Ищется первая твёрдая земля под ямой — она и задаёт материал.
     * Не нашлось (обрыв до самого низа) — земля: она есть везде
     * и ни с каким биомом не спорит.
     */
    private static BlockState earthUnder(ServerWorld world, BlockPos from) {
        for (int depth = 0; depth <= DEEP; depth++) {
            BlockPos at = from.down(depth);
            if (at.getY() <= world.getBottomY()) {
                break;
            }
            BlockState state = world.getBlockState(at);
            if (isFooting(world, at, state) && !state.hasBlockEntity()) {
                // Только грунт: под ямой над пещерой первой твёрдой может
                // оказаться руда или аметист, и насыпь из двенадцати алмазных
                // блоков игрок выкапывал бы даром. Не грунт — земля.
                if (!Grading.isEarth(state)) {
                    return Blocks.DIRT.getDefaultState();
                }
                // Дёрн класть нельзя: под домом он потемнеет и станет
                // землёй сам, а по дороге посеет траву в подполе.
                return state.isOf(Blocks.GRASS_BLOCK) ? Blocks.DIRT.getDefaultState() : state;
            }
        }
        return Blocks.DIRT.getDefaultState();
    }
}
