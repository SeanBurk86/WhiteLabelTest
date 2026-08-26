package whitelabeltest.gamemanagers.background;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

public class ScrollingBackground {
    public static final float DEFAULT_SCROLL_SPEED = -1.25f;

    // A single texture, or (see StageDefinition.BackgroundLayerDef.textureSequence) several played
    // one after another as ONE continuously-scrolling layer, handing off to the next once the
    // current one is fully revealed instead of freezing there - see clampToTopOfImage().
    private static class Layer {
        final Texture[] textures;
        final float worldWidth, worldHeight;
        final float scrollSpeed;
        int currentIndex;
        Texture texture;
        float drawHeight;
        float minScrollY;
        float scrollY;
        boolean frozen;

        Layer(Texture[] textures, float worldWidth, float worldHeight, float scrollSpeed) {
            this.textures = textures;
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
            this.scrollSpeed = scrollSpeed;
            for (Texture t : textures) t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            setCurrentTexture(0);
        }

        // Switches to textures[index] and restarts this layer's scroll at its top - used both to
        // hand off between sequence entries (clampToTopOfImage()) and to (re)start a layer from its
        // first texture (reset()/seekTo()).
        void setCurrentTexture(int index) {
            currentIndex = index;
            texture = textures[index];
            // Scale to the world width but keep the texture's native aspect ratio intact rather than stretching it.
            drawHeight = worldWidth * ((float) texture.getHeight() / texture.getWidth());
            // scrollY at which the image's top edge lines up with the top of the viewport - the point
            // at which there's no more image left to reveal, so scrolling further would leave blank
            // space above it (a single-texture layer just freezes there - see clampToTopOfImage()).
            minScrollY = worldHeight - drawHeight;
            scrollY = 0f;
        }

        boolean isLastTexture() {
            return currentIndex == textures.length - 1;
        }
    }

    private final Array<Layer> layers = new Array<>();
    private final AudioSettings audioSettings;
    private final float worldWidth;
    private final float worldHeight;
    private final String bossVideoFile;
    // A stage-long looping video used AS the background from the moment the stage loads (unlike
    // bossVideoFile, which only cuts in later, on a spawn-schedule cue - see triggerBossVideo()) -
    // see StageDefinition.backgroundVideo/GameController.loadStage(). Stages that use this
    // typically have no (or few) backgroundLayers, since the video fully covers the screen.
    private final String backgroundVideoFile;
    // See StageDefinition.shaderBackground - a procedural full-screen effect standing in for
    // backgroundVideoFile, same "plays immediately, covers the whole screen" priority (checked
    // ahead of it in draw()/update() below since a stage only ever sets one or the other).
    private final BackgroundShader shaderBackground;
    private final Texture shaderQuadTexture;
    // See StageDefinition.hueCycleBackground - null unless this stage opted in. Independent of
    // shaderBackground: this color-shifts the ordinary Layer draws below, not a replacement for them.
    private final HueCycleShader hueCycleShader;
    private boolean stopped;
    private boolean muted;

    private VideoPlayer bossVideoPlayer;
    private boolean bossVideoStarted;
    private VideoPlayer backgroundVideoPlayer;
    private boolean backgroundVideoStarted;

