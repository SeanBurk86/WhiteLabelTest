package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.Enemy;

public class OrbitingBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private int damage = 1;
    private Enemy sourceEnemy;

    private float spawnCenterX, spawnCenterY;
    private float centerVx, centerVy;
    private float orbitRadius;
    private float orbitSpeed;
    private float initialPhase;
    private float time;

    private Animation<TextureRegion> animation;
    private float animationTime;

    private final Vector2 tempVelocity = new Vector2();

    public OrbitingBullet() {
        this.rectangle = new Rectangle();
    }

    public void init(Animation<TextureRegion> animation, float spawnCenterX, float spawnCenterY,
                     float centerVx, float centerVy,
                     float orbitRadius, float orbitSpeed, float initialPhase, float size, int damage, Enemy source) {
        this.animation = animation;
        this.damage = damage;
        this.sourceEnemy = source;
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        float aspectRatio = (float) frames[0].getRegionHeight() / frames[0].getRegionWidth();
        sprite.setSize(size, size * aspectRatio);
        sprite.setOriginCenter();

        this.spawnCenterX = spawnCenterX;
        this.spawnCenterY = spawnCenterY;
        this.centerVx = centerVx;
        this.centerVy = centerVy;
        this.orbitRadius = orbitRadius;
        this.orbitSpeed = orbitSpeed;
        this.initialPhase = initialPhase;
        this.time = 0;
        this.animationTime = 0;

        updatePosition();
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    private void updatePosition() {
        float angle = orbitSpeed * time + initialPhase;
        float cx = spawnCenterX + centerVx * time;
        float cy = spawnCenterY + centerVy * time;
        sprite.setCenter(cx + orbitRadius * MathUtils.cos(angle), cy + orbitRadius * MathUtils.sin(angle));

        // Instantaneous velocity = center velocity + tangential orbit velocity
        float vx = centerVx - orbitRadius * orbitSpeed * MathUtils.sin(angle);
        float vy = centerVy + orbitRadius * orbitSpeed * MathUtils.cos(angle);
        sprite.setRotation(tempVelocity.set(vx, vy).angleDeg() - 90);
    }

    @Override
    public void update(float delta) {
        time += delta;
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));
        updatePosition();
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        float cx = spawnCenterX + centerVx * time;
        float cy = spawnCenterY + centerVy * time;
        return cy + orbitRadius < -2f || cy - orbitRadius > 14f
            || cx + orbitRadius < 0f || cx - orbitRadius > 9f;
    }

    @Override
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public float getHitRadius() {
        return Math.min(rectangle.width, rectangle.height) / 2f;
    }

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
        animationTime = 0;
        sourceEnemy = null;
    }
}