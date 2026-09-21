package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** The flying camera for MandelbulbShader - a small stateful, collision-safe autopilot rather than a
 *  closed-form path, because the shader's world isn't empty space you can just fly a curve through.
 *
 *  What the shader marches (see map() in mandelbulb.frag) is the fractal's surface with a thin solid
 *  SKIN around it, which leaves two separate regions of open space: the OUTSIDE, and a hollow
 *  CHAMBER inside (the Mandelbulb's solid core, seen from within). The camera starts outside orbiting
 *  the bulb, then when the dive begins it heads for the middle, punches through the skin, and from
 *  then on flies around the inside chamber.
 *
 *  Steering is the same in both regions: head for a goal point while sliding along/away from walls,
 *  with speed proportional to the clearance from the nearest wall - so it slows as walls close in
 *  (an exponential approach that can't overshoot into one). Being integrated frame to frame with a
 *  low-pass filtered heading is what keeps the motion smooth: nothing here is a function of absolute
 *  time that could snap when a parameter changes. The one deliberate exception to "never touch a wall"
 *  is the skin crossing, which drives straight through it.
 *
 *  distance() must stay in step with field() in mandelbulb.frag - same iteration count, same twist,
 *  same formula - and SHELL with the shader's SHELL, since the shader renders the very walls this
 *  camera avoids. */
final class MandelbulbCamera {
    // Must match BULB_ITERATIONS in mandelbulb.frag.
    private static final int BULB_ITERATIONS = 6;
    /** Thickness of the solid skin around the fractal surface. Must match SHELL in mandelbulb.frag. */
    static final float SHELL = 0.02f;

    /** Hard floor on the distance to a wall. */
    private static final float MIN_CLEARANCE = 0.012f;
    private static final float MAX_SPEED = 1.4f;
    private static final float MIN_SPEED = 0.03f;
    // speed = SPEED_PER_CLEARANCE * clearance: each second the camera closes this fraction of the gap.
    private static final float SPEED_PER_CLEARANCE = 1.6f;
    private static final float GOAL_GAIN = 1.5f;
    // Within this distance of a wall the heading is bent away from / along it.
    private static final float AVOID_RANGE = 0.3f;
    // 1/seconds: how quickly the heading / view direction chase what they're aimed at.
    private static final float MOVE_TURN_RATE = 2.5f;
    private static final float VIEW_TURN_RATE = 3f;
    private static final float MAX_SUBSTEP = 1f / 60f;

    // The dive begins crossing the skin once `inside` passes this and the camera is this close to the outer wall.
    private static final float DIVE_THRESHOLD = 0.5f;
    private static final float CROSS_TRIGGER_DISTANCE = 0.06f;
    private static final float CROSS_SPEED = 0.6f;
    // The crossing ends once the camera is this close to the centre AND has this much room around it -
    // i.e. it has reached the smooth open ball in the middle of the bulb, not merely a thin pocket just
    // behind the skin (there are internal ridges near the wall, so the first open space past it is
    // often a sliver between the wall and one of them).
    private static final float CROSS_DONE_RADIUS = 0.5f;
    private static final float CROSS_DONE_CLEARANCE = 0.05f;
    // Long enough to cross the whole ridge zone at CROSS_SPEED.
    private static final float CROSS_TIMEOUT = 2.5f;
    private static final float CROSS_RETRY_DELAY = 1.5f;

    private final Vector3 pos = new Vector3();
    private final Vector3 lastGood = new Vector3();
    private final Vector3 moveDir = new Vector3();
    private final Vector3 view = new Vector3();
    private final Vector3 goal = new Vector3();
    private final Vector3 a = new Vector3();
    private final Vector3 b = new Vector3();
    private final Vector3 n = new Vector3();
    private static final Vector3 QUERY = new Vector3();

    private boolean insideBulb;
    private boolean crossing;
    private float crossTimer;
    private float crossCooldown;

    MandelbulbCamera() {
        reset();
    }

    /** Puts the camera back at the start of its outside orbit, already heading along it. */
    void reset() {
        orbitGoal(0f, pos);
        orbitGoal(0.35f, a);
        moveDir.set(a).sub(pos).nor();
        // Start the view where step() will be steering it, not along the orbit - otherwise the first
        // half second is spent swinging it around to the bulb.
        float lookAtBulb = MathUtils.lerp(0.4f, 0.95f, smoothstep(0.9f, 1.9f, pos.len()));
        view.set(pos).nor().scl(-lookAtBulb).mulAdd(moveDir, 1f - lookAtBulb).nor();
        lastGood.set(pos);
        insideBulb = false;
        crossing = false;
        crossTimer = 0f;
        crossCooldown = 0f;
    }

    Vector3 position() { return pos; }
    Vector3 forward() { return view; }
    /** True once the camera has crossed the skin and is flying around the inside chamber. */
    boolean isInsideBulb() { return insideBulb; }
    boolean isCrossing() { return crossing; }

    /** @param orbitTime clock for the outside orbit goal
     *  @param diveTime clock for the interior goal - kept separate so blending the two goals never
     *  changes either one's own speed (blending SPEEDS instead makes the phase sweep wildly)
     *  @param inside 0 = orbit the bulb from outside ... 1 = dive into it */
    void update(float delta, float orbitTime, float diveTime, float inside, float power, float twist) {
        float remaining = Math.min(delta, 0.1f);
        while (remaining > 0f) {
            float h = Math.min(remaining, MAX_SUBSTEP);
            step(h, orbitTime, diveTime, inside, power, twist);
            remaining -= h;
        }
    }

    /** Distance to the nearest wall in the region the camera is currently in. */
    private float clearance(Vector3 p, float power, float twist) {
        float g = distance(p, power, twist);
        return insideBulb ? -(g + SHELL) : g;
    }

    private void step(float h, float orbitTime, float diveTime, float inside, float power, float twist) {
        crossCooldown = Math.max(0f, crossCooldown - h);

        if (crossing) {
            stepCrossing(h, power, twist);
        } else {
            stepFlying(h, orbitTime, diveTime, inside, power, twist);
        }

        // Look where it's going; when it's out in the open, blend that toward the bulb so the view
        // never drifts off into empty space. Once diving it just looks ahead.
        float lookAtBulb = insideBulb || crossing ? 0.15f
            : MathUtils.lerp(MathUtils.lerp(0.4f, 0.95f, smoothstep(0.9f, 1.9f, pos.len())), 0.15f, inside);
        b.set(pos).nor().scl(-lookAtBulb).mulAdd(moveDir, 1f - lookAtBulb).nor();
        view.lerp(b, 1f - (float) Math.exp(-VIEW_TURN_RATE * h)).nor();
    }

    private void stepFlying(float h, float orbitTime, float diveTime, float inside, float power, float twist) {
        orbitGoal(orbitTime, a);
        diveGoal(diveTime, b);
        goal.set(a).lerp(b, insideBulb ? 1f : inside);

        float d = clearance(pos, power, twist);
        if (d <= 0f) {
            // A wall swept over the camera (the fractal is morphing) - go back to the last point known
            // to be in open space and carry on from there.
            pos.set(lastGood);
            d = Math.max(clearance(pos, power, twist), MIN_CLEARANCE);
        }
        wallNormal(pos, d, power, twist, n);

        // Once the dive is on, stop being bent away from the outer wall: the point is to reach it.
        boolean approachingWall = !insideBulb && inside > DIVE_THRESHOLD;
        if (approachingWall && crossCooldown <= 0f && d < CROSS_TRIGGER_DISTANCE) {
            crossing = true;
            crossTimer = 0f;
            // Head straight into the wall, i.e. against the outward normal.
            moveDir.set(n).scl(-1f);
            return;
        }

        a.set(goal).sub(pos);
        float goalDist = Math.max(a.len(), 1e-4f);
        a.scl(1f / goalDist);
        float wall = MathUtils.clamp(1f - d / AVOID_RANGE, 0f, 1f);
        wall *= wall;
        if (approachingWall && crossCooldown <= 0f) wall = 0f;
        float into = a.dot(n);
        // Slide along the wall instead of pushing into it, and lean a little away from it.
        if (into < 0f) a.mulAdd(n, -into * wall);
        a.mulAdd(n, 0.6f * wall).nor();

        moveDir.lerp(a, 1f - (float) Math.exp(-MOVE_TURN_RATE * h)).nor();
        float speed = MathUtils.clamp(Math.min(GOAL_GAIN * goalDist, SPEED_PER_CLEARANCE * d), MIN_SPEED, MAX_SPEED);
        pos.mulAdd(moveDir, speed * h);

        // Keep a hard floor under the clearance so the camera can't tunnel through a wall.
        for (int i = 0; i < 3; i++) {
            float dn = clearance(pos, power, twist);
            if (dn >= MIN_CLEARANCE) break;
            if (dn <= 0f) { pos.set(lastGood); break; }
            wallNormal(pos, dn, power, twist, n);
            pos.mulAdd(n, MIN_CLEARANCE - dn);
        }
        if (clearance(pos, power, twist) > MIN_CLEARANCE * 0.5f) lastGood.set(pos);
    }

    /** Bursts through the outer wall and on to the middle of the bulb, ignoring every wall on the way -
     *  the skin, and the thin ridges inside it - in a straight line at the centre. It ends when it reaches
     *  the open ball at the middle. If it never does (it timed out somewhere unlucky) it gives up, backs
     *  out to the last point known to be in open space, and tries again from somewhere else later. */
    private void stepCrossing(float h, float power, float twist) {
        crossTimer += h;
        moveDir.lerp(a.set(pos).nor().scl(-1f), 1f - (float) Math.exp(-6f * h)).nor();
        pos.mulAdd(moveDir, CROSS_SPEED * h);

        float gNow = distance(pos, power, twist);
        if (pos.len() < CROSS_DONE_RADIUS && -(gNow + SHELL) >= CROSS_DONE_CLEARANCE) {
            crossing = false;
            insideBulb = true;
            lastGood.set(pos);
        } else if (crossTimer > CROSS_TIMEOUT) {
            crossing = false;
            crossCooldown = CROSS_RETRY_DELAY;
            pos.set(lastGood);
        }
    }

    /** The outside orbit: an orbit whose radius swings in and out and whose pitch wanders. It stays
     *  clear of the bulb's surface layer (which reaches out to about radius 1.2): a goal that dips into
     *  the solid pins the camera against the wall at minimum speed while the goal slides underneath
     *  it, and the heading whips around - the jerkiness this used to have before the dive. */
    static Vector3 orbitGoal(float t, Vector3 out) {
        float yaw = t * 0.5f;
        float pitch = 0.95f * MathUtils.sin(t * 0.37f);
        float radius = 1.85f + 0.55f * MathUtils.sin(t * 0.53f + 0.6f);
        return out.set(MathUtils.cos(pitch) * MathUtils.sin(yaw), MathUtils.sin(pitch), MathUtils.cos(pitch) * MathUtils.cos(yaw)).scl(radius);
    }

    /** The dive goal: a point toward the middle of the bulb that slowly sweeps around, so once inside the
     *  camera keeps working its way around the chamber instead of parking in one spot. */
    static Vector3 diveGoal(float t, Vector3 out) {
        float yaw = t * 0.45f;
        float pitch = 0.9f * MathUtils.sin(t * 0.31f + 1.0f);
        return out.set(MathUtils.cos(pitch) * MathUtils.sin(yaw), MathUtils.sin(pitch), MathUtils.cos(pitch) * MathUtils.cos(yaw)).scl(0.4f);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = MathUtils.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Mandelbulb field at p (mirrors field() in mandelbulb.frag): positive outside the fractal,
     *  negative inside it. Computed in double - it's only a handful of calls per frame, and the extra
     *  precision keeps the clearance tests steady. */
    static float distance(Vector3 p, float power, float twist) {
        double px = p.x, py = p.y, pz = p.z;
        double wx = px, wy = py, wz = pz;
        double m = wx * wx + wy * wy + wz * wz;
        double dz = 1.0;
        double c = Math.cos(twist), s = Math.sin(twist);
        for (int i = 0; i < BULB_ITERATIONS; i++) {
            m = Math.max(m, 1e-9);
            dz = power * Math.pow(m, 0.5 * (power - 1.0)) * dz + 1.0;
            double r = Math.sqrt(m);
            double beta = power * Math.acos(Math.max(-1.0, Math.min(1.0, wy / r)));
            double alpha = power * Math.atan2(wx, wz);
            double k = Math.pow(m, 0.5 * power);
            double zx = k * Math.sin(beta) * Math.sin(alpha);
            double zy = k * Math.cos(beta);
            double zz = k * Math.sin(beta) * Math.cos(alpha);
            double rx = c * zx + s * zz;
            double rz = -s * zx + c * zz;
            wx = px + rx;
            wy = py + zy;
            wz = pz + rz;
            m = wx * wx + wy * wy + wz * wz;
            if (m > 256.0) break;
        }
        return (float) (0.25 * Math.log(m) * Math.sqrt(m) / dz);
    }

    /** Unit normal of the wall in the camera's current region, pointing INTO the open space (away from
     *  the wall): the gradient of the clearance. eps scales with the local clearance. */
    private void wallNormal(Vector3 p, float d, float power, float twist, Vector3 out) {
        gradient(p, d, power, twist, out);
        if (insideBulb) out.scl(-1f);
    }

    /** Unit gradient of distance() at p (pointing away from the fractal's surface, toward higher field). */
    private static void gradient(Vector3 p, float d, float power, float twist, Vector3 out) {
        float e = MathUtils.clamp(d * 0.5f, 1e-4f, 0.02f);
        float x = p.x, y = p.y, z = p.z;
        Vector3 q = QUERY;
        float dx = distance(q.set(x + e, y, z), power, twist) - distance(q.set(x - e, y, z), power, twist);
        float dy = distance(q.set(x, y + e, z), power, twist) - distance(q.set(x, y - e, z), power, twist);
        float dzz = distance(q.set(x, y, z + e), power, twist) - distance(q.set(x, y, z - e), power, twist);
        out.set(dx, dy, dzz);
        if (out.len2() < 1e-20f) out.set(p).nor(); else out.nor();
    }
}
