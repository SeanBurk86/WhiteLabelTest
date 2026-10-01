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
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.gamemanagers.input.InputManager;
import whitelabeltest.player.weapons.*;

/** The player ship: movement, two weapon slots, firing, the halo (graze hitbox and the Basic /
 *  Thunderbolt Hyper Attacks), bombs, graze points, lives, death and i-frames. See the README's
 *  "Player" section. */
public class Player {
    private final PlayerDefinition playerDef;

    private final Sprite sprite;
    private final Circle hitbox;
    private final Circle grazeHitbox;
    private final float movementSpeed;
    private final float worldWidth;
    private final float worldHeight;

    private final float bulletSpawnOffsetY = 0f;

    private final BasicWeapon basicWeapon;
    private final WaveBlastWeapon waveBlastWeapon;
    private final OrbitWeapon orbitWeapon;
    private final ThunderboltWeapon thunderboltWeapon;
    private final WeaponDefinition orbitWeaponDef;
    // Hyper Attack tuning (WeaponDefinition's halo* / thunderbolt* fields).
    private final WeaponDefinition basicWeaponDef;
    private final WeaponDefinition thunderboltWeaponDef;
    private final Animation<TextureRegion> shieldAnimation;
    private final Circle shieldHitbox = new Circle();

    private final Weapon[] weaponSlots = new Weapon[2];
    private int activeSlot;
    private int numBombs;
    private int maxBombs;
    private int numLives;
    private float grazePoints;

    private int deathRestoreLevel;

    private final Animation<TextureRegion> animation;
    private float animationTime = 0;

    private final Animation<TextureRegion> deathAnimation;
    private final float deathDrawWidth, deathDrawHeight;

    private final Animation<TextureRegion> haloAnimation;
    private final float haloDrawWidth, haloDrawHeight;
    private float haloAnimationTime = 0;

    // Halo sprite while detached by Basic's Hyper Attack.
    private final Animation<TextureRegion> basicHaloDetachAnimation;
    private final float basicHaloDetachDrawWidth, basicHaloDetachDrawHeight;

    // Basic Hyper Attack: the halo dashes forward (hitting enemies), then rests detached, firing
    // part of Basic's pattern, until pressed again, when it glides back.
    private static final float HALO_ARRIVE_EPSILON = 0.05f;

    private boolean haloDetached;
    private boolean haloDashing;
    private boolean haloReturning;
    private boolean haloFastReturn;
    private float haloDetachedX, haloDetachedY;
    private float haloDashTargetY;
    private float haloFireTimer;
    private final Circle haloHitbox = new Circle();
    private final Array<Enemy> haloDashHitEnemies = new Array<>(false, 8);

    // Thunderbolt Hyper Attack: the halo moves out in front of the ship and tracks it, charging a
    // bomb up a tier every thunderboltChargeLevelTime. Releasing detonates it at that tier's damage
    // and radius (applied by CollisionManager), plays the bomb animation in place, then reattaches.
    // One halo animation per charge tier, plus the one-shot bomb animation.
    private final Animation<TextureRegion>[] thunderShrinkAnimations;
    private final float[] thunderShrinkDrawWidth, thunderShrinkDrawHeight;
    private final Animation<TextureRegion> thunderHaloBombAnimation;
    // Bomb sprite size at the top tier; scaled down for lower tiers to match the actual blast.
    private final float thunderHaloBombDrawWidth, thunderHaloBombDrawHeight;

    private boolean thunderboltHaloActive;
    private boolean thunderboltMoving;
    private boolean thunderboltCharging;
    private float thunderboltChargeTimer;
    private int thunderboltChargeLevel;
    private int thunderboltChargeSoundLevel;
    private boolean thunderboltDetonationPending;
    private float thunderboltDetonationX, thunderboltDetonationY;
    private int thunderboltDetonationDamage;
    private float thunderboltDetonationRadius;
    // This detonation's radius as a fraction of the full radius (scales the bomb sprite).
    private float thunderboltDetonationVisualScale = 1f;
    // True while the bomb animation plays after release (damage has already been applied).
    private boolean thunderboltDetonating;
    private float thunderboltDetonationAnimTime;
    // A Hyper Attack press during the bomb animation, replayed when the halo reattaches.
    private boolean thunderboltHyperAttackBuffered;

    private float invincibleFrameTime = 2f;
    private float invincibilityTimer = 0f;
    private boolean isInvincible;

