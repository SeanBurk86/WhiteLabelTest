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

    // BasicWeapon's Hyper Attack (see BasicWeapon.hyperAttack/triggerBasicHyperAttack): the halo
    // launches forward a short distance, dealing damage to anything it clips along the way, then
    // rests there - detached from the player, firing BasicWeapon's own stream on its own cadence
    // for as long as it's detached - until Hyper Attack is pressed again, at which point it glides
    // back to wherever the player currently is instead of snapping there.
    private static final float HALO_DASH_DISTANCE = 4.2f;
    private static final float HALO_DASH_SPEED = 9f;
    private static final float HALO_RETURN_SPEED = 6f;
    private static final float HALO_ARRIVE_EPSILON = 0.05f;
    private static final int HALO_DASH_DAMAGE = 30;

    private boolean haloDetached;
    private boolean haloDashing;
    private boolean haloReturning;
    private float haloDetachedX, haloDetachedY;
    private float haloDashTargetY;
    private float haloFireTimer;
    private final Circle haloHitbox = new Circle();
    private final Array<Enemy> haloDashHitEnemies = new Array<>(false, 8);

    // ThunderboltWeapon's Hyper Attack (see ThunderboltWeapon.hyperAttack/triggerThunderboltHyperAttack):
    // the halo launches out to hover in front of the ship - tracking it, rather than resting at a
    // fixed spot like Basic's dash does - where it charges a bomb through four damage tiers (see
    // THUNDERBOLT_CHARGE_DAMAGE), gaining a tier every THUNDERBOLT_CHARGE_LEVEL_TIME seconds it's
    // held. Releasing the button detonates it at whatever tier it reached - see
    // CollisionManager.checkThunderboltDetonation for the actual area damage and green-lightning
    // visual this only queues up - then sends the halo gliding back to the player the same way
    // Basic's does (shares haloReturning/haloDetached with it; see updateHaloMovement()).
    private static final float THUNDERBOLT_HALO_FRONT_DISTANCE = 2.5f;
    private static final float THUNDERBOLT_HALO_MOVE_SPEED = 10f;
    private static final float THUNDERBOLT_CHARGE_LEVEL_TIME = 0.5f;
    private static final int[] THUNDERBOLT_CHARGE_DAMAGE = {32, 64, 128, 256};
    private static final float THUNDERBOLT_BLAST_RADIUS = 3.5f;

    private boolean thunderboltHaloActive;
    private boolean thunderboltMoving;
    private boolean thunderboltCharging;
    private float thunderboltChargeTimer;
    private int thunderboltChargeLevel;
    private int thunderboltChargeSoundLevel;
    private boolean thunderboltDetonationPending;
    private float thunderboltDetonationX, thunderboltDetonationY;
    private int thunderboltDetonationDamage;

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
        numLives = 6;
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
            recallHaloOnWeaponSwitch();
        }

        handleMovement(delta, input.getMoveDirection(), input.isShooting());
        handleShooting(delta, input.isShooting(), assets, audio, bullets, enemies);
        maintainOrbitRing(bullets, assets, input.isShooting());
        handleHyperAttack(input.isHyperAttackJustPressed(), bullets, enemies, assets, audio);
        updateThunderboltCharge(delta, input.isHyperAttackJustReleased(), audio);
        updateHaloMovement(delta);
        updateHaloFiring(delta, input.isShooting(), bullets, assets, audio);
        updateHitbox();
        updateGrazeHitbox();
        resolveGrazePoints();
        resolveInvincibility(delta);
    }

    // The orbit ring is up only while OrbitWeapon is both the actively selected slot and the fire
    // button is held - see OrbitWeapon's class comment for why the prototype/ring-member split is
    // safe despite sharing a class.
    private void maintainOrbitRing(Array<Weapon> bullets, AssetManager assets, boolean isShooting) {
        if (getCurrentWeapon() == orbitWeapon && isShooting) {
            orbitWeapon.maintainRing(bullets, assets.getTexture(orbitWeaponDef.texture), this);
        } else {
            orbitWeapon.clearRing(bullets);
        }
    }

    // Hyper Attack triggers whichever weapon is currently active's own ability (a no-op for
    // weapons that don't define one yet - see Weapon.hyperAttack), independent of the fire button.
    // Blocked for every weapon but Basic while the halo hasn't reattached, so a different
    // weapon's Hyper Attack can't start - or, for Thunderbolt, restart - while the halo's still
    // out on some other ability's business. Basic's own re-press still goes through, since that's
    // what reattaches it (see triggerBasicHyperAttack()); Thunderbolt has no re-press step of its
    // own since releasing the button is what detonates/recalls it (see updateThunderboltCharge()).
    private void handleHyperAttack(boolean hyperAttackJustPressed, Array<Weapon> bullets, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        if (!hyperAttackJustPressed) return;
        if (haloDetached && getCurrentWeapon() != basicWeapon) return;
        getCurrentWeapon().hyperAttack(this, bullets, enemies, assets, audio);
    }

    /** Switching weapons recalls a still-detached halo immediately, interrupting an in-progress
     *  dash if needed, instead of leaving it stranded away from the player while a different
     *  weapon is equipped. No-op once it's already heading back. */
    private void recallHaloOnWeaponSwitch() {
        if (!haloDetached || haloReturning) return;
        haloDashing = false;
        // Cancels a mid-flight Thunderbolt charge without detonating it - switching away is
        // treated as a fizzle, not a release.
        thunderboltMoving = false;
        thunderboltCharging = false;
        haloReturning = true;
    }

    /** BasicWeapon's Hyper Attack (see BasicWeapon.hyperAttack), toggled by each press: while
     *  attached, launches the halo forward a short distance - see updateHaloMovement() for the
     *  damage dealt along the way - where it then rests, detached, until this is called again,
     *  which starts it gliding back to the player instead of snapping there. Ignored mid-launch
     *  or mid-return so a rapid second press can't restart either motion. */
    public void triggerBasicHyperAttack() {
        if (!haloDetached) {
            haloDetached = true;
            haloDashing = true;
            haloReturning = false;
            haloDashHitEnemies.clear();
            haloFireTimer = 0f;
            haloDetachedX = attachedHaloX();
            haloDetachedY = attachedHaloY();
            haloDashTargetY = haloDetachedY + HALO_DASH_DISTANCE;
        } else if (!haloDashing && !haloReturning) {
            haloReturning = true;
        }
    }

    /** ThunderboltWeapon's Hyper Attack (see ThunderboltWeapon.hyperAttack): launches the halo out
     *  in front of the ship, detached, where it hovers - tracking the ship, unlike Basic's dash,
     *  which rests wherever it lands - until this is called again. Ignored while the halo's
     *  already out on either ability's business (mirrors triggerBasicHyperAttack's own re-press
     *  guard) - Thunderbolt has no "press again" step of its own, since releasing the button (see
     *  updateThunderboltCharge()) is what detonates and recalls it instead. */
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

    /** Advances the Thunderbolt bomb's charge tier while the halo is holding position in front of
     *  the ship (see updateHaloMovement()'s thunderboltMoving branch, which flips thunderboltCharging
     *  on once it arrives), playing thunderbolthyperlevel.wav/-001/-002/-003 in order as it climbs
     *  through each tier - including the base tier the instant the attack starts, not just the
     *  three tiers above it - and, on release, queues the actual detonation for
     *  CollisionManager.checkThunderboltDetonation to apply next - this method only decides
     *  where/how hard, not who it hits, since that needs the enemies list CollisionManager already
     *  has wired up. Also watches for a release during the brief move-out (a quick tap, released
     *  before the halo ever reaches its charge position) - the charge timer never started ticking
     *  in that case, so it still detonates, just at the base tier, rather than silently swallowing
     *  the release and leaving the halo charging forever with the button already let go. Snaps the
     *  halo straight back onto the player on release - unlike Basic's dash, which glides back over
     *  time (see haloReturning/updateHaloMovement) - since the detonation itself is the payoff
     *  moment; there's no reason to keep the player waiting on a return flight afterward. */
    private void updateThunderboltCharge(float delta, boolean hyperAttackJustReleased, AudioManager audio) {
        if (!thunderboltMoving && !thunderboltCharging) return;

        if (thunderboltCharging) {
            thunderboltChargeTimer += delta;
            thunderboltChargeLevel = Math.min((int) (thunderboltChargeTimer / THUNDERBOLT_CHARGE_LEVEL_TIME), THUNDERBOLT_CHARGE_DAMAGE.length - 1);
        }

        if (thunderboltChargeLevel != thunderboltChargeSoundLevel) {
            thunderboltChargeSoundLevel = thunderboltChargeLevel;
            audio.playThunderboltHyperLevel(thunderboltChargeLevel);
        }

        if (hyperAttackJustReleased) {
            thunderboltDetonationPending = true;
            thunderboltDetonationX = haloCenterX();
            thunderboltDetonationY = haloCenterY();
            thunderboltDetonationDamage = THUNDERBOLT_CHARGE_DAMAGE[thunderboltChargeLevel];

            thunderboltMoving = false;
            thunderboltCharging = false;
            haloDetached = false;
            thunderboltHaloActive = false;
        }
    }

    /** Fires BasicWeapon's pattern - split with whatever the player's own gun is firing (see
     *  handleShooting()) so the two firing points don't double the total bullet count - from
     *  wherever the halo currently is: dashing out, resting, or gliding back, on the weapon's own
     *  fire-rate cadence, for as long as it's detached, but only while the player is actually
     *  holding Shoot (mirrors handleShooting's own gating). The cooldown still accumulates in the
     *  background while not shooting, same as a normal weapon's, so it's ready to fire the instant
     *  Shoot is pressed again. Skipped entirely while the halo is out on Thunderbolt's business
     *  instead of Basic's - haloDetached alone doesn't say which ability sent it out there. */
    private void updateHaloFiring(float delta, boolean isShooting, Array<Weapon> bullets, AssetManager assets, AudioManager audio) {
        if (!haloDetached || thunderboltHaloActive) return;

        haloFireTimer += delta;
        if (isShooting && haloFireTimer > basicWeapon.getFireRate()) {
            haloFireTimer = 0f;
            basicWeapon.spawnHaloPortion(bullets, resolveTextureFor("BasicWeapon", assets), haloCenterX(), haloCenterY());
            basicWeapon.playFireSound(audio, basicWeapon.getLevel());
        }
    }

    private void updateHaloMovement(float delta) {
        if (!haloDetached) return;

        if (haloDashing) {
            float remaining = haloDashTargetY - haloDetachedY;
            float step = HALO_DASH_SPEED * delta;
            if (Math.abs(remaining) <= step) {
                haloDetachedY = haloDashTargetY;
                haloDashing = false;
            } else {
                haloDetachedY += step;
            }
            haloHitbox.set(haloCenterX(), haloCenterY(), Math.min(haloDrawWidth, haloDrawHeight) / 2f);
        } else if (thunderboltMoving) {
            // Chases a moving target (the ship keeps moving while this plays out) rather than a
            // fixed point - the gap is small and this only runs for the brief trip out, so it
            // converges close enough well before any real drift could accumulate.
            float targetX = attachedHaloX();
            float targetY = attachedHaloY() + THUNDERBOLT_HALO_FRONT_DISTANCE;
            float dx = targetX - haloDetachedX;
            float dy = targetY - haloDetachedY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float step = THUNDERBOLT_HALO_MOVE_SPEED * delta;
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
            // Rigidly tracks the ship's front while charging, rather than drifting toward it like
            // the move-in phase above - the ship's own per-frame movement is already smooth, so
            // snapping here doesn't introduce any visible jitter.
            haloDetachedX = attachedHaloX();
            haloDetachedY = attachedHaloY() + THUNDERBOLT_HALO_FRONT_DISTANCE;
        } else if (haloReturning) {
            float targetX = attachedHaloX();
            float targetY = attachedHaloY();
            float dx = targetX - haloDetachedX;
            float dy = targetY - haloDetachedY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float step = HALO_RETURN_SPEED * delta;
            if (dist <= Math.max(step, HALO_ARRIVE_EPSILON)) {
                haloDetached = false;
                haloReturning = false;
                thunderboltHaloActive = false;
            } else {
                haloDetachedX += dx / dist * step;
                haloDetachedY += dy / dist * step;
            }
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
            if (currentWeapon == basicWeapon && haloDetached) {
                // Split the pattern with the halo (see updateHaloFiring()) instead of doubling it.
                basicWeapon.spawnPlayerPortion(bullets, bulletTex, spawnPoint.x, spawnPoint.y);
            } else {
                currentWeapon.spawn(bullets, bulletTex, spawnPoint.x, spawnPoint.y, this, enemies, assets);
            }
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

    // The graze halo's hitbox - grazing enemy bullets, picking up weapon powerups, and collecting
    // point gems (see CollisionManager's checkGrazeCollisions/checkPlayerPowerupCollisions/
    // checkPlayerGemCollisions, all keyed on getGrazeHitbox()) - follows the halo itself, not the
    // player, whenever BasicWeapon's Hyper Attack has it detached.
    private void updateGrazeHitbox() {
        float radius = Math.min(sprite.getWidth(), sprite.getHeight()) * playerDef.haloHitboxSize;
        if (haloDetached) {
            grazeHitbox.set(haloCenterX(), haloCenterY(), radius);
        } else {
            grazeHitbox.set(getCenterX(), getCenterY(), radius);
        }
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

        // Halo drawn under the player sprite, centered on the player - unless BasicWeapon's Hyper
        // Attack has detached it, in which case it's wherever the dash/return motion currently
        // has it instead (see triggerBasicHyperAttack()/updateHaloMovement()).
        TextureRegion haloFrame = haloAnimation.getKeyFrame(haloAnimationTime);
        float drawHaloX = getHaloX();
        float drawHaloY = getHaloY();
        boolean grazeFlashing = grazeFlashTimer > 0;

        if (isInvincible) {
            boolean visible = ((int) (invincibilityTimer / BLINK_INTERVAL) % 2) == 0;
            float alpha = visible ? 1f : 0f;
            batch.setColor(grazeFlashing ? 0.3f : 1f, grazeFlashing ? 0.6f : 1f, 1f, alpha);
            batch.draw(haloFrame, drawHaloX, drawHaloY, haloDrawWidth, haloDrawHeight);
            batch.setColor(1f, 1f, 1f, 1f);
            sprite.setAlpha(visible ? 1f : 0f);
            sprite.draw(batch);
            sprite.setAlpha(1f);
        } else {
            batch.setColor(grazeFlashing ? 0.3f : 1f, grazeFlashing ? 0.6f : 1f, 1f, 1f);
            batch.draw(haloFrame, drawHaloX, drawHaloY, haloDrawWidth, haloDrawHeight);
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
        waveBlastWeapon.resetHyperAttackCooldown();
        orbitWeapon.resetShootTimer();
        orbitWeapon.resetShield();
        thunderboltWeapon.resetShootTimer();
        animationTime = 0;
        numBombs = 1;
        numLives = 6;
        isInvincible = false;
        isDead = false;
        deathTimer = 0f;
        grazePoints = 0f;
        reattachHaloImmediately();
    }

    /** Snaps the halo straight back onto the player, canceling whatever hyper attack ability
     *  currently has it detached (Basic's dash/return, or Thunderbolt's move-out/charge/return)
     *  instead of leaving it stranded mid-flight - used by reset() on a full game restart, and by
     *  startDeath() so losing a life doesn't otherwise require an extra Hyper Attack press just to
     *  recall a halo that was already out when the hit landed. */
    private void reattachHaloImmediately() {
        haloDetached = false;
        haloDashing = false;
        haloReturning = false;
        haloFireTimer = 0f;
        haloDashHitEnemies.clear();
        thunderboltHaloActive = false;
        thunderboltMoving = false;
        thunderboltCharging = false;
        thunderboltChargeTimer = 0f;
        thunderboltChargeLevel = 0;
        thunderboltChargeSoundLevel = 0;
        thunderboltDetonationPending = false;
    }

    public Circle getHitbox() { return hitbox; }
    public Circle getGrazeHitbox() { return grazeHitbox; }
    public Circle getShieldHitbox() { return shieldHitbox; }
    public boolean isHaloDashing() { return haloDashing; }
    public Circle getHaloHitbox() { return haloHitbox; }
    public boolean hasHaloDamaged(Enemy enemy) { return haloDashHitEnemies.contains(enemy, true); }
    public void markHaloDamaged(Enemy enemy) { haloDashHitEnemies.add(enemy); }
    public int getHaloDashDamage() { return HALO_DASH_DAMAGE; }
    public boolean hasPendingThunderboltDetonation() { return thunderboltDetonationPending; }
    public float getThunderboltDetonationX() { return thunderboltDetonationX; }
    public float getThunderboltDetonationY() { return thunderboltDetonationY; }
    public int getThunderboltDetonationDamage() { return thunderboltDetonationDamage; }
    public float getThunderboltBlastRadius() { return THUNDERBOLT_BLAST_RADIUS; }
    public void clearPendingThunderboltDetonation() { thunderboltDetonationPending = false; }
    // True from the moment the bomb launches out until it detonates (moving out or holding
    // position and charging) - i.e. for as long as a release would actually detonate it, since
    // the blast radius itself doesn't change with charge tier - see debug hitbox overlay
    // (Main.drawDebug), which uses this plus getHaloCenterX/Y/getThunderboltBlastRadius to show
    // where the bomb will go off.
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
    public TextureRegion getHaloFrame() { return haloAnimation.getKeyFrame(haloAnimationTime); }
    public float getHaloX() { return haloDetached ? haloDetachedX : attachedHaloX(); }
    public float getHaloY() { return haloDetached ? haloDetachedY : attachedHaloY(); }
    private float attachedHaloX() { return sprite.getX() + sprite.getWidth() / 2f - haloDrawWidth / 2f; }
    private float attachedHaloY() { return sprite.getY() + sprite.getHeight() / 2f - haloDrawHeight / 2f; }
    private float haloCenterX() { return haloDetachedX + haloDrawWidth / 2f; }
    private float haloCenterY() { return haloDetachedY + haloDrawHeight / 2f; }
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
