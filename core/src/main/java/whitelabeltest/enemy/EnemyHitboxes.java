package whitelabeltest.enemy;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** Converts HitboxDefs to world-space shapes, shared by CollisionManager and the debug overlay so they
 *  always agree. A circle becomes a world circle; a rectangle becomes an unrotated rectangle at its
 *  true center that turns about that center by totalRotation(), which the usual rotated-rectangle
 *  tests handle. */
public final class EnemyHitboxes {
    private EnemyHitboxes() {}

    /** A rectangle hitbox as an unrotated rectangle centered at its world center (its offset rotated
     *  with the sprite); it turns about that center by totalRotation(). */
    public static Rectangle rect(HitboxDef box, Rectangle sprite, float spriteRotationDeg, Rectangle out) {
        float w = box.width * sprite.width;
        float h = box.height * sprite.height;
        float dx = box.x * sprite.width;
        float dy = box.y * sprite.height;
        if (spriteRotationDeg != 0f) {
            float cos = MathUtils.cosDeg(spriteRotationDeg);
            float sin = MathUtils.sinDeg(spriteRotationDeg);
            float rx = dx * cos - dy * sin;
            float ry = dx * sin + dy * cos;
            dx = rx;
            dy = ry;
        }
        float cx = sprite.x + sprite.width / 2f + dx;
        float cy = sprite.y + sprite.height / 2f + dy;
        return out.set(cx - w / 2f, cy - h / 2f, w, h);
    }

    /** rect()'s rotation about its own center: the hitbox's rotation plus the sprite's. */
    public static float totalRotation(HitboxDef box, float spriteRotationDeg) {
        return box.rotation + spriteRotationDeg;
    }

    /** A circle hitbox as a world-space circle. */
    public static Circle circle(HitboxDef box, Rectangle sprite, float rotationDeg, Circle out) {
        float pivotX = sprite.x + sprite.width / 2f;
        float pivotY = sprite.y + sprite.height / 2f;
        float dx = box.x * sprite.width;
        float dy = box.y * sprite.height;
        if (rotationDeg != 0f) {
            float cos = MathUtils.cosDeg(rotationDeg);
            float sin = MathUtils.sinDeg(rotationDeg);
            float rx = dx * cos - dy * sin;
            float ry = dx * sin + dy * cos;
            dx = rx;
            dy = ry;
        }
        out.set(pivotX + dx, pivotY + dy, box.radius * Math.min(sprite.width, sprite.height));
        return out;
    }
}