    public ScrollingBackground(float worldWidth, float worldHeight, AudioSettings audioSettings, AssetManager assets,
                                Array<StageDefinition.BackgroundLayerDef> layerDefs, String bossVideoFile, String backgroundVideoFile,
                                String shaderBackgroundId, boolean hueCycleBackground) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;
        this.bossVideoFile = bossVideoFile;
        this.backgroundVideoFile = backgroundVideoFile;
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
        hueCycleShader = hueCycleBackground ? new HueCycleShader() : null;
        backgroundVideoPlayer = startVideo(backgroundVideoFile);
        backgroundVideoStarted = backgroundVideoPlayer != null;
    }

    /** See StageDefinition.shaderBackground for the id each stage sets - null means "no shader
     *  background" (the ordinary Layer/backgroundVideoFile path). Add a new stage's shader here as
     *  its own BackgroundShader implementation and a new id, rather than growing one shader class
     *  to cover every stage. */
    private static BackgroundShader createShaderBackground(String shaderBackgroundId) {
        if (shaderBackgroundId == null) return null;
        return switch (shaderBackgroundId) {
            case "boxTunnel" -> new TutorialBoxTunnelShader();
            case "kaleidoscope" -> new Stage2KaleidoscopeShader();
            default -> throw new IllegalArgumentException("Unknown shaderBackground id: " + shaderBackgroundId);
        };
    }

    /** Passes a stage's schedule-configured kaleidoscope-to-tentacles switchover time down to the
     *  shader background - see SpawnScheduler.getKaleidoscopeTransitionTime()/
     *  Stage2KaleidoscopeShader.setTransitionTime(). No-op if this stage's shaderBackground isn't
     *  "kaleidoscope" (or has none), so GameController can call this unconditionally after loading
     *  any stage without checking which one it got first. */
    public void setKaleidoscopeTransitionTime(float transitionTime) {
        if (shaderBackground instanceof Stage2KaleidoscopeShader kaleidoscope) {
            kaleidoscope.setTransitionTime(transitionTime);
        }
    }

    /** Passes a stage's schedule-configured boss-video cue time down as the hue cycle's period - see
     *  SpawnScheduler.getBackgroundVideoTime()/HueCycleShader.setPeriod(). No-op if this stage didn't
     *  set StageDefinition.hueCycleBackground, so GameController can call this unconditionally after
     *  loading any stage without checking which one it got first - same pattern as
     *  setKaleidoscopeTransitionTime(). */
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

    /** Stops the background scroll (e.g. on game over) until reset() restarts it. Also cuts the
     *  boss video's own audio if it was playing, so nothing competes with the victory/game-over
     *  theme that plays next - see AudioManager.playVictory()/playGameOver(). Deliberately leaves
     *  backgroundVideoFile's video alone - it's the stage's background, not a temporary crossfade,
     *  so (like a static Layer) it just keeps looping quietly underneath the game-over overlay. */
    public void stop() {
        stopped = true;
        if (bossVideoStarted) {
            bossVideoPlayer.stop();
            bossVideoStarted = false;
        }
    }

    public void update(float delta) {
        if (!stopped) {
            for (Layer layer : layers) {
                if (layer.frozen) continue;
                layer.scrollY += layer.scrollSpeed * delta;
                clampToTopOfImage(layer);
            }
        }
        if (bossVideoStarted) {
            bossVideoPlayer.update();
        }
        if (backgroundVideoStarted) {
            backgroundVideoPlayer.update();
        }
        // Keeps animating through stop() same as backgroundVideoFile does - see stop()'s own doc.
        if (shaderBackground != null) {
            shaderBackground.update(delta);
        }
        if (hueCycleShader != null) {
            hueCycleShader.update(delta);
        }
    }

    /** Freezes a layer's scrollY once its top edge reaches the top of the viewport, instead of
     *  scrolling past it - unless this is a sequence layer (see Layer.textures) not yet on its last
     *  texture, in which case it hands off to the next one instead: recurses so any overshoot from
     *  this texture carries into the next one's own scroll, in case that one wraps too (a big jump -
     *  e.g. seekTo() setting scrollY for a large elapsedTime in one shot, rather than update()'s tiny
     *  per-frame steps - could need to cascade through more than one handoff at once). */
    private void clampToTopOfImage(Layer layer) {
        if (layer.scrollY > layer.minScrollY) return;
        if (layer.isLastTexture()) {
            layer.scrollY = layer.minScrollY;
            layer.frozen = true;
            return;
        }
        float overshoot = layer.minScrollY - layer.scrollY;
        layer.setCurrentTexture(layer.currentIndex + 1);
        layer.scrollY = -overshoot;
        clampToTopOfImage(layer);
    }

    /** Hands the background off from the scrolling image to the boss video (with its own audio) -
     *  called by GameController once the spawn schedule's backgroundVideoTime cue fires. No-ops if
     *  this stage has no boss video. Ignored if the video is already playing. */
    public void triggerBossVideo() {
        if (bossVideoStarted || bossVideoFile == null) return;
        bossVideoPlayer = startVideo(bossVideoFile);
        bossVideoStarted = bossVideoPlayer != null;
    }

    /** Loads and starts file looping (with this instance's current mute state), or returns null
     *  (logging the failure) if it can't be opened - shared by both bossVideoFile (triggered later,
     *  on a schedule cue) and backgroundVideoFile (started immediately, in the constructor). null
     *  file is a plain no-op, no error logged: most stages have neither. */
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

    public void draw(SpriteBatch batch) {
        if (bossVideoStarted && drawVideoFrame(batch, bossVideoPlayer)) return;
        if (shaderBackground != null) {
            shaderBackground.render(batch, shaderQuadTexture, worldWidth, worldHeight);
            return;
        }
        if (backgroundVideoStarted && drawVideoFrame(batch, backgroundVideoPlayer)) return;
        beginLayeredDraw(batch);
        // Back-to-front: declaration order in the stage's backgroundLayers is far-to-near.
        for (int i = 0; i < layers.size; i++) drawLayer(batch, i);
        endLayeredDraw(batch);
    }

    // Set by beginLayeredDraw(), consumed by the matching endLayeredDraw() - see those.
    private ShaderProgram layeredDrawPreviousShader;

    /** True while the ordinary Layer stack (drawLayer()/getLayerCount() below) is actually what's
     *  on screen, rather than a boss video, the stage-long background video, or a procedural
     *  shader background covering the whole screen instead - mirrors draw()'s own short-circuits
     *  above without any drawing side effects. GameController.draw() checks this before attempting
     *  to sandwich an EnemyDefinition.backgroundLayer-attached enemy's draw between two layers -
     *  there's no layer stack to sandwich anything between otherwise, so it falls back to drawing
     *  every enemy the ordinary way (see EntityManager.draw()'s skipLayerAttached param). */
    public boolean isDrawingLayerStack() {
        if (bossVideoStarted && bossVideoPlayer.getTexture() != null) return false;
        if (shaderBackground != null) return false;
        if (backgroundVideoStarted && backgroundVideoPlayer.getTexture() != null) return false;
        return true;
    }

    /** Number of ordinary background layers (declaration order in the stage's backgroundLayers -
     *  see draw()'s "far-to-near" doc) - see drawLayer()/getLayerScrollSpeed() and
     *  EnemyDefinition.backgroundLayer, which indexes into this same order. */
    public int getLayerCount() { return layers.size; }

    /** backgroundLayers[index]'s current scrollSpeed, or fallback if index is out of range for
     *  this stage's actual layer count - see EnemyDefinition.backgroundLayer/EntityManager's
     *  ground-scroll resolution, which lets a ground enemy move at a SPECIFIC layer's speed
     *  instead of the schedule-wide SpawnScheduler.groundScrollSpeed, so it stays visually planted
     *  on whichever layer it's actually drawn against (e.g. a closer parallax layer scrolling
     *  faster than the base background) rather than always the same single ground speed regardless
     *  of attachment. Falling back rather than throwing lets one enemy definition be safely reused
     *  across stages with different numbers of background layers. */
    public float getLayerScrollSpeed(int index, float fallback) {
        if (index < 0 || index >= layers.size) return fallback;
        return layers.get(index).scrollSpeed;
    }

    /** Begins the hue-cycle shader's begin/end wrap (see HueCycleShader) around one or more
     *  drawLayer() calls - draw()'s own normal full-stack pass above, or GameController's
     *  interleaved pass sandwiching EnemyDefinition.backgroundLayer-attached enemy draws between
     *  individual layers. No-op if this stage has no hue-cycle shader. Must be paired with a
     *  matching endLayeredDraw() once every drawLayer() call for this pass is done. */
    public void beginLayeredDraw(SpriteBatch batch) {
        layeredDrawPreviousShader = hueCycleShader != null ? hueCycleShader.begin(batch) : null;
    }

    public void endLayeredDraw(SpriteBatch batch) {
        if (hueCycleShader != null) hueCycleShader.end(batch, layeredDrawPreviousShader);
    }

    /** Draws backgroundLayers[index] on its own - must be called between beginLayeredDraw()/
     *  endLayeredDraw(). Only valid while isDrawingLayerStack() is true. */
    public void drawLayer(SpriteBatch batch, int index) {
        Layer layer = layers.get(index);
        batch.draw(layer.texture, 0, layer.scrollY, worldWidth, layer.drawHeight);
    }

    /** Draws player's current decoded frame full-screen and returns true, or returns false (drawing
     *  nothing) if no frame has been decoded yet - callers fall through to whatever's behind the
     *  video (another video, or the ordinary Layer stack) for those first few frames. */
    private boolean drawVideoFrame(SpriteBatch batch, VideoPlayer player) {
        Texture frame = player.getTexture();
        if (frame == null) return false;
        // gdx-video pads its decode buffer to the right (frame.getWidth() can exceed the real video
        // width), so drawing the whole texture stretches that padding across the screen too,
        // squeezing the actual picture a couple pixels narrower than it should be. Only the real
        // video region maps onto the full screen quad.
        batch.draw(frame, 0, 0, worldWidth, worldHeight, 0, 0, player.getVideoWidth(), player.getVideoHeight(), false, false);
        return true;
    }

    public void reset() {
        stopped = false;
        for (Layer layer : layers) {
            layer.setCurrentTexture(0);
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
    }

    /** Jumps the scroll position to where it would be after scrolling for elapsedTime seconds from reset(). */
    public void seekTo(float elapsedTime) {
        stopped = false;
        for (Layer layer : layers) {
            // Recomputes drawHeight/minScrollY for the FIRST texture before scoring elapsedTime
            // against it - clampToTopOfImage() then cascades through as many further textures as
            // elapsedTime's distance actually covers (see its own doc), correctly landing a
            // sequence layer on whichever entry - and scroll position within it - a real
            // reset()-then-scrolled-for-elapsedTime run would have reached.
            layer.setCurrentTexture(0);
            layer.scrollY = layer.scrollSpeed * elapsedTime;
            layer.frozen = false;
            clampToTopOfImage(layer);
        }
        bossVideoStarted = false;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
            bossVideoPlayer = null;
        }
        // Same "no seek API, jump to the top of its own loop instead" compromise the video comment
        // below describes - the shader has no persistent state to fast-forward either, so this just
        // restarts its clock at 0 rather than approximating elapsedTime seconds of animation.
        if (shaderBackground != null) shaderBackground.resetTime();
        // Unlike shaderBackground, this shader has no accumulated pixel state - just time % period -
        // so it can jump straight to the correct hue instead of restarting the cycle from 0.
        if (hueCycleShader != null) hueCycleShader.setTime(elapsedTime);
        // gdx-video has no seek API, so a debug/replay jump to elapsedTime can't fast-forward the
        // background video to match - it just restarts from the top, same as reset().
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
        }
        if (hueCycleShader != null) {
            hueCycleShader.dispose();
        }
    }
}
