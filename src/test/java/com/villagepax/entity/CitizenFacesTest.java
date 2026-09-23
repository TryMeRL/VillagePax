package com.villagepax.entity;

import com.villagepax.sim.Gender;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * У каждого народа есть лицо для каждого ремесла.
 * <p>
 * Написано по настоящей дыре, прожившей в моде месяц. Облик жителя
 * складывается по правилу имени ({@link Looks}), а картинки рисует
 * генератор — и списки у них разошлись молча. Ремёсел в датапаке девять,
 * а рисовалось шесть: <b>пивовар, купец и ткачиха не имели лица ни у одного
 * народа</b>. В игре это чёрно-фиолетовый куб вместо человека, и стоит он
 * не где-нибудь — купец есть в каждой деревне, и говорит с ним игрок чаще,
 * чем с кем бы то ни было.
 * <p>
 * Не нашлось это само ни разу, и не могло: путь собирается без ошибки,
 * файл просто не находится, а игра о ненайденной текстуре не говорит
 * ничего. Заметить можно, только встретив такого человека в мире, —
 * то есть это заметит игрок, а не автор.
 * <p>
 * Проверяется <b>правилом, а не его копией</b>: путь спрашивается
 * у {@link Looks}, у того самого кода, по которому игра ищет картинку.
 * Собери мы путь здесь своими руками — проверка сверяла бы генератор
 * со своей копией правила, и разойтись они могли бы втроём.
 * <p>
 * Читается с диска: поднимать игру ради сверки списков файлов незачем.
 */
class CitizenFacesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path DATA = RESOURCES.resolve(Path.of("data", "villagepax", "villagepax"));

    /**
     * Ремёсла кукол — тех, у кого нет записи жителя.
     * <p>
     * Налётчик, союзник и приезжий возница рисуются по {@link Looks#puppet},
     * а профессии у них нет вовсе. Список короткий и пишется рукой, потому
     * что в датапаке его нет: это решение кода, а не данных.
     */
    private static final List<String> PUPPETS = List.of("courier", "guard");

    @Test
    void everyPeopleHasAFaceForEveryCraft() throws IOException {
        List<String> missing = new ArrayList<>();

        for (String culture : namesIn("cultures")) {
            Identifier people = new Identifier("villagepax", culture);
            for (Gender gender : Gender.values()) {
                check(missing, Looks.of(people, gender, Optional.empty()));
                for (String craft : namesIn("professions")) {
                    check(missing, Looks.of(people, gender,
                            Optional.of(new Identifier("villagepax", craft))));
                }
            }
            for (String craft : PUPPETS) {
                check(missing, Looks.puppet(people, craft));
            }
        }

        assertTrue(missing.isEmpty(), "Нет картинки для облика: " + missing);
    }

    /** Имена файлов в разделе датапака — они же опознаватели. */
    private static List<String> namesIn(String folder) throws IOException {
        try (Stream<Path> files = Files.list(DATA.resolve(folder))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }

    private static void check(List<String> missing, Identifier look) {
        Path file = RESOURCES.resolve(Path.of("assets", look.getNamespace()))
                .resolve(look.getPath().replace('/', java.io.File.separatorChar));
        if (!Files.isRegularFile(file)) {
            missing.add(look.toString());
        }
    }
}
