package com.villagepax.sim.work;

import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Профессия → логика работы.
 * <p>
 * Пока таблица в коде: сами профессии станут данными в задаче 1.9, но логик
 * останется семь, и они всегда были заявлены кодом. Датапак будет выбирать
 * логику по имени, а не приносить свою.
 */
public final class Jobs {

    private static final Map<Identifier, Job> BY_PROFESSION = register(new BuilderJob(), new HaulJob());

    private Jobs() {
    }

    private static Map<Identifier, Job> register(Job... jobs) {
        Map<Identifier, Job> byProfession = new LinkedHashMap<>();
        for (Job job : jobs) {
            byProfession.put(job.profession(), job);
        }
        return Map.copyOf(byProfession);
    }

    public static Optional<Job> forProfession(Optional<Identifier> profession) {
        return profession.map(BY_PROFESSION::get);
    }

    public static Set<Identifier> professions() {
        return BY_PROFESSION.keySet();
    }
}
