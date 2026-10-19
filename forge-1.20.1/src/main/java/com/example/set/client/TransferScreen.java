package com.example.set.client;

import com.example.set.ModNetwork;
import com.example.set.SelectiveEnchantmentTransfer;
import com.example.set.SetConfig;
import com.example.set.menu.TransferMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pantalla de transferencia con estilo "arcano": panel oscuro, acentos violeta y dorado, todo dibujado a mano. */
public class TransferScreen extends AbstractContainerScreen<TransferMenu> {
    private static final int W = 224, H = 174;
    private static final int RESULT_X = 113;
    private static final int LIST_X = 8, LIST_Y = 67, LIST_W = 208, ROWS = 4, ROW_H = 18, LIST_H = ROWS * ROW_H + 2;
    private static final int SB_W = 5;
    // botones (coordenadas relativas al panel)
    private static final int TB_X = 8, TB_Y = 146, TB_W = 124, TB_H = 20;     // Transferir
    private static final int CB_X = 136, CB_Y = 146, CB_W = 80, CB_H = 20;    // Cancelar
    private static final int SETB_X = 168, SETB_Y = 4, SETB_W = 48, SETB_H = 12; // Ajustes
    private static final int MODE_X = 148, MODE_Y = 52, MODE_W = 68, MODE_H = 12; // Mover | Copiar

    // paleta
    private static final int OUTLINE = 0xFF08050F, VIOLET = 0xFF7A56D0, VIOLET_DK = 0xFF3A2A66, VIOLET_HI = 0xFF9B7BEA;
    private static final int BG_TOP = 0xFF241A40, BG_BOT = 0xFF120D22, WELL = 0xFF120D22, SLOT_BG = 0xFF171127;
    private static final int GOLD = 0xFFFFD36B, GOLD_DK = 0xFFB8862B, TEXT_DIM = 0xFFA89BD6, TEXT_SOFT = 0xFFC9BDF0;
    private static final int GREEN = 0xFF5CFFB0, RED = 0xFFFF6B6B;
    private static final int[][] CHECK = {{1, 3}, {2, 4}, {3, 5}, {4, 4}, {5, 3}, {6, 2}, {7, 1}};

    private final Map<String, Boolean> selected = new LinkedHashMap<>(); // id -> true = copiar, false = mover (el modo con el que se marco)
    private final Map<String, Integer> splitIdx = new HashMap<>();         // id -> reparto elegido entre libros
    private boolean curCopy = false;                                       // modo con el que se marcan los SIGUIENTES encantamientos
    private int scroll = 0;
    private boolean draggingScrollbar = false;

    // estado que se recalcula en cada fotograma
    private List<Map.Entry<Enchantment, Integer>> enchants = new ArrayList<>();
    private Map<Enchantment, Integer> moved = new LinkedHashMap<>();    // los que se mueven
    private Map<Enchantment, Integer> copied = new LinkedHashMap<>();   // los que se copian
    private Map<Enchantment, int[]> partsMap = new LinkedHashMap<>();   // niveles por libro de cada elegido
    private int bookCount = 0;      // libros normales disponibles
    private int maxParts = 1;       // maximo de libros en que se puede repartir un encantamiento
    private int booksNeeded = 0;    // libros de salida que haran falta
    private boolean keepOneRule = false;   // origen = libro y se moverian todos sin repartir (saldria el mismo libro)
    private boolean valid = false;
    private int cost = 0;
    private int wear = 0;           // puntos de durabilidad que costaria la copia
    private boolean wearOk = true;  // el objeto sobreviviria al desgaste
    private ItemStack resultStack = ItemStack.EMPTY;

