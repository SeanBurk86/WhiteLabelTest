package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.FiringPattern;
import whitelabeltest.enemy.movementpatterns.MovementPattern;

public abstract class BaseEnemy implements Enemy {
    protected Sprite sprite;
    protected Rectangle rectangle;
    protected int health;
    protected String guaranteedPowerup;
    protected float worldWidth, worldHeight;
    protected boolean invertMovement; // Added field to store inversion state

    protected Animation<TextureRegion> animation;
    protected float animationTime = 0;

    protected float damageFlashTimer = 0;
    protected final float flashDuration = 0.05f;

    protected MovementPattern movement;
    protected FiringPattern firing;

    public BaseEnemy() {
        this.rectangle = new Rectangle();
    }

    @Override
    public void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.invertMovement = false;
    }

    @Override
    public void update(float delta, Array<EnemyBullet> enemyBullets, Texture bulletTexture, Circle playerHitbox) {
        if (sprite == null) return;

        animationTime += delta;
        if (animation != null) {
            sprite.setRegion(animation.getKeyFrame(animationTime));
        }

        if (damageFlashTimer > 0) {
            damageFlashTimer -= delta;
            sprite.setColor(1, 0, 0, 1);
        } else {
            sprite.setColor(1, 1, 1, 1);
        }

        if (movement != null) {
            movement.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, invertMovement);
        }

        if (firing != null) {
            firing.update(delta, sprite, rectangle, enemyBullets, bulletTexture, playerHitbox);
        }
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null && !isOffScreen()) {
            sprite.draw(batch);
        }
    }

    @Override
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public boolean takeDamage(int amount) {
        health -= amount;
        damageFlashTimer = flashDuration;
        return health <= 0;
    }

    @Override
    public void setGuaranteedPowerup(String type) {
        this.guaranteedPowerup = type;
    }

    @Override
    public String getGuaranteedPowerup() {
        return guaranteedPowerup;
    }

    @Override
    public void setInvertMovement(boolean invert) { // Implemented method from Enemy interface
        this.invertMovement = invert;
    }

    @Override
    public void reset() {
        animationTime = 0;
        damageFlashTimer = 0;
        guaranteedPowerup = null;
        invertMovement = false; // Reset on pool
        if (sprite != null) {
            sprite.setRotation(0);
            sprite.setColor(1, 1, 1, 1);
        }
        if (movement != null) movement.reset();
        if (firing != null) firing.reset();
    }
}
