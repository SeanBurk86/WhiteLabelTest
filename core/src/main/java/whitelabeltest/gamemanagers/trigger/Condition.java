package whitelabeltest.gamemanagers.trigger;

/** One gameplay condition a Trigger waits on after it arms. See the README's "Trigger conditions"
 *  table for every `type` and which extra fields it reads. Unknown or null types count as satisfied
 *  so a typo can't soft-lock a stage. */
public class Condition {
    public String type;
    public int count = 1;
    public String enemyType;
    public String weaponId;
    // "spawnDestroyed" only: the Trigger.id of the spawn to wait on.
    public String triggerId;

    // Runtime-only: the relevant counter's value when the trigger armed (see
    // TriggerManager.armConditions()), so counts measure progress since arming.
    public float baseline;

    public Condition() {}
}
