package whitelabeltest.gamemanagers.background;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** MandelbulbShader's camera: a collision-safe autopilot rather than a fixed path, since the scene
 *  is full of walls. The rendered surface has a thin solid skin, leaving two open regions: outside,
 *  and a hollow chamber inside. The camera orbits outside, then on the dive punches straight through
 *  the skin (the one time it ignores walls) and flies around the chamber.
 *
 *  Steering heads for a goal while sliding along and leaning away from walls, at a speed
 *  proportional to wall clearance so it can't overshoot into one. Everything is integrated frame to
 *  frame with a smoothed heading, so nothing snaps when parameters change.
 *
 *  distance() must match field() in mandelbulb.frag (iterations, twist, formula), and SHELL must
 *  match the shader's SHELL. */
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
    // The crossing ends near the center with room around it: the open middle, not a thin pocket
    // between the skin and an internal ridge.
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
        // Start the view where step() will steer it, so it doesn't swing around at the start.
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

    /** @param orbitTime,diveTime separate clocks for the two goals, so blending goals never changes
     *  either one's speed
     *  @param inside 0 = orbit outside ... 1 = dive in */
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

        // Look ahead, blended toward the bulb when outside so the view never drifts into empty space.
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
            // A morphing wall swept over the camera; return to the last open-space point.
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

    /** Drives straight at the center through the skin and ridges until it reaches the open middle.
     *  On timeout it backs out to the last open point and retries later. */
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

    /** Outside orbit goal with a swinging radius and wandering pitch. Stays outside the surface
     *  (radius ~1.2): a goal inside the solid pins the camera to the wall and makes it jerk. */
    static Vector3 orbitGoal(float t, Vector3 out) {
        float yaw = t * 0.5f;
        float pitch = 0.95f * MathUtils.sin(t * 0.37f);
        float radius = 1.85f + 0.55f * MathUtils.sin(t * 0.53f + 0.6f);
        return out.set(MathUtils.cos(pitch) * MathUtils.sin(yaw), MathUtils.sin(pitch), MathUtils.cos(pitch) * MathUtils.cos(yaw)).scl(radius);
    }

    /** Interior goal: a slowly sweeping point near the middle, so the camera keeps touring the chamber. */
    static Vector3 diveGoal(float t, Vector3 out) {
        float yaw = t * 0.45f;
        float pitch = 0.9f * MathUtils.sin(t * 0.31f + 1.0f);
        return out.set(MathUtils.cos(pitch) * MathUtils.sin(yaw), MathUtils.sin(pitch), MathUtils.cos(pitch) * MathUtils.cos(yaw)).scl(0.4f);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = MathUtils.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** The field at p, mirroring field() in mandelbulb.frag: positive outside, negative inside.
     *  Computed in double for steady clearance tests. */
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

    /** Unit wall normal pointing into the open region (the clearance gradient). */
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
