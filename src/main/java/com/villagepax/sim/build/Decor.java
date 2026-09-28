package com.villagepax.sim.build;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Sounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Слоты декора: одна схема даёт разные дома.
 * <p>
 * Маркер {@code marker_decor} задумывался с самого начала, но до сих дел не
 * делал — просто становился воздухом, и все дома колонии выходили близнецами.
 * Теперь на его место встаёт блок из списка культуры: у одного дома поленница
 * у входа, у другого бельё на верёвке, у третьего мешок снеди.
 * <p>
 * <b>Выбор устойчив.</b> Он считается из опознавателя здания и места слота,
 * а не случайно: одно и то же здание всегда получает один и тот же декор, а
 * разные — разный. Это не мелочь. Случайность из генератора пережила бы
 * постройку, но не {@code /reload} и не ремонт: дом менял бы облик всякий раз,
 * когда билдер подлатает стену, и игрок видел бы мод, который сам себя
 * переделывает. Хранить выбор в данных поселения тоже нельзя — это ещё одно
 * состояние, которое умеет разойтись с миром.
 * <p>
 * Декор ставится <b>бесплатно</b>, и это осознанно. Материал за него сделал бы
 * слот почти всегда пустым — курьер декор не носит, потому что заявка на
 * материалы считается по плану, а декор в плане только место. Пустой слот
 * означал бы, что вся затея не видна, а видно должно быть. Считать это
 * позволительно так: расчистка площадки сдаёт колонии брёвна и землю, и один
 * блок обстановки из этого — честная сдача.
 */
public final class Decor {

    private Decor() {
    }

    /**
     * Заполнить слоты декора сданного здания.
     * <p>
     * Зовётся в тот миг, когда стройка объявлена готовой, — в том числе после
     * ремонта, потому что ремонт проходит план заново и кончается тем же
     * местом. Слот, в котором игрок уже что-то поставил, не трогается: то же
     * правило, по которому фермер не считает своими чужие посадки.
     */
    public static void fill(ServerWorld world, Settlement colony, Building building,
                            Schematic schematic) {
        List<Block> table = tableFor(colony);
        if (table.isEmpty()) {
            return;
        }

        for (BlockPos slot : BuildJob.pointsOfInterest(building, schematic, MarkerKind.DECOR)) {
            if (!world.getBlockState(slot).isAir()) {
                continue;
            }

            BlockState chosen = table.get(pick(building, slot, table.size())).getDefaultState();
            if (!chosen.canPlaceAt(world, slot)) {
                // Цветочному горшку нужна опора, белью — нет. Схема слот
                // задаёт, а годится ли он этому блоку, решает сам блок.
                continue;
            }

            // Тот же порядок, что у стройки: сперва отойти из клетки. Тюк
            // сена, поставленный на курицу, её замуровывал.
            if (!BuildJob.makeRoomFor(world, building, schematic, slot, chosen)) {
                continue;
            }
            world.setBlockState(slot, chosen, Block.NOTIFY_ALL);
            Sounds.placed(world, slot, chosen);
        }
    }

    /**
     * Чем этот народ обставляет свои дома. Данными, как и всё остальное:
     * новая культура добавляется файлом датапака.
     */
    private static List<Block> tableFor(Settlement colony) {
        Culture culture = CultureManager.get(colony.culture());
        if (culture == null) {
            return List.of();
        }

        List<Block> table = new ArrayList<>();
        for (Identifier id : culture.decor()) {
            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR) {
                table.add(block);
            }
        }
        return table;
    }

    /**
     * Какой блок достанется этому слоту.
     * <p>
     * Смешиваются опознаватель здания и место слота, поэтому два слота одного
     * дома получают разное, а один и тот же слот — всегда одно и то же.
     */
    private static int pick(Building building, BlockPos slot, int size) {
        int mixed = building.id().hashCode() * 31 + slot.hashCode();
        return Math.floorMod(mixed, size);
    }
}
