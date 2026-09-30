package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.SquadronMovement;
import whitelabeltest.enemy.movementpatterns.WaypointPathMovement;

/** Implements Trigger.enterFromAbove: the enemy spawns off-screen above the play area and travels
 *  down to its authored (x, y), arriving as the camera reaches the trigger's distance. Shared by
 *  TriggerManager (game) and PlayerPreviewView (editor) so the two can't drift apart. */
public final class EnemyEntranceMovement {
    private EnemyEntranceMovement() {}

    /** Off-screen spawn Y above both the play area and the arrival point. The margin is one sprite
     *  height, which must stay under GenericEnemy.isOffScreen()'s removal tolerance (2x height) or
     *  the enemy is removed before it can enter, so pass the real sprite height. */
    public static float spawnY(Trigger trigger, float worldHeight, float spriteHeight) {
        return spawnY(trigger, worldHeight, spriteHeight, null);
    }

    /** As above, but also clears the first leg target of a WaypointPathMovement `afterEntrance`
     *  (looking through one SquadronMovement wrapper). That path curves from the spawn point to its
     *  first target, so spawning below that target would make the entrance climb instead of descend. */
    public static float spawnY(Trigger trigger, float worldHeight, float spriteHeight, MovementPattern afterEntrance) {
        float arrivalY = trigger.y;
        // Additive per-member lift, so a wave keeps its vertical shape (see Trigger.waveSpawnLift).
        if (!Float.isNaN(trigger.waveSpawnLift)) arrivalY += trigger.waveSpawnLift;
        MovementPattern leader = afterEntrance instanceof SquadronMovement squadron ? squadron.getLeader() : afterEntrance;
        if (leader instanceof WaypointPathMovement waypointPath && waypointPath.getLegs().size > 0) {
            arrivalY = Math.max(arrivalY, waypointPath.getLegs().first().targetY);
        }
        return Math.max(arrivalY, worldHeight) + Math.max(spriteHeight, 0.5f);
    }

    /** The entrance movement for `trigger`, or null for a normal spawn (enterFromAbove unset, no y,
     *  or no lead time to travel in).
     *
     * - The travel time comes from min(spawnLead, distance), matching TriggerManager's clamp of the
     *   arm distance at 0.
     * - spriteWidth/Height must be the real sprite's size: WaypointPathMovement works in sprite-center
     *   space, while trigger x/y is the bottom-left corner.
     * - The entrance is a single WaypointPathMovement leg because that pattern holds at its target
     *   instead of reporting finished, then hands off to `afterEntrance` via Chained.
     * - If `afterEntrance` is itself a WaypointPathMovement (directly or as a Squadron leader), it is
     *   returned as-is: it already curves in from the off-screen spawn point on its own, and a
     *   straight drop in front of it looked like two disconnected motions. */
    public static MovementPattern build(Trigger trigger, float cameraSpeed, float worldHeight, float spriteWidth, float spriteHeight, MovementPattern afterEntrance) {
        if (!trigger.enterFromAbove || Float.isNaN(trigger.y)) return null;
        MovementPattern effectiveLeader = afterEntrance instanceof SquadronMovement squadron ? squadron.getLeader() : afterEntrance;
        if (effectiveLeader instanceof WaypointPathMovement) return afterEntrance;
        float effectiveLead = Math.min(trigger.spawnLead, trigger.distance);
        if (effectiveLead <= 0f) return null;

        float spawnY = spawnY(trigger, worldHeight, spriteHeight);
        float leadSeconds = effectiveLead / Math.max(cameraSpeed, 0.01f);
        float speed = (spawnY - trigger.y) / Math.max(leadSeconds, 0.01f);

        float targetCenterX = trigger.x + spriteWidth / 2f;
        float targetCenterY = trigger.y + spriteHeight / 2f;
        Array<WaypointPathMovement.Leg> legs = new Array<>();
        legs.add(new WaypointPathMovement.Leg(targetCenterX, targetCenterY, 0f, speed, 0f,
            "spline", 0f, 0f, null, 0f, 0f, 0f, false, null));
        MovementPattern entrance = new WaypointPathMovement(legs, false, 1f);
        return afterEntrance != null ? new Chained(entrance, afterEntrance) : entrance;
    }

    /** Runs `entrance` until it settles, then `after` for good. `after` isn't updated before the
     *  handoff, so its first update reads the sprite at its real arrival point. */
    private static final class Chained implements MovementPattern {
        private final MovementPattern entrance;
        private final MovementPattern after;
        private boolean handedOff = false;

        Chained(MovementPattern entrance, MovementPattern after) {
            this.entrance = entrance;
            this.after = after;
        }

        @Override
        public void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement) {
            if (!handedOff) {
                entrance.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, inverseMovement);
                if (!entrance.isSettled()) return;
                handedOff = true;
            }
            after.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, inverseMovement);
        }

        @Override
        public boolean isFinished() {
            return handedOff && after.isFinished();
        }

        @Override
        public void reset() {
            entrance.reset();
            after.reset();
            handedOff = false;
        }

        @Override
        public boolean isSettled() {
            return handedOff && after.isSettled();
        }

        @Override
        public WaypointCue consumeCue() {
            return handedOff ? after.consumeCue() : entrance.consumeCue();
        }
    }
}
