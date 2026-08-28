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

public class ScrollingBackground {
    public static final float DEFAULT_SCROLL_SPEED = -1.25f;

    // A single texture, or (see StageDefinition.BackgroundLayerDef.textureSequence) several stacked
    // bottom-to-top into ONE tall continuous strip (textures[0]'s bottom edge is the strip's own
    // bottom, textures[i]'s top edge = textures[i+1]'s bottom edge, and so on) and scrolled through
    // as a single unit - see cumulativeTop's/scrollY's own doc below. drawLayer() draws whichever of the strip's
    // textures currently overlap the viewport (ordinarily one, briefly two at a time while the
    // viewport straddles a seam between two of them), so consecutive images' ends visibly meet with
    // no gap and no hard cut - unlike an earlier version of this that swapped to a single "current"
    // texture at a time, which could only ever pick one of "leave a gap while the new one scrolls
    // into place" or "restart it from scratch, cutting the content," neither of which is a real
    // seamless splice.
    private static class Layer {
        final Texture[] textures;
        // heights[i] is textures[i]'s own drawn height (worldWidth * its aspect ratio - scaled to the
        // world width, native aspect ratio otherwise). cumulativeTop[i] is the summed height of every
        // texture BEFORE i - i.e. textures[i]'s own position within the strip's local coordinate
        // space (local Y 0 = the strip's bottom, at textures[0]'s own bottom edge) is
        // [cumulativeTop[i], cumulativeTop[i] + heights[i]]. Both precomputed once in the constructor
        // since neither ever changes after a texture is loaded.
        final float[] heights;
        final float[] cumulativeTop;
        final float totalHeight;
        final float worldWidth, worldHeight;
        final float scrollSpeed;
        // scrollY at which the FULL strip's top edge (not any one texture's) lines up with the top of
        // the viewport - the point at which there's no more of the strip left to reveal, so scrolling
        // further would leave blank space above it (the layer just freezes there - see update()).
        final float minScrollY;
        // World-Y position of the strip's own local Y=0 (textures[0]'s bottom edge) - i.e. textures[i]
        // is drawn at world-Y [scrollY + cumulativeTop[i], scrollY + cumulativeTop[i] + heights[i]] -
        // see drawLayer(). Decreases over time (scrollSpeed is normally negative), same sign
        // convention a single-texture layer's scrollY always used.
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
    // See StageDefinition.playerFeedbackBackground - null unless this stage opted in. Independent of
    // shaderBackground/hueCycleShader: this overlays a trail on top of ALL background content
    // (drawBaseContent()'s result, whichever branch it took), not a replacement for any of it - see
    // draw()/drawPlayerFeedbackOverlay().
    private final PlayerFeedbackShader playerFeedback;
    private final Texture playerFeedbackQuadTexture;
    private boolean stopped;
    private boolean muted;

    private VideoPlayer bossVideoPlayer;
    private boolean bossVideoStarted;
    private VideoPlayer backgroundVideoPlayer;
    private boolean backgroundVideoStarted;

