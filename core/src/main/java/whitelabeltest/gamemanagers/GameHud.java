package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.gamemanagers.effects.ChainFireEffect;
import whitelabeltest.gamemanagers.effects.CircleMeterEffect;
import whitelabeltest.player.Player;

/** The in-game HUD, drawn over the play area in a WipEout / Designers Republic style: cyan, lime and
 *  orange on dark carbon, chamfered panels, glows and hazard stripes.
 *
 *  - Score bar along the top: score, stage tag, high score.
 *  - Chain stack (top right): the COMBO box (chain count over the chain flame, a bar filling toward
 *    a chain of 100, when MAX lights up and the flame grows out of the box), lives as shield pods,
 *    and the chain timer as a hazard-striped heat meter.
 *  - Weapons capsule (bottom left): weapon level, the two weapon icons, the graze and bomb gauges
 *    and the bomb chevrons, with a PWR status tag underneath.
 *  The chain stack and the capsule each move to the opposite edge when the player comes near.
 *
 *  Sizes are in reference-design pixels (PX = 1/90 of a world unit, i.e. one screen pixel at 1080p). */
public class GameHud implements Disposable {
    public static final Color CYAN = new Color(0f, 0.941f, 1f, 1f);
    public static final Color LIME = new Color(0.8f, 1f, 0f, 1f);
    public static final Color ORANGE = new Color(1f, 0.231f, 0.188f, 1f);
    private static final Color CARBON = new Color(0.02f, 0.031f, 0.067f, 1f);
    private static final Color SLATE_950 = new Color(0.008f, 0.024f, 0.09f, 1f);
    private static final Color SLATE_900 = new Color(0.059f, 0.09f, 0.165f, 1f);
    private static final Color SLATE_700 = new Color(0.2f, 0.255f, 0.333f, 1f);
    private static final Color SLATE_600 = new Color(0.278f, 0.333f, 0.412f, 1f);
    private static final Color SLATE_400 = new Color(0.58f, 0.639f, 0.722f, 1f);
    private static final Color SLATE_300 = new Color(0.796f, 0.835f, 0.882f, 1f);
    private static final Color CYAN_950 = new Color(0.031f, 0.2f, 0.267f, 1f);
    private static final Color CYAN_300 = new Color(0.404f, 0.91f, 0.976f, 1f);
    private static final Color CYAN_500 = new Color(0.024f, 0.714f, 0.831f, 1f);
    private static final Color RED_600 = new Color(0.863f, 0.149f, 0.149f, 1f);
    private static final Color RED_500 = new Color(0.937f, 0.267f, 0.267f, 1f);
    private static final Color RED_400 = new Color(0.973f, 0.443f, 0.443f, 1f);

    // The reference design scaled up 25% for legibility in game (its smallest labels are 6 px).
    private static final float SCALE = 1.25f;
    private static final float PX = SCALE / 90f;
    // Gap between the floating panels and the play-area edges.
    private static final float INSET = 8 * PX;

    private static final float HEADER_H = 28 * PX;

    private static final float BOX_W = 80 * PX, BOX_H = 53 * PX, BOX_CHAMFER = 10 * PX;
    private static final float POD_W = 16 * PX, POD_H = 14 * PX, POD_GAP = 4 * PX;
    private static final float PODS_W = POD_W + 6 * PX;
    private static final float METER_W = 10 * PX, METER_H = 128 * PX;
    private static final float STACK_GAP = 6 * PX;
    private static final float STACK_H = BOX_H + STACK_GAP + METER_H + 12 * PX;
    // The flame's reach above the box at full overflow: up through the score bar, tip off-screen.
    private static final float FLAME_MAX_OVERFLOW = INSET + HEADER_H + 20 * PX;
    // Flame noise cells per world unit, relative to the shader's per-rect default.
    private static final float FLAME_NOISE_SCALE = 0.5f;
    private static final int MAX_CHAIN = 100;

    private static final float CAPSULE_W = 40 * PX;
    private static final float TILE = 24 * PX, GRAZE_D = 24 * PX, BOMB_D = 28 * PX;
    private static final float STACK_SPACE = 6 * PX;
    private static final float CHEVRON_W = 20 * PX, CHEVRON_H = 14.5f * PX, CHEVRON_GAP = 2 * PX;
    private static final float CAPSULE_H = 3 * PX + 20 * PX + 4 * PX
        + TILE + STACK_SPACE + TILE + STACK_SPACE + GRAZE_D + STACK_SPACE + BOMB_D + STACK_SPACE
        + 5 * PX + STACK_SPACE + 2 * PX + Player.MAX_BOMB_CAPACITY * (CHEVRON_H + CHEVRON_GAP)
        + 5 * PX;
    private static final float TAG_H = 10 * PX;
    private static final float COLUMN_H = CAPSULE_H + 2 * PX + TAG_H;

    // A panel moves sides when the player's hitbox centre comes within this distance of it.
    private static final float SIDE_SWITCH_MARGIN = 1.0f;
    // Slide out past the edge, then in on the other side (each half takes half of this).
    private static final float SIDE_SWITCH_DURATION = 0.3f;

    private final HudFont mono;
    private final HudFont display;
    private final GlyphLayout layout = new GlyphLayout();

    private final Texture pixel;
    private final Texture disc;
    private final Texture halfDisc;
    private final Texture triangle;
    private final Texture stripes;
    private final Texture glowDisc;
    private final Texture glowBoxTexture;
    private final NinePatch glowBox;
    private final Texture heart;
    private final Texture rainIcon, lightningIcon, moonIcon;
    private final Texture fullBombIcon, emptyBombIcon, bombIcon;

