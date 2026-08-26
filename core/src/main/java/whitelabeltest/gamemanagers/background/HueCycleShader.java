package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Rotates the hue of whatever ScrollingBackground draws through it (its ordinary scrolling
 *  backgroundLayers, NOT a shaderBackground/backgroundVideo) - see
 *  StageDefinition.hueCycleBackground. Wraps a normal draw call with begin()/end() rather than
 *  implementing BackgroundShader: that interface's render() is for a full-screen procedural effect
 *  that ignores its input texture (see Stage2KaleidoscopeShader/TutorialBoxTunnelShader), whereas
 *  this needs to color-shift the stage's REAL background image as it's actually drawn.
 *
 *  The rotation completes exactly one full cycle - ending back at the image's native colors - over
 *  setPeriod()'s value, which ScrollingBackground sets to the stage's own
 *  SpawnScheduler.getBackgroundVideoTime(), so "cycles the hue and ends where it started right
 *  before the boss video" falls out automatically rather than needing its own separately-tuned
 *  duration that could drift out of sync with the actual cue. */
public class HueCycleShader implements Disposable {
    private final ShaderProgram shader;
    private float time;
    private float period = 60f;

    public HueCycleShader() {
        shader = ShaderLoader.compile("HueCycleShader", "tinted.vert", "hue_cycle.frag");
    }

    /** How many seconds one full hue rotation takes - see the class doc. Zero or negative is treated
     *  as "no cycling" (begin() always applies a shift of 0 in that case) rather than dividing by
     *  zero, so a stage that opts in without ever setting this (e.g. no backgroundVideoTime) just
     *  shows the image's native colors instead of a NaN-corrupted draw. */
    public void setPeriod(float period) {
        this.period = period;
    }

    public void update(float delta) {
        time += delta;
    }

    public void resetTime() {
        time = 0f;
    }

    /** Unlike the FBO-based background shaders (which have real accumulated pixel state that can't
     *  be cheaply fast-forwarded - see their own resetTime()-only seek handling), this shader is
     *  just time % period, so a debug/replay seek can jump straight to the correct hue instead of
     *  restarting the cycle from 0 - see ScrollingBackground.seekTo(). */
    public void setTime(float time) {
        this.time = time;
    }

    /** Swaps in this shader with the current hue shift and returns the batch's previous shader, so
     *  the caller can restore it (via end()) once it's done drawing with this one - same
     *  capture/restore pattern the FBO-based background shaders use around their own draws. */
    public ShaderProgram begin(SpriteBatch batch) {
        ShaderProgram previous = batch.getShader();
        float hueShift = period > 0f ? (time % period) / period : 0f;
        batch.setShader(shader);
        shader.setUniformi("u_texture", 0);
        shader.setUniformf("u_hueShift", hueShift);
        return previous;
    }

    public void end(SpriteBatch batch, ShaderProgram previous) {
        batch.setShader(previous);
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
