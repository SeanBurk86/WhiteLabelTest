package whitelabeltest.player;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.gamemanagers.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
import whitelabeltest.gamemanagers.InputManager;
import whitelabeltest.player.weapons.*;

public class Player {
    private final PlayerDefinition playerDef;

    private final Sprite sprite;
    private final Circle hitbox;
    private final Circle grazeHitbox;
    private final float movementSpeed = 7.5f;
    private final float worldWidth;
    private final float worldHeight;

    private final float bulletSpawnOffsetY = 0f;

    private final BasicWeapon basicWeapon;
    private final WaveBlastWeapon waveBlastWeapon;
    private final OrbitWeapon orbitWeapon;
    private final ThunderboltWeapon thunderboltWeapon;
    private final WeaponDefinition orbitWeaponDef;
    private final Animation<TextureRegion> shieldAnimation;
    private final Circle shieldHitbox = new Circle();

    private final Weapon[] weaponSlots = new Weapon[2];
    private int activeSlot;
    private int numBombs;
    private int numLives;
    private float grazePoints;

    private final Animation<TextureRegion> animation;
    private float animationTime = 0;

    private final Animation<TextureRegion> deathAnimation;
    private final float deathDrawWidth, deathDrawHeight;

    private final Animation<TextureRegion> haloAnimation;
    private final float haloDrawWidth, haloDrawHeight;
    private float haloAnimationTime = 0;

    private float invincibleFrameTime = 2f;
    private float invincibilityTimer = 0f;
    private boolean isInvincible;

    private boolean isDead;
    private float deathTimer;
    private float deathX, deathY;
    private static final float DEATH_WAIT = 2f;

    private float grazeFlashTimer;
    private static final float GRAZE_FLASH_DURATION = 0.12f;

    private static final int MAX_WEAPON_LEVEL = 4;
    private static final float BLINK_INTERVAL = 0.1f;

    public Player(AssetManager assets, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.playerDef = assets.getPlayerDefinition();

        PlayerDefinition.SpriteDef playerSprite = playerDef.player;
        animation = AnimationCache.get(assets.playerTexture, playerSprite.columns > 0 ? playerSprite.columns : playerSprite.frameCount,
            playerSprite.rows, playerSprite.frameCount, 0.04f, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();
        float aspect = (float) frames[0].getRegionHeight() / frames[0].getRegionWidth();

        sprite = new Sprite(frames[0]);
        sprite.setSize(playerSprite.size, playerSprite.size * aspect);
        sprite.setX(worldWidth / 2f - sprite.getWidth() / 2f);
        sprite.setY(0);

        PlayerDefinition.SpriteDef deathSprite = playerDef.playerDeath;
        deathAnimation = AnimationCache.get(assets.playerDeathTexture, deathSprite.columns > 0 ? deathSprite.columns : deathSprite.frameCount,
            deathSprite.rows, deathSprite.frameCount, 1f / 30f, Animation.PlayMode.NORMAL);
        deathDrawWidth = deathSprite.size;
        deathDrawHeight = deathSprite.size;

        PlayerDefinition.SpriteDef haloSprite = playerDef.playerHalo;
        haloAnimation = AnimationCache.get(assets.playerHaloTexture, haloSprite.columns > 0 ? haloSprite.columns : haloSprite.frameCount,
            haloSprite.rows, haloSprite.frameCount, 1f / 24f, Animation.PlayMode.LOOP);
        TextureRegion[] haloFrames = haloAnimation.getKeyFrames();
        float haloAspect = (float) haloFrames[0].getRegionHeight() / haloFrames[0].getRegionWidth();
        haloDrawWidth = haloSprite.size;
        haloDrawHeight = haloSprite.size * haloAspect;

        // Initialize weapons using the new dynamic AssetManager - before the hitboxes below,
        // since updateHitbox() reads orbitWeapon's shield radius.
        WeaponDefinition bDef = assets.getWeaponDefinition("BasicWeapon");
        basicWeapon = new BasicWeapon();
        basicWeapon.init(bDef, assets.getTexture(bDef.texture), 0, 0, new Vector2(0,1), bDef.getSpeed(1));

        WeaponDefinition wDef = assets.getWeaponDefinition("WaveBlastWeapon");
        waveBlastWeapon = new WaveBlastWeapon();
        waveBlastWeapon.init(wDef, assets.getTexture(wDef.texture), 0, 0, new Vector2(0,1), wDef.getSpeed(1));

        orbitWeaponDef = assets.getWeaponDefinition("OrbitWeapon");
        orbitWeapon = new OrbitWeapon();
        orbitWeapon.init(orbitWeaponDef, assets.getTexture(orbitWeaponDef.texture), this, 0f);

        PlayerDefinition.SpriteDef shieldSprite = playerDef.reflectShield;
        float shieldFrameDuration = OrbitWeapon.SHIELD_DURATION / shieldSprite.frameCount;
        shieldAnimation = AnimationCache.get(assets.playerReflectShieldTexture, shieldSprite.columns > 0 ? shieldSprite.columns : shieldSprite.frameCount,
            shieldSprite.rows, shieldSprite.frameCount, shieldFrameDuration, Animation.PlayMode.NORMAL);

        WeaponDefinition thbDef = assets.getWeaponDefinition("Thunderbolt");
        thunderboltWeapon = new ThunderboltWeapon();
        thunderboltWeapon.init(thbDef, assets.pixelTexture, assets.circleTexture, new Vector2(0, 0), new Vector2(0, 1), thbDef.size, worldHeight);

        hitbox = new Circle();
        updateHitbox();
        grazeHitbox = new Circle();
        updateGrazeHitbox();

        weaponSlots[0] = basicWeapon;
        weaponSlots[1] = null;
        activeSlot = 0;
        numBombs = 1;
        numLives = 3;
        grazePoints = 0;
        isInvincible = false;
    }

    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies) {
        if (isDead) {
            deathTimer += delta;
            if (deathTimer >= DEATH_WAIT) {
                isDead = false;
                deathTimer = 0f;
                sprite.setPosition(deathX, deathY);
                updateHitbox();
                updateGrazeHitbox();
                startIFrames();
            }
            return;
        }

        animationTime += delta;
        haloAnimationTime += delta;
        if (grazeFlashTimer > 0) grazeFlashTimer -= delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (input.isWeaponSwitchJustPressed()) {
            switchActiveSlot();
        }

        handleMovement(delta, input.getMoveDirection(), input.isShooting());
        handleShooting(delta, input.isShooting(), assets, audio, bullets, enemies);
        maintainOrbitRing(bullets, assets);
        updateHitbox();
        updateGrazeHitbox();
        resolveGrazePoints();
        resolveInvincibility(delta);
    }

