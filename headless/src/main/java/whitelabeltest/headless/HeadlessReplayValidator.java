package whitelabeltest.headless;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import whitelabeltest.gamemanagers.GameController;
import whitelabeltest.gamemanagers.replay.ReplayData;
import whitelabeltest.gamemanagers.replay.ReplayResult;
import whitelabeltest.online.BuildFingerprint;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/** Re-simulates replays with no window, GPU or audio, so a server can check a submitted score.
 *
 *  start() launches one headless libGDX application for the process; simulate() runs a replay on
 *  its thread and blocks until the run ends. Replays run one at a time. The working directory
 *  must be the game's assets/ folder, and the assets must be the build the replay was recorded
 *  on (compare ReplayData.gameBuild / dataHash with GAME_BUILD and dataHash()). */
public final class HeadlessReplayValidator {
    // The play area every stage is authored against (Main.PLAY_AREA_WIDTH / PLAY_AREA_HEIGHT).
    public static final float WORLD_WIDTH = 9f;
    public static final float WORLD_HEIGHT = 12f;

    private static HeadlessApplication app;

    private HeadlessReplayValidator() {}

    /** Starts the headless application (no-op if already running). */
    public static synchronized void start() {
        if (app != null) return;
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        // Its own Preferences, so the player's real settings are never read or written.
        config.preferencesDirectory = ".whitelabeltest-headless-prefs/";
        config.updatesPerSecond = 200;
        CountDownLatch ready = new CountDownLatch(1);
        app = new HeadlessApplication(new ApplicationAdapter() {
            @Override
            public void create() {
                GL20 gl = NoOpGL.create();
                Gdx.gl = gl;
                Gdx.gl20 = gl;
                ready.countDown();
            }
        }, config);
        try {
            ready.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting the headless application", e);
        }
    }

    public static synchronized void stop() {
        if (app == null) return;
        app.exit();
        app = null;
    }

    /** True if the replay was recorded on this build's code and data, so simulate() can
     *  reproduce it. */
    public static boolean isSameBuild(ReplayData replay) {
        return BuildFingerprint.GAME_BUILD.equals(replay.gameBuild) && BuildFingerprint.dataHash().equals(replay.dataHash);
    }

    /** Replays `replay` to its end and returns the outcome. Blocks the caller. */
    public static ReplayResult simulate(ReplayData replay) {
        return run(replay, false);
    }

    /** As simulate(), but also calls GameController.draw() after every frame (onto the no-op GL).
     *  For self-tests: the result must equal simulate()'s, proving drawing doesn't affect the
     *  simulation and so a headless result is valid for a run played with graphics. */
    public static ReplayResult simulateWithDrawing(ReplayData replay) {
        return run(replay, true);
    }

    private static ReplayResult run(ReplayData replay, boolean draw) {
        start();
        CompletableFuture<ReplayResult> result = new CompletableFuture<>();
        Gdx.app.postRunnable(() -> {
            SpriteBatch batch = draw ? new SpriteBatch() : null;
            try {
                result.complete(GameController.simulateReplay(replay, WORLD_WIDTH, WORLD_HEIGHT, draw ? game -> {
                    batch.begin();
                    game.draw(batch);
                    batch.end();
                } : null));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            } finally {
                if (batch != null) batch.dispose();
            }
        });
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating a replay", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Replay simulation failed", cause);
        }
    }
}