    private boolean isDead;
    private float deathTimer;
    private float deathX, deathY;
    private static final float DEATH_WAIT = 2f;

    private float grazeFlashTimer;
    private static final float GRAZE_FLASH_DURATION = 0.12f;

    // Red halo flash on a dash hit (graze flashes it blue).
    private float haloBashFlashTimer;
    private static final float HALO_BASH_FLASH_DURATION = 0.15f;

    private static final float BLINK_INTERVAL = 0.1f;

    public Player(AssetManager assets, float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.playerDef = assets.getPlayerDefinition();
        this.movementSpeed = playerDef.movementSpeed;

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

        PlayerDefinition.SpriteDef basicHaloDetachSprite = playerDef.basicHaloDetach;
        basicHaloDetachAnimation = AnimationCache.get(assets.basicHaloDetachTexture, basicHaloDetachSprite.columns > 0 ? basicHaloDetachSprite.columns : basicHaloDetachSprite.frameCount,
            basicHaloDetachSprite.rows, basicHaloDetachSprite.frameCount, 1f / 24f, Animation.PlayMode.LOOP);
        TextureRegion[] basicHaloDetachFrames = basicHaloDetachAnimation.getKeyFrames();
        float basicHaloDetachAspect = (float) basicHaloDetachFrames[0].getRegionHeight() / basicHaloDetachFrames[0].getRegionWidth();
        basicHaloDetachDrawWidth = basicHaloDetachSprite.size;
        basicHaloDetachDrawHeight = basicHaloDetachSprite.size * basicHaloDetachAspect;

        @SuppressWarnings("unchecked")
        Animation<TextureRegion>[] shrinkAnims = new Animation[4];
        float[] shrinkWidths = new float[4];
        float[] shrinkHeights = new float[4];
        PlayerDefinition.SpriteDef[] shrinkSprites = {
            playerDef.thunderHyperHaloShrink1, playerDef.thunderHyperHaloShrink2,
            playerDef.thunderHyperHaloShrink3, playerDef.thunderHyperHaloShrink4,
        };
        for (int i = 0; i < shrinkSprites.length; i++) {
            float[] dims = new float[2];
            shrinkAnims[i] = buildHaloAnimation(assets.thunderHyperHaloShrinkTextures[i], shrinkSprites[i], Animation.PlayMode.LOOP, dims);
            shrinkWidths[i] = dims[0];
            shrinkHeights[i] = dims[1];
        }
        thunderShrinkAnimations = shrinkAnims;
        thunderShrinkDrawWidth = shrinkWidths;
        thunderShrinkDrawHeight = shrinkHeights;

        float[] bombDims = new float[2];
        thunderHaloBombAnimation = buildHaloAnimation(assets.thunderHaloBombTexture, playerDef.thunderHaloBomb, Animation.PlayMode.NORMAL, bombDims);
        thunderHaloBombDrawWidth = bombDims[0];
        thunderHaloBombDrawHeight = bombDims[1];

        // Weapons before hitboxes: updateHitbox() reads the orbit shield radius.
        WeaponDefinition bDef = assets.getWeaponDefinition("BasicWeapon");
        basicWeaponDef = bDef;
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
        thunderboltWeaponDef = thbDef;
        thunderboltWeapon = new ThunderboltWeapon();
        thunderboltWeapon.initDefinition(thbDef);

        hitbox = new Circle();
        updateHitbox();
        grazeHitbox = new Circle();
        updateGrazeHitbox();

        weaponSlots[0] = basicWeapon;
        weaponSlots[1] = null;
        activeSlot = 0;
        numBombs = 1;
        maxBombs = Math.min(playerDef.baseMaxBombs, MAX_BOMB_CAPACITY);
        numLives = playerDef.startingLives;
        grazePoints = 0;
        isInvincible = false;
    }

    /** Builds a halo animation from player.json, writing {width, height} into dimsOut. */
    private Animation<TextureRegion> buildHaloAnimation(Texture texture, PlayerDefinition.SpriteDef sprite, Animation.PlayMode mode, float[] dimsOut) {
        Animation<TextureRegion> anim = AnimationCache.get(texture, sprite.columns > 0 ? sprite.columns : sprite.frameCount,
            sprite.rows, sprite.frameCount, 1f / 24f, mode);
        TextureRegion[] frames = anim.getKeyFrames();
        float aspect = (float) frames[0].getRegionHeight() / frames[0].getRegionWidth();
        dimsOut[0] = sprite.size;
        dimsOut[1] = sprite.size * aspect;
        return anim;
    }

