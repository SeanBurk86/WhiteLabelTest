package whitelabeltest.gamemanagers.background;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

/** A stage's background: scrolling image layers, a looping background video, or a procedural shader,
 *  plus the boss video that can cut in later and the optional hue-cycle and player-feedback effects.
 *  Draw priority: boss video > shader > background video > layers. See the README's "Backgrounds". */
public class ScrollingBackground {
    public static final float DEFAULT_SCROLL_SPEED = -1.25f;

    // One image, or a textureSequence stacked bottom to top into one continuous strip scrolled as a
    // unit. Every texture overlapping the viewport is drawn, so seams meet with no gap.
    private static class Layer {
        final Texture[] textures;
        // Each texture's drawn height (scaled to world width) and its offset from the strip's bottom.
        final float[] heights;
        final float[] cumulativeTop;
        final float totalHeight;
        final float worldWidth, worldHeight;
        final float scrollSpeed;
        // scrollY at which the strip's top meets the top of the viewport; the layer freezes there.
        final float minScrollY;
        // World Y of the strip's bottom edge; decreases as it scrolls (scrollSpeed is negative).
        float scrollY;
        boolean frozen;

        Layer(Texture[] textures, float worldWidth, float worldHeight, float scrollSpeed) {
            this.textures = textures;
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
            this.scrollSpeed = scrollSpeed;
            for (Texture t : textures) t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            heights = new float[textures.length];
            cumulativeTop = new float[textures.length];
            float cumulative = 0f;
            for (int i = 0; i < textures.length; i++) {
                heights[i] = worldWidth * ((float) textures[i].getHeight() / textures[i].getWidth());
                cumulativeTop[i] = cumulative;
                cumulative += heights[i];
            }
            totalHeight = cumulative;
            minScrollY = worldHeight - totalHeight;
            scrollY = 0f;
        }
    }

    private final Array<Layer> layers = new Array<>();
    private final AudioSettings audioSettings;
    private final float worldWidth;
    private final float worldHeight;
    private final String bossVideoFile;
    // Looping video background from stage start.
    private final String backgroundVideoFile;
    // Procedural full-screen background, or null.
    private final BackgroundShader shaderBackground;
    private final ReducedResolutionRenderer shaderRenderer = new ReducedResolutionRenderer();
    private final Texture shaderQuadTexture;
    // Hue-shifts the image layers; null unless the stage opted in.
    private final HueCycleShader hueCycleShader;
    // Player trail overlay on top of any background; null unless the stage opted in.
    private final PlayerFeedbackShader playerFeedback;
    private final Texture playerFeedbackQuadTexture;
    private boolean stopped;
    private boolean muted;
    // Multiplies every layer's scroll speed (TriggerManager.getSpeedScale(), so setSpeed(0) stops it).
    private float scrollSpeedScale = 1f;

    private VideoPlayer bossVideoPlayer;
    private boolean bossVideoStarted;
    private VideoPlayer backgroundVideoPlayer;
    private boolean backgroundVideoStarted;

