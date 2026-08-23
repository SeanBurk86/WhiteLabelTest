package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.IntBuffer;

/** Stage 2's background - plays the folded/mirrored fractal kaleidoscope effect ("phosphene": UV
 *  coordinates repeatedly reflected/rotated, colorized with a cosine palette) until u_btime seconds
 *  in, then switches to a raymarched "tentacles" tunnel for the rest of the stage. The switchover
 *  itself is a datamoshed smear - the source content jumps instantly, but the on-screen image doesn't:
 *  a block-matching motion estimator (ported from https://www.shadertoy.com/view/scd3Rs, the
 *  standard "P-frame with no I-frame refresh" datamosh technique) keeps warping the accumulated
 *  (mostly still phosphene) image by the REAL motion of the incoming content, so the old image
 *  visibly gets dragged/distorted as if the new scene's motion is pulling it apart, rather than a
 *  plain cross-fade blur.
 *
 *  The motion estimator compares each frame's source render against the PREVIOUS frame's source
 *  render (sourceA/sourceB, ping-ponged - see currentSource()/previousSource()) - i.e. real temporal
 *  motion of whichever content is currently playing (phosphene's own slow rotational drift before the
 *  cut, the tentacles tunnel's own flow after it) - NOT against the accumulated/warped buffer from
 *  the accumulate pass. An earlier version of this compared against the accumulated buffer instead,
 *  which - right at the cut - meant comparing brand-new tentacles against a still-phosphene-ish
 *  buffer: two unrelated images with no real correspondence, so the "motion" it found was
 *  meaningless noise (this was the cause of a "weird in-between texture" bug report). Comparing
 *  same-content frames instead gives a coherent motion field to warp the smear buffer by, which is
 *  what actually reads as the old image being dragged/distorted rather than garbled.
 *
 *  The accumulate blend weight (see u_decay - computed fresh each frame in render(), NOT the DECAY
 *  constant directly) ramps from DECAY down to 0 over the course of DATAMOSH_TAIL after the cut,
 *  rather than staying constant for the whole window. A constant decay - what an earlier version of
 *  this did - keeps blending old-warped with new-fresh content for as long as the window runs, which
 *  for a continuously-animating source (the tentacles tunnel never stops moving) reads as a sustained
 *  blur that just gets longer with a longer DATAMOSH_TAIL rather than resolving into a sharp final
 *  image ("the long tail just extends the blur" bug report). Ramping to 0 means the smear strength is
 *  highest right at the cut and tapers off, so by the end of the window the image is fully sharp
 *  fresh content again regardless of how long DATAMOSH_TAIL is.
 *
 *  Content shaders (VERTEX_SHADER/SOURCE_FRAGMENT_SHADER) are ported the same way as
 *  TutorialBoxTunnelShader (iTime/iResolution/fragCoord -> u_time/u_resolution/v_texCoords*
 *  u_resolution). The datamosh pipeline itself (MOTION_FRAGMENT_SHADER/ACCUMULATE_FRAGMENT_SHADER)
 *  is a from-scratch multi-pass port of the guide's 3-buffer Shadertoy pipeline (Buf A = motion
 *  estimation, Buf B = plain source passthrough, Buf C = warp-and-blend accumulator) onto real
 *  libGDX FrameBuffers, since libGDX/SpriteBatch has no equivalent of Shadertoy's implicit
 *  multi-buffer/multi-channel wiring:
 *   - "Buf B" doesn't need its own pass here - the source shader renders straight into sourceA/sourceB.
 *   - "Buf A" (motion estimation) here gets its OWN dedicated small FrameBuffer sized to
 *     realResolution/BLOCKDIM, one pixel per block, rather than computing into an unused corner of
 *     a full-size buffer the way the guide's version does (it has to, since Shadertoy buffers are
 *     always screen-sized) - same algorithm, less wasted GPU work.
 *   - "Buf C" (warp + blend) ping-pongs between two full-res FrameBuffers (accumA/accumB) since it
 *     feeds its own previous output back into itself every frame.
 *  BLOCKDIM/ITERS/STRIDE/DECAY below are this port's own tuning - the guide's Common tab (where
 *  Shadertoy defines them) wasn't available to port verbatim from.
 *
 *  Every FrameBuffer read is Y-flipped in-shader (see the "flipped" vec2 in the motion/accumulate
 *  sources and the final on-screen draw's flipY=true) - FrameBuffer color textures come out of the
 *  GPU bottom-row-first, unlike a normal loaded Texture (which libGDX flips once on load), so
 *  reading one "normally" renders upside down; every consumer of an FBO texture here corrects for
 *  it at the point of sampling instead. The whole pipeline only runs for a short window around
 *  u_btime (see DATAMOSH_LEAD_IN/DATAMOSH_TAIL) - block matching is expensive: up to 1 + 8*ITERS
 *  candidates per motion-buffer pixel, each sampling (BLOCKDIM/BLOCK_SAMPLE_STRIDE)^2 texel pairs
 *  (see BLOCK_SAMPLE_STRIDE's own doc for why it's not the full BLOCKDIM^2) - and
 *  outside the transition there's nothing to datamosh anyway (a single unchanging scene has ~zero
 *  motion to estimate), so the plain single-pass content shader renders directly the rest of the
 *  time. Also temporarily disables GL_SCISSOR_TEST around the FBO passes - see render()'s
 *  scissorWasEnabled handling - since Main.java has it enabled (clipped to the play-area rect, in
 *  real screen pixels) for its whole outer game.draw() call, which would otherwise clip these
 *  differently-sized off-screen buffers. */
