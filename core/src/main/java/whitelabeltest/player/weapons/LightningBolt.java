package whitelabeltest.player.weapons;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

final class LightningBolt {
    static final class Segment {
        final float x1, y1, x2, y2;
        final int level;
        final boolean isFork;
        final float width;

        Segment(float x1, float y1, float x2, float y2, int level, boolean isFork, float width) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.level = level;
            this.isFork = isFork;
            this.width = width;
        }
    }

    private static final class Working {
        float x1, y1, x2, y2;
        int level;
        boolean isFork;
        int remaining;

        Working(float x1, float y1, float x2, float y2, int level, boolean isFork, int remaining) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.level = level;
            this.isFork = isFork;
            this.remaining = remaining;
        }
    }

    private static final int SUBDIVISIONS = 5;
    private static final int FORK_SUBDIVISIONS = 2;
    private static final float DISPLACEMENT_FACTOR = 0.5f;
    private static final float DISPLACEMENT_DECAY = 0.65f;
    private static final float FORK_PROBABILITY = 0.25f;
    private static final float FORK_MAX_ANGLE_DEG = 60f;
    private static final float FORK_LENGTH_SCALE = 0.5f;
    private static final float WIDTH_FALLOFF = 0.75f; // width shrinks per subdivision level
    private static final float FORK_WIDTH_SCALE = 0.7f;

    private LightningBolt() {}

    // minAlong/maxAlong/minPerp/maxPerp clamp every displaced/fork point to a caller-given box (the
    // weapon's hitbox, when start/end are given in its local along/perp frame) so the recursive
    // displacement and forks can't wander past it. Clamping incrementally as each point is created
    // - rather than only at the finished leaves - keeps neighboring points close together instead
    // of producing one long straight segment where a stray point gets yanked back to the boundary.
    static Array<Segment> generate(float startX, float startY, float endX, float endY, long seed, float thickness,
                                    float minAlong, float maxAlong, float minPerp, float maxPerp) {
        Array<Working> current = new Array<>(true, 32);
        current.add(new Working(startX, startY, endX, endY, 0, false, SUBDIVISIONS));

        Array<Segment> finished = new Array<>(true, 64);

        while (current.size > 0) {
            Array<Working> next = new Array<>(true, current.size * 2);
            for (int i = 0; i < current.size; i++) {
                Working seg = current.get(i);

                if (seg.remaining <= 0) {
                    float width = thickness * (float) Math.pow(WIDTH_FALLOFF, seg.level) * (seg.isFork ? FORK_WIDTH_SCALE : 1f);
                    finished.add(new Segment(seg.x1, seg.y1, seg.x2, seg.y2, seg.level, seg.isFork, width));
                    continue;
                }

                long segSeed = hash(seed, i, seg.level);
                float dx = seg.x2 - seg.x1;
                float dy = seg.y2 - seg.y1;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                if (length < 0.0001f) length = 0.0001f;
                float nx = -dy / length;
                float ny = dx / length;

                float displacement = length * DISPLACEMENT_FACTOR * (float) Math.exp(-DISPLACEMENT_DECAY * seg.level);
                float jitter = (rand(segSeed) * 2f - 1f) * displacement;

                float midX = MathUtils.clamp((seg.x1 + seg.x2) / 2f + nx * jitter, minAlong, maxAlong);
                float midY = MathUtils.clamp((seg.y1 + seg.y2) / 2f + ny * jitter, minPerp, maxPerp);

                int childRemaining = seg.remaining - 1;
                next.add(new Working(seg.x1, seg.y1, midX, midY, seg.level + 1, seg.isFork, childRemaining));
                next.add(new Working(midX, midY, seg.x2, seg.y2, seg.level + 1, seg.isFork, childRemaining));

                if (!seg.isFork && rand(hash(segSeed, 1, 0)) < FORK_PROBABILITY) {
                    float forkAngle = (rand(hash(segSeed, 2, 0)) * 2f - 1f) * FORK_MAX_ANGLE_DEG;
                    Vector2 forkDir = new Vector2(dx, dy).nor().rotateDeg(forkAngle).scl(length * FORK_LENGTH_SCALE);
                    float forkEndX = MathUtils.clamp(midX + forkDir.x, minAlong, maxAlong);
                    float forkEndY = MathUtils.clamp(midY + forkDir.y, minPerp, maxPerp);
                    next.add(new Working(midX, midY, forkEndX, forkEndY, seg.level + 1, true, FORK_SUBDIVISIONS));
                }
            }
            current = next;
        }

        return finished;
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
