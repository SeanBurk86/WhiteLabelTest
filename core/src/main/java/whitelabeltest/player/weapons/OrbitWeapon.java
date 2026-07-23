package whitelabeltest.player.weapons;

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

/** Two very different things share this class:
 *  - the "prototype" instance held in Player.weaponSlots, which never itself orbits or draws -
 *    it just tracks the reflect-shield's active/cooldown state and, via maintainRing(), keeps
 *    the ring of orbiting bullets in sync with the weapon's level; and
 *  - the ring member instances (pooled, added to the shared bullets array) that actually orbit
 *    and damage enemies on contact, one per maintainRing() call per level.
 *  Ring members never touch the shield fields (only the prototype's tryActivateShield() does,
 *  since that's the instance Player.getCurrentWeapon() returns), so the split is safe despite
 *  being the same class. The fire button just controls whether the ring exists (see
 *  Player.maintainOrbitRing) - the reflect shield is a separate ability, raised by the Hyper
 *  Attack input via tryActivateShield(). */
public class OrbitWeapon extends BaseWeapon {
    public static final float SHIELD_DURATION = 2f;
    private static final float SHIELD_COOLDOWN = 4f;
    private static final float SHIELD_RADIUS = 1.1f;

    private WeaponDefinition def;
    private Player player;
    private float angle;

    private boolean shieldActive;
    private float shieldTimer;
    private float shieldCooldownTimer;

    public void init(WeaponDefinition def, Texture texture, Player player, float initialAngle) {
        this.def = def;
        this.player = player;
        this.angle = initialAngle;

        animation = AnimationCache.get(texture, def.columns > 0 ? def.columns : def.frameCount, def.rows, def.frameCount, def.frameDuration, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        int frameWidth = frames[0].getRegionWidth();
        int frameHeight = frames[0].getRegionHeight();
        sprite.setSize(def.size, def.size * ((float) frameHeight / frameWidth));
        sprite.setOriginCenter();
        sprite.setColor(1, 1, 1, 1);

        this.damage = def.getDamage(level);
        this.chainWindow = def.chainWindow;
        this.shootSpeedMultiplier = def.shootSpeedMultiplier;
        this.hitAnimation = def.hitAnimation;
        this.hitEffectSize = def.hitSize;
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

    // Ring members orbit for as long as they exist; maintainRing()/clearRing() (called every
    // frame from Player, not gated on firing) are what add/remove them, not this.
    @Override
    public boolean isOffScreen(float worldHeight) { return false; }

    /** Creates/replaces the ring of orbiting bullets so its size always matches this weapon's
     *  level - called every frame while OrbitWeapon is the actively selected slot, regardless of
     *  firing, so the ring is up whenever this weapon is the one selected. */
    public void maintainRing(Array<Weapon> bullets, Texture texture, Player player) {
        int currentOrbitWeapons = 0;
        for (Weapon w : bullets) {
            if (w instanceof OrbitWeapon) currentOrbitWeapons++;
        }
        if (currentOrbitWeapons == this.level) return;

        clearRing(bullets);
        if (this.level <= 0) return;

        int numShields = this.level;
        float step = (float) (2 * Math.PI / numShields);
        for (int i = 0; i < numShields; i++) {
            OrbitWeapon w = ObjectPools.orbitWeaponPool.obtain();
            w.setLevel(this.level);
            w.init(def, texture, player, i * step);
            bullets.add(w);
        }
    }

    /** Removes any ring members - used whenever OrbitWeapon isn't the actively selected slot (or
     *  its level drops to 0), so orbiting bullets never outlive actually being selected. */
    public void clearRing(Array<Weapon> bullets) {
        for (int i = bullets.size - 1; i >= 0; i--) {
            if (bullets.get(i) instanceof OrbitWeapon) {
                ObjectPools.orbitWeaponPool.free((OrbitWeapon) bullets.removeIndex(i));
            }
        }
    }

    /** The fire button only controls the ring's presence (see Player.maintainOrbitRing) - firing
     *  itself spawns nothing. */
    @Override
    public void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets) {
    }

    @Override
    public float getFireRate() { return def.getFireRate(level); }

    @Override
    public void playFireSound(AudioManager audio, int level) {
    }

    /** Raises the reflect shield if the Hyper Attack input triggers it - subject to its own
     *  active/cooldown timers, independent of the normal fire-rate cooldown. Returns whether it
     *  actually activated, so the caller knows whether to play the activation sound. */
    public boolean tryActivateShield() {
        if (shieldActive || shieldCooldownTimer > 0f) return false;
        shieldActive = true;
        shieldTimer = 0f;
        return true;
    }

    @Override
    public boolean shouldDestroyOnCollision() { return false; }

    // Advanced every frame while equipped in either slot (see Player.advanceWeaponTimers), same
    // as the fire-rate cooldown every other weapon uses - so the shield's duration/cooldown run
    // on real elapsed time regardless of which slot is active.
    @Override
    public void addShootTimer(float delta) {
        super.addShootTimer(delta);

        if (shieldActive) {
            shieldTimer += delta;
            if (shieldTimer >= SHIELD_DURATION) {
                shieldActive = false;
                shieldTimer = 0f;
                shieldCooldownTimer = SHIELD_COOLDOWN;
            }
        } else if (shieldCooldownTimer > 0f) {
            shieldCooldownTimer -= delta;
        }
    }

    public boolean isShieldActive() { return shieldActive; }
    public float getShieldTimer() { return shieldTimer; }
    public float getShieldRadius() { return SHIELD_RADIUS; }
    public float getShieldCooldownTimer() { return Math.max(shieldCooldownTimer, 0f); }
    public float getShieldCooldownFraction() { return Math.max(shieldCooldownTimer, 0f) / SHIELD_COOLDOWN; }

    /** Called on a full game reset, not on pool reuse (see reset()) - the prototype instance is
     *  never pooled, so its shield state would otherwise survive a restart. */
    public void resetShield() {
        shieldActive = false;
        shieldTimer = 0f;
        shieldCooldownTimer = 0f;
    }

    @Override
    public void reset() {
        super.reset();
        angle = 0;
    }
}
