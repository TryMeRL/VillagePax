package com.villagepax.client.screen;

import com.villagepax.VillagePax;
import com.villagepax.screen.BoardNet;
import com.villagepax.screen.BoardView;
import com.villagepax.screen.QuestView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * Доска заданий: деревянный щит, на нём листки на гвоздях.
 * <p>
 * Нарочно без вкладок, рамок и кнопок-плашек — заказчик просил доску,
 * а не сайт. Листок — желтоватая бумага, приколотая гвоздём и чуть
 * перекошенная, как вешают руками; на нём просьба ремесла, сколько уже
 * при себе, награда и печать «Отдать». Листков больше, чем влезает, —
 * доска листается стрелками или колесом.
 */
public class BoardScreen extends Screen {

    private static final Identifier WOOD = new Identifier(VillagePax.MOD_ID, "textures/gui/notice_board.png");
    private static final Identifier PAPER = new Identifier(VillagePax.MOD_ID, "textures/gui/notice_sheet.png");

    private static final int SHEET_W = 96;
    private static final int SHEET_H = 128;
    private static final int GAP = 10;
    private static final int FRAME = 6;
    private static final int PADDING = 12;
    private static final int TITLE_H = 22;

    /** Цвета чернил на бумаге и резьбы на доске. */
    private static final int INK = 0xFF33291B;
    private static final int MUTED = 0xFF6B5E49;
    private static final int GOOD = 0xFF2F6B2A;
    private static final int BAD = 0xFFA33021;
    private static final int CARVED = 0xFFF3E6C8;
    private static final int FRAME_DARK = 0xFF3A2614;
    private static final int FRAME_LIGHT = 0xFF5C3E22;
    private static final int WAX = 0xFF9C2A1E;
    private static final int WAX_HOVER = 0xFFBF3A28;
    private static final int WAX_DULL = 0xFFB8A888;

    private BoardView view;
    private int page;

    /** Где на экране лежат печати и вещи этой отрисовки: для щелчка и подсказки. */
    private final List<Stamp> stamps = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();
    private final List<Words> words = new ArrayList<>();

