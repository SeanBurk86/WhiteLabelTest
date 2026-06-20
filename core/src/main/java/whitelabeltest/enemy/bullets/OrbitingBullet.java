package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class OrbitingBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final int damage = 1;

    private float spawnCenterX, spawnCenterY;
    private float centerVx, centerVy;
    private float orbitRadius;
    private float orbitSpeed;
    private float initialPhase;
    private float time;

    private Animation<TextureRegion> animation;
    private float animationTime;

    public OrbitingBullet() {
        this.rectangle = new Rectangle();
    }

    public void init(Texture texture, float spawnCenterX, float spawnCenterY,
                     float centerVx, float centerVy,
                     float orbitRadius, float orbitSpeed, float initialPhase) {
        int frameWidth = texture.getWidth() / 3;
        int frameHeight = texture.getHeight();
        TextureRegion[][] tmp = TextureRegion.split(texture, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[3];
        System.arraycopy(tmp[0], 0, frames, 0, 3);
        animation = new Animation<>(0.1f, frames);
        animation.setPlayMode(Animation.PlayMode.LOOP);

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        float aspectRatio = (float) frameHeight / frameWidth;
        float baseWidth = 0.5f;
        sprite.setSize(baseWidth, baseWidth * aspectRatio);
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
        sprite.setRotation(new Vector2(vx, vy).angleDeg() - 90);
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
    public int getDamage() {
        return damage;
    }

    @Override
    public void reset() {
        time = 0;
        animationTime = 0;
    }
}