    public void update(float delta, InputManager input, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies, boolean weaponsDisabled, boolean hyperAttackDisabled) {
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
        if (haloBashFlashTimer > 0) haloBashFlashTimer -= delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (input.isWeaponSwitchJustPressed()) {
            switchActiveSlot();
            recallHaloOnWeaponSwitch(audio);
        }

        // Disabled windows only block firing / Hyper Attack; the raw shoot input still drives the
        // focus slowdown, orbit ring and gem homing. A press while disabled is dropped, not buffered.
        boolean canShoot = input.isShooting() && !weaponsDisabled;
        handleMovement(delta, input.getMoveDirection(), input.isShooting());
        handleShooting(delta, canShoot, assets, audio, bullets, enemies);
        maintainOrbitRing(bullets, assets, audio, input.isShooting());
        handleHyperAttack(input.isHyperAttackJustPressed() && !hyperAttackDisabled, bullets, enemies, assets, audio);
        updateThunderboltCharge(delta, input.isHyperAttackJustReleased(), audio);
        updateThunderboltDetonationAnim(delta, input.isHyperAttackHeld(), audio);
        updateHaloMovement(delta, audio);
        updateHaloFiring(delta, canShoot, bullets, assets, audio);
        updateHitbox();
        updateGrazeHitbox();
        resolveGrazePoints(audio);
        resolveInvincibility(delta);
    }

    // The orbit ring exists only while OrbitWeapon is active and fire is held.
    private void maintainOrbitRing(Array<Weapon> bullets, AssetManager assets, AudioManager audio, boolean isShooting) {
        if (getCurrentWeapon() == orbitWeapon && isShooting) {
            orbitWeapon.maintainRing(bullets, assets.getTexture(orbitWeaponDef.texture), this, audio);
        } else {
            orbitWeapon.clearRing(bullets);
        }
    }

    // Triggers the active weapon's Hyper Attack. While the halo is detached only Basic's re-press
    // (which recalls it) goes through. A press during the bomb animation is buffered.
    private void handleHyperAttack(boolean hyperAttackJustPressed, Array<Weapon> bullets, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        if (!hyperAttackJustPressed) return;
        if (thunderboltDetonating) {
            thunderboltHyperAttackBuffered = true;
            return;
        }
        Weapon currentWeapon = getCurrentWeapon();
        if (currentWeapon == null) return;
        if (haloDetached && currentWeapon != basicWeapon) return;
        currentWeapon.hyperAttack(this, bullets, enemies, assets, audio);
    }

    /** Switching weapons recalls a detached halo (a Thunderbolt charge fizzles without detonating). */
    private void recallHaloOnWeaponSwitch(AudioManager audio) {
        if (!haloDetached || haloReturning || thunderboltDetonating) return;
        haloDashing = false;
        thunderboltMoving = false;
        thunderboltCharging = false;
        haloReturning = true;
        haloFastReturn = !thunderboltHaloActive;
        audio.playHaloReturn();
    }

    /** Basic Hyper Attack toggle: launch the halo forward, or (once resting) start its return.
     *  Ignored mid-dash or mid-return. */
    public void triggerBasicHyperAttack(AudioManager audio) {
        if (!haloDetached) {
            haloDetached = true;
            haloDashing = true;
            haloReturning = false;
            haloDashHitEnemies.clear();
            haloFireTimer = 0f;
            haloDetachedX = attachedHaloX();
            haloDetachedY = attachedHaloY();
            haloDashTargetY = haloDetachedY + basicWeaponDef.haloDashDistance;
            audio.playHaloDetach();
        } else if (!haloDashing && !haloReturning) {
            haloReturning = true;
            haloFastReturn = true;
            audio.playHaloReturn();
        }
    }

    /** Thunderbolt Hyper Attack: sends the halo out in front of the ship to charge. Releasing the
     *  button detonates it (see updateThunderboltCharge()). */
    public void triggerThunderboltHyperAttack() {
        if (haloDetached) return;

        haloDetached = true;
        haloDashing = false;
        haloReturning = false;
        thunderboltHaloActive = true;
        thunderboltMoving = true;
        thunderboltCharging = false;
        thunderboltChargeTimer = 0f;
        thunderboltChargeLevel = 0;
        thunderboltChargeSoundLevel = -1;
        haloDetachedX = attachedHaloX();
        haloDetachedY = attachedHaloY();
    }

