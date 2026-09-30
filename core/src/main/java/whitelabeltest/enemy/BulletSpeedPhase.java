package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** One step of a bullet speed sequence: accelerate at `acceleration` (units/sec^2) for `duration`
 *  seconds, then move to the next phase. duration <= 0 holds this phase forever. */
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