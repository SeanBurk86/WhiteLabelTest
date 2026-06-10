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
    public boolean inverseMovement = false; // Added inverseMovement field
    public String firingType;
    public float fireRate;

    public EnemyDefinition() {}
}
