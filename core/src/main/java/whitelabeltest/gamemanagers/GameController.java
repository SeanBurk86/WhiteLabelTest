package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.effects.ExplosionEffect;
import whitelabeltest.gamemanagers.spawning.GameBalance;
import whitelabeltest.gamemanagers.effects.PointGem;
import whitelabeltest.gamemanagers.replay.ReplayFrame;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
import whitelabeltest.gamemanagers.spawning.StageMapDefinition;
import whitelabeltest.gamemanagers.spawning.StageSequenceDefinition;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.replay.DebugSaveState;
import whitelabeltest.gamemanagers.replay.DebugSaveStateManager;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.gamemanagers.input.InputType;
import whitelabeltest.gamemanagers.input.KeyBindings;
import whitelabeltest.gamemanagers.spawning.LevelRank;
import whitelabeltest.gamemanagers.spawning.PatternPreviewer;
import whitelabeltest.gamemanagers.spawning.SpawnScheduleEditor;
import whitelabeltest.gamemanagers.replay.ReplayBrowser;
import whitelabeltest.gamemanagers.replay.ReplayData;
import whitelabeltest.gamemanagers.replay.ReplayPlayer;
import whitelabeltest.gamemanagers.replay.ReplayRecorder;
import whitelabeltest.gamemanagers.replay.ReplayResult;
import whitelabeltest.gamemanagers.background.ScrollingBackground;
import whitelabeltest.gamemanagers.background.Stage2KaleidoscopeShader;
import whitelabeltest.enemy.EnemyDefinitionLoader;
import whitelabeltest.gamemanagers.spawning.SpawnScheduler;
import whitelabeltest.gamemanagers.spawning.StartingLoadoutDefinition;
import whitelabeltest.gamemanagers.trigger.TriggerManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.ExplosionPatternDef;
import whitelabeltest.enemy.PatternRegistry;
import whitelabeltest.enemy.MovementPatternDef;
import whitelabeltest.perf.PerfProbe;
import whitelabeltest.player.Player;
import whitelabeltest.player.WeaponLoadout;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.powerups.WeaponPowerup;

/** The game loop for one run: loads stages from a stage sequence, updates entities, triggers,
 *  collisions, scoring and the background, and handles game over, level complete, stage select,
 *  replays and the debug menu. See the README's "Game loop" section. */
public class GameController implements Disposable {
    private final AssetManager assets;
    private final AudioManager audio;
    private final KeyBindings keyBindings;
    private final EntityManager entities;
    private final CollisionManager collisionManager;
    private ScrollingBackground background;
    // Legacy time-based schedule; null for stages with a trigger file.
    private SpawnScheduler spawnScheduler;
    // The stage's trigger file runner; null for stages without one.
    private TriggerManager triggerManager;
    // Distance by which the kaleidoscope background has faded to color; <= 0 = use the shader's clock.
    private float colorFadeDistance = -1f;
    // Distance units before the boss trigger that the kaleidoscope swaps to the tentacles tunnel.
    private static final float KALEIDOSCOPE_SWITCH_LEAD = 4f;
    // Distance units before the boss trigger that the mandelbulb camera starts diving; sized so the
    // dive has settled inside the bulb shortly before the boss.
    private static final float MANDELBULB_DIVE_LEAD = 10f;
    // Text cues from both the schedule and the triggers, rebuilt every update().
    private final Array<TextCue> combinedTextCues = new Array<>();
    private final InputManager input;
    private final AudioSettings audioSettings;
    // Stage sequence ids from stage_sequences.json.
    public static final String DEFAULT_STAGE_SEQUENCE_ID = "campaign";
    // TUTORIAL skips WeaponSelectScreen and uses the sequence's startingLoadout.
    public static final String TUTORIAL_STAGE_SEQUENCE_ID = "tutorial";
    // Non-final: a replay swaps in the recorded run's sequence.
    private String stageSequenceId;
    // Stage ids for this run, re-resolved on every reset().
    private Array<String> stageSequence;
    // For a map sequence: the map and the nodes played so far (last = current stage). stageSequence
    // then grows one stage per choice. Both null for a fixed-order sequence.
    private StageMap stageMap;
    private Array<StageMap.Node> stageMapPath;
    // Non-null while stage select is open after a clear.
    private StageSelect stageSelect;
    // Index into stageSequence of the loaded stage.
    private int stageIndex;
    private int totalEnemiesAcrossRun;
    // Non-final: a replay swaps in the recorded run's loadout.
    private WeaponLoadout loadout;

    // Replay recording and playback are mutually exclusive.
    private final ReplayBrowser replayBrowser = new ReplayBrowser();
    private ReplayRecorder recorder;
    private ReplayPlayer replayPlayer;
    // Receives each finished run's replay for the leaderboard. A run stops being eligible once
    // debug mode is used, since debug changes (weapon levels, lives) aren't recorded.
    private java.util.function.Consumer<ReplayData> runFinishedListener;
    private boolean runEligibleForLeaderboard;

    // Headless mode simulates a replay with no rendering (see simulateReplay()). Shaders, videos
    // and debug tools are skipped; draw() must not be called.
    private final boolean headless;
    // Headless: set once the initial reset for the replay is done, so the next reset() (the
    // replayed run ending on a confirm press) is recognised as the end of the run.
    private boolean headlessRunStarted;
    private ReplayResult headlessResult;
    private boolean headlessSawSeek;

    // Pre-stage video.
    private final InterstitialPlayer interstitialPlayer = new InterstitialPlayer();

    private final ScoreManager scoreManager;
    private boolean gameOver;
    private float gameOverTimer;
    private boolean levelComplete;
    // Set when QUIT is chosen after game over/level complete; Main then returns to the start screen.
    private boolean quitToMenuRequested;
    private boolean bossVideoTriggered;
    private boolean musicFadeTriggered;
    // The stage's own music, used until a Trigger.music is reached.
    private String stageMusicPath;
    private static final float LEVEL_COMPLETE_DELAY = 3f;
    private float levelCompleteDelayTimer = -1f;
    private boolean debugMode;
    private boolean debugToolsAvailable;
    private float levelStartTimer;
    private float bombCooldownTimer;
    private final float worldWidth, worldHeight;
    private StageDefinition currentStageDef;
    // Ground scroll speed for stages without a spawnScheduler (from StageDefinition or the default).
    private float groundScrollSpeed;

