package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.effects.ChainFireEffect;
import whitelabeltest.gamemanagers.effects.CircleMeterEffect;
import whitelabeltest.gamemanagers.effects.DataStreamEffect;
import whitelabeltest.gamemanagers.replay.DebugSaveState;
import whitelabeltest.gamemanagers.spawning.LevelRank;
import whitelabeltest.gamemanagers.spawning.PatternPreviewer;
import whitelabeltest.gamemanagers.spawning.SpawnScheduleEditor;
import whitelabeltest.gamemanagers.replay.ReplayBrowser;
import whitelabeltest.gamemanagers.input.InputType;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.player.Player;

public class UIManager implements Disposable {
    // "Cyber terminal" HUD palette - see drawHUD()/drawLeftHudPanel()/drawRightHudPanel(), modeled
    // on the reference mockup (Screenshot 2026-08-06 131050.png): green/amber readouts over dim
    // dividers and borders on a near-black panel. Package-visible (not private) so ChainFireEffect
    // can reuse these exact colors for its own ramp instead of duplicating hand-picked literals.
    public static final Color HUD_GREEN = new Color(0.35f, 1f, 0.55f, 1f);
    public static final Color HUD_GREEN_DIM = new Color(0.16f, 0.4f, 0.24f, 1f);
    public static final Color HUD_AMBER = new Color(1f, 0.72f, 0.18f, 1f);
    public static final Color HUD_RED = new Color(1f, 0.32f, 0.26f, 1f);
    private static final Color HUD_LABEL = new Color(0.5f, 0.62f, 0.55f, 1f);
    private static final Color HUD_GAUGE_BG = new Color(0.05f, 0.12f, 0.08f, 1f);
    // Bright near-white "hot head" leading each DATA_STREAM column - see drawRightHudPanel().
    private static final Color HUD_STREAM_HEAD = new Color(0.85f, 1f, 0.9f, 1f);

    private final BitmapFont font;
    private final GlyphLayout gameOverLayout;
    private final GlyphLayout textCueLayout;
    private final GlyphLayout measureLayout;
    private final Texture whitePixel;
    private final Texture heartIcon;
    // GameOverSign.png (4 columns x 3 rows, 12 frames) - see drawGameOver(), which plays it once
    // and freezes on the last frame via Animation.PlayMode.NORMAL rather than looping.
    private final Texture gameOverSignTexture;
    private final Animation<TextureRegion> gameOverSignAnimation;
    private static final float GAME_OVER_SIGN_FRAME_DURATION = 0.07f;
    private final InputType inputType;
    private final CircleMeterEffect circleMeterEffect;
    private final ChainFireEffect chainFireEffect;
    private final DataStreamEffect dataStreamEffect;

    public UIManager(InputType inputType) {
        this.inputType = inputType;
        this.circleMeterEffect = new CircleMeterEffect();
        this.chainFireEffect = new ChainFireEffect();
        this.dataStreamEffect = new DataStreamEffect();

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        generator.dispose();

        font.setUseIntegerPositions(false);
        font.getData().setScale(0.009375f);
        gameOverLayout = new GlyphLayout();
        textCueLayout = new GlyphLayout();
        measureLayout = new GlyphLayout();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        // Baked once as a plain white icon (two lobes + a point), tinted per-draw via
        // batch.setColor() the same way whitePixel is - see drawHeartIcon()/INTEGRITY readout.
        Pixmap heartPixmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        heartPixmap.setColor(Color.WHITE);
        heartPixmap.fillCircle(10, 11, 8);
        heartPixmap.fillCircle(22, 11, 8);
        heartPixmap.fillTriangle(2, 12, 30, 12, 16, 30);
        heartIcon = new Texture(heartPixmap);
        heartPixmap.dispose();

        gameOverSignTexture = new Texture(Gdx.files.internal("images/ui/GameOverSign.png"));
        gameOverSignAnimation = AnimationCache.get(gameOverSignTexture, 4, 3, 12, GAME_OVER_SIGN_FRAME_DURATION, Animation.PlayMode.NORMAL);
    }

    public void drawHUD(SpriteBatch batch, ScoreManager scoreManager, Player player, float worldHeight,
                         float leftPanelX, float rightPanelX, float panelWidth,
                         float bombCooldownTimer, float bombCooldownFraction) {
        chainFireEffect.update(Gdx.graphics.getDeltaTime(), scoreManager.getChainCount());
        dataStreamEffect.update(Gdx.graphics.getDeltaTime());
        drawLeftHudPanel(batch, scoreManager, player, worldHeight, leftPanelX, panelWidth, bombCooldownTimer, bombCooldownFraction);
        drawRightHudPanel(batch, player, worldHeight, rightPanelX, panelWidth);
    }

    private void drawLeftHudPanel(SpriteBatch batch, ScoreManager scoreManager, Player player, float worldHeight,
                                   float leftPanelX, float panelWidth, float bombCooldownTimer, float bombCooldownFraction) {
        float x = leftPanelX + 0.2f;
        float innerWidth = panelWidth - 0.4f;
        float rightEdge = x + innerWidth;
        float y = worldHeight - 0.25f;

        drawLabelValue(batch, "SYS:", "ONLINE", x, y, HUD_GREEN);

        y -= 0.35f;
        drawDivider(batch, x, y, innerWidth);

        y -= 0.35f;
        drawSectionLabel(batch, "SCORE_REGISTER", x, y);
        y -= 0.6f;
        drawScaledText(batch, String.format("%010d", scoreManager.getScore()), x, y, 1.7f, HUD_GREEN);

        y -= 0.45f;
        font.setColor(HUD_LABEL);
        font.draw(batch, "HI_SCORE:", x, y);
        font.setColor(Color.WHITE);
        y -= 0.42f;
        drawScaledText(batch, String.format("%010d", scoreManager.getHighScore()), x, y, 1.25f, HUD_AMBER);

        y -= 0.35f;
        drawDivider(batch, x, y, innerWidth);

        y -= 0.55f;
        drawSectionLabel(batch, "CHAIN_COUNTER", x, y);
        y -= 1.0f;

        // Fire effect behind the big chain number - see ChainFireEffect - sized to bracket the
        // number's footprint at its scale-3.2 glyph height (~0.96) with a little breathing room.
        float chainNumberHeight = 0.96f;
        float chainFlameWidth = 3.4f;
        float chainFlameHeight = 1.35f;
        chainFireEffect.render(batch, whitePixel, x, y - chainNumberHeight - 0.15f, chainFlameWidth, chainFlameHeight);

        drawScaledText(batch, String.valueOf(scoreManager.getChainCount()), x, y, 3.2f, HUD_AMBER);

        int chainMult = MathUtils.clamp(1 + scoreManager.getChainCount() / 10, 1, 9);
        y -= 0.28f;
        drawMeterBar(batch, x, y, innerWidth, 0.06f, scoreManager.getChainTimerFraction(), HUD_GREEN);
        y -= 0.32f;
        drawTextRightAligned(batch, "x" + chainMult + " MULT", rightEdge, y, HUD_GREEN);

        y -= 0.4f;
        boolean critical = player.getNumLives() <= 1;
        font.setColor(HUD_LABEL);
        font.draw(batch, "STATUS:", x, y);
        font.setColor(Color.WHITE);
        drawTextRightAligned(batch, critical ? "CRITICAL" : "NOMINAL", rightEdge, y, critical ? HUD_RED : HUD_GREEN);

        y -= 0.35f;
        drawDivider(batch, x, y, innerWidth);

        // Bottom-anchored footer (lives/bombs), matching the reference mockup's large empty gap
        // between the chain block and this row rather than continuing to stack downward from y.
        float labelY = 1.0f;
        float iconY = 0.55f;
        float iconSpacing = 0.34f;
        float iconSize = 0.26f;
        float rightColX = x + innerWidth * 0.62f;

        font.setColor(HUD_LABEL);
        font.draw(batch, "BURNER SHELLS:", x, labelY);
        font.draw(batch, "BOMB.EXE:", rightColX, labelY);
        font.setColor(Color.WHITE);

        for (int i = 0; i < player.getNumLives(); i++) {
            drawHeartIcon(batch, x + i * iconSpacing, iconY - iconSize, iconSize, HUD_GREEN);
        }
        for (int i = 0; i < player.getNumBombs(); i++) {
            drawDiamondIcon(batch, rightColX + i * iconSpacing + iconSize / 2f, iconY - iconSize / 2f, iconSize, HUD_AMBER);
        }

        if (bombCooldownTimer > 0) {
            drawMeterBar(batch, rightColX, iconY - iconSize - 0.14f, rightEdge - rightColX, 0.05f,
                1f - bombCooldownFraction, HUD_AMBER);
        }
    }

