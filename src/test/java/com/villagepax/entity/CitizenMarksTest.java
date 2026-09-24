package com.villagepax.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villagepax.sim.Gender;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Приметы жителя, развёртка его модели и кожи — друг с другом и с датапаком.
 * <p>
 * Три места, и ни одно не знает о двух других. Условие примет живёт
 * в имени кости ({@code beard@dwarf+male}), народы и ремёсла — в датапаке,
 * картинки — в текстурах. Опечатка в слове условия <b>не роняет ничего</b>:
 * кость просто никогда не покажется, и гном останется без бороды молча.
 * Развёртка, вылезшая за край текстуры, рисует куб чужими пикселями,
 * а кожа не того размера растягивает всю модель — и тоже молча.
 * <p>
 * Это та же болезнь, от которой заведены {@code CitizenFacesTest}
 * и {@code CitizenAnimationTest}: объявлено в одном месте, нарисовано
 * в другом.
 */
class CitizenMarksTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path ASSETS = RESOURCES.resolve(Path.of("assets", "villagepax"));
    private static final Path GEO = ASSETS.resolve(Path.of("geo", "entity"));
    private static final Path SKINS = ASSETS.resolve(Path.of("textures", "entity", "citizen"));
    private static final Path DATA = RESOURCES.resolve(Path.of("data", "villagepax", "villagepax"));

    /**
     * Пора жизни — единственные слова, которых нет в датапаке: их знает
     * тело, а не облик. См. {@code CitizenEntityRenderer#wordsOf}.
     */
    private static final Set<String> AGES = Set.of("child", "adult");

    @Test
    void everyMarkNamesSomethingThatExists() throws IOException {
        Set<String> known = new HashSet<>(AGES);
        known.addAll(namesIn(DATA.resolve("cultures")));
        known.addAll(namesIn(DATA.resolve("professions")));
        for (Gender gender : Gender.values()) {
            known.add(gender.name().toLowerCase(Locale.ROOT));
        }

        List<String> strangers = new ArrayList<>();
        int marks = 0;
        for (Path model : models()) {
            for (JsonElement bone : bones(model)) {
                String name = bone.getAsJsonObject().get("name").getAsString();
                Marks found = Marks.of(name).orElse(null);
                if (found == null) {
                    continue;
                }
                marks++;
                for (String word : found.words()) {
                    if (!known.contains(word)) {
                        strangers.add(model.getFileName() + ": " + name + " — «" + word + "»");
                    }
                }
            }
        }

        assertTrue(marks > 0, "В моделях не нашлось ни одной приметы — проверка сверяет пустоту");
        assertTrue(strangers.isEmpty(), "Примета ждёт слова, которого не бывает: " + strangers);
    }

    /** Развёртка каждого куба лежит на текстуре, а не за её краем. */
    @Test
    void everyCubeIsPaintedFromInsideTheTexture() throws IOException {
        List<String> outside = new ArrayList<>();
        for (Path model : models()) {
            JsonObject description = geometry(model).getAsJsonObject("description");
            int width = description.get("texture_width").getAsInt();
            int height = description.get("texture_height").getAsInt();
            for (JsonElement element : bones(model)) {
                JsonObject bone = element.getAsJsonObject();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cube : bone.getAsJsonArray("cubes")) {
                    for (int[] rect : rects(cube.getAsJsonObject())) {
                        if (rect[0] < 0 || rect[1] < 0
                                || rect[0] + rect[2] > width || rect[1] + rect[3] > height) {
                            outside.add(model.getFileName() + ": " + bone.get("name").getAsString());
                        }
                    }
                }
            }
        }
        assertTrue(outside.isEmpty(), "Куб берёт краску за краем текстуры: " + outside);
    }

    /**
     * Кожа того же размера, что объявила модель её народа.
     * <p>
     * GeckoLib делит развёртку на объявленный размер, а не на настоящий:
     * кожа вдвое ниже модели натягивается вдвое, и под ногами жителя
     * оказываются его же приметы.
     */
    @Test
    void everySkinHasTheSizeItsBodyExpects() throws IOException {
        List<String> wrong = new ArrayList<>();
        int skins = 0;
        for (String people : namesIn(DATA.resolve("cultures"))) {
            Path own = GEO.resolve("citizen_" + people + ".geo.json");
            JsonObject description = geometry(Files.isRegularFile(own) ? own
                    : GEO.resolve("citizen.geo.json")).getAsJsonObject("description");
            int width = description.get("texture_width").getAsInt();
            int height = description.get("texture_height").getAsInt();
            Path folder = SKINS.resolve(people);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                for (Path skin : files.filter(path -> path.toString().endsWith(".png")).toList()) {
                    skins++;
                    int[] size = pngSize(skin);
                    if (size[0] * height != size[1] * width) {
                        wrong.add(people + "/" + skin.getFileName() + " " + size[0] + "x" + size[1]
                                + " при модели " + width + "x" + height);
                    }
                }
            }
        }
        assertTrue(skins > 0, "Не нашлось ни одной кожи");
        assertTrue(wrong.isEmpty(), "Кожа не той формы, что ждёт модель: " + wrong);
    }

    /**
     * Прямоугольники развёртки куба: коробочной или по граням.
     * <p>
     * Размер коробочной — вниз до целого, как считает сама GeckoLib.
     */
    private static List<int[]> rects(JsonObject cube) {
        List<int[]> out = new ArrayList<>();
        JsonElement uv = cube.get("uv");
        JsonArray size = cube.getAsJsonArray("size");
        int w = (int) Math.floor(size.get(0).getAsDouble());
        int h = (int) Math.floor(size.get(1).getAsDouble());
        int d = (int) Math.floor(size.get(2).getAsDouble());
        if (uv.isJsonArray()) {
            int u = uv.getAsJsonArray().get(0).getAsInt();
            int v = uv.getAsJsonArray().get(1).getAsInt();
            out.add(new int[]{u, v, 2 * (w + d), d + h});
            return out;
        }
        for (String face : uv.getAsJsonObject().keySet()) {
            JsonObject each = uv.getAsJsonObject().getAsJsonObject(face);
            JsonArray at = each.getAsJsonArray("uv");
            JsonArray span = each.getAsJsonArray("uv_size");
            out.add(new int[]{at.get(0).getAsInt(), at.get(1).getAsInt(),
                    span.get(0).getAsInt(), span.get(1).getAsInt()});
        }
        return out;
    }

    /** Ширина и высота картинки — из заголовка PNG, без чтения пикселей. */
    private static int[] pngSize(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             DataInputStream data = new DataInputStream(in)) {
            data.skipNBytes(16);
            return new int[]{data.readInt(), data.readInt()};
        }
    }

    private static List<Path> models() throws IOException {
        try (Stream<Path> files = Files.list(GEO)) {
            List<Path> found = files.filter(path -> path.toString().endsWith(".geo.json"))
                    .sorted().toList();
            assertFalse(found.isEmpty(), "Моделей жителя не нашлось");
            return found;
        }
    }

    private static JsonObject geometry(Path model) throws IOException {
        return JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static JsonArray bones(Path model) throws IOException {
        return geometry(model).getAsJsonArray("bones");
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }
}
