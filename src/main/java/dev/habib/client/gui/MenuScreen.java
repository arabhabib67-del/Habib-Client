package dev.habib.client.gui;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.hud.HudManager;
import dev.habib.client.hud.HudModule;
import dev.habib.client.hud.HudModule.Category;
import dev.habib.client.packs.ModrinthPacks;
import dev.habib.client.packs.ModrinthPacks.Hit;
import dev.habib.client.update.Updater;
import dev.habib.client.util.SwingAnimator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Habib Client menu: category sidebar, animated toggles, smooth scrolling, swing-animation settings
 * and a full-screen HUD layout editor. Everything is custom drawn so it can animate smoothly.
 */
public class MenuScreen extends Screen {
    private enum Tab {
        INFO("Info", Category.INFO),
        COMBAT("Combat", Category.COMBAT),
        GEAR("Gear", Category.GEAR),
        MOVEMENT("Movement", Category.MOVEMENT),
        ANIMATIONS("Animations", null),
        PACKS("Packs", null),
        OPTIMIZE("Optimize", null),
        EFFECTS("Effects", null),
        PRESETS_TAB("Presets", null),
        LAYOUT("Layout", null);

        final String label;
        final Category category;

        Tab(String label, Category category) {
            this.label = label;
            this.category = category;
        }
    }

    private static final Tab[] TABS = Tab.values();

    private static final int ACCENT = 0xFFFF6A2B;
    private static final int ACCENT_DIM = 0xFFC9501C;
    private static final int BG = 0xF2121214;
    private static final int SIDE_BG = 0xFF0C0C0E;
    private static final int ROW = 0xFF18181B;
    private static final int ROW_HOVER = 0xFF202024;
    private static final int TEXT = 0xFFECECEE;
    private static final int MUTED = 0xFF85858C;
    private static final int OFF = 0xFF38383E;
    private static final int HAIR = 0x16FFFFFF;

    private static final int HEADER_H = 30;
    private static final int SIDE_W = 100;
    private static final int ROW_H = 26;
    private static final int ROW_STRIDE = 28;

    private Tab tab = Tab.INFO;
    private boolean layoutMode;

    // animation state
    private float open;
    private float tabFade = 1f;
    private float highlightY = -1f;
    private float scroll;
    private float scrollTarget;
    private float segmentX = -1f;
    private float buttonHoverA;
    private float buttonHoverB;
    private final float[] toggle;
    private final float[] hover;
    private final float[] tabHover = new float[TABS.length];
    private long lastNanos = System.nanoTime();

    // navigation / smoothness helpers
    private float frameDt = 0.016f;
    private int hoverCounter;
    private final Map<Integer, Float> rowHover = new HashMap<>();
    private final Map<Integer, Boolean> keyWas = new HashMap<>();
    private final float[] tabScroll = new float[TABS.length];
    private boolean sbDrag;

    // interaction state
    private HudModule dragging;
    private double grabX;
    private double grabY;
    private boolean sliderDrag;

    // resource pack browser state
    private static final int PACK_ROW_H = 36;
    private static final int PACK_STRIDE = 38;
    private static final int INSTALLED_ROW_H = 26;
    private static final int INSTALLED_STRIDE = 28;
    private TextFieldWidget packField;
    private boolean packsInstalledView; // true for both the Installed and Enabled views
    private int packView; // 0 = Modrinth, 1 = Installed, 2 = Enabled
    private List<Hit> hits = new ArrayList<>();
    private String packsStatus = "";
    private int searchGen;
    private long searchAt = -1L;
    private boolean searchedOnce;
    private float packSegX = -1f;
    private float bgToggle = 1f;
    private float heldToggle = 1f;
    private float crystalToggle;
    private float particleToggle;
    private String presetNote = "";
    private float onHitToggle = 1f;
    private static final int SLIDER_Y = 86;
    private final String[] viewQuery = {"", "", ""};
    private final Set<String> busy = new HashSet<>();
    private final Map<String, String> packErrors = new HashMap<>();
    private final Map<String, Float> packToggle = new HashMap<>();

    // geometry (recomputed every frame)
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cx;
    private int cy;
    private int cw;
    private int ch;

    public MenuScreen() {
        super(Text.literal("Habib Client"));
        int n = HudManager.MODULES.size();
        toggle = new float[n];
        hover = new float[n];
        for (int i = 0; i < n; i++) {
            toggle[i] = HudManager.MODULES.get(i).enabled ? 1f : 0f;
        }
    }