public class Stage2KaleidoscopeShader implements BackgroundShader {
    // Matches the guide's own "#define BTIME 10.0" - used whenever a stage's schedule doesn't set
    // SpawnScheduler.getKaleidoscopeTransitionTime() (see ScheduleFile.kaleidoscopeTransitionTime),
    // so this shader still looks right even without one.
    public static final float DEFAULT_TRANSITION_TIME = 10f;

    // Side length (in blocks) of the motion buffer / the search grid each blockDist() call scores -
    // see the class doc's "Buf A" note. Also the world-space search step size, in source texels.
    private static final int BLOCK_DIM = 16;
    // blockDist() only samples every BLOCK_SAMPLE_STRIDE-th texel within each BLOCK_DIM x BLOCK_DIM
    // block instead of every one - a (BLOCK_DIM/BLOCK_SAMPLE_STRIDE)^2 : BLOCK_DIM^2 reduction in its
    // own inner-loop cost (4x at the current values) for a sparser, still-representative estimate of
    // how well two blocks match - see the class doc's performance note, this is the single biggest
    // lever on the motion pass's cost since it multiplies through every candidate direction.
    private static final int BLOCK_SAMPLE_STRIDE = 2;
    // How many steps outward, in each of the 8 compass directions, the motion search tries. Total
    // blockDist() calls per motion-buffer pixel is 1 + 8*ITERS - this is the other big cost lever
    // (linear, unlike BLOCK_SAMPLE_STRIDE's quadratic one) - see the class doc's performance note.
    private static final int ITERS = 4;
    // Texels per search step - together with ITERS, caps the search radius at ITERS*STRIDE texels.
    private static final int STRIDE = 2;
    // The STARTING blend weight - see the class doc's u_decay note. render() ramps the actual
    // per-frame u_decay down from this to 0 over DATAMOSH_TAIL, so this is "how strong the smear is
    // right at the cut", not a constant applied for the whole window.
    private static final float DECAY = 0.95f;
    // Matches the guide's "if (iFrame < 5)" warm-up guard - skips the feedback read for this many
    // frames after (re-)entering the transition window, since accumA/accumB start out with
    // undefined content.
    private static final int WARMUP_FRAMES = 5;
    // The datamosh pipeline is only active for [btime - LEAD_IN, btime + TAIL] of u_time - see the
    // class doc's performance note. LEAD_IN just needs to cover WARMUP_FRAMES before the cut (the
    // decay=0 lead-in path in render() means nothing actually smears before the cut regardless of
    // how long this is - see the u_decay note above). TAIL is how long the post-cut smear-to-clean
    // ramp (see render()) takes to fully resolve - this IS the visible transition duration.
    private static final float DATAMOSH_LEAD_IN = 0.4f;
    private static final float DATAMOSH_TAIL = 4.0f;
    // How long u_decay takes to ramp UP from 0 to DECAY right at the cut - see render()'s decay
    // envelope. Without this, decay jumps from 0 to DECAY in a single frame the instant the source
    // content cuts, and that first frame's motion estimate is comparing two genuinely different
    // images (last frame's phosphene vs. this frame's tentacles) with nowhere near enough real
    // correspondence to produce a clean motion field - applied at full (0.95) strength in one frame,
    // that produced a sharp jagged/moire pop (confirmed by capturing actual screen output frame-by-
    // frame across the cut: frame 60 clean, frame 61 immediately distorted) - a "flicker right before
    // the transition" bug report. Ramping up over a few frames instead means that first noisy estimate
    // only ever gets blended in a little, same fix as the DECAY_MAX-during-LEAD_IN bug before this.
    private static final float DATAMOSH_ATTACK = 0.15f;

