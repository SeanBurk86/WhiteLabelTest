package whitelabeltest.player.weapons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

public class OrbitWeapon extends BaseWeapon {
    private WeaponDefinition def;
    private Player player;
    private float angle;

    public void init(WeaponDefinition def, Texture texture, Player player, float initialAngle) {
        this.def = def;
        this.player = player;
        this.angle = initialAngle;

        int frameHeight = texture.getHeight();
        int frameWidth = texture.getWidth() / def.frameCount;

        animation = AnimationCache.get(texture, def.frameCount, 0.08f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setColor(1, 1, 1, 1);

        this.damage = def.baseDamage + (level - 1) * def.damagePerLevel;
        this.chainWindow = def.chainWindow;
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.animationTime = 0;
    }

    @Override
    public void update(float delta) {
        if (animation != null) {
            animationTime += delta;
            sprite.setRegion(animation.getKeyFrame(animationTime));
        }

        if (player == null || sprite == null) return;

        angle += def.rotationSpeed * delta;
        float x = player.getCenterX() + (float) Math.cos(angle) * def.radius;
        float y = player.getCenterY() + (float) Math.sin(angle) * def.radius;

        sprite.setCenterX(x);
        sprite.setCenterY(y);
        sprite.rotate(def.rotationSpeed * 50 * delta);
        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
    }

    @Override
    public boolean isOffScreen(float worldHeight) {
        boolean isShooting = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        Controller controller = Controllers.getCurrent();
        if (controller != null) {
            isShooting |= controller.getButton(controller.getMapping().buttonA);
            isShooting |= controller.getButton(controller.getMapping().buttonR1);
        }
        if (!isShooting) return true;
        return player != null && player.getWeaponPrototype() != null && !(player.getWeaponPrototype() instanceof OrbitWeapon);
    }

    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
        int currentOrbitWeapons = 0;
        for (Weapon w : activeWeapons) {
            if (w instanceof OrbitWeapon) currentOrbitWeapons++;
        }
        if (currentOrbitWeapons == this.level) return;

        for (int i = activeWeapons.size - 1; i >= 0; i--) {
            if (activeWeapons.get(i) instanceof OrbitWeapon) {
                ObjectPools.orbitWeaponPool.free((OrbitWeapon) activeWeapons.removeIndex(i));
            }
        }

        int numShields = this.level;
        float step = (float) (2 * Math.PI / numShields);
        for (int i = 0; i < numShields; i++) {
            OrbitWeapon w = ObjectPools.orbitWeaponPool.obtain();
            w.setLevel(this.level);
            w.init(def, texture, player, i * step);
            activeWeapons.add(w);
        }
    }

    @Override
    public float getFireRate() { return def.baseFireRate; }
    @Override
    public void playFireSound(AudioManager audio, int level) {
        audio.playOrbitWeaponSound(level);
    }

    @Override
    public boolean shouldDestroyOnCollision() { return false; }

    @Override
    public void reset() {
        super.reset();
        angle = 0;
    }
}
