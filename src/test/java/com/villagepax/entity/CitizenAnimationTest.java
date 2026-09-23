package com.villagepax.entity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Движения жителя привязаны к костям, которые у него есть, а код зовёт
 * движения, которые написаны.
 * <p>
 * Три файла, и ни один не знает о двух других. Кости живут в geo-модели,
 * дорожки движения — в animation-файле, имена дорожек — в коде жителя.
 * Опечатка в любом из трёх <b>не роняет ничего</b>: GeckoLib молча
 * не найдёт кость и не повернёт её, или молча не найдёт дорожку и оставит
 * человека стоять столбом. В логе тишина, в игре — житель, который просто
 * не машет рукой.
 * <p>
 * Это та же болезнь, от которой заведены {@code CitizenFacesTest}
 * и {@code DatapackWordsTest}: объявлено в одном месте, нарисовано
 * в другом, и никто их не сверяет. Здесь мест уже три.
 */
class CitizenAnimationTest {

    private static final Path ASSETS =
            Path.of("src", "main", "resources", "assets", "villagepax");

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "villagepax",
            "entity", "CitizenEntity.java");

    /** Имена дорожек, названные в коде: {@code thenLoop("walk")} и такие же. */
    private static final Pattern CALLED = Pattern.compile("then(?:Loop|Play|PlayAndHold)\\(\"([^\"]+)\"\\)");

    @Test
    void everyAnimationTurnsBonesThatExist() throws IOException {
        Set<String> bones = bonesOfModel();
        JsonObject animations = read(ASSETS.resolve(
                Path.of("animations", "entity", "citizen.animation.json")))
                .getAsJsonObject("animations");
        List<String> strangers = new ArrayList<>();

        for (String dance : animations.keySet()) {
            JsonObject turned = animations.getAsJsonObject(dance).getAsJsonObject("bones");
            if (turned == null) {
                continue;
            }
            for (String bone : turned.keySet()) {
                if (!bones.contains(bone)) {
                    strangers.add(dance + " вращает кость " + bone + ", которой в модели нет");
                }
            }
        }

        assertTrue(strangers.isEmpty(), "Движение в пустоту: " + strangers);
    }

    @Test
    void everyAnimationTheCodeAsksForIsWritten() throws IOException {
        Set<String> written = read(ASSETS.resolve(
                Path.of("animations", "entity", "citizen.animation.json")))
                .getAsJsonObject("animations").keySet();
        List<String> asked = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        Matcher calls = CALLED.matcher(Files.readString(SOURCE, StandardCharsets.UTF_8));
        while (calls.find()) {
            asked.add(calls.group(1));
            if (!written.contains(calls.group(1))) {
                missing.add(calls.group(1));
            }
        }

        assertFalse(asked.isEmpty(), "В коде жителя не нашлось ни одной дорожки движения — "
                + "проверка сверяет пустоту с пустотой");
        assertTrue(missing.isEmpty(), "Код зовёт движение, которого не написано: " + missing);
    }

    /**
     * И наоборот: написанное движение кто-то зовёт.
     * <p>
     * Дорожка, которую не запускает никто, — это не запас на будущее,
     * а строчка, которую никто никогда не проигрывал, и значит не видел.
     */
    @Test
    void everyWrittenAnimationIsAskedFor() throws IOException {
        Set<String> written = read(ASSETS.resolve(
                Path.of("animations", "entity", "citizen.animation.json")))
                .getAsJsonObject("animations").keySet();
        String code = Files.readString(SOURCE, StandardCharsets.UTF_8);
        List<String> idle = new ArrayList<>();

        for (String dance : written) {
            if (!code.contains("\"" + dance + "\"")) {
                idle.add(dance);
            }
        }

        assertTrue(idle.isEmpty(), "Движение написано, но его никто не зовёт: " + idle);
    }

    /** Имена костей модели, включая вложенные. */
    private static Set<String> bonesOfModel() throws IOException {
        JsonObject geometry = read(ASSETS.resolve(Path.of("geo", "entity", "citizen.geo.json")))
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Set<String> names = new LinkedHashSet<>();
        geometry.getAsJsonArray("bones").forEach(bone ->
                names.add(bone.getAsJsonObject().get("name").getAsString()));
        return names;
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
