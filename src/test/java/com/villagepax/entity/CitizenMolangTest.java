package com.villagepax.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.core.molang.MolangException;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.core.molang.expressions.MolangValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Каждое выражение в движениях жителя разбирается и считается в число.
 * <p>
 * Выражение живёт строкой в JSON, и разбирает его GeckoLib при загрузке
 * ресурсов — то есть в игре, когда уже поздно. Опечатка в имени функции
 * роняет загрузку всех движений, а опечатка в имени переменной не роняет
 * ничего: разборщик молча заводит неизвестное имя нулём, и шаг выходит
 * с нулевым размахом — житель едет по улице на прямых ногах.
 * <p>
 * Поэтому здесь — тот же разборщик GeckoLib, и каждое выражение
 * считается на разных шагах: оно обязано дать конечный угол, по модулю
 * не больше полуоборота. Угол больше — это почти всегда забытые скобки.
 */
class CitizenMolangTest {

    private static final Path DANCES = Path.of("src", "main", "resources", "assets",
            "villagepax", "animations", "entity");

    /** Те же имена, что заводит CitizenGeoModel; сверку имён держит CitizenAnimationTest. */
    private static final String STRIDE = "query.villagepax_stride";
    private static final String STRIDE_AMOUNT = "query.villagepax_stride_amount";

    private static final double[] STRIDES = {0, 1.3, 4.7, 23.9};
    private static final double[] AMOUNTS = {0, 0.5, 1};

    @Test
    void everyExpressionParsesAndStaysWithinHalfATurn() throws IOException {
        List<String> broken = new ArrayList<>();
        int expressions = 0;

        try (Stream<Path> files = Files.list(DANCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".animation.json")).toList()) {
                JsonObject dances = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
                for (String dance : dances.keySet()) {
                    List<String> found = new ArrayList<>();
                    strings(dances.get(dance), found);
                    for (String expression : found) {
                        expressions++;
                        String problem = check(expression);
                        if (problem != null) {
                            broken.add(file.getFileName() + "/" + dance + ": «" + expression
                                    + "» — " + problem);
                        }
                    }
                }
            }
        }

        assertTrue(expressions > 0, "Ни одного выражения — проверка сверяет пустоту");
        assertTrue(broken.isEmpty(), "Выражение движения не считается: " + broken);
    }

    private static String check(String expression) {
        MolangValue value;
        try {
            value = MolangParser.parseExpression(expression);
        } catch (MolangException | RuntimeException failure) {
            return "не разбирается: " + failure.getMessage();
        }
        for (double stride : STRIDES) {
            for (double amount : AMOUNTS) {
                MolangParser.INSTANCE.setValue(STRIDE, () -> stride);
                MolangParser.INSTANCE.setValue(STRIDE_AMOUNT, () -> amount);
                double angle = value.get();
                if (!Double.isFinite(angle) || Math.abs(angle) > 180) {
                    return "на шаге " + stride + " с размахом " + amount + " даёт " + angle;
                }
            }
        }
        return null;
    }

    /** Все строковые значения внутри — числа в JSON выражениями не бывают. */
    private static void strings(JsonElement element, List<String> out) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            out.add(element.getAsString());
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(item -> strings(item, out));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> strings(entry.getValue(), out));
        }
    }
}
