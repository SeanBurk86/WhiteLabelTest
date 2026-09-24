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

/** Draws a full-screen BackgroundShader into an off-screen buffer at a fraction of the play area's real
 *  pixel size, then stretches that buffer (linearly filtered) over the play area. The stage shaders
 *  ray-march every pixel every frame (see mandelbulb.frag/kaleidoscope_source.frag/tunnel.frag), so their
 *  cost scales with pixel count - at RESOLUTION_SCALE 0.5 they do a quarter of the work, and their soft,
 *  procedural look hides the lower resolution. Every one of them computes its image from v_texCoords and
 *  u_resolution (the world size), never gl_FragCoord, so they render identically into a buffer of any size.
 *
 *  Same off-screen pass handling as PlayerFeedbackShader: the outer draw clips to the play area in real
 *  screen pixels (Main.java's scissor) and FrameBuffer.end() resets the GL viewport to the whole back
 *  buffer, so both are saved and restored around the pass, and the pass uses a tight world-space projection
 *  so the shader fills the buffer edge to edge. */
public class ReducedResolutionRenderer implements Disposable {
    public static final float RESOLUTION_SCALE = 0.5f;

    private final IntBuffer query = BufferUtils.newIntBuffer(16);
    private final Matrix4 tightProjection = new Matrix4();
    private final Matrix4 previousProjection = new Matrix4();
    private FrameBuffer buffer;

    /** Renders `shader` into the reduced-resolution buffer and draws the result over (0, 0, worldWidth,
     *  worldHeight). Expects the batch to be drawing (begun) and leaves it that way. */
    public void render(BackgroundShader shader, SpriteBatch batch, Texture quadTexture, float worldWidth, float worldHeight) {
        boolean scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        ensureBuffer(scissorWasEnabled);

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
        ShaderProgram previousShader = batch.getShader();
        float previousColor = batch.getPackedColor();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        Texture result = buffer.getColorBufferTexture();
        batch.draw(result, 0, 0, worldWidth, worldHeight, 0, 0, result.getWidth(), result.getHeight(), false, true);
        batch.setShader(previousShader);
        batch.setPackedColor(previousColor);
    }

    /** (Re)creates the buffer at RESOLUTION_SCALE of the play area's current pixel size - the scissor box
     *  when the outer draw is clipping to the play area, else the whole back buffer - so a window resize
     *  gets a correctly sized buffer on the next frame. */
    private void ensureBuffer(boolean scissorEnabled) {
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
        int w = Math.max(1, Math.round(areaW * RESOLUTION_SCALE));
        int h = Math.max(1, Math.round(areaH * RESOLUTION_SCALE));
        if (buffer != null && buffer.getWidth() == w && buffer.getHeight() == h) return;
        if (buffer != null) buffer.dispose();
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    @Override
    public void dispose() {
        if (buffer != null) buffer.dispose();
        buffer = null;
    }
}
