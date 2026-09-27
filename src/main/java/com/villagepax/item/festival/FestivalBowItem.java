package com.villagepax.item.festival;

import net.minecraft.item.BowItem;

/**
 * Праздничный лук: затейник даёт его стрелку на время состязания.
 * <p>
 * Свой предмет, а не ванильный лук, потому что лук состязания не должен
 * пережить состязание: его не унесёшь с ярмарки и не заложишь в сундук.
 */
public class FestivalBowItem extends BowItem {

    public FestivalBowItem(Settings settings) {
        super(settings);
    }
}
