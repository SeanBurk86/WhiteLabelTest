package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import whitelabeltest.gamemanagers.effects.AnimationCache;
import whitelabeltest.gamemanagers.ObjectPools;
import whitelabeltest.gamemanagers.trigger.EnemyEntranceMovement;
import whitelabeltest.gamemanagers.trigger.Trigger;
import whitelabeltest.enemy.firingpatterns.FiringPattern;
import whitelabeltest.enemy.firingpatterns.SelfDestructFiring;
import whitelabeltest.enemy.movementpatterns.MovementPattern;

public class GenericEnemy extends BaseEnemy {

    private EnemyDefinition def;
    private Texture bulletTexture;
    private Texture spawnTexture;
    private Texture deathTexture;

    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, Float.NaN, Float.NaN, null);
    }

    /** @param formationOffsetX, formationOffsetY this spawn's slot in a squad formation, passed
     *  straight through to PatternFactory.createMovement - see its javadoc. NaN (the other
     *  overload above) means "not part of a formation spawned this way". */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, null);
    }

    /** @param movementPatternId this spawn's own movement pattern id - see Trigger.movementPattern's
     *  own doc. Movement isn't part of EnemyDefinition at all (unlike firingPatternId below, which
     *  DOES fall back to def.firingPattern - firing stayed type-level), so null here simply means
     *  this particular spawn doesn't move, same as any other unset trigger field. */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, movementPatternId, null);
    }

    /** @param firingPatternId overrides def.firingPattern when non-null - same reasoning as
     *  movementPatternId above, lets several spawn events share one enemy definition while each
     *  firing something different (e.g. WallFiring's per-wave gapCenterX) instead of needing a
     *  near-duplicate enemy definition that differs only in firingPattern. */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId, String firingPatternId) {
        initWithDefinition(def, texture, bulletTexture, spawnTexture, deathTexture, worldWidth, worldHeight, startX, startY, formationOffsetX, formationOffsetY, movementPatternId, firingPatternId, null, 0f);
    }

    /** @param entranceTrigger non-null (only ever from EnemySpawnOps.spawnEnemy(), i.e.
     *  TriggerManager.fire()) builds and applies EnemyEntranceMovement.build() as this spawn's
     *  movement, replacing whatever movementPatternId would otherwise have resolved to - see that
     *  method's own doc. Built HERE rather than by the caller because it needs the real spawn
     *  sprite's true size (set below, before this runs), which isn't known any earlier than this. */
    public void initWithDefinition(EnemyDefinition def, Texture texture, Texture bulletTexture,
                                    Texture spawnTexture, Texture deathTexture,
                                    float worldWidth, float worldHeight, float startX, float startY,
                                    float formationOffsetX, float formationOffsetY, String movementPatternId, String firingPatternId,
                                    Trigger entranceTrigger, float cameraSpeed) {
        this.def = def;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.bulletTexture = bulletTexture;
        this.spawnTexture = spawnTexture;
        this.deathTexture = deathTexture;
        this.invertMovement = def.inverseMovement;
        this.rotateWithMovement = def.rotateWithMovement;
        this.facePlayer = def.facePlayer;

        this.animation = AnimationCache.get(texture, def.columns > 0 ? def.columns : def.frameCount, def.rows, def.frameCount, def.frameDuration, Animation.PlayMode.LOOP);
        TextureRegion[] frames = animation.getKeyFrames();

        this.bulletAnimation = (bulletTexture != null)
            ? AnimationCache.get(bulletTexture, FiringPatternDef.DEFAULT_BULLET_COLUMNS > 0 ? FiringPatternDef.DEFAULT_BULLET_COLUMNS : FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT,
                FiringPatternDef.DEFAULT_BULLET_ROWS, FiringPatternDef.DEFAULT_BULLET_FRAME_COUNT, FiringPatternDef.DEFAULT_BULLET_FRAME_DURATION, Animation.PlayMode.LOOP)
            : null;

        if (sprite == null) sprite = new Sprite(frames[0]);
        else sprite.setRegion(frames[0]);

        float aspect = frames[0].getRegionWidth() / (float) frames[0].getRegionHeight();
        if (aspect >= 1f) {
            sprite.setSize(def.size, def.size / aspect);
        } else {
            sprite.setSize(def.size * aspect, def.size);
        }
        sprite.setOriginCenter();

        if (!Float.isNaN(startX)) {
            sprite.setX(startX);
        } else {
            sprite.setX(MathUtils.random(0.5f, worldWidth - (sprite.getWidth() + 0.5f)));
        }

        this.health = def.health;
        this.maxHealth = def.health;
        this.healthRegenPerSecond = def.healthRegenPerSecond;
        this.animationTime = 0;

        // No def-level fallback (see EnemyDefinition.java's own doc) - PatternRegistry.getMovement(null)
        // and PatternFactory.createMovement(null, ...) are both already null-safe, resolving to
        // NoMovement, so a spawn with no movementPatternId of its own simply doesn't move. Built
        // BEFORE startY below (this method's original order had it after) because the 4-arg
        // EnemyEntranceMovement.spawnY() overload needs to inspect THIS already-resolved pattern - see
        // that overload's own doc - not just trigger.y; movement itself never depends on the sprite's Y
        // (only X, already set above), so nothing here loses anything by building it first.
        this.movement = PatternFactory.createMovement(PatternRegistry.getMovement(movementPatternId), worldWidth, worldHeight, sprite.getX() + sprite.getWidth() / 2f, formationOffsetX, formationOffsetY);

        // See EnemyEntranceMovement.spawnY()'s own doc - overrides the caller-supplied startY with
        // the real off-screen spawn point ONLY now, because that computation needs this sprite's
        // actual height (just set above via sprite.setSize()) to stay safely under isOffScreen()'s
        // own removal tolerance - computing it any earlier (back when the caller only knew
        // trigger.enterFromAbove, not yet this sprite's true size) is what silently deleted these
        // spawns before they could visibly enter at all. Passing this.movement lets it also clear a
        // WaypointPathMovement's own first-leg target, not just trigger.y - see that overload's own
        // doc on why a plain trigger.y-only spawn point isn't always high enough.
        if (entranceTrigger != null && entranceTrigger.enterFromAbove && !Float.isNaN(entranceTrigger.y)) {
            startY = EnemyEntranceMovement.spawnY(entranceTrigger, worldHeight, sprite.getHeight(), this.movement);
        }

        if (!Float.isNaN(startY)) {
            sprite.setY(startY);
        } else {
            sprite.setY(worldHeight + 1.0f);
        }

        if (entranceTrigger != null) {
            MovementPattern entrance = EnemyEntranceMovement.build(entranceTrigger, cameraSpeed, worldHeight, sprite.getWidth(), sprite.getHeight(), this.movement);
            if (entrance != null) this.movement = entrance;
        }
        String resolvedFiringPattern = firingPatternId != null ? firingPatternId : def.firingPattern;
        this.firing = PatternFactory.createFiring(def, PatternRegistry.getFiring(resolvedFiringPattern), worldWidth, worldHeight);

        this.spawnDuration = def.spawnDuration;
        this.spawnAnimation = (spawnTexture != null && def.spawnFrameCount > 0)
            ? AnimationCache.get(spawnTexture, def.spawnColumns > 0 ? def.spawnColumns : def.spawnFrameCount, def.spawnRows, def.spawnFrameCount, 0.05f, Animation.PlayMode.NORMAL)
            : null;

        this.deathDuration = def.deathDuration;
        this.deathAnimation = (deathTexture != null && def.deathFrameCount > 0)
            ? AnimationCache.get(deathTexture, def.deathColumns > 0 ? def.deathColumns : def.deathFrameCount, def.deathRows, def.deathFrameCount, 0.05f, Animation.PlayMode.NORMAL)
            : null;

        rectangle.set(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());

        beginEntrance();
    }

    @Override
    public void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY) {
        initWithDefinition(null, texture, null, null, null, worldWidth, worldHeight, startX, startY);
    }

    /** The bounds check below (generous tolerance - spriteWidth/Height*2 past each edge, not the
     *  literal [0,worldWidth]x[0,worldHeight] play area) only actually REMOVES this enemy once
     *  hasBeenOnScreen is already true - i.e. once it's been WITHIN those bounds at least one frame
     *  since it last spawned (see BaseEnemy.beginEntrance(), which resets the flag). Before that,
     *  being outside the bounds never removes it, no matter how far outside or for how long -
     *  covering a spawn placed anywhere off-axis (a wave member spread wide in X, an anchor trigger
     *  authored off to one side, not just the vertical entrance the old margin math was originally
     *  sized for) that's meant to fly ONTO screen via its own movement/waypoint path rather than
     *  spawning already inside the tolerance. Once it's genuinely been seen, the ordinary rule
     *  applies again: wandering back out (by design, or by running off the bottom/top/either side)
     *  removes it exactly as before. */
    @Override
    public boolean isOffScreen() {
        if (lifecycleState == LifecycleState.DYING) return isDeathAnimationFinished();

        if (firing instanceof SelfDestructFiring && ((SelfDestructFiring)firing).isTriggered()) return true;
        if (movement != null && movement.isFinished()) return true;

        boolean outsideBounds = sprite.getY() < -sprite.getHeight() * 2f || sprite.getY() > worldHeight + sprite.getHeight() * 2f ||
               sprite.getX() + sprite.getWidth() < -sprite.getWidth() * 2f || sprite.getX() > worldWidth + sprite.getWidth() * 2f;
        if (!outsideBounds) {
            hasBeenOnScreen = true;
            return false;
        }
        return hasBeenOnScreen;
    }

    @Override
    public Enemy create(Texture texture, float worldWidth, float worldHeight) {
        GenericEnemy e = ObjectPools.genericEnemyPool.obtain();
        e.initWithDefinition(this.def, texture, this.bulletTexture, this.spawnTexture, this.deathTexture, worldWidth, worldHeight, Float.NaN, worldHeight);
        return e;
    }

    /** See BaseEnemy.resolveWeaponSet()'s own doc - looks `weaponSetName` up in this enemy's own
     *  def.weaponSets to find which firing-pattern id to switch to, then builds it the same way
     *  initWithDefinition() builds `firing` in the first place. Null (no swap) if this definition
     *  has no weaponSets at all, doesn't define that name, or the name doesn't resolve to a real
     *  firing pattern on disk. */
    @Override
    protected FiringPattern resolveWeaponSet(String weaponSetName) {
        if (def == null || def.weaponSets == null || weaponSetName == null) return null;
        String firingPatternId = def.weaponSets.get(weaponSetName);
        if (firingPatternId == null) return null;
        return PatternFactory.createFiring(def, PatternRegistry.getFiring(firingPatternId), worldWidth, worldHeight);
    }

    @Override
    public boolean isBoss() { return def != null && def.isBoss; }

    @Override
    public boolean isGround() { return def != null && def.isGround; }

    @Override
    public int getBackgroundLayer() { return def != null ? def.backgroundLayer : -1; }

    @Override
    public boolean isSealable() { return def != null && def.sealable; }

    @Override
    public boolean isDefiant() { return def != null && def.defiant; }

    @Override
    public boolean isDamageableByEnemyBullets() { return def != null && def.damageableByEnemyBullets; }

    @Override
    public boolean showsHealthBar() { return def != null && def.showHealthBar; }

    @Override
    public boolean isTargetableByHoming() { return def == null || def.targetableByHoming; }

    @Override
    public String getPairId() { return def != null ? def.pairId : null; }

    @Override
    public String getDefinitionId() { return def != null ? def.id : null; }

    @Override
    public boolean cancelsBulletsOnDeath() { return def != null && def.bulletCancel; }

    @Override
    public int getScore() { return def != null ? def.score : 10; }

    @Override
    public String getExplosionPattern() { return def != null ? def.explosionPattern : null; }

    @Override
    public float getSpawnRate() { return 1.0f; }
}
