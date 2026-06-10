package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.player.Player;

public class UIManager implements Disposable {
    private final BitmapFont font;
    private final GlyphLayout gameOverLayout;

    public UIManager() {
        font = new BitmapFont();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.02f);
        gameOverLayout = new GlyphLayout();
    }

    public void drawHUD(SpriteBatch batch, int score, Player player, float worldHeight) {
        font.setColor(Color.WHITE);
        font.draw(batch, "Score: " + score, 0.2f, worldHeight - 0.2f);

        // Weapon levels display using String IDs
        font.draw(batch, "Basic Lvl: " + player.getWeaponLevel("BasicWeapon"), 0.2f, worldHeight - 0.6f);
        font.draw(batch, "Fast Lvl: " + player.getWeaponLevel("WaveBlastWeapon"), 0.2f, worldHeight - 1.0f);
        font.draw(batch, "Wave Lvl: " + player.getWeaponLevel("ThunderWhipWeapon"), 0.2f, worldHeight - 1.4f);
        font.draw(batch, "Orbit Lvl: " + player.getWeaponLevel("OrbitWeapon"), 0.2f, worldHeight - 1.8f);
    }

    public void drawGameOver(SpriteBatch batch, float worldWidth, float worldHeight) {
        font.setColor(Color.RED);
        gameOverLayout.setText(font, "GAME OVER\nPress R to Restart\nPress Q to Quit");
        font.draw(batch, gameOverLayout, (worldWidth - gameOverLayout.width) / 2, (worldHeight + gameOverLayout.height) / 2);
        font.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
