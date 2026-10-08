package com.villagepax.sim;

import com.villagepax.core.building.BuildingType;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Призвание деревни: чем она славится и что строит раньше прочего.
 * <p>
 * Жалоба заказчика: «почему везде одинаковое развитие». Ступень у деревень
 * выпадала разная, но набор и порядок зданий шёл по списку народа, и две
 * деревни одной ступени стояли близнецами: те же мастерские в том же
 * порядке. Теперь у каждой деревни своё призвание — по её опознавателю,
 * то есть навсегда: пахари сеют больше полей и рубят лес, торговцы
 * ставят рынок и гильдию, стражи — башню, мастера — пивоварню и ткацкую,
 * богомольцы — храм. Остальное деревня строит в своём, а не общем порядке.
 */
public enum Calling {

    FARMERS("farmers", List.of("farmer", "lumberjack")),
    TRADERS("traders", List.of("merchant", "guildmaster")),
    WARDENS("wardens", List.of("guard", "guildmaster")),
    CRAFTERS("crafters", List.of("brewer", "weaver", "builder")),
    PILGRIMS("pilgrims", List.of());

    /** Слова в имени здания, по которым узнают святилище. */
    private static final List<String> HOLY = List.of("chapel", "shrine", "temple");

    private final String id;
    private final List<String> trades;

    Calling(String id, List<String> trades) {
        this.id = id;
        this.trades = trades;
    }

    public String id() {
        return id;
    }

    /** Ключ строки «славится …». */
    public String key() {
        return "villagepax.calling." + id;
    }

    /** Призвание деревни: от её опознавателя, а значит, навсегда. */
    public static Calling of(UUID village) {
        int mixed = village.hashCode();
        mixed ^= mixed >>> 16;
        return values()[Math.floorMod(mixed * 0x45D9F3B, values().length)];
    }

    /** Любит ли деревня с этим призванием такое здание. */
    public boolean favours(Identifier type, BuildingType kind) {
        if (this == PILGRIMS) {
            String path = type.getPath();
            return HOLY.stream().anyMatch(path::contains);
        }
        return kind.profession().filter(profession -> trades.contains(profession.getPath())).isPresent();
    }
}
