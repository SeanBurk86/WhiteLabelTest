package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** Follows a Cardinal-spline curve (see WaypointSpline) through an ordered list of waypoints,
 *  starting from wherever the sprite actually is the first time update() runs (the enemy's real
 *  spawn position - not baked in at construction, same "read the sprite fresh" idiom
 *  MoveToPointMovement already uses) - see PatternFactory's "WaypointPath" case, which builds the
 *  Leg list from a MovementPatternDef's `patterns` (each a "MoveToPoint" leg carrying the new
 *  per-waypoint fields - see that class's own doc) with flipX/flipY already applied.
 *
 * Each segment is walked at ITS OWN destination waypoint's speed (matching how "speed at waypoint"
 * reads in the reference tool this was modeled on - see this session's design discussion), scaled
 * by the whole path's globalSpeed. Progress within a segment is driven by the curve's own local
 * tangent magnitude (an arc-length-rate approximation, not exact reparameterization - accurate
 * enough at this game's scale/tension range) so a sharp corner (tension near 1) doesn't get walked
 * unnaturally fast or slow relative to a smooth stretch. */
public class WaypointPathMovement implements MovementPattern {
    /** One waypoint's full authored field set, extracted (with flip already applied) from its
     *  MovementPatternDef leg by PatternFactory - see that class's "WaypointPath" case. */
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

    // Minimum tangent magnitude used when converting "world units/sec" into "curve-parameter/sec" -
    // guards against a near-zero local tangent (a very high-tension corner) making progress blow up.
    private static final float MIN_TANGENT_MAGNITUDE = 0.5f;

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

    // Accumulated MovementPattern.applyGroundScroll() offset - see that method's own doc on why a
    // ground enemy on this pattern needs it added into finalY below rather than relying on
    // BaseEnemy's own sprite.translate(), which this class's own setCenterY() call would otherwise
    // silently overwrite (and thus discard) the very next update().
    private float groundScrollOffsetY = 0f;

    public WaypointPathMovement(Array<Leg> legs, boolean closePath, float globalSpeed) {
        this.legs = legs;
        this.closePath = closePath;
        this.globalSpeed = globalSpeed > 0 ? globalSpeed : 1f;
    }

    /** This path's own authored legs, in order - see EnemyEntranceMovement.spawnY()'s own doc on why
     *  it needs legs.first().targetY: this pattern's initFrom() starts from wherever the sprite
     *  actually spawns and curves straight to that first leg's own absolute target, so the off-screen
     *  entrance spawn point has to sit safely above THAT target specifically, not just above
     *  trigger.y/worldHeight - a target already authored close to worldHeight (or pushed there by a
     *  wave's own rotation - see TriggerManager.fireWave()'s own doc) can otherwise land AT OR ABOVE
     *  the spawn point computed from trigger.y alone, sending the entrance climbing further off-screen
     *  instead of descending onto it. */
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
            float t = segmentIndex + localT;
            float tangentMag = WaypointSpline.tangentAt(tempTangent, points, tensions, closePath, t).len();
            float rate = (destination.speed * globalSpeed) / Math.max(tangentMag, MIN_TANGENT_MAGNITUDE);
            localT += rate * delta;
        }

        // Evaluated BEFORE onArrive() below touches segmentIndex/localT, using localT clamped (not
        // yet reset) to 1 - the arrival frame's own position/orientation must land exactly on the
        // waypoint it just reached. Evaluating AFTER onArrive() (this method's original, wrong shape)
        // used the OLD segmentIndex together with the ALREADY-RESET localT=0, i.e. the START of the
        // segment just finished, not its end - visually snapping the sprite backward for one frame at
        // every intermediate waypoint, and PERMANENTLY freezing it there on a path's LAST waypoint,
        // since pathComplete's own top-of-method early return then skips this block on every
        // subsequent frame - it never got a second chance to land on the real target. That's what
        // "reaching the end of the path" actually looked like: not a despawn, a silent teleport back
        // to the start of the final leg that was never corrected afterward.
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

    /** Turns `current` toward `target` (both degrees) by at most `maxDelta` degrees, going whichever
     *  way (cw/ccw) is shorter - libGDX's MathUtils only offers a progress-based lerpAngleDeg, not a
     *  fixed-rate-per-frame one, so this is the fixed-rate equivalent "player" orientation needs. */
    private static float turnToward(float current, float target, float maxDelta) {
        float diff = ((target - current + 180f) % 360f + 360f) % 360f - 180f;
        if (Math.abs(diff) <= maxDelta) return current + diff;
        return current + Math.signum(diff) * maxDelta;
    }

    /** Always false - reaching the end of a (non-looping) path holds the enemy at its final waypoint
     *  rather than ending it. `pathComplete` above only ever short-circuits update() internally (so the
     *  sprite settles and stays put once the last leg is reached, instead of continuing to re-evaluate
     *  curve math past the authored points) - it was ALSO being returned here until this fix, which
     *  is wrong: GenericEnemy.isOffScreen() (and PlayerPreviewView's own mirrored simulation) treats
     *  isFinished()==true as an immediate "remove this entity" signal, the same as a MoveToPoint
     *  enemy that's truly meant to vanish on arrival - but a waypoint path finishing usually means the
     *  opposite (an enemy parked at its last waypoint to keep fighting/holding until an explicit
     *  despawn trigger, a kill, or the ordinary off-screen bounds check actually removes it), not an
     *  instant unconditional despawn the moment its scripted flight-in completes. This was also the
     *  root cause of enemies' apparent timing drifting from the editor's own Player View preview: that
     *  preview runs the exact same MovementPattern.isFinished() check to decide when to stop drawing
     *  an enemy, so it was silently reproducing the same premature-disappearance bug it was supposed
     *  to be an accurate preview of. */
    @Override
    public boolean isFinished() {
        return false;
    }

    /** True once this (non-looping) path has reached its last waypoint - see MovementPattern.
     *  isSettled()'s own doc for why this is exposed separately from isFinished() above. Always false
     *  for a closePath loop, which by construction never stops moving on its own. */
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
