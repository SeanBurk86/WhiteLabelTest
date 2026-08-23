package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.player.Player;

/** A bullet the player's reflect shield bounced back at the enemies. Never spawned through the
 *  normal fire-button path (spawn()/getFireRate() are unused) - CollisionManager builds one
 *  directly, at the point of reflection, whenever an enemy bullet touches an active shield,
 *  copying that bullet's own sprite and homing on whichever enemy fired it. */
public class ReflectedBolt extends BaseWeapon {
    private static final float SPEED = 10f;

    /** @param sourceSprite the reflected bullet's own current sprite, copied (not shared) so this
     *  bolt looks exactly like what it bounced back
     *  @param targetEnemy the enemy that fired the reflected bullet, if known and still active -
     *  the bolt is aimed at its current position; otherwise it just flies straight up */
    public void init(Sprite sourceSprite, float x, float y, int damage, Enemy targetEnemy) {
        if (sprite == null) sprite = new Sprite(sourceSprite);
        else {
            sprite.setRegion(sourceSprite);
            sprite.setSize(sourceSprite.getWidth(), sourceSprite.getHeight());
            sprite.setColor(sourceSprite.getColor());
        }
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setCenterY(y);

        Vector2 dir = new Vector2(0, 1);
        if (targetEnemy != null && targetEnemy.isActive()) {
            Rectangle target = targetEnemy.getRectangle();
            dir.set(target.x + target.width / 2f - x, target.y + target.height / 2f - y);
            if (dir.len2() < 0.0001f) dir.set(0, 1);
        }

        this.velocity.set(dir.nor()).scl(SPEED);
        sprite.setRotation(velocity.angleDeg() - 90);

        this.damage = Math.max(damage, 1);
        this.animation = null;
        this.path = null;
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {}

    @Override
    public float getFireRate() { return Float.MAX_VALUE; }

    @Override
    public void playFireSound(AudioManager audio, int level) {}
}
