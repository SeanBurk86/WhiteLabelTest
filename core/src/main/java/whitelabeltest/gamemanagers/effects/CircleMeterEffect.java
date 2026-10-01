package whitelabeltest.gamemanagers.effects;
import whitelabeltest.gamemanagers.background.ShaderLoader;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** A ring progress meter (fraction 0..1, clockwise from the top) between innerRadius and outerRadius
 *  (0..0.5 of the quad), drawn with a shader inside the UI's SpriteBatch. */
public class CircleMeterEffect implements Disposable {
    private final ShaderProgram shader;

    public CircleMeterEffect() {
        shader = ShaderLoader.compile("CircleMeterEffect shader", "tinted.vert", "circle_meter.frag");
    }

    public void render(SpriteBatch batch, Texture quadTexture, float fraction, Color bgColor, Color fillColor,
                        float innerRadius, float outerRadius, float x, float y, float size) {
        render(batch, quadTexture, fraction, bgColor, fillColor, innerRadius, outerRadius, x, y, size, 0);
    }

    /** @param dashes number of dashes around the ring, or 0 for a solid ring */
    public void render(SpriteBatch batch, Texture quadTexture, float fraction, Color bgColor, Color fillColor,
                        float innerRadius, float outerRadius, float x, float y, float size, int dashes) {
        render(batch, quadTexture, fraction, bgColor, fillColor, innerRadius, outerRadius, x, y, size, dashes, 0f);
    }

    /** @param dashOffset rotates the dashes, as a fraction of a turn */
    public void render(SpriteBatch batch, Texture quadTexture, float fraction, Color bgColor, Color fillColor,
                        float innerRadius, float outerRadius, float x, float y, float size, int dashes, float dashOffset) {
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();
        int previousSrcFunc = batch.getBlendSrcFunc();
        int previousDstFunc = batch.getBlendDstFunc();
        int previousSrcFuncAlpha = batch.getBlendSrcFuncAlpha();
        int previousDstFuncAlpha = batch.getBlendDstFuncAlpha();

        batch.setShader(shader);
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.setUniformf("u_fraction", fraction);
        shader.setUniformf("u_bgColor", bgColor.r, bgColor.g, bgColor.b, bgColor.a);
        shader.setUniformf("u_fillColor", fillColor.r, fillColor.g, fillColor.b, fillColor.a);
        shader.setUniformf("u_innerRadius", innerRadius);
        shader.setUniformf("u_outerRadius", outerRadius);
        shader.setUniformf("u_dashes", dashes);
        shader.setUniformf("u_dashOffset", dashOffset);

        batch.draw(quadTexture, x, y, size, size);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
        batch.setBlendFunctionSeparate(previousSrcFunc, previousDstFunc, previousSrcFuncAlpha, previousDstFuncAlpha);
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
