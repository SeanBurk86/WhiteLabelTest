package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.player.Player;

/** A homing projectile launched by WaveBlastWeapon's Hyper Attack (see
 *  WaveBlastWeapon.hyperAttack()): re-aims itself toward the nearest active enemy every frame,
 *  turning at a limited rate rather than snapping instantly, and just flies straight once
 *  nothing's left to home in on. Never spawned through the normal fire-button path. */
public class HomingBolt extends BaseWeapon {
    private static final float TURN_RATE_DEG_PER_SEC = 480f;

    public void init(Animation<TextureRegion> animation, float size, float x, float y, Vector2 dir, float speed, int damage) {
        this.animation = animation;
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        int frameWidth = frames[0].getRegionWidth();
        int frameHeight = frames[0].getRegionHeight();
        sprite.setSize(size, size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.velocity.set(dir).nor().scl(speed);
        this.damage = damage;
        this.animationTime = 0;
        this.path = null;

        sprite.setRotation(velocity.angleDeg() - 90);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void updateWithEnemies(float delta, Array<Enemy> enemies) {
        steerToward(nearestActiveEnemy(enemies), delta);
        update(delta);
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {}

    @Override
    public float getFireRate() { return Float.MAX_VALUE; }

    @Override
    public void playFireSound(AudioManager audio, int level) {}

    /** Turns velocity toward target by at most TURN_RATE_DEG_PER_SEC * delta degrees this frame,
     *  keeping its speed unchanged - a no-op if there's nothing left to home in on. */
    private void steerToward(Enemy target, float delta) {
        if (target == null) return;

        Rectangle targetRect = target.getRectangle();
        float dx = targetRect.x + targetRect.width / 2f - (rectangle.x + rectangle.width / 2f);
        float dy = targetRect.y + targetRect.height / 2f - (rectangle.y + rectangle.height / 2f);
        if (dx == 0f && dy == 0f) return;

        float desiredAngle = new Vector2(dx, dy).angleDeg();
        float currentAngle = velocity.angleDeg();
        float maxTurn = TURN_RATE_DEG_PER_SEC * delta;
        float turn = MathUtils.clamp(shortestAngleDelta(currentAngle, desiredAngle), -maxTurn, maxTurn);

        velocity.setAngleDeg(currentAngle + turn);
        sprite.setRotation(velocity.angleDeg() - 90);
    }

    /** Signed shortest angular distance from `from` to `to`, in (-180, 180]. */
    private static float shortestAngleDelta(float from, float to) {
        float diff = (to - from) % 360f;
        if (diff < -180f) diff += 360f;
        if (diff > 180f) diff -= 360f;
        return diff;
    }

    private Enemy nearestActiveEnemy(Array<Enemy> enemies) {
        float centerX = rectangle.x + rectangle.width / 2f;
        float centerY = rectangle.y + rectangle.height / 2f;

        Enemy nearest = null;
        float nearestDist2 = Float.MAX_VALUE;
        for (Enemy enemy : enemies) {
            if (!enemy.isActive()) continue;
            Rectangle r = enemy.getRectangle();
            float dx = r.x + r.width / 2f - centerX;
            float dy = r.y + r.height / 2f - centerY;
            float dist2 = dx * dx + dy * dy;
            if (dist2 < nearestDist2) {
                nearestDist2 = dist2;
                nearest = enemy;
            }
        }
        return nearest;
    }
}
