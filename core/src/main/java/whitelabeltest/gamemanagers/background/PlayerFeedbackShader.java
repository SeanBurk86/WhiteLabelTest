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

/** An analog "video feedback" OVERLAY - the classic look of pointing a camera at the monitor it's
 *  plugged into: the player's own sprite AND halo (see updatePlayer()/updateHalo()) endlessly
 *  re-drawn into a slowly zooming, fading accumulation buffer, so every past position/frame recedes
 *  outward from the player's current position as a trail of echoes of themselves. The trail also
 *  drifts downward each frame in step with the background's own scroll speed (see update()/SCROLL_
 *  VISUAL_SCALE/u_scroll in player_feedback.frag), so it reads as riding along with the scrolling
 *  backdrop rather than hovering in a fixed spot, and cycles hue a little further each generation it
 *  recirculates (u_hueStep), so older/farther-out echoes are visibly more rainbow-shifted than fresh
 *  ones - only the recirculating trail rainbow-cycles, the real player/halo drawn fresh each frame
 *  keep their true colors.
 *
 *  Unlike Stage2KaleidoscopeShader/TutorialBoxTunnelShader (BackgroundShader implementations that
 *  REPLACE a stage's background outright), this is composited ON TOP of whatever background content
 *  a stage already draws - ordinary scrolling backgroundLayers, a boss/background video frame, or
 *  even one of those other shaderBackground effects - see ScrollingBackground.drawPlayerFeedbackOverlay()/
 *  StageDefinition.playerFeedbackBackground, which is an independent opt-in (like hueCycleBackground),
 *  not a shaderBackground choice. The accumulation buffer's own alpha channel decays alongside its
 *  color each generation (see player_feedback.frag), so renderOverlay()'s final draw - a plain
 *  alpha-blended full-screen blit - lets whatever was drawn just before it show through in proportion
 *  to how faded each part of the trail is, rather than painting over it.
 *
 *  Deliberately does NOT capture/re-render whatever background content came before it into its own
 *  FrameBuffer first - only the persistent accum ping-pong buffers need to survive across frames, and
 *  the plain player sprite drawn fresh into them each frame is a normal draw call, not itself an FBO
 *  consumer - so unlike an attempt to nest this inside another shaderBackground's own FBO passes
 *  (which would break: libGDX FrameBuffer binding doesn't stack, so a nested begin()/end() leaves the
 *  outer capture pointing at nothing once the inner one unbinds), running this AFTER that content has
 *  already finished drawing (to whatever target - screen or an already-restored viewport) is both
 *  simpler and safe regardless of how that content was produced.
 *
 *  Same viewport/scissor/projection handling as Stage2KaleidoscopeShader's own render() - see its
 *  class doc - since this still runs its own tight-projection FBO pass for the accumulate step even
 *  though there's no base content to capture. Every FrameBuffer read is Y-flipped in-shader, and the
 *  final on-screen draw uses flipY=true, for the same reason: FBO color textures come out of the GPU
 *  bottom-row-first, unlike a normally-loaded Texture. */
public class PlayerFeedbackShader implements Disposable {
    // Zoom > 1 shrinks the sampled "rel" vector each frame, so content that was near the player's
    // position in the previous frame gets displayed further away this frame - the outward-expanding
    // "flying through a tunnel of yourself" look. See player_feedback.frag's own comment. Boosted from
    // an earlier, barely-noticeable 1.015 for a much more dramatic, faster-expanding tunnel.
    private static final float ZOOM = 1.035f;
    // Multiplicative fade applied to the warped previous frame each pass (color AND alpha together -
    // see the class doc's compositing note) - how many frames of trail survive before fading to
    // nothing (roughly 1/(1-DECAY) frames to fully vanish). Needs to be high enough that the trail
    // lives long enough for the downward scroll drift below to actually accumulate into something
    // visible - see SCROLL_VISUAL_SCALE's own doc. Boosted from an earlier 0.97 for a longer, more
    // visible stack of echoes.
    private static final float DECAY = 0.975f;
    // Per-channel radial sample offset, in texels, for the chromatic-aberration fringing on the
    // receding echoes - see player_feedback.frag. Boosted from an earlier 2.5 for a more pronounced
    // color-fringed look on the receding trail.
    private static final float CHROMA_TEXELS = 4f;
    // A background layer's real scrollSpeed (world units/second) divided by worldHeight (world
    // units, e.g. 12 - see PLAY_AREA_HEIGHT) is only a few percent of screen height per second - at
    // DECAY's ~1-second trail life, matching that literally reads as static, not "drifting". This
    // exaggerates the SAME real per-frame scroll delta (still proportional to and signed with the
    // stage's actual scrollSpeed, so a faster/slower/reversed background still drifts faster/slower/
    // reversed in step) up to something that actually reads as riding the scroll on screen.
    private static final float SCROLL_VISUAL_SCALE = 6f;
    // Hue-rotation fraction (of a full 0..1 cycle) applied EVERY generation - see player_feedback.frag.
    // Compounds each frame a given echo recirculates, so older/farther-out copies have visibly cycled
    // further through the rainbow than fresher ones. Boosted from an earlier 0.02 - together with
    // DECAY's now-longer trail life, this sweeps multiple full hue cycles by the time a copy fades out.
    private static final float HUE_STEP = 0.035f;
    // Floors HSV saturation on the recirculating trail before converting back to RGB - see
    // player_feedback.frag's own comment. Without this, a pale/near-white sprite (a common look for a
    // halo glow or sprite highlights) barely shows the hue rotation at all, since there's almost no
    // saturation for the rotated hue to express. Boosted from an earlier 0.55 for more vivid color.
    private static final float HUE_SATURATION_FLOOR = 0.75f;
    // Matches Stage2KaleidoscopeShader's own warm-up guard - accumA/accumB start out with undefined
    // GPU garbage, so the first few frames skip reading them entirely.
    private static final int WARMUP_FRAMES = 3;

