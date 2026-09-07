package com.villagepax.core;

import com.villagepax.VillagePax;
import net.minecraft.block.Block;
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

    private ModTags() {
    }
}