    /** Raises the charge tier while charging (with a sound per tier, including the base tier). On
     *  release, queues the detonation for CollisionManager at the current tier's damage and radius
     *  and starts the bomb animation. A release during the move-out (a quick tap) detonates at the
     *  base tier. */
    private void updateThunderboltCharge(float delta, boolean hyperAttackJustReleased, AudioManager audio) {
        if (!thunderboltMoving && !thunderboltCharging) return;

        if (thunderboltCharging) {
            thunderboltChargeTimer += delta;
            thunderboltChargeLevel = Math.min((int) (thunderboltChargeTimer / thunderboltWeaponDef.thunderboltChargeLevelTime), thunderboltWeaponDef.thunderboltChargeDamageByTier.length - 1);
        }

        if (thunderboltChargeLevel != thunderboltChargeSoundLevel) {
            thunderboltChargeSoundLevel = thunderboltChargeLevel;
            audio.playThunderboltHyperLevel(thunderboltChargeLevel);
        }

        if (hyperAttackJustReleased) {
            thunderboltDetonationPending = true;
            thunderboltDetonationX = haloCenterX();
            thunderboltDetonationY = haloCenterY();
            thunderboltDetonationDamage = thunderboltWeaponDef.thunderboltChargeDamageByTier[thunderboltChargeLevel];
            thunderboltDetonationRadius = thunderboltWeaponDef.thunderboltBlastRadiusByTier[thunderboltChargeLevel];
            thunderboltDetonationVisualScale = thunderboltDetonationRadius / thunderboltFullBlastRadius();

            thunderboltMoving = false;
            thunderboltCharging = false;
            thunderboltDetonating = true;
            thunderboltDetonationAnimTime = 0f;
        }
    }

    /** Plays the bomb animation in place, then reattaches the halo. A buffered press restarts the
     *  charge (if Thunderbolt is still active); if the button is no longer held there will be no
     *  release, so it detonates at the base tier immediately. */
    private void updateThunderboltDetonationAnim(float delta, boolean hyperAttackHeld, AudioManager audio) {
        if (!thunderboltDetonating) return;

        thunderboltDetonationAnimTime += delta;
        if (thunderHaloBombAnimation.isAnimationFinished(thunderboltDetonationAnimTime)) {
            thunderboltDetonating = false;
            haloDetached = false;
            thunderboltHaloActive = false;

            boolean replay = thunderboltHyperAttackBuffered;
            thunderboltHyperAttackBuffered = false;
            if (replay && getCurrentWeapon() == thunderboltWeapon) {
                triggerThunderboltHyperAttack();
                if (!hyperAttackHeld) {
                    updateThunderboltCharge(0f, true, audio);
                }
            }
        }
    }

    /** While Basic's halo is detached and fire is held, the halo fires its share of Basic's pattern
     *  (split with the ship, not doubled) on the weapon's cadence. */
    private void updateHaloFiring(float delta, boolean isShooting, Array<Weapon> bullets, AssetManager assets, AudioManager audio) {
        if (!haloDetached || thunderboltHaloActive) return;

        haloFireTimer += delta;
        if (isShooting && haloFireTimer > basicWeapon.getFireRate()) {
            haloFireTimer = 0f;
            basicWeapon.spawnHaloPortion(bullets, resolveTextureFor("BasicWeapon", assets), haloCenterX(), haloCenterY());
            basicWeapon.playFireSound(audio, basicWeapon.getLevel());
        }
    }