    private final ShaderProgram feedbackShader;

    // This frame's downward scroll movement, in world units - set by update(), consumed by
    // renderAccumulatePass() (converted to uv space there, since worldHeight isn't known until
    // renderOverlay() is called). See update()'s own doc.
    private float scrollDeltaWorld;
    private int warmupFramesRemaining = WARMUP_FRAMES;

    // Lazily allocated on first renderOverlay() - see prewarmFrameBuffers().
    private FrameBuffer accumA;
    private FrameBuffer accumB;
    private boolean accumAIsCurrent;

    // This frame's player sprite/position - set by updatePlayer(), consumed (then left in place,
    // harmless to redraw) by renderOverlay(). Null until the first updatePlayer() call, e.g. before a
    // stage's player has spawned; renderOverlay() just skips the fresh-player draw until then.
    private TextureRegion playerFrame;
    private float playerX, playerY, playerWidth, playerHeight;
    // The player's halo, drawn UNDER the player sprite - same layering Player.draw() itself uses -
    // set by updateHalo(). Independently nullable from playerFrame (though in practice Player always
    // has some halo visual once it's spawned - see Player.getHaloFrame()).
    private TextureRegion haloFrame;
    private float haloX, haloY, haloWidth, haloHeight;

    private final IntBuffer viewportQuery = BufferUtils.newIntBuffer(4);

    public PlayerFeedbackShader() {
        feedbackShader = ShaderLoader.compile("PlayerFeedbackShader accumulate pass", "background.vert", "player_feedback.frag");
    }

    /** scrollSpeed is the same world-units/second value driving this stage's ordinary background
     *  scroll (see ScrollingBackground's own per-frame call site) - negative moves the background
     *  (and so the trail) downward, matching Layer.scrollSpeed's own sign convention. */
    public void update(float delta, float scrollSpeed) {
        scrollDeltaWorld = scrollSpeed * delta;
    }

    public void resetTime() {
        scrollDeltaWorld = 0f;
        warmupFramesRemaining = WARMUP_FRAMES;
        accumAIsCurrent = false;
        // Drop any stale sprite from before the reset/seek - see the fields' own doc.
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

    /** Draws this frame's feedback trail on top of whatever background content was just drawn - see
     *  the class doc. quadTexture is a harmless dummy (assets.pixelTexture) purely to trigger the
     *  accumulate pass's draw call; its actual pixel content is never read by feedbackShader, which
     *  samples u_previous instead. */
    public void renderOverlay(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        prewarmFrameBuffers();

        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();
        Matrix4 previousProjection = batch.getProjectionMatrix().cpy();

        // See Stage2KaleidoscopeShader's own scissor note - Main.java clips to the play-area rect in
        // real screen pixels for the whole outer game.draw() call this runs inside of, which would
        // wrongly clip these off-screen, differently-sized FBO passes.
        boolean scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        if (scissorWasEnabled) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        // FrameBuffer.end() always resets the GL viewport to the full back buffer - captured here and
        // restored after, same as Stage2KaleidoscopeShader, so the rest of this frame's draws (the
        // real player, enemies, bullets, UI, HUD) still use the game's real (narrower) play-area viewport.
        viewportQuery.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportQuery);
        int viewportX = viewportQuery.get(0);
        int viewportY = viewportQuery.get(1);
        int viewportW = viewportQuery.get(2);
        int viewportH = viewportQuery.get(3);

        batch.end();

        // Tight world-space projection so the FBO pass fills the buffer edge-to-edge instead of the
        // caller's wider ExtendViewport-extended projection - see Stage2KaleidoscopeShader's own note.
        Matrix4 tightProjection = new Matrix4().setToOrtho2D(0, 0, worldWidth, worldHeight);
        batch.setProjectionMatrix(tightProjection);

        renderAccumulatePass(batch, quadTexture, worldWidth, worldHeight);

        if (warmupFramesRemaining > 0) warmupFramesRemaining--;
        accumAIsCurrent = !accumAIsCurrent;
        batch.setProjectionMatrix(previousProjection);

        Gdx.gl.glViewport(viewportX, viewportY, viewportW, viewportH);
        if (scissorWasEnabled) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);

        // Plain alpha-blended blit on top of whatever's already at this target (screen, or whatever
        // viewport/framebuffer was active when the caller invoked us) - the accum buffer's own alpha
        // (fresh player = opaque, decaying trail = increasingly transparent - see the class doc) is
        // what makes the background underneath show through, not any special blend setup here.
        batch.begin();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        Texture result = currentAccum().getColorBufferTexture();
        batch.draw(result, 0, 0, worldWidth, worldHeight, 0, 0, result.getWidth(), result.getHeight(), false, true);
        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
        batch.end();

        // Leave the batch "began" - the caller (ScrollingBackground, itself called from within
        // Main.java's own spriteBatch.begin()/end()) expects it in the same state it was handed to us in.
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

        // The fresh player sprite (and its halo, drawn under it - same order Player.draw() itself
        // uses) layers on top, at its real world position, with a plain draw - no flip needed here
        // (only reading an FBO's texture back out needs that correction, not writing a normal draw
        // into one - see the class doc). Each is left out entirely until its first update*() call
        // (e.g. before this stage's player has spawned).
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
