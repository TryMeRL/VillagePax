package com.villagepax.core;

import com.villagepax.VillagePax;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ModTags {

    /**
     * Блоки, которые билдер вносит последними, когда коробка здания готова.
     * <p>
     * Отделять обстановку от несущего предикатом по блокстейту
     * ({@code isSolid}, {@code isOpaque}) — гадание о внутренностях ванили:
     * у ступеней и плит эти флаги не те, которых ждёшь, и меняются между
     * версиями молча. Тег снимает вопрос: поведение задано данными, автор
     * датапака правит его без Java, и это тот же принцип, на котором стоит
     * весь мод.
     */
    public static final TagKey<Block> BUILD_DECOR = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "build_decor"));

    /**
     * Что житель считает едой.
     * <p>
     * Тег, а не {@code Item.isFood()}: последний вернёт истину и для гнилой
     * плоти, и для золотого яблока. То же решение, что и у
     * {@link #BUILD_DECOR} — поведение задают данные, и у культуры сможет
     * быть свой стол.
     */
    public static final TagKey<Item> CITIZEN_FOOD = TagKey.of(
            RegistryKeys.ITEM, new Identifier(VillagePax.MOD_ID, "citizen_food"));

    private ModTags() {
    }
}