    private void drawRightHudPanel(SpriteBatch batch, Player player, float worldHeight,
                                    float rightPanelX, float panelWidth) {
        float x = rightPanelX + 0.2f;
        float innerWidth = panelWidth - 0.4f;
        float rightEdge = x + innerWidth;
        float y = worldHeight - 0.25f;

        drawLabelValue(batch, "SCAN:", "ACTIVE", x, y, HUD_GREEN);
        String pwr = pwrLabel(player);
        drawLabelValueRight(batch, "PWR:", pwr, rightEdge, y, pwrColor(pwr));

        y -= 0.35f;
        drawDivider(batch, x, y, innerWidth);

        y -= 0.35f;
        drawSectionLabel(batch, "GRAZE_SENSOR", x, y);

        float gaugeSize = 1.7f;
        float gaugeCenterX = x + innerWidth / 2f;
        float gaugeCenterY = y - 0.15f - gaugeSize / 2f;
        float grazeFraction = Math.min(player.getGrazePoints() / 100f, 1f);
        circleMeterEffect.render(batch, whitePixel, grazeFraction, HUD_GAUGE_BG, HUD_GREEN,
            0.34f, 0.5f, gaugeCenterX - gaugeSize / 2f, gaugeCenterY - gaugeSize / 2f, gaugeSize);
        drawScaledCentered(batch, String.format("%03d", (int) player.getGrazePoints()), gaugeCenterX, gaugeCenterY + 0.15f, 1.5f, HUD_GREEN);
        drawCentered(batch, "GRAZE_PTS", gaugeCenterX, gaugeCenterY - 0.35f, HUD_LABEL);

        y = gaugeCenterY - gaugeSize / 2f - 0.3f;
        drawDivider(batch, x, y, innerWidth);

        y -= 0.35f;
        drawSectionLabel(batch, "WEAPON_SYSTEM", x, y);
        y -= 0.15f;

        y -= WEAPON_BOX_HEIGHT;
        drawWeaponBox(batch, player, 0, x, y + WEAPON_BOX_HEIGHT, innerWidth);
        y -= 0.15f + WEAPON_BOX_HEIGHT;
        drawWeaponBox(batch, player, 1, x, y + WEAPON_BOX_HEIGHT, innerWidth);

        y -= 0.35f;
        drawDivider(batch, x, y, innerWidth);

        y -= 0.35f;
        drawSectionLabel(batch, "DATA_STREAM", x, y);
        y -= 0.15f;

        // Fills the rest of the panel down to a small floor margin - see DataStreamEffect.
        float cellSize = 0.18f;
        float streamHeight = Math.max(0.6f, y - 0.3f);
        int columns = Math.max(4, (int) (innerWidth / cellSize));
        int rows = Math.max(4, (int) (streamHeight / cellSize));
        dataStreamEffect.render(batch, font, x, y - streamHeight, innerWidth, streamHeight,
            columns, rows, HUD_GREEN, HUD_STREAM_HEAD, 0.85f);
    }

    private static final float WEAPON_BOX_HEIGHT = 1.05f;

    /** One bordered SPREAD/LASER-style box in WEAPON_SYSTEM - see the reference mockup. yTop is
     *  the box's top edge; the box occupies [yTop - WEAPON_BOX_HEIGHT, yTop]. */
    private void drawWeaponBox(SpriteBatch batch, Player player, int slot, float x, float yTop, float width) {
        String weaponId = player.getSlotWeaponId(slot);
        boolean equipped = weaponId != null;
        boolean active = equipped && player.getActiveSlot() == slot;
        Color accent = active ? HUD_GREEN : HUD_GREEN_DIM;

        drawBoxBorder(batch, x, yTop - WEAPON_BOX_HEIGHT, width, WEAPON_BOX_HEIGHT, accent);

        float innerX = x + 0.12f;
        float rightEdge = x + width - 0.12f;
        float rowY = yTop - 0.26f;

        font.setColor(accent);
        font.draw(batch, "> " + weaponLabel(weaponId), innerX, rowY);
        font.setColor(Color.WHITE);
        String status = !equipped ? "OFFLINE" : (active ? "ONLINE" : "STANDBY");
        drawTextRightAligned(batch, "[" + status + "]", rightEdge, rowY, accent);

        int level = player.getWeaponLevel(weaponId);
        int maxLevel = player.getMaxWeaponLevel();
        float fraction = maxLevel > 0 ? level / (float) maxLevel : 0f;

        rowY -= 0.3f;
        drawMeterBar(batch, innerX, rowY - 0.09f, rightEdge - innerX, 0.09f, fraction, accent);

        rowY -= 0.26f;
        font.setColor(HUD_LABEL);
        font.draw(batch, "LV:" + level + "/" + maxLevel, innerX, rowY);
        font.setColor(Color.WHITE);
        drawTextRightAligned(batch, "PWR:" + (int) (fraction * 100) + "%", rightEdge, rowY, HUD_LABEL);

        if (equipped && weaponId.equals("OrbitWeapon")) {
            rowY -= 0.24f;
            String shieldText;
            Color shieldColor;
            if (player.isShieldActive()) {
                shieldText = "SHIELD: ACTIVE";
                shieldColor = Color.CYAN;
            } else if (player.getShieldCooldownTimer() > 0) {
                shieldText = String.format("SHIELD_CD: %.1fs", player.getShieldCooldownTimer());
                shieldColor = HUD_LABEL;
            } else {
                shieldText = "SHIELD: READY";
                shieldColor = HUD_GREEN;
            }
            font.setColor(shieldColor);
            font.draw(batch, shieldText, innerX, rowY);
            font.setColor(Color.WHITE);
        }
    }

    private String pwrLabel(Player player) {
        float fraction = averageEquippedWeaponFraction(player);
        if (fraction <= 0f) return "--";
        if (fraction >= 0.9f) return "MAX";
        if (fraction >= 0.6f) return "HIGH";
        if (fraction >= 0.3f) return "MED";
        return "LOW";
    }

    private Color pwrColor(String label) {
        return switch (label) {
            case "MAX", "HIGH" -> HUD_GREEN;
            case "MED" -> HUD_AMBER;
            case "LOW" -> HUD_RED;
            default -> HUD_LABEL;
        };
    }

    private float averageEquippedWeaponFraction(Player player) {
        float sum = 0f;
        int count = 0;
        for (int slot = 0; slot < 2; slot++) {
            String id = player.getSlotWeaponId(slot);
            if (id == null) continue;
            sum += player.getWeaponLevel(id) / (float) player.getMaxWeaponLevel();
            count++;
        }
        return count > 0 ? sum / count : 0f;
    }

