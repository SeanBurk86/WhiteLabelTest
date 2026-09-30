package whitelabeltest.enemy.bullets;

import com.badlogic.gdx.math.MathUtils;
import whitelabeltest.enemy.SpeedProfile;

/** A bullet's progress through a shared SpeedProfile; reused across pool cycles. */
public class SpeedRamp {
    private SpeedProfile profile = SpeedProfile.CONSTANT_SPEED;
    private int phaseIndex;
    private float phaseTimer;

    public void set(SpeedProfile profile) {
        this.profile = profile != null ? profile : SpeedProfile.CONSTANT_SPEED;
        this.phaseIndex = 0;
        this.phaseTimer = 0f;
    }

    /** False = constant speed; callers can skip apply(). */
    public boolean isActive() {
        return profile.active;
    }

    /** Advances by delta and returns the new speed, clamped to the profile's min/max. */
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