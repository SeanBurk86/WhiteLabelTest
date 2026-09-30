package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.FiringPattern;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.gamemanagers.audio.AudioManager;

/** Shared enemy behavior: lifecycle (entering -> active -> dying), animation, movement, firing
 *  gates, damage, health regen, health phases, ground scroll, facing and drop shadow. */
public abstract class BaseEnemy implements Enemy {
    protected enum LifecycleState { ENTERING, ACTIVE, DYING }

    protected Sprite sprite;
    protected Rectangle rectangle;
    protected int health;
    protected int maxHealth;
    // Fractional regen accumulates until it's worth a whole health point.
    protected float healthRegenPerSecond = 0f;
    private float healthRegenAccumulator = 0f;
    protected Integer guaranteedPowerup;
    protected float worldWidth, worldHeight;
    protected boolean invertMovement;
    protected boolean rotateWithMovement = true;
    // Overrides movement rotation every frame to face the player.
    protected boolean facePlayer = false;

    protected Animation<TextureRegion> animation;
    protected float animationTime = 0;

    // World units per source pixel for uniformPixelScale, or NaN to draw frames into the sprite box.
    protected float unitsPerPixel = Float.NaN;

    // Direction flipping; lastCenterX is NaN until the first movement update after a spawn.
    protected boolean flipWithDirection = false;
    private boolean facingRight = false;
    private float lastCenterX = Float.NaN;

    protected Animation<TextureRegion> bulletAnimation;

    protected float damageFlashTimer = 0;
    protected final float flashDuration = 0.05f;

    protected MovementPattern movement;
    protected FiringPattern firing;

    // An enemy whose hitbox is within this distance of the bottom, left or right edge holds fire.
    private static final float CEASEFIRE_ZONE_Y = 1.5f;
    private static final float CEASEFIRE_ZONE_X = 0.5f;

    // Sorted highest threshold first; nextHealthPhase only moves forward. Null = no phases.
    private Array<HealthPhase> healthPhases;
    private int nextHealthPhase = 0;

    // Whether it has fired yet (for defiant enemies), detected by enemyBullets growing.
    private boolean hasFiredOnce = false;

    private boolean pairResolved = false;
    private float pairGraceTimer = -1f;

    protected Animation<TextureRegion> spawnAnimation;
    protected float spawnDuration = 0.4f;
    protected Animation<TextureRegion> deathAnimation;
    protected float deathDuration = 0.4f;

    protected LifecycleState lifecycleState = LifecycleState.ACTIVE;
    protected float lifecycleTime = 0f;

    // Set once the sprite has been on screen since spawning; off-screen removal waits for it.
    protected boolean hasBeenOnScreen = false;

    public BaseEnemy() {
        this.rectangle = new Rectangle();
    }

