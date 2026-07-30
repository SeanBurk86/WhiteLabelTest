package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool;

/** Base for an effect that plays one animation once at a fixed world position then reports
 *  finished - shared by HitEffect (weapon-bullet impacts) and BulletCancelEffect (bullets
 *  destroyed outright by a bomb or an enemy's bullet-cancel death), which differ only in how they
 *  pick their animation. */
abstract class SingleShotAnimation implements Pool.Poolable {
    private Animation<TextureRegion> animation;
    private float x, y, size;
    private float stateTime;

    protected void init(Animation<TextureRegion> animation, float x, float y, float size) {
        this.animation = animation;
        this.x = x;
        this.y = y;
        this.size = size;
        this.stateTime = 0f;
    }

    public void update(float delta) {
        stateTime += delta;
    }

    public void draw(SpriteBatch batch) {
        TextureRegion frame = animation.getKeyFrame(stateTime);
        batch.draw(frame, x - size / 2f, y - size / 2f, size, size);
    }

    public boolean isFinished() {
        return animation.isAnimationFinished(stateTime);
    }

    @Override
    public void reset() {
        stateTime = 0f;
    }
}
