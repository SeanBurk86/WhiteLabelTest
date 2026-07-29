package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.enemy.HitboxSpec;
import whitelabeltest.enemy.SpeedProfile;

public class SineBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    // Null shape defaults to CIRCLE for this bullet type - see HitboxSpec.shape.
    private HitboxSpec hitboxSpec = HitboxSpec.DEFAULT;
    private int damage = 1;
    private Enemy sourceEnemy;

    private float spawnX, spawnY;
    private float amplitude;
    private float frequency;
    private float phase;
    private float currentSpeed;
    private final SpeedRamp speedRamp = new SpeedRamp();
    private float time;
    // Distance traveled down the Y axis so far - accumulated from currentSpeed each frame instead
    // of the old closed-form "spawnY - speed * time", since that formula only holds for a constant
    // speed; ramping currentSpeed means the Y offset has to be integrated frame by frame instead.
    private float traveledY;

    private Animation<TextureRegion> animation;
    private float animationTime;

    private final Vector2 tempVelocity = new Vector2();

    public SineBullet() {
        this.rectangle = new Rectangle();
    }

    public void init(Animation<TextureRegion> animation, float x, float y, float amplitude, float frequency, float phase, float speed, float size, int damage, Enemy source) {
        init(animation, x, y, amplitude, frequency, phase, speed, size, damage, source, SpeedProfile.CONSTANT_SPEED, HitboxSpec.DEFAULT);
    }

    /** @param speedProfile how currentSpeed changes over the bullet's flight - see SpeedProfile;
     *  pass SpeedProfile.CONSTANT_SPEED for the classic constant-speed behavior
     *  @param hitboxSpec the bullet's collision hitbox, independent of its visual size - see
     *  HitboxSpec; pass HitboxSpec.DEFAULT for the classic "hitbox exactly fits the sprite" */
    public void init(Animation<TextureRegion> animation, float x, float y, float amplitude, float frequency, float phase, float speed, float size, int damage, Enemy source, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
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

        this.spawnX = x;
        this.spawnY = y;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.phase = phase;
        this.currentSpeed = speed;
        speedRamp.set(speedProfile);
        this.time = 0;
        this.traveledY = 0;
        this.animationTime = 0;

        rectangle.set(sprite.getX() + .036f, sprite.getY() + .036f, sprite.getWidth() * .6f, sprite.getHeight() * .6f);
    }

    @Override
    public void update(float delta) {
        time += delta;
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (speedRamp.isActive()) currentSpeed = speedRamp.apply(currentSpeed, delta);
        traveledY += currentSpeed * delta;

        float newX = spawnX + amplitude * MathUtils.sin(frequency * time + phase);
        float newY = spawnY - traveledY;
        sprite.setCenter(newX, newY);

        // Rotate sprite to face its direction of travel
        float vx = amplitude * frequency * MathUtils.cos(frequency * time + phase);
        float vy = -currentSpeed;
        sprite.setRotation(tempVelocity.set(vx, vy).angleDeg() - 90);

        rectangle.setPosition(sprite.getX() + .036f, sprite.getY() + .036f);
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return sprite.getY() + sprite.getHeight() < -2f || sprite.getY() > 14f
            || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > 9f;
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
        time = 0;
        traveledY = 0;
        currentSpeed = 0f;
        speedRamp.reset();
        hitboxSpec = HitboxSpec.DEFAULT;
        animationTime = 0;
        sourceEnemy = null;
    }
}
