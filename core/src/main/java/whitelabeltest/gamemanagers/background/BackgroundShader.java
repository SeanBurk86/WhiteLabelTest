package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/** A full-screen procedural background effect standing in for a stage's scrolling image layers or
 *  background video - see StageDefinition.shaderBackground/ScrollingBackground. Each implementation
 *  wraps its own GLSL fragment shader (typically ported from a Shadertoy source) behind this same
 *  small lifecycle so ScrollingBackground doesn't need to know which one a given stage picked. */
public interface BackgroundShader extends Disposable {
    void update(float delta);

    /** Restarts the shader's own animation clock at 0 - called on ScrollingBackground.reset()/
     *  seekTo(), which (like gdx-video's own lack of a seek API) can't fast-forward a shader's
     *  state to an arbitrary elapsed time either, so a seek just restarts it from the top. */
    void resetTime();

    void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight);
}
