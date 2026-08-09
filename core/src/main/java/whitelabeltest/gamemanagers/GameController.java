package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.ExplosionPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.player.Player;
import whitelabeltest.player.WeaponLoadout;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;

public class GameController implements Disposable {
    private final AssetManager assets;
    private final AudioManager audio;
    private final EntityManager entities;
    private final CollisionManager collisionManager;
    private ScrollingBackground background;
    private SpawnScheduler spawnScheduler;
    private final InputManager input;
    private final AudioSettings audioSettings;
    // Which named ordering of stages (see StageSequenceDefinition/AssetManager.getStageSequence())
    // this run is playing through - swapping this is how alternate modes (tutorial, practice, a
    // boss-rush, etc.) reuse the same stage pool in a different order/subset without any other
    // GameController change.
    public static final String DEFAULT_STAGE_SEQUENCE_ID = "campaign";
    private final String stageSequenceId;
    // The resolved list of stage ids for stageSequenceId, fixed for the lifetime of this
    // GameController (re-resolved on every reset() in case the underlying JSON changed, e.g. via
    // the debug enemy/pattern editor's live-reload path).
    private Array<String> stageSequence;
    // Explicit index into stageSequence (not raw position in AssetManager's stage pool) of the
    // currently-loaded stage - see loadStage()/advanceToNextStage().
    private int stageIndex;
    private int totalEnemiesAcrossRun;
    private final WeaponLoadout loadout;

    private final ScoreManager scoreManager;
    private boolean gameOver;
    private float gameOverTimer;
    private boolean levelComplete;
    private boolean bossVideoTriggered;
    private boolean musicFadeTriggered;
    private static final float LEVEL_COMPLETE_DELAY = 3f;
    private float levelCompleteDelayTimer = -1f;
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

    private int levelCompleteBombBonus;
    private int levelCompleteLivesMultiplier;

    private float bossDefeatedScheduleTime = -1f;
    private float levelCompleteBossFightSeconds = -1f;
    private int levelCompleteTimeBonus;

    private LevelRank levelCompleteRank = LevelRank.D;

    private static final float BOMB_SAVE_WINDOW = 0.065f;
    private float hitGraceTimer = -1f;

    private int currentFps;
    private int lowestFps = Integer.MAX_VALUE;
    private int highestFps;

    private static final int FPS_HISTORY_SECONDS = 12;
    private final int[] fpsHistory = new int[FPS_HISTORY_SECONDS];
    private float fpsHistoryTimer = 0f;

    public GameController(float worldWidth, float worldHeight, KeyBindings keyBindings, AudioSettings audioSettings, WeaponLoadout loadout) {
        this(worldWidth, worldHeight, keyBindings, audioSettings, loadout, DEFAULT_STAGE_SEQUENCE_ID);
    }

    /** @param stageSequenceId id of the StageSequenceDefinition (assets/data/stage_sequences.json)
     *  this run plays through - e.g. a future tutorial/practice mode would pass its own sequence id
     *  here instead of DEFAULT_STAGE_SEQUENCE_ID. */
    public GameController(float worldWidth, float worldHeight, KeyBindings keyBindings, AudioSettings audioSettings, WeaponLoadout loadout, String stageSequenceId) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.loadout = loadout;
        this.audioSettings = audioSettings;
        this.stageSequenceId = stageSequenceId;
        this.assets = new AssetManager();
        this.audio = new AudioManager(audioSettings);
        this.entities = new EntityManager(assets, worldWidth, worldHeight);
        this.collisionManager = new CollisionManager();
        this.input = new InputManager(keyBindings);

        this.scoreManager = new ScoreManager(assets.getGameBalance().defaultChainWindow);
        this.debugSaveStateManager = new DebugSaveStateManager();

        if (System.getProperty("debug") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp")) {
            this.debugMode = true;
        }

