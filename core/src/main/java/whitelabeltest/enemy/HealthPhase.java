package whitelabeltest.enemy;

/** One health-triggered change to a spawned enemy's behavior - an entry in Trigger.healthPhases.
 *  The first time the enemy's remaining health drops to or below `healthPercent` of its max, it
 *  swaps to `movementPattern` and/or `firingPattern` (each null/blank = leave that one alone).
 *  Phases are one-way: once entered, a phase stays entered even if the enemy later regenerates
 *  above its threshold. If a single hit crosses several thresholds at once, they're all entered in
 *  order from highest threshold to lowest, so the deepest one's patterns win.
 *
 *  A new movementPattern starts from wherever the enemy currently is - it is NOT reset to the spawn
 *  point - and, like any movement pattern, its absolute waypoint targets are still world
 *  coordinates. Ids are the same ones Trigger.movementPattern/Trigger.firingPattern take
 *  (data/movement_patterns/, data/firing_patterns/). */
public class HealthPhase {
    // Percent (0-100) of the enemy's max health REMAINING at which this phase starts - 50 means
    // "once it's down to half health", not "once it's lost 50 health".
    public float healthPercent;
    public String movementPattern;
    public String firingPattern;

    public HealthPhase() {}

    public HealthPhase(float healthPercent, String movementPattern, String firingPattern) {
        this.healthPercent = healthPercent;
        this.movementPattern = movementPattern;
        this.firingPattern = firingPattern;
    }
}
