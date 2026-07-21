package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;

/** A point pickup spawned when an enemy dies (see GameController.destroyEnemy - one per 10 of
 *  the enemy's max health). Pops up in a random mostly-upward direction and falls under gravity;
 *  while the player isn't firing, it homes in on the player's graze hitbox instead of falling.
 *  CollisionManager.checkPlayerGemCollisions awards a flat score bonus and removes it on contact. */
public class PointGem implements Pool.Poolable {
    private static final float SIZE = 0.3f;
    private static final float GRAVITY = -9f;
    private static final float HOMING_ACCEL = 14f;
    private static final float HOMING_MAX_SPEED = 9f;

    private final Rectangle rectangle = new Rectangle();
    private Animation<TextureRegion> animation;
    private float stateTime;
    private float vx, vy;
    private float worldWidth, worldHeight;

    public void init(Animation<TextureRegion> animation, float x, float y, float worldWidth, float worldHeight) {
        this.animation = animation;
        this.stateTime = 0f;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        rectangle.set(x - SIZE / 2f, y - SIZE / 2f, SIZE, SIZE);

        // Mostly-upward pop with some horizontal spread, rather than a perfectly random direction.
        float angle = MathUtils.random(20f, 160f);
        float speed = MathUtils.random(2.5f, 4.5f);
        vx = MathUtils.cosDeg(angle) * speed;
        vy = MathUtils.sinDeg(angle) * speed;
    }

    public void update(float delta, boolean playerFiring, Circle grazeHitbox) {
        stateTime += delta;

        if (!playerFiring && grazeHitbox.radius > 0f) {
            float centerX = rectangle.x + rectangle.width / 2f;
            float centerY = rectangle.y + rectangle.height / 2f;
            float dx = grazeHitbox.x - centerX;
            float dy = grazeHitbox.y - centerY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0.0001f) {
                vx += (dx / dist) * HOMING_ACCEL * delta;
                vy += (dy / dist) * HOMING_ACCEL * delta;
                float speed = (float) Math.sqrt(vx * vx + vy * vy);
                if (speed > HOMING_MAX_SPEED) {
                    vx = vx / speed * HOMING_MAX_SPEED;
                    vy = vy / speed * HOMING_MAX_SPEED;
                }
            }
        } else {
            vy += GRAVITY * delta;
        }

        rectangle.x += vx * delta;
        rectangle.y += vy * delta;
    }

    public void draw(SpriteBatch batch) {
        TextureRegion frame = animation.getKeyFrame(stateTime);
        batch.draw(frame, rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

    public boolean isOffScreen() {
        return rectangle.y + rectangle.height < -1f || rectangle.y > worldHeight + 1f
            || rectangle.x + rectangle.width < -1f || rectangle.x > worldWidth + 1f;
    }

    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public void reset() {
        vx = 0f;
        vy = 0f;
        stateTime = 0f;
    }
}
