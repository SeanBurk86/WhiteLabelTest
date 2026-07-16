package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.bullets.EnemyBullet;

public class ExplodingAimedBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private float speed;
    private int damage = 1;
    private Enemy sourceEnemy;

    private Animation<TextureRegion> animation;
    private float animationTime = 0;

    private float stateTime = 0;
    private final float explodeDuration = 0.4f;
    private boolean isAimed = false;
    private Circle playerHitbox; // Reference to track for aiming trigger

    public ExplodingAimedBullet() {
        this.rectangle = new Rectangle();
    }

    public void init(Animation<TextureRegion> animation, float x, float y, float angle, Circle playerHitbox, float size, float speed, int damage, Enemy source) {
        this.animation = animation;
        this.damage = damage;
        this.sourceEnemy = source;
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        float aspectRatio = (float) frames[0].getRegionHeight() / frames[0].getRegionWidth();
        sprite.setSize(size, size * aspectRatio);
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setCenterY(y);

        this.speed = speed;
        // Initial radial velocity
        velocity.set(1, 0).setAngleDeg(angle).scl(speed);
        sprite.setRotation(angle - 90);

        this.playerHitbox = playerHitbox;
        this.stateTime = 0;
        this.isAimed = false;
        this.animationTime = 0;
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void update(float delta) {
        animationTime += delta;
        stateTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (!isAimed && stateTime >= explodeDuration) {
            isAimed = true;
            // Re-aim at player's CURRENT position
            float targetX = playerHitbox.x;
            float targetY = playerHitbox.y;
            float currentX = sprite.getX() + sprite.getWidth() / 2;
            float currentY = sprite.getY() + sprite.getHeight() / 2;

            velocity.set(targetX - currentX, targetY - currentY).nor().scl(speed * 1.2f);
            sprite.setRotation(velocity.angleDeg() - 90);
        }

        sprite.translate(velocity.x * delta, velocity.y * delta);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return sprite.getY() < -2f || sprite.getY() > 14f || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > 9f;
    }

    @Override
    public Rectangle getRectangle() { return rectangle; }
    @Override
    public int getDamage() { return damage; }

    @Override
    public Enemy getSourceEnemy() { return sourceEnemy; }

    @Override
    public Sprite getSprite() { return sprite; }

    @Override
    public void reset() {
        stateTime = 0;
        isAimed = false;
        velocity.setZero();
        sourceEnemy = null;
    }
}
