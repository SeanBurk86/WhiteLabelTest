package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.SelfDestructFiring;

public class GenericEnemy extends BaseEnemy {

    private EnemyDefinition def;
    private Texture secondaryBulletTexture;

    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture, float worldWidth, float worldHeight, float startX, float startY) {
        this.def = def;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.secondaryBulletTexture = bulletTexture;
        this.invertMovement = def.inverseMovement; // Apply inversion from definition

        int frameWidth = texture.getWidth() / def.frameCount;
        int frameHeight = texture.getHeight();
        TextureRegion[][] tmp = TextureRegion.split(texture, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[def.frameCount];
        System.arraycopy(tmp[0], 0, frames, 0, def.frameCount);

        this.animation = new Animation<>(0.1f, frames);
        this.animation.setPlayMode(Animation.PlayMode.LOOP);

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
        this.firing = PatternFactory.createFiring(def.firingType, def.fireRate);

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY) {
        // Fallback or random initialization if initWithDefinition isn't used
        initWithDefinition(null, texture, null, worldWidth, worldHeight, startX, startY);
    }

    @Override
    public void update(float delta, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Rectangle playerHitbox) {
        super.update(delta, enemyBullets, secondaryBulletTexture, playerHitbox);
    }

    @Override
    public boolean isOffScreen() {
        if (firing instanceof SelfDestructFiring && ((SelfDestructFiring)firing).isTriggered()) return true;
        if (movement != null && movement.isFinished()) return true;

        // Bounds checking that handles movement in any direction
        return sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > worldHeight + sprite.getHeight() * 2f ||
               sprite.getX() < -sprite.getWidth() * 2f || sprite.getX() > worldWidth + sprite.getWidth() * 2f;
    }

    @Override
    public Enemy create(Texture texture, float worldWidth, float worldHeight) {
        GenericEnemy e = ObjectPools.genericEnemyPool.obtain();
        e.initWithDefinition(this.def, texture, this.secondaryBulletTexture, worldWidth, worldHeight, -1f, worldHeight);
        return e;
    }

    @Override
    public float getSpawnRate() { return 1.0f; }
}
