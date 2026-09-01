package whitelabeltest.gamemanagers;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.effects.ExplosionEffect;
import whitelabeltest.gamemanagers.spawning.GameBalance;
import whitelabeltest.gamemanagers.effects.PointGem;
import whitelabeltest.gamemanagers.replay.ReplayFrame;
import whitelabeltest.gamemanagers.spawning.StageDefinition;
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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
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
    private final KeyBindings keyBindings;
    private final EntityManager entities;
    private final CollisionManager collisionManager;
    private ScrollingBackground background;
    private SpawnScheduler spawnScheduler;
    // Camera-position-driven counterpart to spawnScheduler - see TriggerManager's class doc. Null
    // for any stage whose StageDefinition.triggerFile is unset (every stage but stage1, for now).
    private TriggerManager triggerManager;
    // spawnScheduler's own (wall-clock) textCues plus triggerManager's (distance-driven) ones,
    // refreshed every update() - see getTextCues(). A stage like stage1, whose schedule.json no
    // longer authors any text cues at all, just contributes an empty list here, so UIManager keeps
    // drawing from one combined source either way.
    private final Array<TextCue> combinedTextCues = new Array<>();
    private final InputManager input;
    private final AudioSettings audioSettings;
    // Which named ordering of stages (see StageSequenceDefinition/AssetManager.getStageSequence())
    // this run is playing through - swapping this is how alternate modes (tutorial, practice, a
    // boss-rush, etc.) reuse the same stage pool in a different order/subset without any other
    // GameController change.
    public static final String DEFAULT_STAGE_SEQUENCE_ID = "campaign";
    // TUTORIAL on the start menu skips WeaponSelectScreen entirely and jumps straight here - see
    // Main.transitionToTutorial() and this sequence's startingLoadout in stage_sequences.json.
    public static final String TUTORIAL_STAGE_SEQUENCE_ID = "tutorial";
    // Non-final: startReplay() swaps this to the recorded run's sequence id without needing a new
    // GameController instance.
    private String stageSequenceId;
    // The resolved list of stage ids for stageSequenceId, fixed for the lifetime of this
    // GameController (re-resolved on every reset() in case the underlying JSON changed, e.g. via
    // the debug enemy/pattern editor's live-reload path).
    private Array<String> stageSequence;
    // Explicit index into stageSequence (not raw position in AssetManager's stage pool) of the
    // currently-loaded stage - see loadStage()/advanceToNextStage().
    private int stageIndex;
    private int totalEnemiesAcrossRun;
    // Non-final: startReplay() swaps this to the recorded run's loadout without needing a new
    // GameController instance.
    private WeaponLoadout loadout;

    // Replay recording/playback - see ReplayRecorder/ReplayPlayer/ReplayBrowser. Mutually
    // exclusive: recorder is null while a replay is being watched, and vice versa.
    private final ReplayBrowser replayBrowser = new ReplayBrowser();
    private ReplayRecorder recorder;
    private ReplayPlayer replayPlayer;

    // Full-screen "before the stage starts" cinematic - see InterstitialPlayer/startInterstitial().
    private final InterstitialPlayer interstitialPlayer = new InterstitialPlayer();

    private final ScoreManager scoreManager;
    private boolean gameOver;
    private float gameOverTimer;
    private boolean levelComplete;
    // Set when QUIT is confirmed from the game-over/level-complete prompt - see
    // handleGameOverInput()/handleLevelCompleteInput(). Main polls this each frame while PLAYING and
    // tears this GameController down for the start screen once it's set, same as it already does
    // when a replay-from-menu playback ends or the tutorial's schedule reaches its scripted end.
    private boolean quitToMenuRequested;
    private boolean bossVideoTriggered;
    private boolean musicFadeTriggered;
    private static final float LEVEL_COMPLETE_DELAY = 3f;
    private float levelCompleteDelayTimer = -1f;
    private boolean debugMode;
    private boolean debugToolsAvailable;
    private float levelStartTimer;
    private float bombCooldownTimer;
    private final float worldWidth, worldHeight;
    // The stage currently loaded (see loadStage()) - kept around so the debug Spawn Schedule Editor
    // can still open the right file (StageDefinition.spawnSchedule) even for a stage whose
    // spawnScheduler is null (see that field's own doc), without needing a live SpawnScheduler
    // instance just to ask it for the path it was already constructed from.
    private StageDefinition currentStageDef;
    // Resolved once per loadStage() from stageDef.groundScrollSpeed (falling back to
    // ScrollingBackground.DEFAULT_SCROLL_SPEED) for a stage with no spawnScheduler running -
    // consulted every frame in update() the same way spawnScheduler.getGroundScrollSpeed() is for
    // one that still has one. See StageDefinition.groundScrollSpeed's own doc.
    private float groundScrollSpeed;

    private final DebugSaveStateManager debugSaveStateManager;
    private final PatternPreviewer patternPreviewer = new PatternPreviewer();
    private final SpawnScheduleEditor spawnScheduleEditor = new SpawnScheduleEditor();
    private boolean debugMenuOpen;
    private int debugMenuSelectedIndex;
    private float debugMenuSeekTime;
    // Index into AssetManager.getStageIds() the ROW_STAGE_SELECT row is currently showing - see
    // handleDebugMenuInput()/debugLoadStage(). Initialized to the currently-loaded stage whenever
    // the menu opens, same as debugMenuSeekTime snapping to the current schedule time.
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
        this.keyBindings = keyBindings;
        this.input = new InputManager(keyBindings);

        this.scoreManager = new ScoreManager(assets.getGameBalance().defaultChainWindow);
        this.debugSaveStateManager = new DebugSaveStateManager();

        if (System.getProperty("debug") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp")) {
            this.debugToolsAvailable = true;
            this.debugMode = true;
        }

        reset();
    }

    public void update(float delta) {
        ReplayFrame frame = null;
        // Mirrors recording's own rule (recorder.record() only runs once past the debugMenuOpen
        // early-return below) - don't consume a replay frame while the menu is open, or reopening
        // it mid-playback would drop frames the same way starting a replay used to. Same reasoning
        // for the interstitial: it's a real-time cosmetic moment outside "the run" (see
        // InterstitialPlayer's class doc), so it must never eat into the frame stream either.
        if (replayPlayer != null && !debugMenuOpen && !interstitialPlayer.isActive()) {
            if (!replayPlayer.hasNext()) {
                stopReplay();
                return;
            }
            frame = replayPlayer.next();
            if (!Float.isNaN(frame.seekToTime)) {
                if (spawnScheduler != null) spawnScheduler.seekTo(frame.seekToTime, audio);
                if (triggerManager != null) triggerManager.seekTo(frame.seekToTime);
                entities.clearWorld();
                background.seekTo(frame.seekToTime);
                return; // instantaneous - consumes no simulated time, resume on the next update() call
            }
            delta = frame.delta;
        }

        if (interstitialPlayer.isActive()) {
            // Always live input here, live run or replay watch alike - a skip must never touch the
            // recorded/replayed frame stream (see InterstitialPlayer's class doc).
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
        }
        input.update(frame);
        updateFpsMonitor(delta);

        if (debugToolsAvailable && input.isDebugToggleJustPressed()) {
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

        // See SpawnScheduler.isWeaponsDisabled() - a scripted "you haven't been taught this yet"
        // window (e.g. the tutorial, before its weapons section) that withholds both firing and
        // bombing, not just one. OR'd with TriggerManager's own distance-based equivalent (see
        // TriggerManager.isWeaponsDisabled()) so a stage can author these windows either way.
        boolean weaponsDisabled = spawnScheduler != null ? spawnScheduler.isWeaponsDisabled(spawnScheduler.getTotalTime())
            : (triggerManager != null && triggerManager.isWeaponsDisabled(triggerManager.getCamera().getPosition()));
        // See SpawnScheduler.isHyperAttackDisabled()/isBombDisabled() - separate "not taught yet"
        // windows from weaponsDisabled, since a tutorial teaches normal fire, Hyper Attack, and
        // bombing at three different points rather than all at once.
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
            handleLevelCompleteInput();
            return;
        }

        background.update(delta);
        entities.update(delta, input, assets, audio, weaponsDisabled, hyperAttackDisabled,
            spawnScheduler != null ? spawnScheduler.getGroundScrollSpeed() : groundScrollSpeed, background);
        if (spawnScheduler != null) {
            spawnScheduler.update(delta, entities, audio, input,
                scoreManager.getEnemiesDestroyed(), scoreManager.getGemsCollected(), entities.getPlayer().getGrazePoints());
        }
        if (triggerManager != null) {
            triggerManager.update(delta, entities, audio, input, scoreManager);
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
        // Must run after every check above - see its javadoc for why paired-enemy deaths are
        // deferred instead of resolved inline in each of those.
        collisionManager.resolvePairedEnemyDeaths(entities.getEnemies(), audio, entities, assets, worldWidth, worldHeight, scoreManager, delta);
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
            // No-op for a stage now driven entirely by its triggerFile (spawnScheduler == null -
            // see StageDefinition.triggerFile's own doc) - since nothing reads spawnSchedule content
            // back into live gameplay for one anymore, opening this editor for it would just be
            // misleading (edits made there would silently have no in-game effect).
            if (input.isDebugMenuConfirmJustPressed() && spawnScheduler != null) {
                spawnScheduleEditor.open(assets, currentStageDef.spawnSchedule, worldWidth, worldHeight,
                    // Saving must reach the currently-running game immediately (see
                    // SpawnScheduleEditor.onSavedToDisk's doc) - reuses the same full stage reload
                    // the Stage Select row already does, for the stage that's currently active.
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
        entities.clearWorld();
        background.seekTo(targetTime);
        debugMenuOpen = false;
        // A seek is an instantaneous clock jump outside the normal delta-accumulation model
        // recorder.record() captures - without this, a replay of this run would have no idea the
        // jump happened and would desync from whatever the recorded player was actually reacting to.
        if (recorder != null) recorder.recordSeek(targetTime);
    }

    private void applyLevelCompleteBonus() {
        Player player = entities.getPlayer();
        GameBalance balance = assets.getGameBalance();

        levelCompleteBombBonus = player.getNumBombs() * balance.bombBonusPerUnusedBomb;
        if (levelCompleteBombBonus > 0) scoreManager.addBonus(levelCompleteBombBonus);

        float bossSpawnTime = spawnScheduler != null ? spawnScheduler.getBossSpawnTime() : -1f;
        // Falls back to the trigger-driven boss spawn (see TriggerManager.getBossSpawnDistance())
        // once a stage's boss spawns via a trigger instead of a SpawnEvent - otherwise this stays -1
        // forever and the boss time bonus/rank contribution below silently zeroes out.
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

    private void handleLevelCompleteInput() {
        if (input.isRestartJustPressed()) {
            if (hasNextStage()) advanceToNextStage(); else reset();
        } else if (input.isQuitJustPressed()) {
            quitToMenuRequested = true;
        }
    }

    private void loadStage(int index) {
        StageDefinition stageDef = assets.getStageDefinition(stageSequence.get(index));
        currentStageDef = stageDef;
        if (background != null) background.dispose();
        background = new ScrollingBackground(worldWidth, worldHeight, audioSettings, assets, stageDef.backgroundLayers, stageDef.bossVideo, stageDef.backgroundVideo, stageDef.shaderBackground, stageDef.hueCycleBackground, stageDef.playerFeedbackBackground);
        background.setMuted(audio.isMuted());
        // A stage with a triggerFile reads ONLY from it for gameplay - see StageDefinition.
        // triggerFile's own doc - so spawnScheduler isn't even constructed for one, keeping the
        // level editor (which only ever edits triggerFile) in full parity with what actually runs.
        // spawnSchedule stays a fallback for some hypothetical future stage authored the old way.
        if (stageDef.triggerFile != null) {
            spawnScheduler = null;
            triggerManager = new TriggerManager(worldWidth, worldHeight, assets, stageDef.triggerFile, EnemyDefinitionLoader.load(), background);
        } else {
            spawnScheduler = new SpawnScheduler(worldWidth, worldHeight, assets, stageDef.spawnSchedule);
            triggerManager = null;
        }
        background.setKaleidoscopeTransitionTime(stageDef.kaleidoscopeTransitionTime != null ? stageDef.kaleidoscopeTransitionTime
            : (spawnScheduler != null ? spawnScheduler.getKaleidoscopeTransitionTime() : Stage2KaleidoscopeShader.DEFAULT_TRANSITION_TIME));
        groundScrollSpeed = stageDef.groundScrollSpeed != null ? stageDef.groundScrollSpeed
            : (spawnScheduler != null ? spawnScheduler.getGroundScrollSpeed() : ScrollingBackground.DEFAULT_SCROLL_SPEED);
        // A trigger-authored boss video (see Trigger.triggerBossVideo) wins over the schedule's own
        // (wall-clock) backgroundVideoTime when both could apply - see
        // TriggerManager.getBossVideoDistance()'s own doc on why this period is derived from
        // whichever source actually drives this stage's boss video now.
        float bossVideoDistance = triggerManager != null ? triggerManager.getBossVideoDistance() : -1f;
        background.setHueCyclePeriod(bossVideoDistance >= 0f ? bossVideoDistance : (spawnScheduler != null ? spawnScheduler.getBackgroundVideoTime() : -1f));
        audio.loadStageMusic(stageDef.music);
        totalEnemiesAcrossRun += (spawnScheduler != null ? spawnScheduler.getSchedule().size : 0) + (triggerManager != null ? triggerManager.getEnemySpawnCount() : 0);
        combinedTextCues.clear();
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
        startInterstitial();
    }

    /** Kicks off a randomly-chosen interstitial video before a stage's gameplay begins - see
     *  InterstitialPlayer/update(). Falls straight through to stage music with no video if none are
     *  configured (empty data/interstitials.json). The pick draws from the same seeded
     *  MathUtils.random stream as everything else, so which clip plays stays reproducible across a
     *  replay's record/playback - see InterstitialPlayer's class doc. */
    private void startInterstitial() {
        Array<String> videos = assets.getInterstitialVideos();
        if (videos.size == 0) {
            audio.playStageMusic();
            return;
        }
        String chosen = videos.get(MathUtils.random(videos.size - 1));
        interstitialPlayer.play(chosen, audio.isMuted() ? 0f : audioSettings.getEffectiveMusicVolume());
    }

    /** Debug-only: jumps straight into an arbitrary stage from stages.json, bypassing whatever
     *  stage_sequences.json normally governs progression - the only way to reach a stage (like
     *  "testground") that isn't part of any curated sequence. Replaces stageSequence with a
     *  synthetic single-entry list so hasNextStage() is false and normal advancement stays inert.
     *  Not representable as a mid-run seek (c.f. seekToTime's recordSeek), so any in-progress
     *  replay recording is simply dropped rather than corrupted. */
    private void debugLoadStage(String stageId) {
        recorder = null;
        stageSequence = new Array<>();
        stageSequence.add(stageId);
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

    /** The JavaFX editor's "Quick Play" button (see Main.transitionToQuickPlay()) - jumps straight
     *  into `stageId` (reusing debugLoadStage()'s existing, already-proven "arbitrary stage" path),
     *  overrides the player's two weapon slots directly (bypassing WeaponLoadout - Player.
     *  setSlotWeapon() is the same call WeaponLoadout's own application already goes through), then
     *  seeks to startDistance (reusing seekToTime() - already safe for a triggerFile-only stage, see
     *  its own null-guards) so testing can start mid-level instead of always from distance 0. A
     *  slot id of null leaves that slot at whatever the constructor's placeholder WeaponLoadout gave
     *  it; startDistance <= 0 skips seeking entirely (already at distance 0 from the fresh
     *  debugLoadStage() above, so nothing to do). */
    public void quickStartAtStage(String stageId, float startDistance, String slotAWeaponId, String slotBWeaponId) {
        debugLoadStage(stageId);
        if (slotAWeaponId != null) entities.getPlayer().setSlotWeapon(0, slotAWeaponId);
        if (slotBWeaponId != null) entities.getPlayer().setSlotWeapon(1, slotBWeaponId);
        if (startDistance > 0) seekToTime(startDistance);
    }

    public boolean hasNextStage() { return stageIndex + 1 < stageSequence.size; }
    public int getStageNumber() { return stageIndex + 1; }

    /** Overrides entities.reset(loadout)'s ordinary WeaponSelectScreen-driven loadout with a stage
     *  sequence's fixed StartingLoadoutDefinition (e.g. "tutorial"'s) - see reset(). Runs right
     *  after entities.reset(loadout), which has already zeroed every weapon's level and cleared
     *  both slots, so this only needs to set what the config actually specifies. */
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

    /** Switches this GameController into replaying a previously-recorded run - see ReplayBrowser/
     *  ReplayRecorder. Doesn't flush the outgoing recorder itself; reset() (called at the end here)
     *  already does that at its own top, exactly once, in the right place. */
    public void startReplay(ReplayData data) {
        this.stageSequenceId = data.stageSequenceId;
        try {
            this.loadout = WeaponLoadout.valueOf(data.weaponLoadout);
        } catch (IllegalArgumentException e) {
            Gdx.app.error("GameController", "Unknown weapon loadout in replay: " + data.weaponLoadout, e);
            return;
        }
        this.replayPlayer = new ReplayPlayer(data);
        // Picking a replay happens *from inside* the debug menu - leaving it open would otherwise
        // silently drop every frame update() pulls from replayPlayer (consumed at the top of
        // update(), but discarded by the debugMenuOpen early-return below) until the dev manually
        // closes it, permanently offsetting the schedule from the input stream from that point on.
        this.debugMenuOpen = false;
        reset();
    }

    /** Hands control back to live play with a fresh recording - called once a replay's frames run
     *  out (see update()) or manually to abandon a replay early. */
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
        // Scripted "safely stand in this fire" window (see SpawnScheduler.isPlayerInvincible) -
        // completely consequence-free, not even a chain break, unlike every other branch below. OR'd
        // with TriggerManager's own distance-based equivalent (see TriggerManager.isPlayerInvincible()).
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

    /** A hit during SpawnScheduler.isInPracticeSection() - e.g. a tutorial dodge drill - takes this
     *  path instead of applyPlayerHit()'s normal life-loss/game-over branch: costs no life, plays a
     *  hit sound for feedback, then rewinds the schedule back to the section's start and wipes
     *  whatever hit the player so it replays from scratch - same seekTo()+clearWorld() pairing the
     *  debug menu's seek uses. Deliberately does NOT call player.startDeath() the way a real hit
     *  does - that sets isDead for DEATH_WAIT (2s) and then isInvincible for another
     *  invincibleFrameTime (2s), during which EntityManager holds firingPaused true for every
     *  enemy (see its firingPaused computation). The schedule doesn't pause for that: with waves
     *  spawning every ~1.2s here, a 4-second firing freeze let 2+ waves stack up fully spawned but
     *  unfired, so they all fired together the instant firingPaused cleared - each wave's bullets
     *  covering the lane the OTHER wave left open, unioning into a solid, gap-free wall. Skipping
     *  startDeath() avoids that stall entirely; clearWorld() already wipes every bullet on screen,
     *  so there's nothing left that could hit the player again this instant anyway. */
    private void restartPracticeSection() {
        audio.playPlayerDeath();
        entities.destroyAllPlayerBullets();
        // Whichever source's window actually contains the current position wins - same priority
        // isInPracticeSection() above already checks (spawnScheduler first, then triggerManager).
        float checkpointStart;
        if (spawnScheduler != null) {
            checkpointStart = spawnScheduler.isInPracticeSection(spawnScheduler.getTotalTime())
                ? spawnScheduler.getPracticeCheckpointStart(spawnScheduler.getTotalTime())
                : spawnScheduler.getTotalTime();
        } else if (triggerManager != null) {
            float position = triggerManager.getCamera().getPosition();
            checkpointStart = triggerManager.isInPracticeSection(position) ? triggerManager.getPracticeCheckpointStart(position) : position;
        } else {
            checkpointStart = 0f;
        }
        if (spawnScheduler != null) spawnScheduler.seekTo(checkpointStart, audio);
        if (triggerManager != null) triggerManager.seekTo(checkpointStart);
        entities.clearWorld();
    }

    private void sufferBombDamage(int damage, Array<Enemy> enemies) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            // See CollisionManager.resolvePairedEnemyDeaths(), called later this same update() -
            // a paired enemy's death is deferred there instead of scored/destroyed immediately.
            if (e.takeDamage(damage) && e.getPairId() == null) {
                scoreManager.addScore(destroyEnemy(audio, entities, assets, worldWidth, worldHeight, e, scoreManager));
            }
        }
    }

    public static int destroyEnemy(AudioManager audio, EntityManager entityManager, AssetManager assets, float worldWidth, float worldHeight, Enemy enemy, ScoreManager scoreManager) {
        scoreManager.registerEnemyDestroyed(enemy.getDefinitionId());
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

    /** Interleaves EnemyDefinition.backgroundLayer-attached enemies between individual background
     *  layers (see ScrollingBackground.drawLayer()/EntityManager.drawEnemiesAttachedToLayer())
     *  whenever there's an ordinary layer stack to sandwich them against - see
     *  ScrollingBackground.isDrawingLayerStack(). Falls back to the plain "background fully behind
     *  everything" draw whenever there isn't (a boss/background video or shader background is
     *  covering the screen instead, or this stage simply has no backgroundLayers at all) - a
     *  layer-attached enemy just draws normally in that case, same as before this feature existed. */
    public void draw(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        // No-op unless this stage set StageDefinition.playerFeedbackBackground - see
        // ScrollingBackground.updatePlayer()/updateHalo(). Must happen before background.draw()/
        // beginLayeredDraw() below so the feedback overlay composites THIS frame's player position,
        // not last frame's.
        //
        // While the player is dead, Player.update() early-returns (see its own isDead branch), so
        // sprite/position are frozen for the whole DEATH_WAIT window - feeding that same frozen frame
        // into the feedback trail every frame at full opacity would reinforce it faster than
        // PlayerFeedbackShader's own decay can fade it, leaving a solid, non-blinking "statue" sitting
        // at the respawn point for roughly as long as the invincibility window that follows lasts (a
        // "reappears without blinking and can't move" bug report - the real player CAN move, a stale
        // feedback copy is just stuck on top of them). Passing a null frame (but the real position, so
        // the shader's zoom/warp pivot doesn't snap to the origin) skips that reinforcement entirely,
        // so any already-accumulated trail just decays normally through the death window instead.
        Player feedbackPlayer = entities.getPlayer();
        TextureRegion feedbackFrame = feedbackPlayer.isDead() ? null : feedbackPlayer.getCurrentFrame();
        TextureRegion feedbackHaloFrame = feedbackPlayer.isDead() ? null : feedbackPlayer.getHaloFrame();
        background.updatePlayer(feedbackFrame, feedbackPlayer.getX(), feedbackPlayer.getY(),
            feedbackPlayer.getWidth(), feedbackPlayer.getHeight());
        background.updateHalo(feedbackHaloFrame, feedbackPlayer.getHaloX(), feedbackPlayer.getHaloY(),
            feedbackPlayer.getHaloWidth(), feedbackPlayer.getHaloHeight());

        int layerCount = background.getLayerCount();
        if (background.isDrawingLayerStack() && layerCount > 0) {
            background.beginLayeredDraw(batch);
            for (int i = 0; i < layerCount; i++) {
                background.drawLayer(batch, i);
                entities.drawEnemiesAttachedToLayer(batch, i);
            }
            background.endLayeredDraw(batch);
            // draw() itself isn't called on this branch, so the feedback overlay (which draw() would
            // otherwise apply on top of the layer stack) needs its own explicit call here.
            background.drawPlayerFeedbackOverlay(batch);
            entities.draw(batch, layerCount);
        } else {
            background.draw(batch);
            entities.draw(batch, 0);
        }
        interstitialPlayer.draw(batch, worldWidth, worldHeight);
    }

    public void reset() {
        if (recorder != null) {
            recorder.setSummary(scoreManager.getScore(), stageIndex + 1, gameOver);
            recorder.saveIfNonTrivial();
        }
        long seed = replayPlayer != null ? replayPlayer.getSeed() : System.nanoTime();
        MathUtils.random.setSeed(seed);
        // Tutorial runs are scripted practice, not "a run" worth sharing/replaying - see
        // ReplayRecorder.record()/dispose(), which only ever fire when recorder is non-null, so
        // leaving it null here is enough to suppress recording entirely for this mode.
        boolean recordingEnabled = replayPlayer == null && !stageSequenceId.equals(TUTORIAL_STAGE_SEQUENCE_ID);
        recorder = recordingEnabled ? new ReplayRecorder(stageSequenceId, loadout, seed) : null;

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
        stageSequence = sequenceDef.stageIds;
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
    }

    @Override
    public void dispose() {
        if (recorder != null) {
            recorder.setSummary(scoreManager.getScore(), stageIndex + 1, gameOver);
            recorder.saveIfNonTrivial();
            recorder = null;
        }
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
    public int getLevelCompleteBombBonus() { return levelCompleteBombBonus; }
    public int getLevelCompleteLivesMultiplier() { return levelCompleteLivesMultiplier; }
    public int getEnemiesDestroyed() { return scoreManager.getEnemiesDestroyed(); }
    public int getTotalEnemyCount() { return totalEnemiesAcrossRun; }
    public int getMaxChainCount() { return scoreManager.getMaxChainCount(); }
    public LevelRank getLevelCompleteRank() { return levelCompleteRank; }
    public int getLevelCompleteTimeBonus() { return levelCompleteTimeBonus; }
    public float getLevelCompleteBossFightSeconds() { return levelCompleteBossFightSeconds; }
    public boolean isDebugMode() { return debugMode; }
    // Debug-only (see UIManager.drawDebugTriggerInfo()) - null when this stage has no trigger file
    // at all, so the overlay simply doesn't draw.
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
    // Falls back to TriggerManager's own clock (see that class's own doc on why it now owns one)
    // once a stage has no spawnScheduler running - UIManager.drawTextCues() uses this SAME value to
    // measure every cue's reveal progress regardless of which source actually fired it.
    public float getSpawnScheduleRealTime() {
        return spawnScheduler != null ? spawnScheduler.getRealTime() : (triggerManager != null ? triggerManager.getRealTime() : 0f);
    }
    // See SpawnScheduler.isScheduleEndTriggered()/TriggerManager.isScheduleEndTriggered() - always
    // false for an ordinary arcade stage (only a schedule/trigger file that explicitly sets one,
    // e.g. the tutorial, ever latches this).
    public boolean isScheduleEndTriggered() {
        return spawnScheduler != null ? spawnScheduler.isScheduleEndTriggered() : (triggerManager != null && triggerManager.isScheduleEndTriggered());
    }
    // See SpawnScheduler.isTextCuesRequireConfirm()/UIManager.drawTextCues(). No whole-file
    // equivalent exists on the trigger side (Trigger.requireConfirm is already per-trigger, a finer
    // grain than this global UI hint flag ever was) - false (this flag's own default, same as an
    // empty/unset schedule already produced for every triggerFile-driven stage today) once
    // spawnScheduler stops running, so this preserves the exact behavior already in effect.
    public boolean isTextCuesRequireConfirm() { return spawnScheduler != null && spawnScheduler.isTextCuesRequireConfirm(); }
    // Matches whichever input the player's actually using - a gamepad player dismissing tutorial
    // messages with the SHOOT/RESTART buttons (see SpawnScheduler.update()'s cue-await-confirm
    // block) shouldn't see a keyboard-only "Press SPACE" hint they can't act on.
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
