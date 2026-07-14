package whitelabeltest.player.weapons;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;


final class RRTLightning {
    static final class Edge {
        final float alongStart, perpStart, alongEnd, perpEnd;
        final int depth;

        Edge(float alongStart, float perpStart, float alongEnd, float perpEnd, int depth) {
            this.alongStart = alongStart;
            this.perpStart = perpStart;
            this.alongEnd = alongEnd;
            this.perpEnd = perpEnd;
            this.depth = depth;
        }
    }

    private RRTLightning() {}

    static Array<Edge> generate(int nodeCount, float length, float halfWidth, long seed) {
        float[] along = new float[nodeCount + 1];
        float[] perp = new float[nodeCount + 1];
        int[] parent = new int[nodeCount + 1];
        int[] depth = new int[nodeCount + 1];

        along[0] = 0f;
        perp[0] = 0f;
        parent[0] = -1;
        depth[0] = 0;

        Array<Edge> edges = new Array<>(true, nodeCount);

        for (int i = 1; i <= nodeCount; i++) {
            long nodeSeed = hash(seed, i, 0);
            float sampleAlong = rand(nodeSeed) * length;
            float samplePerp = (rand(hash(nodeSeed, 1, 0)) * 2f - 1f) * halfWidth;

            int nearest = 0;
            float nearestDist = Float.MAX_VALUE;
            for (int j = 0; j < i; j++) {
                float dAlong = along[j] - sampleAlong;
                float dPerp = perp[j] - samplePerp;
                float dist = dAlong * dAlong + dPerp * dPerp;
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = j;
                }
            }

            along[i] = sampleAlong;
            perp[i] = samplePerp;
            parent[i] = nearest;
            depth[i] = depth[nearest] + 1;

            edges.add(new Edge(along[nearest], perp[nearest], sampleAlong, samplePerp, depth[i]));
        }

        return edges;
    }

    // Recursive midpoint-displacement roughening applied to a single straight edge, after the
    // fact: each pass splits every existing segment at its midpoint, nudged perpendicular to that
    // segment by a random amount, then the displacement amplitude shrinks by `roughness` for the
    // next pass. This is what turns one of the tree's straight connector edges into an actual
    // jagged crack instead of a plain line between two random points.
    // minX/maxX/minY/maxY clamp every displaced point to a caller-given box (e.g. the weapon's own
    // hitbox, when ax/ay/bx/by are given in its local along/perp frame) so the jagged displacement
    // can't bulge past it; clamping incrementally as each point is created - rather than only at
    // the end - keeps neighboring points close together instead of producing one long straight
    // segment where a stray point gets yanked back to the boundary.
    static Array<Vector2> roughen(float ax, float ay, float bx, float by, int detail, float jitter, float roughness, long seed,
                                   float minX, float maxX, float minY, float maxY) {
        float dx0 = bx - ax, dy0 = by - ay;
        float length = (float) Math.sqrt(dx0 * dx0 + dy0 * dy0);
        float amp = length * jitter;

        Array<Vector2> points = new Array<>(true, 2);
        points.add(new Vector2(ax, ay));
        points.add(new Vector2(bx, by));

        for (int iter = 0; iter < detail; iter++) {
            Array<Vector2> next = new Array<>(true, points.size * 2);
            for (int i = 0; i < points.size - 1; i++) {
                Vector2 p0 = points.get(i);
                Vector2 p1 = points.get(i + 1);
                float midX = (p0.x + p1.x) * 0.5f;
                float midY = (p0.y + p1.y) * 0.5f;

                float segX = p1.x - p0.x;
                float segY = p1.y - p0.y;
                float segLen = (float) Math.sqrt(segX * segX + segY * segY);
                if (segLen > 0f) {
                    float perpX = -segY / segLen;
                    float perpY = segX / segLen;
                    float r = rand(hash(seed, i, iter)) * 2f - 1f;
                    midX += perpX * (r * amp);
                    midY += perpY * (r * amp);
                }
                midX = MathUtils.clamp(midX, minX, maxX);
                midY = MathUtils.clamp(midY, minY, maxY);

                next.add(p0);
                next.add(new Vector2(midX, midY));
            }
            next.add(points.get(points.size - 1));
            points = next;
            amp *= roughness;
        }

        return points;
    }

    private static long hash(long seed, int a, int b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }

    private static float rand(long seed) {
        return ((seed >>> 11) & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}
