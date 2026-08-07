package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

public class ScrollingBackground {
    private static final float SCROLL_SPEED = -1.25f;
    private static final String BOSS_VIDEO_FILE = "video/bossbacvk.webm";

    private final Texture texture;
    private final AudioSettings audioSettings;
    private final float worldWidth;
    private final float worldHeight;
    private final float drawHeight;
    private final float minScrollY;
    private float scrollY;
    private boolean stopped;
    private boolean muted;

    private VideoPlayer bossVideoPlayer;
    private boolean bossVideoStarted;

    public ScrollingBackground(float worldWidth, float worldHeight, AudioSettings audioSettings) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.audioSettings = audioSettings;
        this.texture = new Texture(Gdx.files.internal("images/backgrounds/bg1.png"));
        this.texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        // Scale to the world width but keep the texture's native aspect ratio intact rather than stretching it.
        this.drawHeight = worldWidth * ((float) texture.getHeight() / texture.getWidth());
        // scrollY at which the image's top edge lines up with the top of the viewport - the point at which
        // there's no more image left to reveal, so scrolling further would leave blank space above it.
        this.minScrollY = worldHeight - drawHeight;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.setVolume(muted ? 0f : audioSettings.getMusicVolume());
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
            scrollY += SCROLL_SPEED * Gdx.graphics.getDeltaTime();
            clampToTopOfImage();
        }
        if (bossVideoStarted) {
            bossVideoPlayer.update();
        }
    }

    /** Freezes scrollY once its top edge reaches the top of the viewport, instead of scrolling past it. */
    private void clampToTopOfImage() {
        if (scrollY <= minScrollY) {
            scrollY = minScrollY;
            stopped = true;
        }
    }

    /** Hands the background off from the scrolling image to the boss video (with its own audio) -
     *  called by GameController once the spawn schedule's backgroundVideoTime cue fires. Ignored if
     *  the video is already playing. */
    public void triggerBossVideo() {
        if (bossVideoStarted) return;
        bossVideoPlayer = VideoPlayerCreator.createVideoPlayer();
        bossVideoPlayer.setLooping(true);
        try {
            bossVideoPlayer.load(Gdx.files.internal(BOSS_VIDEO_FILE));
            bossVideoPlayer.setVolume(muted ? 0f : audioSettings.getMusicVolume());
            bossVideoPlayer.play();
            bossVideoStarted = true;
        } catch (FileNotFoundException e) {
            Gdx.app.error("ScrollingBackground", "Could not open " + BOSS_VIDEO_FILE, e);
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
        batch.draw(texture, 0, scrollY, worldWidth, drawHeight);
    }

    public void reset() {
        stopped = false;
        scrollY = 0f;
        bossVideoStarted = false;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
            bossVideoPlayer = null;
        }
    }

    /** Jumps the scroll position to where it would be after scrolling for elapsedTime seconds from reset(). */
    public void seekTo(float elapsedTime) {
        scrollY = SCROLL_SPEED * elapsedTime;
        stopped = false;
        bossVideoStarted = false;
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
            bossVideoPlayer = null;
        }
        clampToTopOfImage();
    }

    public void dispose() {
        texture.dispose();
        if (bossVideoPlayer != null) {
            bossVideoPlayer.dispose();
        }
    }
}