    public ScrollingBackground(float worldWidth, float worldHeight, AudioSettings audioSettings, AssetManager assets,
                                Array<StageDefinition.BackgroundLayerDef> layerDefs, String bossVideoFile, String backgroundVideoFile,
                                String shaderBackgroundId, boolean hueCycleBackground, boolean playerFeedbackBackground) {
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
        playerFeedback = playerFeedbackBackground ? new PlayerFeedbackShader() : null;
        playerFeedbackQuadTexture = playerFeedback != null ? assets.pixelTexture : null;
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

    /** Forwards this frame's player sprite/position to the feedback overlay - see
     *  PlayerFeedbackShader.updatePlayer(). No-op unless this stage set
     *  StageDefinition.playerFeedbackBackground, so GameController can call this unconditionally every
     *  frame without checking first - same pattern as setKaleidoscopeTransitionTime(). Must be called
     *  before draw() (or the interleaved beginLayeredDraw()/drawLayer()/endLayeredDraw()/
     *  drawPlayerFeedbackOverlay() sequence) each frame so this frame's player position is what
     *  actually gets composited, not last frame's. */
    public void updatePlayer(TextureRegion frame, float x, float y, float width, float height) {
        if (playerFeedback != null) playerFeedback.updatePlayer(frame, x, y, width, height);
    }

    /** Forwards this frame's player HALO sprite/position to the feedback overlay - see
     *  PlayerFeedbackShader.updateHalo(). Same "no-op unless opted in, call before draw()" contract as
     *  updatePlayer() above. */
    public void updateHalo(TextureRegion frame, float x, float y, float width, float height) {
        if (playerFeedback != null) playerFeedback.updateHalo(frame, x, y, width, height);
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
                clampToTopOfStrip(layer);
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
        if (playerFeedback != null) {
            playerFeedback.update(delta, feedbackScrollSpeed());
        }
    }

    /** A representative scroll speed for PlayerFeedbackShader's trail to drift downward in sync with -
     *  see StageDefinition.playerFeedbackBackground/PlayerFeedbackShader.update(). Uses this stage's
     *  first backgroundLayers entry (declaration order - see draw()'s "far-to-near" note, so typically
     *  the farthest-back layer) if it has any; video/shader backgrounds have no comparable linear
     *  scroll rate of their own to read instead, so a stage with no layers just falls back to
     *  DEFAULT_SCROLL_SPEED rather than leaving the trail static. */
    private float feedbackScrollSpeed() {
        return layers.size > 0 ? layers.first().scrollSpeed : DEFAULT_SCROLL_SPEED;
    }

    /** Freezes a layer's scrollY once the FULL strip's top edge (see Layer's own class doc -
     *  totalHeight across every texture in the sequence, not any one texture's own height) reaches
     *  the top of the viewport, instead of scrolling past it and leaving blank space above. Unlike an
     *  earlier version of this, there's no per-texture handoff step here at all: drawLayer() below
     *  draws directly from scrollY against each texture's own position in the strip every frame, so
     *  consecutive textures are simply always exactly where the strip geometry puts them - "handoff"
     *  isn't a distinct event that needs its own clamping/carry-over logic, it just falls out of
     *  drawLayer() picking up a texture as soon as scrollY brings it into view. */
    private void clampToTopOfStrip(Layer layer) {
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

    /** Draws this stage's actual background content, then (see StageDefinition.playerFeedbackBackground)
     *  overlays the feedback trail on top of it - see drawPlayerFeedbackOverlay(). GameController's
     *  interleaved layer-stack path (background-attached enemies sandwiched between individual
     *  drawLayer() calls) doesn't go through this method at all - it calls drawBaseContent()'s pieces
     *  (beginLayeredDraw()/drawLayer()/endLayeredDraw()) and drawPlayerFeedbackOverlay() itself instead,
     *  so the overlay still applies there too. */
    public void draw(SpriteBatch batch) {
        drawBaseContent(batch);
        drawPlayerFeedbackOverlay(batch);
    }

    private void drawBaseContent(SpriteBatch batch) {
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

    /** Draws PlayerFeedbackShader's trail on top of whatever background content was just drawn - see
     *  StageDefinition.playerFeedbackBackground. No-op if this stage didn't opt in. Public (not just
     *  called from draw()) so GameController's interleaved layer-stack path can call it once its own
     *  beginLayeredDraw()/drawLayer()/endLayeredDraw() sequence is done, since that path never calls
     *  draw() itself. */
    public void drawPlayerFeedbackOverlay(SpriteBatch batch) {
        if (playerFeedback != null) playerFeedback.renderOverlay(batch, playerFeedbackQuadTexture, worldWidth, worldHeight);
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
     *  endLayeredDraw(). Only valid while isDrawingLayerStack() is true. Draws every one of this
     *  layer's textures that currently overlaps the viewport - see Layer's own class doc - which is
     *  ordinarily just one, but briefly two while the viewport straddles the seam between a texture
     *  sequence's consecutive entries, so both halves of the seam are visible at once with no gap. */
    public void drawLayer(SpriteBatch batch, int index) {
        Layer layer = layers.get(index);
        for (int i = 0; i < layer.textures.length; i++) {
            float y = layer.scrollY + layer.cumulativeTop[i];
            float height = layer.heights[i];
            if (y + height <= 0f || y >= worldHeight) continue;
            batch.draw(layer.textures[i], 0, y, worldWidth, height);
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
        if (hueCycleShader != null) hueCycleShader.resetTime();
        if (playerFeedback != null) playerFeedback.resetTime();
    }

    /** Jumps the scroll position to where it would be after scrolling for elapsedTime seconds from reset(). */
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
        // Same "no seek API, jump to the top of its own loop instead" compromise the video comment
        // below describes - the shader has no persistent state to fast-forward either, so this just
        // restarts its clock at 0 rather than approximating elapsedTime seconds of animation.
        if (shaderBackground != null) shaderBackground.resetTime();
        // Unlike shaderBackground, this shader has no accumulated pixel state - just time % period -
        // so it can jump straight to the correct hue instead of restarting the cycle from 0.
        if (hueCycleShader != null) hueCycleShader.setTime(elapsedTime);
        // Same "no seek API" compromise as shaderBackground above - the accumulation buffer can't be
        // fast-forwarded either, so this just restarts its clock (and drops the stale trail) at 0.
        if (playerFeedback != null) playerFeedback.resetTime();
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
        if (playerFeedback != null) {
            playerFeedback.dispose();
        }
    }
}
