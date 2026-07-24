package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.ExplosionPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.player.Player;
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
    private final PatternPreviewer patternPreviewer = new PatternPreviewer();
    private boolean debugMenuOpen;
    private int debugMenuSelectedIndex;
    private float debugMenuSeekTime;

    private static final float DEBUG_MENU_SCRUB_SPEED = 5f;

    // Debug menu row layout: 0 = seek-time editor, 1-2 = weapon slot pickers, 3-6 = weapon
    // levels, 7 = lives editor, 8 = enemy/pattern editor, 9+ = saved bookmarks. Kept in sync with
    // UIManager.drawDebugMenu's own row constants.
    private static final int ROW_SEEK = 0;
    private static final int ROW_SLOT1 = 1;
    private static final int ROW_SLOT2 = 2;
    private static final int ROW_LEVELS_START = 3;
    private static final String[] WEAPON_LEVEL_IDS = {"BasicWeapon", "WaveBlastWeapon", "Thunderbolt", "OrbitWeapon"};
    private static final int ROW_LIVES = ROW_LEVELS_START + WEAPON_LEVEL_IDS.length;
    private static final int ROW_PATTERN_PREVIEW = ROW_LIVES + 1;
    private static final int ROW_BOOKMARKS_START = ROW_PATTERN_PREVIEW + 1;
    private static final String[] SLOT_WEAPON_OPTIONS = {null, "BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt"};
    private static final int MAX_DEBUG_LIVES = 9;

    private static final float BOMB_COOLDOWN = 15f;
    private static final int BOMB_BONUS_PER_UNUSED = 10000;
    private int levelCompleteBombBonus;
    private int levelCompleteLivesMultiplier;

    private static final float BOMB_SAVE_WINDOW = 0.065f;
    private float hitGraceTimer = -1f;

    // Debug-only FPS monitor (see UIManager.drawDebugFpsMonitor) - lowest/highest track the
    // extremes seen since the last reset() instead of just the instantaneous reading, so a brief
    // stutter or a load-triggered spike stays visible instead of scrolling by unnoticed.
    private int currentFps;
    private int lowestFps = Integer.MAX_VALUE;
    private int highestFps;

    // Debug-only FPS histogram (see UIManager.drawDebugFpsHistogram): one bucket per second over
    // the last FPS_HISTORY_SECONDS, oldest at index 0. Sampled at 1s intervals rather than every
    // frame since Gdx.graphics.getFramesPerSecond() itself only refreshes once a second - sampling
    // faster would just repeat the same reading.
    private static final int FPS_HISTORY_SECONDS = 12;
    private final int[] fpsHistory = new int[FPS_HISTORY_SECONDS];
    private float fpsHistoryTimer = 0f;

    public GameController(float worldWidth, float worldHeight, KeyBindings keyBindings) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.assets = new AssetManager();
        this.audio = new AudioManager();
        this.entities = new EntityManager(assets, worldWidth, worldHeight);
        this.collisionManager = new CollisionManager();
        this.background = new ScrollingBackground(worldWidth, worldHeight);
        this.input = new InputManager(keyBindings);

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
        updateFpsMonitor(delta);

        if (input.isDebugToggleJustPressed()) {
            debugMode = !debugMode;
        }

        if (debugMode && input.isDebugMuteJustPressed()) {
            boolean nowMuted = !audio.isMuted();
            audio.setMuted(nowMuted);
            background.setMuted(nowMuted);
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
            } else {
                patternPreviewer.close(entities);
            }
        }

        if (debugMenuOpen) {
            handleDebugMenuInput(delta);
            patternPreviewer.tick(delta, entities);
            return;
        }

        if (input.isBombJustPressed() && !entities.getPlayer().isDead()) {
            if (tryFireBomb() && hitGraceTimer >= 0f) {
                hitGraceTimer = -1f; // panic bomb: fired in time, so the pending hit doesn't count
            }
        }

        if (hitGraceTimer >= 0f) {
            hitGraceTimer -= delta;
            if (hitGraceTimer <= 0f) {
                hitGraceTimer = -1f;
                applyPlayerHit();
            }
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
            applyLevelCompleteBonus();
            return;
        }

        collisionManager.checkShieldReflections(entities.getPlayer(), entities.getEnemyBullets(), entities.getBullets(), assets);

        if (hitGraceTimer < 0f && !entities.getPlayer().isInvincible() && !entities.getPlayer().isDead()) {
            if (collisionManager.checkPlayerEnemyCollisions(entities.getPlayer(), entities.getEnemies()) ||
                collisionManager.checkPlayerBulletCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
                if (canFireBomb()) {
                    hitGraceTimer = BOMB_SAVE_WINDOW;
                } else {
                    applyPlayerHit();
                }
            }
        }

        if (collisionManager.checkGrazeCollisions(entities.getPlayer(), entities.getEnemyBullets())) {
            entities.getPlayer().setGrazePoints(entities.getPlayer().getGrazePoints() + 0.5f);
            entities.getPlayer().triggerGrazeFlash();
        }

        collisionManager.checkPlayerPowerupCollisions(entities.getPlayer(), entities.getPowerups(), audio);
        collisionManager.checkBulletPowerupCollisions(entities.getBullets(), entities.getPowerups(), assets);
        collisionManager.checkPlayerGemCollisions(entities.getPlayer(), entities.getPointGems(), scoreManager, audio);

        collisionManager.checkBulletEnemyCollisions(entities.getBullets(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
        collisionManager.checkHaloDashCollisions(entities.getPlayer(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
    }

    // libGDX only refreshes getFramesPerSecond() once per second and reports 0 before that first
    // sample, so 0 is ignored rather than collapsing lowestFps immediately on startup.
    private void updateFpsMonitor(float delta) {
        currentFps = Gdx.graphics.getFramesPerSecond();
        if (currentFps <= 0) return;
        if (currentFps < lowestFps) lowestFps = currentFps;
        if (currentFps > highestFps) highestFps = currentFps;

        // A while loop (not if) so a long stall that eats several seconds in one delta still
        // advances the history by that many buckets instead of freezing it mid-stall.
        fpsHistoryTimer += delta;
        while (fpsHistoryTimer >= 1f) {
            fpsHistoryTimer -= 1f;
            System.arraycopy(fpsHistory, 1, fpsHistory, 0, fpsHistory.length - 1);
            fpsHistory[fpsHistory.length - 1] = currentFps;
        }
    }

    private void handleDebugMenuInput(float delta) {
        if (patternPreviewer.isActive()) {
            boolean deleteConsumed = patternPreviewer.handleInput(input);
            if (input.isDebugMenuDeleteJustPressed() && !deleteConsumed) {
                patternPreviewer.close(entities);
            }
            return;
        }

        int bookmarkCount = debugSaveStateManager.getSaveStates().size;
        int totalRows = ROW_BOOKMARKS_START + bookmarkCount;

        if (input.isDebugMenuUpJustPressed()) {
            debugMenuSelectedIndex = (debugMenuSelectedIndex - 1 + totalRows) % totalRows;
        }
        if (input.isDebugMenuDownJustPressed()) {
            debugMenuSelectedIndex = (debugMenuSelectedIndex + 1) % totalRows;
        }

        if (debugMenuSelectedIndex == ROW_SEEK) {
            if (input.isDebugMenuLeftPressed()) debugMenuSeekTime = Math.max(0f, debugMenuSeekTime - DEBUG_MENU_SCRUB_SPEED * delta);
            if (input.isDebugMenuRightPressed()) debugMenuSeekTime += DEBUG_MENU_SCRUB_SPEED * delta;
            if (input.isDebugMenuConfirmJustPressed()) seekToTime(debugMenuSeekTime);
            if (input.isDebugMenuNewBookmarkJustPressed()) debugSaveStateManager.addSaveState("Bookmark", debugMenuSeekTime);
        } else if (debugMenuSelectedIndex == ROW_SLOT1 || debugMenuSelectedIndex == ROW_SLOT2) {
            int slot = debugMenuSelectedIndex - ROW_SLOT1;
            if (input.isDebugMenuLeftJustPressed()) cycleSlotWeapon(slot, -1);
            if (input.isDebugMenuRightJustPressed()) cycleSlotWeapon(slot, 1);
        } else if (debugMenuSelectedIndex == ROW_LIVES) {
            Player player = entities.getPlayer();
            if (input.isDebugMenuLeftJustPressed()) {
                player.setNumLives(Math.max(0, player.getNumLives() - 1));
            }
            if (input.isDebugMenuRightJustPressed()) {
                player.setNumLives(Math.min(MAX_DEBUG_LIVES, player.getNumLives() + 1));
            }
        } else if (debugMenuSelectedIndex == ROW_PATTERN_PREVIEW) {
            if (input.isDebugMenuConfirmJustPressed()) {
                patternPreviewer.open(entities, assets, worldWidth, worldHeight);
            }
        } else if (debugMenuSelectedIndex < ROW_BOOKMARKS_START) {
            String weaponId = WEAPON_LEVEL_IDS[debugMenuSelectedIndex - ROW_LEVELS_START];
            Player player = entities.getPlayer();
            if (input.isDebugMenuLeftJustPressed()) player.setWeaponLevel(weaponId, player.getWeaponLevel(weaponId) - 1);
            if (input.isDebugMenuRightJustPressed()) player.setWeaponLevel(weaponId, player.getWeaponLevel(weaponId) + 1);
        } else {
            int bookmarkIndex = debugMenuSelectedIndex - ROW_BOOKMARKS_START;
            if (input.isDebugMenuConfirmJustPressed()) {
                seekToTime(debugSaveStateManager.getSaveStates().get(bookmarkIndex).time);
            }
            if (input.isDebugMenuDeleteJustPressed()) {
                debugSaveStateManager.removeSaveState(bookmarkIndex);
                debugMenuSelectedIndex = Math.max(0, debugMenuSelectedIndex - 1);
            }
        }
    }

    private void cycleSlotWeapon(int slot, int direction) {
        Player player = entities.getPlayer();
        String current = player.getSlotWeaponId(slot);
        int idx = 0;
        for (int i = 0; i < SLOT_WEAPON_OPTIONS.length; i++) {
            if (java.util.Objects.equals(SLOT_WEAPON_OPTIONS[i], current)) { idx = i; break; }
        }
        int next = (idx + direction + SLOT_WEAPON_OPTIONS.length) % SLOT_WEAPON_OPTIONS.length;
        player.setSlotWeapon(slot, SLOT_WEAPON_OPTIONS[next]);
    }

    private void seekToTime(float targetTime) {
        spawnScheduler.seekTo(targetTime);
        entities.clearWorld();
        debugMenuOpen = false;
    }

    /** End-of-level tally: 10000 points per unused bomb, added as a flat bonus, then - only if the
     *  player still has lives in reserve - the whole score (including that bonus) is multiplied by
     *  the number of lives remaining. Stored for UIManager.drawLevelComplete to show the breakdown. */
    private void applyLevelCompleteBonus() {
        Player player = entities.getPlayer();

        levelCompleteBombBonus = player.getNumBombs() * BOMB_BONUS_PER_UNUSED;
        if (levelCompleteBombBonus > 0) scoreManager.addBonus(levelCompleteBombBonus);

        levelCompleteLivesMultiplier = player.getNumLives();
        if (levelCompleteLivesMultiplier > 0) scoreManager.multiplyScore(levelCompleteLivesMultiplier + 1);
    }

    private void handleGameOverInput() {
        if (input.isRestartJustPressed()) {
            reset();
        } else if (input.isQuitJustPressed()) {
            Gdx.app.exit();
        }
    }

    private boolean canFireBomb() {
        return entities.getPlayer().getNumBombs() > 0 && bombCooldownTimer <= 0 && !gameOver;
    }

    private boolean tryFireBomb() {
        if (!canFireBomb()) return false;
        sufferBombDamage(50, entities.getEnemies());
        entities.destroyAllEnemyBullets();
        entities.getPlayer().setNumBombs(entities.getPlayer().getNumBombs() - 1);
        entities.triggerBombEffect();
        audio.playBomb();
        bombCooldownTimer = BOMB_COOLDOWN;
        return true;
    }

    /** Applies a hit's actual consequence (life loss/death, or game over) - called either
     *  immediately on detection (no bomb available to save it) or after the panic-bomb grace
     *  window (see hitGraceTimer) expires unused. */
    private void applyPlayerHit() {
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

        int gemCount = enemy.getMaxHealth() / 10;
        if (gemCount > 0) {
            Animation<TextureRegion> gemAnimation =
                AnimationCache.get(assets.pointGemTexture, 6, 4, 24, 0.05f, Animation.PlayMode.LOOP);
            for (int i = 0; i < gemCount; i++) {
                PointGem gem = ObjectPools.pointGemPool.obtain();
                gem.init(gemAnimation, centerX, centerY, worldWidth, worldHeight);
                entityManager.getPointGems().add(gem);
            }
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
        hitGraceTimer = -1f;
        levelCompleteBombBonus = 0;
        levelCompleteLivesMultiplier = 0;
        audio.stopVictory();
        patternPreviewer.close(entities);
        entities.reset();
        collisionManager.reset();
        background.reset();
        spawnScheduler.reset();
        lowestFps = Integer.MAX_VALUE;
        highestFps = 0;
        java.util.Arrays.fill(fpsHistory, 0);
        fpsHistoryTimer = 0f;
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
    public int getLevelCompleteBombBonus() { return levelCompleteBombBonus; }
    public int getLevelCompleteLivesMultiplier() { return levelCompleteLivesMultiplier; }
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
    public int getCurrentFps() { return currentFps; }
    public int getLowestFps() { return lowestFps == Integer.MAX_VALUE ? currentFps : lowestFps; }
    public int getHighestFps() { return highestFps; }
    public int[] getFpsHistory() { return fpsHistory; }
    public EntityManager getEntities() { return entities; }
    public boolean isPatternPreviewActive() { return patternPreviewer.isActive(); }
    public PatternPreviewer getPatternPreviewer() { return patternPreviewer; }
    public CollisionManager getCollisionManager() { return collisionManager; }
    public boolean isAudioMuted() { return audio.isMuted(); }
}