    private final ShaderProgram sourceShader;
    private final ShaderProgram motionShader;
    private final ShaderProgram accumulateShader;

    private float time;
    private float transitionTime = DEFAULT_TRANSITION_TIME;

    // Lazily allocated the first time the transition window is entered (see render()) - most of a
    // stage 2 playthrough never touches these at all.
    private FrameBuffer sourceA;
    private FrameBuffer sourceB;
    private FrameBuffer motionBuffer;
    private FrameBuffer accumA;
    private FrameBuffer accumB;
    // True while sourceA is THIS frame's source render and sourceB is last frame's - flips every
    // datamosh frame. See currentSource()/previousSource() - the motion pass compares these two
    // (true temporal motion of whichever content is playing), not the accumulated buffer.
    private boolean sourceAIsCurrent;
    // True while accumA is the "just-written, now current" buffer and accumB is last frame's -
    // flips every datamosh frame. See currentAccum()/previousAccum().
    private boolean accumAIsCurrent;
    private int warmupFramesRemaining = WARMUP_FRAMES;
    // Reused every datamosh frame to capture/restore the GL viewport around the FBO passes below -
    // see render()'s viewport handling.
    private final IntBuffer viewportQuery = BufferUtils.newIntBuffer(4);

    public Stage2KaleidoscopeShader() {
        sourceShader = ShaderLoader.compile("Stage2KaleidoscopeShader source pass", "background.vert", "kaleidoscope_source.frag");
        // BLOCK_DIM/BLOCK_SAMPLE_STRIDE/ITERS/STRIDE are substituted into the GLSL here (rather than
        // hardcoded in kaleidoscope_motion.frag) since BLOCK_DIM also drives motionBuffer's own pixel
        // dimensions below - keeping the Java constants as the single source of truth.
        String motionSource = ShaderLoader.read("kaleidoscope_motion.frag")
            .replace("${BLOCK_DIM}", String.valueOf(BLOCK_DIM))
            .replace("${BLOCK_SAMPLE_STRIDE}", String.valueOf(BLOCK_SAMPLE_STRIDE))
            .replace("${ITERS}", String.valueOf(ITERS))
            .replace("${STRIDE}", String.valueOf(STRIDE));
        motionShader = ShaderLoader.compileSource("Stage2KaleidoscopeShader motion pass", ShaderLoader.read("background.vert"), motionSource);
        accumulateShader = ShaderLoader.compile("Stage2KaleidoscopeShader accumulate pass", "background.vert", "kaleidoscope_accumulate.frag");
    }

