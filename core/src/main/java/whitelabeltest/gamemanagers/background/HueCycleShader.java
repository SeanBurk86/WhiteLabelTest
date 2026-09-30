package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Hue-rotates the background image layers drawn between begin()/end()
 *  (StageDefinition.hueCycleBackground). One full cycle takes `period`, which is set to the time to
 *  the boss video so the colors return to normal as it starts. */
public class HueCycleShader implements Disposable {
    private final ShaderProgram shader;
    private float time;
    private float period = 60f;

    public HueCycleShader() {
        shader = ShaderLoader.compile("HueCycleShader", "tinted.vert", "hue_cycle.frag");
    }

    /** Seconds per full rotation; <= 0 disables cycling. */
    public void setPeriod(float period) {
        this.period = period;
    }

    public void update(float delta) {
        time += delta;
    }

    public void resetTime() {
        time = 0f;
    }

    /** Stateless (time % period), so a seek can jump straight to the right hue. */
    public void setTime(float time) {
        this.time = time;
    }

    /** Installs this shader and returns the previous one, to pass to end(). */
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
