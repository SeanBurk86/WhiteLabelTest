package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.player.Player;

public class UIManager implements Disposable {
    private final BitmapFont font;
    private final GlyphLayout gameOverLayout;
    private final GlyphLayout levelCompleteLayout;
    private final GlyphLayout textCueLayout;
    private final Texture whitePixel;
    private final InputType inputType;

    public UIManager(InputType inputType) {
        this.inputType = inputType;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("VT323-Regular.ttf"));
        FreeTypeFontParameter fontParams = new FreeTypeFontParameter();
        fontParams.size = 32;
        font = generator.generateFont(fontParams);
        generator.dispose();

        font.setUseIntegerPositions(false);
        font.getData().setScale(0.009375f);
        gameOverLayout = new GlyphLayout();
        levelCompleteLayout = new GlyphLayout();
        textCueLayout = new GlyphLayout();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
    }

    public void drawHUD(SpriteBatch batch, ScoreManager scoreManager, Player player, float worldHeight, float leftPanelX, float bombCooldownTimer, float bombCooldownFraction) {
        float textX = leftPanelX + 0.2f;
        float barWidth = -leftPanelX - 0.4f;

        font.setColor(Color.WHITE);
        font.draw(batch, "Score: " + scoreManager.getScore(), textX, worldHeight - 0.2f);

        if (scoreManager.getChainCount() > 0) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "Chain x" + scoreManager.getChainCount(), textX, worldHeight - 0.45f);
            font.setColor(Color.WHITE);

            drawChainMeter(batch, scoreManager.getChainTimerFraction(), textX, worldHeight - 0.58f, barWidth);
        }

        font.draw(batch, "Basic Lvl: " + player.getWeaponLevel("BasicWeapon"), textX, worldHeight - 0.8f);
        font.draw(batch, "Fast Lvl: " + player.getWeaponLevel("WaveBlastWeapon"), textX, worldHeight - 1.2f);
        font.draw(batch, "Bolt Lvl: " + player.getWeaponLevel("Thunderbolt"), textX, worldHeight - 1.6f);
        font.draw(batch, "Orbit Lvl: " + player.getWeaponLevel("OrbitWeapon"), textX, worldHeight - 2.0f);

        if (player.getWeaponLevel("OrbitWeapon") > 0) {
            if (player.isShieldActive()) {
                font.setColor(Color.CYAN);
                font.draw(batch, "Shield: Active", textX, worldHeight - 2.13f);
                font.setColor(Color.WHITE);
            } else if (player.getShieldCooldownTimer() > 0) {
                font.setColor(Color.GRAY);
                font.draw(batch, String.format("Shield Cooldown: %.1fs", player.getShieldCooldownTimer()), textX, worldHeight - 2.13f);
                font.setColor(Color.WHITE);
                drawShieldCooldownMeter(batch, 1f - player.getShieldCooldownFraction(), textX, worldHeight - 2.26f, barWidth);
            } else {
                font.setColor(Color.GREEN);
                font.draw(batch, "Shield: Ready", textX, worldHeight - 2.13f);
                font.setColor(Color.WHITE);
            }
        }

        font.draw(batch, "# of Bombs: " + player.getNumBombs(), textX, worldHeight - 2.4f);

        if (bombCooldownTimer > 0) {
            font.setColor(Color.GRAY);
            font.draw(batch, String.format("Bomb Cooldown: %.1fs", bombCooldownTimer), textX, worldHeight - 2.5f);
            font.setColor(Color.WHITE);
            drawBombCooldownMeter(batch, 1f - bombCooldownFraction, textX, worldHeight - 2.63f, barWidth);
        }

        font.setColor(Color.CYAN);
        font.draw(batch, "Graze:", textX, worldHeight - 2.85f);
        font.setColor(Color.WHITE);
        drawGrazeMeter(batch, Math.min(player.getGrazePoints() / 100f, 1f), textX, worldHeight - 2.98f, barWidth);

        font.draw(batch, "# of Lives: " + player.getNumLives(), textX, worldHeight - 3.25f);

        font.setColor(player.getActiveSlot() == 0 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, "Slot 1: " + weaponLabel(player.getSlotWeaponId(0)) + (player.getActiveSlot() == 0 ? " <" : ""), textX, worldHeight - 3.6f);
        font.setColor(player.getActiveSlot() == 1 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, "Slot 2: " + weaponLabel(player.getSlotWeaponId(1)) + (player.getActiveSlot() == 1 ? " <" : ""), textX, worldHeight - 3.95f);
        font.setColor(Color.WHITE);
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
    private static final String[] DEBUG_WEAPON_LEVEL_LABELS = {"Basic Lvl", "Fast Lvl", "Bolt Lvl", "Orbit Lvl"};
    private static final int DEBUG_ROW_LIVES = DEBUG_ROW_LEVELS_START + DEBUG_WEAPON_LEVEL_IDS.length;
    private static final int DEBUG_ROW_PATTERN_PREVIEW = DEBUG_ROW_LIVES + 1;
    private static final int DEBUG_ROW_BOOKMARKS_START = DEBUG_ROW_PATTERN_PREVIEW + 1;

    // Debug-only: shows a "MUTED" badge in the left panel when audio is silenced.
    public void drawDebugMuteIndicator(SpriteBatch batch, float leftPanelX, float worldHeight) {
        font.setColor(Color.ORANGE);
        font.draw(batch, "MUTED", leftPanelX + 0.2f, worldHeight * 0.5f);
        font.setColor(Color.WHITE);
    }

    // Debug-only: F1 menu for jumping the spawn schedule clock to a chosen time or a saved
    // bookmark, and for setting equipped weapons/slots and their levels.
    public void drawDebugMenu(SpriteBatch batch, float worldWidth, float worldHeight, float scheduleTime,
                               float seekTime, int selectedIndex, Array<DebugSaveState> saveStates, Player player,
                               boolean audioMuted) {
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
        font.draw(batch, "Up/Down select   </> adjust or cycle   Enter = confirm/new id", x, y);
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

    private String weaponLabel(String weaponId) {
        if (weaponId == null) return "-";
        return switch (weaponId) {
            case "BasicWeapon" -> "Basic";
            case "WaveBlastWeapon" -> "Fast";
            case "OrbitWeapon" -> "Orbit";
            case "Thunderbolt" -> "Bolt";
            default -> weaponId;
        };
    }

    private void drawChainMeter(SpriteBatch batch, float fraction, float x, float y, float totalWidth) {
        float height = 0.08f;

        batch.setColor(0.25f, 0.25f, 0.25f, 1f);
        batch.draw(whitePixel, x, y, totalWidth, height);

        Color fill = fraction > 0.5f ? Color.YELLOW : (fraction > 0.25f ? Color.ORANGE : Color.RED);
        batch.setColor(fill);
        batch.draw(whitePixel, x, y, totalWidth * fraction, height);

        batch.setColor(Color.WHITE);
    }

    private void drawBombCooldownMeter(SpriteBatch batch, float fraction, float x, float y, float totalWidth) {
        float height = 0.08f;

        batch.setColor(0.25f, 0.25f, 0.25f, 1f);
        batch.draw(whitePixel, x, y, totalWidth, height);

        batch.setColor(Color.GRAY);
        batch.draw(whitePixel, x, y, totalWidth * fraction, height);

        batch.setColor(Color.WHITE);
    }

    private void drawShieldCooldownMeter(SpriteBatch batch, float fraction, float x, float y, float totalWidth) {
        float height = 0.08f;

        batch.setColor(0.25f, 0.25f, 0.25f, 1f);
        batch.draw(whitePixel, x, y, totalWidth, height);

        batch.setColor(Color.CYAN);
        batch.draw(whitePixel, x, y, totalWidth * fraction, height);

        batch.setColor(Color.WHITE);
    }

    private void drawGrazeMeter(SpriteBatch batch, float fraction, float x, float y, float totalWidth) {
        float height = 0.08f;

        batch.setColor(0.25f, 0.25f, 0.25f, 1f);
        batch.draw(whitePixel, x, y, totalWidth, height);

        batch.setColor(fraction > 0.8f ? Color.WHITE : Color.CYAN);
        batch.draw(whitePixel, x, y, totalWidth * fraction, height);

        batch.setColor(Color.WHITE);
    }

    public void drawGameOver(SpriteBatch batch, float worldWidth, float worldHeight) {
        font.setColor(Color.RED);
        if (inputType == InputType.KEYBOARD) {gameOverLayout.setText(font, "GAME OVER\nPress R to Restart\nPress Q to Quit");}
        else gameOverLayout.setText(font, "GAME OVER\nPress Start to Restart\nPress Select to Quit");
        font.draw(batch, gameOverLayout, (worldWidth - gameOverLayout.width) / 2, (worldHeight + gameOverLayout.height) / 2);
        font.setColor(Color.WHITE);
    }

    public void drawLevelComplete(SpriteBatch batch, float worldWidth, float worldHeight, int score) {
        font.setColor(Color.RED);
        if (inputType == InputType.KEYBOARD) {
            levelCompleteLayout.setText(font, "LEVEL COMPLETE\nYour Score: " + score + "\nPress R to Restart\nPress Q to Quit");
        } else {
            levelCompleteLayout.setText(font, "LEVEL COMPLETE\nYour Score: " + score + "\nPress Start to Restart\nPress Select to Quit");
        }
        font.draw(batch, levelCompleteLayout, (worldWidth - levelCompleteLayout.width) / 2, (worldHeight + levelCompleteLayout.height) / 2);
        font.setColor(Color.WHITE);
    }

    public void drawTextCues(SpriteBatch batch, float elapsedTime, Array<TextCue> cues) {
        if (cues == null) return;

        font.setColor(Color.RED);
        for (TextCue cue : cues) {
            if (elapsedTime < cue.time || elapsedTime >= cue.time + cue.duration) continue;
            drawTextCue(batch, cue, elapsedTime - cue.time);
        }
        font.setColor(Color.WHITE);
    }

    private void drawTextCue(SpriteBatch batch, TextCue cue, float cueElapsedTime) {
        float originalScaleX = font.getData().scaleX;
        float originalScaleY = font.getData().scaleY;
        if (cue.fontSize != 1f) {
            font.getData().setScale(originalScaleX * cue.fontSize, originalScaleY * cue.fontSize);
        }

        float x = cue.x;
        float y = cue.y;
        if (cue.centered) {
            textCueLayout.setText(font, cue.text);
            x = cue.x - textCueLayout.width / 2f;
            y = cue.y + textCueLayout.height / 2f;
        }

        switch (cue.effect) {
            case "typewriter" -> drawTypewriter(batch, cue.text, x, y, cueElapsedTime, cue.charsPerSecond);
            case "blinking" -> drawBlinking(batch, cue.text, x, y, cueElapsedTime, cue.blinksPerSecond);
            default -> font.draw(batch, cue.text, x, y);
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
    }
}
