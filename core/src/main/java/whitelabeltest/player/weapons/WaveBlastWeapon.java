package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.player.Player;

public class WaveBlastWeapon extends BaseWeapon {
    private static final float SPLINTER_SIZE_SCALE = 0.5f;
    private static final float SPLINTER_DAMAGE_SCALE = 0.5f;
    private static final float SPLINTER_ANGLE = 45f;

    private WeaponDefinition def;
    private Texture texture;
    private float size;
    private int splitDepthRemaining;

    public void init(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float speed) {
        init(def, texture, x, y, dir, speed, def.size, def.getDamage(level), initialSplitDepth(level));
    }

    private void init(WeaponDefinition def, Texture texture, float x, float y, Vector2 dir, float speed, float size, int damage, int splitDepthRemaining) {
        this.def = def;
        this.texture = texture;
        this.size = size;
        this.splitDepthRemaining = splitDepthRemaining;

        int frameHeight = texture.getHeight();
        int frameWidth = texture.getWidth() / def.frameCount;

        this.animation = AnimationCache.get(texture, def.frameCount, 0.05f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        sprite.setSize(size, size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.velocity.set(dir).scl(speed);
        this.damage = damage;
        this.chainWindow = def.chainWindow;
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.animationTime = 0;
        this.path = null;

        sprite.setRotation(velocity.angleDeg() - 90);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    private static int initialSplitDepth(int level) {
        if (level >= 3) return 2;
        return 1;
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        float speed = def.getSpeed(level);
        spawnSingle(activeWeapons, texture, x, y, new Vector2(0, 1), speed);
    }

    private void spawnSingle(Array<Weapon> activeWeapons, Texture texture, float x, float y, Vector2 dir, float speed) {
        WaveBlastWeapon w = ObjectPools.fastWeaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, x, y, dir.nor(), speed);
        activeWeapons.add(w);
    }

    @Override
    public void onHit(Enemy enemy, Array<Weapon> activeWeapons, AssetManager assets) {
        if (splitDepthRemaining <= 0) return;

        float hitX = enemy.getRectangle().x + enemy.getRectangle().width / 2f;
        float hitY = enemy.getRectangle().y + enemy.getRectangle().height / 2f;
        float enemyHalfWidth = enemy.getRectangle().width / 2f;
        float enemyHalfHeight = enemy.getRectangle().height / 2f;
        float speed = velocity.len();
        Vector2 forward = new Vector2(velocity).nor();

        float splinterSize = size * SPLINTER_SIZE_SCALE;
        int splinterDamage = Math.max(1, Math.round(damage * SPLINTER_DAMAGE_SCALE));
        int childDepth = splitDepthRemaining - 1;

        spawnSplinterOutside(activeWeapons, hitX, hitY, enemyHalfWidth, enemyHalfHeight, new Vector2(forward).rotateDeg(SPLINTER_ANGLE), speed, splinterSize, splinterDamage, childDepth);
        spawnSplinterOutside(activeWeapons, hitX, hitY, enemyHalfWidth, enemyHalfHeight, new Vector2(forward).rotateDeg(-SPLINTER_ANGLE), speed, splinterSize, splinterDamage, childDepth);
        if (level >= 3) {
            spawnSplinterOutside(activeWeapons, hitX, hitY, enemyHalfWidth, enemyHalfHeight, forward, speed, splinterSize, splinterDamage, childDepth);
        }
    }

    private void spawnSplinterOutside(Array<Weapon> activeWeapons, float centerX, float centerY, float enemyHalfWidth, float enemyHalfHeight, Vector2 dir, float speed, float size, int damage, int splitDepthRemaining) {
        float clearance = exitDistance(dir, enemyHalfWidth, enemyHalfHeight) + size / 2f;
        spawnSplinter(activeWeapons, centerX + dir.x * clearance, centerY + dir.y * clearance, dir, speed, size, damage, splitDepthRemaining);
    }

    private static float exitDistance(Vector2 dir, float halfWidth, float halfHeight) {
        float tx = Math.abs(dir.x) > 0.0001f ? halfWidth / Math.abs(dir.x) : Float.MAX_VALUE;
        float ty = Math.abs(dir.y) > 0.0001f ? halfHeight / Math.abs(dir.y) : Float.MAX_VALUE;
        return Math.min(tx, ty);
    }

    private void spawnSplinter(Array<Weapon> activeWeapons, float x, float y, Vector2 dir, float speed, float size, int damage, int splitDepthRemaining) {
        WaveBlastWeapon w = ObjectPools.fastWeaponPool.obtain();
        w.setLevel(this.level);
        w.init(def, texture, x, y, dir.nor(), speed, size, damage, splitDepthRemaining);
        activeWeapons.add(w);
    }

    @Override
    public float getFireRate() { return def.getFireRate(level); }
    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playWaveBlastWeaponSound(level);
    }
}
