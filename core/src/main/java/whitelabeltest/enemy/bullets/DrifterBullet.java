package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.HitboxSpec;
import whitelabeltest.enemy.SpeedProfile;

public class DrifterBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    // Kept separate from velocity so currentSpeed can keep ramping over the bullet's flight (see
    // update()) without needing to re-derive direction from anywhere.
    private final Vector2 direction = new Vector2();
    private final Vector2 velocity = new Vector2();
    private float currentSpeed;
    private final SpeedRamp speedRamp = new SpeedRamp();
    // Null shape defaults to CIRCLE for this bullet type - see HitboxSpec.shape.
    private HitboxSpec hitboxSpec = HitboxSpec.DEFAULT;
    private int damage = 1;
    private Enemy sourceEnemy;

    private Animation<TextureRegion> animation;
    private float animationTime = 0;

    public DrifterBullet() {
        this.rectangle = new Rectangle();
    }

    public void init(Animation<TextureRegion> animation, float x, float y, float vx, float vy, float size, float speed, int damage, Enemy source) {
        init(animation, x, y, vx, vy, size, speed, damage, source, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile how currentSpeed changes over the bullet's flight - see SpeedProfile;
     *  pass SpeedProfile.CONSTANT_SPEED for the classic constant-speed behavior
     *  @param hitboxSpec the bullet's collision hitbox, independent of its visual size - see
     *  HitboxSpec; pass HitboxSpec.DEFAULT for the classic "hitbox exactly fits the sprite" */
    public void init(Animation<TextureRegion> animation, float x, float y, float vx, float vy, float size, float speed, int damage, Enemy source, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
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

        direction.set(vx, vy).nor();
        this.currentSpeed = speed;
        speedRamp.set(speedProfile);
        velocity.set(direction).scl(currentSpeed);
        sprite.setRotation(velocity.angleDeg() - 90);

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        this.animationTime = 0;
    }

    @Override
    public void update(float delta) {
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (speedRamp.isActive()) {
            currentSpeed = speedRamp.apply(currentSpeed, delta);
            velocity.set(direction).scl(currentSpeed);
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
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public float getHitRadius() {
        HitboxSpec.Shape shape = hitboxSpec.shape != null ? hitboxSpec.shape : HitboxSpec.Shape.CIRCLE;
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

    // The rect is already centered on the sprite's true center (see init()'s use of
    // setCenterX/Y), the same point Sprite.setOriginCenter() rotates the sprite around - so
    // pivoting the hitbox there keeps it turning in lockstep with the sprite.
    @Override
    public float getRotationPivotX() { return rectangle.x + rectangle.width / 2f; }

    @Override
    public float getRotationPivotY() { return rectangle.y + rectangle.height / 2f; }

    @Override
    public int getDamage() {
        return damage;
    }

    @Override
    public Enemy getSourceEnemy() { return sourceEnemy; }

    @Override
    public Sprite getSprite() { return sprite; }

    @Override
    public void reset() {
        animationTime = 0;
        direction.setZero();
        velocity.setZero();
        currentSpeed = 0f;
        speedRamp.reset();
        hitboxSpec = HitboxSpec.DEFAULT;
        sourceEnemy = null;
    }
}
