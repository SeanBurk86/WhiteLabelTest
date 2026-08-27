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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.EntityManager;
import whitelabeltest.gamemanagers.GameController;
import whitelabeltest.gamemanagers.input.InputType;
import whitelabeltest.gamemanagers.input.KeyBindings;
import whitelabeltest.gamemanagers.replay.ReplayData;
import whitelabeltest.gamemanagers.UIManager;
import whitelabeltest.player.WeaponLoadout;
import whitelabeltest.player.powerups.Powerup;
import whitelabeltest.player.weapons.ThunderboltWeapon;
import whitelabeltest.player.weapons.Weapon;

public class Main extends ApplicationAdapter {
    private enum AppState { START, WEAPON_SELECT, OPTIONS, REPLAY_SELECT, PLAYING }

    private AppState state = AppState.START;
    private StartScreen startScreen;
    private WeaponSelectScreen weaponSelectScreen;
    private InputType pendingInputType;
    private OptionsScreen optionsScreen;
    private ReplaySelectScreen replaySelectScreen;
    // True only when the current PLAYING session was launched by picking a replay from the start
    // menu (as opposed to an ordinary ARCADE MODE run) - see transitionToReplayWatch(). Drives
    // whether finishing/backing out of watching returns to the start screen instead of leaving the
    // player in a live run they never asked to start - see render()'s PLAYING branch.
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
    private ExtendViewport viewport;

    private final float PLAY_AREA_WIDTH = 9f;
    private final float PLAY_AREA_HEIGHT = 12f;

    private final Vector3 scissorBL = new Vector3();
    private final Vector3 scissorTR = new Vector3();

    private boolean prevControllerBackDown;

    @Override
    public void create() {
        spriteBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        viewport = new ExtendViewport(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT);
        keyBindings = new KeyBindings();
        audioSettings = new AudioSettings();
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
    }

