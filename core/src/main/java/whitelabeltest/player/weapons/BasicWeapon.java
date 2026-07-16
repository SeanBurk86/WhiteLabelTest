package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class BasicWeapon extends BaseWeapon {
    private static final float STREAM_BULLET_SPACING = 0.2f;
    private static final float OUTER_SPREAD_ANGLE = 15f;
    private static final float INNER_SPREAD_ANGLE = 6f;

    private WeaponDefinition def;

    public void init(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float speed) {
        this.def = def;

        this.animation = AnimationCache.get(texture, def.columns > 0 ? def.columns : def.frameCount, def.rows, def.frameCount, def.frameDuration, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        int frameWidth = frames[0].getRegionWidth();
        int frameHeight = frames[0].getRegionHeight();
        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.velocity.set(dir).scl(speed);
        this.damage = def.getDamage(level);
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.chainWindow = def.chainWindow;
        this.animationTime = 0;
        this.path = null;

        sprite.setRotation(velocity.angleDeg() - 90);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        float baseSpeed = def.getSpeed(level);
        if (level == 1) {
            spawnSingle(activeWeapons, texture, x, y, new Vector2(0, 1), baseSpeed);
        } else if (level == 2) {
            spawnStream(activeWeapons, texture, x, y, 0f, 4, baseSpeed);
        } else if (level == 3) {
            spawnStream(activeWeapons, texture, x, y, 0f, 4, baseSpeed);
            spawnStream(activeWeapons, texture, x, y, -OUTER_SPREAD_ANGLE, 2, baseSpeed);
            spawnStream(activeWeapons, texture, x, y, OUTER_SPREAD_ANGLE, 2, baseSpeed);
        } else {
            spawnStream(activeWeapons, texture, x, y, 0f, 4, baseSpeed);
            spawnStream(activeWeapons, texture, x, y, -OUTER_SPREAD_ANGLE, 4, baseSpeed);
            spawnStream(activeWeapons, texture, x, y, OUTER_SPREAD_ANGLE, 4, baseSpeed);
            spawnStream(activeWeapons, texture, x, y, -INNER_SPREAD_ANGLE, 4, baseSpeed);
            spawnStream(activeWeapons, texture, x, y, INNER_SPREAD_ANGLE, 4, baseSpeed);
        }
    }

    /** Spawns bulletCount bullets side-by-side (evenly spaced perpendicular to their travel
     *  direction), all fired in the same direction: straight up, rotated by angleOffsetDeg. */
    private void spawnStream(Array<Weapon> activeWeapons, Texture texture, float x, float y, float angleOffsetDeg, int bulletCount, float speed) {
        Vector2 dir = new Vector2(0, 1).rotateDeg(angleOffsetDeg);
        float perpX = -dir.y, perpY = dir.x;
        for (int i = 0; i < bulletCount; i++) {
            float offset = (i - (bulletCount - 1) / 2f) * STREAM_BULLET_SPACING;
            spawnSingle(activeWeapons, texture, x + perpX * offset, y + perpY * offset, new Vector2(dir), speed);
        }
    }

    private void spawnSingle(Array<Weapon> activeWeapons, Texture texture, float x, float y, Vector2 dir, float speed) {
        BasicWeapon w = ObjectPools.weaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, x, y, dir.nor(), speed);
        activeWeapons.add(w);
    }

    @Override
    public float getFireRate() {
        return def.getFireRate(level);
    }

    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playBasicWeaponSound(level);
    }
}
