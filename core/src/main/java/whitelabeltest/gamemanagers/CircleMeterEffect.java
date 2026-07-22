package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Draws a circular ring-shaped progress meter (0..1 fraction, filling clockwise from the top),
 *  as an alternative to UIManager's rectangular meters. Renders as a hollow ring between
 *  innerRadius and outerRadius (both 0..0.5, relative to the drawn quad) via a small shader
 *  rather than ShapeRenderer, so it can be drawn inline within UIManager's existing SpriteBatch
 *  session - same custom-shader-in-SpriteBatch approach as ChainFireEffect. */
public class CircleMeterEffect implements Disposable {
    private static final String VERTEX_SHADER =
        "attribute vec4 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main() {\n" +
        "    v_color = a_color;\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoords;\n" +
        "uniform float u_fraction;\n" +
        "uniform vec4 u_bgColor;\n" +
        "uniform vec4 u_fillColor;\n" +
        "uniform float u_innerRadius;\n" +
        "uniform float u_outerRadius;\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 uv = v_texCoords - vec2(0.5);\n" +
        "    float dist = length(uv);\n" +
        "    float ringAlpha = (dist >= u_innerRadius && dist <= u_outerRadius) ? 1.0 : 0.0;\n" +
        "\n" +
        "    // Angle measured from straight up, increasing clockwise, normalized to 0..1.\n" +
        "    float angle = atan(uv.x, -uv.y);\n" +
        "    if (angle < 0.0) angle += 6.28318530718;\n" +
        "    float normalizedAngle = angle / 6.28318530718;\n" +
        "\n" +
        "    vec4 color = normalizedAngle <= u_fraction ? u_fillColor : u_bgColor;\n" +
        "    gl_FragColor = vec4(color.rgb, color.a * ringAlpha * v_color.a);\n" +
        "}\n";

    private final ShaderProgram shader;

    public CircleMeterEffect() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("CircleMeterEffect shader failed to compile: " + shader.getLog());
        }
    }

    public void render(SpriteBatch batch, Texture quadTexture, float fraction, Color bgColor, Color fillColor,
                        float innerRadius, float outerRadius, float x, float y, float size) {
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
