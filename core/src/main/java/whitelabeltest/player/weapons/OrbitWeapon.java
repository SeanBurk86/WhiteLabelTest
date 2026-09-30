package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.player.Player;

/** Used two ways: the slot instance (never drawn) owns the reflect shield and maintains the ring;
 *  the pooled ring members (one per level) orbit the ship and damage on contact. Hyper Attack
 *  raises the reflect shield. */
public class OrbitWeapon extends BaseWeapon {
    public static final float SHIELD_DURATION = 2f;
    private static final float SHIELD_COOLDOWN = 4f;
    private static final float SHIELD_RADIUS = 1.1f;
    private static final float TWO_PI = (float) (2 * Math.PI);

    private WeaponDefinition def;
    private Player player;
    private float angle;
    // Set only on ring members (see maintainRing()) - the prototype instance never orbits, so it
    // never needs this and is left null. Used by update() to crack the whip sound once per lap.
    private AudioManager audio;

    private boolean shieldActive;
    private float shieldTimer;
    private float shieldCooldownTimer;
    // Set the frame the shield's cooldown runs out - see consumeShieldReady().
    private boolean shieldJustReady;

    public void init(WeaponDefinition def, Texture texture, Player player, float initialAngle) {
        init(def, texture, player, initialAngle, null);
    }

    public void init(WeaponDefinition def, Texture texture, Player player, float initialAngle, AudioManager audio) {
        this.def = def;
        this.player = player;
        this.angle = initialAngle;
        this.audio = audio;

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

        float prevAngle = angle;
        angle += def.rotationSpeed * delta;
        // A whip crack per lap per member, i.e. `level` cracks per ring rotation.
        if (audio != null && Math.floor(angle / TWO_PI) != Math.floor(prevAngle / TWO_PI)) {
            audio.playOrbitWhip();
        }
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

    /** Keeps the ring's member count equal to the level (called every frame the ring is up). */
    public void maintainRing(Array<Weapon> bullets, Texture texture, Player player, AudioManager audio) {
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
            w.init(def, texture, player, i * step, audio);
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

    /** Raises the reflect shield if it's off cooldown (uses WaveBlast's fire sound). */
    @Override
    public void hyperAttack(Player player, Array<Weapon> activeWeapons, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        if (tryActivateShield()) {
            audio.playWaveBlastWeaponSound(level);
        }
    }

    private boolean tryActivateShield() {
        if (shieldActive || shieldCooldownTimer > 0f) return false;
        shieldActive = true;
        shieldTimer = 0f;
        return true;
    }

    @Override
    public boolean shouldDestroyOnCollision() { return false; }

    // Also ticks the shield's duration/cooldown.
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
            if (shieldCooldownTimer <= 0f) shieldJustReady = true;
        }
    }

    /** True once, the first time it's asked after the shield finishes recharging - Player plays the
     *  "shields ready" sound off it (see Player.advanceWeaponTimers()). */
    public boolean consumeShieldReady() {
        boolean ready = shieldJustReady;
        shieldJustReady = false;
        return ready;
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
        shieldJustReady = false;
    }

    @Override
    public void reset() {
        super.reset();
        angle = 0;
    }
}