    private record Stamp(int x, int y, int w, int h, BoardView.Sheet sheet, boolean active) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Icon(int x, int y, int size, ItemStack stack) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + size && my >= y && my < y + size;
        }
    }

    /** Слова, не уместившиеся на листке: по наведению — целиком. */
    private record Words(int x, int y, int w, int h, Text full) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private BoardScreen(BoardView view) {
        super(Text.translatable("villagepax.board.title", view.name()));
        this.view = view;
    }

    public static void open(MinecraftClient client, BoardView view) {
        if (client.currentScreen instanceof BoardScreen board && board.view.village().equals(view.village())) {
            board.view = view;
            board.page = Math.min(board.page, Math.max(0, board.pages() - 1));
            return;
        }
        client.setScreen(new BoardScreen(view));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- раскладка ---

    private int perPage() {
        int room = width - 2 * (FRAME + PADDING) - 40;
        return Math.max(1, Math.min(4, (room + GAP) / (SHEET_W + GAP)));
    }

    private int pages() {
        return Math.max(1, (view.sheets().size() + perPage() - 1) / perPage());
    }

    private int boardWidth() {
        int shown = Math.max(1, Math.min(perPage(), view.sheets().size()));
        return Math.max(260, shown * SHEET_W + (shown - 1) * GAP + 2 * (FRAME + PADDING));
    }

    private int boardHeight() {
        return TITLE_H + SHEET_H + 2 * FRAME + PADDING + 14;
    }

    private int left() {
        return (width - boardWidth()) / 2;
    }

    private int top() {
        return Math.max(4, (height - boardHeight()) / 2);
    }

    // --- отрисовка ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        stamps.clear();
        icons.clear();
        words.clear();

        int x = left();
        int y = top();
        int w = boardWidth();
        int h = boardHeight();

        // Обвязка: тёмный брус с фаской, потом щит из досок.
        context.fill(x, y, x + w, y + h, FRAME_DARK);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, FRAME_LIGHT);
        context.fill(x + 1, y + 1, x + 2, y + h - 1, FRAME_LIGHT);
        for (int ty = y + FRAME; ty < y + h - FRAME; ty += 128) {
            for (int tx = x + FRAME; tx < x + w - FRAME; tx += 128) {
                int tw = Math.min(128, x + w - FRAME - tx);
                int th = Math.min(128, y + h - FRAME - ty);
                context.drawTexture(WOOD, tx, ty, 0, 0, tw, th, 128, 128);
            }
        }

        // Шапка — резьбой по доске: название деревни и ступень доверия.
        Text title = Text.translatable("villagepax.board.title", view.name());
        context.drawText(textRenderer, title, x + (w - textRenderer.getWidth(title)) / 2, y + FRAME + 4,
                CARVED, true);
        Text trust = Text.translatable("villagepax.board.standing",
                Text.translatable(view.standing()), view.reputation());
        context.drawText(textRenderer, trust, x + w - FRAME - 4 - textRenderer.getWidth(trust),
                y + h - FRAME - 11, 0xFFD9C7A0, true);

        List<BoardView.Sheet> sheets = view.sheets();
        int sheetsTop = y + FRAME + TITLE_H;
        if (sheets.isEmpty()) {
            drawEmpty(context, x + (w - SHEET_W) / 2, sheetsTop);
        } else {
            int from = page * perPage();
            int to = Math.min(sheets.size(), from + perPage());
            int shown = to - from;
            int rowWidth = shown * SHEET_W + (shown - 1) * GAP;
            int sx = x + (w - rowWidth) / 2;
            for (int i = from; i < to; i++) {
                drawSheet(context, sheets.get(i), sx + (i - from) * (SHEET_W + GAP), sheetsTop,
                        mouseX, mouseY);
            }
        }

        if (pages() > 1) {
            Text where = Text.translatable("villagepax.board.page", page + 1, pages());
            context.drawText(textRenderer, where, x + FRAME + 4, y + h - FRAME - 11, 0xFFD9C7A0, true);
            arrow(context, x - 14, y + h / 2, "‹", page > 0, mouseX, mouseY);
            arrow(context, x + w + 4, y + h / 2, "›", page < pages() - 1, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);

        for (Icon icon : icons) {
            if (icon.hit(mouseX, mouseY)) {
                context.drawItemTooltip(textRenderer, icon.stack(), mouseX, mouseY);
            }
        }
        for (Words said : words) {
            if (said.hit(mouseX, mouseY)) {
                context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(said.full(), 200),
                        mouseX, mouseY);
            }
        }
        for (Stamp stamp : stamps) {
            if (!stamp.active() && stamp.hit(mouseX, mouseY) && !stamp.sheet().trusted()) {
                context.drawTooltip(textRenderer, Text.translatable("villagepax.board.no_trust.why"),
                        mouseX, mouseY);
            }
        }
    }

    private void arrow(DrawContext context, int x, int y, String glyph, boolean active,
                       int mouseX, int mouseY) {
        boolean hover = active && mouseX >= x && mouseX < x + 10 && mouseY >= y - 8 && mouseY < y + 8;
        context.getMatrices().push();
        context.getMatrices().translate(x, y - 8, 0);
        context.getMatrices().scale(2f, 2f, 1f);
        context.drawText(textRenderer, glyph, 0, 0, active ? (hover ? 0xFFFFFFFF : CARVED) : 0x66F3E6C8, true);
        context.getMatrices().pop();
    }

    /** Наклон листка: от ремесла, чтобы листок не дёргался от кадра к кадру. */
    private static float tilt(BoardView.Sheet sheet) {
        int hash = sheet.giver().hashCode();
        return ((Math.floorMod(hash, 7)) - 3) * 0.6f;
    }

    private void drawEmpty(DrawContext context, int x, int y) {
        context.drawTexture(PAPER, x, y, 0, 0, SHEET_W, SHEET_H, SHEET_W, SHEET_H);
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("villagepax.board.empty"),
                SHEET_W - 16);
        int ty = y + 40;
        for (OrderedText line : lines) {
            context.drawText(textRenderer, line, x + (SHEET_W - textRenderer.getWidth(line)) / 2, ty,
                    MUTED, false);
            ty += 10;
        }
    }

    /**
     * Листок: шапка — ремесло и кто повесил, ниже слова жителя мелко,
     * просьба с вещами, награда и печать внизу.
     */
    private void drawSheet(DrawContext context, BoardView.Sheet sheet, int x, int y,
                           int mouseX, int mouseY) {
        float angle = tilt(sheet);
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + SHEET_W / 2f, y + 4, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        matrices.translate(-SHEET_W / 2f, -4, 0);

        context.drawTexture(PAPER, 0, 0, 0, 0, SHEET_W, SHEET_H, SHEET_W, SHEET_H);

        int inner = SHEET_W - 12;
        int ty = 12;
        Text trade = sheet.title().map(key -> (Text) Text.translatable(key))
                .orElseGet(() -> Text.translatable("villagepax.profession." + sheet.giver().getPath()));
        Text heading = trade.copy().formatted(Formatting.BOLD);
        drawCentered(context, heading, ty, INK, 1f);
        ty += 10;
        drawCentered(context, Text.literal(sheet.author()), ty, MUTED, 0.75f);
        ty += 8;
        context.fill(10, ty, SHEET_W - 10, ty + 1, 0x5533291B);
        ty += 4;

        QuestView.Offer offer = sheet.offer();
        Text said = Text.translatable(offer.dialogue()).formatted(Formatting.ITALIC);
        int saidFrom = ty;
        ty = drawWrapped(context, said, 6, ty, inner, MUTED, 0.75f, 3);
        if (textRenderer.wrapLines(said, (int) (inner / 0.75f)).size() > 3) {
            words.add(new Words(x + 6, y + saidFrom, inner, ty - saidFrom, said));
        }
        ty += 3;

        for (QuestView.Need need : offer.objectives()) {
            Text about = need.item().map(item -> (Text) item.getName())
                    .orElseGet(() -> Text.translatable(need.what().orElse("")));
            Text line = Text.translatable(need.key(), about, String.valueOf(need.have()),
                    String.valueOf(need.need()));
            int textX = 6;
            if (need.item().isPresent()) {
                ItemStack stack = new ItemStack(need.item().get());
                drawIcon(context, stack, 5, ty, x, y, angle);
                textX = 19;
            }
            ty = Math.max(ty + 13, drawWrapped(context, line, textX, ty + 1, SHEET_W - textX - 5,
                    need.enough() ? GOOD : BAD, 0.75f, 2) + 2);
        }

        if (!offer.rewards().isEmpty()) {
            context.drawText(textRenderer, Text.translatable("villagepax.board.reward"), 6, ty, INK, false);
            ty += 10;
            int rx = 6;
            for (QuestView.Prize prize : offer.rewards()) {
                if (prize.isGoods()) {
                    ItemStack stack = new ItemStack(prize.goods().get(), prize.amount());
                    drawIcon(context, stack, rx, ty, x, y, angle);
                    rx += 18;
                } else {
                    Text words = Text.translatable(prize.key(), Text.literal("+" + prize.amount()));
                    matrices.push();
                    matrices.translate(rx, ty + 4, 0);
                    matrices.scale(0.75f, 0.75f, 1f);
                    context.drawText(textRenderer, words, 0, 0, GOOD, false);
                    matrices.pop();
                    rx += (int) (textRenderer.getWidth(words) * 0.75f) + 4;
                }
                if (rx > SHEET_W - 18) {
                    rx = 6;
                    ty += 17;
                }
            }
        }

        // Печать: сургуч, если можно отдать; блёклый оттиск — если нет.
        boolean active = offer.ready() && sheet.trusted();
        Text label = Text.translatable(active ? "villagepax.board.hand_in"
                : sheet.trusted() ? "villagepax.board.short" : "villagepax.board.no_trust");
        int sw = Math.max(54, textRenderer.getWidth(label) + 12);
        int sx = (SHEET_W - sw) / 2;
        int sy = SHEET_H - 20;
        Stamp stamp = new Stamp(x + sx, y + sy, sw, 14, sheet, active);
        boolean hover = active && stamp.hit(mouseX, mouseY);
        context.fill(sx, sy, sx + sw, sy + 14, active ? (hover ? WAX_HOVER : WAX) : WAX_DULL);
        context.fill(sx + 1, sy + 1, sx + sw - 1, sy + 2, 0x33FFFFFF);
        context.drawText(textRenderer, label, sx + (sw - textRenderer.getWidth(label)) / 2, sy + 3,
                active ? 0xFFF7E9D0 : 0xFF6E6250, false);
        stamps.add(stamp);

        matrices.pop();
    }

    /** Вещь на листке: значок с числом, и запомнить, где она, — для подсказки. */
    private void drawIcon(DrawContext context, ItemStack stack, int lx, int ly, int sheetX, int sheetY,
                          float angle) {
        context.drawItem(stack, lx, ly);
        context.drawItemInSlot(textRenderer, stack, lx, ly);
        // Наклон мал, и подсказка ловится по ровной клетке — разница в пиксель.
        icons.add(new Icon(sheetX + lx, sheetY + ly, 16, stack));
    }

    private void drawCentered(DrawContext context, Text text, int y, int colour, float scale) {
        var matrices = context.getMatrices();
        int width = textRenderer.getWidth(text);
        matrices.push();
        matrices.translate(SHEET_W / 2f - width * scale / 2f, y, 0);
        matrices.scale(scale, scale, 1f);
        context.drawText(textRenderer, text, 0, 0, colour, false);
        matrices.pop();
    }

    /** Строки с переносом, не больше {@code most}; возвращает, где кончились. */
    private int drawWrapped(DrawContext context, Text text, int x, int y, int width, int colour,
                            float scale, int most) {
        var matrices = context.getMatrices();
        List<OrderedText> lines = textRenderer.wrapLines(text, (int) (width / scale));
        int shown = Math.min(most, lines.size());
        for (int i = 0; i < shown; i++) {
            matrices.push();
            matrices.translate(x, y, 0);
            matrices.scale(scale, scale, 1f);
            int end = context.drawText(textRenderer, lines.get(i), 0, 0, colour, false);
            // Не уместилось — многоточие, а целиком скажет подсказка.
            if (i == shown - 1 && lines.size() > shown) {
                context.drawText(textRenderer, "…", end, 0, colour, false);
            }
            matrices.pop();
            y += (int) Math.ceil(10 * scale);
        }
        return y;
    }

    // --- ввод ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Stamp stamp : stamps) {
                if (stamp.active() && stamp.hit(mouseX, mouseY)) {
                    handIn(stamp.sheet());
                    return true;
                }
            }
            if (pages() > 1) {
                int y = top() + boardHeight() / 2;
                if (mouseY >= y - 8 && mouseY < y + 8) {
                    if (mouseX >= left() - 14 && mouseX < left() - 4 && page > 0) {
                        turn(-1);
                        return true;
                    }
                    int right = left() + boardWidth() + 4;
                    if (mouseX >= right && mouseX < right + 10 && page < pages() - 1) {
                        turn(1);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (pages() > 1) {
            int next = Math.max(0, Math.min(pages() - 1, page - (int) Math.signum(amount)));
            if (next != page) {
                turn(next - page);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void turn(int by) {
        page += by;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f));
    }

    private void handIn(BoardView.Sheet sheet) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeBlockPos(view.board());
        buf.writeIdentifier(sheet.giver());
        ClientPlayNetworking.send(BoardNet.HAND_IN, buf);
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8f));
    }
}
