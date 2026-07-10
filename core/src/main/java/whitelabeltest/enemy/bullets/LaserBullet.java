package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** A persistent beam anchored at a fixed emission point that can rotate around that point over
 *  its lifetime, unlike other bullets which translate away from where they were fired. Expires
 *  after `duration` seconds rather than by leaving the screen. */
public class LaserBullet implements EnemyBullet {
    // The hitbox is narrower than the rendered beam (as SineBullet's rectangle is smaller than
    // its sprite too) so a visually chunky beam doesn't feel unfairly wide to dodge.
    private static final float HITBOX_THICKNESS_SCALE = 0.4f;

    private Sprite sprite;
    private final Rectangle rectangle = new Rectangle();
    private final int damage = 1;

    private float originX, originY;
    private float angleDeg;
    private float angularSpeed;
    private float length;
    private float thickness;
    private float elapsedTime;
    private float duration;

    private Animation<TextureRegion> animation;
    private float animationTime;

    /** @param startAngleDeg, angularSpeed standard math convention (0 = right, 90 = up); the beam
     *  rotates at angularSpeed degrees/second for its whole lifetime (0 = doesn't rotate). */
    public void init(Animation<TextureRegion> animation, float originX, float originY, float startAngleDeg, float angularSpeed, float length, float thickness, float duration) {
        this.animation = animation;
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        this.originX = originX;
        this.originY = originY;
        this.angleDeg = startAngleDeg;
        this.angularSpeed = angularSpeed;
        this.length = length;
        this.thickness = thickness;
        this.duration = duration;
        this.elapsedTime = 0f;
        this.animationTime = 0f;

        sprite.setSize(thickness, length);
        sprite.setOrigin(thickness / 2f, 0f); // pivot at the emission point (base of the beam)
        // The origin never moves once fired, so the un-rotated box is constant for the bullet's
        // whole lifetime — only its rotation (see getRotation()) changes as the beam sweeps.
        float hitboxThickness = thickness * HITBOX_THICKNESS_SCALE;
        rectangle.set(originX - hitboxThickness / 2f, originY, hitboxThickness, length);
        updateTransform();
    }

    @Override
    public void update(float delta) {
        elapsedTime += delta;
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        angleDeg += angularSpeed * delta;
        updateTransform();
    }

    private void updateTransform() {
        sprite.setPosition(originX - thickness / 2f, originY);
        sprite.setRotation(angleDeg - 90f);
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return elapsedTime >= duration;
    }

    @Override
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public float getRotation() {
        return angleDeg - 90f;
    }

    @Override
    public int getDamage() {
        return damage;
    }

    @Override
    public void reset() {
        elapsedTime = 0f;
        angularSpeed = 0f;
    }
}