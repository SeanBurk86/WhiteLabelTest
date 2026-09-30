package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;

/** "Video feedback" overlay (StageDefinition.playerFeedbackBackground): the player and halo are
 *  redrawn every frame into a ping-pong accumulation buffer that slowly zooms out from the player,
 *  fades, drifts with the background scroll and hue-shifts each generation, leaving a trail of
 *  echoes. It's alpha-blended on top of whatever background was already drawn (the buffer's alpha
 *  decays with its color).
 *
 *  It runs after the background has finished drawing rather than nesting inside another FBO pass,
 *  because libGDX FrameBuffer binding doesn't stack. The scissor and viewport are saved and restored
 *  around the off-screen pass, and FBO textures are read Y-flipped. */
public class PlayerFeedbackShader implements Disposable {
    // > 1: last frame's content is sampled closer to the player, so echoes expand outward.
    private static final float ZOOM = 1.035f;
    // Per-generation fade of color and alpha; the trail lasts about 1/(1-DECAY) frames.
    private static final float DECAY = 0.975f;
    // Chromatic-aberration offset in texels on the echoes.
    private static final float CHROMA_TEXELS = 4f;
    // Exaggerates the real per-frame scroll so the trail visibly rides the background (still signed
    // and proportional to the stage's scroll speed).
    private static final float SCROLL_VISUAL_SCALE = 6f;
    // Hue rotation (fraction of a cycle) per generation, so older echoes are more shifted.
    private static final float HUE_STEP = 0.035f;
    // Minimum saturation on the trail, so pale sprites still show the hue shift.
    private static final float HUE_SATURATION_FLOOR = 0.75f;
    // The accumulation buffers start with undefined contents; skip reading them for a few frames.
    private static final int WARMUP_FRAMES = 3;

    private final ShaderProgram feedbackShader;

    // This frame's scroll in world units (converted to UV in renderAccumulatePass()).
    private float scrollDeltaWorld;
    private int warmupFramesRemaining = WARMUP_FRAMES;

    // Created on first render.
    private FrameBuffer accumA;
    private FrameBuffer accumB;
    private boolean accumAIsCurrent;

    // This frame's player and halo sprites (null until first set).
    private TextureRegion playerFrame;
    private float playerX, playerY, playerWidth, playerHeight;
    private TextureRegion haloFrame;
    private float haloX, haloY, haloWidth, haloHeight;

    private final IntBuffer viewportQuery = BufferUtils.newIntBuffer(4);

    public PlayerFeedbackShader() {
        feedbackShader = ShaderLoader.compile("PlayerFeedbackShader accumulate pass", "background.vert", "player_feedback.frag");
    }

    /** @param scrollSpeed the background's scroll speed (world units/sec; negative = downward). */
    public void update(float delta, float scrollSpeed) {
        scrollDeltaWorld = scrollSpeed * delta;
    }

    public void resetTime() {
        scrollDeltaWorld = 0f;
        warmupFramesRemaining = WARMUP_FRAMES;
        accumAIsCurrent = false;
        // Drop stale sprites from before the reset/seek.
        playerFrame = null;
        haloFrame = null;
    }

    public void updatePlayer(TextureRegion frame, float x, float y, float width, float height) {
        playerFrame = frame;
        playerX = x;
        playerY = y;
        playerWidth = width;
        playerHeight = height;
    }

    public void updateHalo(TextureRegion frame, float x, float y, float width, float height) {
        haloFrame = frame;
        haloX = x;
        haloY = y;
        haloWidth = width;
        haloHeight = height;
    }

    /** Updates the trail and draws it over the current target. quadTexture is only a dummy for the
     *  accumulate draw call. Expects and leaves the batch begun. */
    public void renderOverlay(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        prewarmFrameBuffers();

        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();
        Matrix4 previousProjection = batch.getProjectionMatrix().cpy();

        // Main.java's play-area scissor would clip the off-screen pass.
        boolean scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        if (scissorWasEnabled) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        // FrameBuffer.end() resets the viewport to the whole back buffer; save it to restore after.
        viewportQuery.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportQuery);
        int viewportX = viewportQuery.get(0);
        int viewportY = viewportQuery.get(1);
        int viewportW = viewportQuery.get(2);
        int viewportH = viewportQuery.get(3);

