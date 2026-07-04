package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.enemy.bullets.EnemyBullet;
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

        this.animation = AnimationCache.get(texture, def.frameCount, 0.1f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        sprite.setSize(def.size, def.size);
        sprite.setOriginCenter();

        // Use provided startX, or random if -1 was passed
        if (startX >= 0) {
            sprite.setX(startX);
        } else {
            sprite.setX(MathUtils.random(0.5f, worldWidth - (def.size + 0.5f)));
        }

        if (startY >= 0) {
            sprite.setY(startY);
        } else {
            sprite.setY(worldHeight + 1.0f); // Default to spawning above screen
        }

        this.health = def.health;
        this.animationTime = 0;

        // Initialize patterns
        this.movement = PatternFactory.createMovement(def.movementType, def.speed, worldWidth, worldHeight);
        this.firing = def.firingPattern != null
            ? PatternFactory.createFiring(def.firingPattern)
            : PatternFactory.createFiring(def.firingType, def.fireRate);

        this.spawnDuration = def.spawnDuration;
        this.spawnAnimation = (spawnTexture != null && def.spawnFrameCount > 0)
            ? AnimationCache.get(spawnTexture, def.spawnFrameCount, 0.05f, Animation.PlayMode.NORMAL)
            : null;

        this.deathDuration = def.deathDuration;
        this.deathAnimation = (deathTexture != null && def.deathFrameCount > 0)
            ? AnimationCache.get(deathTexture, def.deathFrameCount, 0.05f, Animation.PlayMode.NORMAL)
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
    public void update(float delta, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Circle playerHitbox) {
        super.update(delta, enemyBullets, this.bulletTexture, playerHitbox);
    }

    @Override
    public boolean isOffScreen() {
        // While dying, only the death animation controls when this enemy is actually reaped.
        if (lifecycleState == LifecycleState.DYING) return isDeathAnimationFinished();

        if (firing instanceof SelfDestructFiring && ((SelfDestructFiring)firing).isTriggered()) return true;
        if (movement != null && movement.isFinished()) return true;

        // Bounds checking that handles movement in any direction
        return sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > worldHeight + sprite.getHeight() * 2f ||
               sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > worldWidth;
    }

    @Override
    public Enemy create(Texture texture, float worldWidth, float worldHeight) {
        GenericEnemy e = ObjectPools.genericEnemyPool.obtain();
        e.initWithDefinition(this.def, texture, this.bulletTexture, this.spawnTexture, this.deathTexture, worldWidth, worldHeight, -1f, worldHeight);
        return e;
    }

    @Override
    public boolean isBoss() { return def != null && def.isBoss; }

    @Override
    public float getSpawnRate() { return 1.0f; }
}
