package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;

public class ScrollingBackground {
    private VideoPlayer videoPlayer;
    private final float worldWidth;
    private final float worldHeight;
    private boolean stopped;

    public ScrollingBackground(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        startVideo();
    }

    private void startVideo() {
        if (videoPlayer != null) videoPlayer.dispose();

        videoPlayer = VideoPlayerCreator.createVideoPlayer();
        try {
            String videoFile = "level1.webm";
            videoPlayer.load(Gdx.files.internal(videoFile));
            videoPlayer.setLooping(true);
            videoPlayer.play();
        } catch (Exception e) {
            Gdx.app.error("Video", "Could not play background video file", e);
        }
    }

    /** Stops the background video (e.g. on game over) until reset() restarts it. */
    public void stop() {
        if (videoPlayer != null && !stopped) {
            videoPlayer.stop();
            stopped = true;
        }
    }

    public void update() {
        if (videoPlayer != null && !stopped) {
            videoPlayer.update();
        }
    }

    public void draw(SpriteBatch batch) {
        if (videoPlayer != null && !stopped) {
            Texture frame = videoPlayer.getTexture();
            if (frame != null) {
                batch.draw(frame, 0, 0, worldWidth, worldHeight);
            }
        }
    }

    public void reset() {
        stopped = false;
        startVideo();
    }

    public void dispose() {
        if (videoPlayer != null) {
            videoPlayer.dispose();
            videoPlayer = null;
        }
    }
}
