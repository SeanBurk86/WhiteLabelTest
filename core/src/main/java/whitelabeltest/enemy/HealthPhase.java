package whitelabeltest.enemy;

/** A health-triggered behavior change (Trigger.healthPhases). When remaining health first drops to
 *  healthPercent, the enemy switches movement/firing/animation (null = keep). Phases are one-way;
 *  a hit crossing several thresholds enters them in order, so the deepest wins. A new movement
 *  starts from the enemy's current position. */
public class HealthPhase {
    // Percent (0-100) of max health REMAINING (50 = at half health).
    public float healthPercent;
    public String movementPattern;
    public String firingPattern;
    // Key into EnemyDefinition.animations; null keeps the current animation.
    public String animation;
    // Non-null turns flipWithDirection on/off from this phase on.
    public Boolean flipWithDirection;

    public HealthPhase() {}

    public HealthPhase(float healthPercent, String movementPattern, String firingPattern) {
        this.healthPercent = healthPercent;
        this.movementPattern = movementPattern;
        this.firingPattern = firingPattern;
    }

    /** A copy with a different movementPattern (for per-member wave copies). */
    public HealthPhase withMovementPattern(String movementPattern) {
        HealthPhase copy = new HealthPhase(healthPercent, movementPattern, firingPattern);
        copy.animation = animation;
        copy.flipWithDirection = flipWithDirection;
        return copy;
    }
}
