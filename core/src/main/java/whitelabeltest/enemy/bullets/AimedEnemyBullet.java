package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.enemy.Enemy;

public class AimedEnemyBullet implements EnemyBullet {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private int damage = 1;
    private Enemy sourceEnemy;

    private Animation<TextureRegion> animation;
    private float animationTime = 0;

    public AimedEnemyBullet() {
        this.rectangle = new Rectangle();
    }

    public AimedEnemyBullet(Animation<TextureRegion> animation, float x, float y, float targetX, float targetY, float size, float speed) {
        this();
        init(animation, x, y, targetX, targetY, size, speed, 1, null);
    }

    public void init(Animation<TextureRegion> animation, float x, float y, float targetX, float targetY, float size, float speed, int damage, Enemy source) {
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
        return sprite.getY() < -1f || sprite.getY() > 13f || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > 9f;
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
    public Enemy getSourceEnemy() { return sourceEnemy; }

    @Override
    public Sprite getSprite() { return sprite; }

    @Override
    public void reset() {
        velocity.setZero();
        sprite.setRotation(0);
        animationTime = 0;
        sourceEnemy = null;
    }
}
