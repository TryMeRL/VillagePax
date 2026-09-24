package com.villagepax.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Примета: кость модели, которая видна не всем.
 * <p>
 * Борода гнома, уши эльфа, поля шляпы пахаря, наплечники стража — всё
 * это кости одной модели, и прячет их не код, а <b>имя кости</b>:
 * {@code beard@dwarf+male+adult}. После {@code @} идут слова через
 * {@code +}, и совпасть должны все; у слова может стоять {@code !}
 * (не должно совпасть) и варианты через {@code |} (хватит одного):
 * {@code skirt@female+!elder}, {@code saddlebags@courier|merchant}.
 * <p>
 * Слова те же, из которых складывается облик ({@link Looks#words}):
 * народ, пол, ремесло — и пора жизни, которую тело знает само. Поэтому
 * в коде нет ни одного народа и ни одного ремесла: народ из чужого
 * датапака получает свои приметы, дописав кость в свою модель.
 * <p>
 * Правило живёт здесь, а не в отрисовке, ради проверки: условие читает
 * и клиент, и модульная проверка, сверяющая слова в моделях с народами
 * и ремёслами датапака, — и читать они обязаны одинаково.
 */
public final class Marks {

    /** Что отделяет имя кости от условия. */
    public static final char AT = '@';

    private final List<Term> terms;

    private Marks(List<Term> terms) {
        this.terms = terms;
    }

    /**
     * Условие этой кости — или пусто, если кость видна всем.
     * <p>
     * Пустое условие ({@code hat@}) тоже «видна всем»: запрета в нём нет,
     * а спрятать кость навсегда можно и не рисуя её.
     */
    public static Optional<Marks> of(String boneName) {
        int at = boneName.indexOf(AT);
        if (at < 0) {
            return Optional.empty();
        }
        List<Term> terms = new ArrayList<>();
        for (String raw : boneName.substring(at + 1).split("\\+")) {
            if (raw.isEmpty()) {
                continue;
            }
            boolean negated = raw.charAt(0) == '!';
            String body = negated ? raw.substring(1) : raw;
            terms.add(new Term(negated, List.of(body.split("\\|"))));
        }
        return Optional.of(new Marks(List.copyOf(terms)));
    }

    /** Видна ли кость тому, о ком сказаны эти слова. */
    public boolean fits(Set<String> words) {
        for (Term term : terms) {
            boolean any = term.options().stream().anyMatch(words::contains);
            if (any == term.negated()) {
                return false;
            }
        }
        return true;
    }

    /** Все слова условия — для сверки с тем, что бывает на свете. */
    public List<String> words() {
        return terms.stream().flatMap(term -> term.options().stream()).toList();
    }

    /** Одно слово условия: варианты и знак. */
    private record Term(boolean negated, List<String> options) {
    }
}
