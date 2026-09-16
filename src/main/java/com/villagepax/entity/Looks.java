package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import net.minecraft.util.Identifier;

import java.util.Optional;

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
     * Облик куклы без записи: у налётчика и торговца нет ни имени,
     * ни ремесла, но народ есть, и по нему видно, чьи они.
     */
    public static Identifier puppet(Identifier culture, String craft) {
        return new Identifier(culture.getNamespace(),
                "textures/entity/citizen/" + culture.getPath() + "/male_" + craft + ".png");
    }
}
