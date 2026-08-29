package whitelabeltest.gamemanagers.trigger;

/** One gameplay condition a Trigger can wait on after the camera reaches its distance and arms it
 *  (see Trigger.conditions/Trigger.conditionMode, TriggerManager.update()) - same condition
 *  vocabulary as SpawnScheduler.GateCue's own `condition` string, plus "enemyTypeDestroyed", which
 *  GateCue has no equivalent for: waiting on a SPECIFIC EnemyDefinition id's kill count instead of
 *  the run's overall kill count. Unlike GateCue (a single serialized gate at a time on
 *  SpawnScheduler's own clock), any number of Triggers can be armed and independently waiting on
 *  their own conditions at once, so each condition tracks its own baseline (see
 *  TriggerManager.armConditions()) rather than sharing one set of "at gate start" fields.
 *
 * type is one of: "shoot"/"bomb"/"hyperAttack"/"hyperAttackReleased" (input freshly pressed/
 * released), "weaponSwitch" (+weaponId - the currently equipped weapon), "moved"/"movedLeft"/
 * "movedRight" (movement freshly started, optionally in that direction), "enemiesDestroyed"
 * (+count - any enemy, since this condition armed), "enemyTypeDestroyed" (+enemyType,+count - only
 * that EnemyDefinition id's kills, since armed), "gemsCollected" (+count, since armed), "grazed"
 * (+count of graze POINTS, not raw graze events - see GateCue's own doc). An unrecognized/null type
 * is treated as already satisfied, same "a typo can't soft-lock content" rule GateCue follows. */
public class Condition {
    public String type;
    public int count = 1;
    public String enemyType;
    public String weaponId;

    // Snapshotted by TriggerManager.armConditions() the moment this condition's Trigger arms - not
    // authored in JSON, purely runtime bookkeeping (same role as SpawnScheduler's
    // enemiesDestroyedAtGateStart/gemsCollectedAtGateStart/grazePointsAtGateStart, just per-condition
    // instead of per-schedule since several can be armed at once here).
    public float baseline;

    public Condition() {}
}
