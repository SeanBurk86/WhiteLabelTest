package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;

public interface MovementPattern {
    // Shared "no rotation" baseline (standard math convention: 0 = right, 90 = up; 270 = down,
    // the heading every pattern used before angles were configurable). Enemy definitions default
    // movementAngle to this value, and each pattern treats it as "use my original orientation".
    float DEFAULT_ANGLE_DEG = 270f;

    void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement);
    boolean isFinished();
    void reset();

    /** True once this pattern's own position/rotation math is guaranteed not to change on any FUTURE
     *  update() call without some external state change - a held WaypointPathMovement after its last
     *  (non-looping) leg, e.g. Distinct from isFinished(), which GenericEnemy.isOffScreen() treats as
     *  "remove this entity" - a settled pattern's enemy is very much still alive, just done moving.
     *  Every pattern defaults to false (never assume movement has settled); see
     *  PlayerPreviewView.stepMovement() for the one caller that needs this, to know it can stop
     *  stepping update() any further once true rather than either (a) keep burning cycles re-running
     *  update() calls that can only ever produce the exact same result, for however far past this
     *  point the preview is scrubbed, or (b) give up after some fixed number of steps and have to
     *  guess whether the entity's still around - a real problem once an entity can be alive
     *  indefinitely (see WaypointPathMovement.isFinished()'s own doc), since there's no step count
     *  that's always "enough" to reach its true settled position other than actually reaching it. */
    default boolean isSettled() { return false; }

    /** A one-shot sound/weapon-set-swap event queued the moment a WaypointPathMovement reaches a
     *  waypoint that sets one - see MovementPatternDef's WaypointPath field docs and BaseEnemy's own
     *  handling. Returns null (and every OTHER pattern's default never overrides this) once nothing
     *  is pending, so BaseEnemy can just poll this every frame with no extra bookkeeping. */
    default WaypointCue consumeCue() { return null; }

    /** See consumeCue() - soundName/changeWeaponSet are independently nullable/false, since a
     *  waypoint can set either, both, or neither. */
    class WaypointCue {
        public String soundName;
        public float soundVolume;
        public float soundPitch;
        public boolean changeWeaponSet;
        public String weaponSet;
    }
}
