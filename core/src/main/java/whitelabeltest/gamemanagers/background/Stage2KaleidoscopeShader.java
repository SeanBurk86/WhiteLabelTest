package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Stage 2's "kaleidoscope" background (kaleidoscope_source.frag, one pass): a folded/mirrored
 *  fractal "phosphene" with a screen-blended, square-to-hex morphing lattice overlay, then a hard cut
 *  to a ray-marched "tentacles" tunnel a few units before the boss. Colors fade from monochrome to a
 *  cosine palette with stage progress. kaleidoscope_motion.frag and kaleidoscope_accumulate.frag in
 *  assets/shaders are unused leftovers of a removed transition effect. */
public class Stage2KaleidoscopeShader implements BackgroundShader {
    // Seconds before switching to tentacles when there's no camera distance to follow.
    public static final float DEFAULT_TRANSITION_TIME = 10f;
    // Seconds for the color fade when there's no camera distance to follow.
    public static final float DEFAULT_COLOR_FADE_TIME = 130f;

    private final ShaderProgram sourceShader;

    private float time;
    private float transitionTime = DEFAULT_TRANSITION_TIME;
    private float colorFadeTime = DEFAULT_COLOR_FADE_TIME;
    // Camera distance pushed each frame; NaN until then (falls back to the shader clock).
    private float stageDistance = Float.NaN;
    // Distances at which the color fade completes / the tentacles take over; <= 0 = use the clock.
    private float colorFadeDistance = -1f;
    private float tentacleSwitchDistance = -1f;
    // Ground scroll speed, so the lattice overlay drifts with grounded enemies.
    private float groundScrollSpeed;

    public Stage2KaleidoscopeShader() {
        sourceShader = ShaderLoader.compile("Stage2KaleidoscopeShader source pass", "background.vert", "kaleidoscope_source.frag");
    }

    /** Clock-based fallback: seconds before switching to tentacles. */
    public void setTransitionTime(float transitionTime) {
        this.transitionTime = transitionTime;
    }

    /** Clock-based fallback: seconds for the color fade (<= 0 = fully colored). */
    public void setColorFadeTime(float colorFadeTime) {
        this.colorFadeTime = colorFadeTime;
    }

    /** The camera distance, preferred over the shader clock because the clock restarts on every seek
     *  and would snap the look back to the start. */
    public void setStageDistance(float distance) {
        this.stageDistance = distance;
    }

    /** Distances for the color fade and the tentacle switch; <= 0 uses the clock for that one. */
    public void setDistances(float colorFadeDistance, float tentacleSwitchDistance) {
        this.colorFadeDistance = colorFadeDistance;
        this.tentacleSwitchDistance = tentacleSwitchDistance;
    }

    /** Current ground scroll (world units/sec, negative = down), including setSpeed scaling. */
    public void setGroundScrollSpeed(float groundScrollSpeed) {
        this.groundScrollSpeed = groundScrollSpeed;
    }

    @Override
    public void update(float delta) {
        time += delta;
    }

    @Override
    public void resetTime() {
        time = 0f;
    }

    /** 0 = monochrome, 1 = full color: by distance if available, else by time. */
    private float colorMix() {
        if (!Float.isNaN(stageDistance) && colorFadeDistance > 0f) return Math.max(0f, Math.min(1f, stageDistance / colorFadeDistance));
        if (colorFadeTime <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, time / colorFadeTime));
    }

    private boolean useTentacles() {
        if (!Float.isNaN(stageDistance) && tentacleSwitchDistance > 0f) return stageDistance >= tentacleSwitchDistance;
        return time >= transitionTime;
    }

    @Override
    public void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();

        batch.setShader(sourceShader);
        batch.setColor(Color.WHITE);
        sourceShader.setUniformf("u_time", time);
        sourceShader.setUniformf("u_resolution", worldWidth, worldHeight);
        sourceShader.setUniformf("u_tentacles", useTentacles() ? 1f : 0f);
        sourceShader.setUniformf("u_colorMix", colorMix());
        sourceShader.setUniformf("u_groundScrollSpeed", groundScrollSpeed);
        // quadTexture is only a dummy for the draw call; the shader is fully procedural.
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
    }

    @Override
    public void dispose() {
        sourceShader.dispose();
    }
}
