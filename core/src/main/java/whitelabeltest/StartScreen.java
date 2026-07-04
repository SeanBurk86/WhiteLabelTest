package whitelabeltest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.video.VideoPlayer;
import com.badlogic.gdx.video.VideoPlayerCreator;
import whitelabeltest.gamemanagers.InputType;

import java.io.FileNotFoundException;

public class StartScreen implements Disposable {
    private enum Phase { SELECTING, FADING, DONE }

    private static final float FADE_DURATION = 0.7f;
    private static final String CONFIRM_SOUND = "bgaregga-073.wav";

    private static final int[] KEYBOARD_DETECT_KEYS = {
        Input.Keys.SPACE, Input.Keys.ENTER, Input.Keys.Z, Input.Keys.X,
        Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP, Input.Keys.DOWN,
        Input.Keys.W, Input.Keys.A, Input.Keys.S, Input.Keys.D,
        Input.Keys.SHIFT_LEFT, Input.Keys.CONTROL_LEFT
    };

    private final VideoPlayer videoPlayer;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final Texture fadePixel;
    private final float worldWidth, worldHeight;

    private InputType detectedInput;
    private Phase phase = Phase.SELECTING;
    private float fadeTimer;
    private boolean prevAnyButtonDown;

    // Kept alive after this screen is disposed (see getConfirmSound()) so the cue can keep
    // playing while GameController loads; the caller is responsible for disposing it eventually.
    private Sound confirmSound;

    public StartScreen(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        videoPlayer = VideoPlayerCreator.createVideoPlayer();
        videoPlayer.setLooping(true);
        try {
            videoPlayer.load(Gdx.files.internal("openingscreen.webm"));
            videoPlayer.play();
        } catch (FileNotFoundException e) {
            Gdx.app.error("StartScreen", "Could not open openingscreen.webm", e);
        }

        font = new BitmapFont();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.025f);
        layout = new GlyphLayout();

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        fadePixel = new Texture(pm);
        pm.dispose();
    }

    /** Returns the chosen InputType once the fade and sound cue finish, null otherwise. */
    public InputType update(float delta) {
        videoPlayer.update();

        switch (phase) {
            case SELECTING:
                if (anyKeyJustPressed()) {
                    detectedInput = InputType.KEYBOARD;
                    phase = Phase.FADING;
                } else {
                    Controller c = Controllers.getCurrent();
                    if (c != null && anyButtonJustPressed(c)) {
                        detectedInput = InputType.GAMEPAD;
                        phase = Phase.FADING;
                    }
                }
                break;

            case FADING:
                fadeTimer += delta;
                if (fadeTimer >= FADE_DURATION) {
                    // Sound plays while game initialises; kept in a field (not disposed here)
                    // so it isn't cut off the moment this screen is torn down. See getConfirmSound().
                    confirmSound = Gdx.audio.newSound(Gdx.files.internal(CONFIRM_SOUND));
                    confirmSound.play();
                    phase = Phase.DONE;
                }
                break;

            case DONE:
                return detectedInput;
        }
        return null;
    }

    public void draw(SpriteBatch batch) {
        // Video frame
        Texture frame = videoPlayer.getTexture();
        if (frame != null) {
            batch.setColor(Color.WHITE);
            batch.draw(frame, 0, 0, worldWidth, worldHeight);
        }
        if (phase == Phase.SELECTING) {
            float cx = worldWidth / 2f;
        }
        if (phase == Phase.FADING || phase == Phase.DONE) {
            float alpha = Math.min(fadeTimer / FADE_DURATION, 1f);
            batch.setColor(0f, 0f, 0f, alpha);
            batch.draw(fadePixel, 0, 0, worldWidth, worldHeight);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawCentered(SpriteBatch batch, String text, float cx, float y) {
        layout.setText(font, text);
        font.draw(batch, layout, cx - layout.width / 2f, y);
    }

    private boolean anyKeyJustPressed() {
        for (int key : KEYBOARD_DETECT_KEYS) {
            if (Gdx.input.isKeyJustPressed(key)) return true;
        }
        return false;
    }

    private boolean anyButtonJustPressed(Controller controller) {
        int[] buttons = {
            controller.getMapping().buttonA,
            controller.getMapping().buttonB,
            controller.getMapping().buttonX,
            controller.getMapping().buttonY,
            controller.getMapping().buttonStart,
            controller.getMapping().buttonR1,
            controller.getMapping().buttonL1,
            controller.getMapping().buttonDpadUp,
            controller.getMapping().buttonDpadDown,
            controller.getMapping().buttonDpadLeft,
            controller.getMapping().buttonDpadRight,
        };
        boolean anyDown = false;
        for (int button : buttons) {
            if (controller.getButton(button)) { anyDown = true; break; }
        }
        boolean justPressed = !prevAnyButtonDown && anyDown;
        prevAnyButtonDown = anyDown;
        return justPressed;
    }

    /** Returns the fire-and-forget confirm sound so the caller can dispose it once it's safe to
     * cut off (e.g. at app shutdown). Never disposed here, since this screen is torn down while
     * the sound is still meant to be playing. May be null if the fade never completed. */
    public Sound getConfirmSound() {
        return confirmSound;
    }

    @Override
    public void dispose() {
        videoPlayer.dispose();
        font.dispose();
        fadePixel.dispose();
    }
}
