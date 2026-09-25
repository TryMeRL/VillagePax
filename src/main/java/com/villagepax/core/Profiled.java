package com.villagepax.core;

import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.profiler.Profiler;

/**
 * Тик мода — своей строкой в профилировщике сервера.
 * <p>
 * Всё, что мод делает каждый тик мира, зовётся из события конца тика,
 * и без разметки его время ложилось в профилировщике в общую кучу
 * «чего-то после мира». Администратор, открывший {@code /debug} или spark
 * на тормозящем сервере, видел, что тик долог, но не видел, чей он.
 * Теперь у каждого дела мода своя строка — {@code villagepax:work},
 * {@code villagepax:raids} и остальные, — и вопрос «это мод деревень?»
 * решается одним взглядом, а не выключением модов по очереди.
 * <p>
 * Стоит это два вызова на обработчик за тик: профилировщик, когда
 * не пишет, — пустышка.
 */
public final class Profiled {

    private Profiled() {
    }

    /**
     * Обработчик конца тика мира, размеченный своим именем.
     *
     * @param section имя строки без пространства имён: {@code "work"}
     * @param handler сам обработчик
     */
    public static ServerTickEvents.EndWorldTick tick(String section,
                                                     ServerTickEvents.EndWorldTick handler) {
        String name = VillagePax.MOD_ID + ":" + section;
        return world -> {
            Profiler profiler = world.getProfiler();
            profiler.push(name);
            try {
                handler.onEndTick(world);
            } finally {
                profiler.pop();
            }
        };
    }
}
