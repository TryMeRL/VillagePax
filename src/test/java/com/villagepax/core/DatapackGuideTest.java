package com.villagepax.core;

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
 * Инструкция для авторов датапаков не расходится с модом.
 * <p>
 * `docs/datapacks.md` — единственное обещание, которое мод даёт наружу:
 * «новый народ добавляется данными, без единой строчки Java». Обещание
 * это проверяемое, и проверять его надо, потому что документация гниёт
 * тише всего на свете: код правят, а текст рядом остаётся вчерашним
 * и врёт с уверенным видом.
 * <p>
 * Сверяется не пересказ, а <b>имена, которые автор датапака напишет
 * в свой файл</b>: черты, дорожки движения, кости для предмета в руках
 * и разделы самого датапака. Ошибиться в них — значит отправить человека
 * писать то, чего мод не поймёт, и он об этом узнает не из ошибки,
 * а из того, что ничего не работает.
 */
class DatapackGuideTest {

    private static final Path DOC = Path.of("docs", "datapacks.md");
    private static final Path JAVA = Path.of("src", "main", "java", "com", "villagepax");
    private static final Path CLIENT = Path.of("src", "client", "java", "com", "villagepax");
    private static final Path DATA =
            Path.of("src", "main", "resources", "data", "villagepax", "villagepax");

    /**
     * Каждая черта мода названа в инструкции, и ни одной лишней.
     * <p>
     * В обе стороны нарочно. Черта, о которой не сказано, не будет
     * включена никем — её всё равно что нет. Черта, о которой сказано,
     * а в моде её нет, — это прямая ложь: автор напишет строку, мод
     * промолчит, и виноватым будет автор.
     */
    @Test
    void everyTraitIsDocumentedAndEveryDocumentedTraitExists() throws IOException {
        Set<String> inCode = new LinkedHashSet<>();
        Matcher declared = Pattern.compile("^\\s+[A-Z_]+\\(\"([a-z_]+)\"\\)", Pattern.MULTILINE)
                .matcher(read(JAVA.resolve(Path.of("core", "culture", "Trait.java"))));
        while (declared.find()) {
            inCode.add(declared.group(1));
        }

        Set<String> inDoc = new LinkedHashSet<>();
        Matcher named = Pattern.compile("`villagepax:([a-z_]+)`").matcher(read(DOC));
        while (named.find()) {
            inDoc.add(named.group(1));
        }

        assertFalse(inCode.isEmpty(), "В Trait.java не нашлось ни одной черты — "
                + "проверка сверяет пустоту с пустотой");

        List<String> silent = inCode.stream().filter(trait -> !inDoc.contains(trait)).toList();
        List<String> invented = inDoc.stream().filter(trait -> !inCode.contains(trait)).toList();

        assertTrue(silent.isEmpty(), "Черта есть, а в инструкции о ней молчат: " + silent);
        assertTrue(invented.isEmpty(), "Инструкция обещает черту, которой нет: " + invented);
    }

    /**
     * Каждая дорожка движения, которую мод спрашивает у тела, названа
     * в инструкции.
     * <p>
     * Автор датапака, дающий народу своё тело, обязан написать их все:
     * не написанная молча замрёт. Узнать их список ему неоткуда, кроме
     * как отсюда.
     */
    @Test
    void everyAnimationTheModAsksForIsDocumented() throws IOException {
        String doc = read(DOC);
        Set<String> asked = new LinkedHashSet<>();
        Matcher calls = Pattern.compile("then(?:Loop|Play|PlayAndHold)\\(\"([^\"]+)\"\\)")
                .matcher(read(JAVA.resolve(Path.of("entity", "CitizenEntity.java"))));
        while (calls.find()) {
            asked.add(calls.group(1));
        }

        assertFalse(asked.isEmpty(), "Мод не спрашивает ни одной дорожки — "
                + "проверка сверяет пустоту с пустотой");
        List<String> silent = asked.stream().filter(dance -> !doc.contains("`" + dance + "`"))
                .toList();
        assertTrue(silent.isEmpty(), "Дорожка есть, а в инструкции о ней молчат: " + silent);
    }

    /**
     * И каждая кость, на которую мод вешает предмет в руках.
     * <p>
     * Народ со своим телом обязан назвать их так же, иначе у его людей
     * работа перестанет быть видной — а это правило мода с первой недели.
     */
    @Test
    void everyCarryingBoneIsDocumented() throws IOException {
        String doc = read(DOC);
        Set<String> bones = new LinkedHashSet<>();
        Matcher named = Pattern.compile(
                        "(?:HAND|PACK|TEETH)\\s*=\\s*\"([a-z_]+)\"")
                .matcher(read(CLIENT.resolve(Path.of("client", "CitizenEntityRenderer.java"))));
        while (named.find()) {
            bones.add(named.group(1));
        }

        assertFalse(bones.isEmpty(), "Отрисовщик не вешает предмет ни на одну кость — "
                + "проверка сверяет пустоту с пустотой");
        List<String> silent = bones.stream().filter(bone -> !doc.contains("`" + bone + "`"))
                .toList();
        assertTrue(silent.isEmpty(), "Кость есть, а в инструкции о ней молчат: " + silent);
    }

    /**
     * Каждый раздел датапака назван в инструкции.
     * <p>
     * Заведи мод завтра папку {@code rites/} — и автор датапака о ней
     * не узнает никогда: раздел, о котором не сказано, для него
     * не существует.
     */
    @Test
    void everyDatapackFolderIsDocumented() throws IOException {
        String doc = read(DOC);
        List<String> silent = new ArrayList<>();
        try (Stream<Path> inside = Files.list(DATA)) {
            for (Path folder : inside.filter(Files::isDirectory).sorted().toList()) {
                String name = folder.getFileName().toString();
                if (!doc.contains(name + "/")) {
                    silent.add(name);
                }
            }
        }
        assertTrue(silent.isEmpty(), "Раздел датапака есть, а в инструкции о нём молчат: "
                + silent);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
