package whitelabeltest.player.powerups;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.player.Player;

public class WeaponPowerup implements Powerup {
    private static final int FRAME_COLUMNS = 4;
    private static final int FRAME_ROWS = 3;
    private static final int FRAME_COUNT = FRAME_COLUMNS * FRAME_ROWS;
    private static final float FRAME_DURATION = 0.08f;

    private Sprite sprite;
    private Animation<TextureRegion> animation;
    private float animationTime;
    private final Rectangle rectangle;
    private final Vector2 velocity = new Vector2();
    private float worldWidth, worldHeight;

    // How many levels this adds to both equipped weapons at once on pickup (see
    // Player.levelUpEquippedWeapons()) - same for a normal drop and a death-restore drop (see
    // initAsRestore()).
    private int amount;

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

        this.rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());

        if (velocity.isZero()) {
            velocity.set(3f, 0).setAngleDeg(-80f);
        }
    }

    /** A normal weapon-level pickup - adds `amount` levels (1-3, see GameController's tier
     *  textures) to both currently equipped weapons on pickup (see Player.levelUpEquippedWeapons()). */
    public void initWithAmount(Texture texture, int amount, float x, float y, float worldWidth, float worldHeight) {
        this.amount = amount;
        init(texture, x, y, worldWidth, worldHeight);
    }

    /** The pickup GameController.applyPlayerHit() spawns at the death spot: adds back the levels
     *  death's floor-to-1 just took away (see Player.resetWeaponsOnDeath()) - an ordinary additive
     *  level-up like initWithAmount(), so it stacks correctly with any other pickup collected
     *  before or after it instead of clobbering whichever arrived second with a flat "restore to
     *  X" that ignored the other's gain. */
    public void initAsRestore(Texture texture, int amount, float x, float y, float worldWidth, float worldHeight) {
        this.amount = amount;
        init(texture, x, y, worldWidth, worldHeight);
    }

    public int getAmount() { return amount; }

    @Override
    public void update(float delta) {
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

    // No longer time-limited - a dropped weapon powerup stays on screen (bouncing off the world
    // edges, see update()) until collected. Bounds check kept only as a pooling safety net.
    @Override
    public boolean isOffScreen() {
        return sprite.getY() < -sprite.getHeight() || sprite.getY() > worldHeight + sprite.getHeight()
               || sprite.getX() + sprite.getWidth() < 0f || sprite.getX() > worldWidth;
    }

    @Override
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public void apply(Player player) {
        player.levelUpEquippedWeapons(amount);
    }

    @Override
    public void reset() {
        if (sprite != null) sprite.setRotation(0);
        animationTime = 0;
        velocity.setZero();
        amount = 0;
    }
}
