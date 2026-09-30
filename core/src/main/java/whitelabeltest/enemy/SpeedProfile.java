package whitelabeltest.enemy;

/** How a bullet's speed changes over its flight: one constant acceleration or a sequence of phases.
 *  Resolved once per firing pattern; each bullet tracks its progress with a SpeedRamp. */
public class SpeedProfile {
    public static final SpeedProfile CONSTANT_SPEED = new SpeedProfile(new float[]{0f}, new float[]{-1f}, false, 0f, Float.MAX_VALUE);

    public final float[] accelerations;
    // Seconds per phase; <= 0 holds that phase forever.
    public final float[] durations;
    // Wrap to phase 0 after the last phase instead of holding it.
    public final boolean loop;
    public final float minSpeed;
    public final float maxSpeed;
    // False for constant speed, so bullets can skip ramp work.
    public final boolean active;

    public SpeedProfile(float[] accelerations, float[] durations, boolean loop, float minSpeed, float maxSpeed) {
        this.accelerations = accelerations;
        this.durations = durations;
        this.loop = loop;
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.active = accelerations.length > 1 || accelerations[0] != 0f;
    }

    /** A single constant acceleration as a one-phase profile. */
    public static SpeedProfile constant(float acceleration, float minSpeed, float maxSpeed) {
        if (acceleration == 0f && minSpeed <= 0f && maxSpeed >= Float.MAX_VALUE) return CONSTANT_SPEED;
        return new SpeedProfile(new float[]{acceleration}, new float[]{-1f}, false, minSpeed, maxSpeed);
    }
}