    @Override
    public void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.invertMovement = false;
    }

    protected void beginEntrance() {
        lifecycleTime = 0f;
        hasBeenOnScreen = false;
        if (spawnDuration > 0f) {
            lifecycleState = LifecycleState.ENTERING;
            if (sprite != null) sprite.setColor(1, 1, 1, spawnAnimation != null ? 1f : 0f);
        } else {
            lifecycleState = LifecycleState.ACTIVE;
            if (sprite != null) sprite.setColor(1, 1, 1, 1);
        }
    }

    protected void startDeath() {
        lifecycleState = LifecycleState.DYING;
        lifecycleTime = 0f;
        if (sprite != null) sprite.setColor(1, 1, 1, 1);
    }

    @Override
    public boolean isActive() { return lifecycleState == LifecycleState.ACTIVE; }

    @Override
    public boolean isDying() { return lifecycleState == LifecycleState.DYING; }

    @Override
    public void update(float delta, Array<EnemyBullet> enemyBullets, Circle playerHitbox, Circle grazeHitbox, boolean firingPaused, float groundScrollSpeed, AudioManager audio) {
        if (sprite == null) return;

        if (lifecycleState == LifecycleState.DYING) {
            // An unresolved paired death holds here, death animation frozen, until the pair is
            // confirmed dead or revived.
            if (getPairId() != null && !isPairResolved()) return;
            lifecycleTime += delta;
            updateDeathAnimation();
            return;
        }

        lifecycleTime += delta;

        if (lifecycleState == LifecycleState.ENTERING) {
            updateSpawnAnimation();
            if (movement != null) {
                movement.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, invertMovement);
                if (!rotateWithMovement) sprite.setRotation(0);
                resolveMovementCue(audio);
            }
            if (facePlayer) applyFacePlayer(playerHitbox);
            applyGroundScroll(delta, groundScrollSpeed);
            updateFacing();
            if (lifecycleTime >= spawnDuration) {
                lifecycleState = LifecycleState.ACTIVE;
                lifecycleTime = 0f;
                sprite.setColor(1, 1, 1, 1);
            }
            return;
        }

        animationTime += delta;
        if (animation != null) {
            applyFrame(animation.getKeyFrame(animationTime));
        }

        if (damageFlashTimer > 0) {
            damageFlashTimer -= delta;
            sprite.setColor(1, 0, 0, 1);
        } else {
            sprite.setColor(1, 1, 1, 1);
        }

        if (movement != null) {
            movement.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, invertMovement);
            if (!rotateWithMovement) sprite.setRotation(0);
            resolveMovementCue(audio);
        }
        if (facePlayer) applyFacePlayer(playerHitbox);
        applyGroundScroll(delta, groundScrollSpeed);
        updateFacing();

        if (healthRegenPerSecond > 0f && health < maxHealth) {
            healthRegenAccumulator += healthRegenPerSecond * delta;
            int wholePoints = (int) healthRegenAccumulator;
            if (wholePoints > 0) {
                health = Math.min(maxHealth, health + wholePoints);
                healthRegenAccumulator -= wholePoints;
            }
        }

        // Firing isn't updated at all while paused, in a ceasefire zone, or sealed by the graze halo,
        // so the pattern's cooldown stays frozen instead of unloading a backlog when firing resumes.
        boolean sealed = isSealable() && grazeHitbox.radius > 0f && Intersector.overlaps(grazeHitbox, rectangle);
        if (firing != null && !firingPaused && (ignoresCeasefireZone() || !isInCeasefireZone()) && !sealed) {
            int bulletsBefore = enemyBullets.size;
            firing.update(delta, this, sprite, rectangle, enemyBullets, bulletAnimation, playerHitbox);
            if (!hasFiredOnce && enemyBullets.size > bulletsBefore) hasFiredOnce = true;
        }
    }

    @Override
    public void silenceFiring() {
        firing = null;
    }

    /** Handles a waypoint event queued by the movement: plays its sound and switches weapon set. */
    private void resolveMovementCue(AudioManager audio) {
        MovementPattern.WaypointCue cue = movement.consumeCue();
        if (cue == null) return;
        if (cue.soundName != null && audio != null) {
            audio.playCueSound(cue.soundName, cue.soundVolume, cue.soundPitch);
        }
        if (cue.changeWeaponSet) {
            FiringPattern resolved = resolveWeaponSet(cue.weaponSet);
            if (resolved != null) firing = resolved;
        }
    }

    /** Resolves a weapon set name to a FiringPattern (overridden by GenericEnemy). */
    protected FiringPattern resolveWeaponSet(String weaponSetName) { return null; }

    // Points the sprite at the player (art faces up at rotation 0, like aimed bullets).
    private void applyFacePlayer(Circle playerHitbox) {
        float dx = playerHitbox.x - (sprite.getX() + sprite.getWidth() / 2f);
        float dy = playerHitbox.y - (sprite.getY() + sprite.getHeight() / 2f);
        sprite.setRotation(MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 90f);
    }

    // Moves a ground enemy with the scrolling terrain, on top of its own movement. The movement is
    // told first: curve-based patterns (WaypointPath, Spline) set the position outright each frame,
    // so they must fold the scroll into their own position or it would be overwritten.
    private void applyGroundScroll(float delta, float groundScrollSpeed) {
        if (!isGround()) return;
        float dy = groundScrollSpeed * delta;
        if (movement != null) movement.applyGroundScroll(dy);
        sprite.translate(0f, dy);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    /** Shows `frame`; with uniformPixelScale, resizes the sprite (and hitbox) around its center to
     *  the frame's pixel size. */
    protected void applyFrame(TextureRegion frame) {
        sprite.setRegion(frame);
        if (Float.isNaN(unitsPerPixel)) return;
        float width = frame.getRegionWidth() * unitsPerPixel;
        float height = frame.getRegionHeight() * unitsPerPixel;
        if (width == sprite.getWidth() && height == sprite.getHeight()) return;
        float centerX = sprite.getX() + sprite.getWidth() / 2f;
        float centerY = sprite.getY() + sprite.getHeight() / 2f;
        sprite.setSize(width, height);
        sprite.setOriginCenter();
        sprite.setCenter(centerX, centerY);
        rectangle.set(sprite.getX(), sprite.getY(), width, height);
    }

    /** Mirrors the sprite to face its horizontal movement (keeping the last facing when still).
     *  Re-applied every frame because setRegion() resets the flip. */
    private void updateFacing() {
        float centerX = sprite.getX() + sprite.getWidth() / 2f;
        if (flipWithDirection && !Float.isNaN(lastCenterX)) {
            float dx = centerX - lastCenterX;
            if (dx > 0.0001f) facingRight = true;
            else if (dx < -0.0001f) facingRight = false;
        }
        lastCenterX = centerX;
        // Leave the flip alone for enemies that never use direction flipping.
        if (flipWithDirection || facingRight) sprite.setFlip(flipWithDirection && facingRight, false);
    }

    private void updateSpawnAnimation() {
        if (spawnAnimation != null) {
            applyFrame(spawnAnimation.getKeyFrame(lifecycleTime, false));
        } else {
            float t = Math.min(1f, lifecycleTime / spawnDuration);
            sprite.setColor(1, 1, 1, t);
        }
    }

    private void updateDeathAnimation() {
        if (deathAnimation != null) {
            sprite.setRegion(deathAnimation.getKeyFrame(lifecycleTime, false));
        } else {
            sprite.setColor(1, 1, 1, 0);
        }
    }

    /** True once the death animation (dedicated or fallback fade) has finished playing. */
    protected boolean isDeathAnimationFinished() {
        return deathAnimation != null ? deathAnimation.isAnimationFinished(lifecycleTime) : lifecycleTime >= deathDuration;
    }

    private static final float SHADOW_OFFSET_FACTOR = 1.25f;
    private static final float SHADOW_SCALE = 0.66f;
    private static final float SHADOW_ALPHA = 0.75f;

    @Override
    public void draw(SpriteBatch batch) {
        if (sprite != null && !isOffScreen()) {
            sprite.draw(batch);
        }
    }

    // Drawn in a pass before all enemy sprites so shadows never cover another enemy.
    @Override
    public void drawShadow(SpriteBatch batch) {
        if (sprite != null && !isOffScreen() && !isGround()) {
            drawDropShadow(batch);
        }
    }

    private void drawDropShadow(SpriteBatch batch) {
        Color color = sprite.getColor();
        float r = color.r, g = color.g, b = color.b, a = color.a;
        if (a <= 0f) return;

        // The shadow falls toward the center of the play area (straight down at dead center).
        float magnitude = sprite.getWidth() * SHADOW_OFFSET_FACTOR;
        float dx = worldWidth / 2f - (sprite.getX() + sprite.getWidth() / 2f);
        float dy = worldHeight / 2f - (sprite.getY() + sprite.getHeight() / 2f);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float offsetX, offsetY;
        if (dist > 0.0001f) {
            offsetX = (dx / dist) * magnitude;
            offsetY = (dy / dist) * magnitude;
        } else {
            offsetX = 0f;
            offsetY = -magnitude;
        }
        float scaleX = sprite.getScaleX();
        float scaleY = sprite.getScaleY();

        sprite.translate(offsetX, offsetY);
        sprite.setScale(scaleX * SHADOW_SCALE, scaleY * SHADOW_SCALE);
        sprite.setColor(0f, 0f, 0f, a * SHADOW_ALPHA);
        sprite.draw(batch);
        sprite.translate(-offsetX, -offsetY);
        sprite.setScale(scaleX, scaleY);
        sprite.setColor(r, g, b, a);
    }

    @Override
    public Rectangle getRectangle() {
        return rectangle;
    }

    @Override
    public float getRotation() {
        return sprite.getRotation();
    }

    // Enemies can't be damaged until their whole box is inside the play area.
    private boolean isFullyOnScreen() {
        return rectangle.x >= 0f && rectangle.x + rectangle.width <= worldWidth
            && rectangle.y >= 0f && rectangle.y + rectangle.height <= worldHeight;
    }

    // Within the bottom/left/right ceasefire zones.
    private boolean isInCeasefireZone() {
        return rectangle.y <= CEASEFIRE_ZONE_Y
            || rectangle.x <= CEASEFIRE_ZONE_X
            || rectangle.x + rectangle.width >= worldWidth - CEASEFIRE_ZONE_X;
    }

    @Override
    public int getHealth() {
        return health;
    }

    @Override
    public int getMaxHealth() {
        return maxHealth;
    }

    @Override
    public boolean takeDamage(int amount) {
        if (lifecycleState != LifecycleState.ACTIVE) return false; // invulnerable while entering/already dying
        if (!isFullyOnScreen()) return false; // invulnerable until its whole sprite has entered the play area
        if (isDefiant() && !hasFiredOnce) return false; // defiant: invulnerable until it's fired at least once
        health -= amount;
        damageFlashTimer = flashDuration;
        if (health <= 0) {
            startDeath();
            return true;
        }
        advanceHealthPhases();
        return false;
    }

    @Override
    public void setHealthPhases(Array<HealthPhase> phases) {
        nextHealthPhase = 0;
        if (phases == null || phases.size == 0) {
            healthPhases = null;
            return;
        }
        healthPhases = new Array<>(phases);
        healthPhases.sort((a, b) -> Float.compare(b.healthPercent, a.healthPercent));
    }

    /** Enters every phase whose threshold has been reached, highest first. Compared as
     *  health*100 <= maxHealth*percent to avoid truncating the threshold. */
    private void advanceHealthPhases() {
        if (healthPhases == null || maxHealth <= 0) return;
        while (nextHealthPhase < healthPhases.size
                && health * 100f <= maxHealth * healthPhases.get(nextHealthPhase).healthPercent) {
            enterHealthPhase(healthPhases.get(nextHealthPhase));
            nextHealthPhase++;
        }
    }

    /** Applies a phase. An unknown or blank id keeps the current pattern rather than stopping it. */
    private void enterHealthPhase(HealthPhase phase) {
        if (phase.movementPattern != null && !phase.movementPattern.isBlank()) {
            MovementPattern resolved = resolveMovementPattern(phase.movementPattern);
            if (resolved != null) movement = resolved;
        }
        if (phase.firingPattern != null && !phase.firingPattern.isBlank()) {
            FiringPattern resolved = resolveFiringPattern(phase.firingPattern);
            if (resolved != null) firing = resolved;
        }
        if (phase.animation != null && !phase.animation.isBlank()) {
            Animation<TextureRegion> resolved = resolveAnimation(phase.animation);
            if (resolved != null) {
                animation = resolved;
                animationTime = 0f;
            }
        }
        if (phase.flipWithDirection != null) flipWithDirection = phase.flipWithDirection;
    }

    // Hooks overridden by GenericEnemy (which has the definition); null = no swap.
    protected Animation<TextureRegion> resolveAnimation(String animationName) { return null; }

    protected MovementPattern resolveMovementPattern(String movementPatternId) { return null; }

    protected FiringPattern resolveFiringPattern(String firingPatternId) { return null; }

    @Override
    public void advanceFiringPattern() {
        if (firing != null) firing.advance();
    }

    @Override
    public void reviveFully() {
        health = maxHealth;
        healthRegenAccumulator = 0f;
        lifecycleState = LifecycleState.ACTIVE;
        lifecycleTime = 0f;
        damageFlashTimer = 0f;
        pairResolved = false;
        pairGraceTimer = -1f;
        if (sprite != null) sprite.setColor(1, 1, 1, 1);
    }

    @Override
    public boolean isPairResolved() { return pairResolved; }

    @Override
    public void markPairResolved() { pairResolved = true; }

    @Override
    public float getPairGraceTimer() { return pairGraceTimer; }

    @Override
    public void setPairGraceTimer(float secondsRemaining) { pairGraceTimer = secondsRemaining; }

    @Override
    public void setGuaranteedPowerup(Integer tier) {
        this.guaranteedPowerup = tier;
    }

    @Override
    public Integer getGuaranteedPowerup() {
        return guaranteedPowerup;
    }

    @Override
    public void setInvertMovement(boolean invert) {
        this.invertMovement = invert;
    }

    private String spawnGroup;

    @Override
    public String getSpawnGroup() { return spawnGroup; }

    @Override
    public void setSpawnGroup(String group) { this.spawnGroup = group; }

    @Override
    public void reset() {
        animationTime = 0;
        spawnGroup = null;
        damageFlashTimer = 0;
        guaranteedPowerup = null;
        invertMovement = false;
        rotateWithMovement = true;
        facePlayer = false;
        unitsPerPixel = Float.NaN;
        flipWithDirection = false;
        facingRight = false;
        lastCenterX = Float.NaN;
        hasFiredOnce = false;
        pairResolved = false;
        pairGraceTimer = -1f;
        healthRegenAccumulator = 0f;
        healthPhases = null;
        nextHealthPhase = 0;
        lifecycleState = LifecycleState.ACTIVE;
        lifecycleTime = 0f;
        if (sprite != null) {
            sprite.setRotation(0);
            sprite.setColor(1, 1, 1, 1);
            sprite.setFlip(false, false);
        }
        if (movement != null) movement.reset();
        if (firing != null) firing.reset();
    }
}
