package com.villagepax.sim;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Путевые записки — то, что игрок читает вместо вики.
 * <p>
 * Решение принято по чужому опыту, а не по вкусу: у модов с колониями
 * главная жалоба новичка — не сложность, а <b>непонятность первого часа</b>.
 * Игрок не идёт в вики и не читает чужой Discord; всё, что он прочтёт, —
 * это то, что оказалось у него в руках. Поэтому руководство — предмет,
 * и лежит оно в инвентаре, а не на сайте.
 * <p>
 * <b>Ванильная книга, а не библиотека.</b> Существуют целые моды для
 * руководств — с картинками, поиском и трёхмерными сценами. Любой из них
 * — это ещё одна зависимость, которую игрок обязан скачать, и ещё один
 * повод не запуститься после обновления. Подписанная книга есть в игре
 * с самого начала и не ломается никогда.
 * <p>
 * <b>Страницы — переводимые, а не готовый текст.</b> Страница книги хранит
 * JSON текстового компонента, и это значит, что в неё можно положить ключ
 * перевода. Так одна и та же книга читается по-русски и по-английски —
 * у каждого на своём языке, хотя лежит она в мире одна.
 */
public final class Guide {

    /** Ключи страниц по порядку. Порядок здесь — порядок в книге. */
    private static final List<String> PAGES = List.of(
            "villagepax.guide.what",
            "villagepax.guide.find",
            "villagepax.guide.elder",
            "villagepax.guide.blueprint",
            "villagepax.guide.console",
            "villagepax.guide.order",
            "villagepax.guide.storage",
            "villagepax.guide.citizens",
            "villagepax.guide.trade",
            "villagepax.guide.trust",
            "villagepax.guide.people",
            "villagepax.guide.caravan",
            "villagepax.guide.raid",
            "villagepax.guide.peace",
            "villagepax.guide.faith",
            "villagepax.guide.errands",
            "villagepax.guide.life");

    private Guide() {
    }

    /**
     * Книга целиком.
     * <p>
     * Заголовок и автор — обычные строки: ваниль не переводит их никогда,
     * и переводить «Village Pax» незачем. Внутри — ключи.
     */
    public static ItemStack book() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();

        nbt.putString("title", "Village Pax");
        nbt.putString("author", "Village Pax");
        nbt.putInt("generation", 0);

        NbtList pages = new NbtList();
        for (String key : PAGES) {
            pages.add(NbtString.of(Text.Serializer.toJson(Text.translatable(key))));
        }
        nbt.put("pages", pages);
        return book;
    }

    /** Сколько страниц: нужно проверке, чтобы книга не осталась пустой. */
    public static int pageCount() {
        return PAGES.size();
    }

    /** Ключи страниц: проверка сверяет их с переводом. */
    public static List<String> pageKeys() {
        return PAGES;
    }
}
