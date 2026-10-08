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
 * <p>
 * Щелчок по шапке листка срывает его: листок дёргается с гвоздя
 * и улетает вниз, к сумке, а в сумке появляется листок с заданием.
 * На доске остаётся обрывок под гвоздём — и печать сдачи, если всё
 * собрано.
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

    /** Сколько длится срывание листка, в миллисекундах. */
    private static final long TEAR_MS = 520;

    /** Листок, который сейчас срывают: чей и когда начали. */
    private record Tearing(Identifier giver, long since) {
    }

    private Tearing tearing;

    /** Сорванные, о которых сервер ещё не ответил: висят обрывком сразу. */
    private final java.util.Set<Identifier> torn = new java.util.HashSet<>();

    /** Записывать ли, где на экране лежат печати и вещи: летящий листок не ловит щелчков. */
    private boolean recording = true;

    /** Шапки листков: щелчок по ней срывает листок. */
    private final List<Header> headers = new ArrayList<>();

    private record Header(int x, int y, int w, int h, BoardView.Sheet sheet) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

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
            board.torn.clear();
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

    /**
     * Во сколько раз доска крупнее своего чертежа — чтобы заняла экран.
     * <p>
     * Чертёж доски — листки по 96 точек, и на большом экране она была
     * открыткой посреди тьмы, а буквы на листках — мельче травинки.
     * Теперь доска растёт до краёв, шагом в полраза: дробный масштаб
     * размазывал бы пиксельный шрифт.
     */
    private float zoom() {
        int shown = Math.max(1, Math.min(3, view.sheets().size()));
        int needWidth = shown * SHEET_W + (shown - 1) * GAP + 2 * (FRAME + PADDING) + 40;
        float k = Math.min((width - 16f) / needWidth, (height - 16f) / boardHeight());
        return Math.max(1f, Math.min(3f, (float) Math.floor(k * 2) / 2f));
    }

    /** Ширина и высота экрана в точках чертежа доски. */
    private int virtualWidth() {
        return (int) (width / zoom());
    }

    private int virtualHeight() {
        return (int) (height / zoom());
    }

    private int perPage() {
        int room = virtualWidth() - 2 * (FRAME + PADDING) - 40;
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
        return (virtualWidth() - boardWidth()) / 2;
    }

    private int top() {
        return Math.max(4, (virtualHeight() - boardHeight()) / 2);
    }

    // --- отрисовка ---

    @Override
    public void render(DrawContext context, int realX, int realY, float delta) {
        renderBackground(context);
        float zoom = zoom();
        int mouseX = (int) (realX / zoom);
        int mouseY = (int) (realY / zoom);
        context.getMatrices().push();
        context.getMatrices().scale(zoom, zoom, 1f);
        stamps.clear();
        icons.clear();
        words.clear();
        headers.clear();

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
                BoardView.Sheet sheet = sheets.get(i);
                int at = sx + (i - from) * (SHEET_W + GAP);
                if (tearing != null && tearing.giver().equals(sheet.giver())) {
                    drawTaken(context, sheet, at, sheetsTop, mouseX, mouseY);
                    drawTearing(context, sheet, at, sheetsTop, mouseX, mouseY);
                } else if (sheet.taken() || torn.contains(sheet.giver())) {
                    drawTaken(context, sheet, at, sheetsTop, mouseX, mouseY);
                } else {
                    drawSheet(context, sheet, at, sheetsTop, mouseX, mouseY);
                }
            }
            // Подсказка — мелко слева внизу; там же номер страницы, если доска листается.
            if (pages() == 1) {
                Text hint = Text.translatable("villagepax.board.take_hint");
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(x + FRAME + 4, y + h - FRAME - 10, 0);
                matrices.scale(0.75f, 0.75f, 1f);
                context.drawText(textRenderer, hint, 0, 0, 0xCCD9C7A0, false);
                matrices.pop();
            }
        }

        if (pages() > 1) {
            Text where = Text.translatable("villagepax.board.page", page + 1, pages());
            context.drawText(textRenderer, where, x + FRAME + 4, y + h - FRAME - 11, 0xFFD9C7A0, true);
            arrow(context, x - 14, y + h / 2, "‹", page > 0, mouseX, mouseY);
            arrow(context, x + w + 4, y + h / 2, "›", page < pages() - 1, mouseX, mouseY);
        }

        context.getMatrices().pop();
        super.render(context, realX, realY, delta);

        for (Icon icon : icons) {
            if (icon.hit(mouseX, mouseY)) {
                context.drawItemTooltip(textRenderer, icon.stack(), realX, realY);
            }
        }
        for (Words said : words) {
            if (said.hit(mouseX, mouseY)) {
                context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(said.full(), 200),
                        realX, realY);
            }
        }
        for (Stamp stamp : stamps) {
            if (!stamp.active() && stamp.hit(mouseX, mouseY) && !stamp.sheet().trusted()) {
                context.drawTooltip(textRenderer, Text.translatable("villagepax.board.no_trust.why"),
                        realX, realY);
            }
        }
        if (tearing == null) {
            for (Header header : headers) {
                if (header.hit(mouseX, mouseY)) {
                    context.drawTooltip(textRenderer, Text.translatable("villagepax.board.take"), realX, realY);
                }
            }
        }
    }

    /**
     * Летящий листок: дёрнулся с гвоздя, качнулся и ушёл вниз, к сумке,
     * уменьшаясь. Без анимации листок просто пропадал бы — и игрок
     * не понял бы, куда.
     */
    private void drawTearing(DrawContext context, BoardView.Sheet sheet, int x, int y, int mouseX, int mouseY) {
        float p = Math.min(1f, (net.minecraft.util.Util.getMeasuringTimeMs() - tearing.since()) / (float) TEAR_MS);
        if (p >= 1f) {
            finishTearing(sheet);
            return;
        }
        // Первая четверть — рывок вверх и перекос, дальше — падение с разгоном.
        float jerk = p < 0.25f ? p / 0.25f : 1f;
        float fall = p < 0.25f ? 0f : (p - 0.25f) / 0.75f;
        float cx = x + SHEET_W / 2f;
        float cy = y + 4;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(cx + fall * 30f, cy - 4f * jerk + fall * fall * (virtualHeight() - y), 200);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-6f * jerk + 35f * fall));
        float scale = 1f - 0.65f * fall;
        matrices.scale(scale, scale, 1f);
        matrices.translate(-cx, -cy, 0);
        recording = false;
        drawSheet(context, sheet, x, y, mouseX, mouseY);
        recording = true;
        matrices.pop();
    }

    private void finishTearing(BoardView.Sheet sheet) {
        tearing = null;
        torn.add(sheet.giver());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeBlockPos(view.board());
        buf.writeIdentifier(sheet.giver());
        ClientPlayNetworking.send(BoardNet.TAKE, buf);
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ITEM_PICKUP, 1.2f, 0.6f));
    }

    /**
     * Сорванный листок: под гвоздём обрывок бумаги, ниже резьбой —
     * «листок у вас», и печать сдачи прямо на доске, если всё собрано.
     */
    private void drawTaken(DrawContext context, BoardView.Sheet sheet, int x, int y, int mouseX, int mouseY) {
        // Обрывок: полоска бумаги с рваным краем.
        context.drawTexture(PAPER, x + 30, y, 30, 0, SHEET_W - 60, 9, SHEET_W, SHEET_H);
        for (int tx = x + 30; tx < x + SHEET_W - 30; tx += 4) {
            context.fill(tx, y + 9, tx + 2, y + 11, 0xFFE3D3A6);
        }
        context.fill(x + SHEET_W / 2 - 2, y + 2, x + SHEET_W / 2 + 2, y + 6, 0xFF4A4038);
        context.fill(x + SHEET_W / 2 - 1, y + 2, x + SHEET_W / 2 + 1, y + 3, 0xFF8C8170);

        Text trade = sheet.title().map(key -> (Text) Text.translatable(key))
                .orElseGet(() -> Text.translatable("villagepax.profession." + sheet.giver().getPath()));
        int ty = y + 22;
        for (Text line : List.of(trade, Text.translatable("villagepax.board.taken"))) {
            for (OrderedText part : textRenderer.wrapLines(line, SHEET_W - 8)) {
                context.drawText(textRenderer, part, x + (SHEET_W - textRenderer.getWidth(part)) / 2, ty,
                        CARVED, true);
                ty += 11;
            }
        }

        if (sheet.offer().ready() && sheet.trusted()) {
            Text label = Text.translatable("villagepax.board.hand_in");
            int sw = Math.max(54, textRenderer.getWidth(label) + 12);
            int sx = x + (SHEET_W - sw) / 2;
            int sy = y + SHEET_H - 20;
            Stamp stamp = new Stamp(sx, sy, sw, 14, sheet, true);
            boolean hover = stamp.hit(mouseX, mouseY);
            context.fill(sx, sy, sx + sw, sy + 14, hover ? WAX_HOVER : WAX);
            context.fill(sx + 1, sy + 1, sx + sw - 1, sy + 2, 0x33FFFFFF);
            context.drawText(textRenderer, label, sx + (sw - textRenderer.getWidth(label)) / 2, sy + 3,
                    0xFFF7E9D0, false);
            stamps.add(stamp);
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
        Header header = new Header(x, y, SHEET_W, 30, sheet);
        boolean lifted = recording && tearing == null && header.hit(mouseX, mouseY);
        if (recording) {
            headers.add(header);
        }
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + SHEET_W / 2f, y + 4, 0);
        // Под рукой листок приподнимается на гвозде: «меня можно снять».
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lifted ? angle * 2f - 2f : angle));
        matrices.translate(-SHEET_W / 2f, lifted ? -5 : -4, 0);

        if (lifted) {
            context.fill(2, 3, SHEET_W + 2, SHEET_H + 3, 0x55000000);
        }
        context.drawTexture(PAPER, 0, 0, 0, 0, SHEET_W, SHEET_H, SHEET_W, SHEET_H);
        // Гвоздь, на котором листок висит.
        context.fill(SHEET_W / 2 - 2, 2, SHEET_W / 2 + 2, 6, 0xFF4A4038);
        context.fill(SHEET_W / 2 - 1, 2, SHEET_W / 2 + 1, 3, 0xFF8C8170);

        int inner = SHEET_W - 12;
        int ty = 12;
        Text trade = sheet.title().map(key -> (Text) Text.translatable(key))
                .orElseGet(() -> Text.translatable("villagepax.profession." + sheet.giver().getPath()));
        // Без жирного: у «ш» и «ж» штрихи через пиксель, и жирный сдвиг
        // заливает их в сплошной квадрат. Шапку выделяет цвет и черта ниже.
        drawCentered(context, trade, ty, INK, 1f);
        ty += 10;
        drawCentered(context, Text.literal(sheet.author()), ty, MUTED, 0.75f);
        ty += 8;
        context.fill(10, ty, SHEET_W - 10, ty + 1, 0x5533291B);
        ty += 4;

        QuestView.Offer offer = sheet.offer();
        Text said = Text.translatable(offer.dialogue()).formatted(Formatting.ITALIC);
        int saidFrom = ty;
        ty = drawWrapped(context, said, 6, ty, inner, MUTED, 0.75f, 3);
        if (recording && textRenderer.wrapLines(said, (int) (inner / 0.75f)).size() > 3) {
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
        boolean hover = recording && active && stamp.hit(mouseX, mouseY);
        context.fill(sx, sy, sx + sw, sy + 14, active ? (hover ? WAX_HOVER : WAX) : WAX_DULL);
        context.fill(sx + 1, sy + 1, sx + sw - 1, sy + 2, 0x33FFFFFF);
        context.drawText(textRenderer, label, sx + (sw - textRenderer.getWidth(label)) / 2, sy + 3,
                active ? 0xFFF7E9D0 : 0xFF6E6250, false);
        if (recording) {
            stamps.add(stamp);
        }

        matrices.pop();
    }

    /** Вещь на листке: значок с числом, и запомнить, где она, — для подсказки. */
    private void drawIcon(DrawContext context, ItemStack stack, int lx, int ly, int sheetX, int sheetY,
                          float angle) {
        context.drawItem(stack, lx, ly);
        context.drawItemInSlot(textRenderer, stack, lx, ly);
        // Наклон мал, и подсказка ловится по ровной клетке — разница в пиксель.
        if (recording) {
            icons.add(new Icon(sheetX + lx, sheetY + ly, 16, stack));
        }
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
    public boolean mouseClicked(double realX, double realY, int button) {
        double mouseX = realX / zoom();
        double mouseY = realY / zoom();
        if (button == 0 && tearing == null) {
            for (Stamp stamp : stamps) {
                if (stamp.active() && stamp.hit(mouseX, mouseY)) {
                    handIn(stamp.sheet());
                    return true;
                }
            }
            for (Header header : headers) {
                if (header.hit(mouseX, mouseY)) {
                    tearing = new Tearing(header.sheet().giver(), net.minecraft.util.Util.getMeasuringTimeMs());
                    client.getSoundManager().play(PositionedSoundInstance.master(
                            SoundEvents.ITEM_BOOK_PAGE_TURN, 1.6f, 1.0f));
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
        return super.mouseClicked(realX, realY, button);
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