    private final CircleMeterEffect circleMeter = new CircleMeterEffect();
    private final ChainFireEffect chainFire = new ChainFireEffect();
    private final Color scratch = new Color();
    private final float[] gradientVertices = new float[20];

    private final CachedText scoreText = new CachedText();
    private final CachedText highScoreText = new CachedText();
    private final CachedText stageText = new CachedText();
    private final CachedText chainText = new CachedText();
    private final CachedText levelText = new CachedText();
    private final CachedText grazeText = new CachedText();

    private final DockedPanel chainPanel = new DockedPanel(true);
    private final DockedPanel columnPanel = new DockedPanel(false);
    private float time;

    private static final String[] HEART_PIXELS = {
        "..XXX.....XXX..",
        ".XXXXX...XXXXX.",
        "XXX..XX.XX..XXX",
        "XX....XXX....XX",
        "XX.....X.....XX",
        "XX...........XX",
        "XXX.........XXX",
        ".XXX.......XXX.",
        "..XXX.....XXX..",
        "...XXX...XXX...",
        "....XXX.XXX....",
        ".....XXXXX.....",
        "......XXX......",
        ".......X.......",
    };

    /** One piece of HUD text plus the value it was built from (avoids formatting every frame). */
    private static final class CachedText {
        private long key = Long.MIN_VALUE;
        private String text;

        String get(long value, java.util.function.LongFunction<String> build) {
            if (value != key || text == null) {
                key = value;
                text = build.apply(value);
            }
            return text;
        }
    }

    /** A panel docked to the left or right edge that switches edges when the player gets close:
     *  it slides out past its current edge, then slides in at the other. */
    private static final class DockedPanel {
        boolean onRight;
        boolean targetOnRight;
        // 0 = fully in view, 1 = fully hidden past the edge.
        float slide;

        DockedPanel(boolean onRight) {
            this.onRight = onRight;
            this.targetOnRight = onRight;
        }

        void update(float delta, boolean playerNearLeft, boolean playerNearRight) {
            boolean nearCurrent = onRight ? playerNearRight : playerNearLeft;
            boolean nearOther = onRight ? playerNearLeft : playerNearRight;
            // Only move if the other side is clear, so a panel can't bounce back and forth.
            if (targetOnRight == onRight && nearCurrent && !nearOther) targetOnRight = !onRight;

            float step = delta / (SIDE_SWITCH_DURATION / 2f);
            if (targetOnRight != onRight) {
                slide += step;
                if (slide >= 1f) {
                    slide = 1f;
                    onRight = targetOnRight;
                }
            } else {
                slide = Math.max(0f, slide - step);
            }
        }

        /** Left x of a panel `width` wide, inset from its edge, including the slide offset. */
        float x(float width, float worldWidth) {
            float eased = slide * slide * (3f - 2f * slide);
            float travel = (width + INSET) * eased;
            return onRight ? worldWidth - INSET - width + travel : INSET - travel;
        }
    }

    private enum Corner { TL, TR, BL, BR }

