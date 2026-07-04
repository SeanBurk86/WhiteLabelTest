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

public class HomingWeapon extends BaseWeapon {
    private WeaponDefinition def;
    private Enemy target;
    private final float rotationSpeed = 300f;
    private float lifeTime = 0;
    private final float maxLifeTime = 2.0f;
    private boolean spawnOnRight = true;

    public void init(WeaponDefinition def, Texture texture, float x, float y, Enemy initialTarget) {
        this.def = def;
        int frameHeight = texture.getHeight();
        int frameWidth = texture.getWidth() / def.frameCount;

        this.animation = AnimationCache.get(texture, def.frameCount, 0.1f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setCenterX(x);
        sprite.setY(y);

        this.damage = def.baseDamage + (level - 1) * def.damagePerLevel;
        this.chainWindow = def.chainWindow;
        this.target = initialTarget;
        this.velocity.set(0, def.speed);
        this.animationTime = 0;
        this.lifeTime = 0;

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public void update(float delta) {
        if (animation != null) {
            animationTime += delta;
            sprite.setRegion(animation.getKeyFrame(animationTime));
        }

        lifeTime += delta;

        if (target != null && !target.isOffScreen()) {
            Vector2 targetPos = new Vector2(target.getRectangle().x + target.getRectangle().width / 2,
                                            target.getRectangle().y + target.getRectangle().height / 2);
            Vector2 currentPos = new Vector2(sprite.getX() + sprite.getWidth() / 2, sprite.getY() + sprite.getHeight() / 2);
            Vector2 desiredDirection = targetPos.sub(currentPos).nor();

            float currentAngle = velocity.angleDeg();
            float targetAngle = desiredDirection.angleDeg();

            float angleDiff = targetAngle - currentAngle;
            if (angleDiff > 180) angleDiff -= 360;
            if (angleDiff < -180) angleDiff += 360;

            float changeAngle = rotationSpeed * delta;
            if (Math.abs(angleDiff) > changeAngle) currentAngle += Math.signum(angleDiff) * changeAngle;
            else currentAngle = targetAngle;

            velocity.setAngleDeg(currentAngle).setLength(def.speed);
            sprite.setRotation(currentAngle - 90);
        }

        sprite.translate(velocity.x * delta, velocity.y * delta);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    @Override
    public void updateWithEnemies(float delta, Array<Enemy> enemies) {
        if (target == null || target.isOffScreen()) {
            target = findNearestEnemy(enemies, sprite.getX(), sprite.getY());
        }
        update(delta);
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
    public boolean isOffScreen(float worldHeight) {
        return lifeTime >= maxLifeTime || super.isOffScreen(worldHeight);
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        HomingWeapon w = ObjectPools.homingWeaponPool.obtain();
        w.setLevel(this.level);
        float offsetX = spawnOnRight ? 0.6f : -0.6f;
        float spawnX = player.getCenterX() + offsetX;
        float spawnY = player.getCenterY() - 1.0f;
        w.init(def, texture, spawnX, spawnY, findNearestEnemy(enemies, spawnX, spawnY));
        activeWeapons.add(w);
        spawnOnRight = !spawnOnRight;
    }

    @Override
    public float getFireRate() { return def.baseFireRate; }
    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playHomingWeaponSound(level);
    }

    @Override
    public void reset() {
        super.reset();
        lifeTime = 0;
        target = null;
    }
}
