package com.villagepax.sim.work;

import com.villagepax.core.profession.Profession;
import com.villagepax.core.profession.ProfessionManager;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Логики работы, зарегистрированные кодом.
 * <p>
 * Профессия — данные, логика — код: датапак <b>выбирает</b> логику по имени,
 * а не приносит свою. Логик семь на весь мод, и они всегда были кодом; иначе
 * пришлось бы пускать в мод чужой исполняемый код.
 */
public final class Jobs {

    private static final Map<Identifier, Job> BY_LOGIC =
            register(new BuilderJob(), new HaulJob(), new GatherJob(), new FarmJob(),
                    new GuardJob());

    private Jobs() {
    }

    private static Map<Identifier, Job> register(Job... jobs) {
        Map<Identifier, Job> byLogic = new LinkedHashMap<>();
        for (Job job : jobs) {
            byLogic.put(job.logic(), job);
        }
        return Map.copyOf(byLogic);
    }

    /**
     * Логика работы для профессии жителя.
     * <p>
     * Пусто и когда профессии нет, и когда её файл называет логику, которой
     * в коде не существует: опечатка в датапаке не должна ронять сервер —
     * житель просто останется без дела.
     */
    public static Optional<Job> forProfession(Optional<Identifier> profession) {
        return profession
                .flatMap(ProfessionManager::get)
                .map(Profession::job)
                .map(BY_LOGIC::get);
    }

    public static Set<Identifier> logics() {
        return BY_LOGIC.keySet();
    }
}
