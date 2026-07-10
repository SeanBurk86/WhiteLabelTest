package whitelabeltest.enemy;

import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.enemy.movementpatterns.SeekingMovement;

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
    // Rotates this enemy's movementType away from its original orientation, in degrees (standard
    // math convention: 0 = right, 90 = up). The default (270, straight down) reproduces each
    // pattern's original, unrotated behavior: Straight/ZigZag/Spline treat it as an absolute
    // heading; Seeking treats it as an offset from aiming straight at the player.
    public float movementAngle = MovementPattern.DEFAULT_ANGLE_DEG;
    // How close (in world units) a Seeking enemy gets to the player before it stops approaching.
    public float stopDistance = SeekingMovement.DEFAULT_STOP_DISTANCE;
    // Rich pattern definition — supports Sequence and Squadron, mirroring firingPattern below.
    // When set, this takes priority over movementType/speed/movementAngle/stopDistance.
    public MovementPatternDef movementPattern;
    public boolean inverseMovement = false;
    public boolean isBoss = false;
    // Legacy single-pattern fields — used when firingPattern is absent
    public String firingType;
    public float fireRate;
    public float bulletSize = -1f; // -1 means "use this pattern's own default"
    public float bulletSpeed = -1f; // -1 means "use this pattern's own default"
    // Emission point offset from the sprite's center, in world units; only used by the legacy
    // firingType/fireRate path above (firingPattern's own FiringPatternDef.offsetX/Y takes priority).
    public float firingOffsetX = 0f;
    public float firingOffsetY = 0f;
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
