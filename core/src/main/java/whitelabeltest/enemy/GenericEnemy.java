package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.spawning.EnemySpawnRegistry;
import whitelabeltest.gamemanagers.trigger.EnemyEntranceMovement;
import whitelabeltest.gamemanagers.trigger.Trigger;
import whitelabeltest.enemy.firingpatterns.FiringPattern;
import whitelabeltest.enemy.firingpatterns.SelfDestructFiring;
import whitelabeltest.enemy.movementpatterns.MovementPattern;

/** The data-driven enemy: every enemy is a GenericEnemy built from an EnemyDefinition plus the
 *  spawn's own movement/firing pattern ids. */
public class GenericEnemy extends BaseEnemy {

    private EnemyDefinition def;

    @Override
    public Array<HitboxDef> getHitboxDefs() {
        return def != null && def.hitboxes != null && def.hitboxes.size > 0 ? def.hitboxes : null;
    }
    private Texture bulletTexture;
    private Texture spawnTexture;
    private Texture deathTexture;
    // Kept so a health-phase movement swap uses the same formation slot.
    private float formationOffsetX = Float.NaN;
    private float formationOffsetY = Float.NaN;

    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, Float.NaN, Float.NaN, null);
    }

    /** @param formationOffsetX,formationOffsetY squad formation slot (NaN = none); see
     *  PatternFactory.createMovement. */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, null);
    }

    /** @param movementPatternId the spawn's movement pattern; null = doesn't move (no type-level default). */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, movementPatternId, null);
    }

    /** @param firingPatternId overrides def.firingPattern when non-null. */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId, String firingPatternId) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, movementPatternId, firingPatternId, null, 0f);
    }

    /** @param entranceTrigger if set, applies EnemyEntranceMovement (built here because it needs the
     *  real sprite size). */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId, String firingPatternId,
                                    Trigger entranceTrigger, float cameraSpeed) {
        this.def = def;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.bulletTexture = bulletTexture;
        this.spawnTexture = spawnTexture;
        this.deathTexture = deathTexture;
        this.invertMovement = def.inverseMovement;
        this.rotateWithMovement = def.rotateWithMovement;
        this.facePlayer = def.facePlayer;
        this.formationOffsetX = formationOffsetX;
        this.formationOffsetY = formationOffsetY;

        this.animation = AnimationCache.get(texture, def.columns > 0 ? def.columns : def.frameCount, def.rows, def.frameCount, def.frameDuration, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        this.bulletAnimation = (bulletTexture != null)
            ? AnimationCache.get(bulletTexture, FiringPatternDef.DEFAULT_BULLET_COLUMNS > 0 ? FiringPatternDef.DEFAULT_BULLET_COLUMNS : FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT,
                FiringPatternDef.DEFAULT_BULLET_ROWS, FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT, FiringPatternDef.DEFAULT_BULLET_FRAME_DURATION, Animation.PlayMode.LOOP)
            : null;

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        float aspect = frames[0].getRegionWidth() / (float) frames[0].getRegionHeight();
        if (aspect >= 1f) {
            sprite.setSize(def.size, def.size / aspect);
        } else {
            sprite.setSize(def.size * aspect, def.size);
        }
        sprite.setOriginCenter();
        this.unitsPerPixel = def.uniformPixelScale
            ? def.size / Math.max(frames[0].getRegionWidth(), frames[0].getRegionHeight())
            : Float.NaN;
        this.flipWithDirection = def.flipWithDirection;

        if (!Float.isNaN(startX)) {
            sprite.setX(startX);
        } else {
            sprite.setX(MathUtils.random(0.5f, worldWidth - (sprite.getWidth() + 0.5f)));
        }

        this.health = def.health;
        this.maxHealth = def.health;
        this.healthRegenPerSecond = def.healthRegenPerSecond;
        this.animationTime = 0;

        // A null id resolves to NoMovement. Built before Y is set because the entrance spawn height
        // inspects this pattern; movement only depends on X.
        this.movement = PatternFactory.createMovement(PatternRegistry.getMovement(movementPatternId), worldWidth, worldHeight, sprite.getX() + sprite.getWidth() / 2f, formationOffsetX, formationOffsetY);

        // Entrance spawns start off-screen, computed now that the real sprite height is known.
        if (entranceTrigger != null && entranceTrigger.enterFromAbove && !Float.isNaN(entranceTrigger.y)) {
            startY = EnemyEntranceMovement.spawnY(entranceTrigger, worldHeight, sprite.getHeight(), this.movement);
        }

        if (!Float.isNaN(startY)) {
            sprite.setY(startY);
        } else {
            sprite.setY(worldHeight + 1.0f);
        }

        if (entranceTrigger != null) {
            MovementPattern entrance = EnemyEntranceMovement.build(entranceTrigger, cameraSpeed, worldHeight, sprite.getWidth(), sprite.getHeight(), this.movement);
            if (entrance != null) this.movement = entrance;
        }
        String resolvedFiringPattern = firingPatternId != null ? firingPatternId : def.firingPattern;
        this.firing = PatternFactory.createFiring(def, PatternRegistry.getFiring(resolvedFiringPattern), worldWidth, worldHeight);

        this.spawnDuration = def.spawnDuration;
        this.spawnAnimation = (spawnTexture != null && def.spawnFrameCount > 0)
            ? AnimationCache.get(spawnTexture, def.spawnColumns > 0 ? def.spawnColumns : def.spawnFrameCount, def.spawnRows, def.spawnFrameCount,
                def.spawnFrameDuration > 0f ? def.spawnFrameDuration : 0.05f, Animation.PlayMode.NORMAL)
            : null;

        this.deathDuration = def.deathDuration;
        this.deathAnimation = (deathTexture != null && def.deathFrameCount > 0)
            ? AnimationCache.get(deathTexture, def.deathColumns > 0 ? def.deathColumns : def.deathFrameCount, def.deathRows, def.deathFrameCount, 0.05f, Animation.PlayMode.NORMAL)
            : null;

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());

        beginEntrance();
    }

    /** Centers the sprite on (x, y) (for SpawnEnemyFiring). Call before the first update(). */
    public void centerOn(float x, float y) {
        sprite.setCenter(x, y);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY) {
        initWithDefinition(null, texture, null, null, null, worldWidth, worldHeight, startX, startY);
    }

    /** Ready for removal when its death animation ends, it self-destructed, its movement finished,
     *  or it left the bounds (two sprite sizes past each edge). Leaving the bounds only counts after
     *  it has been inside them once, so off-screen spawns can fly in. */
    @Override
    public boolean isOffScreen() {
        if (lifecycleState == LifecycleState.DYING) return isDeathAnimationFinished();

        if (firing instanceof SelfDestructFiring && ((SelfDestructFiring)firing).isTriggered()) return true;
        if (movement != null && movement.isFinished()) return true;

        boolean outsideBounds = sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > worldHeight + sprite.getHeight() * 2f ||
               sprite.getX() + sprite.getWidth() < -sprite.getWidth() * 2f || sprite.getX() > worldWidth + sprite.getWidth() * 2f;
        if (!outsideBounds) {
            hasBeenOnScreen = true;
            return false;
        }
        return hasBeenOnScreen;
    }

    @Override
    public Enemy create(Texture texture, float worldWidth, float worldHeight) {
        GenericEnemy e = ObjectPools.genericEnemyPool.obtain();
        e.initWithDefinition(this.def, texture, this.bulletTexture, this.spawnTexture, this.deathTexture, worldWidth, worldHeight, Float.NaN, worldHeight);
        return e;
    }

    /** Looks the name up in def.weaponSets; null (no swap) if missing or unknown. */
    @Override
    protected FiringPattern resolveWeaponSet(String weaponSetName) {
        if (def == null || def.weaponSets == null || weaponSetName == null) return null;
        return resolveFiringPattern(def.weaponSets.get(weaponSetName));
    }

    /** Builds a firing pattern by id; null (no swap) if unknown. */
    @Override
    protected FiringPattern resolveFiringPattern(String firingPatternId) {
        if (def == null || firingPatternId == null) return null;
        FiringPatternDef patternDef = PatternRegistry.getFiring(firingPatternId);
        if (patternDef == null) return null;
        return PatternFactory.createFiring(def, patternDef, worldWidth, worldHeight);
    }

    /** Builds a def.animations entry; null (no swap) if missing or its texture isn't loaded. */
    @Override
    protected Animation<TextureRegion> resolveAnimation(String animationName) {
        if (def == null || def.animations == null) return null;
        EnemyAnimationDef animDef = def.animations.get(animationName);
        if (animDef == null) return null;
        Texture texture = EnemySpawnRegistry.getTexture(animDef.texture);
        if (texture == null) return null;
        return AnimationCache.get(texture, animDef.columns > 0 ? animDef.columns : animDef.frameCount, animDef.rows, animDef.frameCount,
            animDef.frameDuration, animDef.loop ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL);
    }

    /** Builds a movement pattern starting from the current position; null (no swap) if unknown. */
    @Override
    protected MovementPattern resolveMovementPattern(String movementPatternId) {
        MovementPatternDef patternDef = PatternRegistry.getMovement(movementPatternId);
        if (patternDef == null) return null;
        return PatternFactory.createMovement(patternDef, worldWidth, worldHeight, sprite.getX() + sprite.getWidth() / 2f, formationOffsetX, formationOffsetY);
    }

    @Override
    public boolean isBoss() { return def != null && def.isBoss; }

    @Override
    public boolean isGround() { return def != null && def.isGround; }

    @Override
    public int getBackgroundLayer() { return def != null ? def.backgroundLayer : -1; }

    @Override
    public boolean isSealable() { return def != null && def.sealable; }

    @Override
    public boolean ignoresCeasefireZone() { return def != null && def.ignoreCeasefireZone; }

    @Override
    public boolean isDefiant() { return def != null && def.defiant; }

    @Override
    public boolean isDamageableByEnemyBullets() { return def != null && def.damageableByEnemyBullets; }

    @Override
    public boolean showsHealthBar() { return def != null && def.showHealthBar; }

    @Override
    public boolean isTargetableByHoming() { return def == null || def.targetableByHoming; }

    @Override
    public String getPairId() { return def != null ? def.pairId : null; }

    @Override
    public String getDefinitionId() { return def != null ? def.id : null; }

    @Override
    public boolean cancelsBulletsOnDeath() { return def != null && def.bulletCancel; }

    @Override
    public int getScore() { return def != null ? def.score : 10; }

    @Override
    public String getExplosionPattern() { return def != null ? def.explosionPattern : null; }

    @Override
    public float getSpawnRate() { return 1.0f; }
}
