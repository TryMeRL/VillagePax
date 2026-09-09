package com.villagepax.core.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Тип здания — данные, а не соглашение об именовании.
 * <p>
 * Самый старый долг мода, и он закрыт здесь. До сих пор мод узнавал ратушу
 * по тому, что путь типа кончается на {@code town_hall}, мастерскую профессии
 * — по совпадению имён, а начальные здания деревни — по окончаниям
 * {@code /house} и {@code /farm}. Работало это до первого датапака, который
 * назвал бы своё здание иначе: {@code norman/town_hall_ruins} мод счёл бы
 * ратушей и позволил бы поднимать по ней уровень колонии.
 * <p>
 * Роль — поле <b>обязательное</b>, и это не строгость ради строгости.
 * У необязательного поля с умолчанием DFU глотает ошибку вложенного
 * кодека: описка {@code "role": "castel"} молча становилась бы
 * «просто зданием», и автор датапака искал бы, почему его ратуша
 * не ратуша. Роль решает права здания — она обязана быть названа
 * или названа неверно громко.
 * <p>
 * Теперь роль здания объявлена явно. Файла нет — здание просто здание:
 * оно строится и чинится, но не даёт колонии уровня, не считается жильём
 * и не служит никому мастерской. Это <b>безопасное</b> умолчание: молчание
 * датапака не должно наделять здание правами.
 *
 * @param displayName ключ локализации названия
 * @param role        чем здание служит колонии
 * @param profession  профессия, для которой это мастерская
 * @param starting    ставит ли деревня народа это здание сразу при появлении
 */
public record BuildingType(String displayName, Role role, Optional<Identifier> profession,
                           boolean starting) {

    /**
     * Чем здание служит колонии.
     * <p>
     * Роль, а не набор признаков: у здания она одна, и «ратуша, которая
     * заодно ферма» — это не то, что стоит разрешать данными.
     */
    public enum Role implements Named {

        /** Середина поселения: по её уровню растёт колония. Одна на поселение. */
        TOWN_HALL("town_hall"),

        /** Жильё: кровати в нём считаются местами для жителей. */
        HOME("home"),

        /** Мастерская: в ней работает названная профессия. */
        WORKPLACE("workplace"),

        /** Просто здание. Строится и чинится, прав не даёт. */
        PLAIN("plain");

        public static final Codec<Role> CODEC = EnumCodecs.of(values(), "роль здания");

        private final String id;

        Role(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public static final Codec<BuildingType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(BuildingType::displayName),
            Role.CODEC.fieldOf("role").forGetter(BuildingType::role),
            Identifier.CODEC.optionalFieldOf("profession").forGetter(BuildingType::profession),
            Codec.BOOL.optionalFieldOf("starting", false).forGetter(BuildingType::starting)
    ).apply(instance, BuildingType::new));

    public boolean isTownHall() {
        return role == Role.TOWN_HALL;
    }

    /** Работает ли в этом здании названная профессия. */
    public boolean employs(Identifier candidate) {
        return role == Role.WORKPLACE && profession.filter(candidate::equals).isPresent();
    }
}