        reset();
    }

    public void update(float delta) {
        scoreManager.update(delta);
        audio.update(delta);
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

        if (gameOver) {
            gameOverTimer += delta;
            handleGameOverInput();
            return;
        }
        if (levelComplete) {
            handleLevelCompleteInput();
            return;
        }

        background.update();
        entities.update(delta, input, assets, audio);
        spawnScheduler.update(delta, entities, audio);

        if (!bossVideoTriggered && spawnScheduler.isBackgroundVideoTriggered()) {
            bossVideoTriggered = true;
            background.triggerBossVideo();
        }

        if (!musicFadeTriggered && spawnScheduler.isMusicFadeOutTriggered()) {
            musicFadeTriggered = true;
            audio.fadeOutStageMusic();
        }

        if (levelCompleteDelayTimer < 0f && entities.consumeBossKilled()) {
            levelCompleteDelayTimer = LEVEL_COMPLETE_DELAY;
            bossDefeatedScheduleTime = spawnScheduler.getTotalTime();
        }

        if (levelCompleteDelayTimer >= 0f) {
            levelCompleteDelayTimer -= delta;
            if (levelCompleteDelayTimer <= 0f) {
                levelComplete = true;
                background.stop();
                audio.stopStageMusic();
                audio.playVictory();
                applyLevelCompleteBonus();
            }
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
        collisionManager.checkPlayerGemCollisions(entities.getPlayer(), entities.getPointGems(), scoreManager, audio, assets);

        collisionManager.checkBulletEnemyCollisions(entities.getBullets(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
        collisionManager.checkHaloDashCollisions(entities.getPlayer(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
        collisionManager.checkThunderboltDetonation(entities.getPlayer(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
    }

    private void updateFpsMonitor(float delta) {
        currentFps = Gdx.graphics.getFramesPerSecond();
        if (currentFps <= 0) return;
        if (currentFps < lowestFps) lowestFps = currentFps;
        if (currentFps > highestFps) highestFps = currentFps;

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
        background.seekTo(targetTime);
        debugMenuOpen = false;
    }

    private void applyLevelCompleteBonus() {
        Player player = entities.getPlayer();
        GameBalance balance = assets.getGameBalance();

        levelCompleteBombBonus = player.getNumBombs() * balance.bombBonusPerUnusedBomb;
        if (levelCompleteBombBonus > 0) scoreManager.addBonus(levelCompleteBombBonus);

        float bossSpawnTime = spawnScheduler.getBossSpawnTime();
        if (bossDefeatedScheduleTime >= 0f && bossSpawnTime >= 0f) {
            levelCompleteBossFightSeconds = Math.max(0f, bossDefeatedScheduleTime - bossSpawnTime);
            levelCompleteTimeBonus = Math.max(0,
                Math.round((balance.bossTimeBonusParSeconds - levelCompleteBossFightSeconds) * balance.bossTimeBonusPerSecond));
            if (levelCompleteTimeBonus > 0) scoreManager.addBonus(levelCompleteTimeBonus);
        } else {
            levelCompleteBossFightSeconds = -1f;
            levelCompleteTimeBonus = 0;
        }

        levelCompleteLivesMultiplier = player.getNumLives();
        if (levelCompleteLivesMultiplier > 0) scoreManager.multiplyScore(levelCompleteLivesMultiplier + 1);

        levelCompleteRank = computeRank(player);
    }

    private LevelRank computeRank(Player player) {
        GameBalance balance = assets.getGameBalance();
        int totalEnemies = totalEnemiesAcrossRun;
        float killFraction = totalEnemies > 0 ? scoreManager.getEnemiesDestroyed() / (float) totalEnemies : 1f;
        float chainFraction = MathUtils.clamp(scoreManager.getMaxChainCount() / balance.chainRankTarget, 0f, 1f);
        float bossFraction = levelCompleteBossFightSeconds >= 0f
            ? MathUtils.clamp((balance.bossTimeBonusParSeconds - levelCompleteBossFightSeconds) / balance.bossTimeBonusParSeconds, 0f, 1f)
            : 1f;
        float bombFraction = player.getMaxBombs() > 0 ? player.getNumBombs() / (float) player.getMaxBombs() : 1f;
        float livesFraction = MathUtils.clamp(player.getNumLives() / (float) player.getStartingLives(), 0f, 1f);

        float overall = (killFraction + chainFraction + bossFraction + bombFraction + livesFraction) / 5f;

        GameBalance.RankThresholds thresholds = balance.rankThresholds;
        if (overall >= thresholds.s) return LevelRank.S;
        if (overall >= thresholds.a) return LevelRank.A;
        if (overall >= thresholds.b) return LevelRank.B;
        if (overall >= thresholds.c) return LevelRank.C;
        return LevelRank.D;
    }

    private void handleGameOverInput() {
        if (input.isRestartJustPressed()) {
            reset();
        } else if (input.isQuitJustPressed()) {
            Gdx.app.exit();
        }
    }

    private void handleLevelCompleteInput() {
        if (input.isRestartJustPressed()) {
            if (hasNextStage()) advanceToNextStage(); else reset();
        } else if (input.isQuitJustPressed()) {
            Gdx.app.exit();
        }
    }

    private void loadStage(int index) {
        StageDefinition stageDef = assets.getStageDefinition(stageSequence.get(index));
        if (background != null) background.dispose();
        background = new ScrollingBackground(worldWidth, worldHeight, audioSettings, assets, stageDef.backgroundLayers, stageDef.bossVideo);
        background.setMuted(audio.isMuted());
        spawnScheduler = new SpawnScheduler(worldWidth, worldHeight, assets, stageDef.spawnSchedule);
        audio.loadStageMusic(stageDef.music);
        totalEnemiesAcrossRun += spawnScheduler.getSchedule().size;
        bossVideoTriggered = false;
        musicFadeTriggered = false;
        stageIndex = index;
    }

    private void advanceToNextStage() {
        loadStage(stageIndex + 1);
        entities.clearWorld();
        levelComplete = false;
        levelCompleteDelayTimer = -1f;
        bossDefeatedScheduleTime = -1f;
        audio.stopVictory();
        audio.playStageMusic();
    }

    public boolean hasNextStage() { return stageIndex + 1 < stageSequence.size; }
    public int getStageNumber() { return stageIndex + 1; }

    private boolean canFireBomb() {
        return entities.getPlayer().getNumBombs() > 0 && bombCooldownTimer <= 0 && !gameOver;
    }

    private boolean tryFireBomb() {
        if (!canFireBomb()) return false;
        sufferBombDamage(assets.getGameBalance().bombDamage, entities.getEnemies());
        entities.destroyAllEnemyBullets(assets);
        entities.getPlayer().setNumBombs(entities.getPlayer().getNumBombs() - 1);
        entities.triggerBombEffect();
        audio.playBomb();
        bombCooldownTimer = assets.getGameBalance().bombCooldown;
        return true;
    }

    private void applyPlayerHit() {
        scoreManager.breakChain();
        Player player = entities.getPlayer();
        if (player.getNumLives() <= 0) {
            gameOver = true;
            gameOverTimer = 0f;
            background.stop();
            audio.stopStageMusic();
            audio.playGameOver();
        } else {
            player.setNumLives(player.getNumLives() - 1);
            float restoreX = player.getX();
            float restoreY = player.getY();
            player.startDeath();
            audio.playPlayerDeath();
            entities.destroyAllPlayerBullets();
            if (player.getDeathRestoreLevel() > 1) {
                spawnRestorePowerup(entities.getPowerups(), assets, player, restoreX, restoreY, worldWidth, worldHeight);
            }
        }
    }

    private void sufferBombDamage(int damage, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.takeDamage(damage)) {
                scoreManager.addScore(destroyEnemy(audio, entities, assets, worldWidth, worldHeight, e, scoreManager));
            }
        }
    }

    public static int destroyEnemy(AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, Enemy enemy, ScoreManager scoreManager) {
        scoreManager.registerEnemyDestroyed();
        int scoreValue = enemy.getScore();

        if (enemy.cancelsBulletsOnDeath()) entityManager.destroyEnemyBullets(enemy, assets);

        float centerX = enemy.getRectangle().x + enemy.getRectangle().width / 2;
        float centerY = enemy.getRectangle().y + enemy.getRectangle().height / 2;

        ExplosionPatternDef explosionPattern = PatternRegistry.getExplosion(enemy.getExplosionPattern());
        ExplosionEffect explosion = ObjectPools.explosionPool.obtain();
        explosion.init(explosionPattern, centerX, centerY, enemy.getRectangle().width);
        entityManager.getExplosions().add(explosion);

        Integer guaranteedTier = enemy.getGuaranteedPowerup();
        if (guaranteedTier != null) {
            spawnPowerup(entityManager.getPowerups(), assets, enemy.getRectangle().x, enemy.getRectangle().y, worldWidth, worldHeight, guaranteedTier);
        }

        int gemCount = enemy.getMaxHealth() / assets.getGameBalance().gemsPerEnemyHealth;
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

        for (Enemy other : entityManager.getEnemies()) {
            if (other != enemy && other.isBoss()) other.advanceFiringPattern();
        }

        return scoreValue;
    }

    private static final int POWERUP_TIER_COUNT = 3;

    private static Texture powerupTextureForTier(AssetManager assets, int tier) {
        int index = com.badlogic.gdx.math.MathUtils.clamp(tier, 1, POWERUP_TIER_COUNT) - 1;
        return assets.powerupTierTextures[index];
    }

    public static void spawnPowerup(Array<Powerup> powerups, AssetManager assets, float x, float y, float worldWidth, float worldHeight, Integer forcedTier) {
        WeaponPowerup wp = ObjectPools.weaponPowerupPool.obtain();
        int tier = forcedTier != null ? com.badlogic.gdx.math.MathUtils.clamp(forcedTier, 1, POWERUP_TIER_COUNT)
            : com.badlogic.gdx.math.MathUtils.random(1, POWERUP_TIER_COUNT);
        wp.initWithAmount(powerupTextureForTier(assets, tier), tier, x, y, worldWidth, worldHeight);
        powerups.add(wp);
    }

    public static void spawnRestorePowerup(Array<Powerup> powerups, AssetManager assets, Player player, float x, float y, float worldWidth, float worldHeight) {
        WeaponPowerup wp = ObjectPools.weaponPowerupPool.obtain();
        int amount = player.getDeathRestoreLevel() - 1;
        wp.initAsRestore(powerupTextureForTier(assets, amount), amount, x, y, worldWidth, worldHeight);
        powerups.add(wp);
    }

    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        background.draw(batch);
        entities.draw(batch);
    }

    public void reset() {
        scoreManager.reset();
        gameOver = false;
        gameOverTimer = 0f;
        levelComplete = false;
        bossVideoTriggered = false;
        musicFadeTriggered = false;
        levelCompleteDelayTimer = -1f;
        levelStartTimer = 0f;
        bombCooldownTimer = 0f;
        hitGraceTimer = -1f;
        levelCompleteBombBonus = 0;
        levelCompleteLivesMultiplier = 0;
        bossDefeatedScheduleTime = -1f;
        levelCompleteBossFightSeconds = -1f;
        levelCompleteTimeBonus = 0;
        levelCompleteRank = LevelRank.D;
        totalEnemiesAcrossRun = 0;
        stageSequence = assets.getStageSequence(stageSequenceId).stageIds;
        loadStage(0);
        audio.stopVictory();
        audio.playStageMusic();
        patternPreviewer.close(entities);
        entities.reset(loadout);
        collisionManager.reset();
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
    public float getGameOverTimer() { return gameOverTimer; }
    public boolean isLevelComplete() { return levelComplete; }
    public int getLevelCompleteBombBonus() { return levelCompleteBombBonus; }
    public int getLevelCompleteLivesMultiplier() { return levelCompleteLivesMultiplier; }
    public int getEnemiesDestroyed() { return scoreManager.getEnemiesDestroyed(); }
    public int getTotalEnemyCount() { return totalEnemiesAcrossRun; }
    public int getMaxChainCount() { return scoreManager.getMaxChainCount(); }
    public LevelRank getLevelCompleteRank() { return levelCompleteRank; }
    public int getLevelCompleteTimeBonus() { return levelCompleteTimeBonus; }
    public float getLevelCompleteBossFightSeconds() { return levelCompleteBossFightSeconds; }
    public boolean isDebugMode() { return debugMode; }
    public boolean isDebugMenuOpen() { return debugMenuOpen; }
    public int getDebugMenuSelectedIndex() { return debugMenuSelectedIndex; }
    public float getDebugMenuSeekTime() { return debugMenuSeekTime; }
    public float getSpawnScheduleTotalTime() { return spawnScheduler.getTotalTime(); }
    public Array<DebugSaveState> getDebugSaveStates() { return debugSaveStateManager.getSaveStates(); }
    public float getLevelStartTimer() { return levelStartTimer; }
    public Array<TextCue> getTextCues() { return spawnScheduler.getTextCues(); }
    public float getBombCooldownTimer() { return Math.max(bombCooldownTimer, 0f); }
    public float getBombCooldownFraction() { return Math.max(bombCooldownTimer, 0f) / assets.getGameBalance().bombCooldown; }
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
