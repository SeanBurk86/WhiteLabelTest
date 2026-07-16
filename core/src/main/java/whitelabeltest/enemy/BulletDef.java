package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** A reusable bullet appearance bundle — size, speed, texture, sprite-sheet layout and frame
 *  duration — referenced by id from FiringPatternDef.bulletId so multiple firing patterns can
 *  share one definition instead of repeating these fields inline. Any field a pattern sets on
 *  itself still takes priority over the value here. */
public class BulletDef implements Json.Serializable {
    public String id;
    public float bulletSize = -1f;
    public float bulletSpeed = -1f;
    public String bulletTexture;
    public int bulletFrameCount = -1;
    public int bulletColumns = -1;
    public int bulletRows = -1;
    public float bulletFrameDuration = -1f;

    public BulletDef() {}

    @Override
    public void write(Json json) {
        json.writeValue("id", id);
        if (bulletSize > 0) json.writeValue("bulletSize", bulletSize);
        if (bulletSpeed > 0) json.writeValue("bulletSpeed", bulletSpeed);
        if (bulletTexture != null) json.writeValue("bulletTexture", bulletTexture);
        if (bulletFrameCount > 0) json.writeValue("bulletFrameCount", bulletFrameCount);
        if (bulletColumns >= 0) json.writeValue("bulletColumns", bulletColumns);
        if (bulletRows > 0) json.writeValue("bulletRows", bulletRows);
        if (bulletFrameDuration > 0) json.writeValue("bulletFrameDuration", bulletFrameDuration);
    }

    @Override
    public void read(Json json, JsonValue data) {
        id = data.getString("id", null);
        bulletSize = data.getFloat("bulletSize", -1f);
        bulletSpeed = data.getFloat("bulletSpeed", -1f);
        bulletTexture = data.getString("bulletTexture", null);
        bulletFrameCount = data.getInt("bulletFrameCount", -1);
        bulletColumns = data.getInt("bulletColumns", -1);
        bulletRows = data.getInt("bulletRows", -1);
        bulletFrameDuration = data.getFloat("bulletFrameDuration", -1f);
    }
}