package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.enemy.firingpatterns.SelfDestructFiring;

public class GenericEnemy extends BaseEnemy {

    private EnemyDefinition def;
    private Texture bulletTexture;
    private Texture spawnTexture;
    private Texture deathTexture;

    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, Float.NaN, Float.NaN, null);
    }

    /** @param formationOffsetX, formationOffsetY this spawn's slot in a squad formation, passed
     *  straight through to PatternFactory.createMovement - see its javadoc. NaN (the other
     *  overload above) means "not part of a formation spawned this way". */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, null);
    }

    /** @param movementPatternId overrides def.movementPattern when non-null - lets several spawn
     *  events share one enemy definition while still steering each toward a different movement
     *  pattern (e.g. two squads with the same stats but a different rally point/exit), instead of
     *  needing a near-duplicate enemy definition that differs only in movementPattern. */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId) {
        this.def = def;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.bulletTexture = bulletTexture;
        this.spawnTexture = spawnTexture;
        this.deathTexture = deathTexture;
        this.invertMovement = def.inverseMovement;
        this.rotateWithMovement = def.rotateWithMovement;

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

        if (!Float.isNaN(startX)) {
            sprite.setX(startX);
        } else {
            sprite.setX(MathUtils.random(0.5f, worldWidth - (sprite.getWidth() + 0.5f)));
        }

        if (!Float.isNaN(startY)) {
            sprite.setY(startY);
        } else {
            sprite.setY(worldHeight + 1.0f);
        }

        this.health = def.health;
        this.maxHealth = def.health;
        this.animationTime = 0;

        String resolvedMovementPattern = movementPatternId != null ? movementPatternId : def.movementPattern;
        this.movement = PatternFactory.createMovement(PatternRegistry.getMovement(resolvedMovementPattern), worldHeight, sprite.getX() + sprite.getWidth() / 2f, formationOffsetX, formationOffsetY);
        this.firing = PatternFactory.createFiring(def, PatternRegistry.getFiring(def.firingPattern));

        this.spawnDuration = def.spawnDuration;
        this.spawnAnimation = (spawnTexture != null && def.spawnFrameCount > 0)
            ? AnimationCache.get(spawnTexture, def.spawnColumns > 0 ? def.spawnColumns : def.spawnFrameCount, def.spawnRows, def.spawnFrameCount, 0.05f, Animation.PlayMode.NORMAL)
            : null;

        this.deathDuration = def.deathDuration;
        this.deathAnimation = (deathTexture != null && def.deathFrameCount > 0)
            ? AnimationCache.get(deathTexture, def.deathColumns > 0 ? def.deathColumns : def.deathFrameCount, def.deathRows, def.deathFrameCount, 0.05f, Animation.PlayMode.NORMAL)
            : null;

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());

        beginEntrance();
    }

    @Override
    public void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY) {
        initWithDefinition(null, texture, null, null, null, worldWidth, worldHeight, startX, startY);
    }

    @Override
    public boolean isOffScreen() {
        if (lifecycleState == LifecycleState.DYING) return isDeathAnimationFinished();

        if (firing instanceof SelfDestructFiring && ((SelfDestructFiring)firing).isTriggered()) return true;
        if (movement != null && movement.isFinished()) return true;

        return sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > worldHeight + sprite.getHeight() * 2f ||
               sprite.getX() + sprite.getWidth() < -sprite.getWidth() * 2f || sprite.getX() > worldWidth + sprite.getWidth() * 2f;
    }

    @Override
    public Enemy create(Texture texture, float worldWidth, float worldHeight) {
        GenericEnemy e = ObjectPools.genericEnemyPool.obtain();
        e.initWithDefinition(this.def, texture, this.bulletTexture, this.spawnTexture, this.deathTexture, worldWidth, worldHeight, Float.NaN, worldHeight);
        return e;
    }

    @Override
    public boolean isBoss() { return def != null && def.isBoss; }

    @Override
    public boolean isGround() { return def != null && def.isGround; }

    @Override
    public boolean isSealable() { return def != null && def.sealable; }

    @Override
    public boolean isDefiant() { return def != null && def.defiant; }

    @Override
    public boolean cancelsBulletsOnDeath() { return def != null && def.bulletCancel; }

    @Override
    public int getScore() { return def != null ? def.score : 10; }

    @Override
    public String getExplosionPattern() { return def != null ? def.explosionPattern : null; }

    @Override
    public float getSpawnRate() { return 1.0f; }
}
