package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/**
 * Fire effect drawn behind the chain counter, ported from a Godot canvas_item fire shader.
 * The original sampled a noise texture; since this project has no noise asset, that sampling
 * is replaced with an equivalent procedural hash noise. Chain count maps to an intensity value
 * (0..1) that shifts the color ramp from dim embers to a full blaze, and speeds up / narrows
 * the flame as the chain grows. Beyond {@link #CHAIN_COUNT_AT_MAX_INTENSITY}, a second ramp
 * shifts the blaze toward a purple-bluish "mystic" hue, reaching full effect at double that count.
 */
public class ChainFireEffect implements Disposable {
    private static final int CHAIN_COUNT_AT_MAX_INTENSITY = 125;
    private static final float FADE_OUT_DURATION = 0.5f;
    // Higher = intensity catches up to the target faster. Kept low so a chain-count bump eases
    // the flame's shape (noise scale/aperture/speed all key off intensity) instead of snapping it.
    private static final float INTENSITY_SMOOTHING_SPEED = 3f;

    // Dim green embers at low chain counts - a muted version of UIManager's own HUD_GREEN_DIM.
    private static final Color EMBER_BOTTOM = new Color(0.10f, 0.3f, 0.16f, 1f);
    private static final Color EMBER_MIDDLE = UIManager.HUD_GREEN_DIM;
    private static final Color EMBER_TOP = new Color(0.04f, 0.1f, 0.06f, 1f);

    // Full-blaze ramp at the chain's normal max intensity - bright HUD_GREEN core cooling to
    // HUD_AMBER at the tip, the same two colors the rest of the HUD reads as "healthy/active".
    private static final Color BLAZE_BOTTOM = new Color(0.6f, 1.0f, 0.75f, 1f);
    private static final Color BLAZE_MIDDLE = UIManager.HUD_GREEN;
    private static final Color BLAZE_TOP = UIManager.HUD_AMBER;

    // "Mystic" overdrive ramp, blended in once the chain climbs past CHAIN_COUNT_AT_MAX_INTENSITY -
    // pushes further along the same palette into HUD_RED, matching the HUD's own "critical" color.
    private static final Color MYSTIC_BOTTOM = new Color(1.0f, 0.85f, 0.55f, 1f);
    private static final Color MYSTIC_MIDDLE = UIManager.HUD_AMBER;
    private static final Color MYSTIC_TOP = UIManager.HUD_RED;

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
        "uniform float u_time;\n" +
        "uniform vec4 u_bottomColor;\n" +
        "uniform vec4 u_middleColor;\n" +
        "uniform vec4 u_topColor;\n" +
        "uniform float u_fireAlpha;\n" +
        "uniform vec2 u_fireSpeed;\n" +
        "uniform float u_fireAperture;\n" +
        "uniform float u_intensity;\n" +
        "\n" +
        "float hash(vec2 p) {\n" +
        "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);\n" +
        "}\n" +
        "\n" +
        "float valueNoise(vec2 p) {\n" +
        "    vec2 i = floor(p);\n" +
        "    vec2 f = fract(p);\n" +
        "    vec2 u = f * f * (3.0 - 2.0 * f);\n" +
        "    float a = hash(i);\n" +
        "    float b = hash(i + vec2(1.0, 0.0));\n" +
        "    float c = hash(i + vec2(0.0, 1.0));\n" +
        "    float d = hash(i + vec2(1.0, 1.0));\n" +
        "    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n" +
        "}\n" +
        "\n" +
        "vec4 tri_color_mix(vec4 color1, vec4 color2, vec4 color3, float pos) {\n" +
        "    pos = clamp(pos, 0.0, 1.0);\n" +
        "    if (pos < 0.5) {\n" +
        "        return mix(color1, color2, pos * 2.0);\n" +
        "    } else {\n" +
        "        return mix(color2, color3, (pos - 0.5) * 2.0);\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 uv = v_texCoords;\n" +
        "\n" +
        "    float noise_scale = 6.0 + u_intensity * 6.0;\n" +
        "    vec2 base_uv = uv * noise_scale;\n" +
        "\n" +
        "    vec2 shifted_uv1 = base_uv + u_time * u_fireSpeed;\n" +
        "    vec2 shifted_uv2 = base_uv + u_time * u_fireSpeed * 1.5;\n" +
        "\n" +
        "    float fire_noise1 = valueNoise(shifted_uv1);\n" +
        "    float fire_noise2 = valueNoise(shifted_uv2);\n" +
        "    float combined_noise = (fire_noise1 + fire_noise2) * 0.5;\n" +
        "\n" +
        "    float noise = uv.y * (((uv.y + u_fireAperture) * combined_noise - u_fireAperture) * 75.0);\n" +
        "    noise += sin(uv.y * 10.0 + u_time * (2.0 + u_intensity * 4.0)) * 0.1;\n" +
        "\n" +
        "    float gradient_pos = clamp(noise * 0.08, 0.3, 2.0);\n" +
        "    vec4 fire_color = tri_color_mix(u_bottomColor, u_middleColor, u_topColor, gradient_pos);\n" +
        "    fire_color.rgb *= 1.0 + u_intensity * 0.5;\n" +
        "\n" +
        "    gl_FragColor = vec4(fire_color.rgb, clamp(noise, 0.0, 1.0) * u_fireAlpha * v_color.a);\n" +
        "}\n";

