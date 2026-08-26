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
    // Index (declaration order in the stage's backgroundLayers - see StageDefinition.
    // BackgroundLayerDef/ScrollingBackground's own "far-to-near" doc) of the background layer this
    // enemy is drawn against, or -1 (the default) for "not attached to any layer" - the ordinary
    // behavior of drawing in front of the WHOLE background stack, same as before this field
    // existed. When set, GameController.draw() sandwiches this enemy's shadow+sprite between that
    // layer and the next one instead, so e.g. a ground enemy can sit visually behind a closer
    // foreground parallax layer instead of always drawing on top of it - see
    // EntityManager.drawEnemiesAttachedToLayer(). If isGround is also set, this enemy's implicit
    // scroll (see BaseEnemy.applyGroundScroll) is driven by THIS layer's own scrollSpeed instead of
    // the schedule-wide SpawnScheduler.groundScrollSpeed - see EntityManager's ground-scroll
    // resolution - so it stays planted on the specific layer it's attached to, even if that layer
    // scrolls at a different speed than the rest of the background. Out-of-range for a given
    // stage's actual layer count falls back to "unattached" (z-order) / the schedule-wide speed
    // (scroll), rather than throwing, so one enemy definition can be safely reused across stages
    // with different numbers of background layers.
    public int backgroundLayer = -1;
    public boolean sealable = false;
    public boolean defiant = false;
    // See Enemy.isDamageableByEnemyBullets() - opts this enemy into being damaged by other
    // enemies' bullets (e.g. PowerCarrier as a bullet-streaming drill's target).
    public boolean damageableByEnemyBullets = false;
    // See Enemy.showsHealthBar() - opts this enemy into a player-facing health meter drawn above it.
    public boolean showHealthBar = false;
    // See Enemy.isTargetableByHoming() - false opts a purely decorative/utility enemy (e.g. a
    // scripted bullet emitter) out of any screen-wide homing target scan.
    public boolean targetableByHoming = true;
    // See Enemy.getPairId()/CollisionManager.resolvePairedEnemyDeaths() - null (the default) means
    // normal independent death. Every active enemy sharing this same non-null string is expected
    // to die within CollisionManager.PAIR_GRACE_WINDOW of this one, or none of them do (see
    // reviveFully()). Definition-level rather than per-instance, so this is meant for a bespoke
    // scripted pair (e.g. a tutorial drill), not a generally-reusable enemy type spawned in
    // varying group sizes.
    public String pairId = null;
    // Health regenerated per second while active (0 = no regen, the default) - see
    // BaseEnemy.update()'s regen step. Caps at the enemy's starting health. Lets an enemy demand
    // sustained damage rather than dying to a handful of scattered hits - e.g. the tutorial's
    // bullet-streaming target, which should only fall to a continuous stream, not a stray graze.
    public float healthRegenPerSecond = 0f;
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