    public TransferScreen(TransferMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    // ---------- textos ----------
    private static MutableComponent trc(String key, Object... args) {
        return Component.translatable("gui." + SelectiveEnchantmentTransfer.MODID + "." + key, args);
    }
    private static String tr(String key, Object... args) { return trc(key, args).getString(); }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.1f", v); }

    // ---------- primitivas de dibujo (lo unico que cambia entre versiones de Minecraft) ----------
    private void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int c) { g.fill(x1, y1, x2, y2, c); }
    private void txt(GuiGraphics g, String s, int x, int y, int c, boolean sh) { g.drawString(font, s, x, y, c, sh); }
    private void push(GuiGraphics g) { g.pose().pushPose(); }
    private void pop(GuiGraphics g) { g.pose().popPose(); }
    private void move(GuiGraphics g, float x, float y) { g.pose().translate(x, y, 0.0F); }
    private void zoom(GuiGraphics g, float s) { g.pose().scale(s, s, 1.0F); }
    private void item(GuiGraphics g, ItemStack s, int x, int y) { g.renderItem(s, x, y); }
    private void tipc(GuiGraphics g, List<Component> tip, int mx, int my) { g.renderComponentTooltip(font, tip, mx, my); }
    private void tipItem(GuiGraphics g, ItemStack s, int mx, int my) { g.renderTooltip(font, s, mx, my); }
    private void tipDefault(GuiGraphics g, int mx, int my) { renderTooltip(g, mx, my); }

    // ---------- estado ----------
    private boolean creative() {
        return minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }
    private int playerLevels() {
        return minecraft != null && minecraft.player != null ? minecraft.player.experienceLevel : 0;
    }
    private boolean canPay() { return creative() || playerLevels() >= cost; }
    /** Modo de una fila: el suyo si ya esta elegida; si no, el modo actual (el de los siguientes que se marquen). */
    private boolean rowCopy(Enchantment e) {
        Boolean m = selected.get(TransferMenu.idOf(e));
        return m != null ? m.booleanValue() : curCopy;
    }
    /** Se puede marcar ahora (con el modo actual). */
    private boolean selectable(Enchantment e) { return TransferMenu.denyReason(menu.getSettings(), e, menu.getSource(), curCopy) == 0; }
    private boolean canTransfer() { return valid && canPay() && wearOk; }

    private int[] partsFor(Map.Entry<Enchantment, Integer> e) {
        List<int[]> ps = TransferMenu.partitions(e.getValue(), maxParts);
        int i = splitIdx.getOrDefault(TransferMenu.idOf(e.getKey()), 0);
        if (i < 0 || i >= ps.size()) i = 0;
        return ps.get(i);
    }
    private static String partsLabel(int[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) { if (i > 0) sb.append('+'); sb.append(parts[i]); }
        return sb.toString();
    }

