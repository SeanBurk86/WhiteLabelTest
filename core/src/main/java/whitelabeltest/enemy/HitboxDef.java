package whitelabeltest.enemy;

/** One collision shape of an enemy - see EnemyDefinition.hitboxes. An enemy can have any number of these,
 *  each a rectangle or a circle, so a hitbox can hug the part of the art that should actually be hittable
 *  (a body plus separate wings, say) instead of the sprite's whole bounding box.
 *
 *  Everything is expressed in the sprite's own frame - the UNROTATED sprite, centred on (0, 0), y up - and as
 *  a FRACTION of the sprite's drawn size, so a hitbox keeps lining up with the art when EnemyDefinition.size
 *  changes. The shape then rotates with the sprite, around the sprite's centre, exactly like the old single
 *  sprite-sized box did. See EnemyHitboxes for turning one into a world-space shape. */
public class HitboxDef {
    public static final String RECT = "rect";
    public static final String CIRCLE = "circle";

    // RECT or CIRCLE.
    public String shape = RECT;
    // Where the shape's centre sits relative to the sprite's centre, as a fraction of the sprite's drawn
    // width (x, positive = right) and height (y, positive = up).
    public float x = 0f;
    public float y = 0f;
    // RECT: the size as a fraction of the sprite's drawn width/height (1 x 1 = the whole sprite).
    public float width = 1f;
    public float height = 1f;
    // RECT: how far the rectangle is turned about its OWN centre, in degrees counter-clockwise (the same convention
    // as sprite rotation), in the sprite's frame - so it turns with the sprite on top of this. Meaningless for a
    // CIRCLE.
    public float rotation = 0f;
    // CIRCLE: the radius as a fraction of the sprite's SHORTER side, so it stays a true circle on a
    // non-square sprite (0.5 = as wide as the shorter side).
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
