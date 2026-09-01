package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.math.Vector2;

/** Stateless Cardinal-spline (tension-adjustable Catmull-Rom) curve math shared by
 *  WaypointPathMovement (the actual in-game path) and the editor's path preview/edit rendering
 *  (StageCanvas/MovementPathPreview, which depend on this core module and sample the exact same
 *  curve here rather than re-deriving their own, so the editor's preview always matches what the
 *  game actually flies - see MovementPatternDef's WaypointPath field docs).
 *
 * Each control point i has its own tension (0 = full smooth curve, 1 = straight corner): the
 * tangent at point i is (1 - tension[i]) * (points[i+1] - points[i-1]) / 2, the classic Cardinal-
 * spline formula, generalized to a per-point tension instead of one tension for the whole curve.
 * The segment between points[i] and points[i+1] is then a standard cubic Hermite interpolation
 * using each endpoint's own tangent. */
public final class WaypointSpline {
    private WaypointSpline() {}

    /** Number of straight segments used to APPROXIMATE one curve segment for preview/collision
     *  purposes elsewhere (not used by evaluate() itself, which is exact) - a reasonable default
     *  for how smooth a sampled polyline needs to look at this game's scale. */
    public static final int SAMPLES_PER_SEGMENT = 16;

    /** Position at parameter t, where t's integer part selects the segment (points[i]..points[i+1])
     *  and its fractional part is the local Hermite parameter within that segment. t is clamped to
     *  [0, points.length - 1] unless closePath, in which case it wraps. points/tensions must be the
     *  same length (at least 2). */
    public static Vector2 evaluate(Vector2 out, Vector2[] points, float[] tensions, boolean closePath, float t) {
        int n = points.length;
        int i0 = (int) Math.floor(t);
        float localT = t - i0;
        if (closePath) {
            i0 = ((i0 % n) + n) % n;
        } else {
            if (t <= 0) { i0 = 0; localT = 0; }
            else if (t >= n - 1) { i0 = n - 2; localT = 1; }
        }
        int i1 = closePath ? (i0 + 1) % n : Math.min(i0 + 1, n - 1);

        Vector2 p0 = points[i0];
        Vector2 p1 = points[i1];
        Vector2 m0 = tangentAt(points, i0, tensions[i0], closePath);
        Vector2 m1 = tangentAt(points, i1, tensions[i1], closePath);

        float t2 = localT * localT;
        float t3 = t2 * localT;
        float h00 = 2 * t3 - 3 * t2 + 1;
        float h10 = t3 - 2 * t2 + localT;
        float h01 = -2 * t3 + 3 * t2;
        float h11 = t3 - t2;

        out.x = h00 * p0.x + h10 * m0.x + h01 * p1.x + h11 * m1.x;
        out.y = h00 * p0.y + h10 * m0.y + h01 * p1.y + h11 * m1.y;
        return out;
    }

    /** Tangent (velocity) direction at parameter t - the Hermite basis functions' own derivatives,
     *  same segment/localT resolution as evaluate(). Used for "face travel direction" orientation. */
    public static Vector2 tangentAt(Vector2 out, Vector2[] points, float[] tensions, boolean closePath, float t) {
        int n = points.length;
        int i0 = (int) Math.floor(t);
        float localT = t - i0;
        if (closePath) {
            i0 = ((i0 % n) + n) % n;
        } else {
            if (t <= 0) { i0 = 0; localT = 0; }
            else if (t >= n - 1) { i0 = n - 2; localT = 1; }
        }
        int i1 = closePath ? (i0 + 1) % n : Math.min(i0 + 1, n - 1);

        Vector2 p0 = points[i0];
        Vector2 p1 = points[i1];
        Vector2 m0 = tangentAt(points, i0, tensions[i0], closePath);
        Vector2 m1 = tangentAt(points, i1, tensions[i1], closePath);

        float t2 = localT * localT;
        float dh00 = 6 * t2 - 6 * localT;
        float dh10 = 3 * t2 - 4 * localT + 1;
        float dh01 = -6 * t2 + 6 * localT;
        float dh11 = 3 * t2 - 2 * localT;

        out.x = dh00 * p0.x + dh10 * m0.x + dh01 * p1.x + dh11 * m1.x;
        out.y = dh00 * p0.y + dh10 * m0.y + dh01 * p1.y + dh11 * m1.y;
        return out;
    }

    /** Cardinal-spline tangent at control point i - duplicates the nearest endpoint for the
     *  neighbor that doesn't exist on an open (non-closed) path, so the first/last point still gets
     *  a sensible (one-sided) tangent instead of needing special-cased zero-tangent endpoints. */
    private static Vector2 tangentAt(Vector2[] points, int i, float tension, boolean closePath) {
        int n = points.length;
        Vector2 prev = points[closePath ? ((i - 1 + n) % n) : Math.max(i - 1, 0)];
        Vector2 next = points[closePath ? ((i + 1) % n) : Math.min(i + 1, n - 1)];
        float scale = (1f - tension) * 0.5f;
        return new Vector2((next.x - prev.x) * scale, (next.y - prev.y) * scale);
    }
}