    private void updateState() {
        SetConfig.Settings st = menu.getSettings();
        if (!st.allowCopy) { curCopy = false; selected.values().removeIf(c -> c.booleanValue()); }
        enchants = menu.getEnchantments();
        bookCount = menu.getBookCount();
        maxParts = Math.max(1, Math.min(TransferMenu.MAX_BOOKS, bookCount));
        // los ya elegidos conservan su modo aunque se cambie el de arriba; solo se quitan si ya no existen o el servidor los bloquea
        selected.entrySet().removeIf(en -> {
            Map.Entry<Enchantment, Integer> found = null;
            for (Map.Entry<Enchantment, Integer> e : enchants) if (TransferMenu.idOf(e.getKey()).equals(en.getKey())) found = e;
            return found == null || TransferMenu.denyReason(st, found.getKey(), menu.getSource(), en.getValue().booleanValue()) != 0;
        });
        scroll = Math.max(0, Math.min(Math.max(0, enchants.size() - ROWS), scroll));

        moved = new LinkedHashMap<>();
        copied = new LinkedHashMap<>();
        partsMap = new LinkedHashMap<>();
        booksNeeded = 0;
        for (Map.Entry<Enchantment, Integer> e : enchants) {
            Boolean mode = selected.get(TransferMenu.idOf(e.getKey()));
            if (mode == null) continue;
            int[] parts = partsFor(e);
            partsMap.put(e.getKey(), parts);
            booksNeeded = Math.max(booksNeeded, parts.length);
            if (mode.booleanValue()) copied.put(e.getKey(), e.getValue()); else moved.put(e.getKey(), e.getValue());
        }
        boolean any = !moved.isEmpty() || !copied.isEmpty();
        cost = TransferMenu.totalCost(st, moved, copied);
        wear = TransferMenu.wearPoints(st, menu.getSource(), copied);
        wearOk = copied.isEmpty() || TransferMenu.canPayWear(st, menu.getSource(), copied);

        boolean sourceIsBook = menu.getSource().is(Items.ENCHANTED_BOOK);
        keepOneRule = sourceIsBook && !moved.isEmpty() && moved.size() >= enchants.size() && booksNeeded < 2;
        valid = any && !keepOneRule && booksNeeded <= bookCount;

        if (!any) {
            resultStack = ItemStack.EMPTY;
        } else {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            for (Map.Entry<Enchantment, int[]> e : partsMap.entrySet())
                EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(e.getKey(), e.getValue()[0]));
            book.setCount(Math.max(1, booksNeeded));
            resultStack = book;
        }
    }

    // ---------- distribucion de una fila (la usan el dibujo y el raton) ----------
    private int rowW() { return LIST_W - 2 - (needsScroll() ? SB_W + 2 : 0); }
    /** {px del precio, x de la etiqueta, ancho de la etiqueta, x del selector de reparto, ancho del selector (0 = no hay)}. */
    private int[] layout(Map.Entry<Enchantment, Integer> e) {
        int rx = leftPos + LIST_X + 1;
        boolean on = selected.containsKey(TransferMenu.idOf(e.getKey()));
        boolean rc = rowCopy(e.getKey());
        int pw = font.width(tr("level_short", TransferMenu.costOf(menu.getSettings(), e.getKey(), e.getValue(), rc)));
        int px = rx + rowW() - 6 - pw;
        if (!on) return new int[]{px, px - 3, 0, px - 3, 0};
        int tagW = (int) (font.width(tr(rc ? "mode_copy" : "mode_move")) * 0.75F) + 1;
        int tagX = px - 7 - tagW;
        int chipW = 0, chipX = tagX;
        if (maxParts >= 2 && TransferMenu.partitions(e.getValue(), maxParts).size() > 1) {
            chipW = (int) (font.width(partsLabel(partsFor(e))) * 0.75F) + 8;
            chipX = tagX - 4 - chipW;
        }
        return new int[]{px, tagX, tagW, chipX, chipW};
    }
    private boolean overSplit(int row, double mx, double my) {
        if (row < 0) return false;
        Map.Entry<Enchantment, Integer> e = enchants.get(row);
        if (!selected.containsKey(TransferMenu.idOf(e.getKey()))) return false;
        int[] lay = layout(e);
        int ry = topPos + LIST_Y + 1 + (row - scroll) * ROW_H;
        return lay[4] > 0 && inBox(mx, my, lay[3], ry + 3, lay[4], 12);
    }

    // ---------- render ----------
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        updateState();
        renderBackground(g);
        super.render(g, mx, my, partialTick);

        int x = leftPos, y = topPos;
        SetConfig.Settings st = menu.getSettings();
        int row = rowAt(mx, my);
        boolean overMove = st.allowCopy && inBox(mx, my, x + MODE_X, y + MODE_Y, MODE_W / 2, MODE_H);
        boolean overCopy = st.allowCopy && inBox(mx, my, x + MODE_X + MODE_W / 2, y + MODE_Y, MODE_W / 2, MODE_H);
        if (overMove || overCopy) {
            List<Component> tip = new ArrayList<>();
            tip.add(trc(overCopy ? "mode_copy" : "mode_move").withStyle(ChatFormatting.WHITE));
            tip.add(trc(overCopy ? "mode_copy_tip" : "mode_move_tip", fmt(st.copyCostMultiplier)).withStyle(ChatFormatting.GRAY));
            tip.add(trc("mode_all_tip").withStyle(ChatFormatting.DARK_GRAY));
            tipc(g, tip, mx, my);
        } else if (inBox(mx, my, x + 140, y + 20, 76, 20)) {
            List<Component> tip = new ArrayList<>();
            tip.add(trc("tooltip.cost", cost));
            if (!creative()) tip.add(trc("have", playerLevels()).withStyle(ChatFormatting.GRAY));
            if (booksNeeded > 1) tip.add(trc("books_needed", booksNeeded, bookCount).withStyle(ChatFormatting.GRAY));
            if (wear > 0) {
                ItemStack src = menu.getSource();
                tip.add(trc("tooltip.wear_total", wear, Math.max(0, src.getMaxDamage() - src.getDamageValue() - wear)).withStyle(wearOk ? ChatFormatting.GOLD : ChatFormatting.RED));
            }
            tipc(g, tip, mx, my);
        } else if (overSplit(row, mx, my)) {
            Map.Entry<Enchantment, Integer> e = enchants.get(row);
            int[] parts = partsFor(e);
            List<Component> tip = new ArrayList<>();
            tip.add(trc("tooltip.split_title").withStyle(ChatFormatting.WHITE));
            tip.add(trc("tooltip.split_info", partsLabel(parts), parts.length).withStyle(ChatFormatting.GRAY));
            tipc(g, tip, mx, my);
        } else if (row >= 0) {
            Map.Entry<Enchantment, Integer> e = enchants.get(row);
            boolean on = selected.containsKey(TransferMenu.idOf(e.getKey()));
            boolean rc = rowCopy(e.getKey());
            List<Component> tip = new ArrayList<>();
            tip.add(e.getKey().getFullname(e.getValue()));
            tip.add(trc("tooltip.cost", TransferMenu.costOf(st, e.getKey(), e.getValue(), rc)).withStyle(ChatFormatting.GOLD));
            int deny = on ? 0 : TransferMenu.denyReason(st, e.getKey(), menu.getSource(), rc);
            if (on) {
                tip.add(trc(rc ? "mode_copy" : "mode_move").withStyle(rc ? ChatFormatting.GOLD : ChatFormatting.GREEN));
                tip.add(trc("tooltip.untoggle").withStyle(ChatFormatting.GRAY));
            } else if (deny == 0) {
                tip.add(trc("tooltip.toggle").withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(trc(rc ? "deny.cant_copy" : "deny.cant_move").withStyle(ChatFormatting.RED));
                if (deny == 2) tip.add(trc("cant_copy").withStyle(ChatFormatting.GRAY));
                else if (deny == 3) tip.add(trc("deny.durable").withStyle(ChatFormatting.GRAY));
            }
            int wearPct = TransferMenu.effectiveWear(st, e.getKey(), menu.getSource());
            if (rc && wearPct > 0) tip.add(trc("tooltip.wear", wearPct).withStyle(ChatFormatting.GOLD));
            tipc(g, tip, mx, my);
        } else if (!resultStack.isEmpty() && inBox(mx, my, x + RESULT_X - 1, y + TransferMenu.SLOT_Y - 1, 18, 18)) {
            if (booksNeeded > 1) {
                List<Component> tip = new ArrayList<>();
                tip.add(trc("tooltip.books_title", booksNeeded).withStyle(ChatFormatting.WHITE));
                for (int i = 0; i < booksNeeded; i++) {
                    StringBuilder names = new StringBuilder();
                    for (Map.Entry<Enchantment, int[]> pe : partsMap.entrySet()) {
                        if (pe.getValue().length <= i) continue;
                        if (names.length() > 0) names.append(", ");
                        names.append(pe.getKey().getFullname(pe.getValue()[i]).getString());
                    }
                    tip.add(Component.literal(tr("tooltip.book_n", i + 1) + ": " + names).withStyle(ChatFormatting.GRAY));
                }
                tipc(g, tip, mx, my);
            } else {
                tipItem(g, resultStack, mx, my);
            }
        } else {
            tipDefault(g, mx, my);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos;
        SetConfig.Settings st = menu.getSettings();
        double t = (System.currentTimeMillis() % 100000L) / 1000.0D;
        float pulse = (float) ((Math.sin(t * 3.2D) + 1.0D) / 2.0D);
        boolean any = !moved.isEmpty() || !copied.isEmpty();

        panel(g, x, y, W, H);

        // cabecera
        txt(g, tr("title"), x + 10, y + 7, GOLD, true);
        rect(g, x + 8, y + 18, x + 52, y + 19, GOLD_DK);
        rect(g, x + 52, y + 18, x + W - 8, y + 19, VIOLET_DK);
        button(g, x + SETB_X, y + SETB_Y, SETB_W, SETB_H, tr("settings"), true, inBox(mx, my, x + SETB_X, y + SETB_Y, SETB_W, SETB_H), false);

        // fila superior: objeto + libro -> resultado
        int sy = y + TransferMenu.SLOT_Y;
        slot(g, x + TransferMenu.TOOL_X, sy, VIOLET, 0);
        slot(g, x + TransferMenu.BOOK_X, sy, VIOLET, 0);
        int resBorder = valid ? lerp(GOLD_DK, 0xFFFFE9A8, pulse) : VIOLET_DK;
        slot(g, x + RESULT_X, sy, resBorder, valid ? pulse : 0);
        txt(g, "+", x + 42, sy + 4, VIOLET_HI, false);
        arrow(g, x + 88, sy + 8, valid ? lerp(GOLD_DK, GOLD, pulse) : VIOLET_DK);
        small(g, tr("tool"), x + TransferMenu.TOOL_X + 8, y + 41, TEXT_DIM, true);
        small(g, tr("book"), x + TransferMenu.BOOK_X + 8, y + 41, TEXT_DIM, true);
        small(g, tr("result"), x + RESULT_X + 8, y + 41, TEXT_DIM, true);
        if (!resultStack.isEmpty()) item(g, resultStack, x + RESULT_X, sy);
        else txt(g, "?", x + RESULT_X + 8 - font.width("?") / 2, sy + 4, VIOLET, false);

        // caja de coste
        int cx = x + 140, cy = y + 20;
        String line1;
        int color;
        if (!any) { line1 = tr("cost", "-"); color = 0xFF9A90BE; }
        else if (keepOneRule) { line1 = tr("keep_one"); color = RED; }
        else if (creative() || cost == 0) { line1 = tr("free"); color = GREEN; }
        else { line1 = tr("cost", cost); color = canPay() ? GREEN : RED; }
        int costBorder = !any ? VIOLET_DK : (valid && canPay() && wearOk) ? GOLD_DK : (valid ? RED : VIOLET_DK);
        well(g, cx, cy, 76, 20, costBorder);
        drawFit(g, line1, cx + 5, cy + 3, 66, color);
        if (booksNeeded > 1) small(g, tr("books_needed", booksNeeded, bookCount), cx + 5, cy + 13, booksNeeded <= bookCount ? TEXT_SOFT : RED, false);
        else if (wear > 0) small(g, tr("wear_points", wear), cx + 5, cy + 13, wearOk ? 0xFFFF9955 : RED, false);
        else if (!creative()) small(g, tr("have", playerLevels()), cx + 5, cy + 13, TEXT_DIM, false);

        // cabecera de la lista y selector Mover | Copiar (modo con el que se marcan los siguientes)
        txt(g, tr("choose"), x + 10, y + 55, TEXT_SOFT, false);
        if (st.allowCopy) segmented(g, x + MODE_X, y + MODE_Y, mx, my);

        // lista
        well(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H, VIOLET_DK);
        boolean needsScroll = enchants.size() > ROWS;
        int rowW = rowW();
        int rx = x + LIST_X + 1;
        for (int i = 0; i < ROWS && scroll + i < enchants.size(); i++) {
            Map.Entry<Enchantment, Integer> e = enchants.get(scroll + i);
            boolean on = selected.containsKey(TransferMenu.idOf(e.getKey()));
            boolean rc = rowCopy(e.getKey());
            boolean enabled = on || selectable(e.getKey());
            int ry = y + LIST_Y + 1 + i * ROW_H;
            boolean hover = enabled && inBox(mx, my, rx, ry, rowW, ROW_H - 1);
            int accent = rc ? GOLD : GREEN;
            int bg = !enabled ? 0xFF181222
                    : on ? (rc ? (hover ? 0xFF4A3E14 : 0xFF3A3010) : (hover ? 0xFF2A5A44 : 0xFF214636))
                    : (hover ? 0xFF34285A : (i % 2 == 0 ? 0xFF241B3A : 0xFF1F1733));
            rect(g, rx, ry, rx + rowW, ry + ROW_H - 1, bg);
            rect(g, rx, ry, rx + 2, ry + ROW_H - 1, !enabled ? 0xFF2A2240 : on ? accent : (hover ? VIOLET : VIOLET_DK));

            // casilla redondeada: verde con tick = mover, dorada con "+" = copiar
            int bx = rx + 6, by = ry + 3;
            int boxBorder = !enabled ? 0xFF3A3050 : on ? accent : VIOLET;
            rect(g, bx + 1, by, bx + 11, by + 12, boxBorder);
            rect(g, bx, by + 1, bx + 12, by + 11, boxBorder);
            rect(g, bx + 1, by + 1, bx + 11, by + 11, on ? (rc ? 0xFF2A2008 : 0xFF0F2A20) : 0xFF0B0816);
            if (on && !rc) for (int[] p : CHECK) rect(g, bx + 2 + p[0] - 1, by + 2 + p[1], bx + 3 + p[0] - 1, by + 4 + p[1], accent);
            if (on && rc) { rect(g, bx + 5, by + 3, bx + 7, by + 9, accent); rect(g, bx + 3, by + 5, bx + 9, by + 7, accent); }

            // precio (pastilla a la derecha), etiqueta de modo, selector de reparto y nombre
            int[] lay = layout(e);
            String price = tr("level_short", TransferMenu.costOf(st, e.getKey(), e.getValue(), rc));
            boolean wears = rc && TransferMenu.effectiveWear(st, e.getKey(), menu.getSource()) > 0;
            rect(g, lay[0] - 3, ry + 2, rx + rowW - 3, ry + ROW_H - 3, enabled ? 0xFF120D22 : 0xFF14101E);
            txt(g, price, lay[0], ry + 5, enabled ? (wears ? 0xFFFF9955 : GOLD) : 0xFF6A6080, false);
            int nameRight = lay[0] - 3;
            if (on) {
                small(g, tr(rc ? "mode_copy" : "mode_move"), lay[1], ry + 6, accent, false);
                nameRight = lay[1];
                if (lay[4] > 0) {
                    boolean chipHover = inBox(mx, my, lay[3], ry + 3, lay[4], 12);
                    int[] parts = partsFor(e);
                    boolean split = parts.length > 1;
                    roundRect(g, lay[3], ry + 3, lay[4], 12, split ? (chipHover ? 0xFFFFE394 : GOLD_DK) : (chipHover ? VIOLET_HI : VIOLET), split ? 0xFF3A2C10 : 0xFF1B1430);
                    small(g, partsLabel(parts), lay[3] + lay[4] / 2, ry + 6, split ? GOLD : TEXT_SOFT, true);
                    nameRight = lay[3];
                }
            }
            String name = e.getKey().getFullname(e.getValue()).getString();
            drawFit(g, name, rx + 24, ry + 5, nameRight - 4 - (rx + 24), !enabled ? 0xFF7A7090 : on ? (rc ? 0xFFFFE9A8 : 0xFFB8FFDD) : 0xFFFFFFFF);
        }

        // barra de scroll
        if (needsScroll) {
            int bx2 = scrollbarX(), by2 = scrollbarY(), sh = ROWS * ROW_H;
            rect(g, bx2, by2, bx2 + SB_W, by2 + sh, 0xFF0B0816);
            int th = thumbHeight(), ty = by2 + ((sh - th) * scroll) / Math.max(1, enchants.size() - ROWS);
            rect(g, bx2, ty, bx2 + SB_W, ty + th, VIOLET);
            rect(g, bx2, ty, bx2 + SB_W - 1, ty + th - 1, VIOLET_HI);
        }

        // botones inferiores
        boolean can = canTransfer();
        button(g, x + TB_X, y + TB_Y, TB_W, TB_H, tr("transfer"), can, can && inBox(mx, my, x + TB_X, y + TB_Y, TB_W, TB_H), true);
        button(g, x + CB_X, y + CB_Y, CB_W, CB_H, tr("cancel"), true, inBox(mx, my, x + CB_X, y + CB_Y, CB_W, CB_H), false);
    }

    // ---------- piezas del estilo ----------
    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int gg = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    /** Degradado vertical hecho con lineas de 1 px. */
    private void vgrad(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bottom) {
        int h = y2 - y1;
        for (int i = 0; i < h; i++) rect(g, x1, y1 + i, x2, y1 + i + 1, lerp(top, bottom, h <= 1 ? 0.0F : (float) i / (h - 1)));
    }

    /** Rectangulo con esquinas cortadas y borde de un pixel. */
    private void roundRect(GuiGraphics g, int x, int y, int w, int h, int border, int fillColor) {
        rect(g, x + 1, y, x + w - 1, y + h, border);
        rect(g, x, y + 1, x + w, y + h - 1, border);
        rect(g, x + 1, y + 1, x + w - 1, y + h - 1, fillColor);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        roundRect(g, x, y, w, h, OUTLINE, OUTLINE);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, VIOLET, VIOLET);
        roundRect(g, x + 2, y + 2, w - 4, h - 4, VIOLET_DK, VIOLET_DK);
        vgrad(g, x + 3, y + 3, x + w - 3, y + h - 3, BG_TOP, BG_BOT);
        rect(g, x + 3, y + 3, x + w - 3, y + 4, 0xFF4A3A80);
        // tachuelas doradas en las esquinas
        int[][] cs = {{x + 4, y + 4}, {x + w - 6, y + 4}, {x + 4, y + h - 6}, {x + w - 6, y + h - 6}};
        for (int[] c : cs) { rect(g, c[0], c[1], c[0] + 2, c[1] + 2, GOLD); }
    }

    /** Hueco oscuro (caja de coste, lista). */
    private void well(GuiGraphics g, int x, int y, int w, int h, int border) {
        roundRect(g, x, y, w, h, border, WELL);
    }

    private void slot(GuiGraphics g, int x, int y, int border, float glow) {
        if (glow > 0.0F) roundRect(g, x - 4, y - 4, 24, 24, lerp(0xFF1A1230, GOLD_DK, 0.35F * glow), lerp(0xFF1A1230, GOLD_DK, 0.18F * glow));
        roundRect(g, x - 2, y - 2, 20, 20, border, 0xFF0B0816);
        rect(g, x - 1, y - 1, x + 17, y + 17, SLOT_BG);
    }

    private void arrow(GuiGraphics g, int x, int cy, int c) {
        rect(g, x, cy - 1, x + 12, cy + 1, c);
        for (int i = 0; i < 6; i++) rect(g, x + 12 + i, cy - (5 - i), x + 13 + i, cy + 1 + (5 - i), c);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, boolean hover, boolean primary) {
        int top, bottom, border, text;
        boolean shadow;
        if (!enabled) { top = 0xFF1E1832; bottom = 0xFF1E1832; border = VIOLET_DK; text = 0xFF6C6190; shadow = false; }
        else if (primary) { top = hover ? 0xFFFFE394 : GOLD; bottom = hover ? 0xFFE0A93A : 0xFFC9922E; border = 0xFF5A3C0A; text = 0xFF2A1A00; shadow = false; }
        else { top = hover ? 0xFF4A3A86 : 0xFF3A2C6A; bottom = hover ? 0xFF35286A : 0xFF2A1F4F; border = hover ? VIOLET_HI : VIOLET; text = 0xFFFFFFFF; shadow = true; }
        roundRect(g, x, y, w, h, border, border);
        vgrad(g, x + 1, y + 1, x + w - 1, y + h - 1, top, bottom);
        if (enabled && primary) rect(g, x + 2, y + 1, x + w - 2, y + 2, lerp(top, 0xFFFFFFFF, 0.5F));
        int tw = Math.min(font.width(label), w - 6);
        int ty = y + (h - 8) / 2 + 1;
        if (shadow) txt(g, label, x + (w - tw) / 2, ty, text, true);
        else drawFit(g, label, x + (w - tw) / 2, ty, w - 6, text);
    }

    /** Selector Mover | Copiar en dos mitades. */
    private void segmented(GuiGraphics g, int x, int y, int mx, int my) {
        int hw = MODE_W / 2;
        roundRect(g, x, y, MODE_W, MODE_H, VIOLET, VIOLET);
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == curCopy;
            boolean hover = inBox(mx, my, x + i * hw, y, hw, MODE_H);
            int sx = x + 1 + i * (hw - 1), sw = hw - 1;
            if (on) vgrad(g, sx, y + 1, sx + sw, y + MODE_H - 1, GOLD, 0xFFC9922E);
            else rect(g, sx, y + 1, sx + sw, y + MODE_H - 1, hover ? 0xFF34285A : 0xFF1B1430);
            String label = tr(i == 0 ? "mode_move" : "mode_copy");
            int tw = Math.min(font.width(label), hw - 6);
            drawFit(g, label, sx + (sw - tw) / 2, y + 2, hw - 6, on ? 0xFF2A1A00 : TEXT_SOFT);
        }
    }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Texto pequeño (75%). */
    private void small(GuiGraphics g, String text, int x, int y, int color, boolean centered) {
        float s = 0.75F;
        float w = font.width(text) * s;
        push(g);
        move(g, centered ? x - w / 2.0F : (float) x, (float) y);
        zoom(g, s);
        txt(g, text, 0, 0, color, false);
        pop(g);
    }

    /** Dibuja el texto ajustado a maxWidth: lo encoge hasta un 70% y, si aun no cabe, lo recorta con "...". */
    private void drawFit(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) { txt(g, text, x, y, color, false); return; }
        float scale = Math.max(0.7F, (float) maxWidth / w);
        String shown = text;
        if (w * scale > maxWidth) {
            int avail = (int) (maxWidth / scale) - font.width("...");
            shown = font.plainSubstrByWidth(text, Math.max(0, avail)) + "...";
        }
        push(g);
        move(g, (float) x, y + (1.0F - scale) * 4.0F);
        zoom(g, scale);
        txt(g, shown, 0, 0, color, false);
        pop(g);
    }

    // ---------- geometria de la lista ----------
    private boolean needsScroll() { return enchants.size() > ROWS; }
    private int scrollbarX() { return leftPos + LIST_X + LIST_W - 1 - SB_W - 1; }
    private int scrollbarY() { return topPos + LIST_Y + 1; }
    private int thumbHeight() { return Math.max(14, ROWS * ROW_H * ROWS / Math.max(1, enchants.size())); }

    private int rowAt(double mx, double my) {
        int left = leftPos + LIST_X + 1;
        int right = leftPos + LIST_X + LIST_W - 1 - (needsScroll() ? SB_W + 2 : 0);
        int top = topPos + LIST_Y + 1;
        if (mx < left || mx >= right || my < top || my >= top + ROWS * ROW_H) return -1;
        int idx = scroll + (int) ((my - top) / ROW_H);
        return idx >= 0 && idx < enchants.size() ? idx : -1;
    }

    private boolean overScrollbar(double mx, double my) {
        return needsScroll() && inBox(mx, my, scrollbarX(), scrollbarY(), SB_W, ROWS * ROW_H);
    }

    private void scrollFromMouse(double my) {
        int maxScroll = Math.max(0, enchants.size() - ROWS);
        if (maxScroll == 0) return;
        int travel = ROWS * ROW_H - thumbHeight();
        double ratio = (my - scrollbarY() - thumbHeight() / 2.0D) / Math.max(1, travel);
        scroll = (int) Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * maxScroll);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---------- raton ----------
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = leftPos, y = topPos;
            if (inBox(mx, my, x + SETB_X, y + SETB_Y, SETB_W, SETB_H)) {
                click();
                minecraft.setScreen(new SetConfigScreen(this, menu.getSettings().copy()));
                return true;
            }
            if (inBox(mx, my, x + CB_X, y + CB_Y, CB_W, CB_H)) { click(); onClose(); return true; }
            if (inBox(mx, my, x + TB_X, y + TB_Y, TB_W, TB_H)) {
                if (canTransfer()) {
                    click();
                    List<TransferMenu.Pick> picks = new ArrayList<>();
                    for (Map.Entry<Enchantment, Integer> e : enchants) {
                        String id = TransferMenu.idOf(e.getKey());
                        Boolean mode = selected.get(id);
                        if (mode != null) picks.add(new TransferMenu.Pick(id, mode.booleanValue(), partsFor(e).clone()));
                    }
                    ModNetwork.sendSelection(menu.containerId, picks);
                }
                return true;
            }
            if (menu.getSettings().allowCopy && inBox(mx, my, x + MODE_X, y + MODE_Y, MODE_W, MODE_H)) {
                boolean wantCopy = mx >= x + MODE_X + MODE_W / 2;
                if (wantCopy != curCopy) { curCopy = wantCopy; click(); } // solo cambia el modo de los SIGUIENTES; lo ya elegido no se toca
                return true;
            }
            int row = rowAt(mx, my);
            if (row >= 0) {
                Map.Entry<Enchantment, Integer> e = enchants.get(row);
                String id = TransferMenu.idOf(e.getKey());
                if (selected.containsKey(id)) {
                    if (overSplit(row, mx, my)) {
                        int n = TransferMenu.partitions(e.getValue(), maxParts).size();
                        splitIdx.put(id, (splitIdx.getOrDefault(id, 0) + 1) % Math.max(1, n));
                    } else {
                        selected.remove(id);
                        splitIdx.remove(id);
                    }
                    click();
                } else if (selectable(e.getKey())) {
                    selected.put(id, curCopy);
                    splitIdx.remove(id);
                    click();
                }
                return true;
            }
            if (overScrollbar(mx, my)) {
                draggingScrollbar = true;
                scrollFromMouse(my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) { scrollFromMouse(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, enchants.size() - ROWS);
        if (maxScroll > 0 && inBox(mx, my, leftPos + LIST_X, topPos + LIST_Y, LIST_W, LIST_H)) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + (delta < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
