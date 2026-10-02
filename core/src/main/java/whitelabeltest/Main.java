package whitelabeltest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.EnemyHitboxes;
import whitelabeltest.enemy.HitboxDef;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.GameController;
import whitelabeltest.gamemanagers.input.InputType;
import whitelabeltest.gamemanagers.input.KeyBindings;
import whitelabeltest.gamemanagers.replay.GhostRun;
import whitelabeltest.gamemanagers.replay.ReplayData;
import whitelabeltest.gamemanagers.UIManager;
import whitelabeltest.player.WeaponLoadout;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.ThunderboltWeapon;
import whitelabeltest.player.weapons.Weapon;
import whitelabeltest.perf.PerfProbe;

/** Application root: a small state machine over the menu screens and the game (GameController),
 *  plus the letterboxed draw and the debug hitbox overlay. */
public class Main extends ApplicationAdapter {
    private enum AppState { START, WEAPON_SELECT, OPTIONS, REPLAY_SELECT, PLAYING }

    /** The editor's Quick Play settings (read from system properties by Lwjgl3Launcher); null for
     *  a normal launch. */
    public static class QuickPlayConfig {
        public final String stageId;
        public final float startDistance;
        public final String slotAWeaponId;
        public final String slotBWeaponId;
        // 0 = keep the default equip level (1).
        public final int slotALevel;
        public final int slotBLevel;
        // Chosen in the editor, since the start screen's input detection is skipped. Never null.
        public final InputType inputType;

        public QuickPlayConfig(String stageId, float startDistance, String slotAWeaponId, String slotBWeaponId,
                                int slotALevel, int slotBLevel, InputType inputType) {
            this.stageId = stageId;
            this.startDistance = startDistance;
            this.slotAWeaponId = slotAWeaponId;
            this.slotBWeaponId = slotBWeaponId;
            this.slotALevel = slotALevel;
            this.slotBLevel = slotBLevel;
            this.inputType = inputType;
        }
    }

    private final QuickPlayConfig quickPlay;

    public Main() { this(null); }

    public Main(QuickPlayConfig quickPlay) { this.quickPlay = quickPlay; }

    private AppState state = AppState.START;
    private StartScreen startScreen;
    private WeaponSelectScreen weaponSelectScreen;
    private InputType pendingInputType;
    private OptionsScreen optionsScreen;
    private ReplaySelectScreen replaySelectScreen;
    // Racing a replay picked with "race ghost": its ghost, alongside the live game.
    private GhostRun ghostRun;
    // Watching a replay picked from the menu: when it ends or is cancelled, return to the start screen.
    private boolean replayFromMenu;
    private KeyBindings keyBindings;
    private AudioSettings audioSettings;
    private UIManager ui;
    private GameController game;
    private Sound startScreenConfirmSound;
    private Sound weaponSelectConfirmSound;
    private Sound replaySelectConfirmSound;

    private SpriteBatch spriteBatch;
    private ShapeRenderer shapeRenderer;
    // Scratch shapes for drawDebug()'s enemy hitbox overlay.
    private final Circle debugCircle = new Circle();
    private final Rectangle debugRect = new Rectangle();
    private ExtendViewport viewport;

    private final float PLAY_AREA_WIDTH = 9f;
    private final float PLAY_AREA_HEIGHT = 12f;

    private final Vector3 scissorBL = new Vector3();
    private final Vector3 scissorTR = new Vector3();

    private boolean prevControllerBackDown;

    // -DautoReplay=<replay json> plays that replay with no menus and quits when it ends, or after
    // -DautoReplay.seconds=<n>. For repeatable perf runs (see PerfProbe). 0 = not an auto replay.
    private float autoReplayLimit;
    private float autoReplayElapsed;

