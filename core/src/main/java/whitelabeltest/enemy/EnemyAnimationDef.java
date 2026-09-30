package whitelabeltest.enemy;

/** A named alternate animation in EnemyDefinition.animations, switched to by a HealthPhase. */
public class EnemyAnimationDef {
    public String texture;
    public int frameCount = 1;
    // 0 = all frames on one row.
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.1f;
    // false = play once and hold the last frame.
    public boolean loop = true;

    public EnemyAnimationDef() {}
}
