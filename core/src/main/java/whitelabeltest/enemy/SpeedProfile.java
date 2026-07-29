package whitelabeltest.enemy;

/** Immutable spec for how a bullet's travel speed changes after it spawns - built once per firing
 *  pattern by PatternFactory (see PatternFactory.speedProfile) and shared by every bullet that
 *  pattern fires, with each bullet tracking its own progress through it via a SpeedRamp. Either a
 *  constant acceleration (0 keeps the classic constant-speed behavior) or a sequence of phases a
 *  bullet cycles through over its flight - see BulletDef.bulletSpeedPhases for how a pattern
 *  authors a "speed up then slow down" sequence instead of a single monotonic ramp. */
public class SpeedProfile {
    public static final SpeedProfile CONSTANT_SPEED = new SpeedProfile(new float[]{0f}, new float[]{-1f}, false, 0f, Float.MAX_VALUE);

    public final float[] accelerations;
    // Seconds spent in each phase before advancing to the next - a non-positive duration holds
    // that phase forever instead of advancing (see BulletSpeedPhase).
    public final float[] durations;
    // Whether to wrap back to phase 0 after the last phase's duration elapses, instead of holding
    // the last phase forever.
    public final boolean loop;
    public final float minSpeed;
    public final float maxSpeed;
    // Precomputed so bullets can skip ramp bookkeeping entirely for the common non-ramping case.
    public final boolean active;

    public SpeedProfile(float[] accelerations, float[] durations, boolean loop, float minSpeed, float maxSpeed) {
        this.accelerations = accelerations;
        this.durations = durations;
        this.loop = loop;
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.active = accelerations.length > 1 || accelerations[0] != 0f;
    }

    /** The classic single-acceleration case, expressed as a one-phase profile. */
    public static SpeedProfile constant(float acceleration, float minSpeed, float maxSpeed) {
        if (acceleration == 0f && minSpeed <= 0f && maxSpeed >= Float.MAX_VALUE) return CONSTANT_SPEED;
        return new SpeedProfile(new float[]{acceleration}, new float[]{-1f}, false, minSpeed, maxSpeed);
    }
}