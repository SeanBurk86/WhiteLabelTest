package whitelabeltest.gamemanagers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class ScrollingBackground {
    private static final float SCROLL_SPEED = -1.25f;

    private final Texture texture;
    private final float worldWidth;
    private final float drawHeight;
    private final float minScrollY;
    private float scrollY;
    private boolean stopped;

    public ScrollingBackground(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.texture = new Texture(Gdx.files.internal("bg1.png"));
        this.texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        // Scale to the world width but keep the texture's native aspect ratio intact rather than stretching it.
        this.drawHeight = worldWidth * ((float) texture.getHeight() / texture.getWidth());
        // scrollY at which the image's top edge lines up with the top of the viewport - the point at which
        // there's no more image left to reveal, so scrolling further would leave blank space above it.
        this.minScrollY = worldHeight - drawHeight;
    }

    public void setMuted(boolean muted) {
        // No audio on the background image; kept for API compatibility with the mute toggle.
    }

    /** Stops the background scroll (e.g. on game over) until reset() restarts it. */
    public void stop() {
        stopped = true;
    }

    public void update() {
        if (!stopped) {
            scrollY += SCROLL_SPEED * Gdx.graphics.getDeltaTime();
            clampToTopOfImage();
        }
    }

    /** Freezes scrollY once its top edge reaches the top of the viewport, instead of scrolling past it. */
    private void clampToTopOfImage() {
        if (scrollY <= minScrollY) {
            scrollY = minScrollY;
            stopped = true;
        }
    }

    public void draw(SpriteBatch batch) {
        batch.draw(texture, 0, scrollY, worldWidth, drawHeight);
    }

    public void reset() {
        stopped = false;
        scrollY = 0f;
    }

    /** Jumps the scroll position to where it would be after scrolling for elapsedTime seconds from reset(). */
    public void seekTo(float elapsedTime) {
        scrollY = SCROLL_SPEED * elapsedTime;
        stopped = false;
        clampToTopOfImage();
    }

    public void dispose() {
        texture.dispose();
    }
}