    private final DebugSaveStateManager debugSaveStateManager;
    private final PatternPreviewer patternPreviewer = new PatternPreviewer();
    private final SpawnScheduleEditor spawnScheduleEditor = new SpawnScheduleEditor();
    private boolean debugMenuOpen;
    private int debugMenuSelectedIndex;
    private float debugMenuSeekTime;
    // Index into AssetManager.getStageIds() shown on the debug stage-select row.
    private int debugMenuStageIndex;

    private static final float DEBUG_MENU_SCRUB_SPEED = 5f;

    private static final int ROW_SEEK = 0;
    private static final int ROW_SLOT1 = 1;
    private static final int ROW_SLOT2 = 2;
    private static final int ROW_LEVELS_START = 3;
    private static final String[] WEAPON_LEVEL_IDS = {"BasicWeapon", "WaveBlastWeapon", "Thunderbolt", "OrbitWeapon"};
    private static final int ROW_LIVES = ROW_LEVELS_START + WEAPON_LEVEL_IDS.length;
    private static final int ROW_PATTERN_PREVIEW = ROW_LIVES + 1;
    private static final int ROW_REPLAY_BROWSER = ROW_PATTERN_PREVIEW + 1;
    private static final int ROW_STAGE_SELECT = ROW_REPLAY_BROWSER + 1;
    private static final int ROW_SPAWN_SCHEDULE_EDITOR = ROW_STAGE_SELECT + 1;
    private static final int ROW_BOOKMARKS_START = ROW_SPAWN_SCHEDULE_EDITOR + 1;
    private static final String[] SLOT_WEAPON_OPTIONS = {null, "BasicWeapon", "WaveBlastWeapon", "OrbitWeapon", "Thunderbolt"};
    private static final int MAX_DEBUG_LIVES = 9;

    private int levelCompleteBombBonus;
    private int levelCompleteLivesMultiplier;

    private float bossDefeatedScheduleTime = -1f;
    private float levelCompleteBossFightSeconds = -1f;
    private int levelCompleteTimeBonus;

    private LevelRank levelCompleteRank = LevelRank.D;

    // After a hit, the player has this long to bomb and cancel it ("panic bomb").
    private static final float BOMB_SAVE_WINDOW = 0.0325f;
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

    /** @param stageSequenceId the stage_sequences.json entry this run plays. */
    public GameController(float worldWidth, float worldHeight, KeyBindings keyBindings, AudioSettings audioSettings, WeaponLoadout loadout, String stageSequenceId) {
        this(worldWidth, worldHeight, keyBindings, audioSettings, loadout, stageSequenceId, false);
    }

    private GameController(float worldWidth, float worldHeight, KeyBindings keyBindings, AudioSettings audioSettings,
                           WeaponLoadout loadout, String stageSequenceId, boolean headless) {
        this.headless = headless;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.loadout = loadout;
        this.audioSettings = audioSettings;
        this.stageSequenceId = stageSequenceId;
        this.assets = new AssetManager();
        this.audio = new AudioManager(audioSettings);
        this.entities = new EntityManager(assets, worldWidth, worldHeight);
        this.collisionManager = new CollisionManager();
        this.keyBindings = keyBindings;
        this.input = new InputManager(keyBindings);

        this.scoreManager = new ScoreManager(assets.getGameBalance().defaultChainWindow);
        this.debugSaveStateManager = new DebugSaveStateManager();

        if (!headless && (System.getProperty("debug") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp"))) {
            this.debugToolsAvailable = true;
            this.debugMode = true;
        }

        reset();
    }

    public void update(float delta) {
        if (headlessResult != null) return; // the headless replay has finished
        ReplayFrame frame = null;
        // Like recording, playback skips frames while the debug menu or an interstitial is up, so
        // neither consumes replay frames.
        if (replayPlayer != null && !debugMenuOpen && !interstitialPlayer.isActive()) {
            if (!replayPlayer.hasNext()) {
                if (headless) endHeadlessRun();
                else stopReplay();
                return;
            }
            frame = replayPlayer.next();
            if (!Float.isNaN(frame.seekToTime)) {
                headlessSawSeek = true;
                if (spawnScheduler != null) spawnScheduler.seekTo(frame.seekToTime, audio);
                if (triggerManager != null) triggerManager.seekTo(frame.seekToTime);
                syncStageMusic();
                entities.clearWorld();
                background.seekTo(frame.seekToTime);
                return; // a seek frame takes no simulated time
            }
            delta = frame.delta;
        }

        if (interstitialPlayer.isActive()) {
            // Always live input, even in a replay: skipping is outside the recorded stream.
            input.update(null);
            interstitialPlayer.update(delta);
            if (input.isRestartJustPressed() || input.isShootJustPressed()) interstitialPlayer.skip();
            if (!interstitialPlayer.isActive()) audio.playStageMusic();
            return;
        }

        scoreManager.update(delta);
        audio.update(delta);
        levelStartTimer += delta;
        if (bombCooldownTimer > 0) {
            bombCooldownTimer -= delta;
            if (bombCooldownTimer <= 0 && entities.getPlayer().getNumBombs() > 0 && !gameOver) audio.playBombReady();
        }
        input.update(frame);
        updateFpsMonitor(delta);

        if (debugToolsAvailable && input.isDebugToggleJustPressed()) {
            debugMode = !debugMode;
        }
        if (debugMode) runEligibleForLeaderboard = false;

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
                debugMenuSeekTime = spawnScheduler != null ? spawnScheduler.getTotalTime()
                    : (triggerManager != null ? triggerManager.getCamera().getPosition() : 0f);
                debugMenuSelectedIndex = 0;
                int currentStage = assets.getStageIds().indexOf(stageSequence.get(stageIndex), false);
                debugMenuStageIndex = Math.max(currentStage, 0);
            } else {
                patternPreviewer.close(entities);
                replayBrowser.close();
                spawnScheduleEditor.close();
            }
        }

