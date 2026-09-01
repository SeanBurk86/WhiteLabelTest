package whitelabeltest.gamemanagers.trigger;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.WaypointPathMovement;

/** Trigger.enterFromAbove's actual mechanism: spawn this enemy off-screen, above both the play area
 *  and its own authored y, then carry it straight down to that authored (x, y) at whatever speed
 *  lands it there exactly when its available real-time budget runs out - i.e. by the time the camera
 *  reaches this trigger's own distance, not merely "spawned early" (Trigger.spawnLead alone) with no
 *  way to actually get there without popping fully-formed into an already-visible spot.
 *
 * One shared computation (spawnY()/build()) used by BOTH TriggerManager.fire() (the real game) and
 * PlayerPreviewView (the editor's preview) - never two independently hand-matched reimplementations
 * of the same formula, which is exactly how the editor's OWN background rendering drifted out of
 * sync with real gameplay earlier this session. */
public final class EnemyEntranceMovement {
    private EnemyEntranceMovement() {}

    /** The off-screen spawn Y for `trigger`, above BOTH the visible play area and the trigger's own
     *  arrival point, so it starts out of view regardless of where the arrival point itself sits
     *  (even one already near the top of the play area) - MUST be called with the real spawn
     *  sprite's own height (see build()'s own doc on why that's only known once the sprite actually
     *  exists), not a guessed/default size: the margin above worldHeight this returns is deliberately
     *  kept to spriteHeight - comfortably UNDER GenericEnemy.isOffScreen()'s own worldHeight +
     *  spriteHeight*2 removal tolerance - so EntityManager doesn't strip the enemy as "off-screen"
     *  before its first movement.update() call even has a chance to start carrying it down. A fixed
     *  margin (this method's original shape) got this backwards for any ordinarily-sized (size ~1,
     *  height <= 1) enemy: a flat 3-unit margin sits ABOVE that removal tolerance (height*2 <= 2),
     *  so the entrance spawn was being deleted within a frame or two of spawning, never visibly
     *  entering at all - indistinguishable, from the player's side, from an ordinary no-entrance
     *  "pop in at the arrival point" spawn (see Trigger.spawnLead's own doc on that distinction),
     *  which is exactly the bug this was reported as. */
    public static float spawnY(Trigger trigger, float worldHeight, float spriteHeight) {
        return Math.max(trigger.y, worldHeight) + Math.max(spriteHeight, 0.5f);
    }

    /** The synthetic single-leg WaypointPathMovement carrying this spawn from spawnY() straight down
     *  to its own authored (x, y) - null (meaning "just spawn there normally, no entrance") unless
     *  trigger.enterFromAbove is set, trigger.y is authored, AND there's an actual real-time budget
     *  to travel in (effectiveLead > 0).
     *
     * effectiveLead is Math.min(spawnLead, distance) rather than raw spawnLead - see
     * TriggerManager.update()'s own armDistance clamp: a lead bigger than the trigger's own distance
     * already means "arms/spawns at distance 0" (present from the very start), so the real available
     * travel time is capped at `distance` real-time-equivalent, not however much larger spawnLead
     * itself was authored as - keeping this in sync with that clamp is why this reads trigger.distance
     * directly rather than trusting the caller to have already worked out how much of spawnLead was
     * actually usable.
     *
     * @param spriteWidth, spriteHeight the ALREADY-CONSTRUCTED spawn sprite's own size - required
     * because WaypointPathMovement operates entirely in sprite-CENTER space (initFrom() reads
     * sprite.getX()+width/2 as its own starting point, and every frame applies the curve's evaluated
     * position via sprite.setCenterX/Y()), while trigger.x/trigger.y (like every other spawn-position
     * field in this codebase) are the sprite's BOTTOM-LEFT corner. Landing this leg's own target at
     * bare trigger.x/trigger.y instead of the center-adjusted point below would leave the enemy
     * settled half a sprite-width/height off from where it would have spawned WITHOUT an entrance at
     * all - call this only once the real spawn sprite's true size is known (after EnemyDefinition/
     * texture-aspect sizing has actually run), not with a guessed/default size.
     *
     * Uses WaypointPathMovement (not a raw MoveToPointMovement) specifically because its isFinished()
     * always reads false once arrived (see that class's own doc) - the enemy holds at its arrival
     * point once it gets there instead of being treated as "done, remove me" the instant it lands,
     * the exact bug this session already fixed once for hand-authored waypoint paths.
     *
     * @param afterEntrance whatever movement this spawn would otherwise be using (its own authored
     * movementPattern, or PatternFactory's NoMovement if it didn't set one) - handed off to (see
     * Chained below) the instant the entrance leg itself settles at its arrival point, so an
     * entrance-spawned enemy with its own waypoint path actually starts flying it once it arrives
     * instead of freezing there forever (WaypointPathMovement, including the entrance leg's own,
     * never reports isFinished()==true on its own - see that class's own doc - so nothing would
     * otherwise ever move this spawn on again after this method's returned pattern took over). */
    public static MovementPattern build(Trigger trigger, float cameraSpeed, float worldHeight, float spriteWidth, float spriteHeight, MovementPattern afterEntrance) {
        if (!trigger.enterFromAbove || Float.isNaN(trigger.y)) return null;
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

    /** Delegates to `entrance` until it settles at its arrival point (see WaypointPathMovement.
     *  isSettled()'s own doc), then delegates to `after` for good - see build()'s own doc on why
     *  this handoff has to happen at all. `after` is never update()'d before the handoff, so
     *  whatever it does on its own first call (most patterns read the sprite's CURRENT position
     *  then, the same "read the sprite fresh" idiom WaypointPathMovement.initFrom() itself uses -
     *  see that class's own doc) sees the sprite already sitting at its real arrival point, not
     *  wherever it would have started without an entrance at all. */
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
