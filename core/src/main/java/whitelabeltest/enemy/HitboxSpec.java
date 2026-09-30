package whitelabeltest.enemy;

/** A bullet hitbox resolved once per firing pattern and shared by all its bullets. */
public class HitboxSpec {
    public enum Shape { CIRCLE, RECTANGLE }

    public static final HitboxSpec DEFAULT = new HitboxSpec(null, 1f, 0f, 0f);

    // null = the bullet class's default shape.
    public final Shape shape;
    // Multiplier on the sprite-derived size (1 = fits the sprite).
    public final float scale;
    // Center offset from the sprite's center, in world units.
    public final float offsetX;
    public final float offsetY;

    public HitboxSpec(Shape shape, float scale, float offsetX, float offsetY) {
        this.shape = shape;
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }
}
