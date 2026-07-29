package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** One step in a bullet's speed-over-time sequence (see BulletDef.bulletSpeedPhases /
 *  FiringPatternDef.bulletSpeedPhases) - the bullet ramps at `acceleration` world units/sec^2
 *  (negative decelerates, 0 holds steady) for `duration` seconds, clamped by the enclosing
 *  bullet/pattern's bulletMinSpeed/bulletMaxSpeed, then advances to the next phase in the list
 *  (wrapping back to the first if the list loops). A non-positive duration holds that phase
 *  forever instead of advancing - only meaningful on the last phase of a non-looping sequence. */
public class BulletSpeedPhase implements Json.Serializable {
    public float acceleration = 0f;
    public float duration = -1f;

    public BulletSpeedPhase() {}

    @Override
    public void write(Json json) {
        json.writeValue("acceleration", acceleration);
        json.writeValue("duration", duration);
    }

    @Override
    public void read(Json json, JsonValue data) {
        acceleration = data.getFloat("acceleration", 0f);
        duration = data.getFloat("duration", -1f);
    }
}