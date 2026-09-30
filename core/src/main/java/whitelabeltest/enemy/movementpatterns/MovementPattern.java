package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;

/** Moves an enemy's sprite each frame. See the README's "Movement patterns" table. */
public interface MovementPattern {
    // Default heading: 270 degrees = straight down (0 = right, 90 = up).
    float DEFAULT_ANGLE_DEG = 270f;

    void update(float delta, Sprite sprite, Rectangle rectangle, float worldWidth, float worldHeight, Circle playerHitbox, boolean inverseMovement);
    // True = remove the enemy.
    boolean isFinished();
    void reset();

    /** True once the pattern will no longer move the sprite (e.g. a WaypointPath held at its last
     *  waypoint). Unlike isFinished(), the enemy stays alive. Lets the editor preview stop stepping. */
    default boolean isSettled() { return false; }

    /** Ground-scroll hook for patterns that set the sprite position outright each frame (WaypointPath,
     *  Spline), which must add the scroll to their own position or lose it. Translate-based patterns
     *  need nothing. */
    default void applyGroundScroll(float dy) {}

    /** A pending waypoint event (sound and/or weapon-set switch), or null. Polled every frame. */
    default WaypointCue consumeCue() { return null; }

    /** A waypoint's sound and/or weapon-set switch (either may be unset). */
    class WaypointCue {
        public String soundName;
        public float soundVolume;
        public float soundPitch;
        public boolean changeWeaponSet;
        public String weaponSet;
    }
}
