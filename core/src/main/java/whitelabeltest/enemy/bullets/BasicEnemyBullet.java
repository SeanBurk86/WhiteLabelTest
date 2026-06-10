package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

public class BasicEnemyBullet implements EnemyBullet {
    private Sprite sprite;
    private Rectangle rectangle;
    private final float speed = -5f;
    private final int damage = 1;

    public BasicEnemyBullet() {}

    public BasicEnemyBullet(Texture texture, float x, float y) {
        init(texture, x, y);
    }

    public void init(Texture texture, float x, float y) {
        if (sprite == null) {
            sprite = new Sprite(texture);
        } else {
            sprite.setTexture(texture);
        }
        sprite.setSize(0.1f, 0.3f);
        sprite.setCenterX(x);
        sprite.setY(y);
        sprite.setColor(1, 0, 0, 1); // Red tint for enemy bullets
        if (rectangle == null) {
            rectangle = new Rectangle(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        } else {
            rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
        }
    }

    @Override
    public void update(float delta) {
        sprite.translateY(speed * delta);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void draw(SpriteBatch batch) {
        sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return sprite.getY() < -1f;
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
    }
}
