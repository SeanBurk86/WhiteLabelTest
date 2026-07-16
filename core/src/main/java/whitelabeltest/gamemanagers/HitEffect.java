package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool;

/** A one-shot animation played wherever a weapon's bullet lands a hit - see
 *  WeaponDefinition.hitTexture/hitSize/etc and CollisionManager.checkBulletEnemyCollisions,
 *  which spawns one per hit for any weapon whose definition sets a hit animation. */
public class HitEffect implements Pool.Poolable {
    private Animation<TextureRegion> animation;
    private float x, y, size;
    private float stateTime;

    public void init(Animation<TextureRegion> animation, float x, float y, float size) {
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
