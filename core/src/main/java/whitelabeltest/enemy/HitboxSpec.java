package whitelabeltest.enemy;

/** Immutable spec for a bullet's collision hitbox, independent of its visual sprite - built once
 *  per firing pattern by PatternFactory.hitboxSpec and shared by every bullet that pattern fires.
 *  See BulletDef.hitboxShape/hitboxScale/hitboxOffsetX/hitboxOffsetY for how a pattern authors
 *  one. */
public class HitboxSpec {
    public enum Shape { CIRCLE, RECTANGLE }

    public static final HitboxSpec DEFAULT = new HitboxSpec(null, 1f, 0f, 0f);

    // Null means "whatever shape this bullet type already defaults to" (e.g. AimedEnemyBullet
    // defaults to CIRCLE, ExplodingAimedBullet to RECTANGLE) rather than forcing one - see each
    // bullet class's getHitRadius() for its own default.
    public final Shape shape;
    // Multiplies the hitbox's auto-derived size (from the sprite's own dimensions) - 1 (the
    // default) keeps the classic "hitbox exactly fits the sprite" behavior.
    public final float scale;
    // World-unit offset of the hitbox's center from the sprite's center - 0 (the default) keeps
    // the hitbox centered on the sprite.
    public final float offsetX;
    public final float offsetY;

    public HitboxSpec(Shape shape, float scale, float offsetX, float offsetY) {
        this.shape = shape;
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }
}
