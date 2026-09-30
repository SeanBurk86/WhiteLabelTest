package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;

/** Stage 3's "mandelbulb" background (mandelbulb.frag): a ray-marched Mandelbulb whose power, twist
 *  and colors drift. The camera orbits it, then from setDiveDistance() (a little before the boss)
 *  bursts through into the hollow interior. MandelbulbCamera flies the camera; this class runs the
 *  clocks. The dive follows camera distance, not the shader clock, because the clock restarts on
 *  every seek. */
public class MandelbulbShader implements BackgroundShader {
    /** Distance units the outside-to-inside blend takes. */
    public static final float DIVE_BLEND_DISTANCE = 6f;
    // Orbit clock rate (shader seconds per real second).
    private static final float ORBIT_RATE = 0.3f;
    // Slower fractal morphing inside, where the walls are the fractal surface and the camera must
    // be able to steer clear of them.
    private static final float INSIDE_PARAM_RATE = 0.25f;

    private final ShaderProgram shader;
    private final GraphicsSettings.ShaderQuality quality;
    private float time;
    // Integrated clocks (their rates change during the dive, so deriving them from time would jump).
    private float orbitTime;
    private float diveTime;
    private float paramTime;
    private final MandelbulbCamera camera = new MandelbulbCamera();
    // NaN until the first setStageDistance() arrives - treated as "not inside yet" until then.
    private float stageDistance = Float.NaN;
    // <= 0 = no dive: the camera just orbits outside for the whole stage.
    private float diveDistance = -1f;

    public MandelbulbShader() {
        // Lower quality levels compile with fewer march steps.
        quality = GraphicsSettings.getShaderQuality();
        shader = quality.define != null
            ? ShaderLoader.compile("MandelbulbShader", "background.vert", "mandelbulb.frag", quality.define)
            : ShaderLoader.compile("MandelbulbShader", "background.vert", "mandelbulb.frag");
    }

    /** The stage camera's current distance. */
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

    // Computed here (not in the shader) so the camera steers around exactly the rendered surface.
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

        // quadTexture is only a dummy for the draw call.
        batch.draw(quadTexture, 0, 0, worldWidth, worldHeight);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
    }

    @Override
    public GraphicsSettings.ShaderQuality quality() {
        return quality;
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
