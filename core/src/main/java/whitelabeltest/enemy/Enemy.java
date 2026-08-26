package whitelabeltest.enemy;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.enemy.bullets.EnemyBullet;

public interface Enemy extends Pool.Poolable {
    void init(Texture texture, float worldWidth, float worldHeight, float startX, float startY);
    // groundScrollSpeed is the current stage schedule's background scroll speed (see
    // SpawnScheduler.getGroundScrollSpeed()) - only consumed by a ground enemy (isGround()), which
    // gets shifted down by that same amount every frame on top of its own movement pattern, so it
    // stays visually planted on the scrolling terrain instead of sliding relative to it. Ignored by
    // every other enemy.
    void update(float delta, Array<EnemyBullet> enemyBullets, Circle playerHitbox, Circle grazeHitbox, boolean firingPaused, float groundScrollSpeed);
    void draw(SpriteBatch batch);
    default void drawShadow(SpriteBatch batch) {}
    boolean isOffScreen();
    Rectangle getRectangle();
    boolean takeDamage(int amount);
    default boolean isBoss() { return false; }
    default boolean isGround() { return false; }
    // See EnemyDefinition.backgroundLayer - -1 (the default) means not attached to any background
    // layer, drawn in front of the whole stack as before this existed. GameController.draw()/
    // EntityManager use this both to decide draw order (sandwiched right after that layer instead
    // of after the whole background) and, for a ground enemy, which layer's scrollSpeed drives its
    // implicit movement instead of the schedule-wide one.
    default int getBackgroundLayer() { return -1; }
    // Sealable enemies hold their fire while the player's graze halo overlaps their hitbox - see
    // BaseEnemy.update's firing gate.
    default boolean isSealable() { return false; }
    // Defiant enemies take no damage until they've fired at least once - see
    // BaseEnemy.takeDamage()/hasFiredOnce.
    default boolean isDefiant() { return false; }
    // Whether another enemy's bullets can damage (and be consumed by) this enemy - see
    // CollisionManager.checkEnemyBulletEnemyCollisions. False by default so stray enemy bullets
    // passing near an unrelated enemy (e.g. several stationary tutorial emitters sharing one spawn
    // point) don't get silently eaten; only enemies meant to be a bullet-streaming drill's target
    // (e.g. PowerCarrier) opt in.
    default boolean isDamageableByEnemyBullets() { return false; }
    // Whether UIManager.drawEnemyHealthBars() draws a health meter above this enemy during normal
    // play (not the debug-only drawEnemyHealthDebug, which shows every enemy regardless of this
    // flag) - e.g. the tutorial's bullet-streaming targets, whose regenerating health needs to be
    // visible so the player can tell their stream is actually landing.
    default boolean showsHealthBar() { return false; }
    // Whether a screen-wide homing scan (see ThunderboltWeapon.spawn's nearest-enemy selection)
    // is allowed to pick this enemy as a target. True by default; a purely decorative/utility
    // enemy that's only ever a bullet source (e.g. the tutorial's stationary wall/stream
    // emitters) opts out so it can sit active on screen without stealing a homing bolt meant for
    // a real target.
    default boolean isTargetableByHoming() { return true; }
    // See EnemyDefinition.pairId/CollisionManager.resolvePairedEnemyDeaths() - null (the default)
    // means this enemy dies normally, independent of any other enemy. Two active enemies sharing
    // the same non-null pairId must both cross zero health within PAIR_GRACE_WINDOW of each other
    // to actually die; whichever does so without its partner following in time regenerates instead
    // - see reviveFully()/getPairGraceTimer().
    default String getPairId() { return null; }
    // Reverses a just-started death for a paired enemy whose partner didn't also cross zero within
    // the grace window - restores full health and returns to ACTIVE as if it had never taken the
    // lethal hit. No-op for a non-paired enemy (the default death flow never calls this on one).
    default void reviveFully() {}
    // Guards CollisionManager.resolvePairedEnemyDeaths() against reprocessing a paired enemy on a
    // later frame while it's still playing out an already-finalized death (its death animation
    // takes a few frames, during which it's still isDying()==true and would otherwise look like a
    // fresh, unresolved pair death again). Reset on pool reuse.
    default boolean isPairResolved() { return false; }
    default void markPairResolved() {}
    // How much longer (seconds) a paired enemy that's crossed zero keeps waiting, mid-death, for
    // its partner to also cross zero - see CollisionManager.resolvePairedEnemyDeaths(). Negative
    // means it isn't currently in that wait. Reset on pool reuse/revive.
    default float getPairGraceTimer() { return -1f; }
    default void setPairGraceTimer(float secondsRemaining) {}
    // See EnemyDefinition.bulletCancel - GameController.destroyEnemy checks this to decide
    // whether to also clear out this enemy's in-flight bullets when it dies.
    default boolean cancelsBulletsOnDeath() { return false; }
    default int getScore() { return 10; }
    default String getExplosionPattern() { return null; }

    // Forces this enemy's firing pattern to its next stage immediately - see
    // GameController.destroyEnemy, which calls this on every other boss whenever any enemy dies,
    // and FiringPattern.advance()/SequencedFiringPattern.advance() for the actual step logic. A
    // no-op default since only BaseEnemy (with a FiringPattern to forward to) does anything with it.
    default void advanceFiringPattern() {}

    // Permanently stops this enemy from firing (it stays alive/on-screen otherwise) - see
    // SpawnScheduler.SpawnEvent.silence, which uses this to shut off a scripted enemy the instant
    // its drill actually finishes (e.g. the tutorial's bullet-streaming emitter, whose firing
    // pattern has no fixed duration of its own - see TutorialStreamShot - so it must be told to
    // stop rather than just running out a timer).
    default void silenceFiring() {}
    // This enemy's EnemyDefinition id (e.g. "TutorialStreamEmitter"), or null if it wasn't built
    // from one - see SpawnScheduler.SpawnEvent.silence, which matches on this to find which live
    // enemy/enemies a silence event applies to.
    default String getDefinitionId() { return null; }


    default int getHealth() { return 0; }
    default int getMaxHealth() { return 0; }


    default boolean isActive() { return true; }
    default boolean isDying() { return false; }


    // The guaranteed weapon-powerup tier (1-3, see GameController.spawnPowerup()) this enemy drops
    // on death, or null for no guarantee.
    void setGuaranteedPowerup(Integer powerupTier);
    Integer getGuaranteedPowerup();


    void setInvertMovement(boolean invert);

    Enemy create(Texture texture, float worldWidth, float worldHeight);
    float getSpawnRate();

    @Override
    default void reset() {}
}
