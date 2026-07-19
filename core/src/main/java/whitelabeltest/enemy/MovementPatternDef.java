package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public class MovementPatternDef implements Json.Serializable {
    public String id;
    public String type = "Straight";
    public float speed = -1f;
    public float movementAngle = Float.NaN;
    public float stopDistance = -1f;
    public float targetX = Float.NaN;
    public float targetY = Float.NaN;
    public float duration = 3.0f;
    public Array<MovementPatternDef> patterns;
    public MovementPatternDef pattern;
    public float offsetX = 0f;
    public float offsetY = 0f;

    public MovementPatternDef() {}

    @Override
    public void write(Json json) {
        if (id != null) json.writeValue("id", id);
        json.writeValue("type", type);
        if (speed > 0) json.writeValue("speed", speed);
        if (!Float.isNaN(movementAngle)) json.writeValue("movementAngle", movementAngle);
        if (stopDistance > 0) json.writeValue("stopDistance", stopDistance);
        if (!Float.isNaN(targetX)) json.writeValue("targetX", targetX);
        if (!Float.isNaN(targetY)) json.writeValue("targetY", targetY);
        json.writeValue("duration", duration);
        if (patterns != null) json.writeValue("patterns", patterns, Array.class, MovementPatternDef.class);
        if (pattern != null) json.writeValue("pattern", pattern, MovementPatternDef.class);
        if (offsetX != 0f) json.writeValue("offsetX", offsetX);
        if (offsetY != 0f) json.writeValue("offsetY", offsetY);
    }

    @Override
    public void read(Json json, JsonValue data) {
        id = data.getString("id", null);
        type = data.getString("type", "Straight");
        speed = data.getFloat("speed", -1f);
        movementAngle = data.getFloat("movementAngle", Float.NaN);
        stopDistance = data.getFloat("stopDistance", -1f);
        targetX = data.getFloat("targetX", Float.NaN);
        targetY = data.getFloat("targetY", Float.NaN);
        duration = data.getFloat("duration", 3.0f);
        offsetX = data.getFloat("offsetX", 0f);
        offsetY = data.getFloat("offsetY", 0f);
        JsonValue patternsData = data.get("patterns");
        if (patternsData != null) {
            patterns = new Array<>();
            for (JsonValue child = patternsData.child; child != null; child = child.next) {
                MovementPatternDef sub = new MovementPatternDef();
                sub.read(json, child);
                patterns.add(sub);
            }
        }
        JsonValue patternData = data.get("pattern");
        if (patternData != null) {
            pattern = new MovementPatternDef();
            pattern.read(json, patternData);
        }
    }
}
