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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Движения жителя привязаны к костям, которые у него есть, а код зовёт
 * движения, которые написаны у <b>каждого</b> тела.
 * <p>
 * Три файла, и ни один не знает о двух других. Кости живут в geo-модели,
 * дорожки движения — в animation-файле, имена дорожек — в коде жителя.
 * Опечатка в любом из трёх <b>не роняет ничего</b>: GeckoLib молча
 * не найдёт кость и не повернёт её, или молча не найдёт дорожку и оставит
 * человека стоять столбом. В логе тишина, в игре — житель, который просто
 * не машет рукой.
 * <p>
 * С появлением коня файлов стало не три, а пять, и прибавилось условие,
 * которого раньше не было: <b>дорожки в теле названы раз и навсегда,
 * а тел много</b>. Пони, у которого не написано «дышит», молча заморозил
 * бы целую дорожку — и никто бы этого не заметил, потому что дышит житель
 * еле-еле.
 * <p>
 * Это та же болезнь, от которой заведены {@code CitizenFacesTest}
 * и {@code DatapackWordsTest}: объявлено в одном месте, нарисовано
 * в другом, и никто их не сверяет.
 */
class CitizenAnimationTest {

    private static final Path ASSETS =
            Path.of("src", "main", "resources", "assets", "villagepax");
    private static final Path GEO = ASSETS.resolve(Path.of("geo", "entity"));
    private static final Path DANCES = ASSETS.resolve(Path.of("animations", "entity"));

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "villagepax",
            "entity", "CitizenEntity.java");

    /** Имена дорожек, названные в коде: {@code thenLoop("walk")} и такие же. */
    private static final Pattern CALLED =
            Pattern.compile("then(?:Loop|Play|PlayAndHold)\\(\"([^\"]+)\"\\)");

    /** Все тела жителя: общее человеческое и те, что народы объявили своими. */
    private static List<String> bodies() throws IOException {
        try (Stream<Path> files = Files.list(GEO)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".geo.json"))
                    .map(name -> name.substring(0, name.length() - ".geo.json".length()))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyBodyHasItsOwnAnimations() throws IOException {
        List<String> mute = new ArrayList<>();
        for (String body : bodies()) {
            if (!Files.isRegularFile(DANCES.resolve(body + ".animation.json"))) {
                mute.add(body);
            }
        }
        assertTrue(mute.isEmpty(), "Тело есть, а двигаться ему нечем: " + mute);
    }

    @Test
    void everyAnimationTurnsBonesThatExist() throws IOException {
        List<String> strangers = new ArrayList<>();

        for (String body : bodies()) {
            Set<String> bones = bonesOf(body);
            JsonObject dances = animationsOf(body);
            for (String dance : dances.keySet()) {
                JsonObject turned = dances.getAsJsonObject(dance).getAsJsonObject("bones");
                if (turned == null) {
                    continue;
                }
                for (String bone : turned.keySet()) {
                    if (!bones.contains(bone)) {
                        strangers.add(body + "/" + dance + " вращает кость " + bone
                                + ", которой в модели нет");
                    }
                }
            }
        }

        assertTrue(strangers.isEmpty(), "Движение в пустоту: " + strangers);
    }

    /**
     * Каждое движение, которое зовёт код, написано у каждого тела.
     * <p>
     * У каждого — потому что дорожки заводятся в теле жителя один раз
     * на всех, а тел много. Конь и человек «работают» по-разному, но
     * называется это одинаково, и не назвать значит замолчать.
     */
    @Test
    void everyAnimationTheCodeAsksForIsWrittenForEveryBody() throws IOException {
        List<String> asked = called();
        List<String> missing = new ArrayList<>();

        for (String body : bodies()) {
            Set<String> written = animationsOf(body).keySet();
            for (String dance : asked) {
                if (!written.contains(dance)) {
                    missing.add(body + " не умеет " + dance);
                }
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
        String code = Files.readString(SOURCE, StandardCharsets.UTF_8);
        List<String> idle = new ArrayList<>();

        for (String body : bodies()) {
            for (String dance : animationsOf(body).keySet()) {
                if (!code.contains("\"" + dance + "\"")) {
                    idle.add(body + "/" + dance);
                }
            }
        }

        assertTrue(idle.isEmpty(), "Движение написано, но его никто не зовёт: " + idle);
    }

    private static List<String> called() throws IOException {
        List<String> asked = new ArrayList<>();
        Matcher calls = CALLED.matcher(Files.readString(SOURCE, StandardCharsets.UTF_8));
        while (calls.find()) {
            asked.add(calls.group(1));
        }
        return asked;
    }

    /** Имена костей этого тела. */
    private static Set<String> bonesOf(String body) throws IOException {
        JsonObject geometry = read(GEO.resolve(body + ".geo.json"))
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Set<String> names = new LinkedHashSet<>();
        geometry.getAsJsonArray("bones").forEach(bone ->
                names.add(bone.getAsJsonObject().get("name").getAsString()));
        return names;
    }

    private static JsonObject animationsOf(String body) throws IOException {
        return read(DANCES.resolve(body + ".animation.json")).getAsJsonObject("animations");
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
