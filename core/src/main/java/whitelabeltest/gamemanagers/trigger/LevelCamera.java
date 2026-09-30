package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.math.Rectangle;

/** A stage's progress as a single advancing distance (not a 2D camera transform). Advances at
 *  `speed` world units/sec, which a Trigger.setSpeed action can change, so distance and time are
 *  not necessarily 1:1. */
public class LevelCamera {
    private final float worldWidth;
    private float position;
    private float previousPosition;
    private float speed;

    public LevelCamera(float worldWidth, float initialSpeed) {
        this.worldWidth = worldWidth;
        this.speed = initialSpeed;
    }

    public void update(float delta) {
        previousPosition = position;
        position += speed * delta;
    }

    public float getPosition() { return position; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }

    /** The distance swept during the last frame. Triggers are tested against this span rather than
     *  a point, so a fast camera can't skip a trigger. Never zero-height, even when stalled. */
    public Rectangle getCollisionBox() {
        float min = Math.min(previousPosition, position);
        float max = Math.max(previousPosition, position);
        float height = Math.max(max - min, 0.0001f);
        return new Rectangle(0f, min, worldWidth, height);
    }

    public void reset() {
        position = 0f;
        previousPosition = 0f;
    }

    /** Jumps straight to targetDistance without replaying speed changes along the way. */
    public void seekTo(float targetDistance) {
        position = Math.max(0f, targetDistance);
        previousPosition = position;
    }

    /** Snaps to exactly `distance` when a gate freezes the camera. Without this, the frame's
     *  overshoot past the gate would persist through the freeze and any later trigger at the same
     *  distance would never arm. */
    public void clampTo(float distance) {
        position = distance;
        previousPosition = distance;
    }
}
