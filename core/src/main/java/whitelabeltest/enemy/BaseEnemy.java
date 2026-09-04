package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.FiringPattern;
import whitelabeltest.enemy.movementpatterns.MovementPattern;
import whitelabeltest.gamemanagers.audio.AudioManager;

public abstract class BaseEnemy implements Enemy {
    protected enum LifecycleState { ENTERING, ACTIVE, DYING }

    protected Sprite sprite;
    protected Rectangle rectangle;
    protected int health;
    protected int maxHealth;
    // See EnemyDefinition.healthRegenPerSecond - fractional regen accumulates here until it's
    // worth a whole point of health (health is an int), same idea as SpeedRamp's own accumulation.
    protected float healthRegenPerSecond = 0f;
    private float healthRegenAccumulator = 0f;
    protected Integer guaranteedPowerup;
    protected float worldWidth, worldHeight;
    protected boolean invertMovement; // Added field to store inversion state
    protected boolean rotateWithMovement = true;

    protected Animation<TextureRegion> animation;
    protected float animationTime = 0;

    protected Animation<TextureRegion> bulletAnimation;

    protected float damageFlashTimer = 0;
    protected final float flashDuration = 0.05f;

    protected MovementPattern movement;
    protected FiringPattern firing;

    // World Y=0 is the bottom edge of the play area and X spans [0, worldWidth] - any enemy whose
    // hitbox has reached the bottom, left, or right edge (a little inside the literal edge, so
    // each zone has some breathing room instead of only covering the exact boundary pixel) holds
    // its fire instead of shooting into or past the play area's boundary.
    private static final float CEASEFIRE_ZONE_Y = 1.5f;
    private static final float CEASEFIRE_ZONE_X = 0.5f;

    // Tracks whether a Defiant enemy (see Enemy.isDefiant()) has actually spawned a bullet yet -
    // detected generically (any firing pattern growing enemyBullets) rather than each
    // FiringPattern reporting it, so this works unmodified for every existing/future pattern.
    private boolean hasFiredOnce = false;

    // See Enemy.isPairResolved()/markPairResolved().
    private boolean pairResolved = false;
    // See Enemy.getPairGraceTimer()/setPairGraceTimer().
    private float pairGraceTimer = -1f;

    protected Animation<TextureRegion> spawnAnimation;
    protected float spawnDuration = 0.4f;
    protected Animation<TextureRegion> deathAnimation;
    protected float deathDuration = 0.4f;

    protected LifecycleState lifecycleState = LifecycleState.ACTIVE;
    protected float lifecycleTime = 0f;