    /** @param headless keep only the scrolling layer state: no shaders, framebuffers or videos, which
     *  are purely visual and need a GL context (see GameController's headless mode). */
    public ScrollingBackground(float worldWidth, float worldHeight, AudioSettings audioSettings, AssetManager assets,
                                Array<StageDefinition.BackgroundLayerDef> layerDefs, String bossVideoFile, String backgroundVideoFile,
                                String shaderBackgroundId, boolean hueCycleBackground, boolean playerFeedbackBackground,
                                boolean headless) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;
        this.bossVideoFile = headless ? null : bossVideoFile;
        this.backgroundVideoFile = headless ? null : backgroundVideoFile;
        if (headless) {
            shaderBackgroundId = null;
            hueCycleBackground = false;
            playerFeedbackBackground = false;
        }
        for (StageDefinition.BackgroundLayerDef layerDef : layerDefs) {
            Texture[] textures;
            if (layerDef.textureSequence != null && layerDef.textureSequence.size > 0) {
                textures = new Texture[layerDef.textureSequence.size];
                for (int i = 0; i < textures.length; i++) textures[i] = assets.ensureTexture(layerDef.textureSequence.get(i));
            } else {
                textures = new Texture[] { assets.ensureTexture(layerDef.texture) };
            }
            float scrollSpeed = Float.isNaN(layerDef.scrollSpeed) ? DEFAULT_SCROLL_SPEED : layerDef.scrollSpeed;
            layers.add(new Layer(textures, worldWidth, worldHeight, scrollSpeed));
        }
        shaderBackground = createShaderBackground(shaderBackgroundId);
        shaderQuadTexture = shaderBackground != null ? assets.pixelTexture : null;
        // -Dperf.noHueCycle / -Dperf.noFeedback / -Dperf.noBossVideo disable these for perf A/B runs.
        hueCycleShader = hueCycleBackground && System.getProperty("perf.noHueCycle") == null ? new HueCycleShader() : null;
        playerFeedback = playerFeedbackBackground && System.getProperty("perf.noFeedback") == null ? new PlayerFeedbackShader() : null;
        playerFeedbackQuadTexture = playerFeedback != null ? assets.pixelTexture : null;
        backgroundVideoPlayer = startVideo(backgroundVideoFile);
        backgroundVideoStarted = backgroundVideoPlayer != null;
    }

    /** Maps a StageDefinition.shaderBackground id to its shader (null = none). */
    private static BackgroundShader createShaderBackground(String shaderBackgroundId) {
        if (shaderBackgroundId == null) return null;
        return switch (shaderBackgroundId) {
            case "boxTunnel" -> new TutorialBoxTunnelShader();
            case "kaleidoscope" -> new Stage2KaleidoscopeShader();
            case "mandelbulb" -> new MandelbulbShader();
            default -> throw new IllegalArgumentException("Unknown shaderBackground id: " + shaderBackgroundId);
        };
    }

    // The setters below are no-ops when the stage doesn't use the relevant effect, so callers never
    // need to check which background a stage has.

    /** This frame's player sprite for the feedback overlay; call before drawing. */
    public void updatePlayer(TextureRegion frame, float x, float y, float width, float height) {
        if (playerFeedback != null) playerFeedback.updatePlayer(frame, x, y, width, height);
    }

    /** This frame's halo sprite for the feedback overlay; call before drawing. */
    public void updateHalo(TextureRegion frame, float x, float y, float width, float height) {
        if (playerFeedback != null) playerFeedback.updateHalo(frame, x, y, width, height);
    }

    public void setKaleidoscopeTransitionTime(float transitionTime) {
        if (shaderBackground instanceof Stage2KaleidoscopeShader kaleidoscope) {
            kaleidoscope.setTransitionTime(transitionTime);
        }
    }

    public void setKaleidoscopeStageDistance(float distance) {
        if (shaderBackground instanceof Stage2KaleidoscopeShader kaleidoscope) {
            kaleidoscope.setStageDistance(distance);
        }
    }

    public void setMandelbulbStageDistance(float distance) {
        if (shaderBackground instanceof MandelbulbShader mandelbulb) {
            mandelbulb.setStageDistance(distance);
        }
    }

    public void setMandelbulbDiveDistance(float diveDistance) {
        if (shaderBackground instanceof MandelbulbShader mandelbulb) {
            mandelbulb.setDiveDistance(diveDistance);
        }
    }

    public void setKaleidoscopeDistances(float colorFadeDistance, float tentacleSwitchDistance) {
        if (shaderBackground instanceof Stage2KaleidoscopeShader kaleidoscope) {
            kaleidoscope.setDistances(colorFadeDistance, tentacleSwitchDistance);
        }
    }

    public void setKaleidoscopeGroundScrollSpeed(float groundScrollSpeed) {
        if (shaderBackground instanceof Stage2KaleidoscopeShader kaleidoscope) {
            kaleidoscope.setGroundScrollSpeed(groundScrollSpeed);
        }
    }

    public void setHueCyclePeriod(float period) {
        if (hueCycleShader != null) hueCycleShader.setPeriod(period);
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.setVolume(muted ? 0f : audioSettings.getEffectiveMusicVolume());
        }
        if (backgroundVideoPlayer != null) {
            backgroundVideoPlayer.setVolume(muted ? 0f : audioSettings.getEffectiveMusicVolume());
        }
    }

    /** Stops scrolling (e.g. on game over) and the boss video's audio until reset(). The background
     *  video keeps looping. */
    public void stop() {
        stopped = true;
        if (bossVideoStarted) {
            bossVideoPlayer.stop();
            bossVideoStarted = false;
        }
    }

    public void setScrollSpeedScale(float scale) { this.scrollSpeedScale = scale; }

    public void update(float delta) {
        if (!stopped) {
            for (Layer layer : layers) {
                if (layer.frozen) continue;
                layer.scrollY += layer.scrollSpeed * delta * scrollSpeedScale;
                clampToTopOfStrip(layer);
            }
        }
        if (bossVideoStarted) {
            bossVideoPlayer.update();
        }
        if (backgroundVideoStarted) {
            backgroundVideoPlayer.update();
        }
        // Keeps animating after stop(), like the background video.
        if (shaderBackground != null) {
            shaderBackground.update(delta);
        }
        if (hueCycleShader != null) {
            hueCycleShader.update(delta);
        }
        if (playerFeedback != null) {
            playerFeedback.update(delta, feedbackScrollSpeed());
        }
    }

    /** The speed the feedback trail drifts at: the first (farthest) layer's, or the default. */
    private float feedbackScrollSpeed() {
        return layers.size > 0 ? layers.first().scrollSpeed : DEFAULT_SCROLL_SPEED;
    }

    /** Freezes the layer once the strip's top reaches the top of the viewport. */
    private void clampToTopOfStrip(Layer layer) {
        if (layer.scrollY <= layer.minScrollY) {
            layer.scrollY = layer.minScrollY;
            layer.frozen = true;
        }
    }

    /** Cuts to the boss video (with its audio). No-op without one or if already playing. */
    public void triggerBossVideo() {
        if (bossVideoStarted || bossVideoFile == null || System.getProperty("perf.noBossVideo") != null) return;
        bossVideoPlayer = startVideo(bossVideoFile);
        bossVideoStarted = bossVideoPlayer != null;
    }

    /** Starts `file` looping, or returns null if it's null or can't be opened (logged). */
    private VideoPlayer startVideo(String file) {
        if (file == null) return null;
        VideoPlayer player = VideoPlayerCreator.createVideoPlayer();
        player.setLooping(true);
        try {
            player.load(Gdx.files.internal(file));
            player.setVolume(muted ? 0f : audioSettings.getEffectiveMusicVolume());
            player.play();
            return player;
        } catch (FileNotFoundException e) {
            Gdx.app.error("ScrollingBackground", "Could not open " + file, e);
            player.dispose();
            return null;
        }
    }

    /** Draws the background then the feedback overlay. (GameController's layered path calls the
     *  pieces itself, to draw attached enemies between layers.) */
    public void draw(SpriteBatch batch) {
        drawBaseContent(batch);
        drawPlayerFeedbackOverlay(batch);
    }

    private void drawBaseContent(SpriteBatch batch) {
        if (bossVideoStarted && drawVideoFrame(batch, bossVideoPlayer)) return;
        if (shaderBackground != null) {
            shaderRenderer.render(shaderBackground, batch, shaderQuadTexture, worldWidth, worldHeight);
            return;
        }
        if (backgroundVideoStarted && drawVideoFrame(batch, backgroundVideoPlayer)) return;
        beginLayeredDraw(batch);
        // backgroundLayers are declared far to near.
        for (int i = 0; i < layers.size; i++) drawLayer(batch, i);
        endLayeredDraw(batch);
    }

    /** The player feedback trail over whatever background was just drawn. */
    public void drawPlayerFeedbackOverlay(SpriteBatch batch) {
        if (playerFeedback != null) playerFeedback.renderOverlay(batch, playerFeedbackQuadTexture, worldWidth, worldHeight);
    }

    // Set by beginLayeredDraw(), restored by endLayeredDraw().
    private ShaderProgram layeredDrawPreviousShader;

    /** True when the image layer stack is what's on screen (no video or shader covering it), so
     *  layer-attached enemies can be drawn between layers. */
    public boolean isDrawingLayerStack() {
        if (bossVideoStarted && bossVideoPlayer.getTexture() != null) return false;
        if (shaderBackground != null) return false;
        if (backgroundVideoStarted && backgroundVideoPlayer.getTexture() != null) return false;
        return true;
    }

    /** Number of image layers (EnemyDefinition.backgroundLayer indexes these). */
    public int getLayerCount() { return layers.size; }

    /** Layer `index`'s current (scaled) scroll speed, or `fallback` if out of range, so a ground
     *  enemy attached to it stays planted on it, including when the camera is stopped. */
    public float getLayerScrollSpeed(int index, float fallback) {
        if (index < 0 || index >= layers.size) return fallback;
        return layers.get(index).scrollSpeed * scrollSpeedScale;
    }

    /** Starts a layered draw (applies the hue-cycle shader if any). Pair with endLayeredDraw(). */
    public void beginLayeredDraw(SpriteBatch batch) {
        layeredDrawPreviousShader = hueCycleShader != null ? hueCycleShader.begin(batch) : null;
    }

    public void endLayeredDraw(SpriteBatch batch) {
        if (hueCycleShader != null) hueCycleShader.end(batch, layeredDrawPreviousShader);
    }

    /** Draws one layer's visible textures; call between begin/endLayeredDraw(). */
    public void drawLayer(SpriteBatch batch, int index) {
        Layer layer = layers.get(index);
        for (int i = 0; i < layer.textures.length; i++) {
            float y = layer.scrollY + layer.cumulativeTop[i];
            float height = layer.heights[i];
            if (y + height <= 0f || y >= worldHeight) continue;
            batch.draw(layer.textures[i], 0, y, worldWidth, height);
        }
    }

    /** Draws the video's current frame full-screen, or returns false if none is decoded yet. */
    private boolean drawVideoFrame(SpriteBatch batch, VideoPlayer player) {
        Texture frame = player.getTexture();
        if (frame == null) return false;
        // gdx-video pads its decode buffer, so map only the real video region to the screen.
        batch.draw(frame, 0, 0, worldWidth, worldHeight, 0, 0, player.getVideoWidth(), player.getVideoHeight(), false, false);
        return true;
    }

    public void reset() {
        stopped = false;
        for (Layer layer : layers) {
            layer.scrollY = 0f;
            layer.frozen = false;
        }
        bossVideoStarted = false;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
            bossVideoPlayer = null;
        }
        if (backgroundVideoPlayer != null) {
            backgroundVideoPlayer.dispose();
        }
        backgroundVideoPlayer = startVideo(backgroundVideoFile);
        backgroundVideoStarted = backgroundVideoPlayer != null;
        if (shaderBackground != null) shaderBackground.resetTime();
        if (hueCycleShader != null) hueCycleShader.resetTime();
        if (playerFeedback != null) playerFeedback.resetTime();
    }

    /** Sets layer scroll to where elapsedTime of scrolling would put it. Videos, the shader and the
     *  feedback trail restart instead (gdx-video has no seek and shader state can't be
     *  fast-forwarded); the stateless hue cycle jumps to the right hue. */
    public void seekTo(float elapsedTime) {
        stopped = false;
        for (Layer layer : layers) {
            layer.scrollY = layer.scrollSpeed * elapsedTime;
            layer.frozen = false;
            clampToTopOfStrip(layer);
        }
        bossVideoStarted = false;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
            bossVideoPlayer = null;
        }
        if (shaderBackground != null) shaderBackground.resetTime();
        if (hueCycleShader != null) hueCycleShader.setTime(elapsedTime);
        if (playerFeedback != null) playerFeedback.resetTime();
        if (backgroundVideoPlayer != null) {
            backgroundVideoPlayer.dispose();
        }
        backgroundVideoPlayer = startVideo(backgroundVideoFile);
        backgroundVideoStarted = backgroundVideoPlayer != null;
    }

    public void dispose() {
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
        }
        if (backgroundVideoPlayer != null) {
            backgroundVideoPlayer.dispose();
        }
        if (shaderBackground != null) {
            shaderBackground.dispose();
            shaderRenderer.dispose();
        }
        if (hueCycleShader != null) {
            hueCycleShader.dispose();
        }
        if (playerFeedback != null) {
            playerFeedback.dispose();
        }
    }
}
