package whitelabeltest.enemy;

public class EnemyDefinition {
    public String id;
    public String texture;
    public String bulletTexture;
    public int frameCount;
    public float size;
    public int health;
    public String movementType;
    public float speed;
    public boolean inverseMovement = false;
    // Legacy single-pattern fields — used when firingPattern is absent
    public String firingType;
    public float fireRate;
    // Rich pattern definition — supports Sequence and Combined
    public FiringPatternDef firingPattern;

    public EnemyDefinition() {}
}
