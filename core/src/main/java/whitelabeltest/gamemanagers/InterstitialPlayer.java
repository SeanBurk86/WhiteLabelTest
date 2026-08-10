package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

import java.io.FileNotFoundException;

/** Full-screen, non-looping video shown once before a stage's gameplay begins - see
 *  GameController.startInterstitial()/update(). Deliberately excluded from ReplayRecorder's frame
 *  stream: which clip plays stays deterministic across a replay's record/playback (it's one more
 *  draw from the same seeded MathUtils.random stream everything else uses - see ReplayData), but
 *  skipping is always driven by live input, even while watching a replay, so cutting a video short
 *  never touches the recorded/replayed simulation - see GameController.update(). */
public class InterstitialPlayer {
    private VideoPlayer videoPlayer;
    private boolean active;
    private boolean completed;

    public boolean isActive() { return active; }

    /** Starts videoFile playing full-screen, once. No-ops (stays inactive) if the file can't be
     *  opened, so a bad/missing entry in interstitials.json degrades to "no video" instead of
     *  soft-locking the stage. */
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

    /** Cuts the video short - see GameController.update()'s confirm/shoot check. No-op if nothing's
     *  playing. */
    public void skip() {
        if (active) stop();
    }

    /** Releases the native video player if one is still mid-playback - see
     *  GameController.dispose(). No-op otherwise. */
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
        // Same padded-decode-buffer caveat as ScrollingBackground's boss video - only the real
        // video region maps onto the full screen quad.
        batch.draw(frame, 0, 0, worldWidth, worldHeight,
            0, 0, videoPlayer.getVideoWidth(), videoPlayer.getVideoHeight(), false, false);
    }
}
