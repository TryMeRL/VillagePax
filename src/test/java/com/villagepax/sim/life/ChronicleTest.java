package com.villagepax.sim.life;

import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.SettlementManager.ChronicleEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChronicleTest {

    private static TranslatableTextContent content(Object text) {
        return (TranslatableTextContent) ((Text) text).getContent();
    }

    @Test
    void aLineIsTheDayAndTheEventWithLevelsTranslated() {
        Text line = Chronicle.line(new ChronicleEntry(12, "villagepax.chronicle.level",
                List.of("#villagepax.level.town")));
        assertEquals("villagepax.chronicle.day", content(line).getKey());
        assertEquals(12L, content(line).getArgs()[0]);
        TranslatableTextContent event = content(content(line).getArgs()[1]);
        assertEquals("villagepax.chronicle.level", event.getKey());
        assertEquals("villagepax.level.town", content(event.getArgs()[0]).getKey());
    }

    @Test
    void namesStayNames() {
        Text line = Chronicle.line(new ChronicleEntry(3, "villagepax.chronicle.wedding",
                List.of("Жан", "Мари")));
        TranslatableTextContent event = content(content(line).getArgs()[1]);
        assertEquals("Жан", ((Text) event.getArgs()[0]).getString());
    }

    @Test
    void pagesHoldATitleAndThreeEntriesEach() {
        List<ChronicleEntry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.add(new ChronicleEntry(i, "villagepax.chronicle.raid_repelled", List.of("Кан")));
        }
        assertEquals(1 + 3, Chronicle.pages(Text.literal("x"), entries).size());
        assertEquals(1, Chronicle.pages(Text.literal("x"), List.of()).size());
    }

    @Test
    void theChronicleKeepsTheLatestAndSurvivesASave() {
        SettlementManager manager = new SettlementManager();
        UUID village = UUID.randomUUID();
        for (int day = 0; day < SettlementManager.CHRONICLE_LENGTH + 5; day++) {
            manager.chronicle(village, day, "villagepax.chronicle.wedding", "Жан", "Мари");
        }
        List<ChronicleEntry> kept = manager.chronicleOf(village);
        assertEquals(SettlementManager.CHRONICLE_LENGTH, kept.size());
        assertEquals(5L, kept.get(0).day());

        SettlementManager loaded = SettlementManager.fromNbt(manager.writeNbt(new NbtCompound()));
        assertEquals(kept, loaded.chronicleOf(village));
    }
}