    private final ShaderProgram shader;
    private float time;
    private int lastChainCount;
    private float fadeAlpha;
    private float displayedIntensity;
    private float displayedMysticT;

    public ChainFireEffect() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("ChainFireEffect shader failed to compile: " + shader.getLog());
        }
    }

    /** Call once per frame regardless of chain state; tracks the fade-out after a chain breaks. */
    public void update(float delta, int chainCount) {
        time += delta;

        if (chainCount > 0) {
            lastChainCount = chainCount;
            fadeAlpha = 1f;
        } else if (fadeAlpha > 0f) {
            fadeAlpha = Math.max(0f, fadeAlpha - delta / FADE_OUT_DURATION);
        }

        float targetIntensity = MathUtils.clamp(lastChainCount / (float) CHAIN_COUNT_AT_MAX_INTENSITY, 0f, 1f);
        displayedIntensity += (targetIntensity - displayedIntensity) * Math.min(1f, delta * INTENSITY_SMOOTHING_SPEED);

        float targetMysticT = MathUtils.clamp((lastChainCount - CHAIN_COUNT_AT_MAX_INTENSITY) / (float) CHAIN_COUNT_AT_MAX_INTENSITY, 0f, 1f);
        displayedMysticT += (targetMysticT - displayedMysticT) * Math.min(1f, delta * INTENSITY_SMOOTHING_SPEED);
    }

    /** Draws the fire effect stretched over the given rect using quadTexture only as a UV carrier (e.g. a 1x1 white pixel). Safe to call every frame; no-ops once fully faded out. */
    public void render(SpriteBatch batch, Texture quadTexture, float x, float y, float width, float height) {
        if (fadeAlpha <= 0f) return;

        float intensity = displayedIntensity;
        float mysticT = displayedMysticT;

        // Gameplay draws (flashing sprites, additive glow effects, etc.) can leave the batch's
        // tint and blend function in a state that would wash out or hide this effect, so save
        // and force known-good state rather than inheriting whatever came before.
        ShaderProgram previousShader = batch.getShader();
        float previousPackedColor = batch.getPackedColor();
        int previousSrcFunc = batch.getBlendSrcFunc();
        int previousDstFunc = batch.getBlendDstFunc();
        int previousSrcFuncAlpha = batch.getBlendSrcFuncAlpha();
        int previousDstFuncAlpha = batch.getBlendDstFuncAlpha();

        batch.setShader(shader);
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_fireAlpha", MathUtils.lerp(0.35f, 0.9f, intensity) * fadeAlpha);
        shader.setUniformf("u_fireAperture", MathUtils.lerp(3.00f, 0.00f, intensity));
        shader.setUniformf("u_fireSpeed", 0f, MathUtils.lerp(2.0f, 14.0f, intensity));
        shader.setUniformf("u_intensity", intensity);
        setColorUniform("u_bottomColor", EMBER_BOTTOM, BLAZE_BOTTOM, MYSTIC_BOTTOM, intensity, mysticT);
        setColorUniform("u_middleColor", EMBER_MIDDLE, BLAZE_MIDDLE, MYSTIC_MIDDLE, intensity, mysticT);
        setColorUniform("u_topColor", EMBER_TOP, BLAZE_TOP, MYSTIC_TOP, intensity, mysticT);

        batch.draw(quadTexture, x, y, width, height);

        batch.setShader(previousShader);
        batch.setPackedColor(previousPackedColor);
        batch.setBlendFunctionSeparate(previousSrcFunc, previousDstFunc, previousSrcFuncAlpha, previousDstFuncAlpha);
    }

    private void setColorUniform(String name, Color ember, Color blaze, Color mystic, float intensity, float mysticT) {
        float r = MathUtils.lerp(MathUtils.lerp(ember.r, blaze.r, intensity), mystic.r, mysticT);
        float g = MathUtils.lerp(MathUtils.lerp(ember.g, blaze.g, intensity), mystic.g, mysticT);
        float b = MathUtils.lerp(MathUtils.lerp(ember.b, blaze.b, intensity), mystic.b, mysticT);
        float a = MathUtils.lerp(MathUtils.lerp(ember.a, blaze.a, intensity), mystic.a, mysticT);
        shader.setUniformf(name, r, g, b, a);
    }

    @Override
    public void dispose() {
        shader.dispose();
    }
}
