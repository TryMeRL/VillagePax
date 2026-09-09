package com.villagepax.core.culture;

import com.villagepax.VillagePax;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Черты народа: разбор объявленных датапаком строк и их последствия.
 * <p>
 * Черта объявляется опознавателем — {@code villagepax:stone_masonry}, — а
 * работает кодом. Разбор поэтому обязан быть <b>громким</b>: описка
 * {@code stone_masonery} в json не должна молча означать «черты нет». Народ
 * без своей черты перестаёт быть собой, и искать причину игрок будет
 * в поведении жителей, а не в лишней букве.
 * <p>
 * Пространство имён у своей черты проверяется тоже: {@code someothermod:grazing}
 * — это чужая черта, и молча считать её своей нельзя.
 */
public final class Traits {

    /**
     * Сколько блоков билдер кладёт за одно решение по умолчанию.
     * <p>
     * Один, и это несущее решение темпа: стройка должна быть видна блок
     * за блоком, а не появляться стенами. Черта может ускорить, но
     * замедлять ниже одного нельзя — стоящий без дела работник читается
     * как зависание.
     */
    private static final int BLOCKS_PER_TURN = 1;

    /** И сколько кладёт мастер по камню на каменном шаге. */
    private static final int MASONRY_BLOCKS_PER_TURN = 2;

    private Traits() {
    }

    /**
     * Черты этой культуры.
     * <p>
     * Пусто, если культура не загружена: спрашивать черты у народа,
     * которого нет, — не ошибка вызывающего, а обычное дело при
     * перечитанном датапаке.
     */
    public static Set<Trait> of(Identifier culture) {
        Culture known = CultureManager.get(culture);
        return known == null ? Set.of() : resolve(known.traits());
    }

    public static boolean has(Identifier culture, Trait trait) {
        return of(culture).contains(trait);
    }

    /** Какие из объявленных строк мод знает. */
    public static Set<Trait> resolve(List<Identifier> declared) {
        Set<Trait> found = EnumSet.noneOf(Trait.class);
        for (Identifier id : declared) {
            byId(id).ifPresent(found::add);
        }
        return found;
    }

    /**
     * А какие — нет. Отдельным списком, чтобы о них можно было сказать
     * вслух, а не просто их не применить.
     */
    public static List<Identifier> unknown(List<Identifier> declared) {
        List<Identifier> strangers = new ArrayList<>();
        for (Identifier id : declared) {
            if (byId(id).isEmpty()) {
                strangers.add(id);
            }
        }
        return strangers;
    }

    /**
     * Сказать вслух о непонятых чертах. Зовётся при загрузке культур —
     * там, где автор датапака ещё смотрит в лог.
     */
    public static void audit(Identifier culture, List<Identifier> declared) {
        List<Identifier> strangers = unknown(declared);
        if (!strangers.isEmpty()) {
            VillagePax.LOGGER.warn("У культуры {} черты, которых мод не знает: {} — "
                            + "они не действуют. Известные: {}", culture,
                    strangers.stream().map(Identifier::toString).sorted().toList(),
                    java.util.Arrays.stream(Trait.values())
                            .map(trait -> VillagePax.MOD_ID + ":" + trait.id()).toList());
        }
    }

    /**
     * Сколько блоков билдер этого народа кладёт за решение, если следующий
     * шаг плана — вот этот блок.
     * <p>
     * Спрашивается о <b>следующем</b> шаге, а не о здании целиком: у
     * норманнского дома цоколь каменный, а стены деревянные, и черта должна
     * быть видна на цоколе, а не размазана по среднему.
     */
    public static int blocksPerTurn(Identifier culture, BlockState next) {
        if (next != null && isStonework(next) && has(culture, Trait.STONE_MASONRY)) {
            return MASONRY_BLOCKS_PER_TURN;
        }
        return BLOCKS_PER_TURN;
    }

    /**
     * Каменное ли это дело.
     * <p>
     * По тегу «берётся кайлом», а не по списку блоков: список пришлось бы
     * дописывать при каждом новом виде камня, включая камень из чужих
     * модов, — а тег ванильный и его пополняют за нас.
     */
    private static boolean isStonework(BlockState state) {
        return state.isIn(BlockTags.PICKAXE_MINEABLE);
    }

    private static java.util.Optional<Trait> byId(Identifier id) {
        if (!VillagePax.MOD_ID.equals(id.getNamespace())) {
            // Чужая черта — не наша, даже если имя совпало.
            return java.util.Optional.empty();
        }
        for (Trait trait : Trait.values()) {
            if (trait.id().equals(id.getPath())) {
                return java.util.Optional.of(trait);
            }
        }
        return java.util.Optional.empty();
    }
}
