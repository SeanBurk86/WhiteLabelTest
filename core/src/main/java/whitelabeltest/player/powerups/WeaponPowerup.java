package whitelabeltest.player.powerups;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.player.Player;

public class WeaponPowerup implements Powerup {
    private static final int FRAME_COLUMNS = 4;
    private static final int FRAME_ROWS = 3;
    private static final int FRAME_COUNT = FRAME_COLUMNS * FRAME_ROWS;
    private static final float FRAME_DURATION = 0.08f;
    private static final float CYCLE_COOLDOWN = 1f;

    private Sprite sprite;
    private Animation<TextureRegion> animation;
    private float animationTime;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private float worldWidth, worldHeight;
    private String weaponId; // Changed from Class to String to match the new ID-based system

    private float lifeTime = 0;
    private final float maxLifeTime = 8.0f;
    private float cycleCooldownTimer = 0f;

    public WeaponPowerup() {
        this.rectangle = new Rectangle();
    }

    @Override
    public void init(Texture texture, float x, float y, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;

        animation = AnimationCache.get(texture, FRAME_COLUMNS, FRAME_ROWS, FRAME_COUNT, FRAME_DURATION, Animation.PlayMode.LOOP);
        animationTime = 0;

        if (sprite == null) {
            sprite = new Sprite(animation.getKeyFrame(0));
        } else {
            sprite.setRegion(animation.getKeyFrame(0));
        }
        sprite.setSize(0.6f, 0.6f);
        sprite.setOriginCenter();
        sprite.setPosition(x, y);
        sprite.setColor(1, 1, 1, 1);
        sprite.setRotation(0);

        this.lifeTime = 0;
        this.cycleCooldownTimer = CYCLE_COOLDOWN;
        this.rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());

        if (velocity.isZero()) {
            velocity.set(3f, 0).setAngleDeg(-80f);
        }
    }

    public void initWithType(Texture texture, String weaponId, float x, float y, float worldWidth, float worldHeight) {
        this.weaponId = weaponId;
        init(texture, x, y, worldWidth, worldHeight);
    }

    public String getWeaponId() {
        return weaponId;
    }

    /** Swaps this pickup to a different weapon type in place, keeping its current position/velocity/lifetime. */
    public void setType(String weaponId, Texture texture) {
        this.weaponId = weaponId;
        animation = AnimationCache.get(texture, FRAME_COLUMNS, FRAME_ROWS, FRAME_COUNT, FRAME_DURATION, Animation.PlayMode.LOOP);
        animationTime = 0;
        sprite.setRegion(animation.getKeyFrame(0));
    }

    /** Called when a bullet touches this pickup; reports true (and starts the cooldown) at most
     *  once every CYCLE_COOLDOWN seconds, regardless of how many bullets touch it in between. */
    public boolean registerHit() {
        if (cycleCooldownTimer > 0f) return false;
        cycleCooldownTimer = CYCLE_COOLDOWN;
        return true;
    }

    @Override
    public void update(float delta) {
        lifeTime += delta;
        if (cycleCooldownTimer > 0f) cycleCooldownTimer -= delta;
        animationTime += delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));
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
        animationTime = 0;
        cycleCooldownTimer = 0f;
        velocity.setZero();
        weaponId = null;
    }
}