    @Override
    public void render() {
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
            // startScreen is still alive (and its music still playing) behind Options whenever
            // Options was reached from the start menu - see transitionToOptions() - so its volume
            // needs to keep tracking the sliders live here too, not just once startScreen.update()
            // resumes after backing out.
            if (startScreen != null) startScreen.applyMusicVolume();
            optionsScreen.render(delta);
            if (isControllerBackJustPressed()) {
                optionsScreen.handleControllerBackPressed();
            }
            if (optionsScreen.isBackRequested()) {
                transitionToStartFromOptions();
            }
        } else if (state == AppState.REPLAY_SELECT) {
            // startScreen is still alive behind this screen (see transitionToReplaySelect()), same
            // reasoning as the OPTIONS branch above.
            if (startScreen != null) startScreen.applyMusicVolume();
            ReplayData picked = replaySelectScreen.update(delta);
            drawReplaySelectScreen();
            if (picked != null) {
                transitionToReplayWatch(picked);
            } else if (replaySelectScreen.isBackRequested()) {
                transitionToStartFromReplaySelect();
            }
        } else {
            game.update(delta);
            drawGame();
            if (replayFromMenu) {
                boolean backPressed = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || isControllerBackJustPressed();
                // !isReplaying() covers the replay finishing on its own (GameController auto-stops
                // once frames run out); backPressed covers the player bailing out early.
                if (!game.isReplaying() || backPressed) {
                    transitionToStartFromReplayWatch();
                }
            } else if (game.isScheduleEndTriggered()) {
                // See SpawnScheduler.scheduleEndTime - only ever true for a schedule that has no
                // boss to drive the normal levelComplete flow (the tutorial), so this is a no-op for
                // every ordinary arcade run.
                transitionToStartFromTutorial();
            } else if (game.isQuitToMenuRequested()) {
                // QUIT confirmed from the game-over/level-complete prompt - see
                // GameController.handleGameOverInput()/handleLevelCompleteInput().
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

    /** TUTORIAL skips WeaponSelectScreen entirely - the WeaponLoadout passed to the constructor
     *  here is just a placeholder (same pattern as transitionToReplayWatch()'s), immediately
     *  overridden by reset()'s applyStartingLoadout() once it resolves the "tutorial" stage
     *  sequence's StartingLoadoutDefinition (assets/data/stage_sequences.json). */
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
        // startScreen is deliberately left alive (not disposed) here, same as transitionToOptions()
        // - so backing out via transitionToStartFromReplaySelect() resumes it exactly where it was
        // instead of needing to rebuild it from scratch.
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
        // Only now leave the start-menu flow for good - startScreen was kept alive through OPTIONS
        // and REPLAY_SELECT (see transitionToReplaySelect()), same as WeaponSelectScreen's own
        // disposal in transitionToWeaponSelect().
        if (startScreen != null) {
            startScreen.dispose();
            startScreen = null;
        }
        replayFromMenu = true;
        // The loadout/stage sequence passed here are placeholders - GameController.startReplay()
        // overwrites both from the recorded data before anything simulates.
        game = new GameController(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, keyBindings, audioSettings, WeaponLoadout.BASIC_THUNDERBOLT);
        game.setActiveInput(InputType.KEYBOARD);
        game.startReplay(data);
        ui = new UIManager(InputType.KEYBOARD);
        state = AppState.PLAYING;
    }

    /** Returns to the start screen after a menu-launched replay finishes or the player backs out
     *  early (see render()'s PLAYING branch) - rebuilds StartScreen from scratch since it was
     *  disposed back in transitionToReplayWatch(). No equivalent path exists for an ordinary ARCADE
     *  MODE run - restarting/quitting are handled entirely inside GameController for that case. */
    private void transitionToStartFromReplayWatch() {
        replayFromMenu = false;
        game.dispose();
        game = null;
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.START;
    }

    /** Returns to the start screen once the tutorial's own schedule reaches its scripted end (see
     *  GameController.isScheduleEndTriggered()) - the tutorial has no boss, so it never reaches the
     *  normal levelComplete flow other stages use to advance/restart; this is its equivalent. Mirrors
     *  transitionToStartFromReplayWatch() exactly, just triggered by a different condition. */
    private void transitionToStartFromTutorial() {
        game.dispose();
        game = null;
        if (ui != null) {
            ui.dispose();
            ui = null;
        }
        startScreen = new StartScreen(PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, audioSettings);
        state = AppState.START;
    }

    /** Returns to the start screen when QUIT is confirmed from the game-over/level-complete prompt
     *  (see GameController.isQuitToMenuRequested()) - previously this just called Gdx.app.exit() and
     *  closed the whole game; now it backs out to the main menu instead, same as every other way of
     *  leaving a run early. Mirrors transitionToStartFromTutorial() exactly. */
    private void transitionToStartFromGameOver() {
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

        ui.drawEnemyHealthBars(spriteBatch, game.getEntities().getEnemies());

        if (game.isDebugMode()) {
            ui.drawEnemyHealthDebug(spriteBatch, game.getEntities().getEnemies());
        }

        spriteBatch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        ui.drawHUD(spriteBatch, game.getScoreManager(), game.getEntities().getPlayer(), PLAY_AREA_HEIGHT, leftX, PLAY_AREA_WIDTH, panelWidth, game.getBombCooldownTimer(), game.getBombCooldownFraction());

        ui.drawTextCues(spriteBatch, game.getSpawnScheduleRealTime(), game.getTextCues(),
            game.isTextCuesRequireConfirm(), game.getTextCueConfirmKeyLabel());

        if (isGameOver) {
            ui.drawGameOver(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getGameOverTimer());
        } else if (game.isLevelComplete()) {
            ui.drawLevelComplete(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getScore(),
                game.getLevelCompleteBombBonus(), game.getLevelCompleteLivesMultiplier(),
                game.getEnemiesDestroyed(), game.getTotalEnemyCount(),
                game.getLevelCompleteTimeBonus(), game.getLevelCompleteBossFightSeconds(),
                game.getMaxChainCount(), game.getLevelCompleteRank(),
                game.hasNextStage(), game.getStageNumber());
        }

        if (game.isDebugMode() && game.isAudioMuted() && !game.isDebugMenuOpen()) {
            ui.drawDebugMuteIndicator(spriteBatch, leftX, PLAY_AREA_HEIGHT);
        }

        if (game.isDebugMode()) {
            ui.drawDebugFpsMonitor(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getCurrentFps(), game.getLowestFps(), game.getHighestFps());
            ui.drawDebugFpsHistogram(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getFpsHistory(), game.getHighestFps());
        }

        if (game.isDebugMode() && game.isDebugMenuOpen()) {
            if (game.isPatternPreviewActive()) {
                ui.drawPatternPreview(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getPatternPreviewer());
            } else if (game.isReplayBrowserActive()) {
                ui.drawReplayBrowser(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getReplayBrowser());
            } else {
                ui.drawDebugMenu(spriteBatch, PLAY_AREA_WIDTH, PLAY_AREA_HEIGHT, game.getSpawnScheduleTotalTime(),
                    game.getDebugMenuSeekTime(), game.getDebugMenuSelectedIndex(), game.getDebugSaveStates(),
                    game.getEntities().getPlayer(), game.isAudioMuted(), game.getDebugMenuStageIds(), game.getDebugMenuStageIndex());
            }
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

        if (em.getPlayer().isThunderboltBombActive()) {
            shapeRenderer.setColor(Color.VIOLET);
            shapeRenderer.circle(em.getPlayer().getHaloCenterX(), em.getPlayer().getHaloCenterY(), em.getPlayer().getThunderboltBlastRadius(), 24);
        }

        shapeRenderer.setColor(Color.RED);
        for (Enemy enemy : em.getEnemies()) {
            // Enemy.getRotationPivotX/Y() is always this rectangle's own center (see its own doc
            // comment - GenericEnemy's sprite always uses setOriginCenter()), so the origin offset
            // passed to this rotated overload is always exactly half its width/height.
            Rectangle r = enemy.getRectangle();
            shapeRenderer.rect(r.x, r.y, r.width / 2f, r.height / 2f, r.width, r.height, 1f, 1f, enemy.getRotation());
        }

        shapeRenderer.setColor(Color.ORANGE);
        for (EnemyBullet bullet : em.getEnemyBullets()) {
            Rectangle r = bullet.getRectangle();
            float rotation = bullet.getRotation();

            // getHitboxOffsetX/Y() is defined in the bullet's own unrotated frame - rotate it into
            // world space the same way CollisionManager.overlaps(Circle, EnemyBullet) does, so a
            // rotating bullet's hitbox offset stays attached to (and turns with) its sprite here too.
            float offsetX = bullet.getHitboxOffsetX();
            float offsetY = bullet.getHitboxOffsetY();
            float cosR = MathUtils.cosDeg(rotation);
            float sinR = MathUtils.sinDeg(rotation);
            float worldOffsetX = offsetX * cosR - offsetY * sinR;
            float worldOffsetY = offsetX * sinR + offsetY * cosR;

            // The actual hit-tested box - r scaled around its own center by getHitboxScale() and
            // then shifted by the (now world-space) offset. Reduces to r itself for
            // scale=1/offset=(0,0).
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
            // getRotation() is 0 for most ordinary bullets (a no-op rotation below); bullets that
            // visually turn to face their travel direction (see AimedEnemyBullet and friends) or a
            // beam like LaserBullet report their real angle, rotated around
            // getRotationPivotX()/Y() per EnemyBullet.getRotation()'s contract - which may or may
            // not be effWidth/2, 0 (that pair only happens to be right for a bottom-center pivot
            // like LaserBullet's, not a center pivot like AimedEnemyBullet's).
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
                // getRectangle() is the un-rotated shape pivoted at getRotationPivotX/Y(); rotate
                // it into place the same way the CollisionManager SAT test does.
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
        if (startScreen != null) startScreen.dispose();
        if (weaponSelectScreen != null) weaponSelectScreen.dispose();
        if (optionsScreen != null) optionsScreen.dispose();
        if (replaySelectScreen != null) replaySelectScreen.dispose();
        if (startScreenConfirmSound != null) startScreenConfirmSound.dispose();
        if (weaponSelectConfirmSound != null) weaponSelectConfirmSound.dispose();
        if (replaySelectConfirmSound != null) replaySelectConfirmSound.dispose();
        if (game != null) game.dispose();
        if (ui != null) ui.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }
}
