package whitelabeltest.gamemanagers.effects;
import whitelabeltest.gamemanagers.background.ShaderLoader;
import whitelabeltest.gamemanagers.UIManager;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/** Shader flames behind the chain counter (ported from a Godot fire shader, with procedural noise).
 *  The chain count drives intensity (embers to full blaze, faster and narrower flames); beyond
 *  CHAIN_COUNT_AT_MAX_INTENSITY an overdrive ramp takes over, fully at double that count. */
public class ChainFireEffect implements Disposable {
    private static final int CHAIN_COUNT_AT_MAX_INTENSITY = 125;
    private static final float FADE_OUT_DURATION = 0.5f;
    // How fast displayed intensity eases toward the target (low, so flame shape doesn't snap).
    private static final float INTENSITY_SMOOTHING_SPEED = 3f;

    // Low-chain ember ramp.
    private static final Color EMBER_BOTTOM = new Color(0.10f, 0.3f, 0.16f, 1f);
    private static final Color EMBER_MIDDLE = UIManager.HUD_GREEN_DIM;
    private static final Color EMBER_TOP = new Color(0.04f, 0.1f, 0.06f, 1f);

    // Full-blaze ramp: HUD green core to amber tip.
    private static final Color BLAZE_BOTTOM = new Color(0.6f, 1.0f, 0.75f, 1f);
    private static final Color BLAZE_MIDDLE = UIManager.HUD_GREEN;
    private static final Color BLAZE_TOP = UIManager.HUD_AMBER;

    // Overdrive ramp past max intensity: amber to HUD red.
    private static final Color MYSTIC_BOTTOM = new Color(1.0f, 0.85f, 0.55f, 1f);
    private static final Color MYSTIC_MIDDLE = UIManager.HUD_AMBER;
    private static final Color MYSTIC_TOP = UIManager.HUD_RED;

    private final ShaderProgram shader;
    private float time;
    private int lastChainCount;
    private float fadeAlpha;
    private float displayedIntensity;
    private float displayedMysticT;

    public ChainFireEffect() {
        shader = ShaderLoader.compile("ChainFireEffect shader", "tinted.vert", "chain_fire.frag");
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

    /** Draws the flames over the rect; quadTexture only carries UVs (e.g. a 1x1 white pixel). */
    public void render(SpriteBatch batch, Texture quadTexture, float x, float y, float width, float height) {
        if (fadeAlpha <= 0f) return;

        float intensity = displayedIntensity;
        float mysticT = displayedMysticT;

        // Save and force a known batch tint/blend state; earlier draws may have changed it.
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
