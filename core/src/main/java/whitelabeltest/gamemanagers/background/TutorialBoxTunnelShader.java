package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** The tutorial's "boxTunnel" ray-marched background (tunnel.frag). */
public class TutorialBoxTunnelShader implements BackgroundShader {
    private final ShaderProgram shader;
    private final GraphicsSettings.ShaderQuality quality;
    private float time;

    public TutorialBoxTunnelShader() {
        // Lower quality levels compile with fewer march steps.
        quality = GraphicsSettings.getShaderQuality();
        shader = quality.define != null
            ? ShaderLoader.compile("TutorialBoxTunnelShader", "background.vert", "tunnel.frag", quality.define)
            : ShaderLoader.compile("TutorialBoxTunnelShader", "background.vert", "tunnel.frag");
    }

    @Override
    public void update(float delta) {
        time += delta;
    }

    @Override
    public void resetTime() {
        time = 0f;
    }

    @Override
    public void render(SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();

        batch.setShader(shader);
        batch.setColor(Color.WHITE);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_resolution", worldWidth, worldHeight);

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
