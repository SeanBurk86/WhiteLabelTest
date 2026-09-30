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

    void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets);

    float getFireRate();
    void playFireSound(AudioManager audio, int level);

    // Called once per Hyper Attack press while this is the active weapon. No-op by default.
    default void hyperAttack(Player player, Array<Weapon> activeWeapons, Array<Enemy> enemies, AssetManager assets, AudioManager audio) {}

    // Per-weapon cooldown, ticked every frame for both slots.
    float getShootTimer();
    void addShootTimer(float delta);
    void resetShootTimer();

    default boolean shouldDestroyOnCollision() { return true; }
    default float getChainWindow() { return 2.0f; }
    default float getShootSpeedMultiplier() { return 0.75f; }

    // A non-zero rotation makes collision treat getRectangle() as an oriented box around the pivot.
    default float getRotation() { return 0f; }
    default float getRotationPivotX() { return getRectangle().x + getRectangle().width / 2f; }
    default float getRotationPivotY() { return getRectangle().y; }

    // For persistent (non-destroying) projectiles: damage each enemy only once.
    default boolean hasDamaged(Enemy enemy) { return false; }
    default void markDamaged(Enemy enemy) {}

    // Called on each new hit, before the projectile is freed (e.g. to spawn follow-ups).
    default void onHit(Enemy enemy, Array<Weapon> activeWeapons, AssetManager assets) {}

    // Impact animation played at the contact point, or null for none.
    default Animation<TextureRegion> getHitAnimation() { return null; }
    default float getHitEffectSize() { return 0f; }

    void setLevel(int level);
    int getLevel();

    @Override
    default void reset() {}
}
