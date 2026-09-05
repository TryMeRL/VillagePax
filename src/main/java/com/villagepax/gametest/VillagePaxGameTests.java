package com.villagepax.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Игровые тесты — всё, что нельзя проверить без запущенного мира:
 * сохранение состояния, выгрузка чанков, поведение жителей, стройка.
 * Пока здесь только проверка, что харнесс работает.
 */
public class VillagePaxGameTests implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modIsLoaded(TestContext context) {
        context.complete();
    }
}
