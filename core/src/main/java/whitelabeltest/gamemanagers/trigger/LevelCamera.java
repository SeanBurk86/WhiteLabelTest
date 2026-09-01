package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.math.Rectangle;

/** How far a stage's camera has "scanned up" through the level - a single monotonically-advancing
 *  distance rather than a real 2D position, since nothing visual (background scroll, actual on-
 *  screen camera transform) consumes this yet - see TriggerManager's own class doc. Advances every
 *  frame at speed (world units/sec), which a Trigger.setSpeed action can change on the fly, so
 *  distance and wall-clock time aren't necessarily 1:1 once a stage uses that.
 *
 * getCollisionBox() exposes the camera's own "collision box" - the sliver of distance actually
 * covered during the frame just simulated - so TriggerManager can test it against each Trigger's
 * position the same way the rest of the game tests hitboxes against each other, and a fast camera
 * (a big speed change, or a long delta after a stall) can't skip past a trigger that falls entirely
 * within one frame's advance. */
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

    /** The distance interval covered this frame, as a Rectangle spanning the full world width -
     *  reused so TriggerManager can test overlap with Intersector/Rectangle.overlaps the same way
     *  every other collision check in this game does. Never zero-height (even at speed 0 or before
     *  the first update()), so a stalled camera still yields a valid, testable box. */
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

    /** Debug/practice-rewind parity with SpawnScheduler.seekTo() - jumps straight to targetDistance
     *  without re-running whatever speed changes happened along the way (same "skip rather than
     *  replay" compromise SpawnScheduler.seekTo() itself makes for cues it jumps past). */
    public void seekTo(float targetDistance) {
        position = Math.max(0f, targetDistance);
        previousPosition = position;
    }

    /** Snaps the camera to EXACTLY `distance`, called the instant a gate trigger arms and freezes
     *  (see TriggerManager.update()'s activeGate handling) - mirrors SpawnScheduler.update()'s own
     *  "totalTime = nextGate.time" clamp exactly, and for the same reason: the frame a gate arms,
     *  update() has already run and can leave `position` a hair past the gate's own distance (an
     *  ordinary one-frame advance, same as any other trigger). Left uncorrected, that overshoot
     *  becomes PERMANENT once frozen - camera.update() never runs again until the gate clears, so the
     *  collision box stays pinned to that slightly-past sliver for however long the freeze lasts, and
     *  once it finally clears, the NEXT box starts from there too. Any other trigger sitting at that
     *  SAME exact distance, later in trigger order (overwhelmingly common here - a text cue's own
     *  paired spawn/despawn/silence trigger always shares its distance), would then have
     *  trigger.distance permanently < the collision box's own minY and never arm at all. Clamping
     *  back to the gate's exact distance here means the box starts there again the moment it clears,
     *  same as if the gate had never overshot in the first place. */
    public void clampTo(float distance) {
        position = distance;
        previousPosition = distance;
    }
}
