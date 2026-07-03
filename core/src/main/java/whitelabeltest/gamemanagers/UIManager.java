package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.player.Player;

public class UIManager implements Disposable {
    private final BitmapFont font;
    private final GlyphLayout gameOverLayout;
    private final Texture whitePixel;
    private final InputType inputType;

    public UIManager(InputType inputType) {
        this.inputType = inputType;
        font = new BitmapFont();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.02f);
        gameOverLayout = new GlyphLayout();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();
    }

    public void drawHUD(SpriteBatch batch, ScoreManager scoreManager, Player player, float worldHeight, float leftPanelX) {
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
        font.draw(batch, "Wave Lvl: " + player.getWeaponLevel("ThunderWhipWeapon"), textX, worldHeight - 1.6f);
        font.draw(batch, "Orbit Lvl: " + player.getWeaponLevel("OrbitWeapon"), textX, worldHeight - 2.0f);
        font.draw(batch, "# of Bombs: " + player.getNumBombs(), textX, worldHeight - 2.4f);

        font.setColor(Color.CYAN);
        font.draw(batch, "Graze:", textX, worldHeight - 2.6f);
        font.setColor(Color.WHITE);
        drawGrazeMeter(batch, Math.min(player.getGrazePoints() / 100f, 1f), textX, worldHeight - 2.73f, barWidth);

        font.draw(batch, "# of Lives: " + player.getNumLives(), textX, worldHeight - 3.0f);
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

    @Override
    public void dispose() {
        font.dispose();
        whitePixel.dispose();
    }
}
