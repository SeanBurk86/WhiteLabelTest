package whitelabeltest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.GameController;
import whitelabeltest.gamemanagers.InputType;
import whitelabeltest.gamemanagers.UIManager;
import whitelabeltest.player.powerups.Powerup;

public class Main extends ApplicationAdapter {
    private enum AppState { START, PLAYING }

    private AppState state = AppState.START;
    private StartScreen startScreen;
    private UIManager ui;
    private GameController game;
    private Sound startScreenConfirmSound;

    private SpriteBatch spriteBatch;
    private ShapeRenderer shapeRenderer;
    private ExtendViewport viewport;

    private final float PLAY_AREA_WIDTH = 9f;
    private final float PLAY_AREA_HEIGHT = 12f;

    private final Vector3 scissorBL = new Vector3();
    private final Vector3 scissorTR = new Vector3();

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        viewport = new ExtendViewport(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        if (state == AppState.START) {
            InputType detected = startScreen.update(delta);
            drawStartScreen();
            if (detected != null) transitionToGame(detected);
        } else {
            game.update(delta);
            drawGame();
        }
    }

    private void transitionToGame(InputType inputType) {
        startScreenConfirmSound = startScreen.getConfirmSound();
        startScreen.dispose();
        startScreen = null;
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        game.setActiveInput(inputType);
        ui = new UIManager(inputType);
        state = AppState.PLAYING;
    }

    private void drawStartScreen() {
        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        startScreen.draw(spriteBatch);
        spriteBatch.end();
    }

    private void drawGame() {
        boolean isGameOver = game.isGameOver();
        ScreenUtils.clear(isGameOver ? new Color(0.2f, 0, 0, 1) : Color.BLACK);

        viewport.apply();

        // leftX is negative when side panels exist (e.g. -6.17 on a 1920x1080 screen)
        float leftX = PLAY_AREA_WIDTH / 2f - viewport.getWorldWidth() / 2f;
        float panelWidth = -leftX;

        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Color panelColor = isGameOver ? new Color(0.15f, 0f, 0f, 1f) : Color.BLACK;
        shapeRenderer.setColor(panelColor);
        shapeRenderer.rect(leftX, 0, panelWidth, PLAY_AREA_HEIGHT);
        shapeRenderer.rect(PLAY_AREA_WIDTH, 0, panelWidth, PLAY_AREA_HEIGHT);
        shapeRenderer.end();

        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();

        int bbWidth = Gdx.graphics.getBackBufferWidth();
        int bbHeight = Gdx.graphics.getBackBufferHeight();
        scissorBL.set(0, 0, 0);
        scissorTR.set(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, 0);
        viewport.getCamera().project(scissorBL, 0, 0, bbWidth, bbHeight);
        viewport.getCamera().project(scissorTR, 0, 0, bbWidth, bbHeight);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor((int) scissorBL.x, (int) scissorBL.y, (int) (scissorTR.x - scissorBL.x), (int) (scissorTR.y - scissorBL.y));

        game.draw(spriteBatch);

        spriteBatch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        ui.drawHUD(spriteBatch, game.getScoreManager(), game.getEntities().getPlayer(), PLAY_AREA_HEIGHT, leftX, game.getBombCooldownTimer(), game.getBombCooldownFraction());

        ui.drawLevelStartAesthetics(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getLevelStartTimer());

        if (isGameOver) {
            ui.drawGameOver(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        } else if (game.isLevelComplete()) {
            ui.drawLevelComplete(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getScore());
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

        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.circle(em.getPlayer().getGrazeHitbox().x, em.getPlayer().getGrazeHitbox().y, em.getPlayer().getGrazeHitbox().radius, 16);
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.circle(em.getPlayer().getHitbox().x, em.getPlayer().getHitbox().y, em.getPlayer().getHitbox().radius, 16);

        shapeRenderer.setColor(Color.RED);
        for (Enemy enemy : em.getEnemies()) {
            shapeRenderer.rect(enemy.getRectangle().x, enemy.getRectangle().y, enemy.getRectangle().width, enemy.getRectangle().height);
        }

        shapeRenderer.setColor(Color.ORANGE);
        for (EnemyBullet bullet : em.getEnemyBullets()) {
            Rectangle r = bullet.getRectangle();
            // getRotation() is 0 for ordinary bullets (a no-op rotation below); bullets like
            // LaserBullet whose hitbox rotates without resizing report their real sweep angle,
            // rotated around the box's bottom-center per EnemyBullet.getRotation()'s contract.
            shapeRenderer.rect(r.x, r.y, r.width / 2f, 0f, r.width, r.height, 1f, 1f, bullet.getRotation());
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
        viewport.update(width, height, false);
        viewport.getCamera().position.set(PLAY_AREA_WIDTH / 2f, PLAY_AREA_HEIGHT / 2f, 0);
        viewport.getCamera().update();
    }

    @Override
    public void dispose() {
        if (startScreen != null) startScreen.dispose();
        if (startScreenConfirmSound != null) startScreenConfirmSound.dispose();
        if (game != null) game.dispose();
        if (ui != null) ui.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}
