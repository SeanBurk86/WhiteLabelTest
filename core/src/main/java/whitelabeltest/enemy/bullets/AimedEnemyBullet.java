package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public class AimedEnemyBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private final float speed = 5f;
    private final int damage = 1;

    private Animation<TextureRegion> animation;
    private float animationTime = 0;

    public AimedEnemyBullet() {
        this.rectangle = new Rectangle();
    }

    public AimedEnemyBullet(Texture texture, float x, float y, float targetX, float targetY) {
        this();
        init(texture, x, y, targetX, targetY);
    }

    public void init(Texture texture, float x, float y, float targetX, float targetY) {
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
        float baseWidth = 0.25f;
        sprite.setSize(baseWidth, baseWidth * aspectRatio);
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setCenterY(y);
        sprite.setColor(1, 1, 1, 1); // Use original asset colors

        velocity.set(targetX - x, targetY - y).nor().scl(speed);
        sprite.setRotation(velocity.angleDeg() - 90);

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        this.animationTime = 0;
    }

    @Override
    public void update(float delta) {
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        sprite.translate(velocity.x * delta, velocity.y * delta);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return sprite.getY() < -1f || sprite.getY() > 13f || sprite.getX() < -1f || sprite.getX() > 10f;
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
        velocity.setZero();
        sprite.setRotation(0);
        animationTime = 0;
    }
}