    private void updateHaloMovement(float delta, AudioManager audio) {
        if (!haloDetached) return;

        if (haloDashing) {
            float remaining = haloDashTargetY - haloDetachedY;
            float step = basicWeaponDef.haloDashSpeed * delta;
            if (Math.abs(remaining) <= step) {
                haloDetachedY = haloDashTargetY;
                haloDashing = false;
            } else {
                haloDetachedY += step;
            }
            haloHitbox.set(haloCenterX(), haloCenterY(), Math.min(haloDrawWidth, haloDrawHeight) / 2f);
        } else if (thunderboltMoving) {
            // Chases the (moving) point in front of the ship.
            float targetX = attachedHaloX();
            float targetY = attachedHaloY() + thunderboltWeaponDef.thunderboltHaloFrontDistance;
            float dx = targetX - haloDetachedX;
            float dy = targetY - haloDetachedY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float step = thunderboltWeaponDef.thunderboltHaloMoveSpeed * delta;
            if (dist <= Math.max(step, HALO_ARRIVE_EPSILON)) {
                haloDetachedX = targetX;
                haloDetachedY = targetY;
                thunderboltMoving = false;
                thunderboltCharging = true;
                thunderboltChargeTimer = 0f;
                thunderboltChargeLevel = 0;
            } else {
                haloDetachedX += dx / dist * step;
                haloDetachedY += dy / dist * step;
            }
        } else if (thunderboltCharging) {
            // Locked to the ship's front while charging.
            haloDetachedX = attachedHaloX();
            haloDetachedY = attachedHaloY() + thunderboltWeaponDef.thunderboltHaloFrontDistance;
        } else if (haloReturning) {
            float targetX = attachedHaloX();
            float targetY = attachedHaloY();
            float dx = targetX - haloDetachedX;
            float dy = targetY - haloDetachedY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float step = (haloFastReturn ? basicWeaponDef.haloFastReturnSpeed : basicWeaponDef.haloReturnSpeed) * delta;
            if (dist <= Math.max(step, HALO_ARRIVE_EPSILON)) {
                haloDetached = false;
                haloReturning = false;
                haloFastReturn = false;
                thunderboltHaloActive = false;
                audio.playHaloLatch();
            } else {
                haloDetachedX += dx / dist * step;
                haloDetachedY += dy / dist * step;
            }
        }
    }

    // Focus movement: the weapon's slowdown applies while firing and while a Hyper Attack has the
    // halo out (for precise aiming), but not during the bomb animation.
    private void handleMovement(float delta, Vector2 moveDirection, boolean isShooting) {
        Weapon currentWeapon = getCurrentWeapon();
        float speed = (currentWeapon != null && (isShooting || (haloDetached && !thunderboltDetonating)))
            ? movementSpeed * currentWeapon.getShootSpeedMultiplier() : movementSpeed;
        if (moveDirection.x != 0 || moveDirection.y != 0) {
            sprite.translateX(moveDirection.x * speed * delta);
            sprite.translateY(moveDirection.y * speed * delta);
        }
        sprite.setX(MathUtils.clamp(sprite.getX(), 0, worldWidth - sprite.getWidth()));
        sprite.setY(MathUtils.clamp(sprite.getY(), 0, worldHeight - sprite.getHeight()));
    }

    private void handleShooting(float delta, boolean isShooting, AssetManager assets, AudioManager audio, Array<Weapon> bullets, Array<Enemy> enemies) {
        advanceWeaponTimers(delta, audio);

        Weapon currentWeapon = getCurrentWeapon();
        if (currentWeapon == null) return;
        if (isShooting && currentWeapon.getShootTimer() > currentWeapon.getFireRate()) {
            currentWeapon.resetShootTimer();

            Texture bulletTex = resolveActiveTexture(assets);
            Vector2 spawnPoint = getBulletSpawnPoint();
            if (currentWeapon == basicWeapon && haloDetached) {
                // Split the pattern with the detached halo instead of doubling it.
                basicWeapon.spawnPlayerPortion(bullets, bulletTex, spawnPoint.x, spawnPoint.y);
            } else {
                currentWeapon.spawn(bullets, bulletTex, spawnPoint.x, spawnPoint.y, this, enemies, assets);
            }
            currentWeapon.playFireSound(audio, currentWeapon.getLevel());
        }
    }

    // Both slots' cooldowns tick every frame, so switching can't reset or skip a cooldown.
    private void advanceWeaponTimers(float delta, AudioManager audio) {
        if (weaponSlots[0] != null) weaponSlots[0].addShootTimer(delta);
        if (weaponSlots[1] != null && weaponSlots[1] != weaponSlots[0]) weaponSlots[1].addShootTimer(delta);
        // Orbit reflect shield recharged.
        if (orbitWeapon.consumeShieldReady() && audio != null) audio.playShieldsReady();
    }

    private Texture resolveActiveTexture(AssetManager assets) {
        return resolveTextureFor(weaponId(getCurrentWeapon()), assets);
    }

