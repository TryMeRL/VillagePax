package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Облик жителя: какой текстурой его рисовать.
 * <p>
 * До этого на всех жителей мира была <b>одна картинка</b>. Майя выглядели
 * норманнами, женщина — мужчиной, пахарь — стражником. К людям, которых
 * не различить, невозможно привязаться: колония читалась счётчиком
 * населения, а не местом, где живут люди. Это и был главный ответ
 * на «играть скучно».
 * <p>
 * <b>Облик — это опознаватель текстуры, и считает его сервер.</b> Клиент
 * не знает ни культур (они в датапаке сервера), ни ремёсел, ни того, кто
 * кому кем приходится. Присылать ему культуру и пол значило бы
 * синхронизировать половину датапака ради выбора картинки.
 * <p>
 * Путь складывается из того же, из чего состоит человек:
 * {@code <народ>:textures/entity/citizen/<народ>/<пол>[_<ремесло>].png}.
 * Пространство имён берётся у культуры, а не у мода: народ из чужого
 * датапака кладёт свои текстуры к себе и не трогает наши.
 */
public final class Looks {

    /** Кем рисовать того, о ком ничего не известно. */
    public static final Identifier UNKNOWN =
            new Identifier(VillagePax.MOD_ID, "textures/entity/citizen/norman/male.png");

    /** Где в пути к облику начинается имя народа. */
    private static final String FOLDER = "textures/entity/citizen/";

    private Looks() {
    }

    /** Облик по записи жителя — всё, что нужно, в ней уже есть. */
    public static Identifier of(Citizen citizen) {
        return of(citizen.culture(), citizen.gender(), citizen.profession());
    }

    public static Identifier of(Identifier culture, Gender gender,
                                Optional<Identifier> profession) {
        String who = gender == Gender.FEMALE ? "female" : "male";
        String craft = profession.map(id -> "_" + id.getPath()).orElse("");
        return new Identifier(culture.getNamespace(),
                "textures/entity/citizen/" + culture.getPath() + "/" + who + craft + ".png");
    }

    /**
     * Чей это народ — по пути к текстуре.
     * <p>
     * Единственный канал, по которому клиент узнаёт народ: культуры живут
     * в датапаке сервера, а в облике их имя уже есть. Заводить ради этого
     * второе отслеживаемое поле значило бы посылать по сети то, что и так
     * приехало.
     * <p>
     * Нужно затем, что телом народ отличается не только мастью: у пони
     * своя модель, и выбрать её надо до первой отрисовки.
     */
    public static Optional<String> cultureOf(String look) {
        int start = look.indexOf(FOLDER);
        if (start < 0) {
            return Optional.empty();
        }
        String tail = look.substring(start + FOLDER.length());
        int slash = tail.indexOf('/');
        return slash <= 0 ? Optional.empty() : Optional.of(tail.substring(0, slash));
    }

    /**
     * Из чего сложен этот облик: народ, пол и ремесло — словами.
     * <p>
     * Тем же разбором пути, что {@link #cultureOf}, и по той же причине:
     * других сведений о жителе у клиента нет. Ремесло — всё, что после
     * первого подчёркивания: пол подчёркиваний не содержит, а у ремесла
     * из чужого датапака они быть могут.
     * <p>
     * Слова нужны приметам ({@link Marks}): борода и поля шляпы видны
     * тому, чьи слова совпали с условием в имени кости.
     */
    public static Set<String> words(String look) {
        Optional<String> people = cultureOf(look);
        if (people.isEmpty()) {
            return Set.of();
        }
        String tail = look.substring(look.indexOf(FOLDER) + FOLDER.length() + people.get().length() + 1);
        int dot = tail.lastIndexOf('.');
        String stem = dot < 0 ? tail : tail.substring(0, dot);
        int split = stem.indexOf('_');
        Set<String> words = new HashSet<>();
        words.add(people.get());
        words.add(split < 0 ? stem : stem.substring(0, split));
        if (split >= 0 && split + 1 < stem.length()) {
            words.add(stem.substring(split + 1));
        }
        return Set.copyOf(words);
    }

    /**
     * Облик куклы без записи: у налётчика и торговца нет ни имени,
     * ни ремесла, но народ есть, и по нему видно, чьи они.
     */
    public static Identifier puppet(Identifier culture, String craft) {
        return new Identifier(culture.getNamespace(),
                "textures/entity/citizen/" + culture.getPath() + "/male_" + craft + ".png");
    }
}
