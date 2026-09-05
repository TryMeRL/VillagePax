package com.villagepax.sim;

/**
 * Чем закончилась попытка основать колонию.
 * <p>
 * Отказ несёт ключ локализации и подстановки, а не готовую строку: правила
 * основания живут в общем коде и ничего не должны знать ни о клиенте,
 * ни о языке игрока.
 */
public sealed interface FoundingOutcome {

    record Founded(Settlement settlement) implements FoundingOutcome {
    }

    record Refused(String translationKey, Object[] arguments) implements FoundingOutcome {

        public static Refused of(String translationKey, Object... arguments) {
            return new Refused(translationKey, arguments);
        }
    }

    default boolean isSuccess() {
        return this instanceof Founded;
    }
}