        if (debugMenuOpen) {
            handleDebugMenuInput(delta);
            patternPreviewer.tick(delta, entities);
            return;
        }

        if (recorder != null && !gameOver) recorder.record(delta, input);

        // Scripted "not taught yet" windows (e.g. in the tutorial), from whichever system runs the stage.
        boolean weaponsDisabled = spawnScheduler != null ? spawnScheduler.isWeaponsDisabled(spawnScheduler.getTotalTime())
            : (triggerManager != null && triggerManager.isWeaponsDisabled(triggerManager.getCamera().getPosition()));
        boolean hyperAttackDisabled = spawnScheduler != null ? spawnScheduler.isHyperAttackDisabled(spawnScheduler.getTotalTime())
            : (triggerManager != null && triggerManager.isHyperAttackDisabled(triggerManager.getCamera().getPosition()));
        boolean bombDisabled = spawnScheduler != null ? spawnScheduler.isBombDisabled(spawnScheduler.getTotalTime())
            : (triggerManager != null && triggerManager.isBombDisabled(triggerManager.getCamera().getPosition()));

        if (!weaponsDisabled && !bombDisabled && input.isBombJustPressed() && !entities.getPlayer().isDead()) {
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
            handleLevelCompleteInput(delta);
            return;
        }

        // Scale visible scrolling by the camera's speed so Trigger.setSpeed (e.g. 0 for a stationary
        // boss fight) actually stops the world, not just trigger arming.
        float cameraSpeedScale = triggerManager != null ? triggerManager.getSpeedScale() : 1f;
        background.setScrollSpeedScale(cameraSpeedScale);
        PerfProbe.begin(PerfProbe.Section.BACKGROUND_UPDATE);
        background.update(delta);
        PerfProbe.end(PerfProbe.Section.BACKGROUND_UPDATE);
        float effectiveGroundScrollSpeed =
            (spawnScheduler != null ? spawnScheduler.getGroundScrollSpeed() : groundScrollSpeed) * cameraSpeedScale;
        PerfProbe.begin(PerfProbe.Section.ENTITY_UPDATE);
        entities.update(delta, input, assets, audio, weaponsDisabled, hyperAttackDisabled, effectiveGroundScrollSpeed, background);
        PerfProbe.end(PerfProbe.Section.ENTITY_UPDATE);
        // Kaleidoscope overlay drift follows the ground scroll (no-op for other backgrounds).
        background.setKaleidoscopeGroundScrollSpeed(effectiveGroundScrollSpeed);
        if (spawnScheduler != null) {
            spawnScheduler.update(delta, entities, audio, input,
                scoreManager.getEnemiesDestroyed(), scoreManager.getGemsCollected(), entities.getPlayer().getGrazePoints());
        }
        if (triggerManager != null) {
            PerfProbe.begin(PerfProbe.Section.TRIGGERS);
            triggerManager.update(delta, entities, audio, input, scoreManager);
            PerfProbe.end(PerfProbe.Section.TRIGGERS);
            background.setKaleidoscopeStageDistance(triggerManager.getCamera().getPosition());
            background.setMandelbulbStageDistance(triggerManager.getCamera().getPosition());
        }
        combinedTextCues.clear();
        if (spawnScheduler != null) combinedTextCues.addAll(spawnScheduler.getTextCues());
        if (triggerManager != null) combinedTextCues.addAll(triggerManager.getTextCues());

        if (!bossVideoTriggered && spawnScheduler != null && spawnScheduler.isBackgroundVideoTriggered()) {
            bossVideoTriggered = true;
            background.triggerBossVideo();
        }

        if (!musicFadeTriggered && spawnScheduler != null && spawnScheduler.isMusicFadeOutTriggered()) {
            musicFadeTriggered = true;
            audio.fadeOutStageMusic();
        }

