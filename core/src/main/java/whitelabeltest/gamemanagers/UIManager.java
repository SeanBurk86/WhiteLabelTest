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
    private final GlyphLayout destroyIceLayout;
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
        destroyIceLayout = new GlyphLayout();

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

    private static final String LEVEL_START_LINE_1 = "Accessing secure node...\nExecuting penetration protocol...\nNode ICE detected...\nDaemon loaded...\nDaemon temp: FLAMING HOT!!!";
    private static final String DESTROY_ICE_WARNING = "DESTROY ICE";
    private static final float LEVEL_START_DURATION = 7f;
    private static final float LEVEL_START_LINE_DURATION = LEVEL_START_DURATION / 2f;
    private static final float LEVEL_START_CHARS_PER_SECOND = 30f;
    private static final float DESTROY_ICE_SCALE_MULTIPLIER = 5f;
    private static final float DESTROY_ICE_BLINKS_PER_SECOND = 4f;

    public void drawLevelStartAesthetics(SpriteBatch batch, float worldWidth, float worldHeight, float elapsedTime) {
        if (elapsedTime >= LEVEL_START_DURATION) return;

        font.setColor(Color.RED);
        float x = 0.3f;
        float y = worldHeight - 0.3f;
        if (elapsedTime < LEVEL_START_LINE_DURATION) {
            drawTypewriter(batch, LEVEL_START_LINE_1, x, y, elapsedTime, LEVEL_START_CHARS_PER_SECOND);
        } else {
            float originalScaleX = font.getData().scaleX;
            float originalScaleY = font.getData().scaleY;
            font.getData().setScale(originalScaleX * DESTROY_ICE_SCALE_MULTIPLIER, originalScaleY * DESTROY_ICE_SCALE_MULTIPLIER);

            destroyIceLayout.setText(font, DESTROY_ICE_WARNING);
            float bigX = (worldWidth - destroyIceLayout.width) / 2f;
            float bigY = (worldHeight + destroyIceLayout.height) / 2f;
            drawBlinking(batch, DESTROY_ICE_WARNING, bigX, bigY, elapsedTime - LEVEL_START_LINE_DURATION, DESTROY_ICE_BLINKS_PER_SECOND);

            font.getData().setScale(originalScaleX, originalScaleY);
        }
        font.setColor(Color.WHITE);
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
