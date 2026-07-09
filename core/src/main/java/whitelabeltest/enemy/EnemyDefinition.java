package whitelabeltest.enemy;

public class EnemyDefinition {
    public String id;
    public String texture;
    public String bulletTexture;
    public int frameCount;
    // Sprite sheet layout for `texture`. columns=0 means "single row of frameCount frames".
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.1f;
    // Sprite sheet layout for `bulletTexture`. Same conventions as above; bulletColumns=0 means
    // "single row of bulletFrameCount frames". Individual firing patterns may override this
    // (see FiringPatternDef) when they set their own bulletTexture.
    public int bulletFrameCount = 1;
    public int bulletColumns = 0;
    public int bulletRows = 1;
    public float bulletFrameDuration = 0.1f;
    public float size;
    public int health;
    public String movementType;
    public float speed;
    public boolean inverseMovement = false;
    public boolean isBoss = false;
    // Legacy single-pattern fields — used when firingPattern is absent
    public String firingType;
    public float fireRate;
    public float bulletSize = -1f; // -1 means "use this pattern's own default"
    public float bulletSpeed = -1f; // -1 means "use this pattern's own default"
    // Rich pattern definition — supports Sequence and Combined
    public FiringPatternDef firingPattern;

    // Optional entrance animation. If spawnTexture is omitted, the enemy fades in using its
    // normal texture instead. Set spawnDuration to 0 to disable the entrance state entirely.
    public String spawnTexture;
    public int spawnFrameCount;
    public int spawnColumns = 0;
    public int spawnRows = 1;
    public float spawnDuration = 0.4f;

    // Optional destruction animation. If deathTexture is omitted, the enemy fades out using its
    // normal texture instead. Set deathDuration to 0 to skip straight to removal.
    public String deathTexture;
    public int deathFrameCount;
    public int deathColumns = 0;
    public int deathRows = 1;
    public float deathDuration = 0.4f;

    public EnemyDefinition() {}
}