        batch.end();

        // Tight world-space projection so the pass fills the buffer edge to edge.
        Matrix4 tightProjection = new Matrix4().setToOrtho2D(0, 0, worldWidth, worldHeight);
        batch.setProjectionMatrix(tightProjection);

        renderAccumulatePass(batch, quadTexture, worldWidth, worldHeight);

        if (warmupFramesRemaining > 0) warmupFramesRemaining--;
        accumAIsCurrent = !accumAIsCurrent;
        batch.setProjectionMatrix(previousProjection);

        Gdx.gl.glViewport(viewportX, viewportY, viewportW, viewportH);
        if (scissorWasEnabled) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);

        // Plain alpha-blended blit; the buffer's own alpha lets the background show through.
        batch.begin();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        Texture result = currentAccum().getColorBufferTexture();
        batch.draw(result, 0, 0, worldWidth, worldHeight, 0, 0, result.getWidth(), result.getHeight(), false, true);
        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
        batch.end();

        // Leave the batch begun, as the caller handed it over.
        batch.begin();
    }

    private void prewarmFrameBuffers() {
        if (accumA != null) return;
        int canvasW, canvasH;
        if (Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)) {
            viewportQuery.clear();
            Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, viewportQuery);
            canvasW = Math.max(1, viewportQuery.get(2));
            canvasH = Math.max(1, viewportQuery.get(3));
        } else {
            canvasW = Math.max(1, Gdx.graphics.getBackBufferWidth());
            canvasH = Math.max(1, Gdx.graphics.getBackBufferHeight());
        }
        accumA = new FrameBuffer(Pixmap.Format.RGBA8888, canvasW, canvasH, false);
        accumB = new FrameBuffer(Pixmap.Format.RGBA8888, canvasW, canvasH, false);
    }

    private FrameBuffer currentAccum() { return accumAIsCurrent ? accumA : accumB; }
    private FrameBuffer previousAccum() { return accumAIsCurrent ? accumB : accumA; }

    private void renderAccumulatePass(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        Texture previous = previousAccum().getColorBufferTexture();

        FrameBuffer target = currentAccum();
        target.begin();

        previous.bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        batch.begin();
        batch.setShader(feedbackShader);
        batch.setColor(Color.WHITE);
        feedbackShader.setUniformf("u_resolution", (float) target.getWidth(), (float) target.getHeight());
        feedbackShader.setUniformf("u_center", playerCenterX() / worldWidth, playerCenterY() / worldHeight);
        feedbackShader.setUniformf("u_zoom", ZOOM);
        feedbackShader.setUniformf("u_scroll", 0f, SCROLL_VISUAL_SCALE * scrollDeltaWorld / worldHeight);
        feedbackShader.setUniformf("u_decay", DECAY);
        feedbackShader.setUniformf("u_chroma", CHROMA_TEXELS);
        feedbackShader.setUniformf("u_hueStep", HUE_STEP);
        feedbackShader.setUniformf("u_hueSaturationFloor", HUE_SATURATION_FLOOR);
        feedbackShader.setUniformf("u_warmup", warmupFramesRemaining > 0 ? 1f : 0f);
        feedbackShader.setUniformi("u_previous", 1);
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);
        batch.end();

        // The fresh halo then player on top, at their real positions and colors.
        if (haloFrame != null || playerFrame != null) {
            batch.begin();
            batch.setShader(null);
            batch.setColor(Color.WHITE);
            if (haloFrame != null) batch.draw(haloFrame, haloX, haloY, haloWidth, haloHeight);
            if (playerFrame != null) batch.draw(playerFrame, playerX, playerY, playerWidth, playerHeight);
            batch.end();
        }

        target.end();
    }

    private float playerCenterX() { return playerX + playerWidth / 2f; }
    private float playerCenterY() { return playerY + playerHeight / 2f; }

    @Override
    public void dispose() {
        feedbackShader.dispose();
        if (accumA != null) accumA.dispose();
        if (accumB != null) accumB.dispose();
    }
}
