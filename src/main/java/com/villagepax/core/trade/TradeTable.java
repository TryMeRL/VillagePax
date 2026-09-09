package com.villagepax.core.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Чем народ торгует — данными, как всё остальное.
 * <p>
 * Список сделок, а не расчёт цены по формуле. Формула была бы короче кодом,
 * но её пришлось бы объяснять игроку: почему брёвна стоят столько, а стекло
 * столько. Список отвечает на это сам — так решил этот народ, — и автор
 * датапака правит цену одной строкой.
 * <p>
 * Торговля заменяет собой <b>заглушку «привоз со стороны»</b>. Деревня не
 * умеет ни выплавить стекло, ни соткать кровать, и до сих пор ей просто
 * докладывали на склад то, чего не хватало стройке. Теперь у привоза есть
 * цена: деревня платит за него монетой, монета конечна, и игрок может
 * оказаться поставщиком выгоднее обоза.
 *
 * @param culture чей это стол торга
 * @param sells   что деревня продаёт игроку
 * @param buys    что деревня покупает у игрока
 */
public record TradeTable(Identifier culture, List<Deal> sells, List<Deal> buys) {

    /**
     * Одна сделка: столько-то предмета за столько-то изумрудов.
     * <p>
     * Изумруды, а не своя монета. Своя была бы красивее для народа, но
     * игроку пришлось бы сперва её где-то взять, а взять было бы негде,
     * кроме этой же торговли, — круг замкнулся бы. Изумруд игрок понимает
     * без объяснений: он уже торговал им с жителями ванильной деревни.
     * <p>
     * Порог доверия у сделки <b>необязателен</b>, и здесь это безопасно:
     * молчание датапака означает «торгуют со всяким», то есть меньше прав,
     * а не больше. Так у ступеней доверия появляется смысл и в торге —
     * инструмент чужаку не продадут.
     *
     * @param item          чем торгуют
     * @param count         сколько предметов за одну сделку
     * @param price         во сколько изумрудов обходится сделка
     * @param minReputation с какого доверия сделка вообще предлагается
     */
    public record Deal(Item item, int count, int price, int minReputation) {

        public static final Codec<Deal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Registries.ITEM.getCodec().fieldOf("item").forGetter(Deal::item),
                Codec.intRange(1, 64).fieldOf("count").forGetter(Deal::count),
                Codec.intRange(1, 64).fieldOf("price").forGetter(Deal::price),
                Codec.intRange(0, 1_000).optionalFieldOf("min_reputation", 0)
                        .forGetter(Deal::minReputation)
        ).apply(instance, Deal::new));

        /** Открыта ли сделка при таком доверии. */
        public boolean open(int reputation) {
            return reputation >= minReputation;
        }
    }

    public static final Codec<TradeTable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("culture").forGetter(TradeTable::culture),
            Deal.CODEC.listOf().optionalFieldOf("sells", List.of()).forGetter(TradeTable::sells),
            Deal.CODEC.listOf().optionalFieldOf("buys", List.of()).forGetter(TradeTable::buys)
    ).apply(instance, TradeTable::new));

    /**
     * По какой цене деревня скупает этот предмет.
     * <p>
     * Нужно не только игроку: по этой же цене деревня платит обозу за
     * материалы для своей стройки. Цена одна на обе стороны намеренно —
     * иначе игрок, продав деревне стекло, увидел бы, что обозу оно
     * досталось дешевле, и справедливо счёл бы себя обманутым.
     */
    public Optional<Deal> buyRate(Item item) {
        return buys.stream().filter(deal -> deal.item() == item).findFirst();
    }
}
