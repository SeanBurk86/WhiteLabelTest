package whitelabeltest.enemy;

/** One enemy collision shape (EnemyDefinition.hitboxes), a rectangle or circle, in the unrotated
 *  sprite's frame (centered at 0,0, y up) as fractions of the sprite's drawn size, so it scales with
 *  the sprite and rotates with it. See EnemyHitboxes for the world-space shape. */
public class HitboxDef {
    public static final String RECT = "rect";
    public static final String CIRCLE = "circle";

    // RECT or CIRCLE.
    public String shape = RECT;
    // Center offset as fractions of the sprite's width (x, right) and height (y, up).
    public float x = 0f;
    public float y = 0f;
    // RECT: the size as a fraction of the sprite's drawn width/height (1 x 1 = the whole sprite).
    public float width = 1f;
    public float height = 1f;
    // RECT: rotation about its own center, degrees counter-clockwise, on top of the sprite's rotation.
    public float rotation = 0f;
    // CIRCLE: radius as a fraction of the sprite's shorter side (0.5 = as wide as that side).
    public float radius = 0.5f;

    public HitboxDef() {}

    public boolean isCircle() { return CIRCLE.equals(shape); }

    public HitboxDef copy() {
        HitboxDef c = new HitboxDef();
        c.shape = shape;
        c.x = x;
        c.y = y;
        c.width = width;
        c.height = height;
        c.rotation = rotation;
        c.radius = radius;
        return c;
    }
}
