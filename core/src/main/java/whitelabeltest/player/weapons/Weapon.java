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
    void updateWithEnemies(float delta, Array<Enemy> enemies); // New method for homing logic
    void draw(SpriteBatch batch);
    boolean isOffScreen(float worldHeight);
    Rectangle getRectangle();
    int getDamage();
    void setPath(Path<Vector2> path, float duration);

    // Updated spawn method signature
    void spawn(Array<Weapon> activeWeapons, Texture texture, float x, float y, Player player, Array<Enemy> enemies, AssetManager assets);

    float getFireRate();
    void playFireSound(AudioManager audio);

    default boolean shouldDestroyOnCollision() { return true; }

    void setLevel(int level);
    int getLevel();

    @Override
    default void reset() {}
}
