package com.example.set.client;

import com.example.set.ModNetwork;
import com.example.set.SelectiveEnchantmentTransfer;
import com.example.set.SetConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Menu de ajustes con aspecto del menu de pausa de Minecraft: fondo oscurecido, titulo centrado y botones anchos.
 * Clic = siguiente valor, Mayus + clic = valor anterior. Al guardar, el servidor comprueba que seas operador.
 */
public class SetConfigScreen extends Screen {
    private static final double[] COST_STEPS = {0.0D, 0.25D, 0.5D, 0.75D, 1.0D, 1.25D, 1.5D, 2.0D, 3.0D, 5.0D};
    private static final int[] MAX_STEPS = {10, 15, 20, 25, 30, 39, 50, 75, 100};
    private static final int[] MIN_STEPS = {1, 2, 3, 4, 5, 6};
    private static final int[] BASE_STEPS = {0, 10, 15, 20, 25, 30, 40, 50};
    private static final double[] WEAR_STEPS = {0.25D, 0.5D, 0.75D, 1.0D, 1.5D, 2.0D, 3.0D};
    private static final double[] COPY_STEPS = {1.5D, 2.0D, 3.0D, 4.0D, 5.0D, 7.5D, 10.0D};
    private static final int BTN_W = 150, BTN_H = 20, ROW_H = 22;

    private final Screen parent;
    private final SetConfig.Settings s;
    private final List<Opt> opts = new ArrayList<>();
    private int titleY, fileNoteY;

    /** Una opcion del menu: texto del valor, que hacer al pulsar (siguiente / anterior) y cuando esta activa. */
    private static final class Opt {
        final String id;
        final Supplier<String> value;
        final Runnable next, prev;
        final BooleanSupplier enabled;
        int x, y;
        Opt(String id, Supplier<String> value, Runnable next, Runnable prev, BooleanSupplier enabled) {
            this.id = id; this.value = value; this.next = next; this.prev = prev; this.enabled = enabled;
        }
    }

    public SetConfigScreen(Screen parent, SetConfig.Settings initial) {
        super(Component.translatable("gui." + SelectiveEnchantmentTransfer.MODID + ".config.title"));
        this.parent = parent;
        this.s = initial;

        opts.add(toggle("xp_cost", () -> s.xpCostEnabled, () -> s.xpCostEnabled = !s.xpCostEnabled, () -> true));
        opts.add(new Opt("cost_mult", () -> mult(s.costMultiplier),
                () -> s.costMultiplier = cycle(COST_STEPS, s.costMultiplier, true),
                () -> s.costMultiplier = cycle(COST_STEPS, s.costMultiplier, false), () -> s.xpCostEnabled));
        opts.add(new Opt("max_cost", () -> tr("config.levels", s.maxTotalCost),
                () -> s.maxTotalCost = cycle(MAX_STEPS, s.maxTotalCost, true),
                () -> s.maxTotalCost = cycle(MAX_STEPS, s.maxTotalCost, false), () -> s.xpCostEnabled));
        opts.add(new Opt("min_ench", () -> String.valueOf(s.minEnchantments),
                () -> s.minEnchantments = cycle(MIN_STEPS, s.minEnchantments, true),
                () -> s.minEnchantments = cycle(MIN_STEPS, s.minEnchantments, false), () -> true));
        opts.add(toggle("sounds", () -> s.sounds, () -> s.sounds = !s.sounds, () -> true));
        opts.add(toggle("allow_copy", () -> s.allowCopy, () -> s.allowCopy = !s.allowCopy, () -> true));
        opts.add(new Opt("copy_mult", () -> mult(s.copyCostMultiplier),
                () -> s.copyCostMultiplier = cycle(COPY_STEPS, s.copyCostMultiplier, true),
                () -> s.copyCostMultiplier = cycle(COPY_STEPS, s.copyCostMultiplier, false), () -> s.allowCopy && s.xpCostEnabled));
        opts.add(toggle("copy_treasure", () -> s.allowCopyTreasure, () -> s.allowCopyTreasure = !s.allowCopyTreasure, () -> s.allowCopy));
        opts.add(toggle("copy_wear", () -> s.copyWearEnabled, () -> s.copyWearEnabled = !s.copyWearEnabled, () -> s.allowCopy));
        opts.add(new Opt("base_wear", () -> pct(s.copyBaseWear),
                () -> s.copyBaseWear = cycle(BASE_STEPS, s.copyBaseWear, true),
                () -> s.copyBaseWear = cycle(BASE_STEPS, s.copyBaseWear, false), () -> s.allowCopy && s.copyWearEnabled));
        opts.add(new Opt("wear_mult", () -> wmult(s.copyWearMultiplier),
                () -> s.copyWearMultiplier = cycle(WEAR_STEPS, s.copyWearMultiplier, true),
                () -> s.copyWearMultiplier = cycle(WEAR_STEPS, s.copyWearMultiplier, false), () -> s.allowCopy && s.copyWearEnabled));
    }