    /** How many seconds of u_time the phosphene kaleidoscope plays before this switches over to
     *  the tentacles tunnel - see ScrollingBackground.setKaleidoscopeTransitionTime(), which feeds
     *  this from the current stage's own SpawnScheduler.getKaleidoscopeTransitionTime(). */
    public void setTransitionTime(float transitionTime) {
        this.transitionTime = transitionTime;
    }

    @Override
    public void update(float delta) {
        time += delta;
    }

    @Override
    public void resetTime() {
        time = 0f;
        warmupFramesRemaining = WARMUP_FRAMES;
        accumAIsCurrent = false;
        sourceAIsCurrent = false;
    }

    @Override
    public void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        // Allocating 5 GPU-side FrameBuffers is real work - doing it lazily on first entry into the
        // transition window (what an earlier version of this did) caused a one-time hitch/stutter at
        // exactly the moment the transition should start looking smooth. Called unconditionally every
        // frame, but it's a no-op after the first real allocation (see ensureFrameBuffers's own
        // guard), so this just moves that one-time cost to the first frame this shader ever renders
        // (well before any transition is near) instead.
        prewarmFrameBuffers();

        boolean inTransitionWindow = time >= transitionTime - DATAMOSH_LEAD_IN && time <= transitionTime + DATAMOSH_TAIL;
        if (!inTransitionWindow) {
            // Re-arm so a debug/practice-checkpoint seek back to before the window still gets a
            // clean warm-up instead of resuming mid-feedback with stale ping-pong content.
            warmupFramesRemaining = WARMUP_FRAMES;
            renderDirect(batch, quadTexture, worldWidth, worldHeight);
            return;
        }

        // Captured before any of our own passes touch the batch's shader/color - renderSourcePass/
        // renderMotionPass/renderAccumulatePass each set their own shader and never restore it, so
        // capturing this AFTER them (as an earlier version of this method did) would save the last
        // pass's shader (accumulateShader) instead of the caller's, and the trailing batch.begin()
        // below would then leave that shader active for every sprite/UI draw for the rest of the
        // frame - exactly the "sprites and UI not rendering" bug this fixed.
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();
        Matrix4 previousProjection = batch.getProjectionMatrix().cpy();

