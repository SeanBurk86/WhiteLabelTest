package whitelabeltest.gamemanagers;

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
    private boolean stopped;
    private boolean muted;

    private VideoPlayer bossVideoPlayer;
    private boolean bossVideoStarted;

    public ScrollingBackground(float worldWidth, float worldHeight, AudioSettings audioSettings, AssetManager assets,
                                Array<StageDefinition.BackgroundLayerDef> layerDefs, String bossVideoFile) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;
        this.bossVideoFile = bossVideoFile;
        for (StageDefinition.BackgroundLayerDef layerDef : layerDefs) {
            Texture texture = assets.ensureTexture(layerDef.texture);
            float scrollSpeed = Float.isNaN(layerDef.scrollSpeed) ? DEFAULT_SCROLL_SPEED : layerDef.scrollSpeed;
            layers.add(new Layer(texture, worldWidth, worldHeight, scrollSpeed));
        }
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.setVolume(muted ? 0f : audioSettings.getEffectiveMusicVolume());
        }
    }

    /** Stops the background scroll (e.g. on game over) until reset() restarts it. Also cuts the
     *  boss video's own audio if it was playing, so nothing competes with the victory/game-over
     *  theme that plays next - see AudioManager.playVictory()/playGameOver(). */
    public void stop() {
        stopped = true;
        if (bossVideoStarted) {
            bossVideoPlayer.stop();
            bossVideoStarted = false;
        }
    }

    public void update() {
        if (!stopped) {
            float delta = Gdx.graphics.getDeltaTime();
            for (Layer layer : layers) {
                if (layer.frozen) continue;
                layer.scrollY += layer.scrollSpeed * delta;
                clampToTopOfImage(layer);
            }
        }
        if (bossVideoStarted) {
            bossVideoPlayer.update();
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
        bossVideoPlayer = VideoPlayerCreator.createVideoPlayer();
        bossVideoPlayer.setLooping(true);
        try {
            bossVideoPlayer.load(Gdx.files.internal(bossVideoFile));
            bossVideoPlayer.setVolume(muted ? 0f : audioSettings.getEffectiveMusicVolume());
            bossVideoPlayer.play();
            bossVideoStarted = true;
        } catch (FileNotFoundException e) {
            Gdx.app.error("ScrollingBackground", "Could not open " + bossVideoFile, e);
        }
    }

    public void draw(SpriteBatch batch) {
        if (bossVideoStarted) {
            Texture frame = bossVideoPlayer.getTexture();
            if (frame != null) {
                // gdx-video pads its decode buffer to the right (frame.getWidth() can exceed the
                // real video width), so drawing the whole texture stretches that padding across
                // the screen too, squeezing the actual picture a couple pixels narrower than it
                // should be. Only the real video region maps onto the full screen quad.
                batch.draw(frame, 0, 0, worldWidth, worldHeight,
                    0, 0, bossVideoPlayer.getVideoWidth(), bossVideoPlayer.getVideoHeight(), false, false);
                return;
            }
        }
        // Back-to-front: declaration order in the stage's backgroundLayers is far-to-near.
        for (Layer layer : layers) {
            batch.draw(layer.texture, 0, layer.scrollY, worldWidth, layer.drawHeight);
        }
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
    }

    public void dispose() {
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
        }
    }
}
