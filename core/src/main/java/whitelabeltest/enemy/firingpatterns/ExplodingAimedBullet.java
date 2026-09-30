package whitelabeltest.enemy.firingpatterns;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.HitboxSpec;
import whitelabeltest.enemy.SpeedProfile;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.bullets.SpeedRamp;

/** Flies outward from its burst for explodeDuration, then re-aims at the player at 1.2x speed. */
public class ExplodingAimedBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private float speed;
    private final SpeedRamp speedRamp = new SpeedRamp();
    // A null shape defaults to RECTANGLE for this bullet type.
    private HitboxSpec hitboxSpec = HitboxSpec.DEFAULT;
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
        init(animation, x, y, angle, playerHitbox, size, speed, damage, source, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile applies before and after the re-aim (which boosts the current speed)
     *  @param hitboxSpec see HitboxSpec */
    public void init(Animation<TextureRegion> animation, float x, float y, float angle, Circle playerHitbox, float size, float speed, int damage, Enemy source, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
        this.animation = animation;
        this.hitboxSpec = hitboxSpec != null ? hitboxSpec : HitboxSpec.DEFAULT;
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
        speedRamp.set(speedProfile);
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

        if (speedRamp.isActive()) {
            speed = speedRamp.apply(speed, delta);
            velocity.setLength(speed);
        }

        if (!isAimed && stateTime >= explodeDuration) {
            isAimed = true;
            // Re-aim at the player's current position.
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
    public float getHitRadius() {
        HitboxSpec.Shape shape = hitboxSpec.shape != null ? hitboxSpec.shape : HitboxSpec.Shape.RECTANGLE;
        return shape == HitboxSpec.Shape.CIRCLE ? Math.min(rectangle.width, rectangle.height) / 2f : -1f;
    }

    @Override
    public float getHitboxScale() { return hitboxSpec.scale; }

    @Override
    public float getHitboxOffsetX() { return hitboxSpec.offsetX; }

    @Override
    public float getHitboxOffsetY() { return hitboxSpec.offsetY; }

    @Override
    public float getRotation() { return sprite.getRotation(); }

    // Pivot at the rect center, where the sprite rotates (setOriginCenter()).
    @Override
    public float getRotationPivotX() { return rectangle.x + rectangle.width / 2f; }

    @Override
    public float getRotationPivotY() { return rectangle.y + rectangle.height / 2f; }

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
        speed = 0f;
        speedRamp.reset();
        hitboxSpec = HitboxSpec.DEFAULT;
        sourceEnemy = null;
    }
}
