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

        // This is only the last-resort fallback bullet animation for firing patterns that don't
        // build their own (see PatternFactory.buildBulletAnimation) — e.g. the legacy scalar
        // firingType path, which has no FiringPatternDef to carry its own layout/frame duration.
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

        // NaN means "not specified" -> pick a default. Any finite value is used as-is, including
        // negative or beyond world bounds, so enemies can spawn off either edge of the screen.
        if (!Float.isNaN(startX)) {
            sprite.setX(startX);
        } else {
            sprite.setX(MathUtils.random(0.5f, worldWidth - (sprite.getWidth() + 0.5f)));
        }

        if (!Float.isNaN(startY)) {
            sprite.setY(startY);
        } else {
            sprite.setY(worldHeight + 1.0f); // Default to spawning above screen
        }

        this.health = def.health;
        this.maxHealth = def.health;
        this.animationTime = 0;

        // Initialize patterns
        this.movement = def.movementPattern != null
            ? PatternFactory.createMovement(def, def.movementPattern, worldHeight, sprite.getX() + sprite.getWidth() / 2f)
            : PatternFactory.createMovement(def.movementType, def.speed, worldHeight, def.movementAngle, def.stopDistance, sprite.getX() + sprite.getWidth() / 2f);
        this.firing = def.firingPattern != null
            ? PatternFactory.createFiring(def, def.firingPattern)
            : PatternFactory.createFiring(def.firingType, def.fireRate, def.bulletSize, def.bulletSpeed, def.firingOffsetX, def.firingOffsetY);

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
        // Fallback or random initialization if initWithDefinition isn't used
        initWithDefinition(null, texture, null, null, null, worldWidth, worldHeight, startX, startY);
    }

    @Override
    public boolean isOffScreen() {
        // While dying, only the death animation controls when this enemy is actually reaped.
        if (lifecycleState == LifecycleState.DYING) return isDeathAnimationFinished();

        if (firing instanceof SelfDestructFiring && ((SelfDestructFiring)firing).isTriggered()) return true;
        if (movement != null && movement.isFinished()) return true;

        // Bounds checking that handles movement in any direction. A margin equal to twice the
        // sprite's own size on each edge (matching the Y check) lets enemies spawn off either
        // side of the screen and stay alive long enough to move on-screen.
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
    public float getSpawnRate() { return 1.0f; }
}