    public GameHud() {
        mono = new HudFont("fonts/ShareTechMono-Regular.ttf");
        display = new HudFont("fonts/Orbitron-Black.ttf");

        pixel = texture(solid(1, 1));

        Pixmap discPixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        discPixmap.setColor(Color.WHITE);
        discPixmap.fillCircle(64, 64, 63);
        disc = smooth(texture(discPixmap));

        // Top half of a disc: the circle's centre sits on the bottom edge of the pixmap.
        Pixmap halfPixmap = new Pixmap(256, 128, Pixmap.Format.RGBA8888);
        halfPixmap.setColor(Color.WHITE);
        halfPixmap.fillCircle(128, 128, 127);
        halfDisc = smooth(texture(halfPixmap));

        // Right triangle filling the bottom-right half: a chamfered corner with the top-left cut.
        Pixmap triPixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        triPixmap.setColor(Color.WHITE);
        triPixmap.fillTriangle(63, 0, 63, 63, 0, 63);
        triangle = smooth(texture(triPixmap));

        // Diagonal hazard stripes (orange / dark), tiled.
        Pixmap stripePixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                stripePixmap.setColor((x + y) % 16 < 8 ? ORANGE : SLATE_950);
                stripePixmap.drawPixel(x, y);
            }
        }
        stripes = texture(stripePixmap);
        stripes.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        // Soft glows: a radial falloff, and a box falloff around a square centre (as a nine-patch,
        // so it can wrap any rectangle like a CSS box-shadow).
        Pixmap glowPixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        Pixmap boxPixmap = new Pixmap(48, 48, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float d = (float) Math.hypot(x - 31.5f, y - 31.5f) / 32f;
                float a = MathUtils.clamp(1f - d, 0f, 1f);
                glowPixmap.setColor(1f, 1f, 1f, a * a);
                glowPixmap.drawPixel(x, y);
            }
        }
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                float dx = Math.max(0f, Math.max(16f - x, x - 31f));
                float dy = Math.max(0f, Math.max(16f - y, y - 31f));
                float a = MathUtils.clamp(1f - (float) Math.hypot(dx, dy) / 16f, 0f, 1f);
                boxPixmap.setColor(1f, 1f, 1f, a * a);
                boxPixmap.drawPixel(x, y);
            }
        }
        glowDisc = smooth(texture(glowPixmap));
        glowBoxTexture = smooth(texture(boxPixmap));
        glowBox = new NinePatch(glowBoxTexture, 16, 16, 16, 16);
        // Each glow corner covers 8 px, like box-shadow: 0 0 8px.
        glowBox.scale(8 * PX / 16f, 8 * PX / 16f);

        Pixmap heartPixmap = new Pixmap(HEART_PIXELS[0].length(), HEART_PIXELS.length, Pixmap.Format.RGBA8888);
        heartPixmap.setColor(Color.WHITE);
        for (int row = 0; row < HEART_PIXELS.length; row++) {
            for (int col = 0; col < HEART_PIXELS[row].length(); col++) {
                if (HEART_PIXELS[row].charAt(col) == 'X') heartPixmap.drawPixel(col, row);
            }
        }
        heart = texture(heartPixmap);

        // The pixel-art icons are drawn smaller than their source, so filter them smoothly.
        rainIcon = smooth(new Texture(Gdx.files.internal("images/ui/RainIcon.png"), true));
        lightningIcon = smooth(new Texture(Gdx.files.internal("images/ui/LightningIcon.png"), true));
        moonIcon = smooth(new Texture(Gdx.files.internal("images/ui/MoonIcon.png"), true));
        fullBombIcon = smooth(new Texture(Gdx.files.internal("images/ui/FullBombIcon.png"), true));
        bombIcon = smooth(new Texture(Gdx.files.internal("images/ui/BombIcon.png"), true));
        emptyBombIcon = smooth(new Texture(Gdx.files.internal("images/ui/EmptyBombIcon.png"), true));
    }

    /** A font rendered at two sizes, so small labels aren't shrunk from a large bitmap (blurry)
     *  and large ones aren't blown up from a small one. */
    private static final class HudFont implements Disposable {
        static final int SMALL = 20, LARGE = 48;
        // Reference sizes up to this use the small bitmap.
        static final float SMALL_MAX_PX = 12f;
        final BitmapFont small, large;

        HudFont(String path) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal(path));
            small = generate(generator, SMALL);
            large = generate(generator, LARGE);
            generator.dispose();
        }

        private static BitmapFont generate(FreeTypeFontGenerator generator, int size) {
            FreeTypeFontParameter params = new FreeTypeFontParameter();
            params.size = size;
            params.genMipMaps = true;
            params.minFilter = Texture.TextureFilter.MipMapLinearLinear;
            params.magFilter = Texture.TextureFilter.Linear;
            BitmapFont font = generator.generateFont(params);
            font.setUseIntegerPositions(false);
            return font;
        }

        /** The bitmap for a reference size, scaled to it. */
        BitmapFont at(float sizePx) {
            boolean useSmall = sizePx <= SMALL_MAX_PX;
            BitmapFont font = useSmall ? small : large;
            font.getData().setScale(sizePx * PX / (useSmall ? SMALL : LARGE));
            return font;
        }

        @Override
        public void dispose() {
            small.dispose();
            large.dispose();
        }
    }

    private static Pixmap solid(int w, int h) {
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        return pixmap;
    }

    private static Texture texture(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture smooth(Texture texture) {
        texture.setFilter(texture.getTextureData().useMipMaps() ? Texture.TextureFilter.MipMapLinearLinear : Texture.TextureFilter.Linear,
            Texture.TextureFilter.Linear);
        return texture;
    }

    // ------------------------------------------------------------------ frame

    public void draw(SpriteBatch batch, ScoreManager scoreManager, Player player, int stageNumber,
                     float worldWidth, float worldHeight, float bombCooldownTimer, float bombCooldownFraction) {
        float delta = Gdx.graphics.getDeltaTime();
        time += delta;
        chainFire.update(delta, scoreManager.getChainCount());

        float playTop = worldHeight - HEADER_H;
        float stackY = playTop - INSET - STACK_H;
        float columnY = INSET;
        float px = player.getHitbox().x, py = player.getHitbox().y;
        chainPanel.update(delta,
            isNear(px, py, INSET, stackY, BOX_W, STACK_H),
            isNear(px, py, worldWidth - INSET - BOX_W, stackY, BOX_W, STACK_H));
        columnPanel.update(delta,
            isNear(px, py, INSET, columnY, CAPSULE_W, COLUMN_H),
            isNear(px, py, worldWidth - INSET - CAPSULE_W, columnY, CAPSULE_W, COLUMN_H));

        drawHeader(batch, scoreManager, stageNumber, worldWidth, worldHeight);
        drawChainStack(batch, scoreManager, player, chainPanel.x(BOX_W, worldWidth), stackY, chainPanel.onRight);
        drawWeaponsColumn(batch, player, columnPanel.x(CAPSULE_W, worldWidth), columnY, bombCooldownTimer, bombCooldownFraction);
        drawFrameCorners(batch, worldWidth, worldHeight);
    }

    private static boolean isNear(float px, float py, float x, float y, float width, float height) {
        return px >= x - SIDE_SWITCH_MARGIN && px <= x + width + SIDE_SWITCH_MARGIN
            && py >= y - SIDE_SWITCH_MARGIN && py <= y + height + SIDE_SWITCH_MARGIN;
    }

    // ------------------------------------------------------------------ score bar

    private void drawHeader(SpriteBatch batch, ScoreManager scoreManager, int stageNumber, float worldWidth, float worldHeight) {
        float y = worldHeight - HEADER_H;
        float cy = y + HEADER_H / 2f;
        rect(batch, 0f, y, worldWidth, HEADER_H, CARBON, 0.95f);
        rect(batch, 0f, y, worldWidth, PX, CYAN, 0.5f);
        // Micro corner ticks on the bottom edge.
        rect(batch, 4 * PX, y, 8 * PX, 2 * PX, CYAN, 1f);
        rect(batch, worldWidth - 12 * PX, y, 8 * PX, 2 * PX, LIME, 1f);

        // Left: pinging dot, SCORE label and value.
        float x = 12 * PX;
        float ping = (time % 1f);
        float dotD = 6 * PX;
        discAt(batch, x + dotD / 2f, cy, dotD * (1f + ping), LIME, 0.75f * (1f - ping));
        discAt(batch, x + dotD / 2f, cy, dotD, LIME, 1f);
        x += dotD + 6 * PX;
        x += text(batch, mono, "SCORE:", x, cy, 10, LIME, 0.9f, -1, null, false) + 6 * PX;
        text(batch, mono, scoreText.get(scoreManager.getScore(), v -> String.format("%,d", v)), x, cy, 12, LIME, 1f, -1, LIME, false);

        // Centre: stage tag.
        String stage = stageText.get(stageNumber, v -> String.format("STAGE %02d", v));
        float tagW = measure(mono, stage, 9) + 8 * PX, tagH = 13 * PX;
        float tagX = worldWidth / 2f - tagW / 2f;
        rect(batch, tagX, cy - tagH / 2f, tagW, tagH, CYAN_950, 0.7f);
        frame(batch, tagX, cy - tagH / 2f, tagW, tagH, PX, CYAN_500, 0.4f);
        text(batch, mono, stage, worldWidth / 2f, cy, 9, CYAN_300, 1f, 0, null, false);

        // Right: HIGH SCORE label and value.
        float right = worldWidth - 12 * PX;
        right -= text(batch, mono, highScoreText.get(scoreManager.getHighScore(), v -> String.format("%,d", v)),
            right, cy, 12, Color.WHITE, 1f, 1, CYAN, false) + 6 * PX;
        text(batch, mono, "HIGH SCORE:", right, cy, 10, SLATE_400, 1f, 1, null, false);
    }

    // ------------------------------------------------------------------ chain stack

    /** The COMBO box above, with the lives pods (inner side) and chain timer meter (edge side)
     *  below it. Mirrors when docked on the left. */
    private void drawChainStack(SpriteBatch batch, ScoreManager scoreManager, Player player, float x, float stackY, boolean onRight) {
        float boxY = stackY + STACK_H - BOX_H;
        drawComboBox(batch, scoreManager, x, boxY, onRight);

        float rowTop = boxY - STACK_GAP;
        float meterX = onRight ? x + BOX_W - METER_W : x;
        float podsX = onRight ? meterX - 4 * PX - PODS_W : meterX + METER_W + 4 * PX;
        drawLifePods(batch, player, podsX, rowTop, onRight);
        drawHeatMeter(batch, scoreManager, meterX, rowTop - METER_H, onRight);
    }

    private void drawComboBox(SpriteBatch batch, ScoreManager scoreManager, float x, float y, boolean onRight) {
        // The chamfer is on the inner top corner; the lime marker square on the outer one.
        Corner cut = onRight ? Corner.TL : Corner.TR;
        glow(batch, x, y, BOX_W, BOX_H, LIME, 0.25f);
        chamferFill(batch, x, y, BOX_W, BOX_H, BOX_CHAMFER, cut, CARBON, 0.95f);

        // Flame behind the contents; past a chain of 100 it grows up out of the box.
        float innerX = x + PX, innerY = y + PX, innerW = BOX_W - 2 * PX, innerH = BOX_H - 2 * PX;
        float flameH = innerH + FLAME_MAX_OVERFLOW * chainFire.getOverflow();
        chainFire.render(batch, pixel, innerX, innerY, innerW, flameH,
            FLAME_NOISE_SCALE * innerW, FLAME_NOISE_SCALE * flameH);

        chamferFrame(batch, x, y, BOX_W, BOX_H, BOX_CHAMFER, PX, cut, LIME, 1f);
        rect(batch, onRight ? x + BOX_W - 4 * PX : x - 2 * PX, y + BOX_H - 4 * PX, 6 * PX, 6 * PX, LIME, 1f);

        int chain = scoreManager.getChainCount();
        float left = x + 5 * PX, right = x + BOX_W - 5 * PX, top = y + BOX_H - 5 * PX;

        // Header: COMBO, and a MAX tag once the chain reaches 100.
        float headerCy = top - 4.5f * PX;
        text(batch, mono, "COMBO", left, headerCy, 7, LIME, 1f, -1, null, true);
        if (chain >= MAX_CHAIN) {
            float tagW = measure(mono, "MAX", 6) + 4 * PX;
            rect(batch, right - tagW, headerCy - 4 * PX, tagW, 8 * PX, LIME, 1f);
            text(batch, mono, "MAX", right - tagW / 2f, headerCy, 6, Color.BLACK, 1f, 0, null, false);
        }
        float dividerY = top - 11 * PX;
        rect(batch, left, dividerY, right - left, PX, LIME, 0.3f);

        // CHAIN and the count, bottoms aligned.
        float rowCy = dividerY - 2 * PX - 10 * PX;
        text(batch, mono, "CHAIN", left, rowCy - 3.5f * PX, 9, SLATE_300, 1f, -1, null, true);
        text(batch, display, chainText.get(chain, v -> "x" + v), right, rowCy, 18, LIME, 1f, 1, LIME, true);

        // Progress toward MAX: lime-to-cyan, pulsing (1 px border, 1 px padding, 2 px fill).
        float barH = 6 * PX, barY = rowCy - 10 * PX - 4 * PX - barH, barW = right - left;
        rect(batch, left, barY, barW, barH, SLATE_900, 1f);
        frame(batch, left, barY, barW, barH, PX, LIME, 0.3f);
        float progress = MathUtils.clamp(chain / (float) MAX_CHAIN, 0f, 1f);
        float pulse = 0.75f + 0.25f * MathUtils.cos(time * MathUtils.PI);
        gradient(batch, left + 2 * PX, barY + 2 * PX, (barW - 4 * PX) * progress, barH - 4 * PX, LIME, CYAN, false, pulse);
    }

    /** Lives as a column of shield pods, filled from the top: cyan, orange for the last life, dark
     *  for lives already lost. */
    private void drawLifePods(SpriteBatch batch, Player player, float x, float top, boolean onRight) {
        int lives = player.getNumLives();
        int slots = Math.max(player.getStartingLives(), lives);
        float h = slots * POD_H + (slots - 1) * POD_GAP + 6 * PX;
        float y = top - h;
        Corner cut = onRight ? Corner.BR : Corner.BL;
        chamferFill(batch, x, y, PODS_W, h, 10 * PX, cut, CARBON, 0.9f);
        chamferFrame(batch, x, y, PODS_W, h, 10 * PX, PX, cut, CYAN, 0.4f);

        Color live = lives <= 1 ? ORANGE : CYAN;
        for (int i = 0; i < slots; i++) {
            float podX = x + 3 * PX, podY = top - 3 * PX - POD_H - i * (POD_H + POD_GAP);
            boolean alive = i < lives;
            if (alive) {
                glow(batch, podX, podY, POD_W, POD_H, live, 0.6f);
                rect(batch, podX, podY, POD_W, POD_H, CYAN_950, 0.9f);
                frame(batch, podX, podY, POD_W, POD_H, PX, live, 1f);
            } else {
                rect(batch, podX, podY, POD_W, POD_H, SLATE_900, 0.9f);
                frame(batch, podX, podY, POD_W, POD_H, PX, SLATE_700, 1f);
            }
            float hw = 10 * PX, hh = hw * HEART_PIXELS.length / HEART_PIXELS[0].length();
            tint(batch, alive ? live : SLATE_600, alive ? 1f : 0.6f);
            batch.draw(heart, podX + (POD_W - hw) / 2f, podY + (POD_H - hh) / 2f, hw, hh);
            batch.setColor(Color.WHITE);
        }
    }

    /** The chain timer as a heat meter: a hazard-striped red-orange fill draining downward, with a
     *  critical line at 25% and a CRIT label that blinks when the chain is about to break. */
    private void drawHeatMeter(SpriteBatch batch, ScoreManager scoreManager, float x, float y, boolean onRight) {
        rect(batch, x, y, METER_W, METER_H, SLATE_950, 1f);
        frame(batch, x, y, METER_W, METER_H, PX, SLATE_700, 1f);
        float innerX = x + 2 * PX, innerY = y + 2 * PX, innerW = METER_W - 4 * PX, innerH = METER_H - 4 * PX;

        for (int i = 0; i < 5; i++) {
            float tickY = innerY + 4 * PX + i * (innerH - 8 * PX) / 4f;
            rect(batch, x + PX, tickY, METER_W - 2 * PX, PX, Color.WHITE, 0.25f);
        }

        float fraction = MathUtils.clamp(scoreManager.getChainTimerFraction(), 0f, 1f);
        float fillH = innerH * fraction;
        if (fillH > 0f) {
            glow(batch, innerX, innerY, innerW, fillH, ORANGE, 0.6f);
            gradient(batch, innerX, innerY, innerW, fillH, RED_600, ORANGE, true, 1f);
            tint(batch, Color.WHITE, 0.6f);
            float tile = 16 * PX;
            batch.draw(stripes, innerX, innerY, innerW, fillH, 0f, fillH / tile, innerW / tile, 0f);
            batch.setColor(Color.WHITE);
        }

        // Critical line: dashed, at a quarter.
        float critY = innerY + innerH * 0.25f;
        for (float dx = 0f; dx < METER_W - 2 * PX; dx += 4 * PX) {
            rect(batch, x + PX + dx, critY, Math.min(2 * PX, METER_W - 2 * PX - dx), PX, RED_500, 1f);
        }

        boolean critical = scoreManager.getChainCount() > 0 && fraction < 0.25f;
        float critAlpha = critical ? ((time * 4f) % 1f < 0.5f ? 1f : 0.35f) : 0.6f;
        text(batch, mono, "CRIT", onRight ? x + METER_W : x, y - 8 * PX, 6, RED_400, critAlpha, onRight ? 1 : -1, critical ? ORANGE : null, false);
    }

    // ------------------------------------------------------------------ weapons capsule

    private void drawWeaponsColumn(SpriteBatch batch, Player player, float x, float bottom,
                                   float bombCooldownTimer, float bombCooldownFraction) {
        float cx = x + CAPSULE_W / 2f;
        float capsuleY = bottom + TAG_H + 2 * PX;
        float radius = CAPSULE_W / 2f;
        float bodyH = CAPSULE_H - radius;

        // Capsule: cyan pill-topped shell with a carbon interior.
        glow(batch, x, capsuleY, CAPSULE_W, CAPSULE_H, CYAN_950, 0.7f);
        rect(batch, x, capsuleY, CAPSULE_W, bodyH, CYAN, 1f);
        tint(batch, CYAN, 1f);
        batch.draw(halfDisc, x, capsuleY + bodyH, CAPSULE_W, radius);
        rect(batch, x + PX, capsuleY + PX, CAPSULE_W - 2 * PX, bodyH - PX, CARBON, 1f);
        tint(batch, CARBON, 1f);
        batch.draw(halfDisc, x + PX, capsuleY + bodyH, CAPSULE_W - 2 * PX, radius - PX);
        batch.setColor(Color.WHITE);

        float y = capsuleY + CAPSULE_H - 3 * PX;

        // Weapon level in a pill.
        float pillW = 28 * PX, pillH = 20 * PX, pillCy = y - pillH / 2f;
        pill(batch, cx, pillCy, pillW, pillH, CYAN);
        pill(batch, cx, pillCy, pillW - 2 * PX, pillH - 2 * PX, CYAN_950);
        String activeWeapon = player.getSlotWeaponId(player.getActiveSlot());
        int level = activeWeapon != null ? player.getWeaponLevel(activeWeapon) : 0;
        text(batch, display, levelText.get(level, v -> romanNumeral((int) v)), cx, pillCy + 2.5f * PX, 11, CYAN, 1f, 0, null, false);
        text(batch, mono, "LVL", cx, pillCy - 5.5f * PX, 6, CYAN_300, 1f, 0, null, false);
        y -= pillH + 4 * PX;

        // Weapon tiles: the active one bright, the other dimmed.
        for (int slot = 0; slot < 2; slot++) {
            String weaponId = player.getSlotWeaponId(slot);
            float tileY = y - TILE;
            if (weaponId != null) {
                boolean active = player.getActiveSlot() == slot;
                Color color = weaponColor(weaponId);
                rect(batch, cx - TILE / 2f, tileY, TILE, TILE, SLATE_900, 1f);
                frame(batch, cx - TILE / 2f, tileY, TILE, TILE, PX, color, active ? 0.7f : 0.25f);
                Texture icon = weaponIcon(weaponId);
                float iconSize = 15 * PX;
                if (icon != null) {
                    tint(batch, color, active ? 1f : 0.35f);
                    batch.draw(icon, cx - iconSize / 2f, tileY + (TILE - iconSize) / 2f, iconSize, iconSize);
                    batch.setColor(Color.WHITE);
                } else {
                    // No icon for this weapon (WaveBlast): a short label instead.
                    text(batch, mono, "WAV", cx, tileY + TILE / 2f, 7, color, active ? 1f : 0.35f, 0, null, false);
                }
            }
            y -= TILE + STACK_SPACE;
        }

        // GRZ gauge: a ring filling toward the next graze bomb (100 points), with the points inside.
        float grazeCy = y - GRAZE_D / 2f;
        discAt(batch, cx, grazeCy, GRAZE_D, SLATE_950, 1f);
        float grazeFraction = Math.min(player.getGrazePoints() / 100f, 1f);
        scratch.set(CYAN).a = 0.3f;
        circleMeter.render(batch, pixel, grazeFraction, scratch, CYAN, 0.36f, 0.5f,
            cx - GRAZE_D / 2f, grazeCy - GRAZE_D / 2f, GRAZE_D);
        rect(batch, cx - 2 * PX, grazeCy + GRAZE_D / 2f - PX, 4 * PX, 2 * PX, CYAN, 1f);
        rect(batch, cx - 2 * PX, grazeCy - GRAZE_D / 2f - PX, 4 * PX, 2 * PX, CYAN, 1f);
        text(batch, mono, "GRZ", cx, grazeCy + 2.5f * PX, 7, Color.WHITE, 1f, 0, null, false);
        text(batch, mono, grazeText.get((int) player.getGrazePoints(), v -> Long.toString(v)), cx, grazeCy - 4f * PX, 6, CYAN, 1f, 0, null, false);
        y -= GRAZE_D + STACK_SPACE;

        // BOMB gauge: a slowly turning dashed ring, full when a bomb is ready, filling while it
        // recharges, dim with none; the bomb icon in the middle.
        float bombCy = y - BOMB_D / 2f;
        boolean hasBombs = player.getNumBombs() > 0;
        boolean recharging = bombCooldownTimer > 0f;
        float bombFraction = !hasBombs ? 0f : (recharging ? 1f - bombCooldownFraction : 1f);
        if (hasBombs && !recharging) glowAround(batch, cx, bombCy, BOMB_D, ORANGE, 0.6f);
        discAt(batch, cx, bombCy, BOMB_D, SLATE_950, 1f);
        scratch.set(ORANGE).a = 0.25f;
        circleMeter.render(batch, pixel, bombFraction, scratch, ORANGE, 0.38f, 0.5f,
            cx - BOMB_D / 2f, bombCy - BOMB_D / 2f, BOMB_D, 18, time / 8f);
        float iconH = 13 * PX, iconW = iconH * bombIcon.getWidth() / bombIcon.getHeight();
        tint(batch, ORANGE, hasBombs ? 1f : 0.45f);
        batch.draw(bombIcon, cx - iconW / 2f, bombCy - iconH / 2f, iconW, iconH);
        batch.setColor(Color.WHITE);
        y -= BOMB_D + STACK_SPACE;

        // Separator.
        rect(batch, cx - 12 * PX, y - 3 * PX, 24 * PX, PX, SLATE_700, 1f);

        // Bomb chevrons from the bottom: filled ones bright and pulsing, empty ones dim.
        float thrust = 0.8f + 0.2f * MathUtils.sin(time * MathUtils.PI2 / 0.6f);
        int slots = Math.min(Math.max(player.getMaxBombs(), player.getNumBombs()), Player.MAX_BOMB_CAPACITY);
        float chevronY = capsuleY + 5 * PX;
        for (int i = 0; i < slots; i++) {
            float cy = chevronY + i * (CHEVRON_H + CHEVRON_GAP);
            boolean full = i < player.getNumBombs();
            if (full) glowAround(batch, cx, cy + CHEVRON_H / 2f, CHEVRON_W, ORANGE, 0.35f * thrust);
            tint(batch, ORANGE, full ? thrust : 0.5f);
            batch.draw(full ? fullBombIcon : emptyBombIcon, cx - CHEVRON_W / 2f, cy, CHEVRON_W, CHEVRON_H);
        }
        batch.setColor(Color.WHITE);

        // Status tag under the capsule.
        boolean critical = player.getNumLives() <= 1;
        String status = critical ? "PWR // CRITICAL" : "PWR // NOMINAL";
        float tagW = measure(mono, status, 6) + 9 * PX;
        float tagX = cx - tagW / 2f;
        rect(batch, tagX, bottom, tagW, TAG_H, Color.BLACK, 1f);
        rect(batch, tagX, bottom, PX, TAG_H, CYAN, 1f);
        text(batch, mono, status, tagX + 5 * PX, bottom + TAG_H / 2f, 6, critical ? ORANGE : SLATE_400, 1f, -1, null, false);
    }

    /** Cyan L-brackets in the play area's corners. */
    private void drawFrameCorners(SpriteBatch batch, float worldWidth, float worldHeight) {
        float l = 12 * PX, t = 2 * PX;
        rect(batch, 0f, worldHeight - t, l, t, CYAN, 1f);
        rect(batch, 0f, worldHeight - l, t, l, CYAN, 1f);
        rect(batch, worldWidth - l, worldHeight - t, l, t, CYAN, 1f);
        rect(batch, worldWidth - t, worldHeight - l, t, l, CYAN, 1f);
        rect(batch, 0f, 0f, l, t, CYAN, 1f);
        rect(batch, 0f, 0f, t, l, CYAN, 1f);
        rect(batch, worldWidth - l, 0f, l, t, CYAN, 1f);
        rect(batch, worldWidth - t, 0f, t, l, CYAN, 1f);
    }

    private Texture weaponIcon(String weaponId) {
        return switch (weaponId) {
            case "BasicWeapon" -> rainIcon;
            case "Thunderbolt" -> lightningIcon;
            case "OrbitWeapon" -> moonIcon;
            default -> null;
        };
    }

    private static Color weaponColor(String weaponId) {
        return switch (weaponId) {
            case "BasicWeapon" -> CYAN;
            case "Thunderbolt" -> LIME;
            default -> SLATE_300;
        };
    }

    /** Roman numerals; "-" for 0 (there's no Roman zero). */
    private static String romanNumeral(int value) {
        if (value <= 0) return "-";
        int[] values = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] symbols = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (value >= values[i]) {
                out.append(symbols[i]);
                value -= values[i];
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ drawing helpers

    private void tint(SpriteBatch batch, Color color, float alpha) {
        batch.setColor(color.r, color.g, color.b, color.a * alpha);
    }

    private void rect(SpriteBatch batch, float x, float y, float w, float h, Color color, float alpha) {
        if (w <= 0f || h <= 0f || alpha <= 0f) return;
        tint(batch, color, alpha);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /** A border of thickness t inside the rect. */
    private void frame(SpriteBatch batch, float x, float y, float w, float h, float t, Color color, float alpha) {
        rect(batch, x, y, w, t, color, alpha);
        rect(batch, x, y + h - t, w, t, color, alpha);
        rect(batch, x, y + t, t, h - 2f * t, color, alpha);
        rect(batch, x + w - t, y + t, t, h - 2f * t, color, alpha);
    }

    /** A two-colour gradient: `from` at the bottom (vertical) or left (horizontal). */
    private void gradient(SpriteBatch batch, float x, float y, float w, float h, Color from, Color to, boolean vertical, float alpha) {
        if (w <= 0f || h <= 0f) return;
        float c1 = Color.toFloatBits(from.r, from.g, from.b, from.a * alpha);
        float c2 = Color.toFloatBits(to.r, to.g, to.b, to.a * alpha);
        float bottomLeft = c1, topLeft = vertical ? c2 : c1, topRight = c2, bottomRight = vertical ? c1 : c2;
        float[] v = gradientVertices;
        v[0] = x;     v[1] = y;     v[2] = bottomLeft;  v[3] = 0f; v[4] = 1f;
        v[5] = x;     v[6] = y + h; v[7] = topLeft;     v[8] = 0f; v[9] = 0f;
        v[10] = x + w; v[11] = y + h; v[12] = topRight;  v[13] = 1f; v[14] = 0f;
        v[15] = x + w; v[16] = y;     v[17] = bottomRight; v[18] = 1f; v[19] = 1f;
        batch.draw(pixel, v, 0, 20);
    }

    /** A soft glow around a rectangle, like box-shadow: 0 0 8px. */
    private void glow(SpriteBatch batch, float x, float y, float w, float h, Color color, float alpha) {
        if (w <= 0f || h <= 0f) return;
        float r = 8 * PX;
        scratch.set(color).a = color.a * alpha;
        glowBox.setColor(scratch);
        glowBox.draw(batch, x - r, y - r, w + 2f * r, h + 2f * r);
    }

    /** A soft round glow around a circle of the given diameter. */
    private void glowAround(SpriteBatch batch, float cx, float cy, float diameter, Color color, float alpha) {
        float d = diameter + 16 * PX;
        tint(batch, color, alpha);
        batch.draw(glowDisc, cx - d / 2f, cy - d / 2f, d, d);
        batch.setColor(Color.WHITE);
    }

    private void discAt(SpriteBatch batch, float cx, float cy, float diameter, Color color, float alpha) {
        if (alpha <= 0f) return;
        tint(batch, color, alpha);
        batch.draw(disc, cx - diameter / 2f, cy - diameter / 2f, diameter, diameter);
        batch.setColor(Color.WHITE);
    }

    /** A fully rounded rectangle (stadium), opaque. */
    private void pill(SpriteBatch batch, float cx, float cy, float w, float h, Color color) {
        float r = h / 2f;
        discAt(batch, cx - w / 2f + r, cy, h, color, 1f);
        discAt(batch, cx + w / 2f - r, cy, h, color, 1f);
        rect(batch, cx - w / 2f + r, cy - r, w - 2f * r, h, color, 1f);
    }

    /** A rect with one corner cut at 45 degrees (c along each edge). */
    private void chamferFill(SpriteBatch batch, float x, float y, float w, float h, float c, Corner cut, Color color, float alpha) {
        boolean top = cut == Corner.TL || cut == Corner.TR;
        boolean left = cut == Corner.TL || cut == Corner.BL;
        // The full-width part, the band beside the cut, and the triangle in the cut square.
        rect(batch, x, top ? y : y + c, w, h - c, color, alpha);
        rect(batch, left ? x + c : x, top ? y + h - c : y, w - c, c, color, alpha);
        tint(batch, color, alpha);
        // The triangle texture has its top-left cut; flip it to put the cut in the right corner.
        batch.draw(triangle, left ? x : x + w - c, top ? y + h - c : y, c, c,
            0, 0, triangle.getWidth(), triangle.getHeight(), !left, !top);
        batch.setColor(Color.WHITE);
    }

    private void chamferFrame(SpriteBatch batch, float x, float y, float w, float h, float c, float t, Corner cut, Color color, float alpha) {
        boolean top = cut == Corner.TL || cut == Corner.TR;
        boolean left = cut == Corner.TL || cut == Corner.BL;
        // Top and bottom edges, shortened on the cut side.
        rect(batch, top && left ? x + c : x, y + h - t, top ? w - c : w, t, color, alpha);
        rect(batch, !top && left ? x + c : x, y, top ? w : w - c, t, color, alpha);
        // Left and right edges, shortened on the cut end.
        rect(batch, x, left && !top ? y + c : y, t, left ? h - c : h, color, alpha);
        rect(batch, x + w - t, !left && !top ? y + c : y, t, !left ? h - c : h, color, alpha);
        // The diagonal.
        float x1 = left ? x : x + w - c, x2 = left ? x + c : x + w;
        float y1, y2;
        if (top) {
            y1 = left ? y + h - c : y + h;
            y2 = left ? y + h : y + h - c;
        } else {
            y1 = left ? y + c : y;
            y2 = left ? y : y + c;
        }
        line(batch, x1, y1, x2, y2, t, color, alpha);
    }

    private void line(SpriteBatch batch, float x1, float y1, float x2, float y2, float t, Color color, float alpha) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        float angle = MathUtils.atan2(y2 - y1, x2 - x1) * MathUtils.radiansToDegrees;
        tint(batch, color, alpha);
        batch.draw(pixel, x1, y1 - t / 2f, 0f, t / 2f, length, t, 1f, 1f, angle, 0, 0, 1, 1, false, false);
        batch.setColor(Color.WHITE);
    }

    private float measure(HudFont family, String s, float sizePx) {
        layout.setText(family.at(sizePx), s);
        return layout.width;
    }

    /** Draws text with its cap height centred on centerY and returns its width.
     *  @param align -1: x is the left edge; 0: x is the centre; 1: x is the right edge
     *  @param glow optional glow colour (text-shadow style)
     *  @param outline a dark outline, for text drawn over the chain flame */
    private float text(SpriteBatch batch, HudFont family, String s, float x, float centerY, float sizePx,
                       Color color, float alpha, int align, Color glow, boolean outline) {
        BitmapFont font = family.at(sizePx);
        layout.setText(font, s);
        float w = layout.width;
        float left = align < 0 ? x : align == 0 ? x - w / 2f : x - w;
        float top = centerY + layout.height / 2f;
        if (outline) {
            float o = PX;
            font.setColor(CARBON.r, CARBON.g, CARBON.b, 0.7f * alpha);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) font.draw(batch, s, left + dx * o, top + dy * o);
                }
            }
        }
        if (glow != null) {
            // Two faint rings of offset copies: a soft text-shadow glow that doesn't smear the glyphs.
            for (int ring = 1; ring <= 2; ring++) {
                float r = ring * PX;
                font.setColor(glow.r, glow.g, glow.b, (ring == 1 ? 0.18f : 0.08f) * alpha);
                for (int i = 0; i < 8; i++) {
                    float a = i * MathUtils.PI / 4f;
                    font.draw(batch, s, left + MathUtils.cos(a) * r, top + MathUtils.sin(a) * r);
                }
            }
        }
        font.setColor(color.r, color.g, color.b, color.a * alpha);
        font.draw(batch, s, left, top);
        font.setColor(Color.WHITE);
        return w;
    }

    @Override
    public void dispose() {
        mono.dispose();
        display.dispose();
        pixel.dispose();
        disc.dispose();
        halfDisc.dispose();
        triangle.dispose();
        stripes.dispose();
        glowDisc.dispose();
        glowBoxTexture.dispose();
        heart.dispose();
        rainIcon.dispose();
        lightningIcon.dispose();
        moonIcon.dispose();
        fullBombIcon.dispose();
        emptyBombIcon.dispose();
        bombIcon.dispose();
        circleMeter.dispose();
        chainFire.dispose();
    }
}