    private void drawSectionLabel(SpriteBatch batch, String text, float x, float y) {
        font.setColor(HUD_LABEL);
        font.draw(batch, "> " + text, x, y);
        font.setColor(Color.WHITE);
    }

    private void drawLabelValue(SpriteBatch batch, String label, String value, float x, float y, Color valueColor) {
        font.setColor(HUD_LABEL);
        font.draw(batch, label + " ", x, y);
        measureLayout.setText(font, label + " ");
        font.setColor(valueColor);
        font.draw(batch, value, x + measureLayout.width, y);
        font.setColor(Color.WHITE);
    }

    private void drawLabelValueRight(SpriteBatch batch, String label, String value, float rightEdgeX, float y, Color valueColor) {
        measureLayout.setText(font, label + " " + value);
        float startX = rightEdgeX - measureLayout.width;
        font.setColor(HUD_LABEL);
        font.draw(batch, label + " ", startX, y);
        measureLayout.setText(font, label + " ");
        font.setColor(valueColor);
        font.draw(batch, value, startX + measureLayout.width, y);
        font.setColor(Color.WHITE);
    }

    private void drawTextRightAligned(SpriteBatch batch, String text, float rightEdgeX, float y, Color color) {
        measureLayout.setText(font, text);
        font.setColor(color);
        font.draw(batch, text, rightEdgeX - measureLayout.width, y);
        font.setColor(Color.WHITE);
    }

    private void drawCentered(SpriteBatch batch, String text, float centerX, float y, Color color) {
        measureLayout.setText(font, text);
        font.setColor(color);
        font.draw(batch, text, centerX - measureLayout.width / 2f, y);
        font.setColor(Color.WHITE);
    }

    private void drawScaledText(SpriteBatch batch, String text, float x, float y, float scale, Color color) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
        font.setColor(color);
        font.draw(batch, text, x, y);
        font.setColor(Color.WHITE);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    private void drawScaledCentered(SpriteBatch batch, String text, float centerX, float y, float scale, Color color) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        font.getData().setScale(originalScaleX * scale, originalScaleY * scale);
        measureLayout.setText(font, text);
        font.setColor(color);
        font.draw(batch, text, centerX - measureLayout.width / 2f, y);
        font.setColor(Color.WHITE);
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    private void drawDivider(SpriteBatch batch, float x, float y, float width) {
        batch.setColor(HUD_GREEN_DIM);
        batch.draw(whitePixel, x, y, width, 0.018f);
        float m = 0.08f;
        float cx = x + width / 2f, cy = y + 0.009f;
        drawRotatedQuad(batch, cx - m / 2f, cy - m / 2f, m / 2f, m / 2f, m, m, 45f);
        batch.setColor(Color.WHITE);
    }

    private void drawBoxBorder(SpriteBatch batch, float x, float y, float width, float height, Color color) {
        float t = 0.025f;
        batch.setColor(color);
        batch.draw(whitePixel, x, y, width, t);
        batch.draw(whitePixel, x, y + height - t, width, t);
        batch.draw(whitePixel, x, y, t, height);
        batch.draw(whitePixel, x + width - t, y, t, height);
        batch.setColor(Color.WHITE);
    }

    private void drawMeterBar(SpriteBatch batch, float x, float y, float width, float height, float fraction, Color fillColor) {
        batch.setColor(0.12f, 0.16f, 0.13f, 1f);
        batch.draw(whitePixel, x, y, width, height);
        batch.setColor(fillColor);
        batch.draw(whitePixel, x, y, width * MathUtils.clamp(fraction, 0f, 1f), height);
        batch.setColor(Color.WHITE);
    }

    private void drawDiamondIcon(SpriteBatch batch, float cx, float cy, float size, Color color) {
        batch.setColor(color);
        drawRotatedQuad(batch, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f, size, size, 45f);
        batch.setColor(Color.WHITE);
    }

    // whitePixel is a plain Texture, and SpriteBatch's rotated-draw overload only exists for
    // TextureRegion except for one Texture overload that also demands an explicit source rect -
    // this just supplies that rect (the whole 1x1 pixel) so callers can rotate whitePixel like any
    // other quad (see drawDivider's marker/drawDiamondIcon).
    private void drawRotatedQuad(SpriteBatch batch, float x, float y, float originX, float originY, float width, float height, float rotation) {
        batch.draw(whitePixel, x, y, originX, originY, width, height, 1f, 1f, rotation, 0, 0, whitePixel.getWidth(), whitePixel.getHeight(), false, false);
    }

    private void drawHeartIcon(SpriteBatch batch, float x, float y, float size, Color color) {
        batch.setColor(color);
        batch.draw(heartIcon, x, y, size, size);
        batch.setColor(Color.WHITE);
    }

    // Player-facing (not debug-only, unlike drawEnemyHealthDebug below): a small meter floating
    // above every enemy that opts in via Enemy.showsHealthBar() - e.g. the tutorial's
    // bullet-streaming targets, whose regenerating health isn't otherwise visible to the player
    // deciding whether their stream is actually landing.
    public void drawEnemyHealthBars(SpriteBatch batch, Array<Enemy> enemies) {
        float barHeight = 0.12f;
        for (Enemy enemy : enemies) {
            if (!enemy.isActive() || !enemy.showsHealthBar()) continue;

            int maxHealth = enemy.getMaxHealth();
            if (maxHealth <= 0) continue;

            float fraction = MathUtils.clamp(enemy.getHealth() / (float) maxHealth, 0f, 1f);
            Rectangle r = enemy.getRectangle();
            float barX = r.x;
            float barY = r.y + r.height + 0.15f;

            Color fillColor = fraction > 0.5f ? HUD_GREEN : (fraction > 0.25f ? Color.ORANGE : Color.RED);
            drawMeterBar(batch, barX, barY, r.width, barHeight, fraction, fillColor);
            drawBoxBorder(batch, barX, barY, r.width, barHeight, Color.BLACK);
        }
    }

    // Debug-only: a small meter and "current/max" text floating above each enemy's sprite.
    public void drawEnemyHealthDebug(SpriteBatch batch, Array<Enemy> enemies) {
        float barHeight = 0.08f;
        for (Enemy enemy : enemies) {
            int maxHealth = enemy.getMaxHealth();
            if (maxHealth <= 0) continue; // not tracked for this enemy type

            int health = enemy.getHealth();
            float fraction = MathUtils.clamp(health / (float) maxHealth, 0f, 1f);

            Rectangle r = enemy.getRectangle();
            float barX = r.x;
            float barY = r.y + r.height + 0.1f;

            batch.setColor(0.25f, 0.25f, 0.25f, 1f);
            batch.draw(whitePixel, barX, barY, r.width, barHeight);

            batch.setColor(fraction > 0.5f ? Color.GREEN : (fraction > 0.25f ? Color.ORANGE : Color.RED));
            batch.draw(whitePixel, barX, barY, r.width * fraction, barHeight);
            batch.setColor(Color.WHITE);

            font.draw(batch, health + "/" + maxHealth, barX, barY + barHeight + 0.22f);
        }
    }

