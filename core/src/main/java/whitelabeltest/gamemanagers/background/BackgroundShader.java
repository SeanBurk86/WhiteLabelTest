package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/** A full-screen procedural background (StageDefinition.shaderBackground), usually a Shadertoy port,
 *  used in place of image layers or video. */
public interface BackgroundShader extends Disposable {
    void update(float delta);

    /** Restarts the animation clock (on reset and seek, since shader state can't be fast-forwarded). */
    void resetTime();

    void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight);

    /** The quality level it was built at; sets ReducedResolutionRenderer's resolution and redraw rate. */
    default GraphicsSettings.ShaderQuality quality() {
        return GraphicsSettings.ShaderQuality.HIGH;
    }
}
