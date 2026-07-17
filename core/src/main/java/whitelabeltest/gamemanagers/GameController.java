package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.ExplosionPatternDef;
import whitelabeltest.enemy.PatternRegistry;
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
    private boolean levelComplete;
    private boolean debugMode;
    private float levelStartTimer;
    private float bombCooldownTimer;
    private final float worldWidth, worldHeight;

    private final DebugSaveStateManager debugSaveStateManager;
    private boolean debugMenuOpen;
    private int debugMenuSelectedIndex;
    private float debugMenuSeekTime;

    private static final float DEBUG_MENU_SCRUB_SPEED = 5f;

    private static final float BOMB_COOLDOWN = 15f;

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
        this.debugSaveStateManager = new DebugSaveStateManager();

        if (System.getProperty("debug") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp")) {
            this.debugMode = true;
        }

        reset();
    }

    public void update(float delta) {
        scoreManager.update(delta);
        levelStartTimer += delta;
        if (bombCooldownTimer > 0) {
            bombCooldownTimer -= delta;
        }
        input.update();

        if (input.isDebugToggleJustPressed()) {
            debugMode = !debugMode;
        }

        if (debugMode && input.isDebugRestartJustPressed()) {
            reset();
            return;
        }

        if (debugMode && input.isDebugMenuToggleJustPressed()) {
            debugMenuOpen = !debugMenuOpen;
            if (debugMenuOpen) {
                debugMenuSeekTime = spawnScheduler.getTotalTime();
                debugMenuSelectedIndex = 0;
            }
        }

        if (debugMenuOpen) {
            handleDebugMenuInput(delta);
            return;
        }

        if (input.isBombJustPressed() && !entities.getPlayer().isDead()) {
            handleBomb();
        }

        if (gameOver || levelComplete) {
            handleGameOverInput();
            return;
        }

        background.update();
        entities.update(delta, input, assets, audio);
        spawnScheduler.update(delta, entities);

        if (entities.consumeBossKilled()) {
            levelComplete = true;
            background.stop();
            audio.playVictory();
            return;
        }

        collisionManager.checkShieldReflections(entities.getPlayer(), entities.getEnemyBullets(), entities.getBullets(), assets);

        if (!entities.getPlayer().isInvincible() && !entities.getPlayer().isDead()) {
            if (collisionManager.checkPlayerEnemyCollisions(entities.getPlayer(), entities.getEnemies()) ||
                collisionManager.checkPlayerBulletCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
                if (entities.getPlayer().getNumLives() <= 0) {
                    gameOver = true;
                    background.stop();
                    audio.playGameOver();
                } else {
                    entities.getPlayer().setNumLives(entities.getPlayer().getNumLives() - 1);
                    entities.getPlayer().startDeath();
                    audio.playPlayerDeath();
                    entities.destroyAllPlayerBullets();
                }
            }
        }

        if (collisionManager.checkGrazeCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
            entities.getPlayer().setGrazePoints(entities.getPlayer().getGrazePoints() + 0.25f);
            entities.getPlayer().triggerGrazeFlash();
        }

        collisionManager.checkPlayerPowerupCollisions(entities.getPlayer(), entities.getPowerups(), audio);
        collisionManager.checkBulletPowerupCollisions(entities.getBullets(), entities.getPowerups(), assets);

        collisionManager.checkBulletEnemyCollisions(entities.getBullets(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
    }

    private void handleDebugMenuInput(float delta) {
        int bookmarkCount = debugSaveStateManager.getSaveStates().size;
        int totalRows = 1 + bookmarkCount; // row 0 = seek-time editor, rows 1..N = bookmarks

        if (input.isDebugMenuUpJustPressed()) {
            debugMenuSelectedIndex = (debugMenuSelectedIndex - 1 + totalRows) % totalRows;
        }
        if (input.isDebugMenuDownJustPressed()) {
            debugMenuSelectedIndex = (debugMenuSelectedIndex + 1) % totalRows;
        }

        if (debugMenuSelectedIndex == 0) {
            if (input.isDebugMenuLeftPressed()) debugMenuSeekTime = Math.max(0f, debugMenuSeekTime - DEBUG_MENU_SCRUB_SPEED * delta);
            if (input.isDebugMenuRightPressed()) debugMenuSeekTime += DEBUG_MENU_SCRUB_SPEED * delta;
            if (input.isDebugMenuConfirmJustPressed()) seekToTime(debugMenuSeekTime);
            if (input.isDebugMenuNewBookmarkJustPressed()) debugSaveStateManager.addSaveState("Bookmark", debugMenuSeekTime);
        } else {
            int bookmarkIndex = debugMenuSelectedIndex - 1;
            if (input.isDebugMenuConfirmJustPressed()) {
                seekToTime(debugSaveStateManager.getSaveStates().get(bookmarkIndex).time);
            }
            if (input.isDebugMenuDeleteJustPressed()) {
                debugSaveStateManager.removeSaveState(bookmarkIndex);
                debugMenuSelectedIndex = Math.max(0, debugMenuSelectedIndex - 1);
            }
        }
    }

    private void seekToTime(float targetTime) {
        spawnScheduler.seekTo(targetTime);
        entities.clearWorld();
        debugMenuOpen = false;
    }

    private void handleGameOverInput() {
        if (input.isRestartJustPressed()) {
            reset();
        } else if (input.isQuitJustPressed()) {
            Gdx.app.exit();
        }
    }

    private void handleBomb() {
        if (entities.getPlayer().getNumBombs() > 0 && bombCooldownTimer <= 0 && !gameOver) {
            sufferBombDamage(50, entities.getEnemies());
            entities.destroyAllEnemyBullets();
            entities.getPlayer().setNumBombs(entities.getPlayer().getNumBombs() - 1);
            entities.triggerBombEffect();
            audio.playBomb();
            bombCooldownTimer = BOMB_COOLDOWN;
        }
    }

    private void sufferBombDamage(int damage, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.takeDamage(damage)) {
                scoreManager.addScore(destroyEnemy(audio, entities, assets, worldWidth, worldHeight, e));
            }
        }
    }

    public static int destroyEnemy(AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, Enemy enemy) {
        int scoreValue = enemy.getScore();

        float centerX = enemy.getRectangle().x + enemy.getRectangle().width / 2;
        float centerY = enemy.getRectangle().y + enemy.getRectangle().height / 2;

        ExplosionPatternDef explosionPattern = PatternRegistry.getExplosion(enemy.getExplosionPattern());
        ExplosionEffect explosion = ObjectPools.explosionPool.obtain();
        explosion.init(explosionPattern, centerX, centerY, enemy.getRectangle().width);
        entityManager.getExplosions().add(explosion);

        String guaranteed = enemy.getGuaranteedPowerup();
        if (guaranteed != null) {
            spawnPowerup(entityManager.getPowerups(), assets, enemy.getRectangle().x, enemy.getRectangle().y, worldWidth, worldHeight, guaranteed);
        }

        audio.playExplosion();
        return scoreValue;
    }

    private static final String[] POWERUP_WEAPON_IDS = {"BasicWeapon", "WaveBlastWeapon", "Thunderbolt", "OrbitWeapon"};

    private static int powerupChoiceForWeaponId(String weaponId) {
        for (int i = 0; i < POWERUP_WEAPON_IDS.length; i++) {
            if (POWERUP_WEAPON_IDS[i].equals(weaponId)) return i;
        }
        return POWERUP_WEAPON_IDS.length - 1;
    }

    private static Texture powerupTextureForChoice(AssetManager assets, int choice) {
        switch (choice) {
            case 0: return assets.powerup1;
            case 1: return assets.powerup2;
            case 2: return assets.powerup3;
            default: return assets.powerup4;
        }
    }

    public static void spawnPowerup(Array<Powerup> powerups, AssetManager assets, float x, float y, float worldWidth, float worldHeight, String forcedType) {
        WeaponPowerup wp = ObjectPools.weaponPowerupPool.obtain();
        int choice = forcedType != null ? powerupChoiceForWeaponId(forcedType) : com.badlogic.gdx.math.MathUtils.random(0, POWERUP_WEAPON_IDS.length - 1);
        wp.initWithType(powerupTextureForChoice(assets, choice), POWERUP_WEAPON_IDS[choice], x, y, worldWidth, worldHeight);
        powerups.add(wp);
    }

    /** Cycles a shot-but-not-collected powerup to the next weapon type in the pickup, in place. */
    public static void cyclePowerupType(WeaponPowerup wp, AssetManager assets) {
        int next = (powerupChoiceForWeaponId(wp.getWeaponId()) + 1) % POWERUP_WEAPON_IDS.length;
        wp.setType(POWERUP_WEAPON_IDS[next], powerupTextureForChoice(assets, next));
    }

    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        background.draw(batch);
        entities.draw(batch);
    }

    public void reset() {
        scoreManager.reset();
        gameOver = false;
        levelComplete = false;
        levelStartTimer = 0f;
        bombCooldownTimer = 0f;
        audio.stopVictory();
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
    public boolean isLevelComplete() { return levelComplete; }
    public boolean isDebugMode() { return debugMode; }
    public boolean isDebugMenuOpen() { return debugMenuOpen; }
    public int getDebugMenuSelectedIndex() { return debugMenuSelectedIndex; }
    public float getDebugMenuSeekTime() { return debugMenuSeekTime; }
    public float getSpawnScheduleTotalTime() { return spawnScheduler.getTotalTime(); }
    public Array<DebugSaveState> getDebugSaveStates() { return debugSaveStateManager.getSaveStates(); }
    public float getLevelStartTimer() { return levelStartTimer; }
    public Array<TextCue> getTextCues() { return spawnScheduler.getTextCues(); }
    public float getBombCooldownTimer() { return Math.max(bombCooldownTimer, 0f); }
    public float getBombCooldownFraction() { return Math.max(bombCooldownTimer, 0f) / BOMB_COOLDOWN; }
    public EntityManager getEntities() { return entities; }
    public CollisionManager getCollisionManager() { return collisionManager; }
}
