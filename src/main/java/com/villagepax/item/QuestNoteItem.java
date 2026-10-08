package com.villagepax.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.VillagePax;
import com.villagepax.screen.QuestView;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Листок, сорванный с доски заданий.
 * <p>
 * Доска показывает просьбы, пока игрок стоит перед ней; уйдя, он
 * помнил их только сам. Листок уносит просьбу с собой: в подсказке —
 * кто просил, слова просьбы, что принести и сколько уже при себе
 * (это дописывает клиент), что дадут и где сдавать. Сдача — как прежде,
 * у доски или у самого жителя, и сданный листок забирают.
 * <p>
 * Листок — память, а не ключ: без него просьбу сдать можно, с ним —
 * не обойти правил. Всё, что в нём лежит, — снимок в миг срывания.
 * Подсказку к нему пишет клиент ({@code VillagePaxClient}): там же
 * считается, сколько просимого уже в сумке.
 */
public class QuestNoteItem extends Item {

    private static final String TAG = "note";

    /** Ширина строки подсказки в буквах: длинные слова жителя переносятся. */
    private static final int LINE = 38;

    /**
     * Что записано на листке.
     *
     * @param village     чья доска
     * @param villageName как деревня звалась в тот день
     * @param giver       ремесло, которое просит
     * @param author      имя жителя, повесившего листок
     * @param title       ключ имени ремесла у народа; пусто — общее имя
     * @param quest       опознаватель просьбы: по нему листок узнаётся при сдаче
     * @param day         день, когда сорван: поручения живут один день
     * @param errand      поручение ли это, а не писаная просьба
     * @param offer       просьба, награда и слова — как на доске
     */
    public record Note(UUID village, String villageName, Identifier giver, String author,
                       Optional<String> title, Identifier quest, long day, boolean errand,
                       QuestView.Offer offer) {

        public static final Codec<Note> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Uuids.STRING_CODEC.fieldOf("village").forGetter(Note::village),
                Codec.STRING.fieldOf("village_name").forGetter(Note::villageName),
                Identifier.CODEC.fieldOf("giver").forGetter(Note::giver),
                Codec.STRING.fieldOf("author").forGetter(Note::author),
                Codec.STRING.optionalFieldOf("title").forGetter(Note::title),
                Identifier.CODEC.fieldOf("quest").forGetter(Note::quest),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Note::day),
                Codec.BOOL.optionalFieldOf("errand", false).forGetter(Note::errand),
                QuestView.Offer.CODEC.fieldOf("offer").forGetter(Note::offer)
        ).apply(instance, Note::new));

        /** Имя ремесла так, как его зовёт народ. */
        public Text trade() {
            return title.map(key -> (Text) Text.translatable(key))
                    .orElseGet(() -> Text.translatable("villagepax.profession." + giver.getPath()));
        }
    }

    public QuestNoteItem(Settings settings) {
        super(settings);
    }

    /** Листок с этой записью. */
    public static ItemStack of(Note note) {
        ItemStack stack = new ItemStack(ModItems.QUEST_NOTE);
        Note.CODEC.encodeStart(NbtOps.INSTANCE, note)
                .resultOrPartial(error -> VillagePax.LOGGER.warn("Листок не записался: {}", error))
                .ifPresent(nbt -> stack.getOrCreateNbt().put(TAG, nbt));
        return stack;
    }

    /** Что записано на листке; пусто — не листок или запись испорчена. */
    public static Optional<Note> read(ItemStack stack) {
        if (!stack.isOf(ModItems.QUEST_NOTE) || !stack.hasNbt()
                || !stack.getNbt().contains(TAG, NbtElement.COMPOUND_TYPE)) {
            return Optional.empty();
        }
        NbtCompound nbt = stack.getNbt().getCompound(TAG);
        return Note.CODEC.parse(NbtOps.INSTANCE, nbt).result();
    }

    /** Листок ли это той самой просьбы. */
    public static boolean isFor(ItemStack stack, UUID village, Identifier quest) {
        return read(stack).filter(note -> note.village().equals(village) && note.quest().equals(quest))
                .isPresent();
    }

    /** Есть ли в сумке листок этой просьбы. */
    public static boolean carried(Inventory carried, UUID village, Identifier quest) {
        for (int slot = 0; slot < carried.size(); slot++) {
            if (isFor(carried.getStack(slot), village, quest)) {
                return true;
            }
        }
        return false;
    }

    /** Забрать сданный листок: просьба исполнена, и бумага больше не нужна. */
    public static void tearUp(Inventory carried, UUID village, Identifier quest) {
        for (int slot = 0; slot < carried.size(); slot++) {
            if (isFor(carried.getStack(slot), village, quest)) {
                carried.setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Text getName(ItemStack stack) {
        return read(stack)
                .map(note -> (Text) Text.translatable("item.villagepax.quest_note.of", note.trade(),
                        note.villageName()))
                .orElseGet(() -> super.getName(stack));
    }

    /** Перенос по словам: подсказка сама строки не переносит. */
    public static List<String> wrap(String said) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : said.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > LINE) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
