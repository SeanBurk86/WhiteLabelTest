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

    // The level resetWeaponsOnDeath() just floored both weapons down from, so the restore powerup
    // GameController.applyPlayerHit() spawns at the death spot can be sized to add it back - see
    // getDeathRestoreLevel().
    private int deathRestoreLevel;

    private final Animation<TextureRegion> animation;
    private float animationTime = 0;

    private final Animation<TextureRegion> deathAnimation;
    private final float deathDrawWidth, deathDrawHeight;

    private final Animation<TextureRegion> haloAnimation;
    private final float haloDrawWidth, haloDrawHeight;
    private float haloAnimationTime = 0;

    // BasicWeapon's Hyper Attack (see triggerBasicHyperAttack()): the halo swaps to this sprite
    // for as long as it's detached out on Basic's business - not Thunderbolt's, which uses its own
    // sprites instead (see resolveHaloVisual()).
    private final Animation<TextureRegion> basicHaloDetachAnimation;
    private final float basicHaloDetachDrawWidth, basicHaloDetachDrawHeight;

    // BasicWeapon's Hyper Attack (see BasicWeapon.hyperAttack/triggerBasicHyperAttack): the halo
    // launches forward a short distance, dealing damage to anything it clips along the way, then
    // rests there - detached from the player, firing BasicWeapon's own stream on its own cadence
    // for as long as it's detached - until Hyper Attack is pressed again, at which point it glides
    // back to wherever the player currently is instead of snapping there.
    private static final float HALO_DASH_DISTANCE = 4.2f;
    private static final float HALO_DASH_SPEED = 14f;
    private static final float HALO_RETURN_SPEED = 6f;
    // Basic's own re-press reattaches noticeably snappier than a fizzled recall (switching weapons
    // away mid-flight - see recallHaloOnWeaponSwitch()) or Thunderbolt's return leg glides back at.
    private static final float HALO_FAST_RETURN_SPEED = 14f;
    private static final float HALO_ARRIVE_EPSILON = 0.05f;
    private static final int HALO_DASH_DAMAGE = 30;

    private boolean haloDetached;
    private boolean haloDashing;
    private boolean haloReturning;
    private boolean haloFastReturn;
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
    // Blast radius grows with charge tier, same as damage does - level 4 (index 3) is the full
    // radius the detonation has always used; levels 1-3 are smaller fractions of it.
    private static final float THUNDERBOLT_BLAST_RADIUS = 3.5f;
    private static final float[] THUNDERBOLT_BLAST_RADII = {
        THUNDERBOLT_BLAST_RADIUS * 0.25f,
        THUNDERBOLT_BLAST_RADIUS * 0.5f,
        THUNDERBOLT_BLAST_RADIUS * 0.75f,
        THUNDERBOLT_BLAST_RADIUS,
    };
    // One animation per charge tier (ThunderHyperHaloShrink1-4.png, indexed by thunderboltChargeLevel)
    // shown on the halo while it's out charging, plus a one-shot ThunderHaloBomb.png played in place
    // once released - see resolveHaloVisual()/updateThunderboltDetonationAnim().
    private final Animation<TextureRegion>[] thunderShrinkAnimations;
    private final float[] thunderShrinkDrawWidth, thunderShrinkDrawHeight;
    private final Animation<TextureRegion> thunderHaloBombAnimation;
    // player.json's thunderHaloBomb.size is the sprite's size at the top charge tier (full
    // THUNDERBOLT_BLAST_RADIUS) - resolveHaloVisual() scales it down by thunderboltDetonationVisualScale
    // for lower tiers, so the drawn explosion always matches how big the actual blast was.
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
    // thunderboltDetonationRadius expressed as a fraction of THUNDERBOLT_BLAST_RADIUS - how much to
    // scale thunderHaloBombDrawWidth/Height down by so the explosion sprite matches this particular
    // detonation's actual (smaller-if-not-fully-charged) blast size - see resolveHaloVisual().
    private float thunderboltDetonationVisualScale = 1f;
    // True from the moment the charge is released until the ThunderHaloBomb animation finishes
    // playing in place - see updateThunderboltDetonationAnim(). The area damage itself already
    // applied the instant the charge was released (see CollisionManager.checkThunderboltDetonation);
    // this only delays the halo's snap-back to the player so the explosion has a visual.
    private boolean thunderboltDetonating;
    private float thunderboltDetonationAnimTime;
    // Set when Hyper Attack is pressed while thunderboltDetonating is still true (see
    // handleHyperAttack()) - consumed by updateThunderboltDetonationAnim() the moment the halo
    // reattaches, immediately starting Thunderbolt charging again instead of requiring a second,
    // separately-timed press.
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

    // BasicWeapon's Hyper Attack dash (see CollisionManager.checkHaloDashCollisions): flashes the
    // halo red for a moment each time it lands a hit, the same way grazeFlashTimer flashes it blue
    // on a graze.
    private float haloBashFlashTimer;
    private static final float HALO_BASH_FLASH_DURATION = 0.15f;

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

    /** Builds a player.json-driven halo animation the same way haloAnimation/basicHaloDetachAnimation
     *  are built above, writing its {width, height} into dimsOut so callers can assign their own
     *  final fields from it. */
    private Animation<TextureRegion> buildHaloAnimation(Texture texture, PlayerDefinition.SpriteDef sprite, Animation.PlayMode mode, float[] dimsOut) {
        Animation<TextureRegion> anim = AnimationCache.get(texture, sprite.columns > 0 ? sprite.columns : sprite.frameCount,
            sprite.rows, sprite.frameCount, 1f / 24f, mode);
        TextureRegion[] frames = anim.getKeyFrames();
        float aspect = (float) frames[0].getRegionHeight() / frames[0].getRegionWidth();
        dimsOut[0] = sprite.size;
        dimsOut[1] = sprite.size * aspect;
        return anim;
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
        if (haloBashFlashTimer > 0) haloBashFlashTimer -= delta;
        sprite.setRegion(animation.getKeyFrame(animationTime));

        if (input.isWeaponSwitchJustPressed()) {
            switchActiveSlot();
            recallHaloOnWeaponSwitch(audio);
        }

        handleMovement(delta, input.getMoveDirection(), input.isShooting());
        handleShooting(delta, input.isShooting(), assets, audio, bullets, enemies);
        maintainOrbitRing(bullets, assets, input.isShooting());
        handleHyperAttack(input.isHyperAttackJustPressed(), bullets, enemies, assets, audio);
        updateThunderboltCharge(delta, input.isHyperAttackJustReleased(), audio);
        updateThunderboltDetonationAnim(delta, input.isHyperAttackHeld(), audio);
        updateHaloMovement(delta, audio);
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
    // A press that lands specifically while the ThunderHaloBomb animation is still playing out is
    // buffered instead of dropped, so Thunderbolt immediately starts charging again the instant the
    // halo reattaches - see updateThunderboltDetonationAnim().
    private void handleHyperAttack(boolean hyperAttackJustPressed, Array<Weapon> bullets, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {
        if (!hyperAttackJustPressed) return;
        if (thunderboltDetonating) {
            thunderboltHyperAttackBuffered = true;
            return;
        }
        Weapon currentWeapon = getCurrentWeapon();
        // Briefly true right after a death wipes both weapon slots (see resetWeaponsOnDeath()),
        // until the restore powerup re-equips one - nothing to trigger a Hyper Attack with yet.
        if (currentWeapon == null) return;
        if (haloDetached && currentWeapon != basicWeapon) return;
        currentWeapon.hyperAttack(this, bullets, enemies, assets, audio);
    }

    /** Switching weapons recalls a still-detached halo immediately, interrupting an in-progress
     *  dash if needed, instead of leaving it stranded away from the player while a different
     *  weapon is equipped. No-op once it's already heading back, or while the ThunderHaloBomb
     *  animation is playing out - see updateThunderboltDetonationAnim(), which reattaches it on
     *  its own moments later regardless. */
    private void recallHaloOnWeaponSwitch(AudioManager audio) {
        if (!haloDetached || haloReturning || thunderboltDetonating) return;
        haloDashing = false;
        // Cancels a mid-flight Thunderbolt charge without detonating it - switching away is
        // treated as a fizzle, not a release.
        thunderboltMoving = false;
        thunderboltCharging = false;
        haloReturning = true;
        haloFastReturn = !thunderboltHaloActive;
        audio.playHaloReturn();
    }

    /** BasicWeapon's Hyper Attack (see BasicWeapon.hyperAttack), toggled by each press: while
     *  attached, launches the halo forward a short distance - see updateHaloMovement() for the
     *  damage dealt along the way - where it then rests, detached, until this is called again,
     *  which starts it gliding back to the player instead of snapping there. Ignored mid-launch
     *  or mid-return so a rapid second press can't restart either motion. */
    public void triggerBasicHyperAttack(AudioManager audio) {
        if (!haloDetached) {
            haloDetached = true;
            haloDashing = true;
            haloReturning = false;
            haloDashHitEnemies.clear();
            haloFireTimer = 0f;
            haloDetachedX = attachedHaloX();
            haloDetachedY = attachedHaloY();
            haloDashTargetY = haloDetachedY + HALO_DASH_DISTANCE;
            audio.playHaloDetach();
        } else if (!haloDashing && !haloReturning) {
            haloReturning = true;
            haloFastReturn = true;
            audio.playHaloReturn();
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
     *  the release and leaving the halo charging forever with the button already let go. Starts the
     *  ThunderHaloBomb animation in place on release, rather than snapping the halo straight back
     *  onto the player - see updateThunderboltDetonationAnim(), which handles the actual snap-back
     *  once that animation finishes. The area damage itself still applies instantly, on release -
     *  only the halo's visual return is delayed for the explosion. */
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
            thunderboltDetonationRadius = THUNDERBOLT_BLAST_RADII[thunderboltChargeLevel];
            thunderboltDetonationVisualScale = thunderboltDetonationRadius / THUNDERBOLT_BLAST_RADIUS;

            thunderboltMoving = false;
            thunderboltCharging = false;
            thunderboltDetonating = true;
            thunderboltDetonationAnimTime = 0f;
        }
    }

    /** Plays the ThunderHaloBomb animation once, in place at wherever the halo detonated, before
     *  finally reattaching it to the player - see updateThunderboltCharge()'s release branch, which
     *  starts this instead of reattaching immediately. If Hyper Attack was pressed while this was
     *  still playing (see handleHyperAttack()'s thunderboltHyperAttackBuffered branch), immediately
     *  starts Thunderbolt charging again the instant it reattaches - unless the player switched off
     *  Thunderbolt in the meantime, in which case the buffered press is just dropped. That buffered
     *  press was a single tap already completed (pressed and released) before this replay fires, so
     *  if the button isn't still held right now, there's no future release edge left for
     *  updateThunderboltCharge() to catch - detonating immediately at the base tier here instead
     *  (same as its own "quick tap" handling) avoids leaving the bomb charging forever with nothing
     *  left to end it. */
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

    private void updateHaloMovement(float delta, AudioManager audio) {
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
            float step = (haloFastReturn ? HALO_FAST_RETURN_SPEED : HALO_RETURN_SPEED) * delta;
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

    // The weapon's focus-fire movement slowdown (getShootSpeedMultiplier()) also applies for as
    // long as a Hyper Attack has the halo detached - Basic's dash/rest/return or Thunderbolt's
    // move-out/charge - not just while actually holding Shoot, so aiming the halo's dash/charge
    // position gets the same precision movement firing does. Excludes thunderboltDetonating: the
    // area damage already applied the instant the charge was released (see
    // updateThunderboltCharge()), and there's nothing left to aim once the halo's just replaying
    // its explosion animation in place before reattaching, so movement speed snaps back to normal
    // immediately on detonation instead of staying slowed until the animation finishes.
    private void handleMovement(float delta, Vector2 moveDirection, boolean isShooting) {
        // No weapon at all briefly after a death wipe (see resetWeaponsOnDeath()) falls back to
        // full movement speed rather than dereferencing a null current weapon.
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
        advanceWeaponTimers(delta);

        Weapon currentWeapon = getCurrentWeapon();
        // No weapon at all briefly after a death wipe - see resetWeaponsOnDeath().
        if (currentWeapon == null) return;
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

        // Halo drawn under the player sprite, centered on the player - unless a Hyper Attack has
        // detached it, in which case it's wherever that ability's motion currently has it instead,
        // and it swaps to that ability's own sprite - see resolveHaloVisual().
        HaloVisual haloVisual = resolveHaloVisual();
        TextureRegion haloFrame = haloVisual.frame;
        float haloDrawW = haloVisual.width;
        float haloDrawH = haloVisual.height;
        float drawHaloX = haloDrawX(haloVisual);
        float drawHaloY = haloDrawY(haloVisual);
        boolean grazeFlashing = grazeFlashTimer > 0;
        boolean bashFlashing = haloBashFlashTimer > 0;
        // Bash-flash (red, on a Hyper Attack dash hit) takes priority over graze-flash (blue, on a
        // grazed bullet) if both happen to be active at once.
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

    /** Starts (or restarts) a run with the given starting loadout - see WeaponSelectScreen, which
     *  picks it before the run begins, and GameController, which holds onto it across debug
     *  restarts so those don't force a re-pick. Only the two chosen weapons start at level 1 and
     *  equipped; everything else (including WaveBlastWeapon, never a starting choice, and whichever
     *  of Basic/Thunderbolt/Orbit wasn't picked) starts at level 0 and unequipped, same as any
     *  weapon the player hasn't collected a powerup for yet. */
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
        numLives = 6;
        isInvincible = false;
        isDead = false;
        deathTimer = 0f;
        grazePoints = 0f;
        reattachHaloImmediately();
        setSlotWeapon(0, loadout.slotAWeaponId);
        setSlotWeapon(1, loadout.slotBWeaponId);
        deathRestoreLevel = 0;
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
    public int getHaloDashDamage() { return HALO_DASH_DAMAGE; }
    public boolean hasPendingThunderboltDetonation() { return thunderboltDetonationPending; }
    public float getThunderboltDetonationX() { return thunderboltDetonationX; }
    public float getThunderboltDetonationY() { return thunderboltDetonationY; }
    public int getThunderboltDetonationDamage() { return thunderboltDetonationDamage; }
    // The radius actually applied on detonation, captured at release time alongside the damage/X/Y
    // above - see CollisionManager.checkThunderboltDetonation.
    public float getThunderboltDetonationRadius() { return thunderboltDetonationRadius; }
    // The radius a release would detonate at *right now*, given the current charge tier - grows
    // with thunderboltChargeLevel the same way the damage does (see THUNDERBOLT_BLAST_RADII),
    // reaching THUNDERBOLT_BLAST_RADIUS only at the top tier. Used by the debug hitbox overlay
    // (Main.drawDebug) to preview where/how big the blast will be.
    public float getThunderboltBlastRadius() { return THUNDERBOLT_BLAST_RADII[thunderboltChargeLevel]; }
    public void clearPendingThunderboltDetonation() { thunderboltDetonationPending = false; }
    // True from the moment the bomb launches out until it detonates (moving out or holding
    // position and charging) - i.e. for as long as a release would actually detonate it - see
    // debug hitbox overlay (Main.drawDebug), which uses this plus
    // getHaloCenterX/Y/getThunderboltBlastRadius to show where the bomb will go off.
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
    // haloDetachedX/Y track the halo's logical center (via haloCenterX/Y below), not any particular
    // sprite's bottom-left corner - the different Hyper Attack sprites (basicHaloDetach, the
    // Thunderbolt shrink tiers, the bomb) all differ in size, so the actual draw-space bottom-left
    // has to be re-derived from whichever one is currently active, or it'll render off-center from
    // wherever the halo actually is (see resolveHaloVisual()).
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

    /** Picks which sprite the halo is currently wearing: the ThunderHaloBomb one-shot while its
     *  detonation animation plays, the charge-tier ThunderHyperHaloShrink while Thunderbolt's Hyper
     *  Attack has it out charging, basicHaloDetachAnimation while Basic's Hyper Attack has it out
     *  dashing/resting/returning, or the regular attached haloAnimation otherwise - both Hyper
     *  Attacks share the haloDetached flag itself (see triggerThunderboltHyperAttack()), so the
     *  more specific states have to be checked first. */
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

    /** A normal weapon powerup (see WeaponPowerup.apply()) levels up both currently equipped
     *  weapons at once by the same amount, instead of a single specific weapon - there's no more
     *  "collect this weapon's own powerup to equip/level it" path, so a slot left empty (only one
     *  weapon equipped) is simply skipped rather than being filled. */
    public void levelUpEquippedWeapons(int amount) {
        for (Weapon w : weaponSlots) {
            if (w != null) w.setLevel(Math.min(w.getLevel() + amount, MAX_WEAPON_LEVEL));
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
    }

    /** Dying strips both equipped weapons down to level 1 - still equipped, just back to their
     *  base level - capturing the level being lost first, so GameController.applyPlayerHit() can
     *  size a restore powerup to add it back (see getDeathRestoreLevel()). */
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