    // Row layout must match GameController's ROW_* constants.
    private static final int DEBUG_ROW_SEEK = 0;
    private static final int DEBUG_ROW_SLOT1 = 1;
    private static final int DEBUG_ROW_SLOT2 = 2;
    private static final int DEBUG_ROW_LEVELS_START = 3;
    private static final String[] DEBUG_WEAPON_LEVEL_IDS = {"BasicWeapon", "WaveBlastWeapon", "Thunderbolt", "OrbitWeapon"};
    private static final String[] DEBUG_WEAPON_LEVEL_LABELS = {"RAIN.sh Lvl", "Fast Lvl", "LIGHTNING.bat Lvl", "MOON.cmd Lvl"};
    private static final int DEBUG_ROW_LIVES = DEBUG_ROW_LEVELS_START + DEBUG_WEAPON_LEVEL_IDS.length;
    private static final int DEBUG_ROW_PATTERN_PREVIEW = DEBUG_ROW_LIVES + 1;
    private static final int DEBUG_ROW_REPLAY_BROWSER = DEBUG_ROW_PATTERN_PREVIEW + 1;
    private static final int DEBUG_ROW_STAGE_SELECT = DEBUG_ROW_REPLAY_BROWSER + 1;
    private static final int DEBUG_ROW_SPAWN_SCHEDULE_EDITOR = DEBUG_ROW_STAGE_SELECT + 1;
    private static final int DEBUG_ROW_BOOKMARKS_START = DEBUG_ROW_SPAWN_SCHEDULE_EDITOR + 1;

    // Debug-only: shows a "MUTED" badge in the left panel when audio is silenced.
    public void drawDebugMuteIndicator(SpriteBatch batch, float leftPanelX, float worldHeight) {
        font.setColor(Color.ORANGE);
        font.draw(batch, "MUTED", leftPanelX + 0.2f, worldHeight * 0.5f);
        font.setColor(Color.WHITE);
    }

    // Debug-only: TriggerManager's current camera distance, plus whichever gate (if any) is
    // currently freezing it and what it's still waiting on - see TriggerManager.describeActiveGate().
    // Lets a stuck trigger-driven stage (e.g. the tutorial) be diagnosed on screen instead of
    // guessing which of several back-to-back gates is the blocker. Drawn as its own solid-backed bar
    // across the top of the PLAY AREA (not either side panel, both of which are already packed edge
    // to edge with the real HUD - see drawLeftHudPanel()/drawRightHudPanel() - with nowhere left that
    // wouldn't just print this on top of/underneath that text) - the backdrop keeps it legible over
    // gameplay too, the same trick drawTextCue() already uses for its own box. Drawn whenever a
    // distance is available, gate or no - "not currently blocked" is itself useful information
    // (confirms the camera really is advancing, not just LOOKING stuck). */
    public void drawDebugTriggerInfo(SpriteBatch batch, float worldWidth, float worldHeight, float distance, String activeGateInfo) {
        float boxHeight = activeGateInfo != null ? 0.9f : 0.5f;
        float x = 0.2f;
        float yTop = worldHeight - 0.1f;
        float width = worldWidth - 0.4f;

        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(whitePixel, x, yTop - boxHeight, width, boxHeight);
        batch.setColor(Color.WHITE);

        font.setColor(Color.WHITE);
        font.draw(batch, "Trigger dist: " + String.format("%.2f", distance), x + 0.15f, yTop - 0.15f);
        if (activeGateInfo != null) {
            font.setColor(Color.ORANGE);
            font.draw(batch, "Blocked: " + activeGateInfo, x + 0.15f, yTop - 0.55f);
            font.setColor(Color.WHITE);
        }
    }

    // Debug-only: current FPS in the right panel, with the lowest/highest seen since the last
    // reset listed below it - drawn whether or not the F1 debug menu is open, since that menu's
    // own dim overlay only spans the play area, not the side panels.
    public void drawDebugFpsMonitor(SpriteBatch batch, float rightPanelX, float worldHeight, int currentFps, int lowestFps, int highestFps) {
        float x = rightPanelX + 0.2f;
        float y = worldHeight - 0.2f;
        float lineHeight = 0.4f;

        font.setColor(Color.WHITE);
        font.draw(batch, "FPS: " + currentFps, x, y);
        font.setColor(Color.GRAY);
        font.draw(batch, "Low: " + lowestFps, x, y - lineHeight);
        font.draw(batch, "High: " + highestFps, x, y - lineHeight * 2f);
        font.setColor(Color.WHITE);
    }

    private static final float FPS_HISTOGRAM_BAR_WIDTH = 0.32f;
    private static final float FPS_HISTOGRAM_BAR_GAP = 0.08f;
    private static final float FPS_HISTOGRAM_HEIGHT = 1.6f;
    private static final Color FPS_HISTOGRAM_TRACK = new Color(0.2f, 0.2f, 0.2f, 1f);

    /** Debug-only: below drawDebugFpsMonitor - one bar per second of fpsHistory (oldest on the
     *  left, this second on the right; see GameController.updateFpsMonitor), bar height scaled
     *  against the highest FPS seen so far so the chart doesn't need a fixed axis. Colored red/
     *  orange/green by how choppy that second was, so a dip reads at a glance without needing to
     *  read the bar height precisely. */
    public void drawDebugFpsHistogram(SpriteBatch batch, float rightPanelX, float worldHeight, int[] fpsHistory, int highestFps) {
        float x = rightPanelX + 0.2f;
        float labelY = worldHeight - 1.7f;
        float baseline = labelY - 0.35f - FPS_HISTOGRAM_HEIGHT;
        float trackWidth = fpsHistory.length * (FPS_HISTOGRAM_BAR_WIDTH + FPS_HISTOGRAM_BAR_GAP) - FPS_HISTOGRAM_BAR_GAP;
        int scaleFps = Math.max(highestFps, 1);

        font.setColor(Color.WHITE);
        font.draw(batch, "FPS (last 12s)", x, labelY);

        batch.setColor(FPS_HISTOGRAM_TRACK);
        batch.draw(whitePixel, x, baseline, trackWidth, FPS_HISTOGRAM_HEIGHT);

        for (int i = 0; i < fpsHistory.length; i++) {
            int fps = fpsHistory[i];
            if (fps <= 0) continue; // not sampled yet (early in the run)

            float fraction = MathUtils.clamp(fps / (float) scaleFps, 0f, 1f);
            float barHeight = FPS_HISTOGRAM_HEIGHT * fraction;
            float barX = x + i * (FPS_HISTOGRAM_BAR_WIDTH + FPS_HISTOGRAM_BAR_GAP);

            batch.setColor(fpsBarColor(fps));
            batch.draw(whitePixel, barX, baseline, FPS_HISTOGRAM_BAR_WIDTH, barHeight);
        }
        batch.setColor(Color.WHITE);
    }

    private static Color fpsBarColor(int fps) {
        if (fps < 30) return Color.RED;
        if (fps < 50) return Color.ORANGE;
        return Color.GREEN;
    }

