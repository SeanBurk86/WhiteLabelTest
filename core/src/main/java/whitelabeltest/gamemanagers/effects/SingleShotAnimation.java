package whitelabeltest.gamemanagers.effects;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool;

/** An animation played once, centered at a fixed position (hit, bullet-cancel and sprite cue effects). */
abstract class SingleShotAnimation implements Pool.Poolable {
    private Animation<TextureRegion> animation;
    private float x, y, width, height;
    private float stateTime;

    public void init(Animation<TextureRegion> animation, float x, float y, float size) {
        init(animation, x, y, size, size);
    }

    // Non-square variant (e.g. wide sprite cue art).
    public void init(Animation<TextureRegion> animation, float x, float y, float width, float height) {
        this.animation = animation;
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
        TextureRegion frame = animation.getKeyFrame(stateTime);
        batch.draw(frame, x - width / 2f, y - height / 2f, width, height);
    }

    public boolean isFinished() {
        return animation.isAnimationFinished(stateTime);
    }

    @Override
    public void reset() {
        stateTime = 0f;
    }
}
