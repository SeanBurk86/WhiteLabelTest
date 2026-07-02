package whitelabeltest.player.powerups;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.player.Player;

public class WeaponPowerup implements Powerup {
    private Sprite sprite;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private float worldWidth, worldHeight;
    private String weaponId; // Changed from Class to String to match the new ID-based system

    private float lifeTime = 0;
    private final float maxLifeTime = 8.0f;

    public WeaponPowerup() {
        this.rectangle = new Rectangle();
    }

    @Override
    public void init(Texture texture, float x, float y, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        if (sprite == null) {
            sprite = new Sprite(texture);
        } else {
            sprite.setTexture(texture);
            sprite.setRegion(0, 0, texture.getWidth(), texture.getHeight());
        }
        sprite.setSize(0.6f, 0.6f);
        sprite.setOriginCenter();
        sprite.setPosition(x, y);
        sprite.setColor(1, 1, 1, 1);
        sprite.setRotation(0);

        this.lifeTime = 0;
        this.rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());

        if (velocity.isZero()) {
            velocity.set(4f, 0).setAngleDeg(-80f);
        }
    }

    public void initWithType(Texture texture, String weaponId, float x, float y, float worldWidth, float worldHeight) {
        this.weaponId = weaponId;
        init(texture, x, y, worldWidth, worldHeight);
    }

    @Override
    public void update(float delta) {
        lifeTime += delta;
        sprite.translate(velocity.x * delta, velocity.y * delta);

        if (sprite.getX() < 0) {
            velocity.x = Math.abs(velocity.x);
            sprite.setX(0);
        } else if (sprite.getX() > worldWidth - sprite.getWidth()) {
            velocity.x = -Math.abs(velocity.x);
            sprite.setX(worldWidth - sprite.getWidth());
        }

        if (sprite.getY() < 0) {
            velocity.y = Math.abs(velocity.y);
            sprite.setY(0);
        } else if (sprite.getY() > worldHeight - sprite.getHeight()) {
            velocity.y = -Math.abs(velocity.y);
            sprite.setY(worldHeight - sprite.getHeight());
        }

        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null) sprite.draw(batch);
    }

    @Override
    public boolean isOffScreen() {
        return lifeTime >= maxLifeTime || sprite.getY() < -sprite.getHeight() || sprite.getY() > worldHeight + sprite.getHeight()
               || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > worldWidth;
    }

    @Override
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public void apply(Player player) {
        player.levelUpWeapon(weaponId);
    }

    @Override
    public void reset() {
        if (sprite != null) sprite.setRotation(0);
        lifeTime = 0;
        velocity.setZero();
        weaponId = null;
    }
}
