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

/** One dot of a ShapeFiring picture. All dots leave the same point together; each spreads out in
 *  proportion to its place in the shape so that at formTime they form the picture at formScale,
 *  then keep drifting apart.
 *
 *  Position = origin + direction * travelled + offset * spread(t), where travelled follows the
 *  shared speed profile and spread(t) is:
 *    t < formTime:  u*t + a*t^2/2,  with u = (2 - r) * formScale / formTime and
 *                                   a = 2 * (r - 1) * formScale / formTime^2
 *    t >= formTime: formScale + r * formScale / formTime * (t - formTime)
 *  r (driftRatio) = post-formation spread speed relative to the average forming speed: 1 = no
 *  acceleration, < 1 bursts out then settles into the shape, > 1 starts slow and speeds up.
 *  formTime <= 0 = formed and rigid from the start. */
public class ShapeBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle = new Rectangle();
    private final Vector2 origin = new Vector2();
    private final Vector2 direction = new Vector2();
    // This dot's offset at scale 1, already flipped/rotated to world orientation.
    private final Vector2 offset = new Vector2();
    private float travelled;
    private float currentSpeed;
    private final SpeedRamp speedRamp = new SpeedRamp();
    private float age;
    private float formScale;
    private float formTime;
    private float initialSpreadSpeed;
    private float spreadAcceleration;
    private float driftSpreadSpeed;
    private HitboxSpec hitboxSpec = HitboxSpec.DEFAULT;
    private int damage = 1;
    private Enemy sourceEnemy;

    private Animation<TextureRegion> animation;
    private float animationTime = 0;

    /** @param originX,originY where every dot starts (usually the emitter)
     *  @param angleDeg travel direction (0 = right, 90 = up)
     *  @param offsetX,offsetY this dot's position in the picture at scale 1, already oriented
     *  @param driftRatio clamped to [0, 2] so no dot moves back inward */
    public void init(Animation<TextureRegion> animation, float originX, float originY, float angleDeg, float offsetX, float offsetY,
                     float formScale, float formTime, float driftRatio,
                     float size, float speed, int damage, Enemy source, SpeedProfile speedProfile, HitboxSpec hitboxSpec) {
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
        sprite.setRotation(0);
        sprite.setColor(1, 1, 1, 1);

        origin.set(originX, originY);
        direction.set(1, 0).setAngleDeg(angleDeg);
        offset.set(offsetX, offsetY);
        travelled = 0f;
        currentSpeed = speed;
        speedRamp.set(speedProfile);
        age = 0f;
        this.formScale = formScale;
        this.formTime = formTime;
        if (formTime > 0f) {
            float r = Math.max(0f, Math.min(2f, driftRatio));
            initialSpreadSpeed = (2f - r) * formScale / formTime;
            spreadAcceleration = 2f * (r - 1f) * formScale / (formTime * formTime);
            driftSpreadSpeed = r * formScale / formTime;
        } else {
            initialSpreadSpeed = 0f;
            spreadAcceleration = 0f;
            driftSpreadSpeed = 0f;
        }
        animationTime = 0;
        place();
    }

    /** The picture's current size (see the class doc). */
    private float spread() {
        if (formTime <= 0f) return formScale;
        if (age < formTime) return initialSpreadSpeed * age + 0.5f * spreadAcceleration * age * age;
        return formScale + driftSpreadSpeed * (age - formTime);
    }

    private void place() {
        float spread = spread();
        sprite.setCenter(origin.x + direction.x * travelled + offset.x * spread,
                         origin.y + direction.y * travelled + offset.y * spread);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void update(float delta) {
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (speedRamp.isActive()) currentSpeed = speedRamp.apply(currentSpeed, delta);
        travelled += currentSpeed * delta;
        age += delta;
        place();
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return sprite.getY() < -1f || sprite.getY() > 13f || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > 9f;
    }

    @Override
    public Rectangle getRectangle() { return rectangle; }

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
        origin.setZero();
        direction.setZero();
        offset.setZero();
        travelled = 0f;
        currentSpeed = 0f;
        speedRamp.reset();
        age = 0f;
        formScale = 1f;
        formTime = 0f;
        initialSpreadSpeed = 0f;
        spreadAcceleration = 0f;
        driftSpreadSpeed = 0f;
        hitboxSpec = HitboxSpec.DEFAULT;
        animationTime = 0;
        sourceEnemy = null;
    }
}
