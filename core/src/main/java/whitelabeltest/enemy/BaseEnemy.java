package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import whitelabeltest.enemy.bullets.EnemyBullet;
import whitelabeltest.enemy.firingpatterns.FiringPattern;
import whitelabeltest.enemy.movementpatterns.MovementPattern;

public abstract class BaseEnemy implements Enemy {
    protected enum LifecycleState { ENTERING, ACTIVE, DYING }

    protected Sprite sprite;
    protected Rectangle rectangle;
    protected int health;
    protected int maxHealth;
    protected String guaranteedPowerup;
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

    protected Animation<TextureRegion> spawnAnimation;
    protected float spawnDuration = 0.4f;
    protected Animation<TextureRegion> deathAnimation;
    protected float deathDuration = 0.4f;

    protected LifecycleState lifecycleState = LifecycleState.ACTIVE;
    protected float lifecycleTime = 0f;

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
    public void update(float delta, Array<EnemyBullet> enemyBullets, Circle playerHitbox, boolean firingPaused) {
        if (sprite == null) return;

        lifecycleTime += delta;

        if (lifecycleState == LifecycleState.DYING) {
            updateDeathAnimation();
            return;
        }

        if (lifecycleState == LifecycleState.ENTERING) {
            updateSpawnAnimation();
            if (movement != null) {
                movement.update(delta, sprite, rectangle, worldWidth, worldHeight, playerHitbox, invertMovement);
                if (!rotateWithMovement) sprite.setRotation(0);
            }
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
        }

        // Skipped (not just no-op fired) while paused, so a firing pattern's internal cooldown
        // timer stays frozen at its pre-pause value instead of overshooting and unloading the
        // instant firing resumes - see EntityManager's firingPaused computation.
        if (firing != null && !firingPaused) {
            firing.update(delta, this, sprite, rectangle, enemyBullets, bulletAnimation, playerHitbox);
        }
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
        health -= amount;
        damageFlashTimer = flashDuration;
        if (health <= 0) {
            startDeath();
            return true;
        }
        return false;
    }

    @Override
    public void setGuaranteedPowerup(String type) {
        this.guaranteedPowerup = type;
    }

    @Override
    public String getGuaranteedPowerup() {
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
