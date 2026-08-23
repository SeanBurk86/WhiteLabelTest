package whitelabeltest.gamemanagers.background;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioSettings;
import whitelabeltest.gamemanagers.spawning.StageDefinition;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

public class ScrollingBackground {
    public static final float DEFAULT_SCROLL_SPEED = -1.25f;

    private static class Layer {
        final Texture texture;
        final float drawHeight;
        final float minScrollY;
        final float scrollSpeed;
        float scrollY;
        boolean frozen;

        Layer(Texture texture, float worldWidth, float worldHeight, float scrollSpeed) {
            this.texture = texture;
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            // Scale to the world width but keep the texture's native aspect ratio intact rather than stretching it.
            this.drawHeight = worldWidth * ((float) texture.getHeight() / texture.getWidth());
            // scrollY at which the image's top edge lines up with the top of the viewport - the point at which
            // there's no more image left to reveal, so scrolling further would leave blank space above it.
            this.minScrollY = worldHeight - drawHeight;
            this.scrollSpeed = scrollSpeed;
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
    private boolean stopped;
    private boolean muted;

    private VideoPlayer bossVideoPlayer;
    private boolean bossVideoStarted;
    private VideoPlayer backgroundVideoPlayer;
    private boolean backgroundVideoStarted;

    public ScrollingBackground(float worldWidth, float worldHeight, AudioSettings audioSettings, AssetManager assets,
                                Array<StageDefinition.BackgroundLayerDef> layerDefs, String bossVideoFile, String backgroundVideoFile,
                                String shaderBackgroundId) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;
        this.bossVideoFile = bossVideoFile;
        this.backgroundVideoFile = backgroundVideoFile;
        for (StageDefinition.BackgroundLayerDef layerDef : layerDefs) {
            Texture texture = assets.ensureTexture(layerDef.texture);
            float scrollSpeed = Float.isNaN(layerDef.scrollSpeed) ? DEFAULT_SCROLL_SPEED : layerDef.scrollSpeed;
            layers.add(new Layer(texture, worldWidth, worldHeight, scrollSpeed));
        }
        shaderBackground = createShaderBackground(shaderBackgroundId);
        shaderQuadTexture = shaderBackground != null ? assets.pixelTexture : null;
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
    }

    /** Freezes a layer's scrollY once its top edge reaches the top of the viewport, instead of scrolling past it. */
    private void clampToTopOfImage(Layer layer) {
        if (layer.scrollY <= layer.minScrollY) {
            layer.scrollY = layer.minScrollY;
            layer.frozen = true;
        }
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
        // Back-to-front: declaration order in the stage's backgroundLayers is far-to-near.
        for (Layer layer : layers) {
            batch.draw(layer.texture, 0, layer.scrollY, worldWidth, layer.drawHeight);
        }
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
    }

    /** Jumps the scroll position to where it would be after scrolling for elapsedTime seconds from reset(). */
    public void seekTo(float elapsedTime) {
        stopped = false;
        for (Layer layer : layers) {
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
    }
}