        // Main.java has GL_SCISSOR_TEST enabled, clipped to the play-area rect in real screen pixels
        // (there are HUD side panels outside it - see the ExtendViewport/leftX handling in
        // Main.drawGame()), for the whole outer game.draw() call this runs inside of. That rect would
        // clip the FBO passes below too (they have nothing to do with screen-space coordinates) -
        // disabled for them, restored after.
        boolean scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        if (scissorWasEnabled) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        // FrameBuffer.end() (used by every pass below) unconditionally resets the GL viewport to
        // the full back buffer - not whatever viewport was active before begin() - which is wrong
        // here: the game normally renders through a narrower play-area viewport (there's a side HUD
        // panel outside it). Left uncorrected, every draw call for the rest of THIS frame - enemies,
        // bullets, UI, HUD - would use that wrong (full-window) viewport too, not just our own final
        // blit. Captured once up front and restored after the last FrameBuffer.end() below.
        viewportQuery.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportQuery);
        int viewportX = viewportQuery.get(0);
        int viewportY = viewportQuery.get(1);
        int viewportW = viewportQuery.get(2);
        int viewportH = viewportQuery.get(3);

        // Flush whatever the caller had pending before handing the batch off to our own
        // begin/end pairs below - see the class doc; submission order (not when it's flushed)
        // is what determines draw order, so this is free beyond a few extra draw calls.
        batch.end();

        // The FBO passes below draw with a TIGHT projection (world (0,0)-(worldWidth,worldHeight)
        // mapped straight to the full -1..1 NDC range) rather than the caller's own projection matrix
        // (which maps a much WIDER, ExtendViewport-extended world - the play area plus its side HUD
        // panels - onto the screen, so the play-area quad only ever covers a fraction of it). Content
        // rendered with the caller's projection into these buffers would leave large blank margins on
        // both sides of the real content - and since sourceA/sourceB/motionBuffer/accumA/accumB are
        // full of "real content vs. blank margin" hard edges, block matching would keep finding
        // spuriously "better" matches by shifting blocks near those edges away from them, which -
        // compounded every frame through the warp-feedback loop - visibly eroded the image inward
        // from both sides over the course of the transition (confirmed by dumping accum-buffer PNGs
        // with the old wide-projection version and inspecting them directly). Sizing the buffers to
        // the play area's own pixel footprint (see prewarmFrameBuffers()) AND using this tight projection
        // together mean the real content fills every one of these buffers edge-to-edge, with no blank
        // margin anywhere for the matcher to be confused by.
        Matrix4 tightProjection = new Matrix4().setToOrtho2D(0, 0, worldWidth, worldHeight);
        batch.setProjectionMatrix(tightProjection);
        renderSourcePass(batch, quadTexture, worldWidth, worldHeight);
        renderMotionPass(batch, worldWidth, worldHeight);
        // An attack-decay envelope: zero (no warp - same as the warm-up branch) until the actual cut,
        // then ramps UP from 0 to DECAY over DATAMOSH_ATTACK, then back DOWN from DECAY to 0 over the
        // rest of DATAMOSH_TAIL - see the class doc's u_decay note and DATAMOSH_ATTACK's own doc for
        // why BOTH ends of this need to be ramps rather than jumps: a sudden jump to full smear
        // strength - what an earlier version of this did at both the LEAD_IN boundary and the cut
        // itself - blends in that moment's motion estimate (noisy right when content is genuinely
        // changing) at full strength in one frame, which on phosphene's fine-line pattern reads as a
        // sharp jagged/moire pop rather than a smooth onset.
        float decay;
        if (time < transitionTime) {
            decay = 0f;
        } else {
            float sinceCut = time - transitionTime;
            if (sinceCut < DATAMOSH_ATTACK) {
                float attackProgress = clamp(sinceCut / DATAMOSH_ATTACK, 0f, 1f);
                float eased = attackProgress * attackProgress * (3f - 2f * attackProgress);
                decay = DECAY * eased;
            } else {
                float rampProgress = clamp((sinceCut - DATAMOSH_ATTACK) / (DATAMOSH_TAIL - DATAMOSH_ATTACK), 0f, 1f);
                float eased = rampProgress * rampProgress * (3f - 2f * rampProgress);
                decay = DECAY * (1f - eased);
            }
        }
        renderAccumulatePass(batch, worldWidth, worldHeight, decay);
        if (warmupFramesRemaining > 0) warmupFramesRemaining--;
        accumAIsCurrent = !accumAIsCurrent;
        sourceAIsCurrent = !sourceAIsCurrent;
        batch.setProjectionMatrix(previousProjection);

        Gdx.gl.glViewport(viewportX, viewportY, viewportW, viewportH);
        if (scissorWasEnabled) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);

        // The accum buffer now holds exactly the play-area content, edge-to-edge, at the play area's
        // own pixel resolution - the same plain world-rect draw renderDirect() uses below is correct
        // again (no pixel-perfect viewport-sized blit needed - that was only ever working around the
        // wide-canvas blank-margin problem above).
        batch.begin();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        Texture result = currentAccum().getColorBufferTexture();
        batch.draw(result, 0, 0, worldWidth, worldHeight, 0, 0, result.getWidth(), result.getHeight(), false, true);
        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
        batch.end();

        // Leave the batch "began" - the caller (ScrollingBackground.draw(), itself called from
        // within Main.java's own spriteBatch.begin()/end()) expects it in the same state it was
        // handed to us in.
        batch.begin();
    }

    private void renderDirect(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();

        batch.setShader(sourceShader);
        batch.setColor(Color.WHITE);
        sourceShader.setUniformf("u_time", time);
        sourceShader.setUniformf("u_resolution", worldWidth, worldHeight);
        sourceShader.setUniformf("u_btime", transitionTime);
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    // Safe to call every frame - see render()'s call site. Uses the same play-area pixel footprint
    // (GL_SCISSOR_BOX, set by Main.java for the whole outer game.draw() call this runs inside of) the
    // FBO passes themselves render into, falling back to the full backbuffer if scissor isn't enabled
    // in whatever context is calling this (e.g. a debug preview screen).
    private void prewarmFrameBuffers() {
        if (sourceA != null) return;
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
        ensureFrameBuffers(canvasW, canvasH);
    }

    private void ensureFrameBuffers(int w, int h) {
        if (sourceA != null) return;
        sourceA = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        sourceB = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        motionBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, Math.max(1, w / BLOCK_DIM), Math.max(1, h / BLOCK_DIM), false);
        accumA = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        accumB = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
    }

    private FrameBuffer currentSource() { return sourceAIsCurrent ? sourceA : sourceB; }
    private FrameBuffer previousSource() { return sourceAIsCurrent ? sourceB : sourceA; }
    private FrameBuffer currentAccum() { return accumAIsCurrent ? accumA : accumB; }
    private FrameBuffer previousAccum() { return accumAIsCurrent ? accumB : accumA; }

    private void renderSourcePass(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        FrameBuffer target = currentSource();
        target.begin();
        batch.begin();
        batch.setShader(sourceShader);
        batch.setColor(Color.WHITE);
        sourceShader.setUniformf("u_time", time);
        sourceShader.setUniformf("u_resolution", (float) target.getWidth(), (float) target.getHeight());
        sourceShader.setUniformf("u_btime", transitionTime);
        // quadTexture (a harmless dummy - assets.pixelTexture, see the single-pass shaders' own
        // render()) purely triggers the draw call; its actual pixel content is never read by
        // sourceShader, which computes everything procedurally. Reading the target buffer's own
        // texture here instead - the bug this replaced - would be a read/write hazard: it's the
        // active render target for this exact draw.
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);
        batch.end();
        target.end();
    }

    private void renderMotionPass(SpriteBatch batch, float worldWidth, float worldHeight) {
        Texture channel1 = previousSource().getColorBufferTexture();
        channel1.bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        motionBuffer.begin();
        batch.begin();
        batch.setShader(motionShader);
        batch.setColor(Color.WHITE);
        motionShader.setUniformf("u_resolution", (float) currentSource().getWidth(), (float) currentSource().getHeight());
        motionShader.setUniformi("u_channel1", 1);
        // u_channel0 is bound to unit 0 by this draw() call itself, matching how the SpriteBatch
        // normally binds its primary texture.
        motionShader.setUniformi("u_channel0", 0);
        batch.draw(currentSource().getColorBufferTexture(), 0, 0, worldWidth, worldHeight);
        batch.end();
        motionBuffer.end();
    }

    private void renderAccumulatePass(SpriteBatch batch, float worldWidth, float worldHeight, float decay) {
        Texture previous = previousAccum().getColorBufferTexture();
        Texture source = currentSource().getColorBufferTexture();
        previous.bind(1);
        source.bind(2);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        currentAccum().begin();
        batch.begin();
        batch.setShader(accumulateShader);
        batch.setColor(Color.WHITE);
        accumulateShader.setUniformf("u_decay", decay);
        accumulateShader.setUniformf("u_warmup", warmupFramesRemaining > 0 ? 1f : 0f);
        accumulateShader.setUniformi("u_previous", 1);
        accumulateShader.setUniformi("u_source", 2);
        accumulateShader.setUniformi("u_motion", 0);
        batch.draw(motionBuffer.getColorBufferTexture(), 0, 0, worldWidth, worldHeight);
        batch.end();
        currentAccum().end();
    }

    @Override
    public void dispose() {
        sourceShader.dispose();
        motionShader.dispose();
        accumulateShader.dispose();
        if (sourceA != null) sourceA.dispose();
        if (sourceB != null) sourceB.dispose();
        if (motionBuffer != null) motionBuffer.dispose();
        if (accumA != null) accumA.dispose();
        if (accumB != null) accumB.dispose();
    }
}
