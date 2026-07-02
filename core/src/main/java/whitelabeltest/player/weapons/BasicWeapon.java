package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class BasicWeapon extends BaseWeapon {
    private WeaponDefinition def;

    public void init(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float speed) {
        this.def = def;
        int frameWidth = texture.getWidth() / def.frameCount;
        int frameHeight = texture.getHeight();
        TextureRegion[][] tmp = TextureRegion.split(texture, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[def.frameCount];
        System.arraycopy(tmp[0], 0, frames, 0, def.frameCount);

        this.animation = new Animation<>(0.05f, frames);
        this.animation.setPlayMode(Animation.PlayMode.LOOP);

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.velocity.set(dir).scl(speed);
        this.damage = def.baseDamage + (level - 1) * def.damagePerLevel;
        this.chainWindow = def.chainWindow;
        this.animationTime = 0;
        this.path = null;

        sprite.setRotation(velocity.angleDeg() - 90);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        float baseSpeed = def.speed;
        if (level == 1) {
            spawnSingle(activeWeapons, texture, x, y, new Vector2(0, 1), baseSpeed);
        } else if (level == 2) {
            for (int i = 0; i < 4; i++) {
                spawnSingle(activeWeapons, texture, x - 0.3f + (i * 0.2f), y, new Vector2(0, 1), baseSpeed);
            }
        } else if (level == 3) {
            for (int i = 0; i < 4; i++) spawnSingle(activeWeapons, texture, x - 0.3f + (i * 0.2f), y, new Vector2(0, 1), baseSpeed);
            spawnSingle(activeWeapons, texture, x, y, new Vector2(-1, 1), baseSpeed * 0.8f);
            spawnSingle(activeWeapons, texture, x, y, new Vector2(1, 1), baseSpeed * 0.8f);
            spawnSingle(activeWeapons, texture, x, y, new Vector2(-1, -1), baseSpeed * 0.8f);
            spawnSingle(activeWeapons, texture, x, y, new Vector2(1, -1), baseSpeed * 0.8f);
        } else {
            float[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
            for (float[] d : dirs) {
                for (int i = 0; i < 4; i++) {
                    float offsetX = d[1] * (-0.3f + i * 0.2f);
                    float offsetY = d[0] * (-0.3f + i * 0.2f);
                    spawnSingle(activeWeapons, texture, x + offsetX, y + offsetY, new Vector2(d[0], d[1]), baseSpeed);
                }
            }
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
        return def.baseFireRate - (level - 1) * def.fireRatePerLevel;
    }

    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playBasicWeaponSound(level);
    }
}
