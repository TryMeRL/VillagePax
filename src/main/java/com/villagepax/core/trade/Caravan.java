package com.villagepax.core.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.sim.ItemTally;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Обоз, пришедший в гости: чужой торговец с телегой товара.
 * <p>
 * Обещание из плана: «в фазе с караванами привоз станет настоящим обозом,
 * который можно перехватить или защитить». Здесь исполнена та его половина,
 * которую игрок может <b>встретить</b>: деревня народа посылает торговца
 * к колонии игрока, тот стоит день у ратуши, торгует своим товаром — и
 * уходит. Убить его можно, и тогда товар с монетой рассыплется под ноги.
 * <p>
 * <b>Дорога — это время, а не путь.</b> Между деревней и колонией лежат
 * сотни блоков незагруженных чанков, и вести по ним живого моба нельзя
 * ни дёшево, ни честно: половина дороги просто не существует, пока туда
 * не придёт игрок. Поэтому обоз «в пути» — это запись с днём прихода,
 * а телом он становится там, где его увидят. Это то же правило, на котором
 * стоит весь мод: нет тела — нет работы.
 * <p>
 * Товар лежит <b>в самом обозе</b>, а не на складе деревни. Поэтому у него
 * можно скупить всё — и тогда торговать станет нечем до следующего раза, —
 * и поэтому его есть смысл грабить.
 * <p>
 * Груз считается {@link ItemTally}, а не списком стопок, и это не мелочь.
 * Кодек стопки тянет за собой реестр предметов, а кодек поселения читается
 * в том числе <b>модульным тестом без запущенной игры</b>: список стопок
 * ронял его на месте. Счёт «чего и сколько» обозу и достаточен — он везёт
 * зерно и камень, а не именные мечи.
 *
 * @param id       опознаватель: по нему обоз находят и клиент, и сервер
 * @param home     деревня, которая его послала: у неё доверие и цены
 * @param culture  народ этой деревни — на случай, если деревню снесли
 * @param stands   где он стоит у колонии
 * @param cargo    что привёз на продажу
 * @param purse    сколько монеты привёз на закупки, в медяках
 * @param leavesOn день, в который уйдёт
 */
public record Caravan(UUID id, UUID home, Identifier culture, BlockPos stands,
                      ItemTally cargo, int purse, long leavesOn) {

    public static final Codec<Caravan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(Caravan::id),
            Uuids.STRING_CODEC.fieldOf("home").forGetter(Caravan::home),
            Identifier.CODEC.fieldOf("culture").forGetter(Caravan::culture),
            BlockPos.CODEC.fieldOf("stands").forGetter(Caravan::stands),
            ItemTally.CODEC.optionalFieldOf("cargo", new ItemTally()).forGetter(Caravan::cargo),
            Codec.INT.optionalFieldOf("purse", 0).forGetter(Caravan::purse),
            Codec.LONG.fieldOf("leaves_on").forGetter(Caravan::leavesOn)
    ).apply(instance, Caravan::new));

    /**
     * Тот же обоз с другим содержимым телеги.
     * <p>
     * Заменой, а не правкой: {@link Caravan} — запись, и это то же
     * решение, что у здания и жителя. Менять неизменяемое нельзя,
     * а пересобрать дешевле, чем однажды разойтись с сохранением.
     */
    public Caravan withCargo(ItemTally fresh, int coins) {
        return new Caravan(id, home, culture, stands, fresh, coins, leavesOn);
    }

    /** Есть ли ещё чем торговать: пустой обоз уходит раньше срока. */
    public boolean hasAnything() {
        return purse > 0 || !cargo.isEmpty();
    }
}
