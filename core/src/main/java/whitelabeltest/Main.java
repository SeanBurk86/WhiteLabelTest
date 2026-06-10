package whitelabeltest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.GameController;
import whitelabeltest.gamemanagers.UIManager;
import whitelabeltest.player.powerups.Powerup;

public class Main extends ApplicationAdapter {
    private UIManager ui;
    private GameController game;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapeRenderer;
    private FitViewport viewport;

    private final float PLAY_AREA_WIDTH = 9f;
    private final float PLAY_AREA_HEIGHT = 12f;
//    private final float UI_AREA_WIDTH = 3f;
//    private final float TOTAL_SCREEN_WIDTH = PLAY_AREA_WIDTH + UI_AREA_WIDTH;
//    private final float TOTAL_SCREEN_HEIGHT = PLAY_AREA_HEIGHT;

    @Override
    public void create() {
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        ui = new UIManager();

        spriteBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        viewport = new FitViewport(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        game.update(delta);
        draw();
    }

    private void draw() {
        ScreenUtils.clear(game.isGameOver() ? new Color(0.2f, 0, 0, 1) : Color.BLACK);

        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();

        game.draw(spriteBatch);

        ui.drawHUD(spriteBatch, game.getScore(), game.getEntities().getPlayer(), PLAY_AREA_HEIGHT);

        if (game.isGameOver()) {
            ui.drawGameOver(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        }
        spriteBatch.end();

        if (game.isDebugMode()) {
            drawDebug();
        }
    }

    private void drawDebug() {
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        EntityManager em = game.getEntities();

        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.rect(em.getPlayer().getHitbox().x, em.getPlayer().getHitbox().y,
                          em.getPlayer().getHitbox().width, em.getPlayer().getHitbox().height);

        shapeRenderer.setColor(Color.RED);
        for (Enemy enemy : em.getEnemies()) {
            shapeRenderer.rect(enemy.getRectangle().x, enemy.getRectangle().y, enemy.getRectangle().width, enemy.getRectangle().height);
        }

        shapeRenderer.setColor(Color.ORANGE);
        for (EnemyBullet bullet : em.getEnemyBullets()) {
            shapeRenderer.rect(bullet.getRectangle().x, bullet.getRectangle().y, bullet.getRectangle().width, bullet.getRectangle().height);
        }

        shapeRenderer.setColor(Color.GREEN);
        for (Powerup powerup : em.getPowerups()) {
            shapeRenderer.rect(powerup.getRectangle().x, powerup.getRectangle().y, powerup.getRectangle().width, powerup.getRectangle().height);
        }

        if (game.isGameOver()) {
            Rectangle highlight = game.getCollisionManager().getCollisionHighlight();
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.rect(highlight.x, highlight.y, highlight.width, highlight.height);
        }

        shapeRenderer.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (game != null) game.dispose();
        if (ui != null) ui.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}
