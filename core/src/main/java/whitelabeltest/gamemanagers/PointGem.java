package whitelabeltest.gamemanagers;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;

/** A point pickup spawned when an enemy dies (see GameController.destroyEnemy - one per
 *  GameBalance.gemsPerEnemyHealth of the enemy's max health). Pops up in a random mostly-upward
 *  direction and falls under gravity;
 *  the instant the player isn't firing, it homes in on the player's graze hitbox (the "graze
 *  halo" - see CollisionManager.checkPlayerGemCollisions) instead of falling. Holding the fire
 *  button keeps it falling, so gems can still be farmed by hosing them down rather than collected
 *  automatically.
 *  Homing itself is two-phase: for its first STEERING_SWITCH_DELAY seconds it accelerates its
 *  existing velocity toward the target (the original organic swoop-in), then hands off to a
 *  speed-scalar re-aimed fresh every frame - accelerating the same vector indefinitely lets a gem
 *  that's built up enough momentum swing past the target and settle into an orbit instead of ever
 *  converging, so the switch guarantees it eventually arrives.
 *  CollisionManager.checkPlayerGemCollisions awards a flat score bonus and removes it on contact. */
public class PointGem implements Pool.Poolable {
    private static final float SIZE = 0.3f;
    private static final float GRAVITY = -9f;
    private static final float HOMING_ACCEL = 90f;
    private static final float HOMING_MAX_SPEED = 65f;
    private static final float STEERING_SWITCH_DELAY = 2f;

    private final Rectangle rectangle = new Rectangle();
    private Animation<TextureRegion> animation;
    private float stateTime;
    private float vx, vy;
    // Homing speed scalar - re-applied along the fresh direction-to-player every frame (see
    // update()) rather than accelerating the existing vx/vy vector. Accelerating the existing
    // vector let the gem's momentum outrun the steering correction and settle into an orbit around
    // the player instead of converging on it; re-aiming a speed scalar at the current direction
    // each frame can't accumulate the leftover perpendicular velocity that causes that.
    private float homingSpeed;
    private float rotation;
    private float worldWidth, worldHeight;

    public void init(Animation<TextureRegion> animation, float x, float y, float worldWidth, float worldHeight) {
        this.animation = animation;
        this.stateTime = 0f;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        rectangle.set(x - SIZE / 2f, y - SIZE / 2f, SIZE, SIZE);
        rotation = MathUtils.random(0f, 360f);

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
                if (stateTime < STEERING_SWITCH_DELAY) {
                    // Original method: accelerate the existing vector toward the target.
                    vx += (dx / dist) * HOMING_ACCEL * delta;
                    vy += (dy / dist) * HOMING_ACCEL * delta;
                    float speed = (float) Math.sqrt(vx * vx + vy * vy);
                    if (speed > HOMING_MAX_SPEED) {
                        vx = vx / speed * HOMING_MAX_SPEED;
                        vy = vy / speed * HOMING_MAX_SPEED;
                    }
                } else {
                    // New method: re-aim a speed scalar at the target fresh every frame instead of
                    // accelerating the vector above, so no leftover perpendicular velocity can
                    // build up into an orbit. Seeds from the current speed the first time this
                    // phase runs (homingSpeed == 0 only ever holds then, since it's otherwise
                    // monotonically increasing) so the handoff doesn't visibly snap.
                    if (homingSpeed <= 0f) homingSpeed = (float) Math.sqrt(vx * vx + vy * vy);
                    homingSpeed = Math.min(homingSpeed + HOMING_ACCEL * delta, HOMING_MAX_SPEED);
                    vx = (dx / dist) * homingSpeed;
                    vy = (dy / dist) * homingSpeed;
                }
            }
        } else {
            homingSpeed = 0f;
            vy += GRAVITY * delta;
        }

        rectangle.x += vx * delta;
        rectangle.y += vy * delta;
    }

    public void draw(SpriteBatch batch) {
        TextureRegion frame = animation.getKeyFrame(stateTime);
        batch.draw(frame, rectangle.x, rectangle.y, rectangle.width / 2f, rectangle.height / 2f,
            rectangle.width, rectangle.height, 1f, 1f, rotation);
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
        homingSpeed = 0f;
        rotation = 0f;
        stateTime = 0f;
    }
}