        if (levelCompleteDelayTimer < 0f && entities.consumeBossKilled()) {
            levelCompleteDelayTimer = LEVEL_COMPLETE_DELAY;
            bossDefeatedScheduleTime = spawnScheduler != null ? spawnScheduler.getTotalTime()
                : (triggerManager != null ? triggerManager.getCamera().getPosition() : 0f);
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

        PerfProbe.begin(PerfProbe.Section.COLLISIONS);
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
        collisionManager.checkEnemyBulletEnemyCollisions(entities.getEnemyBullets(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
        collisionManager.checkHaloDashCollisions(entities.getPlayer(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
        collisionManager.checkThunderboltDetonation(entities.getPlayer(), entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager);
        // Must run after every damage check above.
        collisionManager.resolvePairedEnemyDeaths(entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager, delta);
        PerfProbe.end(PerfProbe.Section.COLLISIONS);
        PerfProbe.counts(entities.getEnemies().size, entities.getEnemyBullets().size, entities.getBullets().size, entities.getPointGems().size,
            entities.getExplosions().size + entities.getHitEffects().size, triggerManager != null ? triggerManager.getCamera().getPosition() : Float.NaN);
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

        if (replayBrowser.isActive()) {
            replayBrowser.handleInput(input);
            ReplayData selected = replayBrowser.consumePendingSelection();
            if (selected != null) {
                replayBrowser.close();
                startReplay(selected);
            } else if (input.isDebugMenuDeleteJustPressed()) {
                replayBrowser.close();
            }
            return;
        }

        if (spawnScheduleEditor.isActive()) {
            boolean deleteConsumed = spawnScheduleEditor.handleInput(input);
            if (input.isDebugMenuDeleteJustPressed() && !deleteConsumed) {
                spawnScheduleEditor.close();
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
        } else if (debugMenuSelectedIndex == ROW_REPLAY_BROWSER) {
            if (input.isDebugMenuConfirmJustPressed()) {
                replayBrowser.open();
            }
        } else if (debugMenuSelectedIndex == ROW_STAGE_SELECT) {
            Array<String> stageIds = assets.getStageIds();
            if (input.isDebugMenuLeftJustPressed()) {
                debugMenuStageIndex = (debugMenuStageIndex - 1 + stageIds.size) % stageIds.size;
            }
            if (input.isDebugMenuRightJustPressed()) {
                debugMenuStageIndex = (debugMenuStageIndex + 1) % stageIds.size;
            }
            if (input.isDebugMenuConfirmJustPressed()) {
                debugLoadStage(stageIds.get(debugMenuStageIndex));
            }
        } else if (debugMenuSelectedIndex == ROW_SPAWN_SCHEDULE_EDITOR) {
            // Only for stages actually running a schedule (edits would do nothing otherwise).
            if (input.isDebugMenuConfirmJustPressed() && spawnScheduler != null) {
                spawnScheduleEditor.open(assets, currentStageDef.spawnSchedule, worldWidth, worldHeight,
                    // Reload the stage after saving so the change takes effect.
                    () -> {
                        spawnScheduleEditor.close();
                        debugLoadStage(stageSequence.get(stageIndex));
                    });
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
        if (spawnScheduler != null) spawnScheduler.seekTo(targetTime, audio);
        if (triggerManager != null) triggerManager.seekTo(targetTime);
        syncStageMusic();
        entities.clearWorld();
        background.seekTo(targetTime);
        debugMenuOpen = false;
        // Record the jump so a replay seeks at the same moment.
        if (recorder != null) recorder.recordSeek(targetTime);
    }

    /** After a seek, restores the music that should be playing at the current distance (seeks skip
     *  music triggers rather than replay them). */
    private void syncStageMusic() {
        if (triggerManager == null || stageMusicPath == null) return;
        String track = triggerManager.musicAt(triggerManager.getCamera().getPosition());
        audio.switchStageMusic(track != null ? track : stageMusicPath);
    }

    /** Preloads every trigger sound and waypoint sound so none stalls a frame on first play. */
    private void preloadStageSounds() {
        if (triggerManager != null) for (String path : triggerManager.getCueSoundPaths()) audio.preloadCueSound(path);
        for (String id : PatternRegistry.getMovementIds()) preloadWaypointSounds(PatternRegistry.getMovement(id));
    }

    private void preloadWaypointSounds(MovementPatternDef def) {
        if (def == null) return;
        if (def.soundName != null) audio.preloadCueSound(def.soundName);
        if (def.patterns != null) for (MovementPatternDef sub : def.patterns) preloadWaypointSounds(sub);
        preloadWaypointSounds(def.pattern);
    }

    private void applyLevelCompleteBonus() {
        Player player = entities.getPlayer();
        GameBalance balance = assets.getGameBalance();

        levelCompleteBombBonus = player.getNumBombs() * balance.bombBonusPerUnusedBomb;
        if (levelCompleteBombBonus > 0) scoreManager.addBonus(levelCompleteBombBonus);

        float bossSpawnTime = spawnScheduler != null ? spawnScheduler.getBossSpawnTime() : -1f;
        if (bossSpawnTime < 0f && triggerManager != null) bossSpawnTime = triggerManager.getBossSpawnDistance();
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
            quitToMenuRequested = true;
        }
    }

    private void handleLevelCompleteInput(float delta) {
        if (stageSelect != null) {
            handleStageSelectInput(delta);
            return;
        }
        if (input.isRestartJustPressed()) {
            if (!hasNextStage()) reset();
            else if (stageMap != null) {
                // With a single way on there's nothing to choose.
                Array<StageMap.Node> choices = stageMap.choicesAfter(stageMapPath.peek());
                if (choices.size > 1) openStageSelect();
                else moveToMapNode(choices.first());
            } else advanceToNextStage();
        } else if (input.isQuitJustPressed()) {
            quitToMenuRequested = true;
        }
    }

    private void openStageSelect() {
        stageSelect = new StageSelect(stageMap, stageMapPath);
    }

    /** Move to pick, confirm to launch. Uses only recorded input, so replays make the same choices. */
    private void handleStageSelectInput(float delta) {
        stageSelect.tick(delta);
        if (input.isMoveUpJustStarted() || input.isMoveLeftJustStarted()) stageSelect.move(-1);
        if (input.isMoveDownJustStarted() || input.isMoveRightJustStarted()) stageSelect.move(1);
        if (input.isRestartJustPressed()) {
            StageMap.Node chosen = stageSelect.getSelected();
            stageSelect = null;
            if (chosen != null) moveToMapNode(chosen);
        } else if (input.isQuitJustPressed()) {
            quitToMenuRequested = true;
        }
    }

    /** Appends `node`'s stage to the sequence and advances to it. */
    private void moveToMapNode(StageMap.Node node) {
        stageMapPath.add(node);
        stageSequence.add(node.stageId);
        advanceToNextStage();
    }

    /** Builds the map and labels each node with its stage's name. An unknown stage id fails here at
     *  run start rather than when first chosen. */
    private StageMap buildStageMap(StageMapDefinition def) {
        StageMap map = new StageMap(def);
        for (StageMap.Node node : map.getNodes()) {
            if (!node.hasStage()) continue;
            StageDefinition stage = assets.getStageDefinition(node.stageId);
            node.label = stage.name != null ? stage.name : node.stageId;
        }
        return map;
    }

    private void loadStage(int index) {
        StageDefinition stageDef = assets.getStageDefinition(stageSequence.get(index));
        currentStageDef = stageDef;
        if (background != null) background.dispose();
        background = new ScrollingBackground(worldWidth, worldHeight, audioSettings, assets, stageDef.backgroundLayers, stageDef.bossVideo, stageDef.backgroundVideo, stageDef.shaderBackground, stageDef.hueCycleBackground, stageDef.playerFeedbackBackground, headless);
        background.setMuted(audio.isMuted());
        // A trigger file replaces the schedule entirely, so the editor and the game always agree.
        if (stageDef.triggerFile != null) {
            spawnScheduler = null;
            triggerManager = new TriggerManager(worldWidth, worldHeight, assets, stageDef.triggerFile, EnemyDefinitionLoader.load(), background);
        } else {
            spawnScheduler = new SpawnScheduler(worldWidth, worldHeight, assets, stageDef.spawnSchedule);
            triggerManager = null;
        }
        background.setKaleidoscopeTransitionTime(stageDef.kaleidoscopeTransitionTime != null ? stageDef.kaleidoscopeTransitionTime
            : (spawnScheduler != null ? spawnScheduler.getKaleidoscopeTransitionTime() : Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME));
        float bossDistance = triggerManager != null ? triggerManager.getBossSpawnDistance() : -1f;
        colorFadeDistance = stageDef.kaleidoscopeColorFadeDistance != null ? stageDef.kaleidoscopeColorFadeDistance : bossDistance;
        float tentacleSwitchDistance = stageDef.kaleidoscopeTransitionDistance != null ? stageDef.kaleidoscopeTransitionDistance
            : (bossDistance > 0f ? Math.max(0f, bossDistance - KALEIDOSCOPE_SWITCH_LEAD) : -1f);
        background.setKaleidoscopeDistances(colorFadeDistance, tentacleSwitchDistance);
        background.setMandelbulbDiveDistance(stageDef.mandelbulbDiveDistance != null ? stageDef.mandelbulbDiveDistance
            : (bossDistance > 0f ? Math.max(0f, bossDistance - MANDELBULB_DIVE_LEAD) : -1f));
        groundScrollSpeed = stageDef.groundScrollSpeed != null ? stageDef.groundScrollSpeed
            : (spawnScheduler != null ? spawnScheduler.getGroundScrollSpeed() : ScrollingBackground.DEFAULT_SCROLL_SPEED);
        // Hue-cycle period = time/distance to the boss video, so one cycle ends as it starts.
        float bossVideoDistance = triggerManager != null ? triggerManager.getBossVideoDistance() : -1f;
        background.setHueCyclePeriod(bossVideoDistance >= 0f ? bossVideoDistance : (spawnScheduler != null ? spawnScheduler.getBackgroundVideoTime() : -1f));
        audio.loadStageMusic(stageDef.music);
        stageMusicPath = stageDef.music;
        preloadStageSounds();
        totalEnemiesAcrossRun += (spawnScheduler != null ? spawnScheduler.getSchedule().size : 0) + (triggerManager != null ? triggerManager.getEnemySpawnCount() : 0);
        combinedTextCues.clear();
        bossVideoTriggered = false;
        musicFadeTriggered = false;
        stageIndex = index;
    }

    private void advanceToNextStage() {
        stageSelect = null;
        loadStage(stageIndex + 1);
        entities.clearWorld();
        entities.getPlayer().resetForNewStage();
        levelComplete = false;
        levelCompleteDelayTimer = -1f;
        bossDefeatedScheduleTime = -1f;
        audio.stopVictory();
        startInterstitial();
    }

    /** Plays a random interstitial video (seeded, so replays pick the same one), or goes straight to
     *  the stage music if none are configured. */
    private void startInterstitial() {
        Array<String> videos = assets.getInterstitialVideos();
        if (videos.size == 0) {
            audio.playStageMusic();
            return;
        }
        String chosen = videos.get(MathUtils.random(videos.size - 1));
        // Headless: the pick above still consumes its random value, but nothing plays. Replays
        // don't consume frames during interstitials, so skipping them changes nothing else.
        if (headless) {
            audio.playStageMusic();
            return;
        }
        interstitialPlayer.play(chosen, audio.isMuted() ? 0f : audioSettings.getEffectiveMusicVolume());
    }

    /** Debug: loads any stage from stages.json as a single-stage sequence (the only way to reach
     *  stages outside every sequence, e.g. "testground"). Drops any in-progress replay recording,
     *  since this can't be replayed. */
    private void debugLoadStage(String stageId) {
        recorder = null;
        stageSequence = new Array<>();
        stageSequence.add(stageId);
        stageMap = null;
        stageMapPath = null;
        stageSelect = null;
        loadStage(0);
        entities.clearWorld();
        gameOver = false;
        gameOverTimer = 0f;
        levelComplete = false;
        levelCompleteDelayTimer = -1f;
        bossDefeatedScheduleTime = -1f;
        audio.stopVictory();
        audio.playStageMusic();
        debugMenuOpen = false;
    }

    /** The editor's Quick Play: loads `stageId`, sets the weapon slots (null = leave as is), and
     *  seeks to startDistance (if > 0).
     *  @param slotALevel,slotBLevel weapon levels; <= 0 keeps the level 1 a fresh equip gets. */
    public void quickStartAtStage(String stageId, float startDistance, String slotAWeaponId, String slotBWeaponId,
                                   int slotALevel, int slotBLevel) {
        debugLoadStage(stageId);
        if (slotAWeaponId != null) entities.getPlayer().setSlotWeapon(0, slotAWeaponId);
        if (slotBWeaponId != null) entities.getPlayer().setSlotWeapon(1, slotBWeaponId);
        if (slotAWeaponId != null && slotALevel > 0) entities.getPlayer().setWeaponLevel(slotAWeaponId, slotALevel);
        if (slotBWeaponId != null && slotBLevel > 0) entities.getPlayer().setWeaponLevel(slotBWeaponId, slotBLevel);
        if (startDistance > 0) seekToTime(startDistance);
    }

    /** On a map, true while the current node connects to a node with a stage; a dead end ends the run. */
    public boolean hasNextStage() {
        if (stageMap != null) return !stageMap.choicesAfter(stageMapPath.peek()).isEmpty();
        return stageIndex + 1 < stageSequence.size;
    }

    /** The current stage's display name (by name, not play order, since map routes vary). */
    public String getStageName() {
        String name = currentStageDef != null ? currentStageDef.name : null;
        return name != null ? name : "STAGE " + (stageIndex + 1);
    }

    /** Applies a sequence's fixed loadout over the freshly reset player. */
    private void applyStartingLoadout(StartingLoadoutDefinition config) {
        Player player = entities.getPlayer();
        if (config.weaponLevels != null) {
            for (ObjectMap.Entry<String, Integer> entry : config.weaponLevels) {
                player.setWeaponLevel(entry.key, entry.value);
            }
        }
        player.setSlotWeapon(0, config.slotAWeaponId);
        player.setSlotWeapon(1, config.slotBWeaponId);
        if (config.maxBombs > 0) player.setMaxBombs(config.maxBombs);
        player.setNumBombs(config.numBombs);
        player.setNumLives(config.numLives);
    }

    /** Starts watching a recorded run (reset() flushes the outgoing recording). */
    public void startReplay(ReplayData data) {
        this.stageSequenceId = data.stageSequenceId;
        try {
            this.loadout = WeaponLoadout.valueOf(data.weaponLoadout);
        } catch (IllegalArgumentException e) {
            Gdx.app.error("GameController", "Unknown weapon loadout in replay: " + data.weaponLoadout, e);
            return;
        }
        this.replayPlayer = new ReplayPlayer(data);
        // Replays are picked from the debug menu; close it so playback isn't paused.
        this.debugMenuOpen = false;
        reset();
    }

    /** Returns to live play with a fresh recording. */
    public void stopReplay() {
        this.replayPlayer = null;
        reset();
    }

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
        // Perf runs (-Dperf.invincible): ignore hits so a desynced replay still plays the whole stage.
        if (PerfProbe.INVINCIBLE) return;
        // Scripted invincibility window: no consequence at all, not even a chain break.
        if (spawnScheduler != null ? spawnScheduler.isPlayerInvincible(spawnScheduler.getTotalTime())
            : (triggerManager != null && triggerManager.isPlayerInvincible(triggerManager.getCamera().getPosition()))) return;

        scoreManager.breakChain();
        Player player = entities.getPlayer();
        boolean inPracticeSection = spawnScheduler != null ? spawnScheduler.isInPracticeSection(spawnScheduler.getTotalTime())
            : (triggerManager != null && triggerManager.isInPracticeSection(triggerManager.getCamera().getPosition()));
        if (inPracticeSection) {
            restartPracticeSection();
            return;
        }
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

    /** A hit inside a practice checkpoint: no life lost; rewinds to the checkpoint start and clears
     *  the world. Deliberately skips player.startDeath(): its death + i-frame time pauses all enemy
     *  fire while the stage keeps spawning, and the stacked waves then fire at once as a gapless wall. */
    private void restartPracticeSection() {
        audio.playPlayerDeath();
        entities.destroyAllPlayerBullets();
        if (spawnScheduler != null) {
            float checkpointStart = spawnScheduler.isInPracticeSection(spawnScheduler.getTotalTime())
                ? spawnScheduler.getPracticeCheckpointStart(spawnScheduler.getTotalTime())
                : spawnScheduler.getTotalTime();
            spawnScheduler.seekTo(checkpointStart, audio);
            if (triggerManager != null) triggerManager.seekTo(checkpointStart);
        } else if (triggerManager != null) {
            // Also swaps in retry-only triggers (see Trigger.firstAttemptOnly/retryOnly).
            triggerManager.seekToPracticeRetry(triggerManager.getCamera().getPosition());
        }
        syncStageMusic();
        entities.clearWorld();
    }

    private void sufferBombDamage(int damage, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            // Paired enemies are resolved later by CollisionManager.resolvePairedEnemyDeaths().
            if (e.takeDamage(damage) && e.getPairId() == null) {
                scoreManager.addScore(destroyEnemy(audio, entities, assets, worldWidth, worldHeight, e, scoreManager));
            }
        }
    }

    public static int destroyEnemy(AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, Enemy enemy, ScoreManager scoreManager) {
        scoreManager.registerEnemyDestroyed(enemy.getDefinitionId());
        if (enemy.getSpawnGroup() != null) scoreManager.registerGroupDestroyed(enemy.getSpawnGroup());
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
            // Closer kills drop bigger, more valuable gems. Distance is to the enemy's nearest edge.
            Player player = entityManager.getPlayer();
            Rectangle bounds = enemy.getRectangle();
            float nearestX = MathUtils.clamp(player.getCenterX(), bounds.x, bounds.x + bounds.width);
            float nearestY = MathUtils.clamp(player.getCenterY(), bounds.y, bounds.y + bounds.height);
            float gemScale = assets.getGameBalance().gemScaleForDistance(
                Vector2.dst(player.getCenterX(), player.getCenterY(), nearestX, nearestY));
            // Capped at maxGemsPerEnemy; the represented counts still add up to gemCount.
            int spawned = Math.min(gemCount, Math.max(1, assets.getGameBalance().maxGemsPerEnemy));
            int each = gemCount / spawned, extra = gemCount % spawned;
            for (int i = 0; i < spawned; i++) {
                PointGem gem = ObjectPools.pointGemPool.obtain();
                gem.init(gemAnimation, centerX, centerY, worldWidth, worldHeight, false, gemScale, each + (i < extra ? 1 : 0));
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

    /** Draws the background, entities and interstitial. With a background layer stack, layer-attached
     *  enemies are drawn between their layers; otherwise (video/shader background) everything draws
     *  in front of the background. */
    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        // Feed this frame's player to the feedback overlay (if enabled) before drawing it. While dead
        // the player sprite is frozen, so pass a null frame: re-feeding it would build a solid
        // "statue" in the trail faster than it decays.
        Player feedbackPlayer = entities.getPlayer();
        TextureRegion feedbackFrame = feedbackPlayer.isDead() ? null : feedbackPlayer.getCurrentFrame();
        TextureRegion feedbackHaloFrame = feedbackPlayer.isDead() ? null : feedbackPlayer.getHaloFrame();
        background.updatePlayer(feedbackFrame, feedbackPlayer.getX(), feedbackPlayer.getY(),
            feedbackPlayer.getWidth(), feedbackPlayer.getHeight());
        background.updateHalo(feedbackHaloFrame, feedbackPlayer.getHaloX(), feedbackPlayer.getHaloY(),
            feedbackPlayer.getHaloWidth(), feedbackPlayer.getHaloHeight());

        int layerCount = background.getLayerCount();
        if (background.isDrawingLayerStack() && layerCount > 0) {
            PerfProbe.begin(PerfProbe.Section.BACKGROUND_DRAW);
            background.beginLayeredDraw(batch);
            for (int i = 0; i < layerCount; i++) {
                background.drawLayer(batch, i);
                entities.drawEnemiesAttachedToLayer(batch, i);
            }
            background.endLayeredDraw(batch);
            PerfProbe.end(PerfProbe.Section.BACKGROUND_DRAW);
            // background.draw() isn't used here, so apply its feedback overlay explicitly.
            PerfProbe.begin(PerfProbe.Section.FEEDBACK_DRAW);
            background.drawPlayerFeedbackOverlay(batch);
            PerfProbe.end(PerfProbe.Section.FEEDBACK_DRAW);
            PerfProbe.begin(PerfProbe.Section.ENTITY_DRAW);
            entities.draw(batch, layerCount);
            PerfProbe.end(PerfProbe.Section.ENTITY_DRAW);
        } else {
            PerfProbe.begin(PerfProbe.Section.BACKGROUND_DRAW);
            background.draw(batch);
            PerfProbe.end(PerfProbe.Section.BACKGROUND_DRAW);
            PerfProbe.begin(PerfProbe.Section.ENTITY_DRAW);
            entities.draw(batch, 0);
            PerfProbe.end(PerfProbe.Section.ENTITY_DRAW);
        }
        interstitialPlayer.draw(batch, worldWidth, worldHeight);
    }

    public void setRunFinishedListener(java.util.function.Consumer<ReplayData> listener) {
        this.runFinishedListener = listener;
    }

    /** Ends the current recording: saves it and hands it to the leaderboard if eligible. */
    private void finishRecording() {
        if (recorder == null) return;
        recorder.setSummary(scoreManager.getScore(), stageIndex + 1, gameOver);
        recorder.saveIfNonTrivial();
        if (runEligibleForLeaderboard && runFinishedListener != null && recorder.isNonTrivial()) {
            runFinishedListener.accept(recorder.getData());
        }
    }

    public void reset() {
        // Headless: a reset after the run started is the replayed run ending (a confirm press after
        // the last stage), which is exactly where the live game took its recording summary.
        if (headless && headlessRunStarted) {
            endHeadlessRun();
            return;
        }
        finishRecording();
        long seed = replayPlayer != null ? replayPlayer.getSeed() : System.nanoTime();
        MathUtils.random.setSeed(seed);
        // Tutorial runs aren't recorded.
        boolean recordingEnabled = replayPlayer == null && !stageSequenceId.equals(TUTORIAL_STAGE_SEQUENCE_ID);
        recorder = recordingEnabled ? new ReplayRecorder(stageSequenceId, loadout, seed) : null;
        runEligibleForLeaderboard = recorder != null && !debugMode && !PerfProbe.INVINCIBLE;

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
        StageSequenceDefinition sequenceDef = assets.getStageSequence(stageSequenceId);
        // Always a copy: map runs append to stageSequence, which must not modify the definition.
        stageSelect = null;
        if (sequenceDef.stageMap != null) {
            stageMap = buildStageMap(sequenceDef.stageMap);
            stageMapPath = new Array<>();
            stageMapPath.add(stageMap.getStart());
            stageSequence = new Array<>();
            stageSequence.add(stageMap.getStart().stageId);
        } else {
            stageMap = null;
            stageMapPath = null;
            stageSequence = new Array<>(sequenceDef.stageIds);
        }
        loadStage(0);
        audio.stopVictory();
        patternPreviewer.close(entities);
        spawnScheduleEditor.close();
        entities.reset(loadout);
        if (sequenceDef.startingLoadout != null) applyStartingLoadout(sequenceDef.startingLoadout);
        collisionManager.reset();
        lowestFps = Integer.MAX_VALUE;
        highestFps = 0;
        java.util.Arrays.fill(fpsHistory, 0);
        fpsHistoryTimer = 0f;
        startInterstitial();
        if (headless && replayPlayer != null) headlessRunStarted = true;
    }

    private void endHeadlessRun() {
        ReplayResult result = new ReplayResult();
        result.score = scoreManager.getScore();
        result.stagesReached = stageIndex + 1;
        result.gameOver = gameOver;
        result.framesPlayed = replayPlayer != null ? replayPlayer.getFramesPlayed() : 0;
        result.totalFrames = replayPlayer != null ? replayPlayer.getData().frames.size : 0;
        result.containsSeek = headlessSawSeek;
        result.enemiesDestroyed = scoreManager.getEnemiesDestroyed();
        result.gemsCollected = scoreManager.getGemsCollected();
        result.maxChain = scoreManager.getMaxChainCount();
        result.livesLeft = entities.getPlayer().getNumLives();
        headlessResult = result;
    }

    /** Re-runs a recorded run with no rendering and returns how it ended, for validating a
     *  submitted score: the result should match the replay's recorded summary.
     *
     *  Requires a running libGDX Application whose Gdx.gl accepts calls (textures are still
     *  loaded, because sprite and hitbox sizes come from their dimensions) and whose working
     *  directory is assets/. The headless module sets this up. Runs synchronously on the calling
     *  thread, which must be the application's thread. */
    public static ReplayResult simulateReplay(ReplayData replay, float worldWidth, float worldHeight) {
        return simulateReplay(replay, worldWidth, worldHeight, null);
    }

    /** @param afterEachFrame optional hook run after every update, e.g. to call draw() and check
     *  that drawing doesn't change the outcome */
    public static ReplayResult simulateReplay(ReplayData replay, float worldWidth, float worldHeight,
                                              java.util.function.Consumer<GameController> afterEachFrame) {
        GameController game = new GameController(worldWidth, worldHeight, new KeyBindings(), new AudioSettings(),
            WeaponLoadout.BASIC_THUNDERBOLT, DEFAULT_STAGE_SEQUENCE_ID, true);
        try {
            game.startReplay(replay);
            if (game.replayPlayer == null) throw new IllegalArgumentException("Replay could not be started (unknown weapon loadout?)");
            // Each update consumes one frame; the delta passed in is ignored during playback.
            while (game.headlessResult == null) {
                game.update(0f);
                if (afterEachFrame != null && game.headlessResult == null) afterEachFrame.accept(game);
            }
            return game.headlessResult;
        } finally {
            game.dispose();
        }
    }

    @Override
    public void dispose() {
        finishRecording();
        recorder = null;
        assets.dispose();
        audio.dispose();
        background.dispose();
        interstitialPlayer.dispose();
    }

    public void setActiveInput(InputType inputType) {
        input.setActiveInput(inputType);
    }

    public int getScore() { return scoreManager.getScore(); }
    public int getHighScore() { return scoreManager.getHighScore(); }
    public ScoreManager getScoreManager() { return scoreManager; }
    public boolean isGameOver() { return gameOver; }
    public float getGameOverTimer() { return gameOverTimer; }
    public boolean isQuitToMenuRequested() { return quitToMenuRequested; }
    public boolean isLevelComplete() { return levelComplete; }
    /** True while the stage-select map is showing (only ever during the STAGE CLEAR screen). */
    public boolean isStageSelectActive() { return stageSelect != null; }
    public StageSelect getStageSelect() { return stageSelect; }
    public int getLevelCompleteBombBonus() { return levelCompleteBombBonus; }
    public int getLevelCompleteLivesMultiplier() { return levelCompleteLivesMultiplier; }
    public int getEnemiesDestroyed() { return scoreManager.getEnemiesDestroyed(); }
    public int getTotalEnemyCount() { return totalEnemiesAcrossRun; }
    public int getMaxChainCount() { return scoreManager.getMaxChainCount(); }
    public LevelRank getLevelCompleteRank() { return levelCompleteRank; }
    public int getLevelCompleteTimeBonus() { return levelCompleteTimeBonus; }
    public float getLevelCompleteBossFightSeconds() { return levelCompleteBossFightSeconds; }
    public boolean isDebugMode() { return debugMode; }
    // Debug overlay; null without a trigger file.
    public Float getTriggerDistance() { return triggerManager != null ? triggerManager.getCamera().getPosition() : null; }
    public String getTriggerActiveGateInfo() { return triggerManager != null ? triggerManager.describeActiveGate() : null; }
    public boolean isDebugMenuOpen() { return debugMenuOpen; }
    public int getDebugMenuSelectedIndex() { return debugMenuSelectedIndex; }
    public float getDebugMenuSeekTime() { return debugMenuSeekTime; }
    public int getDebugMenuStageIndex() { return debugMenuStageIndex; }
    public Array<String> getDebugMenuStageIds() { return assets.getStageIds(); }
    public float getSpawnScheduleTotalTime() {
        return spawnScheduler != null ? spawnScheduler.getTotalTime() : (triggerManager != null ? triggerManager.getCamera().getPosition() : 0f);
    }
    // The never-frozen clock text cues are timed against.
    public float getSpawnScheduleRealTime() {
        return spawnScheduler != null ? spawnScheduler.getRealTime() : (triggerManager != null ? triggerManager.getRealTime() : 0f);
    }
    // Stage complete without a boss (e.g. the tutorial).
    public boolean isScheduleEndTriggered() {
        return spawnScheduler != null ? spawnScheduler.isScheduleEndTriggered() : (triggerManager != null && triggerManager.isScheduleEndTriggered());
    }
    // Schedule-wide confirm mode; trigger stages use per-cue TextCue.requireConfirm instead.
    public boolean isTextCuesRequireConfirm() { return spawnScheduler != null && spawnScheduler.isTextCuesRequireConfirm(); }
    // The SHOOT binding for the active input device (keyboard or gamepad), for the confirm hint.
    public String getTextCueConfirmKeyLabel() {
        if (input.getActiveInput() == InputType.GAMEPAD) {
            return keyBindings.getGamepadButton(KeyBindings.Action.SHOOT).displayName;
        }
        return Input.Keys.toString(keyBindings.getKey(KeyBindings.Action.SHOOT));
    }
    public Array<DebugSaveState> getDebugSaveStates() { return debugSaveStateManager.getSaveStates(); }
    public float getLevelStartTimer() { return levelStartTimer; }
    public Array<TextCue> getTextCues() { return combinedTextCues; }
    public float getBombCooldownTimer() { return Math.max(bombCooldownTimer, 0f); }
    public float getBombCooldownFraction() { return Math.max(bombCooldownTimer, 0f) / assets.getGameBalance().bombCooldown; }
    public int getCurrentFps() { return currentFps; }
    public int getLowestFps() { return lowestFps == Integer.MAX_VALUE ? currentFps : lowestFps; }
    public int getHighestFps() { return highestFps; }
    public int[] getFpsHistory() { return fpsHistory; }
    public EntityManager getEntities() { return entities; }
    public boolean isPatternPreviewActive() { return patternPreviewer.isActive(); }
    public PatternPreviewer getPatternPreviewer() { return patternPreviewer; }
    public boolean isSpawnScheduleEditorActive() { return spawnScheduleEditor.isActive(); }
    public SpawnScheduleEditor getSpawnScheduleEditor() { return spawnScheduleEditor; }
    public CollisionManager getCollisionManager() { return collisionManager; }
    public boolean isAudioMuted() { return audio.isMuted(); }
    public boolean isReplaying() { return replayPlayer != null; }
    public float getReplayProgress() { return replayPlayer != null ? replayPlayer.getProgress() : 0f; }
    public boolean isReplayBrowserActive() { return replayBrowser.isActive(); }
    public ReplayBrowser getReplayBrowser() { return replayBrowser; }
}
