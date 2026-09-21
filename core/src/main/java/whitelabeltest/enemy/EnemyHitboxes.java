package whitelabeltest.enemy;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** Turns an enemy's HitboxDefs into world-space shapes - shared by CollisionManager (what actually gets hit)
 *  and Main's debug overlay (what's drawn), so the two can never disagree about where a hitbox is.
 *
 *  An enemy's sprite is drawn as Enemy.getRectangle() rotated by Enemy.getRotation() around that rectangle's
 *  centre (see Enemy.getRotationPivotX/Y), and a HitboxDef is defined in that same unrotated frame. A rectangle
 *  hitbox may also be turned on its own (HitboxDef.rotation), so both kinds are reduced to something simple: a
 *  circle is a plain world-space circle (circle() below - its centre swung around the sprite's centre), and a
 *  rectangle is an unrotated rectangle centred where the hitbox really is (rect() below) that turns about its
 *  OWN centre by totalRotation() - which the same rotated-rectangle tests the sprite-sized box always used
 *  already handle. */
public final class EnemyHitboxes {
    private EnemyHitboxes() {}

    /** Writes `box` (which must not be a circle) into `out` as the rectangle it occupies for an enemy whose sprite
     *  currently fills `sprite` and is rotated by `spriteRotationDeg` around its own centre - positioned UNROTATED
     *  and centred on where the hitbox's centre really is in the world (its offset swung around the sprite's centre
     *  by the sprite's rotation). It then turns about that same centre by totalRotation(): its own rotation plus
     *  the sprite's. That single rotation-about-its-own-centre is what lets the usual rotated-rectangle tests handle
     *  a hitbox that's turned on top of a sprite that's turned. */
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

    /** How far rect()'s rectangle is turned about its own centre in the world. */
    public static float totalRotation(HitboxDef box, float spriteRotationDeg) {
        return box.rotation + spriteRotationDeg;
    }

    /** Writes `box` (which must be a circle) into `out` as the WORLD-space circle it occupies for an enemy
     *  whose sprite currently fills `sprite` and is rotated by `rotationDeg` around its own centre. */
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
