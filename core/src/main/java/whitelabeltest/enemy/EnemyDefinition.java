package whitelabeltest.enemy;

public class EnemyDefinition {
    public String id;
    public String texture;
    public String bulletTexture;
    public int frameCount;
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.1f;
    public float size;
    public int health;
    public String movementPattern;
    public boolean inverseMovement = false;
    public boolean rotateWithMovement = true;
    public boolean isBoss = false;
    public boolean isGround = false;
    public boolean sealable = false;
    public boolean defiant = false;
    // When true, destroying this enemy also destroys every bullet it has in flight - see
    // GameController.destroyEnemy.
    public boolean bulletCancel = false;
    public int score = 10;
    public String firingPattern;
    public String explosionPattern;

    public String spawnTexture;
    public int spawnFrameCount;
    public int spawnColumns = 0;
    public int spawnRows = 1;
    public float spawnDuration = 0.4f;


    public String deathTexture;
    public int deathFrameCount;
    public int deathColumns = 0;
    public int deathRows = 1;
    public float deathDuration = 0.4f;

    public EnemyDefinition() {}
}