    // Debug-only: F1 menu for jumping the spawn schedule clock to a chosen time or a saved
    // bookmark, and for setting equipped weapons/slots and their levels.
    public void drawDebugMenu(SpriteBatch batch, float worldWidth, float worldHeight, float scheduleTime,
                               float seekTime, int selectedIndex, Array<DebugSaveState> saveStates, Player player,
                               boolean audioMuted, Array<String> stageIds, int stageIndex) {
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(whitePixel, 0, 0, worldWidth, worldHeight);
        batch.setColor(Color.WHITE);

        float x = 0.4f;
        float y = worldHeight - 0.5f;
        float lineHeight = 0.4f;

        font.setColor(Color.YELLOW);
        font.draw(batch, "DEBUG MENU (F1 to close)", x, y);
        y -= lineHeight;
        font.setColor(audioMuted ? Color.ORANGE : Color.GRAY);
        font.draw(batch, (audioMuted ? "Sound: MUTED" : "Sound: ON") + "  (M to toggle)", x, y);
        y -= lineHeight;
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Schedule time: %.2fs", scheduleTime), x, y);
        y -= lineHeight * 1.5f;

        font.setColor(selectedIndex == DEBUG_ROW_SEEK ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selectedIndex == DEBUG_ROW_SEEK ? "> " : "  ") + String.format("Jump to time: %.2fs", seekTime), x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  </> scrub   Enter = go   N = save bookmark here", x, y);
        y -= lineHeight * 1.5f;

        font.setColor(selectedIndex == DEBUG_ROW_SLOT1 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selectedIndex == DEBUG_ROW_SLOT1 ? "> " : "  ") + "Slot 1: " + weaponLabel(player.getSlotWeaponId(0)), x, y);
        y -= lineHeight;
        font.setColor(selectedIndex == DEBUG_ROW_SLOT2 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selectedIndex == DEBUG_ROW_SLOT2 ? "> " : "  ") + "Slot 2: " + weaponLabel(player.getSlotWeaponId(1)), x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  </> cycle weapon", x, y);
        y -= lineHeight * 1.5f;

        for (int i = 0; i < DEBUG_WEAPON_LEVEL_IDS.length; i++) {
            int rowIndex = DEBUG_ROW_LEVELS_START + i;
            boolean selected = selectedIndex == rowIndex;
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            font.draw(batch, (selected ? "> " : "  ") + DEBUG_WEAPON_LEVEL_LABELS[i] + ": " + player.getWeaponLevel(DEBUG_WEAPON_LEVEL_IDS[i]) + "/" + player.getMaxWeaponLevel(), x, y);
            y -= lineHeight;
        }
        font.setColor(Color.GRAY);
        font.draw(batch, "  </> adjust level", x, y);
        y -= lineHeight * 1.5f;

