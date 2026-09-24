package com.villagepax.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Каждая ссылка между файлами облика ведёт в существующий файл.
 * <p>
 * Блокстейт называет модель, модель — родителя, текстуры и подмены
 * по предикату (стопка монет, пустой кошель). Промах в любом имени
 * не роняет ничего: игра молча рисует чёрно-фиолетовый куб или клетку
 * «текстура не найдена», и увидит это только тот, кто поставит блок
 * или возьмёт стопку монет в руку, — то есть игрок.
 * <p>
 * {@link BlockResourcesTest} проверяет, что у блока <b>есть</b> блокстейт
 * и модель предмета; эта проверка — что внутри них ссылки не висят.
 * Ссылки на ванильные файлы не проверяются: их нет на диске модуля,
 * а опечатка в них та же, что в наших, — только её ловит сама игра
 * при запуске, в логе.
 */
class ModelReferencesTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "villagepax");

    @Test
    void everyReferenceLeadsToAFile() throws IOException {
        List<String> broken = new ArrayList<>();
        int references = 0;

        for (Path state : jsons(ASSETS.resolve("blockstates"))) {
            List<String> models = new ArrayList<>();
            collect(read(state), "model", models);
            for (String model : models) {
                references++;
                check(broken, state, model, "models", ".json");
            }
        }

        for (String folder : List.of("block", "item")) {
            for (Path model : jsons(ASSETS.resolve(Path.of("models", folder)))) {
                JsonObject json = read(model);
                if (json.has("parent")) {
                    references++;
                    check(broken, model, json.get("parent").getAsString(), "models", ".json");
                }
                if (json.has("textures")) {
                    for (var texture : json.getAsJsonObject("textures").entrySet()) {
                        String value = texture.getValue().getAsString();
                        if (!value.startsWith("#")) {
                            references++;
                            check(broken, model, value, "textures", ".png");
                        }
                    }
                }
                if (json.has("overrides")) {
                    for (JsonElement override : json.getAsJsonArray("overrides")) {
                        references++;
                        check(broken, model, override.getAsJsonObject().get("model").getAsString(),
                                "models", ".json");
                    }
                }
            }
        }

        assertTrue(references > 0, "Ни одной ссылки — проверка сверяет пустоту");
        assertTrue(broken.isEmpty(), "Ссылка ведёт в пустоту:\n  " + String.join("\n  ", broken));
    }

    /** Только наши ссылки: у ванильных файла на диске модуля нет. */
    private static void check(List<String> broken, Path from, String reference, String kind,
                              String extension) {
        String namespace = reference.contains(":") ? reference.substring(0, reference.indexOf(':'))
                : "minecraft";
        if (!namespace.equals("villagepax")) {
            return;
        }
        String path = reference.substring(reference.indexOf(':') + 1);
        Path target = ASSETS.resolve(kind).resolve(path.replace('/', java.io.File.separatorChar)
                + extension);
        if (!Files.isRegularFile(target)) {
            broken.add(from.getFileName() + " → " + reference);
        }
    }

    private static void collect(JsonElement element, String key, List<String> out) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()) {
                    out.add(entry.getValue().getAsString());
                } else {
                    collect(entry.getValue(), key, out);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> collect(item, key, out));
        }
    }

    private static List<Path> jsons(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> found = files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            assertFalse(found.isEmpty(), "Пусто в " + folder);
            return found;
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
