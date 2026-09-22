package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Stage 2's background - plays the folded/mirrored fractal kaleidoscope effect ("phosphene": UV
 *  coordinates repeatedly reflected/rotated, colorized with a cosine palette) until the switch point
 *  (see useTentacles() - a few units before the boss, by camera distance), then switches to a
 *  raymarched "tentacles" tunnel for the rest of the stage. Both effects live in
 *  kaleidoscope_source.frag and render in a single pass.
 *
 *  While the phosphene is showing, kaleidoscope_source.frag also screen-blends a hue-cycling lattice
 *  overlay (latticeOverlay()) on top of it - a "cosine set" plane-wave field that continuously
 *  deforms between a square and a hexagonal grid. Purely a shader-side addition (driven off the same
 *  u_time this class already pushes in) - no Java-side uniform of its own.
 *
 *  Colour: the stage starts out monochrome (the original grey palettes) and fades toward a colour
 *  cosine palette (PAL_A..PAL_D in kaleidoscope_source.frag) as the player progresses through the
 *  stage - see colorMix(). Both effects share the same fade, so it carries straight through the
 *  phosphene -> tentacles switch.
 *
 *  The switch is currently a hard cut. This class used to smooth it with a datamosh-style
 *  block-matching smear (motion/accumulate FrameBuffer passes, kaleidoscope_motion.frag and
 *  kaleidoscope_accumulate.frag), which was removed because it wasn't working as intended - see git
 *  history for that implementation; the two .frag files are still in assets/shaders, unused. */
public class Stage2KaleidoscopeShader implements BackgroundShader {
    // Matches the guide's own "#define BTIME 10.0" - used whenever a stage's schedule doesn't set
    // SpawnScheduler.getKaleidoscopeTransitionTime() (see ScheduleFile.kaleidoscopeTransitionTime),
    // so this shader still looks right even without one.
    public static final float DEFAULT_TRANSITION_TIME = 10f;
    // Fallback only, for a stage with no camera distance to follow (see setStageDistance()): seconds of
    // shader time the monochrome -> colour fade takes.
    public static final float DEFAULT_COLOR_FADE_TIME = 130f;

    private final ShaderProgram sourceShader;

    private float time;
    private float transitionTime = DEFAULT_TRANSITION_TIME;
    private float colorFadeTime = DEFAULT_COLOR_FADE_TIME;
    // The camera distance GameController pushes in every frame (see setStageDistance()), NaN until the
    // first one arrives - while NaN both the colour fade and the phosphene/tentacles switch fall back to
    // this shader's own clock.
    private float stageDistance = Float.NaN;
    // Distances at which the colour fade completes / the tentacles take over; <= 0 = not distance-driven.
    private float colorFadeDistance = -1f;
    private float tentacleSwitchDistance = -1f;
    // World-units/sec grounded enemies currently drift down the screen by - see
    // setGroundScrollSpeed()/kaleidoscope_source.frag's own u_groundScrollSpeed doc. 0 (the default,
    // before GameController's first per-frame push) just means the lattice overlay sits static.
    private float groundScrollSpeed;

    public Stage2KaleidoscopeShader() {
        sourceShader = ShaderLoader.compile("Stage2KaleidoscopeShader source pass", "background.vert", "kaleidoscope_source.frag");
    }

    /** How many seconds of u_time the phosphene kaleidoscope plays before this switches over to
     *  the tentacles tunnel - a fallback for a stage with no camera distance to follow (see
     *  setDistances()); see ScrollingBackground.setKaleidoscopeTransitionTime(). */
    public void setTransitionTime(float transitionTime) {
        this.transitionTime = transitionTime;
    }

    /** How many seconds of u_time the monochrome -> colour fade takes. Fallback only - see
     *  DEFAULT_COLOR_FADE_TIME. Non-positive means "already fully coloured". */
    public void setColorFadeTime(float colorFadeTime) {
        this.colorFadeTime = colorFadeTime;
    }

    /** The camera's current distance into the stage. Preferred over the shader's own clock for both
     *  the colour fade and the phosphene -> tentacles switch, because that clock restarts on every
     *  seek/checkpoint/quick-start (see resetTime()), which would snap the stage back to monochrome
     *  phosphene wherever you jumped to. */
    public void setStageDistance(float distance) {
        this.stageDistance = distance;
    }

    /** Distances at which the monochrome -> colour fade finishes and the tentacles replace the
     *  phosphene; either <= 0 leaves that one on the shader's own clock instead. */
    public void setDistances(float colorFadeDistance, float tentacleSwitchDistance) {
        this.colorFadeDistance = colorFadeDistance;
        this.tentacleSwitchDistance = tentacleSwitchDistance;
    }

    /** The same signed world-units/sec value BaseEnemy.applyGroundScroll() moves a grounded enemy
     *  by (negative = downward) - see GameController's own per-frame push of this same value into
     *  EntityManager.update(). Keeps the phosphene lattice overlay's downward drift in lockstep with
     *  however fast/slow/frozen/reversed the stage's ground scroll actually is right now, including
     *  a Trigger.setSpeed() scale or schedule-level override - see kaleidoscope_source.frag's own
     *  u_groundScrollSpeed doc for the uv-space conversion this feeds into. */
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

    /** 0 (fully monochrome) up to 1 (fully the colour palette): stage distance / colorFadeDistance if the
     *  camera distance is being pushed in, otherwise a linear ramp over colorFadeTime seconds. */
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
        // quadTexture (a harmless dummy - assets.pixelTexture) purely triggers the draw call; its
        // pixel content is never read, sourceShader computes everything procedurally.
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
    }

    @Override
    public void dispose() {
        sourceShader.dispose();
    }
}
