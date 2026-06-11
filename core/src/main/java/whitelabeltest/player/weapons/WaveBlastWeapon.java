package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.player.Player;

public class WaveBlastWeapon extends BaseWeapon {
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
        this.animationTime = 0;
        this.path = null;

        sprite.setRotation(velocity.angleDeg() - 90);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        spawnSingle(activeWeapons, texture, x, y, new Vector2(0, 1), def.speed);
        if (level >= 2) {
            spawnSingle(activeWeapons, texture, x, y, new Vector2(-0.559f, 0.829f), def.speed);
            spawnSingle(activeWeapons, texture, x, y, new Vector2(0.559f, 0.829f), def.speed);
        }
        if (level >= 3) {
            trySpawnHoming(activeWeapons, x, y, enemies, assets);
        }
        if (level >= 4) {
            trySpawnHoming(activeWeapons, x, y, enemies, assets);
            trySpawnHoming(activeWeapons, x, y, enemies, assets);
        }
    }

    private void spawnSingle(Array<Weapon> activeWeapons, Texture texture, float x, float y, Vector2 dir, float speed) {
        WaveBlastWeapon w = ObjectPools.fastWeaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, x, y, dir.nor(), speed);
        activeWeapons.add(w);
    }

    private void trySpawnHoming(Array<Weapon> activeWeapons, float x, float y, Array<Enemy> enemies, AssetManager assets) {
        int homingCount = 0;
        for (Weapon w : activeWeapons) {
            if (w instanceof HomingWeapon) homingCount++;
        }
        if (homingCount < 6) {
            HomingWeapon hw = ObjectPools.homingWeaponPool.obtain();
            hw.setLevel(this.level);
            WeaponDefinition hDef = assets.getWeaponDefinition("HomingWeapon");
            // Use assets.getTexture with the path from definition instead of hardcoded field
            hw.init(hDef, assets.getTexture(hDef.texture), x, y, findNearestEnemy(enemies, x, y));
            activeWeapons.add(hw);
        }
    }

    private Enemy findNearestEnemy(Array<Enemy> enemies, float x, float y) {
        Enemy nearest = null;
        float minDist = Float.MAX_VALUE;
        for (Enemy enemy : enemies) {
            float dist = Vector2.dst(x, y, enemy.getRectangle().x, enemy.getRectangle().y);
            if (dist < minDist) {
                minDist = dist;
                nearest = enemy;
            }
        }
        return nearest;
    }

    @Override
    public float getFireRate() { return def.baseFireRate; }
    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playWaveBlastWeaponSound(level);
    }
}
