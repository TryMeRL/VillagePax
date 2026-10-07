package com.villagepax.sim.life;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Milestones;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.SettlementManager.ChronicleEntry;
import com.villagepax.sim.work.Schedule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Летопись поселения: свадьбы, рождения, смерти, рост, набеги, победы.
 * <p>
 * Всё это мод и раньше говорил в чат — и забывал: через неделю игры никто
 * не помнил, кто на ком женат и откуда пришёл тот набег. Теперь главное
 * записывается, а книгу летописи выдаёт {@code /villagepax chronicle}:
 * у города появляется история, и имена жителей в ней — те самые, что над
 * головами.
 */
public final class Chronicle {

    /** Записей на странице книги: больше не влезет в четырнадцать строк. */
    static final int PER_PAGE = 3;

    /** Что из сказанного вслух ложится в летопись — и какими словами. */
    private static final Map<String, String> FROM_LIFE = Map.of(
            "villagepax.life.wedding", "villagepax.chronicle.wedding",
            "villagepax.life.birth", "villagepax.chronicle.birth",
            "villagepax.life.died", "villagepax.chronicle.died");

    private Chronicle() {
    }

    /** Вписать событие; день — сегодняшний. */
    public static void note(ServerWorld world, Settlement settlement, String key, String... args) {
        SettlementManager.get(world).chronicle(settlement.id(),
                Schedule.dayOf(world.getTimeOfDay()), key, args);
    }

    /** Сказанное вслух о жизни поселения: свадьбу, рождение и смерть — в летопись. */
    static void heard(ServerWorld world, Settlement settlement, String key, String... names) {
        String chronicled = FROM_LIFE.get(key);
        if (chronicled != null) {
            note(world, settlement, chronicled, names);
        }
    }

    /** Ступень поселения — ключом перевода, чтобы книга читалась на языке игрока. */
    public static String level(com.villagepax.sim.SettlementLevel level) {
        return "#" + Milestones.levelKey(level);
    }

    /** Строка летописи: «День N. …». Чистое правило. */
    public static Text line(ChronicleEntry entry) {
        Object[] args = new Object[entry.args().size()];
        for (int i = 0; i < args.length; i++) {
            String arg = entry.args().get(i);
            args[i] = arg.startsWith("#") ? Text.translatable(arg.substring(1)) : Text.literal(arg);
        }
        return Text.translatable("villagepax.chronicle.day", entry.day(),
                Text.translatable(entry.key(), args));
    }

    /** Разложить строки по страницам: по {@link #PER_PAGE} на каждую, через пустую строку. */
    public static List<Text> pages(Text title, List<ChronicleEntry> entries) {
        List<Text> pages = new ArrayList<>();
        MutableText first = title.copy();
        if (entries.isEmpty()) {
            first.append("\n\n").append(Text.translatable("villagepax.chronicle.empty"));
        }
        pages.add(first);
        for (int from = 0; from < entries.size(); from += PER_PAGE) {
            MutableText page = Text.empty();
            for (int i = from; i < Math.min(entries.size(), from + PER_PAGE); i++) {
                if (i > from) {
                    page.append("\n\n");
                }
                page.append(line(entries.get(i)));
            }
            pages.add(page);
        }
        return pages;
    }

    /** Книга летописи поселения. */
    public static ItemStack book(Settlement settlement, List<ChronicleEntry> entries) {
        Text title = Text.translatable("villagepax.chronicle.title", settlement.name(),
                Text.translatable(Milestones.levelKey(settlement.level())),
                Text.translatable("villagepax.culture." + settlement.culture().getPath()),
                settlement.population());
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        String name = settlement.name();
        nbt.putString("title", name.length() > 32 ? name.substring(0, 32) : name);
        nbt.putString("author", chronicler(settlement));
        NbtList written = new NbtList();
        for (Text page : pages(title, entries)) {
            written.add(NbtString.of(Text.Serializer.toJson(page)));
        }
        nbt.put("pages", written);
        return book;
    }

    /** Летописец — старейшина, если он есть; иначе само поселение. */
    private static String chronicler(Settlement settlement) {
        Identifier elder = com.villagepax.sim.Villages.ELDER;
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(elder::equals).isPresent()) {
                return citizen.fullName();
            }
        }
        return settlement.name();
    }
}
