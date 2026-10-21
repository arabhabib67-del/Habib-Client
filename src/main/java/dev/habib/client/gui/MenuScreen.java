package dev.habib.client.gui;

import dev.habib.client.gui.theme.*;
import dev.habib.client.gui.render.GlassRenderer;
import dev.habib.client.gui.render.UiTypography;
import dev.habib.client.config.HabibConfig;
import dev.habib.client.config.ProfileStore;
import dev.habib.client.hud.HudManager;
import dev.habib.client.hud.HudModule;
import dev.habib.client.hud.HudModule.Category;
import dev.habib.client.packs.ModrinthPacks;
import dev.habib.client.packs.ModrinthPacks.Hit;
import dev.habib.client.update.Updater;
import dev.habib.client.util.SwingAnimator;
import dev.habib.client.telemetry.UsageStats;
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
public class MenuScreen extends Screen implements GlassScreen {
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
        LAYOUT("HUD Editor", null),
        PROFILES("Profiles", null),
        APPEARANCE("Appearance", null),
        CAMERA("Camera", null),
        STATS("Stats", null);

        final String label;
        final Category category;

        Tab(String label, Category category) {
            this.label = label;
            this.category = category;
        }
    }

    private static final Tab[] TABS = Tab.values();

    private int ACCENT;
    private int ACCENT_DIM;
    private int BG;
    private int SIDE_BG;
    private int ROW;
    private int ROW_HOVER;
    private int TEXT;
    private int MUTED;
    private int OFF;
    private int HAIR;
    private static final int LAYOUT_SNAP_DISTANCE = 8;

    private static final int HEADER_H = 60;
    private static final int SIDE_W = 156;
    private static final int ROW_H = 42;
    private static final int ROW_STRIDE = 47;
    private static final int TAB_H = 24;

    private HudModule selectedHud;
    private TextFieldWidget hudXField, hudYField;
    private float hudListScroll, selectedScale = 1f, selectedToggle;
    private boolean hudScaleDrag;
    private int armorDropdown = -1;
    private float dropdownExpansion;
    private float uiScale = 1f;
    private boolean closing;
    private boolean confirmReset;
    private boolean appearanceDrag, blurDrag;
    private float modalOpacity;
    private float themeSegment, opacityVisual = 0.8f;
    private final float[] appearanceToggle = new float[8];
    private static final int[] ACCENTS = {0xFF008AFF, 0xFF9258E8, 0xFFFF9426, 0xFFF4F5F7};
    private TextFieldWidget searchField;
    private String query = "";
    private String cachedQuery = "";
    private List<HudModule> filteredModules = List.of();
    private final Map<Category, List<HudModule>> categoryModules = new java.util.EnumMap<>(Category.class);
    private Tab tab = Tab.INFO;

    @Override
    public boolean glassBlurAllowed() { return !layoutMode; }

    private void updatePalette(float dt) {
        ThemePalette p = ThemeManager.update(dt);
        ACCENT = p.accent; ACCENT_DIM = p.accent; BG = p.panel; SIDE_BG = p.sidebar;
        ROW = p.row; ROW_HOVER = p.hover; TEXT = p.text; MUTED = p.muted; OFF = p.track; HAIR = p.edge;
    }
    private boolean layoutMode;

    // animation state
    private float open;
    private float openingTime, openingShape;
    private float panelZoom() { return 0.9f + 0.1f * openingShape; }
    private float openingOffset() { return (1f - openingShape) * 18f; }
    private double panelMouseX(double x) { return (x / uiScale - (px + pw / 2f)) / panelZoom() + px + pw / 2f; }
    private double panelMouseY(double y) { return (y / uiScale - (py + ph / 2f) - openingOffset()) / panelZoom() + py + ph / 2f; }
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
    private final Map<Integer, Float> rowHoverVelocity = new HashMap<>();
    private float lastHoverBounce;
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
    private TextFieldWidget profileField;
    private String profileNote = "";
    private int pendingDelete = -1;
    private long pendingDeleteAt;
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
    private float intensityVisual = 1f;
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
        super(MinecraftClient.getInstance(), UiTypography.create(MinecraftClient.getInstance().textRenderer), Text.literal("Habib Client"));
        for (Category category : Category.values()) {
            categoryModules.put(category, HudManager.MODULES.stream().filter(m -> m.category == category).toList());
        }
        int n = HudManager.MODULES.size();
        toggle = new float[n];
        hover = new float[n];
        for (int i = 0; i < n; i++) {
            toggle[i] = HudManager.MODULES.get(i).enabled ? 1f : 0f;
        }
    }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, 0, 0, 150, 12, Text.literal("Search modules"));
        searchField.setMaxLength(64);
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Search modules..."));
        searchField.setText(query);
        searchField.setChangedListener(value -> {
            query = value.trim().toLowerCase(Locale.ROOT);
            scroll = scrollTarget = 0f;
            if (!query.isEmpty() && tab.category == null) { select(Tab.INFO); setFocused(searchField); }
        });
        addSelectableChild(searchField);
        hudXField = positionField("X"); hudYField = positionField("Y");
        hudXField.setChangedListener(value -> applyPosition(value, true));
        hudYField.setChangedListener(value -> applyPosition(value, false));
        if (selectedHud == null && !HudManager.MODULES.isEmpty()) selectedHud = HudManager.MODULES.getFirst();
        themeSegment = HabibConfig.appearance.themeMode.ordinal();
        updatePalette(0.1f);

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

        String oldName = profileField == null ? "" : profileField.getText();
        profileField = new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.literal("Profile name"));
        profileField.setMaxLength(ProfileStore.MAX_NAME);
        profileField.setDrawsBackground(false);
        profileField.setPlaceholder(Text.literal("Profile name..."));
        profileField.setText(oldName);
        addSelectableChild(profileField);
    }


    private TextFieldWidget positionField(String axis) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, 60, 12, Text.literal(axis + " position"));
        field.setMaxLength(5); field.setDrawsBackground(false);
        field.setTextPredicate(value -> value.matches("-?[0-9]{0,4}"));
        addSelectableChild(field); return field;
    }

    private void applyPosition(String value, boolean horizontal) {
        if (selectedHud == null || value.isEmpty() || value.equals("-")) return;
        try {
            int coordinate = Math.max(-2000, Math.min(8000, Integer.parseInt(value)));
            if (horizontal) selectedHud.x = coordinate; else selectedHud.y = coordinate;
        } catch (NumberFormatException ignored) { }
    }

    // ------------------------------------------------------------------ helpers

    private static float approach(float cur, float target, float dt, float speed) { return ThemeManager.animate(cur, target, dt, speed); }

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

    private static int pillRadius(int w, int h, int radius) { return h >= 18 && h <= 48 && w >= h * 2 ? h / 2 : radius; }

    private static void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int color) { GlassRenderer.rounded(ctx, x, y, w, h, pillRadius(w, h, r), color); }

    private static void border(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void glassBorder(DrawContext ctx, int x, int y, int w, int h, int r, int edge, int fill) { GlassRenderer.surface(ctx, x, y, w, h, pillRadius(w, h, r), edge, fill); }

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
        uiScale = Math.min(1f, Math.min((width - 12f) / 680f, (height - 12f) / 420f));
        uiScale = Math.max(0.1f, uiScale);
        int availableWidth = Math.round(width / uiScale);
        int availableHeight = Math.round(height / uiScale);
        pw = Math.min(780, availableWidth - 36);
        ph = Math.min(480, availableHeight - 28);
        px = (availableWidth - pw) / 2;
        py = (availableHeight - ph) / 2;
        cx = px + SIDE_W + 16;
        cy = py + HEADER_H + 12;
        cw = pw - SIDE_W - 34;
        ch = ph - HEADER_H - 24;
    }

    private int tabAreaHeight() { return pillY() - 8 - (py + HEADER_H + 8); }

    private int tabHeight() {
        return Math.max(18, Math.min(TAB_H, tabAreaHeight() / TABS.length));
    }

    private int tabStride() {
        return Math.max(tabHeight(), Math.min(TAB_H + 1,
                (tabAreaHeight() - tabHeight()) / Math.max(1, TABS.length - 1)));
    }

    private int tabY(int i) { return py + HEADER_H + 8 + tabStride() * i; }


    private int rowY(int j) {
        return cy + 36 + j * ROW_STRIDE - Math.round(scroll);
    }

    private List<HudModule> modulesFor(Category category) {
        if (query.isEmpty()) return categoryModules.getOrDefault(category, List.of());
        if (!query.equals(cachedQuery)) {
            filteredModules = HudManager.MODULES.stream().filter(m -> m.label.toLowerCase(Locale.ROOT).contains(query)
                    || m.id.toLowerCase(Locale.ROOT).contains(query)).toList();
            cachedQuery = query;
        }
        return filteredModules;
    }

    private boolean scrolls() { return true; }

    private float maxScroll() {
        if (tab == Tab.PROFILES) {
            return Math.max(0f, ProfileStore.list().size() * PROFILE_STRIDE - profileListH());
        }
        if (tab == Tab.PACKS) {
            int rows = packsInstalledView ? installedPacks().size() : hits.size();
            int stride = packsInstalledView ? INSTALLED_STRIDE : PACK_STRIDE;
            return Math.max(0f, rows * stride - listH());
        }
        if (tab.category == null) {
            int contentHeight = switch (tab) {
                case APPEARANCE -> 352;
                case ANIMATIONS -> 240;
                case OPTIMIZE -> 252;
                case EFFECTS -> 201;
                case PRESETS_TAB -> 200;
                case LAYOUT -> 440;
                case CAMERA -> 170;
                case STATS -> 244;
                default -> 0;
            };
            return Math.max(0f, contentHeight - ch);
        }
        return Math.max(0f, modulesFor(tab.category).size() * ROW_STRIDE + 36 - ch);
    }

    /** Analytic critically damped hover state, keyed so each control animates independently. */
    private float hoverSpring(int key, boolean over) {
        if (!HabibConfig.appearance.animations) {
            float target = over ? 1f : 0f;
            rowHover.put(key, target); rowHoverVelocity.put(key, 0f);
            lastHoverBounce = 0f; return target;
        }
        float value = rowHover.getOrDefault(key, 0f);
        float velocity = rowHoverVelocity.getOrDefault(key, 0f);
        float target = over ? 1f : 0f;
        float omega = 22f;
        float displacement = value - target;
        float intermediate = (velocity + omega * displacement) * frameDt;
        float decay = (float) Math.exp(-omega * frameDt);
        value = target + (displacement + intermediate) * decay;
        velocity = (velocity - omega * intermediate) * decay;
        if (Math.abs(value - target) < 0.001f && Math.abs(velocity) < 0.01f) { value = target; velocity = 0f; }
        rowHover.put(key, value);
        rowHoverVelocity.put(key, velocity);
        lastHoverBounce = over ? Math.max(0f, value - 1f) : Math.max(0f, -value);
        return Math.max(0f, Math.min(1f, value));
    }

    private void drawHoverSurface(DrawContext ctx, int x, int y, int w, int h, int radius,
                                  int idle, int active, float progress, float bounce, float a) {
        drawHoverBounce(ctx, x, y, w, h, radius, bounce, a);
        rrect(ctx, x, y, w, h, radius, alpha(mix(idle, active, progress), a));
    }

    private void drawHoverBounce(DrawContext ctx, int x, int y, int w, int h, int radius,
                                 float bounce, float a) {
        float pop = Math.min(1f, bounce * 8f);
        int spread = Math.round(pop * 1.5f);
        if (pop > 0.18f) {
            // Expand evenly around the control's centre so the hover bounce never drifts upward.
            rrect(ctx, x - spread, y - spread, w + spread * 2, h + spread * 2,
                    radius + spread, alpha(0x24FFFFFF, a * pop));
        }
    }

    private void hoverRowCard(DrawContext ctx, int x, int y, int w, int h, int radius,
                              boolean over, float a) {
        int key = tab.ordinal() * 64 + (hoverCounter++ & 63);
        float progress = hoverSpring(key, over);
        drawHoverSurface(ctx, x, y, w, h, radius, ROW, ROW_HOVER, progress, lastHoverBounce, a);
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
        if (closing || confirmReset || armorDropdown >= 0 || hudXField.isFocused() || hudYField.isFocused() || searchField.isFocused() || layoutMode || (packField != null && packField.isFocused())
                || (profileField != null && profileField.isFocused())) return;

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
        return scrolls() && maxScroll() > 0f && inside(mx, my, px + pw - 10, cy, 10, ch);
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
        tabFade = HabibConfig.appearance.animations ? 0f : 1f;
        appearanceDrag = blurDrag = false;
        armorDropdown = -1;
        scrollTarget = tabScroll[t.ordinal()];
        scroll = scrollTarget;
        setFocused(null);
        if (t == Tab.PACKS) {
            MinecraftClient.getInstance().getResourcePackManager().scanPacks();
            if (!searchedOnce) runSearch();
        }
        if (t == Tab.STATS) UsageStats.refresh();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client.world == null) renderPanoramaBackground(ctx, delta);
        if (glassBlurAllowed() && HabibConfig.appearance.backgroundBlur && !HabibConfig.appearance.reducedTransparency) applyBlur(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        updatePalette(dt);
        frameDt = dt;
        hoverCounter = 0;
        pollKeys();

        open = approach(open, closing ? 0f : 1f, dt, 18f);
        if (closing && open < 0.02f) { super.close(); return; }
        if (!closing && open > 0.995f) open = 1f;
        openingTime = Math.min(0.8f, openingTime + dt);
        openingShape = !HabibConfig.appearance.animations ? 1f
                : closing ? Math.min(openingShape, open) : AnimationUtils.openingSpring(openingTime);
        tabFade = approach(tabFade, 1f, dt, 12f);
        scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget));
        scroll = approach(scroll, scrollTarget, dt, 18f);

        List<HudModule> all = HudManager.MODULES;
        for (int i = 0; i < all.size(); i++) {
            toggle[i] = approach(toggle[i], all.get(i).enabled ? 1f : 0f, dt, 16f);
        }

        geometry();
        ctx.fill(0, 0, width, height, alpha(0xFF101520, (layoutMode ? 0.08f : 0.15f) * open));

        // modules are drawn here (the normal HUD is skipped while this screen is open) so changes show live
        MinecraftClient mc = MinecraftClient.getInstance();
        for (HudModule m : all) {
            boolean outsidePanel = m.x + m.scaledWidth() < px * uiScale || m.x > (px + pw) * uiScale
                    || m.y + m.scaledHeight() < py * uiScale || m.y > (py + ph) * uiScale;
            if (m.enabled && (layoutMode || outsidePanel)) m.draw(ctx, mc, true);
        }

        if (layoutMode) {
            renderLayoutMode(ctx, mx, my, dt);
        } else {
            ctx.getMatrices().pushMatrix();
            try {
                ctx.getMatrices().scale(uiScale, uiScale);
                ctx.getMatrices().translate(px + pw / 2f, py + ph / 2f + openingOffset());
                ctx.getMatrices().scale(panelZoom(), panelZoom());
                ctx.getMatrices().translate(-(px + pw / 2f), -(py + ph / 2f));
                renderPanel(ctx, (int) Math.round(panelMouseX(mx)), (int) Math.round(panelMouseY(my)), dt, now);
            } finally { ctx.getMatrices().popMatrix(); }
        }
    }

    private void renderPanel(DrawContext ctx, int mx, int my, float dt, long now) {
        searchField.visible = true;
        packField.visible = tab == Tab.PACKS;
        profileField.visible = tab == Tab.PROFILES;
        hudXField.visible = hudYField.visible = tab == Tab.LAYOUT;
        float a = open;
        GlassRenderer.panel(ctx, px, py, pw, ph, a);
        GlassRenderer.surface(ctx, px + 8, py + 8, SIDE_W - 4, ph - 16, 24, alpha(HAIR, a * 0.45f), alpha(SIDE_BG, a));
        txt(ctx, "Modules", px + 23, py + 24, alpha(MUTED, a));

        GlassRenderer.surface(ctx, cx, py + 16, 26, 26, 8, alpha(HAIR, a), alpha(ROW_HOVER, a));
        GlassRenderer.bomb(ctx, cx + 3, py + 19, alpha(TEXT, a), alpha(ACCENT, a));
        sectionTitle(ctx, "Habib Client", cx + 36, py + 16, a);
        txt(ctx, "Premium. Performance. Control.", cx + 36, py + 33, alpha(MUTED, a));

        int searchX = px + pw - 212;
        glassBorder(ctx, searchX, py + 15, 170, 23, 11, alpha(searchField.isFocused() ? ACCENT : HAIR, a), alpha(ROW, a));
        searchField.setX(searchX + 10);
        searchField.setY(py + 23);
        searchField.setWidth(150);
        searchField.setHeight(12);
        searchField.setEditableColor(TEXT);
        searchField.setTextShadow(false);
        searchField.render(ctx, mx, my, 0f);

        boolean closeHover = inside(mx, my, px + pw - 33, py + 15, 23, 23);
        rrect(ctx, px + pw - 33, py + 15, 23, 23, 11, alpha(closeHover ? ROW_HOVER : ROW, a));
        txtCentered(ctx, "x", px + pw - 22, py + 22, alpha(TEXT, a));

        float targetY = tabY(tab.ordinal());
        if (highlightY < 0f) highlightY = targetY;
        highlightY = approach(highlightY, targetY, dt, 18f);
        int tabHeight = tabHeight();
        int tabTextOffset = (tabHeight - 9) / 2;
        int tabIconOffset = (tabHeight - 10) / 2;
        GlassRenderer.rounded(ctx, (float) px + 11, highlightY, SIDE_W - 13, tabHeight, tabHeight / 2, alpha(ThemeManager.palette().selected, a));

        for (int i = 0; i < TABS.length; i++) {
            boolean active = TABS[i] == tab;
            int rowY = tabY(i);
            boolean over = inside(mx, my, px + 11, rowY, SIDE_W - 13, tabHeight);
            tabHover[i] = hoverSpring(2048 + i, over);
            if (!active) rrect(ctx, px + 11, rowY, SIDE_W - 13, tabHeight, 7, alpha(ROW_HOVER, a * tabHover[i] * 0.55f));
            GlassRenderer.icon(ctx, i, px + 19, rowY + tabIconOffset, alpha(active ? ACCENT : MUTED, a));
            txt(ctx, TABS[i].label, px + 38, rowY + tabTextOffset, alpha(active ? TEXT : MUTED, a));
        }
        boolean updateOver = inside(mx, my, pillX(), pillY(), SIDE_W - 20, 20);
        rrect(ctx, pillX(), pillY(), SIDE_W - 20, 20, 7, alpha(updateOver ? ROW_HOVER : ROW, a));
        txt(ctx, pillText(), pillX() + 6, pillY() + 6, alpha(Updater.highlighted() ? ACCENT : MUTED, a));

        // Each content viewport restores its own scissor; HUD previews remain outside this transform.
        float ca = a * tabFade;
        int oy = cy;
        ctx.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
        try {
            if (tab.category == null && tab != Tab.PACKS && tab != Tab.PROFILES) oy -= Math.round(scroll);
            switch (tab) {
                case APPEARANCE -> renderAppearance(ctx, mx, my, dt, ca, oy);
                case ANIMATIONS -> renderAnimations(ctx, mx, my, dt, now, ca, oy);
                case PACKS -> renderPacks(ctx, mx, my, dt, ca, oy);
                case OPTIMIZE -> renderOptimize(ctx, mx, my, dt, ca, oy);
                case EFFECTS -> renderEffects(ctx, mx, my, dt, ca, oy);
                case PRESETS_TAB -> renderPresets(ctx, mx, my, dt, ca, oy);
                case LAYOUT -> renderLayoutTab(ctx, mx, my, dt, ca, oy);
                case CAMERA -> renderCameraTab(ctx, mx, my, ca, oy);
                case STATS -> renderStats(ctx, mx, my, dt, ca, oy);
                case PROFILES -> renderProfiles(ctx, mx, my, ca, oy);
                default -> renderModules(ctx, mx, my, dt, ca);
            }
        } finally { ctx.disableScissor(); }
        scrollbar(ctx, a);
        if (armorDropdown >= 0) renderDropdown(ctx, mx, my);
        if (confirmReset) renderConfirmation(ctx, mx, my);
        else if (updateOver) tooltip(ctx, Updater.statusText(), mx, my);
        else if (closeHover) tooltip(ctx, "Close menu (Esc)", mx, my);
    }

    /** Thin scroll indicator on the panel's right edge, only when the list actually scrolls. */
    private void scrollbar(DrawContext ctx, float a) {
        if (!scrolls()) return;
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
        int slide = 0;
        int enabled = 0;
        for (HudModule module : list) if (module.enabled) enabled++;
        sectionTitle(ctx, query.isEmpty() ? tab.label : "Search results", cx, cy + 1, ca);
        txtRight(ctx, enabled + " / " + list.size() + " ON", cx + cw, cy + 2, alpha(MUTED, ca * 0.95f));
        txt(ctx, "Personalise your HUD with live previews.", cx, cy + 14, alpha(MUTED, ca * 0.92f));

        ctx.enableScissor(cx - 2, cy + 33, cx + cw + 2, cy + ch);
        for (int j = 0; j < list.size(); j++) {
            HudModule m = list.get(j);
            int idx = HudManager.MODULES.indexOf(m);
            int ry = rowY(j) + slide;
            if (ry + ROW_H < cy || ry > cy + ch) continue;

            boolean over = inside(mx, my, cx, ry, cw, ROW_H) && inside(mx, my, cx, cy, cw, ch);
            hover[idx] = hoverSpring(1024 + idx, over);
            float bounce = lastHoverBounce;

            int rowFill = mix(ROW, ROW_HOVER, 0.18f + hover[idx] * 0.58f);
            drawHoverBounce(ctx, cx, ry, cw, ROW_H, 9, bounce, ca);
            rrect(ctx, cx, ry, cw, ROW_H, 10, alpha(rowFill, ca));
            rrect(ctx, cx + 9, ry + 8, 3, ROW_H - 16, 2,
                    alpha(mix(OFF, m.color | 0xFF000000, toggle[idx]), ca));
            txt(ctx, textRenderer.trimToWidth(m.label, cw - 232), cx + 19, ry + 10, alpha(mix(MUTED, TEXT, toggle[idx]), ca));

            txt(ctx, m.category.label + " widget", cx + 19, ry + 25, alpha(MUTED, ca * 0.85f));
            // Scale controls remain available alongside colour and on/off.
            float hv = ca;
            if (hv > 0.02f && cw > 350) {
                txt(ctx, "SCALE", cx + cw - 220, ry + 12, alpha(MUTED, hv * 0.95f));
                smallButton(ctx, mx, my, cx + cw - 184, ry + 8, "-", hv);
                txtCentered(ctx, String.format(Locale.ROOT, "%.1fx", m.scale), cx + cw - 148, ry + 11, alpha(MUTED, hv));
                smallButton(ctx, mx, my, cx + cw - 122, ry + 8, "+", hv);
            }

            rrect(ctx, cx + cw - 94, ry + 9, 14, 14, 6,
                    alpha(m.color | 0xFF000000, ca * (0.58f + 0.42f * toggle[idx])));
            txtRight(ctx, toggle[idx] > 0.5f ? "ON" : "OFF", cx + cw - 47, ry + 12,
                    alpha(toggle[idx] > 0.5f ? TEXT : MUTED, ca * 0.9f));
            drawSwitch(ctx, cx + cw - 40, ry + 7, toggle[idx], ca);
        }

        ctx.disableScissor();
        if (list.isEmpty()) txt(ctx, "No modules match your search.", cx, cy + 38, alpha(MUTED, ca));
    }

    private String pillText() { return textRenderer.trimToWidth("v" + Updater.currentVersion() + "  " + (Updater.highlighted() ? "Update ready" : "Updates"), SIDE_W - 32); }

    private int pillW() { return SIDE_W - 20; }

    private int pillX() { return px + 12; }

    private int pillY() { return py + ph - 28; }

    /** Thin status stripe on the left edge of a row: lit in the given colour when on. */
    private static void stripe(DrawContext ctx, int x, int y, int h, float t, int onColor, float a) {
        rrect(ctx, x, y + 5, 3, h - 10, 2, alpha(mix(0xFF2C2C32, onColor, t), a));
    }

    /** Semibold section heading, scaled independently from body text. */
    private void sectionTitle(DrawContext ctx, String s, int x, int y, float a) {
        ctx.getMatrices().pushMatrix();
        try {
            ctx.getMatrices().translate((float) x, (float) y);
            ctx.getMatrices().scale(1.2f, 1.2f);
            ctx.drawText(textRenderer, UiTypography.heading(s), 0, 0, alpha(TEXT, a), false);
        } finally { ctx.getMatrices().popMatrix(); }
    }

    private void smallButton(DrawContext ctx, int mx, int my, int x, int y, String label, float a) {
        boolean over = inside(mx, my, x, y, 16, 16);
        float progress = hoverSpring(8192 + x * 1024 + y, over);
        float bounce = lastHoverBounce;
        drawHoverBounce(ctx, x, y, 16, 16, 8, bounce, a);
        glassBorder(ctx, x, y, 16, 16, 8,
                alpha(mix(0x55FFFFFF, ACCENT, progress), a),
                alpha(mix(0x16FFFFFF, 0x35FFFFFF, progress), a));
        txtCentered(ctx, label, x + 8, y + 4, alpha(over ? TEXT : MUTED, a));
    }

    private void drawSwitch(DrawContext ctx, int x, int y, float t, float a) { GlassRenderer.toggle(ctx, x, y, t, a); }


    private void renderAppearance(DrawContext ctx, int mx, int my, float dt, float a, int y) {
        AppearanceSettings settings = HabibConfig.appearance;
        sectionTitle(ctx, "Appearance", cx, y, a);
        txt(ctx, "Make Habib Client feel at home.", cx, y + 14, alpha(MUTED, a));
        String[] labels = {"Theme Mode", "Glass Transparency", "Background Blur", "Blur Strength", "Animations", "Reduced Transparency", "Accent Colour", "Reset Appearance"};
        String[] descriptions = {settings.themeMode == ThemeMode.AUTO ? ThemeManager.systemDescription() : "Manual appearance override.", "Adjust the opacity of glass panels.", "Frost the world using Minecraft's blur.", "Amount of world blur behind this menu.", "Smooth, responsive interface transitions.", "More opaque glass with fewer effects.", "Colour for switches and selected controls.", "Restore the default appearance settings."};
        for (int row = 0; row < labels.length; row++) {
            int top = y + 32 + row * 40;
            boolean over = inside(mx, my, cx, top, cw, 36);
            hoverRowCard(ctx, cx, top, cw, 36, 9, over, a);
            txt(ctx, labels[row], cx + 10, top + 6, alpha(TEXT, a));
            txt(ctx, textRenderer.trimToWidth(descriptions[row], cw - 168), cx + 10, top + 21, alpha(MUTED, a));
            int right = cx + cw - 10;
            if (row == 0) {
                int start = right - 138;
                rrect(ctx, start, top + 7, 138, 22, 11, alpha(OFF, a * 0.32f));
                themeSegment = approach(themeSegment, settings.themeMode.ordinal(), dt, 18f);
                GlassRenderer.rounded(ctx, start + themeSegment * 46, top + 7, 46, 22, 11, alpha(ACCENT, a));
                for (ThemeMode mode : ThemeMode.values()) txtCentered(ctx, mode.label, start + mode.ordinal() * 46 + 23, top + 14, alpha(mode == settings.themeMode ? ThemeManager.palette().accentText : MUTED, a));
            } else if (row == 1) {
                opacityVisual = approach(opacityVisual, settings.opacity, dt, 22f);
                GlassRenderer.slider(ctx, right - 138, top + 9, 92, (opacityVisual - 0.35f) / 0.65f, a);
                txtRight(ctx, Math.round(settings.opacity * 100) + "%", right, top + 14, alpha(TEXT, a));
            } else if (row == 2 || row == 4 || row == 5) {
                boolean on = row == 2 ? settings.backgroundBlur : row == 4 ? settings.animations : settings.reducedTransparency;
                appearanceToggle[row] = approach(appearanceToggle[row], on ? 1f : 0f, dt, 18f);
                drawSwitch(ctx, right - 34, top + 9, appearanceToggle[row], a);
            } else if (row == 3) {
                GlassRenderer.slider(ctx, right - 138, top + 9, 92, (settings.blurStrength - 1) / 9f, a * (settings.backgroundBlur && !settings.reducedTransparency ? 1f : 0.4f));
                txtRight(ctx, settings.blurStrength + "/10", right, top + 14, alpha(MUTED, a));
            } else if (row == 6) {
                for (int i = 0; i < ACCENTS.length; i++) {
                    int x = right - 112 + i * 28;
                    rrect(ctx, x - 1, top + 7, 23, 23, 11, alpha(settings.accent == ACCENTS[i] ? TEXT : HAIR, a));
                    rrect(ctx, x + 1, top + 9, 19, 19, 9, alpha(ACCENTS[i], a));
                }
            } else txtRight(ctx, "Reset >", right, top + 14, alpha(TEXT, a));
        }
    }

    private void appearanceClicked(double mx, double my) {
        int row = (int) ((my - cy - 32) / 40);
        if (my < cy + 32 || row < 0 || row > 7 || (my - cy - 32) % 40 >= 36) return;
        AppearanceSettings settings = HabibConfig.appearance;
        int right = cx + cw - 10;
        switch (row) {
            case 0 -> {
                if (mx >= right - 138) settings.themeMode = ThemeMode.values()[Math.min(2, (int) ((mx - right + 138) / 46))];
            }
            case 1 -> { if (mx >= right - 148) { appearanceDrag = true; setOpacity(mx); } }
            case 3 -> { if (mx >= right - 148 && settings.backgroundBlur && !settings.reducedTransparency) { blurDrag = true; setBlur(mx); } }
            case 2 -> settings.backgroundBlur = !settings.backgroundBlur;
            case 4 -> settings.animations = !settings.animations;
            case 5 -> settings.reducedTransparency = !settings.reducedTransparency;
            case 6 -> {
                int i = (int) ((mx - right + 112) / 28);
                if (mx >= right - 113 && i >= 0 && i < ACCENTS.length) settings.accent = ACCENTS[i];
            }
            case 7 -> confirmReset = true;
            default -> { }
        }
        if (!appearanceDrag && !blurDrag) HabibConfig.save();
    }

    private void setOpacity(double mx) {
        HabibConfig.appearance.opacity = 0.35f + 0.65f * (float) Math.max(0, Math.min(1, (mx - (cx + cw - 148)) / 92));
    }

    private void renderConfirmation(DrawContext ctx, int mx, int my) {
        float fade = modalOpacity = approach(modalOpacity, 1f, frameDt, 20f);
        rrect(ctx, px, py, pw, ph, 16, alpha(0x78000000, fade));
        int x = px + (pw - 290) / 2, y = py + (ph - 116) / 2;
        rrect(ctx, x, y, 290, 116, 16, alpha(BG | 0xFF000000, fade));
        GlassRenderer.panel(ctx, x, y, 290, 116, fade);
        sectionTitle(ctx, "Reset appearance?", x + 16, y + 17, fade);
        txt(ctx, "Your modules and profiles will be kept.", x + 16, y + 37, alpha(MUTED, fade));
        profileButton(ctx, inside(mx, my, x + 16, y + 70, 122, 26), x + 16, y + 70, 122, 26, "Cancel", ROW, fade);
        profileButton(ctx, inside(mx, my, x + 150, y + 70, 122, 26), x + 150, y + 70, 122, 26, "Reset", ACCENT, fade);
    }

    private void tooltip(DrawContext ctx, String value, int mx, int my) {
        String label = textRenderer.trimToWidth(value, pw - 28);
        int w = textRenderer.getWidth(label) + 16;
        int x = Math.max(px + 8, Math.min(px + pw - w - 8, mx));
        int y = Math.min(py + ph - 28, my + 15);
        glassBorder(ctx, x, y, w, 23, 7, HAIR, ThemeManager.palette().panel | 0xFF000000);
        txt(ctx, label, x + 8, y + 8, TEXT);
    }

    private void setBlur(double mx) {
        HabibConfig.appearance.blurStrength = 1 + Math.round(9 * (float) Math.max(0, Math.min(1, (mx - (cx + cw - 148)) / 92)));
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
        GlassRenderer.rounded(ctx, segmentX + 2f, segY + 2, segW - 4, segH - 4, 5, alpha(ACCENT, ca * 0.9f));
        for (int i = 0; i < SwingAnimator.COUNT; i++) {
            boolean selected = i == style;
            txtCentered(ctx, SwingAnimator.NAMES[i], cx + i * segW + segW / 2, segY + 7,
                    alpha(selected ? ThemeManager.palette().accentText : MUTED, ca));
        }

        // only-on-hit toggle
        onHitToggle = approach(onHitToggle, HabibConfig.swingOnlyOnHit ? 1f : 0f, dt, 16f);
        boolean overHit = inside(mx, my, cx, oy + 56, cw, 22);
        hoverRowCard(ctx, cx, oy + 56, cw, 22, 8, overHit, ca);
        stripe(ctx, cx, oy + 56, 22, onHitToggle, ACCENT, ca);
        txt(ctx, "Full animation only on hit", cx + 10, oy + 63, alpha(mix(MUTED, TEXT, onHitToggle), ca));
        drawSwitch(ctx, cx + cw - 40, oy + 58, onHitToggle, ca);

        // intensity slider
        float live = style > 0 ? 1f : 0.4f;
        int slY = oy + SLIDER_Y;
        txt(ctx, "Intensity", cx, slY, alpha(TEXT, ca * live));
        txtRight(ctx, Math.round(k * 100f) + "%", cx + cw, slY, alpha(MUTED, ca));
        int trackY = slY + 16;
        intensityVisual = approach(intensityVisual, k, dt, 22f);
        GlassRenderer.slider(ctx, cx + 5, trackY - 3, cw - 10, (intensityVisual - 0.25f) / 1.75f, ca * live);

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
    private float anchorToggle;
    private float totemParticleToggle;

    private void optimizeRow(DrawContext ctx, int mx, int my, int y, String title, String sub, float t, float ca) {
        boolean over = inside(mx, my, cx, y, cw, 30);
        hoverRowCard(ctx, cx, y, cw, 30, 9, over, ca);
        stripe(ctx, cx, y, 30, t, ACCENT, ca);
        txt(ctx, title, cx + 10, y + 5, alpha(mix(MUTED, TEXT, t), ca));
        txt(ctx, textRenderer.trimToWidth(sub, cw - 60), cx + 10, y + 16, alpha(MUTED, ca));
        drawSwitch(ctx, cx + cw - 40, y + 6, t, ca);
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
        anchorToggle = approach(anchorToggle, HabibConfig.anchorOptimizer ? 1f : 0f, dt, 16f);
        optimizeRow(ctx, mx, my, oy + 26, "Crystal optimizer", "Crystals vanish the instant you hit them.", crystalToggle, ca);
        optimizeRow(ctx, mx, my, oy + 58, "No explosion particles", "Cuts the lag from crystal explosions.", particleToggle, ca);
        optimizeRow(ctx, mx, my, oy + 90, "No totem particles", "Hides the totem pop burst. Pop counter still works.", totemParticleToggle, ca);
        optimizeRow(ctx, mx, my, oy + 122, "No hit particles", "Hides crit, enchant-hit and damage particles.", hitParticleToggle, ca);
        optimizeRow(ctx, mx, my, oy + 154, "Ender pearl optimizer", "Hides pearl trail and landing particles. Pearls work as normal.", pearlToggle, ca);
        optimizeRow(ctx, mx, my, oy + 186, "Consumable optimizer", "Hides eating, drinking and splash potion particles.", consumableToggle, ca);
        optimizeRow(ctx, mx, my, oy + 218, "Anchor optimizer", "Shows a replaceable ghost after an explosion and reduces blast particles.", anchorToggle, ca);
    }

    private float lowHealthToggle;
    private float popFlashToggle;
    private float popMessageToggle;
    private float lowArmorToggle;
    private float fullbrightToggle;

    private void renderEffects(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "Effects", cx, oy, ca);
        txt(ctx, "Fullbright, screen alerts and chat notices.", cx, oy + 12, alpha(MUTED, ca));

        lowHealthToggle = approach(lowHealthToggle, HabibConfig.lowHealthWarning ? 1f : 0f, dt, 16f);
        popFlashToggle = approach(popFlashToggle, HabibConfig.popFlash ? 1f : 0f, dt, 16f);
        popMessageToggle = approach(popMessageToggle, HabibConfig.popMessages ? 1f : 0f, dt, 16f);
        lowArmorToggle = approach(lowArmorToggle, HabibConfig.lowArmorWarning ? 1f : 0f, dt, 16f);
        fullbrightToggle = approach(fullbrightToggle, HabibConfig.fullbright ? 1f : 0f, dt, 16f);
        optimizeRow(ctx, mx, my, oy + 28, "Low health warning", "Pulsing red screen edge at 3 hearts or less.", lowHealthToggle, ca);
        optimizeRow(ctx, mx, my, oy + 62, "Totem pop flash", "Gold when you pop, green when your target does.", popFlashToggle, ca);
        optimizeRow(ctx, mx, my, oy + 96, "Totem pop messages", "A chat line when you or your target pops.", popMessageToggle, ca);
        optimizeRow(ctx, mx, my, oy + 130, "Low armor warning", "Orange pulse when a piece is under 10% durability.", lowArmorToggle, ca);
        optimizeRow(ctx, mx, my, oy + 164, "Fullbright", "Brightens dark areas without a potion effect.", fullbrightToggle, ca);
    }

    private void renderStats(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "Usage stats", cx, oy, ca);
        txt(ctx, "Counts include only installations that opt in.", cx, oy + 12, alpha(MUTED, ca));

        UsageStats.Summary summary = UsageStats.summary();
        int cardW = (cw - 8) / 2;
        statsCard(ctx, cx, oy + 32, cardW, "Active today", summary.activeToday(), ca);
        statsCard(ctx, cx + cardW + 8, oy + 32, cardW, "Active in 7 days", summary.activeLast7Days(), ca);

        txt(ctx, "Active today by Minecraft version", cx, oy + 88, alpha(MUTED, ca));
        String gameVersions = formatCounts(summary.byGameVersion());
        if (gameVersions.isEmpty()) {
            txt(ctx, "No opted-in activity reported today.", cx + 4, oy + 105, alpha(MUTED, ca));
        } else {
            txt(ctx, textRenderer.trimToWidth(gameVersions, cw - 4), cx + 4, oy + 105, alpha(TEXT, ca));
        }
        txt(ctx, "Mod versions", cx, oy + 123, alpha(MUTED, ca));
        txt(ctx, textRenderer.trimToWidth(formatCounts(summary.byModVersion()), cw - 4), cx + 4, oy + 140, alpha(TEXT, ca));

        statsSharingToggle = approach(statsSharingToggle, UsageStats.sharingEnabled() ? 1f : 0f, dt, 16f);
        optimizeRow(ctx, mx, my, oy + 160, "Share anonymous usage", "Sends a random install ID and version once a day; no name, world or server.", statsSharingToggle, ca);
        txt(ctx, textRenderer.trimToWidth(UsageStats.status(), cw), cx, oy + 196, alpha(MUTED, ca));

        boolean refreshOver = inside(mx, my, cx, oy + 216, cw, 22);
        glassBorder(ctx, cx, oy + 216, cw, 22, 9, alpha(refreshOver ? ACCENT : HAIR, ca), alpha(ROW, ca));
        txtCentered(ctx, "Refresh stats", cx + cw / 2, oy + 223, alpha(TEXT, ca));
    }

    private void statsCard(DrawContext ctx, int x, int y, int w, String label, int value, float ca) {
        glassBorder(ctx, x, y, w, 48, 10, alpha(HAIR, ca), alpha(ROW, ca * 0.8f));
        txt(ctx, label, x + 9, y + 9, alpha(MUTED, ca));
        txtRight(ctx, Integer.toString(value), x + w - 9, y + 26, alpha(TEXT, ca));
    }

    private String formatCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(5)
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining("  ·  "));
    }

    private float statsSharingToggle;

    private void statsClicked(double mx, double my) {
        if (inside(mx, my, cx, cy + 160, cw, 30)) {
            UsageStats.toggleSharing();
        } else if (inside(mx, my, cx, cy + 216, cw, 22)) {
            UsageStats.refresh();
        }
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
        } else if (inside(mx, my, cx, cy + 164, cw, 30)) {
            HabibConfig.fullbright = !HabibConfig.fullbright;
            HabibConfig.save();
        }
    }

    // ------------------------------------------------------------------ profiles

    private static final int PROFILE_ROW_H = 26;
    private static final int PROFILE_STRIDE = 28;
    private static final int PROFILE_BTN_NEW_W = 66;
    private static final int PROFILE_BTN_PASTE_W = 78;

    private int profileListTop() {
        return cy + 54;
    }

    private int profileListH() {
        return ch - 54;
    }

    private int profileFieldW() {
        return cw - PROFILE_BTN_NEW_W - PROFILE_BTN_PASTE_W - 12;
    }

    private void profileButton(DrawContext ctx, boolean over, int x, int y, int w, int h, String label, int base, float ca) {
        float progress = hoverSpring(100000 + x * 1024 + y, over);
        float bounce = lastHoverBounce;
        drawHoverBounce(ctx, x, y, w, h, 7, bounce, ca);
        rrect(ctx, x, y, w, h, 7, alpha(mix(base, mix(base, 0xFFFFFFFF, 0.15f), progress), ca));
        txtCentered(ctx, label, x + w / 2, y + (h - 8) / 2, alpha(base == ACCENT || base == ACCENT_DIM ? ThemeManager.palette().accentText : TEXT, ca));
    }

    private void renderProfiles(DrawContext ctx, int mx, int my, float ca, int oy) {
        sectionTitle(ctx, "Profiles", cx, oy, ca);
        String note = profileNote.isEmpty() ? "Save your setup, switch between setups, or share one as a code." : profileNote;
        txt(ctx, textRenderer.trimToWidth(note, cw), cx, oy + 12, alpha(profileNote.isEmpty() ? MUTED : ACCENT, ca));

        // name box + the two top buttons
        int fy = oy + 26;
        int fw = profileFieldW();
        rrect(ctx, cx, fy, fw, 20, 5, alpha(ROW, ca));
        profileField.setX(cx + 8);
        profileField.setY(fy + 6);
        profileField.setWidth(fw - 16);
        profileField.setHeight(8);
        profileField.setEditableColor(TEXT);
        profileField.setTextShadow(false);
        profileField.render(ctx, mx, my, 0f);
        int bx = cx + fw + 6;
        profileButton(ctx, inside(mx, my, bx, fy, PROFILE_BTN_NEW_W, 20), bx, fy, PROFILE_BTN_NEW_W, 20, "Save new", ACCENT_DIM, ca);
        int px2 = bx + PROFILE_BTN_NEW_W + 6;
        profileButton(ctx, inside(mx, my, px2, fy, PROFILE_BTN_PASTE_W, 20), px2, fy, PROFILE_BTN_PASTE_W, 20, "Paste code", ROW, ca);

        if (pendingDelete >= 0 && System.currentTimeMillis() - pendingDeleteAt > 3000L) pendingDelete = -1;

        int top = profileListTop();
        int h = profileListH();
        List<ProfileStore.Profile> list = ProfileStore.list();
        ctx.enableScissor(cx - 2, top, cx + cw + 2, top + h);
        if (list.isEmpty()) {
            txt(ctx, textRenderer.trimToWidth("No profiles yet. Type a name and press Save new.", cw), cx + 2, top + 6, alpha(MUTED, ca));
        }
        for (int j = 0; j < list.size(); j++) {
            int ry = top + j * PROFILE_STRIDE - Math.round(scroll);
            if (ry + PROFILE_ROW_H < top || ry > top + h) continue;
            boolean clip = inside(mx, my, cx, top, cw, h);
            boolean over = clip && inside(mx, my, cx, ry, cw, PROFILE_ROW_H);
            hoverRowCard(ctx, cx, ry, cw, PROFILE_ROW_H, 8, over, ca);
            stripe(ctx, cx, ry, PROFILE_ROW_H, over ? 1f : 0f, ACCENT, ca);

            int dx = profileDeleteX();
            int ux = dx - 4 - 42;
            int sx = ux - 4 - 38;
            int lx = sx - 4 - 34;
            int by = ry + 4;
            txt(ctx, textRenderer.trimToWidth(list.get(j).name, lx - cx - 18), cx + 10, ry + 9, alpha(TEXT, ca));
            profileButton(ctx, clip && inside(mx, my, lx, by, 34, 18), lx, by, 34, 18, "Load", ACCENT_DIM, ca);
            profileButton(ctx, clip && inside(mx, my, sx, by, 38, 18), sx, by, 38, 18, "Share", ROW, ca);
            profileButton(ctx, clip && inside(mx, my, ux, by, 42, 18), ux, by, 42, 18, "Update", 0xFF2A2A30, ca);
            boolean pend = pendingDelete == j;
            profileButton(ctx, clip && inside(mx, my, dx, by, 16, 18), dx, by, 16, 18, pend ? "?" : "x", pend ? 0xFF9B2C2C : 0xFF2A2A30, ca);
        }
        ctx.disableScissor();
    }

    private int profileDeleteX() {
        return cx + cw - 6 - 16;
    }

    private void profilesClicked(Click click, boolean doubled, double mx, double my) {
        int fw = profileFieldW();
        if (inside(mx, my, cx, cy + 26, fw, 20)) {
            setFocused(profileField);
            profileField.mouseClicked(click, doubled);
            return;
        }
        setFocused(null);
        int bx = cx + fw + 6;
        if (inside(mx, my, bx, cy + 26, PROFILE_BTN_NEW_W, 20)) {
            int before = ProfileStore.list().size();
            profileNote = ProfileStore.create(profileField.getText());
            if (ProfileStore.list().size() > before) {
                profileField.setText("");
                scrollTarget = maxScroll(); // show the new profile at the bottom of the list
            }
            pendingDelete = -1;
            return;
        }
        int pbx = bx + PROFILE_BTN_NEW_W + 6;
        if (inside(mx, my, pbx, cy + 26, PROFILE_BTN_PASTE_W, 20)) {
            String clip = MinecraftClient.getInstance().keyboard.getClipboard();
            int before = ProfileStore.list().size();
            profileNote = ProfileStore.importCode(clip);
            if (ProfileStore.list().size() > before) scrollTarget = maxScroll();
            pendingDelete = -1;
            return;
        }

        int top = profileListTop();
        if (!inside(mx, my, cx, top, cw, profileListH())) return;
        List<ProfileStore.Profile> list = ProfileStore.list();
        for (int j = 0; j < list.size(); j++) {
            int ry = top + j * PROFILE_STRIDE - Math.round(scroll);
            if (!inside(mx, my, cx, ry, cw, PROFILE_ROW_H)) continue;
            int dx = profileDeleteX();
            int ux = dx - 4 - 42;
            int sx = ux - 4 - 38;
            int lx = sx - 4 - 34;
            int by = ry + 4;
            if (inside(mx, my, dx, by, 16, 18)) {
                if (pendingDelete == j) {
                    profileNote = ProfileStore.delete(j);
                    pendingDelete = -1;
                } else {
                    pendingDelete = j;
                    pendingDeleteAt = System.currentTimeMillis();
                    profileNote = "Click x again to delete \"" + list.get(j).name + "\".";
                }
                return;
            }
            pendingDelete = -1;
            if (inside(mx, my, ux, by, 42, 18)) {
                profileNote = ProfileStore.overwrite(j);
            } else if (inside(mx, my, sx, by, 38, 18)) {
                String code = ProfileStore.codeFor(j);
                if (code == null) {
                    profileNote = "Couldn't make a code for that profile.";
                } else {
                    MinecraftClient.getInstance().keyboard.setClipboard(code);
                    profileNote = "Code for \"" + list.get(j).name + "\" copied. Paste it to share.";
                }
            } else if (inside(mx, my, lx, by, 34, 18)) {
                profileNote = ProfileStore.apply(j);
            }
            return;
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
            presetHover[k] = hoverSpring(3072 + k, over);
            float cardBounce = lastHoverBounce;
            if (active) rrect(ctx, x - 1, y - 1, w + 2, 114, 6, alpha(ACCENT, ca));
            drawHoverSurface(ctx, x, y, w, 112, 10, ROW, ROW_HOVER, presetHover[k], cardBounce, ca);
            txtCentered(ctx, p.name(), x + w / 2, y + 8, alpha(TEXT, ca));
            ctx.fill(x + 10, y + 21, x + w - 10, y + 22, alpha(OFF, ca));
            for (int l = 0; l < p.lines().length; l++) {
                txtCentered(ctx, textRenderer.trimToWidth(p.lines()[l], w - 8), x + w / 2, y + 30 + l * 13,
                        alpha(MUTED, ca));
            }
            int by = y + 86;
            boolean applyOver = !active && inside(mx, my, x + 8, by, w - 16, 18);
            float applyProgress = hoverSpring(3100 + k, applyOver);
            float applyBounce = lastHoverBounce;
            drawHoverSurface(ctx, x + 8, by, w - 16, 18, 8,
                    active ? OFF : ACCENT_DIM, active ? OFF : ACCENT, applyProgress, applyBounce, ca);
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
        } else if (inside(mx, my, cx, cy + 218, cw, 30)) {
            HabibConfig.anchorOptimizer = !HabibConfig.anchorOptimizer;
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
        GlassRenderer.rounded(ctx, packSegX + 2f, oy + 2, third - 4, 16, 5, alpha(ACCENT, ca * 0.9f));
        String[] names = {"Modrinth", "Installed", "Enabled"};
        for (int v = 0; v < 3; v++) {
            boolean over = inside(mx, my, cx + v * third, oy, third, 20);
            float progress = hoverSpring(5000 + v, over);
            float bounce = lastHoverBounce;
            if (v == packView) {
                if (bounce > 0.01f) drawHoverBounce(ctx, cx + v * third, oy + 2, third, 16, 5, bounce, ca);
            } else if (progress > 0.01f || bounce > 0.01f) {
                drawHoverSurface(ctx, cx + v * third, oy + 2, third, 16, 5,
                        0x00000000, 0x24324250, progress, bounce, ca);
            }
            txtCentered(ctx, names[v], cx + v * third + third / 2, oy + 6,
                    alpha(packView == v ? 0xFFFFFFFF : MUTED, ca));
        }

        // search box / hint
        rrect(ctx, cx, oy + 24, cw, 18, 5, alpha(ROW, ca));
        packField.setX(cx + 8);
        packField.setY(oy + 24 + 5);
        packField.setWidth(cw - 16);
        packField.setHeight(8);
        packField.setEditableColor(TEXT);
        packField.setTextShadow(false);
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
            hoverRowCard(ctx, cx, ry, cw, PACK_ROW_H, 9, over, ca);

            int textW = cw - 84;
            txt(ctx, textRenderer.trimToWidth(hit.title(), textW), cx + 8, ry + 4, alpha(TEXT, ca));
            String err = packErrors.get(hit.id());
            if (err != null) {
                txt(ctx, textRenderer.trimToWidth(err, textW), cx + 8, ry + 14, alpha(0xFFFF6B6B, ca));
            } else {
                txt(ctx, textRenderer.trimToWidth(hit.description(), textW), cx + 8, ry + 14, alpha(MUTED, ca));
            }
            txt(ctx, textRenderer.trimToWidth("by " + hit.author() + "  -  " + compact(hit.downloads()) + " downloads", textW),
                    cx + 8, ry + 24, alpha(MUTED, ca * 0.92f));

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
            float progress = hoverSpring(6000 + j, bo);
            float bounce = lastHoverBounce;
            drawHoverSurface(ctx, bx, by, 56, 18, 7, base, mix(base, 0xFFFFFFFF, 0.15f), progress, bounce, ca);
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
            hoverRowCard(ctx, cx, ry, cw, INSTALLED_ROW_H, 8, over, ca);
            stripe(ctx, cx, ry, INSTALLED_ROW_H, t, ACCENT, ca);
            String name = p.getDisplayName().getString();
            txt(ctx, textRenderer.trimToWidth(name, cw - 60), cx + 10, ry + 9, alpha(mix(MUTED, TEXT, t), ca));
            drawSwitch(ctx, cx + cw - 40, ry + 4, t, ca);
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
        hoverRowCard(ctx, cx, y, cw, 22, 8, over, ca);
        txt(ctx, label, cx + 10, y + 7, alpha(TEXT, ca));
        txtRight(ctx, value + "  v", cx + cw - 10, y + 7, alpha(over ? ACCENT : MUTED, ca));
    }


    private int dropdownY() {
        int rowBottom = cy + (armorDropdown == 0 ? 344 : 370) - Math.round(scroll);
        int count = armorDropdown == 0 ? ARMOR_LAYOUTS.length : ARMOR_DURABILITY.length;
        return Math.max(cy, Math.min(rowBottom, cy + ch - count * 22 - 8));
    }

    private void renderDropdown(DrawContext ctx, int mx, int my) {
        String[] options = armorDropdown == 0 ? ARMOR_LAYOUTS : ARMOR_DURABILITY;
        dropdownExpansion = approach(dropdownExpansion, 1f, frameDt, 22f);
        int x = cx + cw - 150, y = dropdownY(), h = options.length * 22 + 8;
        glassBorder(ctx, x, y, 150, h, 9, HAIR, BG | 0xFF000000);
        ctx.enableScissor(x, y, x + 150, y + Math.round(h * dropdownExpansion));
        try {
            for (int i = 0; i < options.length; i++) {
                int top = y + 4 + i * 22;
                if (inside(mx, my, x + 4, top, 142, 22)) rrect(ctx, x + 4, top, 142, 22, 6, ROW_HOVER);
                int selected = armorDropdown == 0 ? HabibConfig.armorLayout : HabibConfig.armorDurability;
                txt(ctx, options[i], x + 12, top + 7, selected == i ? ACCENT : TEXT);
            }
        } finally { ctx.disableScissor(); }
    }

    private void dropdownClicked(double mx, double my) {
        int y = dropdownY();
        String[] options = armorDropdown == 0 ? ARMOR_LAYOUTS : ARMOR_DURABILITY;
        if (inside(mx, my, cx + cw - 150, y + 4, 150, options.length * 22)) {
            int index = Math.min(options.length - 1, (int) ((my - y - 4) / 22));
            if (armorDropdown == 0) HabibConfig.armorLayout = index; else HabibConfig.armorDurability = index;
            HabibConfig.save();
        }
        armorDropdown = -1;
    }

    private void renderLayoutTab(DrawContext ctx, int mx, int my, float dt, float ca, int oy) {
        sectionTitle(ctx, "HUD Editor", cx, oy, ca);
        txt(ctx, "Choose a widget, refine its position, or drag it in the live editor.", cx, oy + 14, alpha(MUTED, ca));
        profileButton(ctx, inside(mx, my, cx, oy + 32, 150, 22), cx, oy + 32, 150, 22, "Open layout editor", ACCENT, ca);
        profileButton(ctx, inside(mx, my, cx + 160, oy + 32, 110, 22), cx + 160, oy + 32, 110, 22, "Reset layout", ROW, ca);
        int top = oy + 64, listWidth = 142, listHeight = 220;
        rrect(ctx, cx, top, listWidth, listHeight, 9, alpha(SIDE_BG, ca));
        ctx.enableScissor(cx, Math.max(cy, top), cx + listWidth, Math.min(cy + ch, top + listHeight));
        try {
            for (int i = 0; i < HudManager.MODULES.size(); i++) {
                HudModule module = HudManager.MODULES.get(i);
                int y = top + 4 + i * 24 - Math.round(hudListScroll);
                if (y + 24 < top || y > top + listHeight) continue;
                boolean over = inside(mx, my, cx + 4, y, listWidth - 8, 22);
                if (module == selectedHud || over) rrect(ctx, cx + 4, y, listWidth - 8, 22, 6, alpha(module == selectedHud ? ThemeManager.palette().selected : ROW_HOVER, ca));
                GlassRenderer.icon(ctx, module.category.ordinal(), cx + 10, y + 6, alpha(module == selectedHud ? ACCENT : MUTED, ca));
                txt(ctx, textRenderer.trimToWidth(module.label, listWidth - 36), cx + 27, y + 7, alpha(TEXT, ca));
            }
        } finally { ctx.disableScissor(); }
        float listMax = Math.max(0, HudManager.MODULES.size() * 24 + 8 - listHeight);
        if (listMax > 0) rrect(ctx, cx + listWidth - 3, top + Math.round(190 * hudListScroll / listMax), 2, 30, 1, alpha(MUTED, ca * 0.6f));
        if (selectedHud == null) return;
        int detailX = cx + 152, detailW = cw - 152;
        glassBorder(ctx, detailX, top, detailW, listHeight, 9, alpha(HAIR, ca), alpha(ROW, ca));
        txt(ctx, textRenderer.trimToWidth(selectedHud.label, detailW - 56), detailX + 12, top + 12, alpha(TEXT, ca));
        drawSwitch(ctx, detailX + detailW - 44, top + 7, approach(selectedToggle, selectedHud.enabled ? 1f : 0f, dt, 18f), ca);
        selectedToggle = approach(selectedToggle, selectedHud.enabled ? 1f : 0f, dt, 18f);
        ctx.fill(detailX + 10, top + 33, detailX + detailW - 10, top + 34, alpha(HAIR, ca));
        txt(ctx, "Position", detailX + 12, top + 44, alpha(MUTED, ca));
        int fieldWidth = (detailW - 32) / 2;
        hudPositionField(ctx, hudXField, detailX + 12, top + 60, fieldWidth, selectedHud.x, "X", mx, my, ca);
        hudPositionField(ctx, hudYField, detailX + 20 + fieldWidth, top + 60, fieldWidth, selectedHud.y, "Y", mx, my, ca);
        txt(ctx, "Scale", detailX + 12, top + 101, alpha(MUTED, ca));
        txtRight(ctx, Math.round(selectedHud.scale * 100) + "%", detailX + detailW - 12, top + 101, alpha(TEXT, ca));
        selectedScale = approach(selectedScale, selectedHud.scale, dt, 22f);
        GlassRenderer.slider(ctx, detailX + 16, top + 118, detailW - 32, (selectedScale - 0.5f) / 2.5f, ca);
        txt(ctx, "Colour", detailX + 12, top + 151, alpha(MUTED, ca));
        rrect(ctx, detailX + detailW - 34, top + 145, 20, 20, 8, selectedHud.color | 0xFF000000);
        ctx.enableScissor(detailX + 12, top + 174, detailX + detailW - 12, top + 212);
        ctx.getMatrices().pushMatrix();
        try {
            ctx.getMatrices().translate((float) (detailX + 16 - selectedHud.x), (float) (top + 184 - selectedHud.y));
            selectedHud.draw(ctx, mc(), true);
        } finally { ctx.getMatrices().popMatrix(); ctx.disableScissor(); }
        txt(ctx, "ARMOR HUD", cx, oy + 302, alpha(MUTED, ca));
        armorOptionRow(ctx, mx, my, oy + 320, "Style", ARMOR_LAYOUTS[HabibConfig.armorLayout], ca);
        armorOptionRow(ctx, mx, my, oy + 346, "Durability", ARMOR_DURABILITY[HabibConfig.armorDurability], ca);
        heldToggle = approach(heldToggle, HabibConfig.armorShowHeld ? 1f : 0f, dt, 18f);
        optimizeRow(ctx, mx, my, oy + 372, "Show held item", "Include your main-hand item in the armor widget.", heldToggle, ca);
        bgToggle = approach(bgToggle, HabibConfig.hudBackground ? 1f : 0f, dt, 18f);
        optimizeRow(ctx, mx, my, oy + 406, "Widget backgrounds", "Draw a readable background behind HUD widgets.", bgToggle, ca);
    }

    private void fovOptionRow(DrawContext ctx, int mx, int my, int y, String label,
                              int value, int key, float ca) {
        boolean over = inside(mx, my, cx, y, cw, 30);
        hoverRowCard(ctx, cx, y, cw, 30, 9, over, ca);
        txt(ctx, label, cx + 10, y + 10, alpha(TEXT, ca));
        int minusX = cx + cw - 56;
        int plusX = cx + cw - 30;
        txtRight(ctx, value + "°", minusX - 7, y + 10, alpha(over ? ACCENT : TEXT, ca));

        boolean minusOver = inside(mx, my, minusX, y + 5, 22, 20);
        float minusMotion = hoverSpring(key, minusOver);
        float minusBounce = lastHoverBounce;
        drawHoverSurface(ctx, minusX, y + 5, 22, 20, 8, ROW, ROW_HOVER,
                minusMotion, minusBounce, ca);
        txtCentered(ctx, "−", minusX + 11, y + 11, alpha(minusOver ? ACCENT : TEXT, ca));

        boolean plusOver = inside(mx, my, plusX, y + 5, 22, 20);
        float plusMotion = hoverSpring(key + 1, plusOver);
        float plusBounce = lastHoverBounce;
        drawHoverSurface(ctx, plusX, y + 5, 22, 20, 8, ROW, ROW_HOVER,
                plusMotion, plusBounce, ca);
        txtCentered(ctx, "+", plusX + 11, y + 11, alpha(plusOver ? ACCENT : TEXT, ca));
    }

    private void renderCameraTab(DrawContext ctx, int mx, int my, float ca, int oy) {
        sectionTitle(ctx, "Camera FOV", cx, oy, ca);
        txt(ctx, "Choose a separate field of view for each camera view.", cx, oy + 15, alpha(MUTED, ca));
        fovOptionRow(ctx, mx, my, oy + 43, "First person", HabibConfig.firstPersonFov, 4200, ca);
        fovOptionRow(ctx, mx, my, oy + 81, "Third person", HabibConfig.thirdPersonFov, 4210, ca);

        boolean firstPerson = MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
        int activeFov = firstPerson ? HabibConfig.firstPersonFov : HabibConfig.thirdPersonFov;
        txt(ctx, "Active view: " + (firstPerson ? "First person" : "Third person")
                + "  ·  " + activeFov + "°", cx + 2, oy + 126, alpha(ACCENT, ca));
        txt(ctx, "30°–170° · Hold C to zoom using the active view's FOV.", cx + 2, oy + 145, alpha(MUTED, ca));
    }

    private void hudPositionField(DrawContext ctx, TextFieldWidget field, int x, int y, int w, int value, String axis, int mx, int my, float a) {
        glassBorder(ctx, x, y, w, 26, 7, alpha(field.isFocused() ? ACCENT : HAIR, a), alpha(ROW, a));
        txt(ctx, axis, x + 8, y + 9, alpha(MUTED, a));
        field.setX(x + 22); field.setY(y + 9); field.setWidth(w - 28); field.setHeight(12);
        field.setEditableColor(TEXT); field.setTextShadow(false);
        if (!field.isFocused()) field.setText(Integer.toString(value));
        field.render(ctx, mx, my, 0f);
    }

    private void setHudScale(double mx) {
        if (selectedHud == null) return;
        int x = cx + 168, w = cw - 184;
        float scale = 0.5f + 2.5f * (float) Math.max(0, Math.min(1, (mx - x) / w));
        selectedHud.scale = Math.round(scale * 20f) / 20f;
    }

    private void renderLayoutMode(DrawContext ctx, int mx, int my, float dt) {
        int centerX = width / 2;
        int centerY = height / 2;
        int centeredX = dragging == null ? centerX : (width - dragging.scaledWidth()) / 2;
        int centeredY = dragging == null ? centerY : (height - dragging.scaledHeight()) / 2;
        boolean snappedX = dragging != null && dragging.x == centeredX;
        boolean snappedY = dragging != null && dragging.y == centeredY;
        int guide = alpha(0xFFECECEE, open * 0.25f);
        ctx.fill(centerX, 0, centerX + 1, height, snappedX ? alpha(ACCENT, open * 0.9f) : guide);
        ctx.fill(0, centerY, width, centerY + 1, snappedY ? alpha(ACCENT, open * 0.9f) : guide);
        rrect(ctx, centerX - 5, centerY - 5, 11, 11, 2, alpha(BG, open));
        border(ctx, centerX - 5, centerY - 5, 11, 11,
                alpha(snappedX || snappedY ? ACCENT : TEXT, open * 0.9f));
        rrect(ctx, centerX - 25, centerY + 9, 50, 14, 3, alpha(BG, open));
        txtCentered(ctx, "CENTER", centerX, centerY + 12, alpha(TEXT, open));

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
        buttonHoverA = hoverSpring(4001, overA);
        float bounceA = lastHoverBounce;
        buttonHoverB = hoverSpring(4002, overB);
        float bounceB = lastHoverBounce;
        drawHoverSurface(ctx, width / 2 - 104, height - 32, 100, 22, 9,
                0xE0131313, ACCENT, buttonHoverA, bounceA, open);
        txtCentered(ctx, "Done", width / 2 - 54, height - 25, alpha(TEXT, open));
        drawHoverSurface(ctx, width / 2 + 4, height - 32, 100, 22, 9,
                0xE0131313, ROW_HOVER, buttonHoverB, bounceB, open);
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
        geometry();
        if (closing) return true;
        double mx = layoutMode ? click.x() : panelMouseX(click.x());
        double my = layoutMode ? click.y() : panelMouseY(click.y());
        int button = click.button();
        if (layoutMode) return layoutClicked(mx, my, button);
        click = new Click(mx, my, click.buttonInfo());
        if (button != 0) return false;
        if (confirmReset) {
            int x = px + (pw - 290) / 2, y = py + (ph - 116) / 2;
            if (inside(mx, my, x + 150, y + 70, 122, 26)) {
                HabibConfig.appearance = new AppearanceSettings(); HabibConfig.save(); confirmReset = false;
            } else if (inside(mx, my, x + 16, y + 70, 122, 26)) confirmReset = false;
            return true;
        }
        modalOpacity = 0f;
        if (armorDropdown >= 0) { dropdownClicked(mx, my); return true; }
        if (!inside(mx, my, px - 4, py - 4, pw + 8, ph + 8)) { close(); return true; }
        if (scrollbarHit(mx, my)) { sbDrag = true; dragScrollbar(my); return true; }
        if (inside(mx, my, px + pw - 33, py + 15, 23, 23)) { close(); return true; }
        if (inside(mx, my, pillX(), pillY(), pillW(), 20)) { Updater.onClick(); return true; }
        if (inside(mx, my, px + pw - 212, py + 15, 170, 23)) {
            setFocused(searchField); searchField.mouseClicked(click, doubled); return true;
        }
        searchField.setFocused(false);
        if (tab == Tab.LAYOUT && my >= cy && my < cy + ch) {
            if (inside(mx, my, hudXField.getX() - 18, hudXField.getY() - 8, hudXField.getWidth() + 24, 26)) {
                setFocused(hudXField); hudXField.mouseClicked(click, doubled); return true;
            }
            if (inside(mx, my, hudYField.getX() - 18, hudYField.getY() - 8, hudYField.getWidth() + 24, 26)) {
                setFocused(hudYField); hudYField.mouseClicked(click, doubled); return true;
            }
        }
        setFocused(null);
        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, px + 6, tabY(i), SIDE_W - 12, tabHeight())) {
                if (!query.isEmpty()) searchField.setText("");
                select(TABS[i]); return true;
            }
        }
        if (!inside(mx, my, cx, cy, cw, ch)) return false;
        double contentY = tab.category == null && tab != Tab.PACKS && tab != Tab.PROFILES ? my + Math.round(scroll) : my;
        switch (tab) {
            case APPEARANCE -> appearanceClicked(mx, contentY);
            case ANIMATIONS -> animationsClicked(mx, contentY);
            case PACKS -> packsClicked(click, doubled, mx, my);
            case OPTIMIZE -> optimizeClicked(mx, contentY);
            case EFFECTS -> effectsClicked(mx, contentY);
            case PRESETS_TAB -> presetsClicked(mx, contentY);
            case LAYOUT -> layoutTabClicked(mx, contentY);
            case CAMERA -> cameraTabClicked(mx, contentY);
            case STATS -> statsClicked(mx, contentY);
            case PROFILES -> profilesClicked(click, doubled, mx, my);
            default -> { if (my >= cy + 33) modulesClicked(mx, my); }
        }
        return true;
    }

    private void modulesClicked(double mx, double my) {
        List<HudModule> list = modulesFor(tab.category);
        for (int j = 0; j < list.size(); j++) {
            int ry = rowY(j);
            if (!inside(mx, my, cx, ry, cw, ROW_H)) continue;
            HudModule m = list.get(j);
            if (inside(mx, my, cx + cw - 94, ry + 9, 14, 14)) {
                m.cycleColor();
            } else if (cw > 350 && inside(mx, my, cx + cw - 184, ry + 8, 16, 16)) {
                m.scale = Math.max(0.5f, Math.round((m.scale - 0.1f) * 10f) / 10f);
            } else if (cw > 350 && inside(mx, my, cx + cw - 122, ry + 8, 16, 16)) {
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
        if (inside(mx, my, cx, cy + 32, 150, 22)) { layoutMode = true; return; }
        if (inside(mx, my, cx + 160, cy + 32, 110, 22)) { HudManager.resetAll(); HabibConfig.save(); return; }
        int top = cy + 64;
        if (inside(mx, my, cx, top, 142, 220)) {
            int index = (int) ((my - top - 4 + Math.round(hudListScroll)) / 24);
            if (index >= 0 && index < HudManager.MODULES.size()) {
                selectedHud = HudManager.MODULES.get(index);
                selectedToggle = selectedHud.enabled ? 1f : 0f;
                selectedScale = selectedHud.scale;
                setFocused(null);
            }
            return;
        }
        int x = cx + 152, w = cw - 152;
        if (selectedHud != null && inside(mx, my, x, top, w, 34)) selectedHud.enabled = !selectedHud.enabled;
        else if (selectedHud != null && inside(mx, my, x + 10, top + 113, w - 20, 24)) { hudScaleDrag = true; setHudScale(mx); }
        else if (selectedHud != null && inside(mx, my, x + w - 36, top + 143, 24, 24)) selectedHud.cycleColor();
        else if (inside(mx, my, cx, cy + 320, cw, 22)) { armorDropdown = 0; dropdownExpansion = 0f; }
        else if (inside(mx, my, cx, cy + 346, cw, 22)) { armorDropdown = 1; dropdownExpansion = 0f; }
        else if (inside(mx, my, cx, cy + 372, cw, 30)) HabibConfig.armorShowHeld = !HabibConfig.armorShowHeld;
        else if (inside(mx, my, cx, cy + 406, cw, 30)) HabibConfig.hudBackground = !HabibConfig.hudBackground;
        HabibConfig.save();
    }

    private void cameraTabClicked(double mx, double my) {
        if (adjustFov(mx, my, cy + 43, cx + cw - 56, cx + cw - 30, true)) return;
        adjustFov(mx, my, cy + 81, cx + cw - 56, cx + cw - 30, false);
    }

    private boolean adjustFov(double mx, double my, int y, int minusX, int plusX, boolean firstPerson) {
        if (!inside(mx, my, cx, y, cw, 30)) return false;
        int value = firstPerson ? HabibConfig.firstPersonFov : HabibConfig.thirdPersonFov;
        if (inside(mx, my, minusX, y + 5, 22, 20)) value = Math.max(HabibConfig.MIN_FOV, value - 5);
        else if (inside(mx, my, plusX, y + 5, 22, 20)) value = Math.min(HabibConfig.MAX_FOV, value + 5);
        else return true;

        if (firstPerson) HabibConfig.firstPersonFov = value;
        else HabibConfig.thirdPersonFov = value;
        HabibConfig.save();
        return true;
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
        geometry();
        double mx = layoutMode ? click.x() : panelMouseX(click.x());
        double my = layoutMode ? click.y() : panelMouseY(click.y());
        if (hudScaleDrag) { setHudScale(mx); return true; }
        if (appearanceDrag || blurDrag) { if (appearanceDrag) setOpacity(mx); else setBlur(mx); return true; }
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
            int maxX = width - dragging.scaledWidth();
            int maxY = height - dragging.scaledHeight();
            int centeredX = maxX / 2;
            int centeredY = maxY / 2;
            if (Math.abs(nx - centeredX) <= LAYOUT_SNAP_DISTANCE) nx = centeredX;
            if (Math.abs(ny - centeredY) <= LAYOUT_SNAP_DISTANCE) ny = centeredY;
            dragging.x = Math.max(0, Math.min(maxX, nx));
            dragging.y = Math.max(0, Math.min(maxY, ny));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (sliderDrag || hudScaleDrag || appearanceDrag || blurDrag || dragging != null || sbDrag) {
            hudScaleDrag = appearanceDrag = blurDrag = false;
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
        if (confirmReset || closing || armorDropdown >= 0) return true;
        if (!layoutMode) { mx = panelMouseX(mx); my = panelMouseY(my); }
        if (!layoutMode && tab == Tab.LAYOUT && inside(mx, my, cx, cy + 64 - Math.round(scroll), 142, 220)) {
            hudListScroll = Math.max(0, Math.min(Math.max(0, HudManager.MODULES.size() * 24 + 8 - 220), hudListScroll - (float) vertical * 24));
            return true;
        }
        if (layoutMode) {
            HudModule m = topModuleAt(mx, my);
            if (m != null) {
                m.scale = Math.max(0.5f, Math.min(3f, m.scale + (float) Math.signum(vertical) * 0.1f));
                HabibConfig.save();
                return true;
            }
        } else if (scrolls() && inside(mx, my, cx, cy, cw, ch)) {
            scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget - (float) vertical * ROW_STRIDE));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    /** Esc: leave the layout editor first, then close the menu. */
    @Override
    public void close() {
        if (confirmReset) { confirmReset = false; return; }
        if (armorDropdown >= 0) { armorDropdown = -1; return; }
        if (layoutMode) { layoutMode = false; return; }
        if (HabibConfig.appearance.animations) closing = true;
        else super.close();
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
