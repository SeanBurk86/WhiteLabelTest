package whitelabeltest.gamemanagers.replay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import java.nio.IntBuffer;
import java.util.Random;
import whitelabeltest.gamemanagers.GameController;
import whitelabeltest.gamemanagers.spawning.EnemySpawnRegistry;

/** Ghost mode: a recorded run replayed alongside the live one, with only its player and shots
 *  drawn, faintly, under everything live.
 *
 *  The ghost is a second GameController (see GameController.createGhost()). The game keeps some
 *  state in statics (MathUtils.random, EnemySpawnRegistry), so the ghost gets its own of each,
 *  swapped in around every call into it; that keeps both runs exactly as they'd be alone.
 *
 *  The two runs are lined up stage by stage on gameplay time (interstitials, stage clears and the
 *  like don't count). A ghost that clears a stage first waits at its stage clear; one that falls a
 *  stage behind catches up hidden, at up to CATCH_UP_FRAMES a frame. When the live run restarts,
 *  the ghost starts over. */
public class GhostRun implements Disposable {
    /** How visible the ghost is. */
    public static final float ALPHA = 0.35f;
    // Most ghost frames simulated per live frame while it is a stage behind (hidden).
    private static final int CATCH_UP_FRAMES = 120;
    // Most per live frame while in step, so a slow live frame (a load hitch) is made up quickly.
    private static final int IN_STEP_FRAMES = 8;

    private final ReplayData replay;
    private final GameController live;
    private final float worldWidth, worldHeight;
    private final Random random = new RandomXS128();
    // The ghost's spawn registry target while it isn't swapped in (null before it's created).
    private EnemySpawnRegistry.State registryState;
    private GameController ghost;

    private int liveRunNumber;
    private int liveStage;
    private float liveStageTime, ghostStageTime;

    // Offscreen layer, so the ghost fades as one image whatever blending its sprites use.
    private FrameBuffer frameBuffer;
    private TextureRegion frameRegion;
    private final Matrix4 layerProjection = new Matrix4();
    private final Matrix4 savedProjection = new Matrix4();
    private final IntBuffer glBox = BufferUtils.newIntBuffer(16);

    public GhostRun(ReplayData replay, GameController live, float worldWidth, float worldHeight) {
        this.replay = replay;
        this.live = live;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        layerProjection.setToOrtho2D(0f, 0f, worldWidth, worldHeight);
        start();
    }

    private void start() {
        if (ghost != null) ghost.dispose();
        registryState = null;
        inGhostContext(() -> ghost = GameController.createGhost(replay, worldWidth, worldHeight, live.getAssets()));
        liveRunNumber = live.getRunNumber();
        liveStage = live.getStageNumber();
        liveStageTime = 0f;
        ghostStageTime = 0f;
    }

    /** Call after the live run's update. */
    public void update(float delta) {
        if (live.getRunNumber() != liveRunNumber) start();
        int stage = live.getStageNumber();
        if (stage != liveStage) {
            liveStage = stage;
            liveStageTime = 0f;
        } else if (live.isInGameplay()) {
            liveStageTime += delta;
        }
        if (!ghost.isGhostFinished()) inGhostContext(this::step);
    }

    private void step() {
        int budget = ghost.getStageNumber() < liveStage ? CATCH_UP_FRAMES : IN_STEP_FRAMES;
        for (int i = 0; i < budget && !ghost.isGhostFinished(); i++) {
            int ghostStage = ghost.getStageNumber();
            if (ghostStage > liveStage) return;
            if (ghostStage == liveStage) {
                if (ghost.isLevelComplete()) return;
                if (ghost.isInGameplay() && ghostStageTime >= liveStageTime) return;
            }
            float frameDelta = ghost.peekReplayDelta();
            boolean wasInGameplay = ghost.isInGameplay();
            ghost.update(frameDelta);
            if (ghost.getStageNumber() != ghostStage) ghostStageTime = 0f;
            else if (wasInGameplay) ghostStageTime += frameDelta;
        }
    }

    /** Runs r with the ghost's RNG and spawn registry in place of the live run's. */
    private void inGhostContext(Runnable r) {
        Random liveRandom = MathUtils.random;
        EnemySpawnRegistry.State liveRegistry = EnemySpawnRegistry.getState();
        MathUtils.random = random;
        if (registryState != null) EnemySpawnRegistry.setState(registryState);
        try {
            r.run();
        } finally {
            registryState = EnemySpawnRegistry.getState();
            MathUtils.random = liveRandom;
            EnemySpawnRegistry.setState(liveRegistry);
        }
    }

    /** Shown while it plays the same stage as the live run, in gameplay. */
    private boolean isVisible() {
        return !ghost.isGhostFinished() && ghost.getStageNumber() == liveStage
            && ghost.isInGameplay() && live.isInGameplay();
    }

    /** Draws the ghost faintly over the play area (0..worldWidth, 0..worldHeight in batch units).
     *  The batch must be drawing, with the play area's scissor box set (sizes the layer). */
    public void draw(SpriteBatch batch) {
        if (!isVisible()) return;
        batch.flush();

        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, glBox);
        int width = glBox.get(2), height = glBox.get(3);
        if (width <= 0 || height <= 0) return;
        ensureFrameBuffer(width, height);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, glBox);
        int vx = glBox.get(0), vy = glBox.get(1), vw = glBox.get(2), vh = glBox.get(3);
        // FrameBuffer.end() always returns to the screen, so restore whatever target was bound.
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, glBox);
        int previousTarget = glBox.get(0);
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        savedProjection.set(batch.getProjectionMatrix());

        if (scissor) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        frameBuffer.bind();
        Gdx.gl.glViewport(0, 0, width, height);
        ScreenUtils.clear(0f, 0f, 0f, 0f);
        batch.setProjectionMatrix(layerProjection);
        // Accumulate alpha properly (plain alpha blending squares it on an empty layer, which all
        // but erases soft glows), so the layer is premultiplied.
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // Drawing doesn't touch gameplay state, so no context swap.
        ghost.drawGhost(batch);
        batch.flush();
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previousTarget);
        Gdx.gl.glViewport(vx, vy, vw, vh);
        if (scissor) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);

        batch.setProjectionMatrix(savedProjection);
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(ALPHA, ALPHA, ALPHA, ALPHA);
        batch.draw(frameRegion, 0f, 0f, worldWidth, worldHeight);
        batch.flush();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(Color.WHITE);
    }

    private void ensureFrameBuffer(int width, int height) {
        if (frameBuffer != null && frameBuffer.getWidth() == width && frameBuffer.getHeight() == height) return;
        if (frameBuffer != null) frameBuffer.dispose();
        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        frameRegion = new TextureRegion(frameBuffer.getColorBufferTexture());
        frameRegion.flip(false, true);
    }

    @Override
    public void dispose() {
        if (ghost != null) ghost.dispose();
        if (frameBuffer != null) frameBuffer.dispose();
    }
}
