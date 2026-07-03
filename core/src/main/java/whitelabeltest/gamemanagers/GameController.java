package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;

public class GameController implements Disposable {
    private final AssetManager assets;
    private final AudioManager audio;
    private final EntityManager entities;
    private final CollisionManager collisionManager;
    private final ScrollingBackground background;
    private final SpawnScheduler spawnScheduler;
    private final InputManager input;

    private final ScoreManager scoreManager;
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

        this.scoreManager = new ScoreManager();
        this.spawnScheduler = new SpawnScheduler(worldWidth, worldHeight, assets);

        if (System.getProperty("debug") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp")) {
            this.debugMode = true;
        }

        reset();
    }

    public void update(float delta) {
        scoreManager.update(delta);
        input.update();

        if (input.isDebugToggleJustPressed()) {
            debugMode = !debugMode;
        }

        if (input.isBombJustPressed()) {
            handleBomb();
        }

        if (gameOver) {
            handleGameOverInput();
            return;
        }

        background.update();
        entities.update(delta, input, assets, audio);
        spawnScheduler.update(delta, entities);

        if (!entities.getPlayer().isInvincible()) {
            if (collisionManager.checkPlayerEnemyCollisions(entities.getPlayer(), entities.getEnemies()) ||
                collisionManager.checkPlayerBulletCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
                if (entities.getPlayer().getNumLives() <= 0) gameOver = true;
                else {
                    entities.getPlayer().setNumLives(entities.getPlayer().getNumLives() - 1);
                    entities.getPlayer().startIFrames();
                }
            }
        }

        if (collisionManager.checkGrazeCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
            entities.getPlayer().setGrazePoints(entities.getPlayer().getGrazePoints() + 1);
        }

        collisionManager.checkPlayerPowerupCollisions(entities.getPlayer(), entities.getPowerups());

        collisionManager.checkBulletEnemyCollisions(entities.getBullets(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
    }

    private void handleGameOverInput() {
        if (input.isRestartJustPressed()) {
            reset();
        } else if (input.isQuitJustPressed()) {
            Gdx.app.exit();
        }
    }

    private void handleBomb() {

        // check if bomb is ready
        if (entities.getPlayer().getNumBombs() > 0 && !gameOver) {
            sufferBombDamage(50, entities.getEnemies());
            entities.destroyAllEnemyBullets();
            entities.getPlayer().setNumBombs(entities.getPlayer().getNumBombs() - 1);
        }
    }

    private void sufferBombDamage(int damage, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.takeDamage(damage)) {
                scoreManager.addScore(destroyEnemy(enemies, audio, entities, assets, worldWidth, worldHeight, e));
            }
        }
    }

    public static int destroyEnemy(Array<Enemy> enemies, AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, Enemy enemy) {
        enemies.removeValue(enemy, false);

        float centerX = enemy.getRectangle().x + enemy.getRectangle().width / 2;
        float centerY = enemy.getRectangle().y + enemy.getRectangle().height / 2;

        ExplosionEffect explosion = ObjectPools.explosionPool.obtain();
        explosion.init(assets.explosionTextures, centerX, centerY, enemy.getRectangle().width);
        entityManager.getExplosions().add(explosion);

        String guaranteed = enemy.getGuaranteedPowerup();
        if (guaranteed != null || com.badlogic.gdx.math.MathUtils.random() < 0.20f) {
            spawnPowerup(entityManager.getPowerups(), assets, enemy.getRectangle().x, enemy.getRectangle().y, worldWidth, worldHeight, guaranteed);
        }

        ObjectPools.freeEnemy(enemy);
        audio.playExplosion();
        return 10;
    }

    public static void spawnPowerup(Array<Powerup> powerups, AssetManager assets, float x, float y, float worldWidth, float worldHeight, String forcedType) {
        WeaponPowerup wp = ObjectPools.weaponPowerupPool.obtain();
        Texture tex;
        String weaponId; // Changed to String
        int choice;
        if (forcedType != null) {
            if (forcedType.equals("BasicWeapon")) choice = 0;
            else if (forcedType.equals("WaveBlastWeapon")) choice = 1;
            else if (forcedType.equals("ThunderWhipWeapon")) choice = 2;
            else choice = 3;
        } else {
            choice = com.badlogic.gdx.math.MathUtils.random(0, 3);
        }
        if (choice == 0) { tex = assets.powerup1; weaponId = "BasicWeapon"; }
        else if (choice == 1) { tex = assets.powerup2; weaponId = "WaveBlastWeapon"; }
        else if (choice == 2) { tex = assets.powerup3; weaponId = "ThunderWhipWeapon"; }
        else { tex = assets.powerup4; weaponId = "OrbitWeapon"; }
        wp.initWithType(tex, weaponId, x, y, worldWidth, worldHeight); // Pass weaponId (String)
        powerups.add(wp);
    }

    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        background.draw(batch);
        entities.draw(batch);
    }

    public void reset() {
        scoreManager.reset();
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

    public void setActiveInput(InputType inputType) {
        input.setActiveInput(inputType);
    }

    public int getScore() { return scoreManager.getScore(); }
    public int getHighScore() { return scoreManager.getHighScore(); }
    public ScoreManager getScoreManager() { return scoreManager; }
    public boolean isGameOver() { return gameOver; }
    public boolean isDebugMode() { return debugMode; }
    public EntityManager getEntities() { return entities; }
    public CollisionManager getCollisionManager() { return collisionManager; }
}
