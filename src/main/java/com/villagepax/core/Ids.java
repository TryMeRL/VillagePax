package com.villagepax.core;

/**
 * Построение идентификаторов мода без обращения к классам Minecraft —
 * так модульные тесты остаются быстрыми и не требуют запуска игры.
 */
public final class Ids {
    public static final String NAMESPACE = "villagepax";

    private Ids() {
    }

    public static String path(String value) {
        return NAMESPACE + ":" + value;
    }
}
