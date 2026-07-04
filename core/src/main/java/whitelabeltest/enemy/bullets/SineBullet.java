package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.gamemanagers.AnimationCache;

public class SineBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final int damage = 1;

    private float spawnX, spawnY;
    private float amplitude;
    private float frequency;
    private float phase;
    private float speed;
    private float time;

    private Animation<TextureRegion> animation;
    private float animationTime;

    public SineBullet() {
        this.rectangle = new Rectangle();
    }

    public void init(Texture texture, float x, float y, float amplitude, float frequency, float phase, float speed) {
        int frameHeight = texture.getHeight();
        int frameWidth = texture.getWidth() / 3;

        animation = AnimationCache.get(texture, 3, 0.1f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        float aspectRatio = (float) frameHeight / frameWidth;
        float baseWidth = 0.2f;
        sprite.setSize(baseWidth, baseWidth * aspectRatio);
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setCenterY(y);

        this.spawnX = x;
        this.spawnY = y;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.phase = phase;
        this.speed = speed;
        this.time = 0;
        this.animationTime = 0;

        rectangle.set(sprite.getX() + .036f, sprite.getY() + .036f, sprite.getWidth() * .6f, sprite.getHeight() * .6f);
    }

    @Override
    public void update(float delta) {
        time += delta;
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        float newX = spawnX + amplitude * MathUtils.sin(frequency * time + phase);
        float newY = spawnY - speed * time;
        sprite.setCenter(newX, newY);

        // Rotate sprite to face its direction of travel
        float vx = amplitude * frequency * MathUtils.cos(frequency * time + phase);
        float vy = -speed;
        sprite.setRotation(new Vector2(vx, vy).angleDeg() - 90);

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
    public int getDamage() {
        return damage;
    }

    @Override
    public void reset() {
        time = 0;
        animationTime = 0;
    }
}
