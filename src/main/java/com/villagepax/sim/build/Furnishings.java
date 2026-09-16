package com.villagepax.sim.build;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.entity.RopeBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Что строитель кладёт <b>внутрь</b> того, что поставил.
 * <p>
 * Блок из схемы встаёт пустым, и для сундука это верно, а для бельевой
 * верёвки — нет: пустая верёвка посреди двора выглядит недоделкой.
 * Раньше бельё было нарисовано в самой текстуре и висело у всех
 * одинаково; теперь на верёвке висит то, что повесили, — а повесить
 * во дворе деревни должен тот, кто её натянул.
 * <p>
 * Отдельным классом, а не строчкой в укладке блоков: обстановки будет
 * больше (в стойку — инструмент, на полку — снедь), и место для неё
 * нужно одно и названное.
 */
public final class Furnishings {

    /**
     * Что вешают во дворе.
     * <p>
     * Ванильные вещи, а не свои: тканевая одежда в моде появится позже,
     * и до тех пор двор обходится тем, что у деревни и так есть, —
     * холстиной да кожей.
     */
    private static final List<Item> WASHING = List.of(
            Items.WHITE_WOOL, Items.LIGHT_BLUE_WOOL, Items.LEATHER, Items.WHITE_CARPET);

    private Furnishings() {
    }

    /**
     * Обставить только что поставленный блок.
     * <p>
     * Зовётся из укладки и ничего не делает для всего остального —
     * одна проверка типа блока на шаг стройки.
     */
    public static void stock(ServerWorld world, BlockPos where, BlockState laid, long seed) {
        if (!laid.isOf(ModBlocks.LAUNDRY)) {
            return;
        }
        if (!(world.getBlockEntity(where) instanceof RopeBlockEntity rope) || !rope.isEmpty()) {
            return;
        }

        // Две вещи из четырёх, выбранные по месту: соседние верёвки в одной
        // деревне не должны выглядеть близнецами, а случай здесь запрещён —
        // мир обязан восстанавливаться одинаково.
        int first = (int) Math.floorMod(seed, WASHING.size());
        int second = (int) Math.floorMod(seed / 3 + 1, WASHING.size());
        rope.hang(new ItemStack(WASHING.get(first)));
        rope.hang(new ItemStack(WASHING.get(second)));
    }
}
