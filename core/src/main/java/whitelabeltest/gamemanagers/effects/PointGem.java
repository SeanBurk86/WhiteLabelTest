package whitelabeltest.gamemanagers.effects;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;

/** A point gem dropped by a dying enemy. It pops upward and falls under gravity while the player
 *  fires; when they stop firing it homes on the graze halo. Homing first accelerates the velocity
 *  (a swoop), then after STEERING_SWITCH_DELAY re-aims a speed scalar each frame, which can't
 *  settle into an orbit. A stationary gem (waypoint marker) doesn't move at all. */
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
    // Second-phase homing speed, re-aimed at the player every frame.
    private float homingSpeed;
    private float rotation;
    private float worldWidth, worldHeight;
    // A fixed waypoint marker: no pop, gravity or homing.
    private boolean stationary;
    // Size and point-value multiplier.
    private float valueScale = 1f;
    // How many gems this one stands for (> 1 when an enemy's gem count was capped).
    private int represents = 1;

    public void init(Animation<TextureRegion> animation, float x, float y, float worldWidth, float worldHeight) {
        init(animation, x, y, worldWidth, worldHeight, false);
    }

    public void init(Animation<TextureRegion> animation, float x, float y, float worldWidth, float worldHeight, boolean stationary) {
        init(animation, x, y, worldWidth, worldHeight, stationary, 1f);
    }

    /** @param valueScale size and point-value multiplier (1 = base gem). */
    public void init(Animation<TextureRegion> animation, float x, float y, float worldWidth, float worldHeight, boolean stationary, float valueScale) {
        init(animation, x, y, worldWidth, worldHeight, stationary, valueScale, 1);
    }

    /** @param represents how many gems this one stands for. */
    public void init(Animation<TextureRegion> animation, float x, float y, float worldWidth, float worldHeight, boolean stationary, float valueScale, int represents) {
        this.represents = Math.max(1, represents);
        this.animation = animation;
        this.stateTime = 0f;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.stationary = stationary;
        this.valueScale = valueScale;
        float size = SIZE * valueScale;
        rectangle.set(x - size / 2f, y - size / 2f, size, size);
        rotation = MathUtils.random(0f, 360f);

        if (stationary) {
            vx = 0f;
            vy = 0f;
            return;
        }

        // Mostly-upward pop with some horizontal spread.
        float angle = MathUtils.random(20f, 160f);
        float speed = MathUtils.random(2.5f, 4.5f);
        vx = MathUtils.cosDeg(angle) * speed;
        vy = MathUtils.sinDeg(angle) * speed;
    }

    public void update(float delta, boolean playerFiring, Circle grazeHitbox) {
        stateTime += delta;
        if (stationary) return;

        if (!playerFiring && grazeHitbox.radius > 0f) {
            float centerX = rectangle.x + rectangle.width / 2f;
            float centerY = rectangle.y + rectangle.height / 2f;
            float dx = grazeHitbox.x - centerX;
            float dy = grazeHitbox.y - centerY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0.0001f) {
                if (stateTime < STEERING_SWITCH_DELAY) {
                    // Accelerate the current velocity toward the target.
                    vx += (dx / dist) * HOMING_ACCEL * delta;
                    vy += (dy / dist) * HOMING_ACCEL * delta;
                    float speed = (float) Math.sqrt(vx * vx + vy * vy);
                    if (speed > HOMING_MAX_SPEED) {
                        vx = vx / speed * HOMING_MAX_SPEED;
                        vy = vy / speed * HOMING_MAX_SPEED;
                    }
                } else {
                    // Re-aim a speed scalar each frame so no orbit can form. Seeded from the current
                    // speed on the first frame of this phase so the handoff doesn't snap.
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

    /** How many gems this one stands for. */
    public int getRepresents() {
        return represents;
    }

    /** The size and point-value multiplier. */
    public float getValueScale() {
        return valueScale;
    }

    @Override
    public void reset() {
        vx = 0f;
        vy = 0f;
        homingSpeed = 0f;
        rotation = 0f;
        stateTime = 0f;
        stationary = false;
        valueScale = 1f;
        represents = 1;
    }
}