    // True once this enemy's sprite has been within GenericEnemy.isOffScreen()'s own bounds at
    // least once since it last spawned - see that method's own doc on why the off-screen REMOVAL
    // check is gated on this instead of applying unconditionally from frame 1. Reset in
    // beginEntrance() (called once per real spawn, pooled reuse included), never anywhere mid-life.
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
            // A paired enemy (see getPairId()) that hasn't been confirmed dead yet - it crossed
            // zero, but CollisionManager.resolvePairedEnemyDeaths() is still giving its partner a
            // window to follow it down - holds here at the exact moment of the hit: lifecycleTime
            // stays frozen so the death animation doesn't play (and isDeathAnimationFinished()
            // can't fire early and get it removed from the world) before the pair is actually
            // resolved one way or the other. Once markPairResolved() confirms the kill (or
            // reviveFully() reverses it), this falls through to the normal flow below.
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
            applyGroundScroll(delta, groundScrollSpeed);
            if (lifecycleTime >= spawnDuration) {
                lifecycleState = LifecycleState.ACTIVE;
                lifecycleTime = 0f;
                sprite.setColor(1, 1, 1, 1);
            }
            return;
        }

        animationTime += delta;
        if (animation != null) {
            sprite.setRegion(animation.getKeyFrame(animationTime));
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
        applyGroundScroll(delta, groundScrollSpeed);

        if (healthRegenPerSecond > 0f && health < maxHealth) {
            healthRegenAccumulator += healthRegenPerSecond * delta;
            int wholePoints = (int) healthRegenAccumulator;
            if (wholePoints > 0) {
                health = Math.min(maxHealth, health + wholePoints);
                healthRegenAccumulator -= wholePoints;
            }
        }

        // Skipped (not just no-op fired) while paused, in the ceasefire zone, or "sealed" by the
        // graze halo overlapping this enemy's hitbox (see Enemy.isSealable()/EnemyDefinition.
        // sealable) - in every case so a firing pattern's internal cooldown timer stays frozen at
        // its pre-gate value instead of overshooting and unloading the instant firing resumes -
        // see EntityManager's firingPaused computation.
        boolean sealed = isSealable() && grazeHitbox.radius > 0f && Intersector.overlaps(grazeHitbox, rectangle);
        if (firing != null && !firingPaused && !isInCeasefireZone() && !sealed) {
            int bulletsBefore = enemyBullets.size;
            firing.update(delta, this, sprite, rectangle, enemyBullets, bulletAnimation, playerHitbox);
            if (!hasFiredOnce && enemyBullets.size > bulletsBefore) hasFiredOnce = true;
        }
    }

    @Override
    public void silenceFiring() {
        firing = null;
    }

    /** Dispatches whatever one-shot event a WaypointPath movement just queued on reaching a
     *  waypoint (see MovementPattern.consumeCue()/WaypointCue) - plays its sound (if any, and if
     *  audio is actually available - see Enemy.update()'s own doc) and swaps this enemy's live
     *  firing pattern (if the waypoint set changeWeaponSet) via resolveWeaponSet(), a plain field
     *  reassignment safe to do mid-flight since `firing` is already re-read fresh every frame (see
     *  silenceFiring() already doing exactly that). A no-op for every OTHER movement pattern, whose
     *  consumeCue() default returns null. */
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

    /** Resolves a "weapon set" name (see MovementPatternDef.weaponSet) to a live FiringPattern for
     *  THIS enemy - a no-op hook here since BaseEnemy has no EnemyDefinition of its own to resolve
     *  the name against; GenericEnemy (the only subclass with one) overrides this using its own
     *  def.weaponSets map. */
    protected FiringPattern resolveWeaponSet(String weaponSetName) { return null; }

    // Shifts a ground enemy (isGround()) down by the stage's current background scroll speed, on
    // top of whatever its own movement pattern already did this frame - same translate-then-sync
    // idiom every MovementPattern uses (see e.g. StraightMovement) - so it stays visually planted on
    // the scrolling terrain (a "Stationary" ground enemy scrolls down screen right along with the
    // ground instead of floating in a fixed screen position) instead of sliding relative to it.
    // No-op for every other enemy.
    //
    // Also hands the same dy to movement.applyGroundScroll() BEFORE translating the sprite - a
    // no-op default for the ordinary translate()-based patterns (StraightMovement, MoveToPointMovement,
    // etc.), whose own next update() call adds to wherever this translate just left the sprite, same
    // as always. WaypointPathMovement/SplineMovement instead SET the sprite's position outright from
    // their own spawn-anchored curve every update() - that overwrites this method's translate the very
    // next frame, before it ever reaches the screen, silently discarding the scroll instead of merely
    // delaying it (a ground enemy on either of those patterns visibly lagged the actual background by
    // however much it should have scrolled, with no waypoint/speed retuning able to fix a discard
    // baked into a different frame's overwrite). Their applyGroundScroll() override folds dy into
    // their OWN evaluated position instead, so it survives.
    private void applyGroundScroll(float delta, float groundScrollSpeed) {
        if (!isGround()) return;
        float dy = groundScrollSpeed * delta;
        if (movement != null) movement.applyGroundScroll(dy);
        sprite.translate(0f, dy);
        rectangle.setPosition(sprite.getX(), sprite.getY());
    }

    private void updateSpawnAnimation() {
        if (spawnAnimation != null) {
            sprite.setRegion(spawnAnimation.getKeyFrame(lifecycleTime, false));
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

    // Drawn in its own pass before any enemy sprite (see EntityManager.draw), so a shadow never
    // paints over another enemy's sprite when the two overlap.
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

        // Shadow points toward the center of the play area, as if lit from behind each enemy
        // outward from the edges: enemies near an edge cast a longer, more skewed shadow toward
        // the middle, while enemies near dead-center fall back to a straight-down offset.
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

    // True once the enemy's whole hitbox - not just some overlap with it - sits within the play
    // area, so an enemy sliding/dropping in from off-screen can't be shot before it's fully in
    // view (see takeDamage()).
    private boolean isFullyOnScreen() {
        return rectangle.x >= 0f && rectangle.x + rectangle.width <= worldWidth
            && rectangle.y >= 0f && rectangle.y + rectangle.height <= worldHeight;
    }

    // True while the enemy's hitbox has reached the bottom, left, or right edge of the play area
    // (see CEASEFIRE_ZONE_Y/CEASEFIRE_ZONE_X's firing gate in update()).
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
        return false;
    }

    @Override
    public void advanceFiringPattern() {
        if (firing != null) firing.advance();
    }

    // See Enemy.reviveFully()/isPairResolved()/markPairResolved().
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
    public void setInvertMovement(boolean invert) { // Implemented method from Enemy interface
        this.invertMovement = invert;
    }

    @Override
    public void reset() {
        animationTime = 0;
        damageFlashTimer = 0;
        guaranteedPowerup = null;
        invertMovement = false; // Reset on pool
        rotateWithMovement = true;
        hasFiredOnce = false;
        pairResolved = false;
        pairGraceTimer = -1f;
        healthRegenAccumulator = 0f;
        lifecycleState = LifecycleState.ACTIVE;
        lifecycleTime = 0f;
        if (sprite != null) {
            sprite.setRotation(0);
            sprite.setColor(1, 1, 1, 1);
        }
        if (movement != null) movement.reset();
        if (firing != null) firing.reset();
    }
}
