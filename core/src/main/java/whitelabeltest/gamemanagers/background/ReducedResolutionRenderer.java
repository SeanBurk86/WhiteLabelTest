package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;

/** Renders a BackgroundShader into an off-screen buffer at a fraction of the play area's pixel size,
 *  then stretches it over the play area. The shaders ray-march every pixel, so cost scales with pixel
 *  count; they use v_texCoords/u_resolution (never gl_FragCoord), so any buffer size works. The
 *  scissor and viewport are saved and restored around the off-screen pass. */
public class ReducedResolutionRenderer implements Disposable {
    // HIGH renders at RESOLUTION_SCALE, MEDIUM at MEDIUM_RESOLUTION_SCALE, LOW at the medium scale
    // capped to LOW_MAX_BUFFER_HEIGHT pixels.
    public static final float RESOLUTION_SCALE = 0.5f;
    public static final float MEDIUM_RESOLUTION_SCALE = 0.35f;
    public static final int LOW_MAX_BUFFER_HEIGHT = 300;
    // LOW redraws every other frame, reusing the last result in between.
    private static final int LOW_REDRAW_INTERVAL = 2;

    private final IntBuffer query = BufferUtils.newIntBuffer(16);
    private final Matrix4 tightProjection = new Matrix4();
    private final Matrix4 previousProjection = new Matrix4();
    private FrameBuffer buffer;
    private int framesSinceRedraw;

    /** Renders (or reuses) the buffer and draws it over the world. Expects and leaves the batch begun. */
    public void render(BackgroundShader shader, SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        GraphicsSettings.ShaderQuality quality = shader.quality();
        boolean scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        boolean recreated = ensureBuffer(scissorWasEnabled, quality);
        framesSinceRedraw++;
        if (recreated || framesSinceRedraw >= redrawIntervalFor(quality)) {
            framesSinceRedraw = 0;
            renderIntoBuffer(shader, batch, quadTexture, worldWidth, worldHeight, scissorWasEnabled);
        }

        ShaderProgram previousShader = batch.getShader();
        float previousColor = batch.getPackedColor();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        Texture result = buffer.getColorBufferTexture();
        batch.draw(result, 0, 0, worldWidth, worldHeight, 0, 0, result.getWidth(), result.getHeight(), false, true);
        batch.setShader(previousShader);
        batch.setPackedColor(previousColor);
    }

    private void renderIntoBuffer(BackgroundShader shader, SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight, boolean scissorWasEnabled) {
        query.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, query);
        int viewportX = query.get(0), viewportY = query.get(1), viewportW = query.get(2), viewportH = query.get(3);

        batch.end();
        if (scissorWasEnabled) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        previousProjection.set(batch.getProjectionMatrix());
        batch.setProjectionMatrix(tightProjection.setToOrtho2D(0, 0, worldWidth, worldHeight));

        buffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        shader.render(batch, quadTexture, worldWidth, worldHeight);
        batch.end();
        buffer.end();

        batch.setProjectionMatrix(previousProjection);
        Gdx.gl.glViewport(viewportX, viewportY, viewportW, viewportH);
        if (scissorWasEnabled) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        batch.begin();
    }

    private static float scaleFor(GraphicsSettings.ShaderQuality quality) {
        return quality == GraphicsSettings.ShaderQuality.HIGH ? RESOLUTION_SCALE : MEDIUM_RESOLUTION_SCALE;
    }

    private static int redrawIntervalFor(GraphicsSettings.ShaderQuality quality) {
        return quality == GraphicsSettings.ShaderQuality.LOW ? LOW_REDRAW_INTERVAL : 1;
    }

    /** (Re)creates the buffer for the play area's current pixel size (scissor box, else back buffer),
     *  so resizes are handled. Returns true for a new, empty buffer that must be drawn before showing. */
    private boolean ensureBuffer(boolean scissorEnabled, GraphicsSettings.ShaderQuality quality) {
        int areaW, areaH;
        if (scissorEnabled) {
            query.clear();
            Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, query);
            areaW = query.get(2);
            areaH = query.get(3);
        } else {
            areaW = Gdx.graphics.getBackBufferWidth();
            areaH = Gdx.graphics.getBackBufferHeight();
        }
        float scale = scaleFor(quality);
        if (quality == GraphicsSettings.ShaderQuality.LOW) scale = Math.min(scale, LOW_MAX_BUFFER_HEIGHT / (float) Math.max(1, areaH));
        int w = Math.max(1, Math.round(areaW * scale));
        int h = Math.max(1, Math.round(areaH * scale));
        if (buffer != null && buffer.getWidth() == w && buffer.getHeight() == h) return false;
        if (buffer != null) buffer.dispose();
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return true;
    }

    @Override
    public void dispose() {
        if (buffer != null) buffer.dispose();
        buffer = null;
    }
}