    // The orbit ring is kept in sync every frame, independent of firing, but only while
    // OrbitWeapon is the actively selected slot - see OrbitWeapon's class comment for why the
    // prototype/ring-member split is safe despite sharing a class.
    private void maintainOrbitRing(Array<Weapon> bullets, AssetManager assets) {
        if (getCurrentWeapon() == orbitWeapon) {
            orbitWeapon.maintainRing(bullets, assets.getTexture(orbitWeaponDef.texture), this);
        } else {
            orbitWeapon.clearRing(bullets);
        }
    }

    private void handleMovement(float delta, Vector2 moveDirection, boolean isShooting) {
        float speed = isShooting ? movementSpeed * getCurrentWeapon().getShootSpeedMultiplier() : movementSpeed;
        if (moveDirection.x != 0 || moveDirection.y != 0) {
            sprite.translateX(moveDirection.x * speed * delta);
            sprite.translateY(moveDirection.y * speed * delta);
        }
        sprite.setX(MathUtils.clamp(sprite.getX(), 0, worldWidth - sprite.getWidth()));
        sprite.setY(MathUtils.clamp(sprite.getY(), 0, worldHeight - sprite.getHeight()));
    }

    private void handleShooting(float delta, boolean isShooting, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies) {
        advanceWeaponTimers(delta);

        Weapon currentWeapon = getCurrentWeapon();
        if (isShooting && currentWeapon.getShootTimer() > currentWeapon.getFireRate()) {
            currentWeapon.resetShootTimer();

            Texture bulletTex = resolveActiveTexture(assets);
            Vector2 spawnPoint = getBulletSpawnPoint();
            currentWeapon.spawn(bullets, bulletTex, spawnPoint.x, spawnPoint.y, this, enemies, assets);
            currentWeapon.playFireSound(audio, currentWeapon.getLevel());
        }
    }

    // Both equipped weapons' cooldowns tick every frame, whether or not their slot is active, so
    // a weapon is ready to fire based on real elapsed time since it last fired - not reset by
    // switching to it, and not fast-forwardable by rapidly toggling slots back and forth.
    private void advanceWeaponTimers(float delta) {
        if (weaponSlots[0] != null) weaponSlots[0].addShootTimer(delta);
        if (weaponSlots[1] != null && weaponSlots[1] != weaponSlots[0]) weaponSlots[1].addShootTimer(delta);
    }

    private Texture resolveActiveTexture(AssetManager assets) {
        WeaponDefinition def = assets.getWeaponDefinition(weaponId(getCurrentWeapon()));
        return (def != null && def.texture != null) ? assets.getTexture(def.texture) : assets.bulletTexture;
    }

    private Weapon getCurrentWeapon() {
        return weaponSlots[activeSlot];
    }

