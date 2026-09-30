package whitelabeltest.enemy.movementpatterns;

import com.badlogic.gdx.math.Vector2;

/** Cardinal-spline math with per-point tension, shared by WaypointPathMovement and the editor's path
 *  preview so they always match. Tangent at point i = (1 - tension[i]) * (p[i+1] - p[i-1]) / 2
 *  (0 = smooth, 1 = sharp corner); each segment is a cubic Hermite between its endpoints. */
public final class WaypointSpline {
    private WaypointSpline() {}

    /** Polyline samples per segment for previews (evaluate() itself is exact). */
    public static final int SAMPLES_PER_SEGMENT = 16;

    /** Position at t (integer part = segment, fraction = position within it). Clamped to
     *  [0, points.length - 1], or wrapped if closePath. Needs at least 2 points. */
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
        float m0x = tangentX(points, i0, tensions[i0], closePath), m0y = tangentY(points, i0, tensions[i0], closePath);
        float m1x = tangentX(points, i1, tensions[i1], closePath), m1y = tangentY(points, i1, tensions[i1], closePath);

        float t2 = localT * localT;
        float t3 = t2 * localT;
        float h00 = 2 * t3 - 3 * t2 + 1;
        float h10 = t3 - 2 * t2 + localT;
        float h01 = -2 * t3 + 3 * t2;
        float h11 = t3 - t2;

        out.x = h00 * p0.x + h10 * m0x + h01 * p1.x + h11 * m1x;
        out.y = h00 * p0.y + h10 * m0y + h01 * p1.y + h11 * m1y;
        return out;
    }

    /** Derivative at t (same parameterization as evaluate()). */
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
        float m0x = tangentX(points, i0, tensions[i0], closePath), m0y = tangentY(points, i0, tensions[i0], closePath);
        float m1x = tangentX(points, i1, tensions[i1], closePath), m1y = tangentY(points, i1, tensions[i1], closePath);

        float t2 = localT * localT;
        float dh00 = 6 * t2 - 6 * localT;
        float dh10 = 3 * t2 - 4 * localT + 1;
        float dh01 = -6 * t2 + 6 * localT;
        float dh11 = 3 * t2 - 2 * localT;

        out.x = dh00 * p0.x + dh10 * m0x + dh01 * p1.x + dh11 * m1x;
        out.y = dh00 * p0.y + dh10 * m0y + dh01 * p1.y + dh11 * m1y;
        return out;
    }

    /** Tangent at control point i (endpoints of an open path use a one-sided difference). Returned
     *  as separate floats to avoid allocating a Vector2 on this hot path. */
    private static float tangentX(Vector2[] points, int i, float tension, boolean closePath) {
        int n = points.length;
        Vector2 prev = points[closePath ? ((i - 1 + n) % n) : Math.max(i - 1, 0)];
        Vector2 next = points[closePath ? ((i + 1) % n) : Math.min(i + 1, n - 1)];
        return (next.x - prev.x) * (1f - tension) * 0.5f;
    }

    private static float tangentY(Vector2[] points, int i, float tension, boolean closePath) {
        int n = points.length;
        Vector2 prev = points[closePath ? ((i - 1 + n) % n) : Math.max(i - 1, 0)];
        Vector2 next = points[closePath ? ((i + 1) % n) : Math.min(i + 1, n - 1)];
        return (next.y - prev.y) * (1f - tension) * 0.5f;
    }
}
