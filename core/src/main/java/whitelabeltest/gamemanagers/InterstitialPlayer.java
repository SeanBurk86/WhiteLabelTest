package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

/** Full-screen video played once before a stage. The clip choice comes from the seeded random stream
 *  (deterministic in replays), but skipping uses live input and is kept out of the replay entirely. */
public class InterstitialPlayer {
    private VideoPlayer videoPlayer;
    private boolean active;
    private boolean completed;

    public boolean isActive() { return active; }

    /** Plays videoFile once. Stays inactive if it can't be opened, so a bad entry just skips it. */
    public void play(String videoFile, float volume) {
        videoPlayer = VideoPlayerCreator.createVideoPlayer();
        videoPlayer.setLooping(false);
        completed = false;
        try {
            videoPlayer.load(Gdx.files.internal(videoFile));
            videoPlayer.setVolume(volume);
            videoPlayer.setOnCompletionListener(file -> completed = true);
            videoPlayer.play();
            active = true;
        } catch (FileNotFoundException e) {
            Gdx.app.error("InterstitialPlayer", "Could not open " + videoFile, e);
            videoPlayer.dispose();
            videoPlayer = null;
            active = false;
        }
    }

    public void update(float delta) {
        if (!active) return;
        videoPlayer.update();
        if (completed) stop();
    }

    /** Cuts the video short. */
    public void skip() {
        if (active) stop();
    }

    /** Releases the native video player if still playing. */
    public void dispose() {
        if (active) stop();
    }

    private void stop() {
        active = false;
        videoPlayer.dispose();
        videoPlayer = null;
    }

    public void draw(SpriteBatch batch, float worldWidth, float worldHeight) {
        if (!active) return;
        Texture frame = videoPlayer.getTexture();
        if (frame == null) return;
        // The decode buffer may be padded; map only the real video region to the screen.
        batch.draw(frame, 0, 0, worldWidth, worldHeight,
            0, 0, videoPlayer.getVideoWidth(), videoPlayer.getVideoHeight(), false, false);
    }
}
