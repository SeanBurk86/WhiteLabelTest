package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** Follows a Cardinal spline (WaypointSpline) through the waypoints, starting from wherever the
 *  sprite is on its first update. Each segment moves at its destination waypoint's speed times
 *  globalSpeed, with progress scaled by the local tangent length (an arc-length approximation) so
 *  corners and straights move at similar speeds. A non-looping path holds at its last waypoint. */
public class WaypointPathMovement implements MovementPattern {
    /** One waypoint's fields (flips already applied by PatternFactory). */
    public static class Leg {
        public final float targetX, targetY;
        public final float tension;
        public final float speed;
        public final float waitSeconds;
        public final String orientation;
        public final float aimSpeed;
        public final float fixedAngle;
        public final String soundName;
        public final float soundVolume, soundPitch, soundPitchVariation;
        public final boolean changeWeaponSet;
        public final String weaponSet;

        public Leg(float targetX, float targetY, float tension, float speed, float waitSeconds,
                   String orientation, float aimSpeed, float fixedAngle,
                   String soundName, float soundVolume, float soundPitch, float soundPitchVariation,
                   boolean changeWeaponSet, String weaponSet) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.tension = tension;
            this.speed = speed;
            this.waitSeconds = waitSeconds;
            this.orientation = orientation;
            this.aimSpeed = aimSpeed;
            this.fixedAngle = fixedAngle;
            this.soundName = soundName;
            this.soundVolume = soundVolume;
            this.soundPitch = soundPitch;
            this.soundPitchVariation = soundPitchVariation;
            this.changeWeaponSet = changeWeaponSet;
            this.weaponSet = weaponSet;
        }
    }

    // Floor on the tangent length when converting speed to curve progress (sharp corners).
    private static final float MIN_TANGENT_MAGNITUDE = 0.5f;
    // Longest step (world units) between re-reading the curve's local speed.
    private static final float MAX_SUBSTEP_DISTANCE = 0.02f;

    private final Array<Leg> legs;
    private final boolean closePath;
    private final float globalSpeed;

    private Vector2[] points;
    private float[] tensions;
    private boolean initialized;
    private boolean pathComplete;

    private int segmentIndex;
    private float localT;
    private float waitTimer;
    private float currentFacingAngle = DEFAULT_ANGLE_DEG;

    private final Vector2 tempPos = new Vector2();
    private final Vector2 tempTangent = new Vector2();
    private final Vector2 tempToPlayer = new Vector2();

    private WaypointCue pendingCue;

    // Accumulated ground scroll (see MovementPattern.applyGroundScroll()).
    private float groundScrollOffsetY = 0f;

    public WaypointPathMovement(Array<Leg> legs, boolean closePath, float globalSpeed) {
        this.legs = legs;
        this.closePath = closePath;
        this.globalSpeed = globalSpeed > 0 ? globalSpeed : 1f;
    }

    /** The waypoints in order (EnemyEntranceMovement reads the first target). */
    public Array<Leg> getLegs() { return legs; }

    private void initFrom(Sprite sprite) {
        points = new Vector2[legs.size + 1];
        tensions = new float[legs.size + 1];
        points[0] = new Vector2(sprite.getX() + sprite.getWidth() / 2f, sprite.getY() + sprite.getHeight() / 2f);
        tensions[0] = legs.get(0).tension;
        for (int i = 0; i < legs.size; i++) {
            Leg leg = legs.get(i);
            points[i + 1] = new Vector2(leg.targetX, leg.targetY);
            tensions[i + 1] = leg.tension;
        }
        segmentIndex = 0;
        localT = 0f;
        waitTimer = 0f;
        currentFacingAngle = sprite.getRotation();
        initialized = true;
    }

    @Override
    public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
        if (!initialized) initFrom(sprite);
        if (pathComplete) return;

        int destinationLegIndex = closePath ? segmentIndex % legs.size : Math.min(segmentIndex, legs.size - 1);
        Leg destination = legs.get(destinationLegIndex);

        if (waitTimer > 0f) {
            waitTimer -= delta;
        } else {
            // Advance in short sub-steps, re-reading the local tangent each time. One big step sized
            // from the frame's starting tangent overshoots badly near sharp reversals.
            float remaining = destination.speed * globalSpeed * delta;
            while (remaining > 0f && localT < 1f) {
                float step = Math.min(remaining, MAX_SUBSTEP_DISTANCE);
                float tangentMag = WaypointSpline.tangentAt(tempTangent, points, tensions, closePath, segmentIndex + localT).len();
                localT += step / Math.max(tangentMag, MIN_TANGENT_MAGNITUDE);
                remaining -= step;
            }
        }

        // Evaluate before onArrive() advances the segment, so the arrival frame lands exactly on the
        // waypoint (and the final waypoint is where the path holds).
        float evalT = segmentIndex + Math.min(localT, 1f);
        WaypointSpline.evaluate(tempPos, points, tensions, closePath, evalT);
        float finalX = tempPos.x;
        float finalY = (inverseMovement ? worldHeight - tempPos.y : tempPos.y) + groundScrollOffsetY;
        sprite.setCenterX(finalX);
        sprite.setCenterY(finalY);
        rectangle.setPosition(sprite.getX(), sprite.getY());

        applyOrientation(sprite, destination, evalT, playerHitbox, inverseMovement, delta);

        if (localT >= 1f) {
            localT = 0f;
            onArrive(destinationLegIndex, destination);
        }
    }

    private void onArrive(int arrivedLegIndex, Leg arrivedLeg) {
        if (arrivedLeg.waitSeconds > 0f) waitTimer = arrivedLeg.waitSeconds;
        if (arrivedLeg.soundName != null || arrivedLeg.changeWeaponSet) {
            WaypointCue cue = new WaypointCue();
            cue.soundName = arrivedLeg.soundName;
            cue.soundVolume = arrivedLeg.soundVolume;
            cue.soundPitch = arrivedLeg.soundPitchVariation > 0f
                ? arrivedLeg.soundPitch + MathUtils.random(-arrivedLeg.soundPitchVariation, arrivedLeg.soundPitchVariation)
                : arrivedLeg.soundPitch;
            cue.changeWeaponSet = arrivedLeg.changeWeaponSet;
            cue.weaponSet = arrivedLeg.weaponSet;
            pendingCue = cue;
        }
        if (closePath) {
            segmentIndex = (segmentIndex + 1) % legs.size;
            // points[0] is the spawn position; move it to the last waypoint so the next lap starts
            // where this one ended instead of teleporting back to the spawn point.
            if (segmentIndex == 0) {
                int last = points.length - 1;
                points[0].set(points[last]);
                tensions[0] = tensions[last];
            }
        } else if (segmentIndex + 1 >= legs.size) {
            pathComplete = true;
        } else {
            segmentIndex++;
        }
    }

    private void applyOrientation(Sprite sprite, Leg destination, float t, Circle playerHitbox, boolean inverseMovement, float delta) {
        switch (destination.orientation) {
            case "fixed" -> {
                currentFacingAngle = destination.fixedAngle;
                sprite.setRotation(currentFacingAngle);
            }
            case "player" -> {
                float spriteCenterX = sprite.getX() + sprite.getWidth() / 2f;
                float spriteCenterY = sprite.getY() + sprite.getHeight() / 2f;
                tempToPlayer.set(playerHitbox.x, playerHitbox.y).sub(spriteCenterX, spriteCenterY);
                float targetAngle = tempToPlayer.len2() > 0.0001f ? tempToPlayer.angleDeg() + 90f : currentFacingAngle;
                currentFacingAngle = turnToward(currentFacingAngle, targetAngle, destination.aimSpeed * delta);
                sprite.setRotation(currentFacingAngle);
            }
            default -> {
                WaypointSpline.tangentAt(tempTangent, points, tensions, closePath, t);
                if (inverseMovement) tempTangent.y = -tempTangent.y;
                if (tempTangent.len2() > 0.0001f) currentFacingAngle = tempTangent.angleDeg() + 90f;
                sprite.setRotation(currentFacingAngle);
            }
        }
    }

    /** Turns toward `target` by at most maxDelta degrees the shorter way (a fixed-rate lerpAngleDeg). */
    private static float turnToward(float current, float target, float maxDelta) {
        float diff = ((target - current + 180f) % 360f + 360f) % 360f - 180f;
        if (Math.abs(diff) <= maxDelta) return current + diff;
        return current + Math.signum(diff) * maxDelta;
    }

    /** Always false: finishing the path parks the enemy at its last waypoint (until killed,
     *  despawned or it leaves the screen) rather than removing it. */
    @Override
    public boolean isFinished() {
        return false;
    }

    /** True once a non-looping path reaches its last waypoint. */
    @Override
    public boolean isSettled() {
        return pathComplete;
    }

    @Override
    public void applyGroundScroll(float dy) {
        groundScrollOffsetY += dy;
    }

    @Override
    public void reset() {
        initialized = false;
        pathComplete = false;
        pendingCue = null;
        groundScrollOffsetY = 0f;
    }

    @Override
    public WaypointCue consumeCue() {
        WaypointCue cue = pendingCue;
        pendingCue = null;
        return cue;
    }
}