    @Override
    protected void init() {
        String old = packField == null ? "" : packField.getText();
        packField = new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.literal("Search"));
        packField.setMaxLength(48);
        packField.setDrawsBackground(false);
        packField.setPlaceholder(Text.literal(placeholderFor(packView)));
        packField.setText(old);
        packField.setChangedListener(t -> {
            if (packsInstalledView) {
                scroll = 0f;
                scrollTarget = 0f;
            } else {
                searchAt = System.currentTimeMillis() + 450L;
            }
        });
        addSelectableChild(packField);
    }

    // ------------------------------------------------------------------ helpers

    private static float approach(float cur, float target, float dt, float speed) {
        return cur + (target - cur) * (1f - (float) Math.exp(-dt * speed));
    }

    private static int alpha(int argb, float a) {
        float c = Math.max(0f, Math.min(1f, a));
        int al = Math.round(((argb >>> 24) & 0xFF) * c);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255;
            int cb = (b >>> s) & 255;
            out |= (Math.round(ca + (cb - ca) * k) & 255) << s;
        }
        return out;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (((color >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) return;
        int rad = Math.max(0, Math.min(Math.min(r, 1), Math.min(w, h) / 2)); // flat look: at most a 1px chamfer
        if (rad == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x, y + rad, x + w, y + h - rad, color);
        for (int i = 0; i < rad; i++) {
            double dy = rad - i - 0.5;
            int inset = rad - (int) Math.round(Math.sqrt(rad * rad - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    private static void border(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void txt(DrawContext ctx, String s, int x, int y, int color) {
        if (((color >>> 24) & 0xFF) < 12) return;
        ctx.drawText(textRenderer, s, x, y, color, false);
    }

    private void txtCentered(DrawContext ctx, String s, int centerX, int y, int color) {
        txt(ctx, s, centerX - textRenderer.getWidth(s) / 2, y, color);
    }

    private void txtRight(DrawContext ctx, String s, int rightX, int y, int color) {
        txt(ctx, s, rightX - textRenderer.getWidth(s), y, color);
    }

    private void geometry() {
        pw = Math.min(430, width - 16);
        ph = Math.min(268, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + Math.round((1f - open) * 10f);
        cx = px + SIDE_W + 10;
        cy = py + HEADER_H + 8;
        cw = pw - SIDE_W - 20;
        ch = ph - HEADER_H - 16;
    }

    private int tabY(int i) {
        int base = py + HEADER_H + 8;
        return i < 4 ? base + 12 + 19 * i : base + 102 + 18 * (i - 4);
    }

    private int rowY(int j) {
        return cy + j * ROW_STRIDE - Math.round(scroll);
    }

    private List<HudModule> modulesFor(Category c) {
        List<HudModule> list = new ArrayList<>();
        for (HudModule m : HudManager.MODULES) {
            if (m.category == c) list.add(m);
        }
        return list;
    }

    private float maxScroll() {
        if (tab == Tab.PACKS) {
            int rows = packsInstalledView ? installedPacks().size() : hits.size();
            int stride = packsInstalledView ? INSTALLED_STRIDE : PACK_STRIDE;
            return Math.max(0f, rows * stride - listH());
        }
        if (tab.category == null) return 0f;
        return Math.max(0f, modulesFor(tab.category).size() * ROW_STRIDE - ch);
    }

    /** Smoothly animated 0..1 hover value for rows that have no state of their own (keyed by draw order). */
    private float rowHoverAnim(boolean over) {
        int key = tab.ordinal() * 64 + (hoverCounter++ & 63);
        float v = approach(rowHover.getOrDefault(key, 0f), over ? 1f : 0f, frameDt, 18f);
        rowHover.put(key, v);
        return v;
    }

    private boolean keyEdge(int key) {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        boolean was = keyWas.getOrDefault(key, false);
        keyWas.put(key, down);
        return down && !was;
    }

    /** Keyboard navigation: Up/Down or 1-9/0 switch tabs, PageUp/PageDown/Home/End move the list. */
    private void pollKeys() {
        boolean up = keyEdge(GLFW.GLFW_KEY_UP);
        boolean downK = keyEdge(GLFW.GLFW_KEY_DOWN);
        boolean pgUp = keyEdge(GLFW.GLFW_KEY_PAGE_UP);
        boolean pgDown = keyEdge(GLFW.GLFW_KEY_PAGE_DOWN);
        boolean home = keyEdge(GLFW.GLFW_KEY_HOME);
        boolean end = keyEdge(GLFW.GLFW_KEY_END);
        int digit = -1;
        for (int d = 0; d < 10; d++) {
            if (keyEdge(GLFW.GLFW_KEY_0 + d) && digit < 0) digit = d == 0 ? 9 : d - 1;
        }
        if (layoutMode || (packField != null && packField.isFocused())) return;

        int cur = tab.ordinal();
        if (up) select(TABS[(cur + TABS.length - 1) % TABS.length]);
        else if (downK) select(TABS[(cur + 1) % TABS.length]);
        else if (digit >= 0 && digit < TABS.length) select(TABS[digit]);
        if (pgUp) scrollTarget = Math.max(0f, scrollTarget - ch * 0.8f);
        if (pgDown) scrollTarget = Math.min(maxScroll(), scrollTarget + ch * 0.8f);
        if (home) scrollTarget = 0f;
        if (end) scrollTarget = maxScroll();
    }

    private int thumbHeight() {
        float max = maxScroll();
        return Math.max(14, Math.round(ch * (ch / (ch + Math.max(1f, max)))));
    }

    private boolean scrollbarHit(double mx, double my) {
        return (tab.category != null || tab == Tab.PACKS) && maxScroll() > 0f && inside(mx, my, px + pw - 10, cy, 10, ch);
    }

    private void dragScrollbar(double my) {
        int th = thumbHeight();
        float frac = (float) Math.max(0.0, Math.min(1.0, (my - cy - th / 2.0) / Math.max(1, ch - th)));
        scrollTarget = frac * maxScroll();
        scroll = scrollTarget;
    }

    private void select(Tab t) {
        if (t == tab) return;
        tabScroll[tab.ordinal()] = scrollTarget; // remember where you were in the tab you leave
        tab = t;
        tabFade = 0f;
        scrollTarget = tabScroll[t.ordinal()];
        scroll = scrollTarget;
        setFocused(null);
        if (t == Tab.PACKS) {
            MinecraftClient.getInstance().getResourcePackManager().scanPacks();
            if (!searchedOnce) runSearch();
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        frameDt = dt;
        hoverCounter = 0;
        pollKeys();

        open = approach(open, 1f, dt, 14f);
        if (open > 0.995f) open = 1f;
        tabFade = approach(tabFade, 1f, dt, 12f);
        scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget));
        scroll = approach(scroll, scrollTarget, dt, 18f);

        List<HudModule> all = HudManager.MODULES;
        for (int i = 0; i < all.size(); i++) {
            toggle[i] = approach(toggle[i], all.get(i).enabled ? 1f : 0f, dt, 16f);
        }

        geometry();
        ctx.fill(0, 0, width, height, alpha(0xFF000000, (layoutMode ? 0.30f : 0.55f) * open));

        // modules are drawn here (the normal HUD is skipped while this screen is open) so changes show live
        MinecraftClient mc = MinecraftClient.getInstance();
        for (HudModule m : all) {
            if (m.enabled) m.draw(ctx, mc, true);
        }

        if (layoutMode) {
            renderLayoutMode(ctx, mx, my, dt);
        } else {
            renderPanel(ctx, mx, my, dt, now);
        }
    }

    private void renderPanel(DrawContext ctx, int mx, int my, float dt, long now) {
        float a = open;

        // soft drop shadow, border, body
        ctx.fill(px - 3, py + 2, px + pw + 3, py + ph + 6, alpha(0x28000000, a));
        ctx.fill(px - 1, py + 1, px + pw + 1, py + ph + 3, alpha(0x30000000, a));
        rrect(ctx, px - 1, py - 1, pw + 2, ph + 2, 1, alpha(0xFF2A2A30, a));
        rrect(ctx, px, py, pw, ph, 5, alpha(BG, a));
        ctx.fill(px, py + HEADER_H, px + SIDE_W, py + ph, alpha(SIDE_BG, a));
        ctx.fill(px + SIDE_W - 1, py + HEADER_H, px + SIDE_W, py + ph, alpha(HAIR, a));
        ctx.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, alpha(HAIR, a));

        // masthead: bold caps, a short accent rule underneath, then the bomb
        Text habib = Text.literal("HABIB").styled(st -> st.withBold(true));
        ctx.drawText(textRenderer, habib, px + 12, py + 7, alpha(TEXT, a), false);
        int habibW = textRenderer.getWidth(habib);
        txt(ctx, " CLIENT", px + 12 + habibW, py + 7, alpha(MUTED, a));
        drawBombIcon(ctx, px + 12 + habibW + textRenderer.getWidth(" CLIENT") + 8, py + 8, a);

        // version + update status (click to check / install)
        boolean pillOver = inside(mx, my, pillX() - 4, py + 5, pillW() + 8, 14);
        int pillCol = Updater.highlighted() ? ACCENT : pillOver ? TEXT : MUTED;
        txt(ctx, pillText(), pillX(), py + 11, alpha(pillCol, a * (Updater.highlighted() || pillOver ? 1f : 0.75f)));

        // breadcrumb: where you are
        txtRight(ctx, tab.label.toUpperCase(Locale.ROOT), px + pw - 32, py + 11, alpha(MUTED, a * 0.7f));

        // close button
        boolean closeHover = inside(mx, my, px + pw - 24, py + 7, 16, 16);
        txtCentered(ctx, "x", px + pw - 16, py + 11, alpha(closeHover ? ACCENT : MUTED, a));

        // sidebar
        int base = py + HEADER_H + 8;
        txt(ctx, "MODULES", px + 14, base, alpha(MUTED, a * 0.6f));
        ctx.fill(px + 14 + textRenderer.getWidth("MODULES") + 6, base + 4, px + SIDE_W - 10, base + 5, alpha(HAIR, a));
        txt(ctx, "CLIENT", px + 14, base + 92, alpha(MUTED, a * 0.6f));
        ctx.fill(px + 14 + textRenderer.getWidth("CLIENT") + 6, base + 96, px + SIDE_W - 10, base + 97, alpha(HAIR, a));

        float targetY = tabY(tab.ordinal());
        if (highlightY < 0f) highlightY = targetY;
        highlightY = approach(highlightY, targetY, dt, 16f);
        int hy = Math.round(highlightY);
        ctx.fill(px + 6, hy, px + SIDE_W - 1, hy + 18, alpha(ACCENT, a * 0.09f)); // active tab plate
        ctx.fill(px + 6, hy + 2, px + 8, hy + 16, alpha(ACCENT, a));              // marker follows the active tab

        for (int i = 0; i < TABS.length; i++) {
            boolean over = inside(mx, my, px + 6, tabY(i), SIDE_W - 12, 18);
            tabHover[i] = approach(tabHover[i], over ? 1f : 0f, dt, 18f);
            float strength = Math.max(TABS[i] == tab ? 1f : 0f, tabHover[i] * 0.8f);
            int nudge = Math.round(tabHover[i] * 2f);
            if (TABS[i] != tab) ctx.fill(px + 6, tabY(i), px + SIDE_W - 1, tabY(i) + 18, alpha(0xFFFFFFFF, a * 0.05f * tabHover[i]));
            txt(ctx, TABS[i].label, px + 18 + nudge, tabY(i) + 5, alpha(mix(MUTED, TEXT, strength), a));
            if (TABS[i].category == null && i < 10) {
                txtRight(ctx, i == 9 ? "0" : Integer.toString(i + 1), px + SIDE_W - 12, tabY(i) + 5, alpha(MUTED, a * 0.3f));
            }
            if (TABS[i].category != null) {
                int on = 0;
                int total = 0;
                for (HudModule m : HudManager.MODULES) {
                    if (m.category != TABS[i].category) continue;
                    total++;
                    if (m.enabled) on++;
                }
                txtRight(ctx, on + "/" + total, px + SIDE_W - 12, tabY(i) + 5,
                        alpha(MUTED, a * (0.45f + 0.4f * strength)));
            }
        }

        // content
        float ca = a * tabFade;
        int oy = cy + Math.round((1f - tabFade) * 8f);
        ctx.enableScissor(cx - 2, cy - 2, cx + cw + 2, cy + ch + 2);
        switch (tab) {
            case ANIMATIONS -> renderAnimations(ctx, mx, my, dt, now, ca, oy);
            case PACKS -> renderPacks(ctx, mx, my, dt, ca, oy);
            case OPTIMIZE -> renderOptimize(ctx, mx, my, dt, ca, oy);
            case EFFECTS -> renderEffects(ctx, mx, my, dt, ca, oy);
            case PRESETS_TAB -> renderPresets(ctx, mx, my, dt, ca, oy);
            case LAYOUT -> renderLayoutTab(ctx, mx, my, dt, ca, oy);
            default -> renderModules(ctx, mx, my, dt, ca);
        }
        ctx.disableScissor();
        scrollbar(ctx, a);
    }

    /** Thin scroll indicator on the panel's right edge, only when the list actually scrolls. */
    private void scrollbar(DrawContext ctx, float a) {
        if (tab.category == null && tab != Tab.PACKS) return;
        float max = maxScroll();
        if (max <= 0f) return;
        int x = px + pw - 6;
        int trackH = ch;
        int thumbH = thumbHeight();
        int thumbY = cy + Math.round((trackH - thumbH) * Math.min(1f, scroll / max));
        ctx.fill(x, cy, x + 2, cy + trackH, alpha(HAIR, a));
        ctx.fill(x, thumbY, x + 2, thumbY + thumbH, alpha(sbDrag ? ACCENT : MUTED, a * 0.8f));
    }

    private void renderModules(DrawContext ctx, int mx, int my, float dt, float ca) {
        List<HudModule> list = modulesFor(tab.category);
        int slide = Math.round((1f - tabFade) * 8f);
        for (int j = 0; j < list.size(); j++) {
            HudModule m = list.get(j);
            int idx = HudManager.MODULES.indexOf(m);
            int ry = rowY(j) + slide;
            if (ry + ROW_H < cy || ry > cy + ch) continue;

            boolean over = inside(mx, my, cx, ry, cw, ROW_H) && inside(mx, my, cx, cy, cw, ch);
            hover[idx] = approach(hover[idx], over ? 1f : 0f, dt, 18f);

            // flat list: only the hovered row gets a plate, rows are separated by hairlines
            rrect(ctx, cx, ry, cw, ROW_H, 5, alpha(ROW_HOVER, ca * hover[idx]));
            ctx.fill(cx + 10, ry + ROW_H, cx + cw - 10, ry + ROW_H + 1, alpha(HAIR, ca));
            stripe(ctx, cx, ry, ROW_H, toggle[idx], m.color | 0xFF000000, ca);
            txt(ctx, m.label, cx + 10, ry + 9, alpha(mix(MUTED, TEXT, toggle[idx]), ca));

            // size - [1.0x] +   (shown on hover so idle rows stay calm)
            float hv = hover[idx] * ca;
            if (hv > 0.02f) {
                smallButton(ctx, mx, my, cx + cw - 128, ry + 6, "-", hv);
                txtCentered(ctx, String.format(Locale.ROOT, "%.1fx", m.scale), cx + cw - 95, ry + 9, alpha(MUTED, hv));
                smallButton(ctx, mx, my, cx + cw - 76, ry + 6, "+", hv);
            }

            // colour swatch
            rrect(ctx, cx + cw - 54, ry + 7, 12, 12, 3, alpha(m.color | 0xFF000000, ca * (0.55f + 0.45f * toggle[idx])));

            // on/off switch
            drawSwitch(ctx, cx + cw - 34, ry + 7, toggle[idx], ca);
        }

        if (list.isEmpty()) {
            txt(ctx, "Nothing here yet.", cx, cy + 4, alpha(MUTED, ca));
        }
    }

    /** 11x11 pixel-art bomb drawn next to the title (no texture needed). */
    private void drawBombIcon(DrawContext ctx, int x, int y, float a) {
        for (int yy = 0; yy < 11; yy++) {
            for (int xx = 0; xx < 11; xx++) {
                double dx = xx - 4.5;
                double dy = yy - 6.5;
                if (dx * dx + dy * dy <= 20.25) {
                    ctx.fill(x + xx, y + yy, x + xx + 1, y + yy + 1, alpha(0xFF4A4A4A, a));
                }
            }
        }
        ctx.fill(x + 2, y + 5, x + 4, y + 6, alpha(0xFFB8B8B8, a)); // shine
        ctx.fill(x + 2, y + 6, x + 3, y + 7, alpha(0xFFB8B8B8, a));
        ctx.fill(x + 5, y + 1, x + 8, y + 3, alpha(0xFF8A8A8A, a)); // cap
        ctx.fill(x + 8, y + 1, x + 9, y + 2, alpha(0xFFC8AA78, a)); // fuse
        ctx.fill(x + 9, y, x + 10, y + 1, alpha(0xFFC8AA78, a));
        ctx.fill(x + 10, y - 1, x + 11, y, alpha(0xFFFFC43C, a));   // spark
        ctx.fill(x + 9, y - 1, x + 10, y, alpha(0xFFFF9B2E, a));
    }

    private String pillText() {
        return textRenderer.trimToWidth("v" + Updater.currentVersion() + "  -  " + Updater.statusText(), pw - 250);
    }

    private int pillW() {
        return textRenderer.getWidth(pillText());
    }

    private int pillX() {
        Text habib = Text.literal("HABIB").styled(st -> st.withBold(true));
        return px + 12 + textRenderer.getWidth(habib) + textRenderer.getWidth(" CLIENT") + 8 + 11 + 12;
    }

    /** Thin status stripe on the left edge of a row: lit in the given colour when on. */
    private static void stripe(DrawContext ctx, int x, int y, int h, float t, int onColor, float a) {
        ctx.fill(x, y + 5, x + 2, y + h - 5, alpha(mix(0xFF2C2C32, onColor, t), a));
    }

    /** Section heading: accent tick plus caps title. */
    private void sectionTitle(DrawContext ctx, String s, int x, int y, float a) {
        ctx.drawText(textRenderer, Text.literal(s).styled(st -> st.withBold(true)), x, y, alpha(TEXT, a), false);
    }

    private void smallButton(DrawContext ctx, int mx, int my, int x, int y, String label, float a) {
        boolean over = inside(mx, my, x, y, 14, 14);
        border(ctx, x, y, 14, 14, alpha(over ? ACCENT : 0xFF303036, a));
        txtCentered(ctx, label, x + 7, y + 3, alpha(over ? TEXT : MUTED, a));
    }

    private void drawSwitch(DrawContext ctx, int x, int y, float t, float a) {
        // outlined track with a square knob; fills with the accent when on
        ctx.fill(x, y, x + 24, y + 12, alpha(mix(0x00000000, ACCENT, t), a));       // solid accent when on
        border(ctx, x, y, 24, 12, alpha(mix(0xFF44444B, ACCENT, t), a));
        int knobX = x + 3 + Math.round(t * 12f);
        ctx.fill(knobX, y + 3, knobX + 6, y + 9, alpha(mix(MUTED, 0xFFFFFFFF, t), a));
    }

    private void renderAnimations(DrawContext ctx, int mx, int my, float dt, long now, float ca, int oy) {
        int style = HabibConfig.swingStyle;
        float k = HabibConfig.swingIntensity;

        sectionTitle(ctx, "Sword & pearl animation", cx, oy, ca);
        txt(ctx, "Sword hits and right-click pearl throws. Misses stay vanilla.", cx, oy + 12, alpha(MUTED, ca));

        // style selector
        int segY = oy + 30;
        int segH = 22;
        int segW = cw / SwingAnimator.COUNT;
        rrect(ctx, cx, segY, segW * SwingAnimator.COUNT, segH, 6, alpha(ROW, ca));
        float targetX = cx + style * segW;
        if (segmentX < 0f) segmentX = targetX;
        segmentX = approach(segmentX, targetX, dt, 16f);
        rrect(ctx, Math.round(segmentX) + 2, segY + 2, segW - 4, segH - 4, 5, alpha(ACCENT, ca * 0.9f));
        for (int i = 0; i < SwingAnimator.COUNT; i++) {
            boolean selected = i == style;
            txtCentered(ctx, SwingAnimator.NAMES[i], cx + i * segW + segW / 2, segY + 7,
                    alpha(selected ? 0xFFFFFFFF : MUTED, ca));
        }

        // only-on-hit toggle
        onHitToggle = approach(onHitToggle, HabibConfig.swingOnlyOnHit ? 1f : 0f, dt, 16f);
        boolean overHit = inside(mx, my, cx, oy + 56, cw, 22);
        rrect(ctx, cx, oy + 56, cw, 22, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(overHit)), ca));
        stripe(ctx, cx, oy + 56, 22, onHitToggle, ACCENT, ca);
        txt(ctx, "Full animation only on hit", cx + 10, oy + 63, alpha(mix(MUTED, TEXT, onHitToggle), ca));
        drawSwitch(ctx, cx + cw - 34, oy + 61, onHitToggle, ca);

        // intensity slider
        float live = style > 0 ? 1f : 0.4f;
        int slY = oy + SLIDER_Y;
        txt(ctx, "Intensity", cx, slY, alpha(TEXT, ca * live));
        txtRight(ctx, Math.round(k * 100f) + "%", cx + cw, slY, alpha(MUTED, ca));
        int trackY = slY + 16;
        float t = (k - 0.25f) / 1.75f;
        int knobX = cx + Math.round(cw * t);
        ctx.fill(cx, trackY + 2, cx + cw, trackY + 4, alpha(OFF, ca));
        ctx.fill(cx, trackY + 2, Math.max(cx + 2, knobX), trackY + 4, alpha(ACCENT, ca * live));
        ctx.fill(knobX - 2, trackY - 2, knobX + 2, trackY + 8, alpha(0xFFEDEDED, ca * live));

        // curve preview
        int pvY = slY + 38;
        txt(ctx, "Swing curve", cx, pvY, alpha(MUTED, ca));
        int plotY = pvY + 12;
        int plotH = Math.max(24, Math.min(60, oy + ch - plotY - 4));
        rrect(ctx, cx, plotY, cw, plotH, 5, alpha(ROW, ca));
        for (int x = 4; x < cw - 4; x += 3) {
            float pp = (x - 4) / (float) (cw - 8);
            float v = Math.min(1.3f, SwingAnimator.curve(style, k, pp));
            int bh = Math.round(v * (plotH - 10) / 1.3f);
            ctx.fill(cx + x, plotY + plotH - 4 - bh, cx + x + 2, plotY + plotH - 4, alpha(ACCENT, ca * 0.55f));
        }
        float cycle = (float) ((now / 1_000_000_000.0) % 1.6) / 1.6f;
        int markerX = cx + 4 + Math.round(cycle * (cw - 8));
        ctx.fill(markerX, plotY + 3, markerX + 1, plotY + plotH - 3, alpha(TEXT, ca * 0.8f));
    }

    // ------------------------------------------------------------------ resource packs

    private int listTop() {
        return cy + 46;
    }

    private int listH() {
        return ch - 46;
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private List<ResourcePackProfile> installedPacks() {
        List<ResourcePackProfile> list = new ArrayList<>();
        ResourcePackManager mgr = mc().getResourcePackManager();
        boolean enabledView = packView == 2;
        if (enabledView) {
            // highest priority first (the manager lists lowest priority first)
            List<String> ids = new ArrayList<>(mgr.getEnabledIds());
            java.util.Collections.reverse(ids);
            for (String id : ids) {
                ResourcePackProfile p = mgr.getProfile(id);
                if (p != null && id.startsWith("file/")) list.add(p); // your packs, not built-in/mod ones
            }
        } else {
            for (ResourcePackProfile p : mgr.getProfiles()) {
                if (p.getId().startsWith("file/")) list.add(p);
            }
        }
        if (packsInstalledView && packField != null) {
            String q = packField.getText().trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                list.removeIf(p -> !p.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(q)
                        && !p.getId().toLowerCase(Locale.ROOT).contains(q));
            }
        }
        if (!enabledView) {
            list.sort((a, b) -> a.getDisplayName().getString().compareToIgnoreCase(b.getDisplayName().getString()));
        }
        return list;
    }

    private boolean packEnabled(String id) {
        return mc().getResourcePackManager().getEnabledIds().contains(id);
    }

    private void setPackEnabled(String id, boolean on) {
        MinecraftClient mc = mc();
        ResourcePackManager mgr = mc.getResourcePackManager();
        List<String> ids = new ArrayList<>(mgr.getEnabledIds());
        ids.remove(id);
        if (on) ids.add(id); // last = highest priority
        mgr.setEnabledProfiles(ids);
        mc.options.refreshResourcePacks(mgr); // saves options and reloads resources if the list changed
    }

    private float hitParticleToggle;
    private float pearlToggle;
    private float consumableToggle;
    private float totemParticleToggle;

    private void optimizeRow(DrawContext ctx, int mx, int my, int y, String title, String sub, float t, float ca) {
        boolean over = inside(mx, my, cx, y, cw, 30);
        rrect(ctx, cx, y, cw, 30, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(over)), ca));
        stripe(ctx, cx, y, 30, t, ACCENT, ca);
        txt(ctx, title, cx + 10, y + 5, alpha(mix(MUTED, TEXT, t), ca));
        txt(ctx, textRenderer.trimToWidth(sub, cw - 60), cx + 10, y + 16, alpha(MUTED, ca));
        drawSwitch(ctx, cx + cw - 34, y + 9, t, ca);
    }

    private void renderOptimize(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "Optimizers", cx, oy, ca);
        txt(ctx, "Visual and performance only. All off until you turn them on.", cx, oy + 12, alpha(MUTED, ca));

        crystalToggle = approach(crystalToggle, HabibConfig.crystalOptimizer ? 1f : 0f, dt, 16f);
        particleToggle = approach(particleToggle, HabibConfig.noExplosionParticles ? 1f : 0f, dt, 16f);
        totemParticleToggle = approach(totemParticleToggle, HabibConfig.noTotemParticles ? 1f : 0f, dt, 16f);
        hitParticleToggle = approach(hitParticleToggle, HabibConfig.noHitParticles ? 1f : 0f, dt, 16f);
        pearlToggle = approach(pearlToggle, HabibConfig.pearlOptimizer ? 1f : 0f, dt, 16f);
        consumableToggle = approach(consumableToggle, HabibConfig.consumableOptimizer ? 1f : 0f, dt, 16f);
        optimizeRow(ctx, mx, my, oy + 26, "Crystal optimizer", "Crystals vanish the instant you hit them.", crystalToggle, ca);
        optimizeRow(ctx, mx, my, oy + 58, "No explosion particles", "Cuts the lag from crystal explosions.", particleToggle, ca);
        optimizeRow(ctx, mx, my, oy + 90, "No totem particles", "Hides the totem pop burst. Pop counter still works.", totemParticleToggle, ca);
        optimizeRow(ctx, mx, my, oy + 122, "No hit particles", "Hides crit, enchant-hit and damage particles.", hitParticleToggle, ca);
        optimizeRow(ctx, mx, my, oy + 154, "Ender pearl optimizer", "Hides pearl trail and landing particles. Pearls work as normal.", pearlToggle, ca);
        optimizeRow(ctx, mx, my, oy + 186, "Consumable optimizer", "Hides eating, drinking and splash potion particles.", consumableToggle, ca);
    }

    private float lowHealthToggle;
    private float popFlashToggle;
    private float popMessageToggle;
    private float lowArmorToggle;

    private void renderEffects(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "Effects", cx, oy, ca);
        txt(ctx, "Screen alerts and chat notices. All off until you turn them on.", cx, oy + 12, alpha(MUTED, ca));

        lowHealthToggle = approach(lowHealthToggle, HabibConfig.lowHealthWarning ? 1f : 0f, dt, 16f);
        popFlashToggle = approach(popFlashToggle, HabibConfig.popFlash ? 1f : 0f, dt, 16f);
        popMessageToggle = approach(popMessageToggle, HabibConfig.popMessages ? 1f : 0f, dt, 16f);
        lowArmorToggle = approach(lowArmorToggle, HabibConfig.lowArmorWarning ? 1f : 0f, dt, 16f);
        optimizeRow(ctx, mx, my, oy + 28, "Low health warning", "Pulsing red screen edge at 3 hearts or less.", lowHealthToggle, ca);
        optimizeRow(ctx, mx, my, oy + 62, "Totem pop flash", "Gold when you pop, green when your target does.", popFlashToggle, ca);
        optimizeRow(ctx, mx, my, oy + 96, "Totem pop messages", "A chat line when you or your target pops.", popMessageToggle, ca);
        optimizeRow(ctx, mx, my, oy + 130, "Low armor warning", "Orange pulse when a piece is under 10% durability.", lowArmorToggle, ca);
    }

    private void effectsClicked(double mx, double my) {
        if (inside(mx, my, cx, cy + 28, cw, 30)) {
            HabibConfig.lowHealthWarning = !HabibConfig.lowHealthWarning;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 62, cw, 30)) {
            HabibConfig.popFlash = !HabibConfig.popFlash;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 96, cw, 30)) {
            HabibConfig.popMessages = !HabibConfig.popMessages;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 130, cw, 30)) {
            HabibConfig.lowArmorWarning = !HabibConfig.lowArmorWarning;
            HabibConfig.save();
        }
    }

    // ------------------------------------------------------------------ graphics presets

    private record Preset(String name, String particles, boolean vsync, int fps, boolean shadows,
                          int view, int sim, int blend, double entity, String[] lines) {}

    private static final Preset[] PRESETS = {
            new Preset("Minimal", "MINIMAL", false, 260, false, 6, 5, 0, 0.5,
                    new String[]{"6 chunks", "Minimal particles", "No shadows", "Unlimited FPS"}),
            new Preset("Medium", "DECREASED", false, 260, true, 10, 8, 1, 0.75,
                    new String[]{"10 chunks", "Fewer particles", "Shadows on", "Unlimited FPS"}),
            new Preset("High", "ALL", false, 260, true, 12, 10, 2, 1.0,
                    new String[]{"12 chunks", "All particles", "Shadows on", "Unlimited FPS"}),
    };

    private float[] presetHover = new float[3];

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumByName(SimpleOption option, String name) {
        Object cur = option.getValue();
        if (cur instanceof Enum<?> e) {
            for (Object c : e.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) c).name().equals(name)) {
                    option.setValue(c);
                    return;
                }
            }
        }
    }

    private static boolean enumIs(SimpleOption<?> option, String name) {
        return option.getValue() instanceof Enum<?> e && e.name().equals(name);
    }

    private static boolean presetActive(Preset p) {
        var o = mc().options;
        return enumIs(o.getParticles(), p.particles())
                && o.getEnableVsync().getValue() == p.vsync()
                && o.getMaxFps().getValue() == p.fps()
                && o.getEntityShadows().getValue() == p.shadows()
                && o.getViewDistance().getValue() == p.view()
                && o.getSimulationDistance().getValue() == p.sim()
                && o.getBiomeBlendRadius().getValue() == p.blend()
                && Math.abs(o.getEntityDistanceScaling().getValue() - p.entity()) < 0.001;
    }

    private void applyPreset(Preset p) {
        var o = mc().options;
        setEnumByName(o.getParticles(), p.particles());
        o.getEnableVsync().setValue(p.vsync());
        o.getMaxFps().setValue(p.fps());
        o.getEntityShadows().setValue(p.shadows());
        o.getViewDistance().setValue(p.view());
        o.getSimulationDistance().setValue(p.sim());
        o.getBiomeBlendRadius().setValue(p.blend());
        o.getEntityDistanceScaling().setValue(p.entity());
        o.write();
        presetNote = p.name() + " applied.";
    }

    private int presetCardW() {
        return (cw - 12) / 3;
    }

    private void renderPresets(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "Graphics presets", cx, oy, ca);
        String current = "Custom";
        for (Preset p : PRESETS) if (presetActive(p)) current = p.name();
        txt(ctx, "Current: " + current, cx, oy + 12, alpha(MUTED, ca));

        int w = presetCardW();
        for (int k = 0; k < PRESETS.length; k++) {
            Preset p = PRESETS[k];
            int x = cx + k * (w + 6);
            int y = oy + 34;
            boolean active = presetActive(p);
            boolean over = inside(mx, my, x, y, w, 112);
            presetHover[k] = approach(presetHover[k], over ? 1f : 0f, dt, 16f);
            if (active) rrect(ctx, x - 1, y - 1, w + 2, 114, 6, alpha(ACCENT, ca));
            rrect(ctx, x, y, w, 112, 5, alpha(mix(ROW, ROW_HOVER, presetHover[k]), ca));
            txtCentered(ctx, p.name(), x + w / 2, y + 8, alpha(TEXT, ca));
            ctx.fill(x + 10, y + 21, x + w - 10, y + 22, alpha(OFF, ca));
            for (int l = 0; l < p.lines().length; l++) {
                txtCentered(ctx, textRenderer.trimToWidth(p.lines()[l], w - 8), x + w / 2, y + 30 + l * 13,
                        alpha(MUTED, ca));
            }
            int by = y + 86;
            rrect(ctx, x + 8, by, w - 16, 18, 5,
                    alpha(active ? OFF : mix(ACCENT_DIM, 0xFFFFFFFF, presetHover[k] * 0.15f), ca));
            txtCentered(ctx, active ? "Active" : "Apply", x + w / 2, by + 5, alpha(active ? MUTED : TEXT, ca));
        }
        txt(ctx, presetNote.isEmpty() ? "Changes your normal video settings." : presetNote, cx, oy + 156,
                alpha(presetNote.isEmpty() ? MUTED : ACCENT, ca));
        txt(ctx, "Clouds and graphics mode are left as you set them.", cx, oy + 168, alpha(MUTED, ca));
    }

    private void presetsClicked(double mx, double my) {
        int w = presetCardW();
        for (int k = 0; k < PRESETS.length; k++) {
            if (inside(mx, my, cx + k * (w + 6), cy + 34, w, 112)) {
                applyPreset(PRESETS[k]);
                return;
            }
        }
    }

    private void optimizeClicked(double mx, double my) {
        if (inside(mx, my, cx, cy + 26, cw, 30)) {
            HabibConfig.crystalOptimizer = !HabibConfig.crystalOptimizer;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 58, cw, 30)) {
            HabibConfig.noExplosionParticles = !HabibConfig.noExplosionParticles;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 90, cw, 30)) {
            HabibConfig.noTotemParticles = !HabibConfig.noTotemParticles;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 122, cw, 30)) {
            HabibConfig.noHitParticles = !HabibConfig.noHitParticles;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 154, cw, 30)) {
            HabibConfig.pearlOptimizer = !HabibConfig.pearlOptimizer;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 186, cw, 30)) {
            HabibConfig.consumableOptimizer = !HabibConfig.consumableOptimizer;
            HabibConfig.save();
        }
    }

    private static String placeholderFor(int view) {
        return switch (view) {
            case 1 -> "Search your installed packs...";
            case 2 -> "Search your enabled packs...";
            default -> "Search Modrinth resource packs...";
        };
    }

    private void setPackView(int view) {
        if (view == packView) return;
        viewQuery[packView] = packField.getText();
        packView = view;
        packsInstalledView = view != 0;
        packField.setPlaceholder(Text.literal(placeholderFor(view)));
        packField.setText(viewQuery[view]);
        searchAt = -1L; // switching views must not trigger a Modrinth search
        scroll = 0f;
        scrollTarget = 0f;
        setFocused(null);
        if (packsInstalledView) mc().getResourcePackManager().scanPacks();
    }

    private void runSearch() {
        searchedOnce = true;
        searchAt = -1L;
        int gen = ++searchGen;
        packsStatus = "Searching...";
        String query = packField == null ? "" : packField.getText();
        ModrinthPacks.search(query).whenComplete((list, err) -> mc().execute(() -> {
            if (gen != searchGen) return;
            if (err != null) {
                hits = new ArrayList<>();
                packsStatus = "Couldn't reach Modrinth: " + errorText(err);
            } else {
                hits = new ArrayList<>(list);
                packsStatus = list.isEmpty() ? "No packs found for " + ModrinthPacks.mcVersion() + "." : "";
            }
            scroll = 0f;
            scrollTarget = 0f;
        }));
    }

    private static String errorText(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException
                || (t.getCause() != null && t.getClass() == RuntimeException.class)) && t.getCause() != null) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private void installOrToggle(Hit hit) {
        String id = hit.id();
        if (busy.contains(id)) return;
        ResourcePackManager mgr = mc().getResourcePackManager();
        String packId = ModrinthPacks.packIdFor(id);
        if (packId != null && mgr.getProfile(packId) != null) {
            setPackEnabled(packId, !packEnabled(packId));
            return;
        }
        packErrors.remove(id);
        busy.add(id);
        ModrinthPacks.install(hit).whenComplete((pid, err) -> mc().execute(() -> {
            busy.remove(id);
            if (err != null) {
                packErrors.put(id, errorText(err));
                return;
            }
            ResourcePackManager m = mc().getResourcePackManager();
            m.scanPacks();
            if (m.getProfile(pid) == null) {
                packErrors.put(id, "Minecraft didn't recognise this pack");
                return;
            }
            setPackEnabled(pid, true); // enable straight away
        }));
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    private void renderPacks(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        // view switch: Modrinth | Installed | Enabled
        int third = cw / 3;
        rrect(ctx, cx, oy, third * 3, 20, 6, alpha(ROW, ca));
        float targetX = cx + packView * third;
        if (packSegX < 0f) packSegX = targetX;
        packSegX = approach(packSegX, targetX, dt, 16f);
        rrect(ctx, Math.round(packSegX) + 2, oy + 2, third - 4, 16, 5, alpha(ACCENT, ca * 0.9f));
        String[] names = {"Modrinth", "Installed", "Enabled"};
        for (int v = 0; v < 3; v++) {
            txtCentered(ctx, names[v], cx + v * third + third / 2, oy + 6,
                    alpha(packView == v ? 0xFFFFFFFF : MUTED, ca));
        }

        // search box / hint
        rrect(ctx, cx, oy + 24, cw, 18, 5, alpha(ROW, ca));
        packField.setX(cx + 8);
        packField.setY(oy + 24 + 5);
        packField.setWidth(cw - 16);
        packField.setHeight(8);
        packField.render(ctx, mx, my, 0f);
        if (!packsInstalledView && searchAt > 0 && System.currentTimeMillis() >= searchAt) runSearch();

        int top = listTop();
        int h = listH();
        ctx.enableScissor(cx - 2, top, cx + cw + 2, top + h);
        if (packsInstalledView) renderInstalled(ctx, mx, my, dt, ca, top, h);
        else renderHits(ctx, mx, my, ca, top, h);
        ctx.disableScissor();
    }

    private void renderHits(DrawContext ctx, int mx, int my, float ca, int top, int h) {
        if (hits.isEmpty()) {
            txt(ctx, textRenderer.trimToWidth(packsStatus, cw), cx + 2, top + 6, alpha(MUTED, ca));
            return;
        }
        ResourcePackManager mgr = mc().getResourcePackManager();
        for (int j = 0; j < hits.size(); j++) {
            int ry = top + j * PACK_STRIDE - Math.round(scroll);
            if (ry + PACK_ROW_H < top || ry > top + h) continue;
            Hit hit = hits.get(j);
            boolean over = inside(mx, my, cx, ry, cw, PACK_ROW_H) && inside(mx, my, cx, top, cw, h);
            rrect(ctx, cx, ry, cw, PACK_ROW_H, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(over)), ca));

            int textW = cw - 84;
            txt(ctx, textRenderer.trimToWidth(hit.title(), textW), cx + 8, ry + 4, alpha(TEXT, ca));
            String err = packErrors.get(hit.id());
            if (err != null) {
                txt(ctx, textRenderer.trimToWidth(err, textW), cx + 8, ry + 14, alpha(0xFFFF6B6B, ca));
            } else {
                txt(ctx, textRenderer.trimToWidth(hit.description(), textW), cx + 8, ry + 14, alpha(MUTED, ca));
            }
            txt(ctx, textRenderer.trimToWidth("by " + hit.author() + "  -  " + compact(hit.downloads()) + " downloads", textW),
                    cx + 8, ry + 24, alpha(0xFF6A6A6A, ca));

            // action button
            String packId = ModrinthPacks.packIdFor(hit.id());
            boolean installed = packId != null && mgr.getProfile(packId) != null;
            String label;
            int base;
            if (busy.contains(hit.id())) {
                label = "...";
                base = OFF;
            } else if (installed) {
                boolean on = packEnabled(packId);
                label = on ? "Disable" : "Enable";
                base = on ? 0xFF353535 : ACCENT_DIM;
            } else {
                label = packErrors.containsKey(hit.id()) ? "Retry" : "Install";
                base = ACCENT_DIM;
            }
            int bx = cx + cw - 64;
            int by = ry + 9;
            boolean bo = inside(mx, my, bx, by, 56, 18) && inside(mx, my, cx, top, cw, h);
            rrect(ctx, bx, by, 56, 18, 5, alpha(bo ? mix(base, 0xFFFFFFFF, 0.15f) : base, ca));
            txtCentered(ctx, label, bx + 28, by + 5, alpha(TEXT, ca));
        }
    }

    private void renderInstalled(DrawContext ctx, int mx, int my, float dt, float ca, int top, int h) {
        List<ResourcePackProfile> list = installedPacks();
        if (list.isEmpty()) {
            String q = packField.getText().trim();
            String none = q.isEmpty()
                    ? (packView == 2 ? "No resource packs are enabled." : "No packs yet - install some from the Modrinth tab.")
                    : "No " + (packView == 2 ? "enabled" : "installed") + " packs match \"" + q + "\".";
            txt(ctx, textRenderer.trimToWidth(none, cw), cx + 2, top + 6, alpha(MUTED, ca));
            return;
        }
        for (int j = 0; j < list.size(); j++) {
            int ry = top + j * INSTALLED_STRIDE - Math.round(scroll);
            if (ry + INSTALLED_ROW_H < top || ry > top + h) continue;
            ResourcePackProfile p = list.get(j);
            boolean on = packEnabled(p.getId());
            float t = approach(packToggle.getOrDefault(p.getId(), on ? 1f : 0f), on ? 1f : 0f, dt, 16f);
            packToggle.put(p.getId(), t);

            boolean over = inside(mx, my, cx, ry, cw, INSTALLED_ROW_H) && inside(mx, my, cx, top, cw, h);
            rrect(ctx, cx, ry, cw, INSTALLED_ROW_H, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(over)), ca));
            stripe(ctx, cx, ry, INSTALLED_ROW_H, t, ACCENT, ca);
            String name = p.getDisplayName().getString();
            txt(ctx, textRenderer.trimToWidth(name, cw - 60), cx + 10, ry + 9, alpha(mix(MUTED, TEXT, t), ca));
            drawSwitch(ctx, cx + cw - 34, ry + 7, t, ca);
        }
    }

    private void packsClicked(Click click, boolean doubled, double mx, double my) {
        int third = cw / 3;
        if (inside(mx, my, cx, cy, third * 3, 20)) {
            setPackView(Math.min(2, (int) ((mx - cx) / third)));
            return;
        }
        if (inside(mx, my, cx, cy + 24, cw, 18)) {
            setFocused(packField);
            packField.mouseClicked(click, doubled);
            return;
        }
        setFocused(null);

        int top = listTop();
        if (!inside(mx, my, cx, top, cw, listH())) return;
        if (packsInstalledView) {
            List<ResourcePackProfile> list = installedPacks();
            for (int j = 0; j < list.size(); j++) {
                int ry = top + j * INSTALLED_STRIDE - Math.round(scroll);
                if (inside(mx, my, cx, ry, cw, INSTALLED_ROW_H)) {
                    String id = list.get(j).getId();
                    setPackEnabled(id, !packEnabled(id));
                    return;
                }
            }
        } else {
            for (int j = 0; j < hits.size(); j++) {
                int ry = top + j * PACK_STRIDE - Math.round(scroll);
                if (inside(mx, my, cx + cw - 64, ry + 9, 56, 18)) {
                    installOrToggle(hits.get(j));
                    return;
                }
            }
        }
    }

    private static final String[] ARMOR_LAYOUTS = {"Vertical", "Horizontal"};
    private static final String[] ARMOR_DURABILITY = {"Uses left", "Percent", "Vanilla bar", "Hidden"};

    private void armorOptionRow(DrawContext ctx, int mx, int my, int y, String label, String value, float ca) {
        boolean over = inside(mx, my, cx, y, cw, 22);
        rrect(ctx, cx, y, cw, 22, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(over)), ca));
        txt(ctx, label, cx + 10, y + 7, alpha(TEXT, ca));
        txtRight(ctx, "< " + value + " >", cx + cw - 10, y + 7, alpha(over ? ACCENT : MUTED, ca));
    }

    private void renderLayoutTab(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "HUD layout", cx, oy, ca);
        txt(ctx, "Drag modules anywhere on screen, resize them with the", cx, oy + 14, alpha(MUTED, ca));
        txt(ctx, "scroll wheel and recolour them with right-click.", cx, oy + 26, alpha(MUTED, ca));

        boolean overA = inside(mx, my, cx, oy + 48, 150, 24);
        boolean overB = inside(mx, my, cx + 160, oy + 48, 110, 24);
        buttonHoverA = approach(buttonHoverA, overA ? 1f : 0f, dt, 18f);
        buttonHoverB = approach(buttonHoverB, overB ? 1f : 0f, dt, 18f);
        rrect(ctx, cx, oy + 48, 150, 24, 6, alpha(mix(ACCENT_DIM, ACCENT, buttonHoverA), ca));
        txtCentered(ctx, "Open layout editor", cx + 75, oy + 56, alpha(0xFFFFFFFF, ca));
        rrect(ctx, cx + 160, oy + 48, 110, 24, 6, alpha(mix(ROW, ROW_HOVER, buttonHoverB), ca));
        txtCentered(ctx, "Reset layout", cx + 215, oy + 56, alpha(mix(MUTED, TEXT, buttonHoverB), ca));

        // background box on/off
        bgToggle = approach(bgToggle, HabibConfig.hudBackground ? 1f : 0f, dt, 16f);
        boolean overC = inside(mx, my, cx, oy + 84, cw, 26);
        rrect(ctx, cx, oy + 84, cw, 26, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(overC)), ca));
        stripe(ctx, cx, oy + 84, 26, bgToggle, ACCENT, ca);
        txt(ctx, "Box behind HUD", cx + 10, oy + 93, alpha(mix(MUTED, TEXT, bgToggle), ca));
        txtRight(ctx, HabibConfig.hudBackground ? "On" : "Text only", cx + cw - 44, oy + 93, alpha(MUTED, ca));
        drawSwitch(ctx, cx + cw - 34, oy + 91, bgToggle, ca);

        // armor HUD options
        txt(ctx, "ARMOR HUD", cx, oy + 120, alpha(MUTED, ca * 0.8f));
        ctx.fill(cx + textRenderer.getWidth("ARMOR HUD") + 6, oy + 124, cx + cw, oy + 125, alpha(HAIR, ca));
        heldToggle = approach(heldToggle, HabibConfig.armorShowHeld ? 1f : 0f, dt, 16f);
        armorOptionRow(ctx, mx, my, oy + 132, "Layout", ARMOR_LAYOUTS[HabibConfig.armorLayout], ca);
        armorOptionRow(ctx, mx, my, oy + 156, "Durability", ARMOR_DURABILITY[HabibConfig.armorDurability], ca);
        boolean overH = inside(mx, my, cx, oy + 180, cw, 22);
        rrect(ctx, cx, oy + 180, cw, 22, 5, alpha(mix(ROW, ROW_HOVER, rowHoverAnim(overH)), ca));
        stripe(ctx, cx, oy + 180, 22, heldToggle, ACCENT, ca);
        txt(ctx, "Show held item", cx + 10, oy + 187, alpha(mix(MUTED, TEXT, heldToggle), ca));
        drawSwitch(ctx, cx + cw - 34, oy + 185, heldToggle, ca);
    }

    private void renderLayoutMode(DrawContext ctx, int mx, int my, float dt) {
        for (HudModule m : HudManager.MODULES) {
            if (!m.enabled) continue;
            int c = m == dragging ? 0xFF55FF55 : m.contains(mx, my) ? 0xFFFFFFFF : 0x66FFFFFF;
            border(ctx, m.x - 1, m.y - 1, m.scaledWidth() + 2, m.scaledHeight() + 2, alpha(c, open));
        }

        String hint = "Drag = move   |   Scroll = resize   |   Right-click = colour   |   Esc = back";
        int hw = textRenderer.getWidth(hint) + 20;
        rrect(ctx, (width - hw) / 2, 8, hw, 20, 6, alpha(0xE0131313, open));
        txtCentered(ctx, hint, width / 2, 14, alpha(TEXT, open));

        boolean overA = inside(mx, my, width / 2 - 104, height - 32, 100, 22);
        boolean overB = inside(mx, my, width / 2 + 4, height - 32, 100, 22);
        buttonHoverA = approach(buttonHoverA, overA ? 1f : 0f, dt, 18f);
        buttonHoverB = approach(buttonHoverB, overB ? 1f : 0f, dt, 18f);
        rrect(ctx, width / 2 - 104, height - 32, 100, 22, 6, alpha(mix(0xE0131313, ACCENT, buttonHoverA), open));
        txtCentered(ctx, "Done", width / 2 - 54, height - 25, alpha(TEXT, open));
        rrect(ctx, width / 2 + 4, height - 32, 100, 22, 6, alpha(mix(0xE0131313, ROW_HOVER, buttonHoverB), open));
        txtCentered(ctx, "Reset layout", width / 2 + 54, height - 25, alpha(TEXT, open));
    }

    // ------------------------------------------------------------------ input

    private HudModule topModuleAt(double mx, double my) {
        List<HudModule> list = HudManager.MODULES;
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (m.enabled && m.contains(mx, my)) return m;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        geometry();

        if (layoutMode) return layoutClicked(mx, my, button);
        if (button != 0) return false;

        if (!inside(mx, my, px - 4, py - 4, pw + 8, ph + 8)) {
            close(); // click on the dimmed background
            return true;
        }
        if (scrollbarHit(mx, my)) {
            sbDrag = true;
            dragScrollbar(my);
            return true;
        }
        if (inside(mx, my, px + pw - 24, py + 7, 16, 16)) {
            close();
            return true;
        }
        if (inside(mx, my, pillX() - 4, py + 5, pillW() + 8, 14)) {
            Updater.onClick();
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, px + 6, tabY(i), SIDE_W - 12, 18)) {
                select(TABS[i]);
                return true;
            }
        }
        if (!inside(mx, my, cx, cy, cw, ch)) return false;

        switch (tab) {
            case ANIMATIONS -> animationsClicked(mx, my);
            case PACKS -> packsClicked(click, doubled, mx, my);
            case OPTIMIZE -> optimizeClicked(mx, my);
            case EFFECTS -> effectsClicked(mx, my);
            case PRESETS_TAB -> presetsClicked(mx, my);
            case LAYOUT -> layoutTabClicked(mx, my);
            default -> modulesClicked(mx, my);
        }
        return true;
    }

    private void modulesClicked(double mx, double my) {
        List<HudModule> list = modulesFor(tab.category);
        for (int j = 0; j < list.size(); j++) {
            int ry = rowY(j);
            if (!inside(mx, my, cx, ry, cw, ROW_H)) continue;
            HudModule m = list.get(j);
            if (inside(mx, my, cx + cw - 54, ry + 6, 14, 14)) {
                m.cycleColor();
            } else if (inside(mx, my, cx + cw - 128, ry + 6, 14, 14)) {
                m.scale = Math.max(0.5f, Math.round((m.scale - 0.1f) * 10f) / 10f);
            } else if (inside(mx, my, cx + cw - 76, ry + 6, 14, 14)) {
                m.scale = Math.min(3f, Math.round((m.scale + 0.1f) * 10f) / 10f);
            } else {
                m.enabled = !m.enabled; // switch or anywhere else on the row
            }
            HabibConfig.save();
            return;
        }
    }

    private void animationsClicked(double mx, double my) {
        int segW = cw / SwingAnimator.COUNT;
        if (inside(mx, my, cx, cy + 30, segW * SwingAnimator.COUNT, 22)) {
            int i = (int) ((mx - cx) / segW);
            HabibConfig.swingStyle = Math.max(0, Math.min(SwingAnimator.COUNT - 1, i));
            HabibConfig.save();
            return;
        }
        if (inside(mx, my, cx, cy + 56, cw, 22)) {
            HabibConfig.swingOnlyOnHit = !HabibConfig.swingOnlyOnHit;
            HabibConfig.save();
            return;
        }
        if (inside(mx, my, cx - 6, cy + SLIDER_Y + 12, cw + 12, 18)) {
            sliderDrag = true;
            setIntensity(mx);
        }
    }

    private void setIntensity(double mx) {
        float t = (float) Math.max(0.0, Math.min(1.0, (mx - cx) / cw));
        float k = 0.25f + 1.75f * t;
        HabibConfig.swingIntensity = Math.round(k * 20f) / 20f;
    }

    private void layoutTabClicked(double mx, double my) {
        if (inside(mx, my, cx, cy + 48, 150, 24)) {
            layoutMode = true;
        } else if (inside(mx, my, cx + 160, cy + 48, 110, 24)) {
            HudManager.resetAll();
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 84, cw, 26)) {
            HabibConfig.hudBackground = !HabibConfig.hudBackground;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 132, cw, 22)) {
            HabibConfig.armorLayout = (HabibConfig.armorLayout + 1) % ARMOR_LAYOUTS.length;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 156, cw, 22)) {
            HabibConfig.armorDurability = (HabibConfig.armorDurability + 1) % ARMOR_DURABILITY.length;
            HabibConfig.save();
        } else if (inside(mx, my, cx, cy + 180, cw, 22)) {
            HabibConfig.armorShowHeld = !HabibConfig.armorShowHeld;
            HabibConfig.save();
        }
    }

    private boolean layoutClicked(double mx, double my, int button) {
        if (inside(mx, my, width / 2 - 104, height - 32, 100, 22)) {
            layoutMode = false;
            return true;
        }
        if (inside(mx, my, width / 2 + 4, height - 32, 100, 22)) {
            HudManager.resetAll();
            HabibConfig.save();
            return true;
        }
        HudModule m = topModuleAt(mx, my);
        if (m == null) return false;
        if (button == 0) {
            dragging = m;
            grabX = mx - m.x;
            grabY = my - m.y;
            return true;
        }
        if (button == 1) {
            m.cycleColor();
            HabibConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x();
        double my = click.y();
        if (sbDrag) {
            geometry();
            dragScrollbar(my);
            return true;
        }
        if (sliderDrag) {
            geometry();
            setIntensity(mx);
            return true;
        }
        if (layoutMode && dragging != null && click.button() == 0) {
            int nx = (int) Math.round(mx - grabX);
            int ny = (int) Math.round(my - grabY);
            dragging.x = Math.max(0, Math.min(width - dragging.scaledWidth(), nx));
            dragging.y = Math.max(0, Math.min(height - dragging.scaledHeight(), ny));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (sliderDrag || dragging != null || sbDrag) {
            sbDrag = false;
            sliderDrag = false;
            dragging = null;
            HabibConfig.save();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        geometry();
        if (layoutMode) {
            HudModule m = topModuleAt(mx, my);
            if (m != null) {
                m.scale = Math.max(0.5f, Math.min(3f, m.scale + (float) Math.signum(vertical) * 0.1f));
                HabibConfig.save();
                return true;
            }
        } else if ((tab.category != null || tab == Tab.PACKS) && inside(mx, my, cx, cy, cw, ch)) {
            scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget - (float) vertical * ROW_STRIDE));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    /** Esc: leave the layout editor first, then close the menu. */
    @Override
    public void close() {
        if (layoutMode) {
            layoutMode = false;
            return;
        }
        super.close();
    }

    @Override
    public void removed() {
        HabibConfig.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
