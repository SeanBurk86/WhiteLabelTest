package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.math.MathUtils;
import whitelabeltest.enemy.SpeedProfile;

/** Per-bullet runtime state for ramping travel speed over time according to a shared SpeedProfile
 *  spec. Reused across pool recycles via set()/reset() instead of allocating a new ramp per shot -
 *  see AimedEnemyBullet/DrifterBullet/SineBullet/ExplodingAimedBullet for how bullets own one. */
public class SpeedRamp {
    private SpeedProfile profile = SpeedProfile.CONSTANT_SPEED;
    private int phaseIndex;
    private float phaseTimer;

    public void set(SpeedProfile profile) {
        this.profile = profile != null ? profile : SpeedProfile.CONSTANT_SPEED;
        this.phaseIndex = 0;
        this.phaseTimer = 0f;
    }

    /** False means currentSpeed never changes on its own - callers can skip apply() entirely. */
    public boolean isActive() {
        return profile.active;
    }

    /** Advances the ramp by delta and returns the new speed, clamped to the profile's
     *  minSpeed/maxSpeed. Only meaningful to call when isActive() is true. */
    public float apply(float currentSpeed, float delta) {
        float duration = profile.durations[phaseIndex];
        if (duration > 0f) {
            phaseTimer += delta;
            while (phaseTimer >= duration) {
                boolean isLastPhase = phaseIndex == profile.accelerations.length - 1;
                if (isLastPhase && !profile.loop) break;
                phaseTimer -= duration;
                phaseIndex = (phaseIndex + 1) % profile.accelerations.length;
                duration = profile.durations[phaseIndex];
                if (duration <= 0f) break;
            }
        }
        return MathUtils.clamp(currentSpeed + profile.accelerations[phaseIndex] * delta, profile.minSpeed, profile.maxSpeed);
    }

    public void reset() {
        profile = SpeedProfile.CONSTANT_SPEED;
        phaseIndex = 0;
        phaseTimer = 0f;
    }
}