package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;

/** Stage 3's background - a raymarched Mandelbulb the camera flies around and, later in the stage, into,
 *  while the fractal's power/twist and its colours drift over time.
 *
 *  The camera starts out orbiting the bulb from outside, then - once the stage's camera distance passes
 *  setDiveDistance() (GameController sets that to a little before the boss trigger) - bursts through the
 *  fractal's skin into the hollow chamber inside it and flies around in there for the rest of the stage.
 *  Where the camera is comes from MandelbulbCamera (see its doc for why that's an autopilot rather than a
 *  path); this class just runs the clocks, feeds it, and hands the result to mandelbulb.frag.
 *
 *  The dive is driven by camera DISTANCE rather than this shader's own clock for the same reason
 *  Stage2KaleidoscopeShader's fade is: the clock restarts on every seek/checkpoint/quick-start (see
 *  resetTime()), which would otherwise throw the camera back outside the bulb wherever you jumped to. */
public class MandelbulbShader implements BackgroundShader {
    /** How many distance units the outside -> inside blend takes, starting at setDiveDistance(). */
    public static final float DIVE_BLEND_DISTANCE = 6f;
    // Camera clock rate (shader seconds per real second) for the outside orbit.
    private static final float ORBIT_RATE = 0.3f;
    // How fast the fractal's parameters drift once the camera is inside, relative to outside. Slowed
    // down because the chamber's walls ARE the fractal surface: morphing it at full speed would sweep
    // walls over the camera faster than it can steer clear.
    private static final float INSIDE_PARAM_RATE = 0.25f;

    private final ShaderProgram shader;
    private float time;
    // The clocks below are integrated (not derived from `time`) because their rates change with the dive
    // - deriving them would make the value jump whenever the rate does.
    private float orbitTime;
    private float diveTime;
    private float paramTime;
    private final MandelbulbCamera camera = new MandelbulbCamera();
    // NaN until the first setStageDistance() arrives - treated as "not inside yet" until then.
    private float stageDistance = Float.NaN;
    // <= 0 = no dive: the camera just orbits outside for the whole stage.
    private float diveDistance = -1f;

    public MandelbulbShader() {
        shader = ShaderLoader.compile("MandelbulbShader", "background.vert", "mandelbulb.frag");
    }

    /** The camera's current distance into the stage - see the class doc. */
    public void setStageDistance(float distance) {
        this.stageDistance = distance;
    }

    /** Camera distance at which the dive into the bulb begins; it's fully inside DIVE_BLEND_DISTANCE
     *  later. <= 0 disables the dive. */
    public void setDiveDistance(float diveDistance) {
        this.diveDistance = diveDistance;
    }

    /** 0 (orbiting outside) up to 1 (flying through the interior), eased. */
    private float inside() {
        if (Float.isNaN(stageDistance) || diveDistance <= 0f) return 0f;
        float x = MathUtils.clamp((stageDistance - diveDistance) / DIVE_BLEND_DISTANCE, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    @Override
    public void update(float delta) {
        float inside = inside();
        time += delta;
        orbitTime += delta * ORBIT_RATE;
        diveTime += delta * inside;
        paramTime += delta * MathUtils.lerp(1f, INSIDE_PARAM_RATE, inside);
        camera.update(delta, orbitTime, diveTime, inside, power(), twist());
    }

    // Same functions the shader used to compute from u_time itself; they live here now because the camera
    // needs the identical values to steer around the identical surface.
    private float power() {
        return 6.5f + 2f * MathUtils.sin(paramTime * 0.11f) + 0.5f * MathUtils.sin(paramTime * 0.29f + 1.7f);
    }

    private float twist() {
        return 0.10f * MathUtils.sin(paramTime * 0.17f + 2f);
    }

    @Override
    public void resetTime() {
        time = 0f;
        orbitTime = 0f;
        diveTime = 0f;
        paramTime = 0f;
        camera.reset();
    }

    @Override
    public void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();

        batch.setShader(shader);
        batch.setColor(Color.WHITE);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_camTime", orbitTime);
        shader.setUniformf("u_camPos", camera.position());
        shader.setUniformf("u_camFwd", camera.forward());
        shader.setUniformf("u_power", power());
        shader.setUniformf("u_twist", twist());
        shader.setUniformf("u_resolution", worldWidth, worldHeight);

        // quadTexture is a dummy that only triggers the draw call - the shader computes every pixel.
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