    private String weaponId(Weapon weapon) {
        if (weapon == basicWeapon) return "BasicWeapon";
        if (weapon == waveBlastWeapon) return "WaveBlastWeapon";
        if (weapon == orbitWeapon) return "OrbitWeapon";
        if (weapon == thunderboltWeapon) return "Thunderbolt";
        return null;
    }

    public void switchActiveSlot() {
        int otherSlot = 1 - activeSlot;
        if (weaponSlots[otherSlot] != null) {
            activeSlot = otherSlot;
        }
    }

    private void updateHitbox() {
        hitbox.set(getCenterX(), getCenterY(), Math.min(sprite.getWidth(), sprite.getHeight()) * playerDef.hitboxSize);
        shieldHitbox.set(getCenterX(), getCenterY(), orbitWeapon.getShieldRadius());
    }

    private void updateGrazeHitbox() {
        grazeHitbox.set(getCenterX(), getCenterY(), Math.min(sprite.getWidth(), sprite.getHeight()) * playerDef.haloHitboxSize);
    }

    private void resolveGrazePoints() {
        if (grazePoints > 100) {
            grazePoints %= 100;
            numBombs++;
        }
    }

    private void resolveInvincibility(float delta) {
        if(isInvincible) {
            invincibilityTimer += delta;
            if (invincibilityTimer > invincibleFrameTime) {
                isInvincible = false;
                invincibilityTimer = 0f;
            }
        }
    }

