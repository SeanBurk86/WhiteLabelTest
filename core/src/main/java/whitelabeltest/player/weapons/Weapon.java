package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Path;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.audio.AudioManager;
import whitelabeltest.enemy.Enemy;
import whitelabeltest.player.Player;

public interface Weapon extends Pool.Poolable {
    void update(float delta);
    void updateWithEnemies(float delta, Array<Enemy> enemies);
    void draw(SpriteBatch batch);
    boolean isOffScreen(float worldHeight);
    Rectangle getRectangle();
    int getDamage();
    void setPath(Path<Vector2> path, float duration);

    // Updated spawn method signature
    void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets);

    float getFireRate();
    void playFireSound(AudioManager audio, int level);

    // Each weapon can define its own Hyper Attack - triggered once (see
    // Player.handleHyperAttack) whenever the Hyper Attack input is pressed while this weapon is
    // the active one. No-op by default; only weapons with a defined ability need to override it.
    // Effects that need to persist over time (timers, position, repeated firing) belong on the
    // weapon itself or, for effects tied to the player (like Basic's detachable halo), behind a
    // dedicated Player method this can call into.
    default void hyperAttack(Player player, Array<Weapon> activeWeapons, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {}

    // Each weapon tracks its own cooldown, advanced every frame regardless of which slot is
    // active (see Player.advanceWeaponTimers). That way switching slots never resets or
    // fast-forwards a cooldown - a weapon is only ready to fire once real time, not switch
    // events, has closed the gap since it last fired.
    float getShootTimer();
    void addShootTimer(float delta);
    void resetShootTimer();

    default boolean shouldDestroyOnCollision() { return true; }
    default float getChainWindow() { return 2.0f; }
    default float getShootSpeedMultiplier() { return 0.75f; }

    // getRectangle() is normally an axis-aligned box tested with plain Rectangle#overlaps. A weapon
    // whose real footprint is rotated (e.g. a diagonal Thunderbolt strike) can instead report
    // getRectangle() as its own un-rotated shape plus a non-zero rotation and pivot, so collision
    // can test it as a true oriented rectangle instead of inflating an axis-aligned bounding box.
    default float getRotation() { return 0f; }
    default float getRotationPivotX() { return getRectangle().x + getRectangle().width / 2f; }
    default float getRotationPivotY() { return getRectangle().y; }

    // Lets a persistent, non-destroying weapon (e.g. a lingering hitbox) damage each enemy only
    // once instead of every frame it overlaps. Bullets that destroy themselves on hit never need this.
    default boolean hasDamaged(Enemy enemy) { return false; }
    default void markDamaged(Enemy enemy) {}

    // Called once, right when a bullet registers a new hit, before it's (possibly) destroyed and
    // freed back to its pool - lets a weapon spawn follow-up projectiles (e.g. shrapnel) at the
    // moment of impact instead of only on its own initial spawn().
    default void onHit(Enemy enemy, Array<Weapon> activeWeapons, AssetManager assets) {}

    // The impact animation to play at the point of contact on every hit (see
    // WeaponDefinition.hitTexture and CollisionManager.checkBulletEnemyCollisions), or null for a
    // weapon whose definition doesn't set one.
    default Animation<TextureRegion> getHitAnimation() { return null; }
    default float getHitEffectSize() { return 0f; }

    void setLevel(int level);
    int getLevel();

    @Override
    default void reset() {}
}
