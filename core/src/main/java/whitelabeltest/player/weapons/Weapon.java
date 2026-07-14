package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Path;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import whitelabeltest.gamemanagers.AssetManager;
import whitelabeltest.gamemanagers.AudioManager;
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

    default boolean shouldDestroyOnCollision() { return true; }
    default float getChainWindow() { return 2.0f; }
    default float getShootSpeedMultiplier() { return 0.75f; }

    // Lets a persistent, non-destroying weapon (e.g. a lingering hitbox) damage each enemy only
    // once instead of every frame it overlaps. Bullets that destroy themselves on hit never need this.
    default boolean hasDamaged(Enemy enemy) { return false; }
    default void markDamaged(Enemy enemy) {}

    // Called once, right when a bullet registers a new hit, before it's (possibly) destroyed and
    // freed back to its pool - lets a weapon spawn follow-up projectiles (e.g. shrapnel) at the
    // moment of impact instead of only on its own initial spawn().
    default void onHit(Enemy enemy, Array<Weapon> activeWeapons, AssetManager assets) {}

    void setLevel(int level);
    int getLevel();

    @Override
    default void reset() {}
}
