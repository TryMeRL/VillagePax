package com.villagepax.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdsTest {

    @Test
    void buildsNamespacedPath() {
        assertEquals("villagepax:norman/lumberjack", Ids.path("norman/lumberjack"));
    }
}