    private boolean startAutoReplay() {
        String path = System.getProperty("autoReplay");
        if (path == null || path.isBlank()) return false;
        ReplayData data = new com.badlogic.gdx.utils.Json().fromJson(ReplayData.class, Gdx.files.absolute(path));
        float seconds = Float.parseFloat(System.getProperty("autoReplay.seconds", "0"));
        autoReplayLimit = seconds > 0f ? seconds : -1f;
        replayFromMenu = true;
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings, WeaponLoadout.BASIC_THUNDERBOLT);
        game.setActiveInput(InputType.KEYBOARD);
        game.startReplay(data);
        ui = new UIManager(InputType.KEYBOARD);
        state = AppState.PLAYING;
        return true;
    }

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        viewport = new ExtendViewport(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        keyBindings = new KeyBindings();
        audioSettings = new AudioSettings();
        PerfProbe.init();
        if (startAutoReplay()) return;
        if (quickPlay != null) {
            transitionToQuickPlay();
        } else {
            startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        }
    }

    @Override
    public void render() {
        PerfProbe.frameStart();
        renderFrame();
        PerfProbe.frameEnd();
        if (autoReplayLimit > 0f && state == AppState.PLAYING && (autoReplayElapsed += Gdx.graphics.getDeltaTime()) >= autoReplayLimit) Gdx.app.exit();
    }

    private void renderFrame() {
        float delta = Gdx.graphics.getDeltaTime();
        if (state == AppState.START) {
            InputType detected = startScreen.update(delta);
            drawStartScreen();
            if (detected != null) {
                if (startScreen.isTutorialSelected()) {
                    transitionToTutorial(detected);
                } else {
                    transitionToWeaponSelect(detected);
                }
            } else if (startScreen.consumeOptionsRequested()) {
                transitionToOptions();
            } else if (startScreen.consumeReplaysRequested()) {
                transitionToReplaySelect();
            }
        } else if (state == AppState.WEAPON_SELECT) {
            WeaponLoadout chosen = weaponSelectScreen.update(delta);
            drawWeaponSelectScreen();
            if (chosen != null) {
                transitionToGame(pendingInputType, chosen);
            }
        } else if (state == AppState.OPTIONS) {
            ScreenUtils.clear(Color.BLACK);
            // The start screen's music keeps playing behind Options; track the volume sliders live.
            if (startScreen != null) startScreen.applyMusicVolume();
            optionsScreen.render(delta);
            if (isControllerBackJustPressed()) {
                optionsScreen.handleControllerBackPressed();
            }
            if (optionsScreen.isBackRequested()) {
                transitionToStartFromOptions();
            }
        } else if (state == AppState.REPLAY_SELECT) {
            if (startScreen != null) startScreen.applyMusicVolume();
            ReplayData picked = replaySelectScreen.update(delta);
            drawReplaySelectScreen();
            if (picked != null && replaySelectScreen.isGhostRequested()) {
                transitionToGhostRace(picked, replaySelectScreen.getPickInputType());
            } else if (picked != null) {
                transitionToReplayWatch(picked);
            } else if (replaySelectScreen.isBackRequested()) {
                transitionToStartFromReplaySelect();
            }
        } else {
            PerfProbe.begin(PerfProbe.Section.UPDATE);
            game.update(delta);
            if (ghostRun != null) ghostRun.update(delta);
            PerfProbe.end(PerfProbe.Section.UPDATE);
            PerfProbe.begin(PerfProbe.Section.DRAW);
            drawGame();
            PerfProbe.end(PerfProbe.Section.DRAW);
            if (replayFromMenu) {
                boolean backPressed = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || isControllerBackJustPressed();
                if (!game.isReplaying() || backPressed) {
                    transitionToStartFromReplayWatch();
                }
            } else if (game.isScheduleEndTriggered()) {
                // Boss-less stages (the tutorial) end here instead of at level complete.
                transitionToStartFromTutorial();
            } else if (game.isQuitToMenuRequested()) {
                transitionToStartFromGameOver();
            }
        }
    }

    private void transitionToWeaponSelect(InputType inputType) {
        startScreenConfirmSound = startScreen.getConfirmSound();
        startScreen.dispose();
        startScreen = null;
        pendingInputType = inputType;
        weaponSelectScreen = new WeaponSelectScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.WEAPON_SELECT;
    }

    private void transitionToGame(InputType inputType, WeaponLoadout loadout) {
        weaponSelectConfirmSound = weaponSelectScreen.getConfirmSound();
        weaponSelectScreen.dispose();
        weaponSelectScreen = null;
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings, loadout);
        game.setActiveInput(inputType);
        ui = new UIManager(inputType);
        state = AppState.PLAYING;
    }

    /** Skips weapon select; the loadout is a placeholder replaced by the tutorial sequence's
     *  startingLoadout. */
    private void transitionToTutorial(InputType inputType) {
        startScreenConfirmSound = startScreen.getConfirmSound();
        startScreen.dispose();
        startScreen = null;
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings,
            WeaponLoadout.BASIC_THUNDERBOLT, GameController.TUTORIAL_STAGE_SEQUENCE_ID);
        game.setActiveInput(inputType);
        ui = new UIManager(inputType);
        state = AppState.PLAYING;
    }

    /** Editor Quick Play: straight into a stage at a distance with the chosen weapons (the loadout
     *  here is a placeholder). */
    private void transitionToQuickPlay() {
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings, WeaponLoadout.BASIC_THUNDERBOLT);
        game.quickStartAtStage(quickPlay.stageId, quickPlay.startDistance, quickPlay.slotAWeaponId, quickPlay.slotBWeaponId,
            quickPlay.slotALevel, quickPlay.slotBLevel);
        game.setActiveInput(quickPlay.inputType);
        ui = new UIManager(quickPlay.inputType);
        state = AppState.PLAYING;
    }

    private boolean isControllerBackJustPressed() {
        Controller controller = Controllers.getCurrent();
        boolean down = controller != null && controller.getButton(controller.getMapping().buttonBack);
        boolean justPressed = down && !prevControllerBackDown;
        prevControllerBackDown = down;
        return justPressed;
    }

    private void transitionToOptions() {
        if (optionsScreen == null) {
            optionsScreen = new OptionsScreen(keyBindings, audioSettings, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        }
        optionsScreen.syncGamepadState();
        Gdx.input.setInputProcessor(optionsScreen.getStage());
        state = AppState.OPTIONS;
    }

    private void transitionToStartFromOptions() {
        optionsScreen.clearBackRequested();
        Gdx.input.setInputProcessor(null);
        state = AppState.START;
    }

    private void transitionToReplaySelect() {
        // startScreen stays alive so backing out resumes it.
        replaySelectScreen = new ReplaySelectScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.REPLAY_SELECT;
    }

    private void transitionToStartFromReplaySelect() {
        replaySelectScreen.dispose();
        replaySelectScreen = null;
        state = AppState.START;
    }

    private void transitionToReplayWatch(ReplayData data) {
        replaySelectConfirmSound = replaySelectScreen.getConfirmSound();
        replaySelectScreen.dispose();
        replaySelectScreen = null;
        if (startScreen != null) {
            startScreen.dispose();
            startScreen = null;
        }
        replayFromMenu = true;
        // Placeholder loadout; startReplay() applies the recorded one.
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings, WeaponLoadout.BASIC_THUNDERBOLT);
        game.setActiveInput(InputType.KEYBOARD);
        game.startReplay(data);
        ui = new UIManager(InputType.KEYBOARD);
        state = AppState.PLAYING;
    }

    /** A live run with the picked replay's stages and loadout, its ghost playing alongside. */
    private void transitionToGhostRace(ReplayData data, InputType inputType) {
        replaySelectConfirmSound = replaySelectScreen.getConfirmSound();
        replaySelectScreen.dispose();
        replaySelectScreen = null;
        if (startScreen != null) {
            startScreen.dispose();
            startScreen = null;
        }
        WeaponLoadout loadout;
        try {
            loadout = WeaponLoadout.valueOf(data.weaponLoadout);
        } catch (IllegalArgumentException | NullPointerException e) {
            loadout = WeaponLoadout.BASIC_THUNDERBOLT;
        }
        String sequence = data.stageSequenceId != null ? data.stageSequenceId : GameController.DEFAULT_STAGE_SEQUENCE_ID;
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings, loadout, sequence);
        game.setActiveInput(inputType);
        ghostRun = new GhostRun(data, game, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        game.setUnderEntitiesDrawer(ghostRun::draw);
        ui = new UIManager(inputType);
        state = AppState.PLAYING;
    }

    private void disposeGhostRun() {
        if (ghostRun == null) return;
        ghostRun.dispose();
        ghostRun = null;
    }

    /** After a menu replay ends or is cancelled (an auto replay quits instead). */
    private void transitionToStartFromReplayWatch() {
        if (autoReplayLimit != 0f) { Gdx.app.exit(); return; }
        replayFromMenu = false;
        disposeGhostRun();
        game.dispose();
        game = null;
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.START;
    }

    /** When the tutorial reaches its scripted end. */
    private void transitionToStartFromTutorial() {
        disposeGhostRun();
        game.dispose();
        game = null;
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.START;
    }

    /** When QUIT is confirmed on the game-over / level-complete prompt. */
    private void transitionToStartFromGameOver() {
        disposeGhostRun();
        game.dispose();
        game = null;
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.START;
    }

    private void drawStartScreen() {
        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        startScreen.draw(spriteBatch);
        spriteBatch.end();
    }

    private void drawWeaponSelectScreen() {
        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        weaponSelectScreen.draw(spriteBatch);
        spriteBatch.end();
    }

    private void drawReplaySelectScreen() {
        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();
        replaySelectScreen.draw(spriteBatch);
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

        PerfProbe.begin(PerfProbe.Section.HUD_DRAW);
        ui.drawEnemyHealthBars(spriteBatch, game.getEntities().getEnemies());

        if (game.isDebugMode()) {
            ui.drawEnemyHealthDebug(spriteBatch, game.getEntities().getEnemies());
        }

        // Inside the scissor: the HUD overlays the play area, and panels switching sides slide
        // out of view past its edges.
        ui.drawHUD(spriteBatch, game.getScoreManager(), game.getEntities().getPlayer(), game.getStageNumber(),
            PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getBombCooldownTimer(), game.getBombCooldownFraction());

        spriteBatch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        ui.drawTextCues(spriteBatch, game.getSpawnScheduleRealTime(), game.getTextCues(),
            game.isTextCuesRequireConfirm(), game.getTextCueConfirmKeyLabel());

        if (isGameOver) {
            ui.drawGameOver(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getGameOverTimer());
        } else if (game.isLevelComplete() && game.isStageSelectActive()) {
            ui.drawStageSelect(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getStageSelect());
        } else if (game.isLevelComplete()) {
            ui.drawLevelComplete(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getScore(),
                game.getLevelCompleteBombBonus(), game.getLevelCompleteLivesMultiplier(),
                game.getEnemiesDestroyed(), game.getTotalEnemyCount(),
                game.getLevelCompleteTimeBonus(), game.getLevelCompleteBossFightSeconds(),
                game.getMaxChainCount(), game.getLevelCompleteRank(),
                game.hasNextStage(), game.getStageName());
        }

        if (game.isDebugMode() && game.isAudioMuted() && !game.isDebugMenuOpen()) {
            ui.drawDebugMuteIndicator(spriteBatch, leftX, PLAY_AREA_HEIGHT);
        }

        if (game.isDebugMode()) {
            ui.drawDebugFpsMonitor(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getCurrentFps(), game.getLowestFps(), game.getHighestFps());
            ui.drawDebugFpsHistogram(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getFpsHistory(), game.getHighestFps());
            Float triggerDistance = game.getTriggerDistance();
            if (triggerDistance != null) {
                ui.drawDebugTriggerInfo(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, triggerDistance, game.getTriggerActiveGateInfo());
            }
        }

        if (game.isDebugMode() && game.isDebugMenuOpen()) {
            if (game.isPatternPreviewActive()) {
                ui.drawPatternPreview(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getPatternPreviewer());
            } else if (game.isReplayBrowserActive()) {
                ui.drawReplayBrowser(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getReplayBrowser());
            } else if (game.isSpawnScheduleEditorActive()) {
                ui.drawSpawnScheduleEditor(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getSpawnScheduleEditor());
            } else {
                ui.drawDebugMenu(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getSpawnScheduleTotalTime(),
                    game.getDebugMenuSeekTime(), game.getDebugMenuSelectedIndex(), game.getDebugSaveStates(),
                    game.getEntities().getPlayer(), game.isAudioMuted(), game.getDebugMenuStageIds(), game.getDebugMenuStageIndex());
            }
        }
        PerfProbe.end(PerfProbe.Section.HUD_DRAW);
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

        if (em.getPlayer().isThunderboltBombActive()) {
            shapeRenderer.setColor(Color.VIOLET);
            shapeRenderer.circle(em.getPlayer().getHaloCenterX(), em.getPlayer().getHaloCenterY(), em.getPlayer().getThunderboltBlastRadius(), 24);
        }

        shapeRenderer.setColor(Color.RED);
        for (Enemy enemy : em.getEnemies()) {
            // Enemies rotate about their rectangle's center.
            Rectangle r = enemy.getRectangle();
            Array<HitboxDef> boxes = enemy.getHitboxDefs();
            if (boxes == null) {
                shapeRenderer.rect(r.x, r.y, r.width / 2f, r.height / 2f, r.width, r.height, 1f, 1f, enemy.getRotation());
                continue;
            }
            // Custom hitboxes, using the same EnemyHitboxes math as collision.
            for (int i = 0; i < boxes.size; i++) {
                HitboxDef box = boxes.get(i);
                if (box.isCircle()) {
                    EnemyHitboxes.circle(box, r, enemy.getRotation(), debugCircle);
                    shapeRenderer.circle(debugCircle.x, debugCircle.y, debugCircle.radius, 16);
                } else {
                    Rectangle hb = EnemyHitboxes.rect(box, r, enemy.getRotation(), debugRect);
                    shapeRenderer.rect(hb.x, hb.y, hb.width / 2f, hb.height / 2f, hb.width, hb.height, 1f, 1f, EnemyHitboxes.totalRotation(box, enemy.getRotation()));
                }
            }
        }

        shapeRenderer.setColor(Color.ORANGE);
        for (EnemyBullet bullet : em.getEnemyBullets()) {
            Rectangle r = bullet.getRectangle();
            float rotation = bullet.getRotation();

            // The hitbox offset is in the bullet's own frame; rotate it to world space (as collision does).
            float offsetX = bullet.getHitboxOffsetX();
            float offsetY = bullet.getHitboxOffsetY();
            float cosR = MathUtils.cosDeg(rotation);
            float sinR = MathUtils.sinDeg(rotation);
            float worldOffsetX = offsetX * cosR - offsetY * sinR;
            float worldOffsetY = offsetX * sinR + offsetY * cosR;

            // The hit-tested box: r scaled about its center, then offset.
            float scale = bullet.getHitboxScale();
            float effWidth = r.width * scale;
            float effHeight = r.height * scale;
            float effX = r.x + (r.width - effWidth) / 2f + worldOffsetX;
            float effY = r.y + (r.height - effHeight) / 2f + worldOffsetY;

            float hitRadius = bullet.getHitRadius();
            if (hitRadius >= 0f) {
                shapeRenderer.circle(effX + effWidth / 2f, effY + effHeight / 2f, hitRadius * scale, 16);
                continue;
            }
            // Rotate about the bullet's own pivot (center for most, bottom-center for lasers).
            float originX = bullet.getRotationPivotX() + worldOffsetX - effX;
            float originY = bullet.getRotationPivotY() + worldOffsetY - effY;
            shapeRenderer.rect(effX, effY, originX, originY, effWidth, effHeight, 1f, 1f, rotation);
        }

        shapeRenderer.setColor(Color.GREEN);
        for (Powerup powerup : em.getPowerups()) {
            shapeRenderer.rect(powerup.getRectangle().x, powerup.getRectangle().y, powerup.getRectangle().width, powerup.getRectangle().height);
        }

        shapeRenderer.setColor(Color.MAGENTA);
        for (Weapon bullet : em.getBullets()) {
            if (bullet instanceof ThunderboltWeapon) {
                Rectangle r = bullet.getRectangle();
                // Un-rotated shape plus rotation about the pivot, as in the SAT test.
                float originX = bullet.getRotationPivotX() - r.x;
                float originY = bullet.getRotationPivotY() - r.y;
                shapeRenderer.rect(r.x, r.y, originX, originY, r.width, r.height, 1f, 1f, bullet.getRotation());
            }
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
        if (optionsScreen != null) optionsScreen.resize(width, height);
    }

    @Override
    public void dispose() {
        PerfProbe.close();
        if (startScreen != null) startScreen.dispose();
        if (weaponSelectScreen != null) weaponSelectScreen.dispose();
        if (optionsScreen != null) optionsScreen.dispose();
        if (replaySelectScreen != null) replaySelectScreen.dispose();
        if (startScreenConfirmSound != null) startScreenConfirmSound.dispose();
        if (weaponSelectConfirmSound != null) weaponSelectConfirmSound.dispose();
        if (replaySelectConfirmSound != null) replaySelectConfirmSound.dispose();
        disposeGhostRun();
        if (game != null) game.dispose();
        if (ui != null) ui.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}
