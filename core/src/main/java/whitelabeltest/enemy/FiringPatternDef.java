package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public class FiringPatternDef implements Json.Serializable {
    public String type = "None";
    public float fireRate = 0;
    public float duration = 3.0f; // used by parent Sequence to know how long to run this pattern
    public Array<FiringPatternDef> patterns; // sub-patterns for Sequence / Combined

    public FiringPatternDef() {}

    @Override
    public void write(Json json) {
        json.writeValue("type", type);
        json.writeValue("fireRate", fireRate);
        json.writeValue("duration", duration);
        if (patterns != null) json.writeValue("patterns", patterns);
    }

    @Override
    public void read(Json json, JsonValue data) {
        type = data.getString("type", "None");
        fireRate = data.getFloat("fireRate", 0);
        duration = data.getFloat("duration", 3.0f);
        JsonValue patternsData = data.get("patterns");
        if (patternsData != null) {
            patterns = new Array<>();
            for (JsonValue child = patternsData.child; child != null; child = child.next) {
                FiringPatternDef sub = new FiringPatternDef();
                sub.read(json, child);
                patterns.add(sub);
            }
        }
    }
}