    private Texture resolveTextureFor(String weaponId, AssetManager assets) {
        WeaponDefinition def = assets.getWeaponDefinition(weaponId);
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

    // The graze hitbox (grazes, powerups, gems) follows the halo, even when detached.
    private void updateGrazeHitbox() {
        float radius = Math.min(sprite.getWidth(), sprite.getHeight()) * playerDef.haloHitboxSize;
        if (haloDetached) {
            grazeHitbox.set(haloCenterX(), haloCenterY(), radius);
        } else {
            grazeHitbox.set(getCenterX(), getCenterY(), radius);
        }
    }

    private void resolveGrazePoints(AudioManager audio) {
        if (grazePoints > 100) {
            grazePoints %= 100;
            int before = numBombs;
            numBombs = Math.min(numBombs + 1, maxBombs);
            if (numBombs > before && audio != null) audio.playGrazeBombEarned();
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

        // Halo under the ship (or wherever a Hyper Attack has it), in the current state's sprite.
        HaloVisual haloVisual = resolveHaloVisual();
        TextureRegion haloFrame = haloVisual.frame;
        float haloDrawW = haloVisual.width;
        float haloDrawH = haloVisual.height;
        float drawHaloX = haloDrawX(haloVisual);
        float drawHaloY = haloDrawY(haloVisual);
        boolean grazeFlashing = grazeFlashTimer > 0;
        boolean bashFlashing = haloBashFlashTimer > 0;
        // Red bash flash beats blue graze flash.
        float haloR = bashFlashing ? 1f : (grazeFlashing ? 0.3f : 1f);
        float haloG = bashFlashing ? 0.15f : (grazeFlashing ? 0.6f : 1f);
        float haloB = bashFlashing ? 0.15f : 1f;

        if (isInvincible) {
            boolean visible = ((int) (invincibilityTimer / BLINK_INTERVAL) % 2) == 0;
            float alpha = visible ? 1f : 0f;
            batch.setColor(haloR, haloG, haloB, alpha);
            batch.draw(haloFrame, drawHaloX, drawHaloY, haloDrawW, haloDrawH);
            batch.setColor(1f, 1f, 1f, 1f);
            sprite.setAlpha(visible ? 1f : 0f);
            sprite.draw(batch);
            sprite.setAlpha(1f);
        } else {
            batch.setColor(haloR, haloG, haloB, 1f);
            batch.draw(haloFrame, drawHaloX, drawHaloY, haloDrawW, haloDrawH);
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

    /** Starts a run with the chosen loadout: those two weapons equipped at level 1, all others at
     *  level 0 and unequipped. */
    public void reset(WeaponLoadout loadout) {
        sprite.setPosition(worldWidth / 2f - sprite.getWidth() / 2f, 0);
        updateHitbox();
        updateGrazeHitbox();
        basicWeapon.setLevel(0);
        waveBlastWeapon.setLevel(0);
        orbitWeapon.setLevel(0);
        thunderboltWeapon.setLevel(0);
        weaponSlots[0] = null;
        weaponSlots[1] = null;
        activeSlot = 0;
        basicWeapon.resetShootTimer();
        waveBlastWeapon.resetShootTimer();
        waveBlastWeapon.resetHyperAttackCooldown();
        orbitWeapon.resetShootTimer();
        orbitWeapon.resetShield();
        thunderboltWeapon.resetShootTimer();
        animationTime = 0;
        numBombs = 1;
        maxBombs = Math.min(playerDef.baseMaxBombs, MAX_BOMB_CAPACITY);
        numLives = playerDef.startingLives;
        isInvincible = false;
        isDead = false;
        deathTimer = 0f;
        grazePoints = 0f;
        reattachHaloImmediately();
        setSlotWeapon(0, loadout.slotAWeaponId);
        setSlotWeapon(1, loadout.slotBWeaponId);
        deathRestoreLevel = 0;
    }

    /** At each new stage: start position, halo attached, equipped weapons back to level 1. Lives,
     *  bombs and loadout carry over. */
    public void resetForNewStage() {
        sprite.setPosition(worldWidth / 2f - sprite.getWidth() / 2f, 0);
        updateHitbox();
        updateGrazeHitbox();
        reattachHaloImmediately();
        for (Weapon w : weaponSlots) {
            if (w != null) w.setLevel(1);
        }
        deathRestoreLevel = 0;
    }

    /** Snaps the halo back onto the ship, cancelling any Hyper Attack (on reset and death). */
    private void reattachHaloImmediately() {
        haloDetached = false;
        haloDashing = false;
        haloReturning = false;
        haloFastReturn = false;
        haloFireTimer = 0f;
        haloDashHitEnemies.clear();
        thunderboltHaloActive = false;
        thunderboltMoving = false;
        thunderboltCharging = false;
        thunderboltChargeTimer = 0f;
        thunderboltChargeLevel = 0;
        thunderboltChargeSoundLevel = 0;
        thunderboltDetonationPending = false;
        thunderboltDetonating = false;
        thunderboltDetonationAnimTime = 0f;
        thunderboltHyperAttackBuffered = false;
    }

    public Circle getHitbox() { return hitbox; }
    public Circle getGrazeHitbox() { return grazeHitbox; }
    public Circle getShieldHitbox() { return shieldHitbox; }
    public boolean isHaloDashing() { return haloDashing; }
    public Circle getHaloHitbox() { return haloHitbox; }
    public boolean hasHaloDamaged(Enemy enemy) { return haloDashHitEnemies.contains(enemy, true); }
    public void markHaloDamaged(Enemy enemy) { haloDashHitEnemies.add(enemy); }
    public int getHaloDashDamage() { return basicWeaponDef.haloDashDamage; }
    public float getHaloCollisionScale() { return basicWeaponDef.haloCollisionScale; }
    public boolean hasPendingThunderboltDetonation() { return thunderboltDetonationPending; }
    public float getThunderboltDetonationX() { return thunderboltDetonationX; }
    public float getThunderboltDetonationY() { return thunderboltDetonationY; }
    public int getThunderboltDetonationDamage() { return thunderboltDetonationDamage; }
    // Radius captured at release.
    public float getThunderboltDetonationRadius() { return thunderboltDetonationRadius; }
    // Radius a release would use right now (debug overlay preview).
    public float getThunderboltBlastRadius() { return thunderboltWeaponDef.thunderboltBlastRadiusByTier[thunderboltChargeLevel]; }
    // The top-tier radius (the size the bomb sprite is authored at).
    private float thunderboltFullBlastRadius() {
        float[] radii = thunderboltWeaponDef.thunderboltBlastRadiusByTier;
        return radii[radii.length - 1];
    }
    public void clearPendingThunderboltDetonation() { thunderboltDetonationPending = false; }
    // True while a release would detonate the bomb (moving out or charging).
    public boolean isThunderboltBombActive() { return thunderboltMoving || thunderboltCharging; }
    public float getHaloCenterX() { return haloCenterX(); }
    public float getHaloCenterY() { return haloCenterY(); }
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
    public TextureRegion getHaloFrame() { return resolveHaloVisual().frame; }
    public float getHaloX() { return haloDrawX(resolveHaloVisual()); }
    public float getHaloY() { return haloDrawY(resolveHaloVisual()); }
    // The detached position is the halo's logical center; the draw corner depends on the current
    // sprite's size.
    private float haloDrawX(HaloVisual visual) { return haloDetached ? haloCenterX() - visual.width / 2f : attachedHaloX(); }
    private float haloDrawY(HaloVisual visual) { return haloDetached ? haloCenterY() - visual.height / 2f : attachedHaloY(); }
    private float attachedHaloX() { return sprite.getX() + sprite.getWidth() / 2f - haloDrawWidth / 2f; }
    private float attachedHaloY() { return sprite.getY() + sprite.getHeight() / 2f - haloDrawHeight / 2f; }
    private float haloCenterX() { return haloDetachedX + haloDrawWidth / 2f; }
    private float haloCenterY() { return haloDetachedY + haloDrawHeight / 2f; }
    public float getHaloWidth() { return resolveHaloVisual().width; }
    public float getHaloHeight() { return resolveHaloVisual().height; }

    private static final class HaloVisual {
        final TextureRegion frame;
        final float width, height;
        HaloVisual(TextureRegion frame, float width, float height) {
            this.frame = frame;
            this.width = width;
            this.height = height;
        }
    }

    /** The halo's current sprite: bomb, Thunderbolt charge tier, Basic detached, or attached
     *  (most specific first, since both Hyper Attacks set haloDetached). */
    private HaloVisual resolveHaloVisual() {
        if (thunderboltDetonating) {
            TextureRegion frame = thunderHaloBombAnimation.getKeyFrame(thunderboltDetonationAnimTime);
            return new HaloVisual(frame, thunderHaloBombDrawWidth * thunderboltDetonationVisualScale, thunderHaloBombDrawHeight * thunderboltDetonationVisualScale);
        }
        if (thunderboltHaloActive) {
            return new HaloVisual(thunderShrinkAnimations[thunderboltChargeLevel].getKeyFrame(haloAnimationTime), thunderShrinkDrawWidth[thunderboltChargeLevel], thunderShrinkDrawHeight[thunderboltChargeLevel]);
        }
        if (haloDetached) {
            return new HaloVisual(basicHaloDetachAnimation.getKeyFrame(haloAnimationTime), basicHaloDetachDrawWidth, basicHaloDetachDrawHeight);
        }
        return new HaloVisual(haloAnimation.getKeyFrame(haloAnimationTime), haloDrawWidth, haloDrawHeight);
    }
    public Vector2 getBulletSpawnPoint() { return new Vector2(getCenterX(), sprite.getY() + bulletSpawnOffsetY); }
    public Weapon getWeaponPrototype() { return getCurrentWeapon(); }
    public int getActiveSlot() { return activeSlot; }
    public String getSlotWeaponId(int slot) { return weaponId(weaponSlots[slot]); }
    public String getCurrentWeaponId() { return weaponId(getCurrentWeapon()); }

    /** A powerup levels up both equipped weapons (capped at maxWeaponLevel). */
    public void levelUpEquippedWeapons(int amount) {
        for (Weapon w : weaponSlots) {
            if (w != null) w.setLevel(Math.min(w.getLevel() + amount, playerDef.maxWeaponLevel));
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

    // Sets a weapon's level directly without equipping it (debug, starting loadouts).
    public void setWeaponLevel(String weaponId, int level) {
        Weapon target = weaponById(weaponId);
        if (target != null) target.setLevel(MathUtils.clamp(level, 0, playerDef.maxWeaponLevel));
    }

    public int getMaxWeaponLevel() { return playerDef.maxWeaponLevel; }
    public int getStartingLives() { return playerDef.startingLives; }

    /** Puts a weapon in a slot (moving it out of the other slot, raising it to level 1). null clears
     *  the slot unless that would leave both empty. */
    public void setSlotWeapon(int slot, String weaponId) {
        Weapon target = weaponById(weaponId);
        int other = 1 - slot;
        if (target == null && weaponSlots[other] == null) return;
        if (target != null && weaponSlots[other] == target) weaponSlots[other] = null;
        if (target != null && target.getLevel() < 1) target.setLevel(1);
        weaponSlots[slot] = target;
        if (weaponSlots[activeSlot] == null) activeSlot = other;
    }

    public void triggerGrazeFlash() {
        grazeFlashTimer = GRAZE_FLASH_DURATION;
    }

    public void triggerHaloBashFlash() {
        haloBashFlashTimer = HALO_BASH_FLASH_DURATION;
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
        reattachHaloImmediately();
        resetWeaponsOnDeath();
        maxBombs = Math.min(maxBombs + 1, MAX_BOMB_CAPACITY);
    }

    /** Dying drops both equipped weapons to level 1, remembering the highest level for the restore
     *  powerup. */
    private void resetWeaponsOnDeath() {
        deathRestoreLevel = Math.max(
            weaponSlots[0] != null ? weaponSlots[0].getLevel() : 0,
            weaponSlots[1] != null ? weaponSlots[1].getLevel() : 0);

        for (Weapon w : weaponSlots) {
            if (w != null) w.setLevel(1);
        }
    }

    public int getDeathRestoreLevel() { return deathRestoreLevel; }

    public boolean isDead() { return isDead; }

    public void startIFrames() {
        isInvincible = true;
    }

    public int getNumBombs() { return numBombs;}
    public int getMaxBombs() { return maxBombs; }

    // The most bombs the player can ever hold; the cap grows by one per death up to this.
    public static final int MAX_BOMB_CAPACITY = 6;

    // Sets the bomb cap directly (normally it grows by one per death).
    public void setMaxBombs(int maxBombs) { this.maxBombs = MathUtils.clamp(maxBombs, 0, MAX_BOMB_CAPACITY); }

    public void setNumBombs(int numBombs) {
        this.numBombs = MathUtils.clamp(numBombs, 0, maxBombs);
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