        boolean livesSelected = selectedIndex == DEBUG_ROW_LIVES;
        font.setColor(livesSelected ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (livesSelected ? "> " : "  ") + "Lives: " + player.getNumLives(), x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  </> adjust lives", x, y);
        y -= lineHeight * 1.5f;

        boolean previewSelected = selectedIndex == DEBUG_ROW_PATTERN_PREVIEW;
        font.setColor(previewSelected ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (previewSelected ? "> " : "  ") + "Enemy / Pattern Editor", x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  Enter = create/edit enemies and patterns live", x, y);
        y -= lineHeight * 1.5f;

        boolean replayBrowserSelected = selectedIndex == DEBUG_ROW_REPLAY_BROWSER;
        font.setColor(replayBrowserSelected ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (replayBrowserSelected ? "> " : "  ") + "Replay Browser", x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  Enter = browse recorded replays", x, y);
        y -= lineHeight * 1.5f;

        boolean stageSelectSelected = selectedIndex == DEBUG_ROW_STAGE_SELECT;
        font.setColor(stageSelectSelected ? Color.YELLOW : Color.WHITE);
        String stageLabel = stageIds.size == 0 ? "(none)" : stageIds.get(stageIndex);
        font.draw(batch, (stageSelectSelected ? "> " : "  ") + "Stage: " + stageLabel, x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  </> cycle stage   Enter = load", x, y);
        y -= lineHeight * 1.5f;

        boolean scheduleEditorSelected = selectedIndex == DEBUG_ROW_SPAWN_SCHEDULE_EDITOR;
        font.setColor(scheduleEditorSelected ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (scheduleEditorSelected ? "> " : "  ") + "Spawn Schedule Editor", x, y);
        y -= lineHeight;
        font.setColor(Color.GRAY);
        font.draw(batch, "  Enter = edit the current stage's spawn events live", x, y);
        y -= lineHeight * 1.5f;

        font.setColor(Color.WHITE);
        font.draw(batch, "Bookmarks:", x, y);
        y -= lineHeight;

        if (saveStates.size == 0) {
            font.setColor(Color.GRAY);
            font.draw(batch, "  (none saved)", x, y);
            y -= lineHeight;
        } else {
            for (int i = 0; i < saveStates.size; i++) {
                DebugSaveState state = saveStates.get(i);
                boolean selected = selectedIndex == i + DEBUG_ROW_BOOKMARKS_START;
                font.setColor(selected ? Color.YELLOW : Color.WHITE);
                font.draw(batch, (selected ? "> " : "  ") + state.label + String.format(" @ %.2fs", state.time), x, y);
                y -= lineHeight;
            }
        }

        font.setColor(Color.GRAY);
        font.draw(batch, "  Enter = jump   Del = remove", x, y);

        font.setColor(Color.WHITE);
    }

    // Debug-only: file picker for recorded replays, opened from the "Replay Browser" row of the
    // main debug menu - see ReplayBrowser/GameController.startReplay().
    public void drawReplayBrowser(SpriteBatch batch, float worldWidth, float worldHeight, ReplayBrowser browser) {
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(whitePixel, 0, 0, worldWidth, worldHeight);
        batch.setColor(Color.WHITE);

        float x = 0.4f;
        float y = worldHeight - 0.5f;
        float lineHeight = 0.4f;

        font.setColor(Color.YELLOW);
        font.draw(batch, "REPLAY BROWSER (Enter = watch, Del = back, </> = page)", x, y);
        y -= lineHeight * 1.5f;

        Array<String> names = browser.getDisplayNames();
        if (names.size == 0) {
            font.setColor(Color.GRAY);
            font.draw(batch, "(no replays recorded yet)", x, y);
            y -= lineHeight;
            font.draw(batch, "Folder: " + browser.getFolderPath(), x, y);
        } else {
            if (browser.getPageCount() > 1) {
                font.setColor(Color.GRAY);
                font.draw(batch, "Page " + (browser.getCurrentPage() + 1) + "/" + browser.getPageCount(), x, y);
                y -= lineHeight;
            }
            for (int i = 0; i < names.size; i++) {
                boolean selected = i == browser.getSelectedIndexInPage();
                font.setColor(selected ? Color.YELLOW : Color.WHITE);
                font.draw(batch, (selected ? "> " : "  ") + names.get(i), x, y);
                y -= lineHeight;
            }
        }

        if (browser.getStatusMessage() != null) {
            y -= lineHeight * 0.5f;
            font.setColor(Color.ORANGE);
            font.draw(batch, browser.getStatusMessage(), x, y);
        }
        font.setColor(Color.WHITE);
    }

    // Debug-only: live editor for enemies and their movement/firing pattern trees, opened from
    // the "Enemy / Pattern Editor" row of the main debug menu. See PatternPreviewer for the row
    // model - each row is either a header, a numeric/boolean/id field, a type switcher, or an
    // action ("+ New X", "+ Add sub-pattern", "Save"). The row list can be long for a deeply
    // nested boss pattern, so this scrolls a fixed-size window around the selected row.
    private static final int PATTERN_EDITOR_VISIBLE_ROWS = 24;

    public void drawPatternPreview(SpriteBatch batch, float worldWidth, float worldHeight, PatternPreviewer previewer) {
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(whitePixel, 0, 0, worldWidth, worldHeight);
        batch.setColor(Color.WHITE);

        if (previewer.isTextEntryActive()) {
            drawTextEntryPrompt(batch, worldWidth, worldHeight, previewer);
            return;
        }

        float x = 0.4f;
        float y = worldHeight - 0.5f;
        float lineHeight = 0.35f;

        font.setColor(Color.YELLOW);
        font.draw(batch, "ENEMY / PATTERN EDITOR", x, y);
        y -= lineHeight * 1.5f;

        Array<PatternPreviewer.DisplayRow> displayRows = previewer.getDisplayRows();
        int selectedRow = previewer.getSelectedRow();

        int start = 0;
        if (displayRows.size > PATTERN_EDITOR_VISIBLE_ROWS) {
            start = MathUtils.clamp(selectedRow - PATTERN_EDITOR_VISIBLE_ROWS / 2, 0, displayRows.size - PATTERN_EDITOR_VISIBLE_ROWS);
        }
        int end = Math.min(displayRows.size, start + PATTERN_EDITOR_VISIBLE_ROWS);

        if (start > 0) {
            font.setColor(Color.GRAY);
            font.draw(batch, "  ^ more above ^", x, y);
            y -= lineHeight;
        }

        for (int i = start; i < end; i++) {
            PatternPreviewer.DisplayRow row = displayRows.get(i);
            boolean selected = i == selectedRow;
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            font.draw(batch, (selected ? "> " : "  ") + "  ".repeat(row.indent) + row.label, x, y);
            y -= lineHeight;
        }

        if (end < displayRows.size) {
            font.setColor(Color.GRAY);
            font.draw(batch, "  v more below v", x, y);
            y -= lineHeight;
        }

        y -= lineHeight * 0.5f;
        font.setColor(Color.GRAY);
        font.draw(batch, "Up/Down select   </> adjust or cycle   Enter = confirm/new id/type value", x, y);
        y -= lineHeight;
        font.draw(batch, "Del = remove sub-pattern (or close screen if nothing to remove)", x, y);

        font.setColor(Color.WHITE);
    }

    /** Modal id-entry field shown in place of the row list while PatternPreviewer is waiting on
     *  a new enemy/movement/firing pattern id (see PatternPreviewer.promptNewId). */
    private void drawTextEntryPrompt(SpriteBatch batch, float worldWidth, float worldHeight, PatternPreviewer previewer) {
        float x = 0.4f;
        float y = worldHeight / 2f + 0.7f;
        float lineHeight = 0.4f;

        font.setColor(Color.YELLOW);
        font.draw(batch, previewer.getTextEntryTitle() + ":", x, y);
        y -= lineHeight;

        font.setColor(Color.WHITE);
        font.draw(batch, "> " + previewer.getTextEntryText() + "_", x, y);
        y -= lineHeight * 1.5f;

        font.setColor(Color.GRAY);
        font.draw(batch, "Enter = confirm   Esc = cancel   Backspace = delete", x, y);

        font.setColor(Color.WHITE);
    }

    // Debug-only: the spawn event editor opened from the "Spawn Schedule Editor" row of the main
    // debug menu - see SpawnScheduleEditor for the row model (same header/number/toggle/id-pick/
    // action row shapes as PatternPreviewer, just over one SpawnEvent at a time).
    private static final int SCHEDULE_EDITOR_VISIBLE_ROWS = 24;

    public void drawSpawnScheduleEditor(SpriteBatch batch, float worldWidth, float worldHeight, SpawnScheduleEditor editor) {
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(whitePixel, 0, 0, worldWidth, worldHeight);
        batch.setColor(Color.WHITE);

        if (editor.isTextEntryActive()) {
            drawScheduleEditorTextEntryPrompt(batch, worldWidth, worldHeight, editor);
            return;
        }

        float x = 0.4f;
        float y = worldHeight - 0.5f;
        float lineHeight = 0.35f;

        font.setColor(Color.YELLOW);
        font.draw(batch, "SPAWN SCHEDULE EDITOR", x, y);
        y -= lineHeight * 1.5f;

        Array<SpawnScheduleEditor.DisplayRow> displayRows = editor.getDisplayRows();
        int selectedRow = editor.getSelectedRow();

        int start = 0;
        if (displayRows.size > SCHEDULE_EDITOR_VISIBLE_ROWS) {
            start = MathUtils.clamp(selectedRow - SCHEDULE_EDITOR_VISIBLE_ROWS / 2, 0, displayRows.size - SCHEDULE_EDITOR_VISIBLE_ROWS);
        }
        int end = Math.min(displayRows.size, start + SCHEDULE_EDITOR_VISIBLE_ROWS);

        if (start > 0) {
            font.setColor(Color.GRAY);
            font.draw(batch, "  ^ more above ^", x, y);
            y -= lineHeight;
        }

        for (int i = start; i < end; i++) {
            SpawnScheduleEditor.DisplayRow row = displayRows.get(i);
            boolean selected = i == selectedRow;
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
            font.draw(batch, (selected ? "> " : "  ") + "  ".repeat(row.indent) + row.label, x, y);
            y -= lineHeight;
        }

        if (end < displayRows.size) {
            font.setColor(Color.GRAY);
            font.draw(batch, "  v more below v", x, y);
            y -= lineHeight;
        }

        y -= lineHeight * 0.5f;
        font.setColor(Color.GRAY);
        font.draw(batch, "Up/Down select   </> adjust or cycle   Enter = confirm/type value", x, y);
        y -= lineHeight;
        font.draw(batch, "Del (on the Event row) = remove event, else close screen", x, y);

        font.setColor(Color.WHITE);
    }

    /** Modal number-entry field shown in place of the row list while SpawnScheduleEditor is
     *  waiting on typed input (see SpawnScheduleEditor.promptNumber) - same modal as
     *  drawTextEntryPrompt below, just reading from SpawnScheduleEditor instead of PatternPreviewer
     *  since the two screens' text-entry state isn't shared. */
    private void drawScheduleEditorTextEntryPrompt(SpriteBatch batch, float worldWidth, float worldHeight, SpawnScheduleEditor editor) {
        float x = 0.4f;
        float y = worldHeight / 2f + 0.7f;
        float lineHeight = 0.4f;

        font.setColor(Color.YELLOW);
        font.draw(batch, editor.getTextEntryTitle() + ":", x, y);
        y -= lineHeight;

        font.setColor(Color.WHITE);
        font.draw(batch, "> " + editor.getTextEntryText() + "_", x, y);
        y -= lineHeight * 1.5f;

        font.setColor(Color.GRAY);
        font.draw(batch, "Enter = confirm   Esc = cancel   Backspace = delete", x, y);

        font.setColor(Color.WHITE);
    }

    private String weaponLabel(String weaponId) {
        if (weaponId == null) return "-";
        return switch (weaponId) {
            case "BasicWeapon" -> "RAIN.sh";
            case "WaveBlastWeapon" -> "Fast";
            case "OrbitWeapon" -> "MOON.cmd";
            case "Thunderbolt" -> "LIGHTNING.bat";
            default -> weaponId;
        };
    }

    /** elapsedTime is seconds since gameOver first became true (GameController.getGameOverTimer())
     *  - drives the GameOverSign reveal, which plays once and then holds on its last frame instead
     *  of looping (see gameOverSignAnimation's PlayMode.NORMAL). */
    public void drawGameOver(SpriteBatch batch, float worldWidth, float worldHeight, float elapsedTime) {
        float aspect = gameOverSignTexture.getWidth() / 4f / (gameOverSignTexture.getHeight() / 3f);
        float signWidth = 6.5f;
        float signHeight = signWidth / aspect;
        float signCenterY = worldHeight * 0.62f;
        TextureRegion frame = gameOverSignAnimation.getKeyFrame(elapsedTime);
        batch.draw(frame, (worldWidth - signWidth) / 2f, signCenterY - signHeight / 2f, signWidth, signHeight);

        font.setColor(Color.RED);
        if (inputType == InputType.KEYBOARD) {gameOverLayout.setText(font, "Press R to Restart\nPress Q to Quit");}
        else gameOverLayout.setText(font, "Press Start to Restart\nPress Select to Quit");
        float textCenterY = worldHeight * 0.28f;
        font.draw(batch, gameOverLayout, (worldWidth - gameOverLayout.width) / 2, textCenterY + gameOverLayout.height / 2);
        font.setColor(Color.WHITE);
    }

    /** "Cyber terminal" stage-clear screen, restyled to match the same HUD language as
     *  drawHUD()/drawLeftHudPanel() (reference mockup: Screenshot 2026-08-07 093835.png) instead
     *  of the old plain centered text block. Shows what's actually tracked: enemies destroyed vs.
     *  SpawnScheduler's total scripted spawn count (ScoreManager.enemiesDestroyed /
     *  GameController.getTotalEnemyCount(), shown as "ICE DELETED"), the run's peak chain
     *  (ScoreManager.maxChainCount), the boss takedown time bonus and the rest of the bonus
     *  breakdown from GameController.applyLevelCompleteBonus(), and the final score - plus, beside
     *  that stats panel like the mockup's "TACTICAL RANK" card, the letter grade GameController.
     *  computeRank() derives from those same stats. */
    public void drawLevelComplete(SpriteBatch batch, float worldWidth, float worldHeight, int score, int bombBonus, int livesMultiplier,
                                   int enemiesDestroyed, int totalEnemies, int bossTimeBonus, float bossFightSeconds, int maxChainCount,
                                   LevelRank rank, boolean hasNextStage, int stageNumber) {
        batch.setColor(0f, 0f, 0f, 0.88f);
        batch.draw(whitePixel, 0, 0, worldWidth, worldHeight);
        batch.setColor(Color.WHITE);

        float centerX = worldWidth / 2f;
        float margin = 0.6f;
        float panelX = margin;
        float panelWidth = worldWidth - margin * 2f;

        String title = hasNextStage ? "STAGE " + stageNumber + " CLEAR" : "MISSION COMPLETE";
        float titleY = worldHeight - 0.9f;
        drawGlowCentered(batch, title, centerX, titleY, 2.6f, HUD_GREEN_DIM, Color.WHITE);

        float subtitleY = titleY - 0.5f;
        drawCentered(batch, "-- HOSTILE ARRAY NEUTRALIZED --", centerX, subtitleY, HUD_GREEN);

        // Decorative scan panel standing in for the mockup's radar graphic - a full CircleMeterEffect
        // ring (fraction 1.0, "scan complete") captioned with the boss kill that always triggers
        // level completion (see GameController: levelCompleteDelayTimer only starts once
        // EntityManager.consumeBossKilled() fires).
        float scanTop = subtitleY - 1.0f;
        float scanHeight = 2.6f;
        float scanBottom = scanTop - scanHeight;
        drawBoxBorder(batch, panelX, scanBottom, panelWidth, scanHeight, HUD_GREEN_DIM);

        float ringSize = 1.5f;
        float ringCenterY = scanBottom + scanHeight / 2f + 0.2f;
        circleMeterEffect.render(batch, whitePixel, 1f, HUD_GAUGE_BG, HUD_GREEN,
            0.34f, 0.5f, centerX - ringSize / 2f, ringCenterY - ringSize / 2f, ringSize);
        drawScaledCentered(batch, "100%", centerX, ringCenterY - 0.12f, 1.3f, HUD_GREEN);

        drawCentered(batch, "[ BOSS DEFEATED ]", centerX, scanBottom + 0.35f, HUD_AMBER);

        // Bonus breakdown panel, with the rank badge card beside it (mockup layout) rather than
        // below it - both share the same top/bottom/height so their borders line up.
        float statsTop = scanBottom - 0.6f;
        float statsHeight = 4.3f;
        float statsBottom = statsTop - statsHeight;

        float cardGap = 0.2f;
        float rankWidth = 2.6f;
        float statsWidth = panelWidth - rankWidth - cardGap;
        float statsX = panelX;
        float rankX = statsX + statsWidth + cardGap;

        drawBoxBorder(batch, statsX, statsBottom, statsWidth, statsHeight, HUD_GREEN_DIM);
        drawRankCard(batch, rankX, statsBottom, rankWidth, statsHeight, rank);

        float rowX = statsX + 0.25f;
        float rowRight = statsX + statsWidth - 0.25f;
        float rowY = statsTop - 0.35f;
        drawSectionLabel(batch, "MISSION_LOG", rowX, rowY);
        rowY -= 0.3f;
        drawDivider(batch, rowX, rowY, rowRight - rowX);

        rowY -= 0.45f;
        drawStatRow(batch, "ICE DELETED", String.format("%03d / %03d", enemiesDestroyed, totalEnemies), rowX, rowRight, rowY, HUD_GREEN);
        rowY -= 0.45f;
        drawStatRow(batch, "PEAK CHAIN", maxChainCount + " HITS", rowX, rowRight, rowY, HUD_GREEN);
        rowY -= 0.45f;
        if (bossTimeBonus > 0) {
            drawStatRow(batch, "BOSS BONUS", String.format("+%d PTS (%.1fs)", bossTimeBonus, bossFightSeconds), rowX, rowRight, rowY, HUD_AMBER);
            rowY -= 0.45f;
        }
        if (bombBonus > 0) {
            drawStatRow(batch, "BOMB BONUS", "+" + bombBonus + " PTS", rowX, rowRight, rowY, HUD_AMBER);
            rowY -= 0.45f;
        }
        if (livesMultiplier > 0) {
            drawStatRow(batch, "LIVES BONUS", "x" + (livesMultiplier + 1) + " MULTIPLIER", rowX, rowRight, rowY, HUD_GREEN);
        }

        float totalBoxHeight = 0.55f;
        float totalBoxY = statsBottom + 0.2f;
        batch.setColor(0.05f, 0.14f, 0.09f, 1f);
        batch.draw(whitePixel, rowX, totalBoxY, rowRight - rowX, totalBoxHeight);
        batch.setColor(Color.WHITE);
        drawBoxBorder(batch, rowX, totalBoxY, rowRight - rowX, totalBoxHeight, HUD_GREEN);
        font.setColor(Color.WHITE);
        font.draw(batch, "TOTAL SCORE", rowX + 0.15f, totalBoxY + totalBoxHeight * 0.65f);
        drawTextRightAligned(batch, String.format("%010d", score), rowRight - 0.15f, totalBoxY + totalBoxHeight * 0.65f, HUD_GREEN);

        String prompt;
        if (inputType == InputType.KEYBOARD) {
            prompt = hasNextStage ? "R = CONTINUE   Q = QUIT" : "R = RESTART   Q = QUIT";
        } else {
            prompt = hasNextStage ? "START = CONTINUE   SELECT = QUIT" : "START = RESTART   SELECT = QUIT";
        }
        drawCentered(batch, prompt, centerX, statsBottom - 0.5f, HUD_LABEL);
    }

    /** The mockup's side "TACTICAL RANK" card: a bordered box (border tinted by rank) holding a
     *  full CircleMeterEffect ring around the big letter grade, with "TACTICAL RANK" and the
     *  rank's flavor subtitle (LevelRank.subtitle) centered below it. */
    private void drawRankCard(SpriteBatch batch, float x, float y, float width, float height, LevelRank rank) {
        Color accent = rankColor(rank);
        drawBoxBorder(batch, x, y, width, height, accent);

        float centerX = x + width / 2f;
        float contentCenterY = y + height / 2f;

        float ringSize = 1.4f;
        float ringCenterY = contentCenterY + 0.55f;
        circleMeterEffect.render(batch, whitePixel, 1f, HUD_GAUGE_BG, accent,
            0.3f, 0.5f, centerX - ringSize / 2f, ringCenterY - ringSize / 2f, ringSize);
        drawScaledCentered(batch, rank.name(), centerX, ringCenterY + 0.1f, 2.4f, accent);

        float labelY = ringCenterY - ringSize / 2f - 0.35f;
        drawCentered(batch, "TACTICAL RANK", centerX, labelY, HUD_LABEL);
        drawCentered(batch, rank.subtitle, centerX, labelY - 0.35f, accent);
    }

    private Color rankColor(LevelRank rank) {
        return switch (rank) {
            case S -> HUD_AMBER;
            case A, B -> HUD_GREEN;
            case C -> HUD_LABEL;
            case D -> HUD_RED;
        };
    }

    private void drawStatRow(SpriteBatch batch, String label, String value, float x, float rightEdge, float y, Color valueColor) {
        font.setColor(HUD_LABEL);
        font.draw(batch, label, x, y);
        font.setColor(Color.WHITE);
        drawTextRightAligned(batch, value, rightEdge, y, valueColor);
    }

    // Cheap outline/glow: the base color drawn once, on top of the glow color drawn at four
    // diagonal offsets - approximates the reference mockup's neon-outlined title without a real
    // shader pass.
    private void drawGlowCentered(SpriteBatch batch, String text, float centerX, float y, float scale, Color glowColor, Color color) {
        float o = 0.035f;
        drawScaledCentered(batch, text, centerX - o, y - o, scale, glowColor);
        drawScaledCentered(batch, text, centerX + o, y - o, scale, glowColor);
        drawScaledCentered(batch, text, centerX - o, y + o, scale, glowColor);
        drawScaledCentered(batch, text, centerX + o, y + o, scale, glowColor);
        drawScaledCentered(batch, text, centerX, y, scale, color);
    }

    // Solid gray backdrop drawn behind a text cue's own bounds (see drawTextCue()) to keep it
    // legible over whatever's on screen behind it (shader backgrounds, bullets, enemies).
    private static final Color TEXT_CUE_BOX_COLOR = new Color(0.5f, 0.5f, 0.5f, 0.85f);
    private static final float TEXT_CUE_BOX_PAD_X = 0.25f;
    private static final float TEXT_CUE_BOX_PAD_TOP = 0.15f;
    private static final float TEXT_CUE_BOX_PAD_BOTTOM = 0.15f;
    // Extra room reserved below the message itself for the "press to continue" hint - see
    // drawTextCue()'s showHint branch, only used while requireConfirm is true.
    private static final float TEXT_CUE_HINT_HEIGHT = 0.4f;

    // realTime must be SpawnScheduler's own never-frozen clock (see TextCue.triggeredAtRealTime),
    // not its gate-freezable totalTime - otherwise a cue whose window spans an unsatisfied gate
    // would stall its typewriter reveal for however long the player takes to clear it.
    // @param requireConfirm mirrors SpawnScheduler.isTextCuesRequireConfirm() for the owning
    //  schedule - true suppresses the normal duration-based auto-hide (a confirm-gated cue lingers
    //  until SpawnScheduler.update() marks it dismissed, however long that takes) and draws the
    //  "press to continue" hint; confirmKeyLabel is only read in that case.
    public void drawTextCues(SpriteBatch batch, float realTime, Array<TextCue> cues, boolean requireConfirm, String confirmKeyLabel) {
        if (cues == null) return;

        for (TextCue cue : cues) {
            if (cue.dismissed || cue.triggeredAtRealTime < 0f) continue;
            float cueElapsedTime = realTime - cue.triggeredAtRealTime;
            if (cueElapsedTime < 0f) continue;
            if (!requireConfirm && cueElapsedTime >= cue.duration) continue;
            drawTextCue(batch, cue, cueElapsedTime, requireConfirm, confirmKeyLabel);
        }
        font.setColor(Color.WHITE);
    }

    private void drawTextCue(SpriteBatch batch, TextCue cue, float cueElapsedTime, boolean requireConfirm, String confirmKeyLabel) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        if (cue.fontSize != 1f) {
            font.getData().setScale(originalScaleX * cue.fontSize, originalScaleY * cue.fontSize);
        }

        // Measured against the cue's FULL text, not just whatever's revealed so far - keeps the
        // backdrop box a stable size while a typewriter cue is still typing instead of growing
        // along with it.
        textCueLayout.setText(font, cue.text);
        float textWidth = textCueLayout.width;
        float textHeight = textCueLayout.height;

        float x = cue.x;
        float y = cue.y; // top edge of the text block, per font.draw's own convention
        if (cue.centered) {
            x = cue.x - textWidth / 2f;
            y = cue.y + textHeight / 2f;
        }

        // The box must cover whichever of the message or the hint line is wider - a short message
        // (e.g. "Nice flying!") is often narrower than "Press SPACE to continue", and both share
        // the same left edge x, so sizing the box off the message alone left the hint sticking out
        // past its right edge, uncovered.
        String hintText = requireConfirm ? "Press " + confirmKeyLabel + " to continue" : null;
        float hintWidth = 0f;
        if (hintText != null) {
            measureLayout.setText(font, hintText);
            hintWidth = measureLayout.width;
        }
        float blockWidth = Math.max(textWidth, hintWidth);

        float hintReserve = requireConfirm ? TEXT_CUE_HINT_HEIGHT : 0f;
        float boxX = x - TEXT_CUE_BOX_PAD_X;
        float boxY = y - textHeight - TEXT_CUE_BOX_PAD_BOTTOM - hintReserve;
        float boxWidth = blockWidth + TEXT_CUE_BOX_PAD_X * 2f;
        float boxHeight = textHeight + TEXT_CUE_BOX_PAD_TOP + TEXT_CUE_BOX_PAD_BOTTOM + hintReserve;
        batch.setColor(TEXT_CUE_BOX_COLOR);
        batch.draw(whitePixel, boxX, boxY, boxWidth, boxHeight);
        batch.setColor(Color.WHITE);

        font.setColor(Color.RED);
        switch (cue.effect) {
            case "typewriter" -> drawTypewriter(batch, cue.text, x, y, cueElapsedTime, cue.charsPerSecond);
            case "blinking" -> drawBlinking(batch, cue.text, x, y, cueElapsedTime, cue.blinksPerSecond);
            default -> font.draw(batch, cue.text, x, y);
        }

        if (hintText != null) {
            font.setColor(HUD_LABEL);
            font.draw(batch, hintText, x, y - textHeight - 0.1f);
        }

        if (cue.fontSize != 1f) {
            font.getData().setScale(originalScaleX, originalScaleY);
        }
    }

    public boolean drawTypewriter(SpriteBatch batch, String text, float x, float y, float elapsedTime, float charsPerSecond) {
        int totalChars = text.length();
        int visibleChars = MathUtils.clamp((int) (elapsedTime * charsPerSecond), 0, totalChars);
        font.draw(batch, text.substring(0, visibleChars), x, y);
        return visibleChars >= totalChars;
    }

    public boolean drawBlinking(SpriteBatch batch, String text, float x, float y, float elapsedTime, float blinksPerSecond) {
        boolean visible = ((int) (elapsedTime * blinksPerSecond * 2f)) % 2 == 0;
        if (visible) {
            font.draw(batch, text, x, y);
        }
        return visible;
    }

    @Override
    public void dispose() {
        font.dispose();
        whitePixel.dispose();
        heartIcon.dispose();
        gameOverSignTexture.dispose();
        circleMeterEffect.dispose();
        chainFireEffect.dispose();
    }
}
