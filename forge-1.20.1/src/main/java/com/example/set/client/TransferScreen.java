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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final Set<String> selected = new LinkedHashSet<>();
    private boolean copyMode = false;
    private int scroll = 0;
    private boolean draggingScrollbar = false;

    // estado que se recalcula en cada fotograma
    private List<Map.Entry<Enchantment, Integer>> enchants = new ArrayList<>();
    private Map<Enchantment, Integer> chosen = new LinkedHashMap<>();
    private boolean keepOneRule = false;   // origen = libro y se marcaron todos (en modo mover)
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
    private MutableComponent modeLabel() { return trc(copyMode ? "mode_copy" : "mode_move"); }

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
    private boolean selectable(Enchantment e) { return TransferMenu.denyReason(menu.getSettings(), e, menu.getSource(), copyMode) == 0; }
    private boolean canTransfer() { return valid && canPay() && wearOk; }

    private void updateState() {
        if (!menu.getSettings().allowCopy) copyMode = false;
        enchants = menu.getEnchantments();
        Set<String> allowed = new HashSet<>();
        for (Map.Entry<Enchantment, Integer> e : enchants) if (selectable(e.getKey())) allowed.add(TransferMenu.idOf(e.getKey()));
        selected.retainAll(allowed);
        scroll = Math.max(0, Math.min(Math.max(0, enchants.size() - ROWS), scroll));

        chosen = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> e : enchants)
            if (selected.contains(TransferMenu.idOf(e.getKey()))) chosen.put(e.getKey(), e.getValue());
        cost = TransferMenu.totalCost(menu.getSettings(), chosen, copyMode);
        wear = copyMode ? TransferMenu.wearPoints(menu.getSettings(), menu.getSource(), chosen) : 0;
        wearOk = !copyMode || TransferMenu.canPayWear(menu.getSettings(), menu.getSource(), chosen);

        boolean sourceIsBook = menu.getSource().is(Items.ENCHANTED_BOOK);
        keepOneRule = !copyMode && sourceIsBook && !chosen.isEmpty() && chosen.size() >= enchants.size();
        valid = !chosen.isEmpty() && !keepOneRule;

        if (chosen.isEmpty()) {
            resultStack = ItemStack.EMPTY;
        } else {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            for (Map.Entry<Enchantment, Integer> e : chosen.entrySet())
                EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(e.getKey(), e.getValue()));
            resultStack = book;
        }
    }

    // ---------- render ----------
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        updateState();
        renderBackground(g);
        super.render(g, mx, my, partialTick);

        int x = leftPos, y = topPos;
        int row = rowAt(mx, my);
        boolean overMove = menu.getSettings().allowCopy && inBox(mx, my, x + MODE_X, y + MODE_Y, MODE_W / 2, MODE_H);
        boolean overCopy = menu.getSettings().allowCopy && inBox(mx, my, x + MODE_X + MODE_W / 2, y + MODE_Y, MODE_W / 2, MODE_H);
        if (overMove || overCopy) {
            List<Component> tip = new ArrayList<>();
            tip.add(trc(overCopy ? "mode_copy" : "mode_move").withStyle(ChatFormatting.WHITE));
            tip.add(trc(overCopy ? "mode_copy_tip" : "mode_move_tip", fmt(menu.getSettings().copyCostMultiplier)).withStyle(ChatFormatting.GRAY));
            tipc(g, tip, mx, my);
        } else if (inBox(mx, my, x + 140, y + 20, 76, 20)) {
            List<Component> tip = new ArrayList<>();
            tip.add(trc("tooltip.cost", cost));
            if (!creative()) tip.add(trc("have", playerLevels()).withStyle(ChatFormatting.GRAY));
            if (copyMode && wear > 0) {
                ItemStack src = menu.getSource();
                tip.add(trc("tooltip.wear_total", wear, Math.max(0, src.getMaxDamage() - src.getDamageValue() - wear)).withStyle(wearOk ? ChatFormatting.GOLD : ChatFormatting.RED));
            }
            tipc(g, tip, mx, my);
        } else if (row >= 0) {
            Map.Entry<Enchantment, Integer> e = enchants.get(row);
            List<Component> tip = new ArrayList<>();
            tip.add(e.getKey().getFullname(e.getValue()));
            tip.add(trc("tooltip.cost", TransferMenu.costOf(menu.getSettings(), e.getKey(), e.getValue(), copyMode)).withStyle(ChatFormatting.GOLD));
            int deny = TransferMenu.denyReason(menu.getSettings(), e.getKey(), menu.getSource(), copyMode);
            if (deny == 0) tip.add(trc("tooltip.toggle").withStyle(ChatFormatting.GRAY));
            else {
                tip.add(trc(copyMode ? "deny.cant_copy" : "deny.cant_move").withStyle(ChatFormatting.RED));
                if (deny == 2) tip.add(trc("cant_copy").withStyle(ChatFormatting.GRAY));
                else if (deny == 3) tip.add(trc("deny.durable").withStyle(ChatFormatting.GRAY));
            }
            int wearPct = TransferMenu.effectiveWear(menu.getSettings(), e.getKey(), menu.getSource());
            if (copyMode && wearPct > 0) tip.add(trc("tooltip.wear", wearPct).withStyle(ChatFormatting.GOLD));
            tipc(g, tip, mx, my);
        } else if (!resultStack.isEmpty() && inBox(mx, my, x + RESULT_X - 1, y + TransferMenu.SLOT_Y - 1, 18, 18)) {
            tipItem(g, resultStack, mx, my);
        } else {
            tipDefault(g, mx, my);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos;
        double t = (System.currentTimeMillis() % 100000L) / 1000.0D;
        float pulse = (float) ((Math.sin(t * 3.2D) + 1.0D) / 2.0D);

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
        if (chosen.isEmpty()) { line1 = tr("cost", "-"); color = 0xFF9A90BE; }
        else if (keepOneRule) { line1 = tr("keep_one"); color = RED; }
        else if (creative() || cost == 0) { line1 = tr("free"); color = GREEN; }
        else { line1 = tr("cost", cost); color = canPay() ? GREEN : RED; }
        int costBorder = chosen.isEmpty() ? VIOLET_DK : (valid && canPay() && wearOk) ? GOLD_DK : (valid ? RED : VIOLET_DK);
        well(g, cx, cy, 76, 20, costBorder);
        drawFit(g, line1, cx + 5, cy + 3, 66, color);
        if (copyMode && wear > 0) small(g, tr("wear_points", wear), cx + 5, cy + 13, wearOk ? 0xFFFF9955 : RED, false);
        else if (!creative()) small(g, tr("have", playerLevels()), cx + 5, cy + 13, TEXT_DIM, false);

        // cabecera de la lista y selector Mover | Copiar
        txt(g, tr("choose"), x + 10, y + 55, TEXT_SOFT, false);
        if (menu.getSettings().allowCopy) segmented(g, x + MODE_X, y + MODE_Y, mx, my);

        // lista
        well(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H, VIOLET_DK);
        boolean needsScroll = enchants.size() > ROWS;
        int rowW = LIST_W - 2 - (needsScroll ? SB_W + 2 : 0);
        for (int i = 0; i < ROWS && scroll + i < enchants.size(); i++) {
            Map.Entry<Enchantment, Integer> e = enchants.get(scroll + i);
            boolean on = selected.contains(TransferMenu.idOf(e.getKey()));
            boolean enabled = selectable(e.getKey());
            int rx = x + LIST_X + 1, ry = y + LIST_Y + 1 + i * ROW_H;
            boolean hover = enabled && inBox(mx, my, rx, ry, rowW, ROW_H - 1);
            int bg = !enabled ? 0xFF181222 : on ? (hover ? 0xFF2A5A44 : 0xFF214636) : (hover ? 0xFF34285A : (i % 2 == 0 ? 0xFF241B3A : 0xFF1F1733));
            rect(g, rx, ry, rx + rowW, ry + ROW_H - 1, bg);
            rect(g, rx, ry, rx + 2, ry + ROW_H - 1, !enabled ? 0xFF2A2240 : on ? GREEN : (hover ? VIOLET : VIOLET_DK));

            // casilla redondeada
            int bx = rx + 6, by = ry + 3;
            int boxBorder = !enabled ? 0xFF3A3050 : on ? GREEN : VIOLET;
            rect(g, bx + 1, by, bx + 11, by + 12, boxBorder);
            rect(g, bx, by + 1, bx + 12, by + 11, boxBorder);
            rect(g, bx + 1, by + 1, bx + 11, by + 11, on ? 0xFF0F2A20 : 0xFF0B0816);
            if (on) for (int[] p : CHECK) rect(g, bx + 2 + p[0] - 1, by + 2 + p[1], bx + 3 + p[0] - 1, by + 4 + p[1], GREEN);

            // precio en una pastilla a la derecha y nombre ajustado
            int priceVal = TransferMenu.costOf(menu.getSettings(), e.getKey(), e.getValue(), copyMode);
            String price = tr("level_short", priceVal);
            int pw = font.width(price);
            boolean wears = copyMode && TransferMenu.effectiveWear(menu.getSettings(), e.getKey(), menu.getSource()) > 0;
            int px = rx + rowW - 6 - pw;
            rect(g, px - 3, ry + 2, rx + rowW - 3, ry + ROW_H - 3, enabled ? 0xFF120D22 : 0xFF14101E);
            txt(g, price, px, ry + 5, enabled ? (wears ? 0xFFFF9955 : GOLD) : 0xFF6A6080, false);
            String name = e.getKey().getFullname(e.getValue()).getString();
            drawFit(g, name, rx + 24, ry + 5, rowW - 24 - pw - 14, !enabled ? 0xFF7A7090 : on ? 0xFFB8FFDD : 0xFFFFFFFF);
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
            boolean on = (i == 1) == copyMode;
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
                    ModNetwork.sendSelection(menu.containerId, new ArrayList<>(selected), copyMode);
                }
                return true;
            }
            if (menu.getSettings().allowCopy && inBox(mx, my, x + MODE_X, y + MODE_Y, MODE_W, MODE_H)) {
                boolean wantCopy = mx >= x + MODE_X + MODE_W / 2;
                if (wantCopy != copyMode) { copyMode = wantCopy; click(); }
                return true;
            }
            int row = rowAt(mx, my);
            if (row >= 0) {
                Enchantment ench = enchants.get(row).getKey();
                if (!selectable(ench)) return true;
                String id = TransferMenu.idOf(ench);
                if (!selected.add(id)) selected.remove(id);
                click();
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