    // ---------- textos y valores ----------
    private static Component trc(String key, Object... args) {
        return Component.translatable("gui." + SelectiveEnchantmentTransfer.MODID + "." + key, args);
    }
    private static String tr(String key, Object... args) { return trc(key, args).getString(); }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((int) v) : String.format("%.2f", v).replaceAll("0+$", ""); }
    private static String pct(int v) { return v + "%"; }
    private static String wmult(double v) { return v <= 0.0D ? tr("config.off") : "×" + fmt(v); }
    private static String mult(double v) { return v <= 0.0D ? tr("config.free") : "×" + fmt(v); }

    private static Opt toggle(String id, BooleanSupplier get, Runnable flip, BooleanSupplier enabled) {
        return new Opt(id, () -> tr(get.getAsBoolean() ? "config.on" : "config.off"), flip, flip, enabled);
    }

    private static int nearest(double[] steps, double v) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) if (Math.abs(steps[i] - v) < Math.abs(steps[best] - v)) best = i;
        return best;
    }
    private static double cycle(double[] steps, double v, boolean next) {
        return steps[(nearest(steps, v) + (next ? 1 : steps.length - 1)) % steps.length];
    }
    private static int cycle(int[] steps, int v, boolean next) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) if (Math.abs(steps[i] - v) < Math.abs(steps[best] - v)) best = i;
        return steps[(best + (next ? 1 : steps.length - 1)) % steps.length];
    }

    private String labelText(Opt o) { return tr("config." + o.id, o.value.get()); }
    private boolean isOn(Opt o) { return o.enabled.getAsBoolean(); }

    // ---------- estilo (igual que la pantalla de transferencia) ----------
    private static final int OUTLINE = 0xFF08050F, VIOLET = 0xFF7A56D0, VIOLET_DK = 0xFF3A2A66, VIOLET_HI = 0xFF9B7BEA;
    private static final int BG_TOP = 0xFF241A40, BG_BOT = 0xFF120D22, WELL = 0xFF120D22, SLOT_BG = 0xFF171127;
    private static final int GOLD = 0xFFFFD36B, GOLD_DK = 0xFFB8862B, TEXT_DIM = 0xFFA89BD6, TEXT_SOFT = 0xFFC9BDF0;
    private static final int GREEN = 0xFF5CFFB0, RED = 0xFFFF6B6B;
    private int panX, panY, panW, panH, leftX, rightX, topY, saveY;

    private void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int c) { g.fill(x1, y1, x2, y2, c); }

    private void txt(GuiGraphics g, String s, int x, int y, int c, boolean sh) { g.drawString(font, s, x, y, c, sh); }

    private void push(GuiGraphics g) { g.pose().pushPose(); }

    private void pop(GuiGraphics g) { g.pose().popPose(); }

    private void move(GuiGraphics g, float x, float y) { g.pose().translate(x, y, 0.0F); }

    private void zoom(GuiGraphics g, float s) { g.pose().scale(s, s, 1.0F); }

    private void tipc(GuiGraphics g, List<Component> tip, int mx, int my) { g.renderComponentTooltip(font, tip, mx, my); }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int gg = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    private void vgrad(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bottom) {
        int h = y2 - y1;
        for (int i = 0; i < h; i++) rect(g, x1, y1 + i, x2, y1 + i + 1, lerp(top, bottom, h <= 1 ? 0.0F : (float) i / (h - 1)));
    }

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

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

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

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void drawCentered(GuiGraphics g, String s, int cx, int y, int color, boolean shadow) {
        txt(g, s, cx - font.width(s) / 2, y, color, shadow);
    }

    // ---------- inicializacion ----------
    @Override protected void init() {
        int blockH = 6 * ROW_H + 8 + BTN_H + 14;
        topY = Math.max(44, (height - blockH) / 2 + 16);
        titleY = topY - 30;
        leftX = width / 2 - BTN_W - 5;
        rightX = width / 2 + 5;
        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            o.x = i < 6 ? leftX : rightX;
            o.y = topY + (i % 6) * ROW_H;
        }
        saveY = topY + 6 * ROW_H + 8;
        fileNoteY = saveY + BTN_H + 7;
        panW = BTN_W * 2 + 10 + 28;
        panX = width / 2 - panW / 2;
        panY = titleY - 9;
        panH = fileNoteY + 16 - panY;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    // ---------- dibujo ----------
    private List<Component> wrap(String text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(candidate) > maxWidth && cur.length() > 0) {
                lines.add(Component.literal(cur.toString()));
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (cur.length() > 0) lines.add(Component.literal(cur.toString()));
        return lines;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        panel(g, panX, panY, panW, panH);
        drawCentered(g, title.getString(), width / 2, titleY, GOLD, true);
        drawCentered(g, tr("config.note"), width / 2, titleY + 13, TEXT_DIM, false);
        rect(g, width / 2 - 60, titleY + 25, width / 2 - 6, titleY + 26, VIOLET_DK);
        rect(g, width / 2 - 6, titleY + 25, width / 2 + 6, titleY + 26, GOLD_DK);
        rect(g, width / 2 + 6, titleY + 25, width / 2 + 60, titleY + 26, VIOLET_DK);

        Opt hovered = null;
        for (Opt o : opts) {
            boolean hover = inBox(mx, my, o.x, o.y, BTN_W, BTN_H);
            if (hover) hovered = o;
            button(g, o.x, o.y, BTN_W, BTN_H, labelText(o), isOn(o), hover && isOn(o), false);
        }
        button(g, rightX, topY + 5 * ROW_H, BTN_W, BTN_H, tr("config.reset"), true, inBox(mx, my, rightX, topY + 5 * ROW_H, BTN_W, BTN_H), false);
        button(g, leftX, saveY, BTN_W, BTN_H, tr("config.save"), true, inBox(mx, my, leftX, saveY, BTN_W, BTN_H), true);
        button(g, rightX, saveY, BTN_W, BTN_H, tr("config.cancel"), true, inBox(mx, my, rightX, saveY, BTN_W, BTN_H), false);
        drawCentered(g, tr("config.file_note"), width / 2, fileNoteY, TEXT_DIM, false);

        if (hovered != null) tipc(g, wrap(tr("config." + hovered.id + ".desc"), 200), mx, my);
    }

    // ---------- raton ----------
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Opt o : opts) {
                if (inBox(mx, my, o.x, o.y, BTN_W, BTN_H)) {
                    if (isOn(o)) {
                        if (hasShiftDown()) o.prev.run(); else o.next.run();
                        click();
                    }
                    return true;
                }
            }
            if (inBox(mx, my, rightX, topY + 5 * ROW_H, BTN_W, BTN_H)) {
                s.copyFrom(SetConfig.Settings.defaults());
                click();
                return true;
            }
            if (inBox(mx, my, leftX, saveY, BTN_W, BTN_H)) {
                click();
                ModNetwork.sendConfig(s.copy());
                onClose();
                return true;
            }
            if (inBox(mx, my, rightX, saveY, BTN_W, BTN_H)) { click(); onClose(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }
}