    public void draw(SpriteBatch batch) {
        if (isDead) {
            if (!deathAnimation.isAnimationFinished(deathTimer)) {
                TextureRegion frame = deathAnimation.getKeyFrame(deathTimer);
                batch.draw(frame, sprite.getX() - (deathDrawWidth/2.5f), sprite.getY()  - (deathDrawHeight/2.5f), deathDrawWidth, deathDrawHeight);
            }
            return;
        }

        // Halo drawn under the player sprite, centered on the player
        TextureRegion haloFrame = haloAnimation.getKeyFrame(haloAnimationTime);
        float haloX = sprite.getX() + sprite.getWidth() / 2f - haloDrawWidth / 2f;
        float haloY = sprite.getY() + sprite.getHeight() / 2f - haloDrawHeight / 2f;
        boolean grazeFlashing = grazeFlashTimer > 0;

        if (isInvincible) {
            boolean visible = ((int) (invincibilityTimer / BLINK_INTERVAL) % 2) == 0;
            float alpha = visible ? 1f : 0f;
            batch.setColor(grazeFlashing ? 0.3f : 1f, grazeFlashing ? 0.6f : 1f, 1f, alpha);
            batch.draw(haloFrame, haloX, haloY, haloDrawWidth, haloDrawHeight);
            batch.setColor(1f, 1f, 1f, 1f);
            sprite.setAlpha(visible ? 1f : 0f);
            sprite.draw(batch);
            sprite.setAlpha(1f);
        } else {
            batch.setColor(grazeFlashing ? 0.3f : 1f, grazeFlashing ? 0.6f : 1f, 1f, 1f);
            batch.draw(haloFrame, haloX, haloY, haloDrawWidth, haloDrawHeight);
            batch.setColor(1f, 1f, 1f, 1f);
            sprite.draw(batch);
        }

        if (orbitWeapon.isShieldActive()) {
            TextureRegion shieldFrame = shieldAnimation.getKeyFrame(orbitWeapon.getShieldTimer());
            float diameter = playerDef.reflectShield.size;
            batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
            batch.draw(shieldFrame, getCenterX() - diameter / 2f, getCenterY() - diameter / 2f, diameter, diameter);
            batch.flush();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    public void reset() {
        sprite.setPosition(worldWidth / 2f - sprite.getWidth() / 2f, 0);
        updateHitbox();
        updateGrazeHitbox();
        basicWeapon.setLevel(1);
        waveBlastWeapon.setLevel(0);
        orbitWeapon.setLevel(0);
        thunderboltWeapon.setLevel(0);
        weaponSlots[0] = basicWeapon;
        weaponSlots[1] = null;
        activeSlot = 0;
        basicWeapon.resetShootTimer();
        waveBlastWeapon.resetShootTimer();
        orbitWeapon.resetShootTimer();
        orbitWeapon.resetShield();
        thunderboltWeapon.resetShootTimer();
        animationTime = 0;
        numBombs = 1;
        numLives = 3;
        isInvincible = false;
        isDead = false;
        deathTimer = 0f;
    }

    public Circle getHitbox() { return hitbox; }
    public Circle getGrazeHitbox() { return grazeHitbox; }
    public Circle getShieldHitbox() { return shieldHitbox; }
    public boolean isShieldActive() { return orbitWeapon.isShieldActive(); }
    public float getShieldCooldownFraction() { return orbitWeapon.getShieldCooldownFraction(); }
    public float getShieldCooldownTimer() { return orbitWeapon.getShieldCooldownTimer(); }
    public float getGrazePoints() { return grazePoints; }
    public void setGrazePoints(float grazePoints) { this.grazePoints = grazePoints;}

    public float getCenterX() { return sprite.getX() + sprite.getWidth() / 2; }
    public float getCenterY() { return sprite.getY() + sprite.getHeight() / 2; }
    public float getWorldHeight() { return worldHeight; }
    public float getX() { return sprite.getX(); }
    public float getY() { return sprite.getY(); }
    public float getWidth() { return sprite.getWidth(); }
    public float getHeight() { return sprite.getHeight(); }
    public TextureRegion getCurrentFrame() { return sprite; }
    public TextureRegion getHaloFrame() { return haloAnimation.getKeyFrame(haloAnimationTime); }
    public float getHaloX() { return sprite.getX() + sprite.getWidth() / 2f - haloDrawWidth / 2f; }
    public float getHaloY() { return sprite.getY() + sprite.getHeight() / 2f - haloDrawHeight / 2f; }
    public float getHaloWidth() { return haloDrawWidth; }
    public float getHaloHeight() { return haloDrawHeight; }
    public Vector2 getBulletSpawnPoint() { return new Vector2(getCenterX(), sprite.getY() + bulletSpawnOffsetY); }
    public Weapon getWeaponPrototype() { return getCurrentWeapon(); }
    public int getActiveSlot() { return activeSlot; }
    public String getSlotWeaponId(int slot) { return weaponId(weaponSlots[slot]); }

    /** Collecting a weapon powerup levels up that weapon type. If it isn't already equipped in
     *  either slot, it's placed into the unequipped slot, replacing whatever weapon was there -
     *  a weapon is never allowed to occupy both slots at once. */
    public void levelUpWeapon(String weaponId) {
        Weapon target = weaponById(weaponId);

        if (target != null) {
            target.setLevel(Math.min(target.getLevel() + 1, MAX_WEAPON_LEVEL));
            if (weaponSlots[0] != target && weaponSlots[1] != target) {
                weaponSlots[1 - activeSlot] = target;
            }
        }
    }

    public int getWeaponLevel(String weaponId) {
        Weapon target = weaponById(weaponId);
        return target != null ? target.getLevel() : 0;
    }

    private Weapon weaponById(String weaponId) {
        if (weaponId == null) return null;
        return switch (weaponId) {
            case "BasicWeapon" -> basicWeapon;
            case "WaveBlastWeapon" -> waveBlastWeapon;
            case "OrbitWeapon" -> orbitWeapon;
            case "Thunderbolt" -> thunderboltWeapon;
            default -> null;
        };
    }

    // Debug-only: sets a weapon's level directly (unlike levelUpWeapon, doesn't equip it into a slot).
    public void setWeaponLevel(String weaponId, int level) {
        Weapon target = weaponById(weaponId);
        if (target != null) target.setLevel(MathUtils.clamp(level, 0, MAX_WEAPON_LEVEL));
    }

    public int getMaxWeaponLevel() { return MAX_WEAPON_LEVEL; }

    /** Debug-only: directly assigns which weapon occupies a slot (null clears it). Refuses to
     *  leave both slots empty, and bumps the same weapon out of the other slot if it's there -
     *  same "never in both slots at once" invariant levelUpWeapon keeps. */
    public void setSlotWeapon(int slot, String weaponId) {
        Weapon target = weaponById(weaponId);
        int other = 1 - slot;
        if (target == null && weaponSlots[other] == null) return;
        if (target != null && weaponSlots[other] == target) weaponSlots[other] = null;
        weaponSlots[slot] = target;
        if (weaponSlots[activeSlot] == null) activeSlot = other;
    }

    public void triggerGrazeFlash() {
        grazeFlashTimer = GRAZE_FLASH_DURATION;
    }

    public void disableGrazeHitbox() {
        grazeHitbox.radius = 0f;
    }

    public void startDeath() {
        deathX = sprite.getX();
        deathY = sprite.getY();
        isDead = true;
        deathTimer = 0f;
        isInvincible = false;
        invincibilityTimer = 0f;
        disableGrazeHitbox();
    }

    public boolean isDead() { return isDead; }

    public void startIFrames() {
        isInvincible = true;
    }

    public int getNumBombs() { return numBombs;}

    public void setNumBombs(int numBombs) {
        this.numBombs = numBombs;
    }

    public int getNumLives() { return numLives; }

    public void setNumLives(int numLives) { this.numLives = numLives; }

    public boolean isInvincible() {
        return isInvincible;
    }

    public void setInvincible(boolean invincible) {
        isInvincible = invincible;
    }
}
