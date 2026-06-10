package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;

public class GameController implements Disposable {
    private final AssetManager assets;
    private final AudioManager audio;
    private final EntityManager entities;
    private final CollisionManager collisionManager;
    private final ScrollingBackground background;
    private final SpawnScheduler spawnScheduler;
    private final InputManager input;

    private int score;
    private boolean gameOver;
    private boolean debugMode;
    private final float worldWidth, worldHeight;

    public GameController(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = new AssetManager();
        this.audio = new AudioManager();
        this.entities = new EntityManager(assets, worldWidth, worldHeight);
        this.collisionManager = new CollisionManager();
        this.background = new ScrollingBackground(worldWidth, worldHeight);
        this.input = new InputManager();

        this.spawnScheduler = new SpawnScheduler(worldWidth, worldHeight, assets);

        //this.audio.playMusic();

        if (System.getProperty("debug") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp")) {
            this.debugMode = true;
        }

        reset();
    }

    public void update(float delta) {
        input.update();

        if (input.isDebugToggleJustPressed()) {
            debugMode = !debugMode;
        }

        if (gameOver) {
            handleGameOverInput();
            return;
        }

        background.update();
        entities.update(delta, input, assets, audio);
        spawnScheduler.update(delta, entities);

        if (collisionManager.checkPlayerEnemyCollisions(entities.getPlayer(), entities.getEnemies()) ||
            collisionManager.checkPlayerBulletCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
            gameOver = true;
        }

        collisionManager.checkPlayerPowerupCollisions(entities.getPlayer(), entities.getPowerups());

        score += collisionManager.checkBulletEnemyCollisions(entities.getBullets(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight);
    }

    private void handleGameOverInput() {
        if (input.isRestartJustPressed()) {
            reset();
        } else if (input.isQuitJustPressed()) {
            Gdx.app.exit();
        }
    }

    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        background.draw(batch);
        entities.draw(batch);
    }

    public void reset() {
        score = 0;
        gameOver = false;
        entities.reset();
        collisionManager.reset();
        background.reset();
        spawnScheduler.reset();
    }

    @Override
    public void dispose() {
        assets.dispose();
        audio.dispose();
        background.dispose();
    }

    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }
    public boolean isDebugMode() { return debugMode; }
    public EntityManager getEntities() { return entities; }
    public CollisionManager getCollisionManager() { return collisionManager; }
}
