package whitelabeltest.gamemanagers.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool;

public class PlayerTrailEffect implements Pool.Poolable {
    private static final float LIFETIME = 0.5f;

    private final TextureRegion frame = new TextureRegion();
    private float x, y, width, height;
    private float stateTime;

    public void init(TextureRegion sourceFrame, float x, float y, float width, float height) {
        frame.setRegion(sourceFrame);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.stateTime = 0f;
    }

    public void update(float delta) {
        stateTime += delta;
    }

    public void draw(SpriteBatch batch) {
        float alpha = Math.max(0f, 1f - stateTime / LIFETIME) * 0.4f;
        batch.setColor(0.6f, 0.85f, 1f, alpha);
        batch.draw(frame, x, y, width, height);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    public boolean isFinished() {
        return stateTime >= LIFETIME;
    }

    @Override
    public void reset() {
        stateTime = 0f;
    }
}
