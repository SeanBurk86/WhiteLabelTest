package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** A reusable death-explosion effect — particle textures/sprite-sheet layout, size, speed,
 *  timing and positioning spread — referenced by id from EnemyDefinition.explosionPattern, the
 *  same way FiringPatternDef/MovementPatternDef let enemies share a behavior by id.
 *
 *  Leaf patterns have type "Burst" (the default) and describe one particle burst. A pattern can
 *  also be "Combined" (its sub-patterns all play at once, each still using its own offsetX/
 *  offsetY) or "Sequence" (its sub-patterns play one at a time — each plays for its own
 *  `duration` before the next stage starts, and the final stage runs until its own particles
 *  finish rather than being cut off), letting an explosion be built out of staged sub-effects
 *  the same way Combined/Sequence firing patterns are built out of sub-patterns. */
public class ExplosionPatternDef implements Json.Serializable {
    public static final int DEFAULT_PARTICLE_COUNT = 8;
    public static final int DEFAULT_FRAME_COUNT = 9;
    public static final int DEFAULT_COLUMNS = 3;
    public static final int DEFAULT_ROWS = 3;
    public static final float DEFAULT_FRAME_DURATION = 0.1f;
    public static final float DEFAULT_SIZE_SCALE = 2.0f;
    public static final float DEFAULT_SIZE_MIN = 0.5f;
    public static final float DEFAULT_SIZE_MAX = 1.2f;
    public static final float DEFAULT_SPEED_MIN = 2f;
    public static final float DEFAULT_SPEED_MAX = 5f;
    public static final float DEFAULT_ANGLE_MIN = 0f;
    public static final float DEFAULT_ANGLE_MAX = 360f;
    public static final float DEFAULT_VELOCITY_DAMPING = 0.95f;
    public static final float DEFAULT_START_DELAY_MAX = 0.1f;
    public static final float DEFAULT_DURATION = 0.5f;

    public String id;
    public String type = "Burst";
    public float duration = DEFAULT_DURATION;
    public Array<ExplosionPatternDef> patterns;
    public Array<String> textures;
    public int particleCount = DEFAULT_PARTICLE_COUNT;
    public int frameCount = DEFAULT_FRAME_COUNT;
    public int columns = DEFAULT_COLUMNS;
    public int rows = DEFAULT_ROWS;
    public float frameDuration = DEFAULT_FRAME_DURATION;
    public float sizeScale = DEFAULT_SIZE_SCALE;
    public float sizeMin = DEFAULT_SIZE_MIN;
    public float sizeMax = DEFAULT_SIZE_MAX;
    public float speedMin = DEFAULT_SPEED_MIN;
    public float speedMax = DEFAULT_SPEED_MAX;
    public float angleMin = DEFAULT_ANGLE_MIN;
    public float angleMax = DEFAULT_ANGLE_MAX;
    public float velocityDamping = DEFAULT_VELOCITY_DAMPING;
    public float startDelayMax = DEFAULT_START_DELAY_MAX;
    public float offsetX = 0f;
    public float offsetY = 0f;

    public ExplosionPatternDef() {}

    @Override
    public void write(Json json) {
        json.writeValue("id", id);
        json.writeValue("type", type);
        json.writeValue("duration", duration);
        if (patterns != null) json.writeValue("patterns", patterns);
        if (textures != null) json.writeValue("textures", textures);
        json.writeValue("particleCount", particleCount);
        json.writeValue("frameCount", frameCount);
        json.writeValue("columns", columns);
        json.writeValue("rows", rows);
        json.writeValue("frameDuration", frameDuration);
        json.writeValue("sizeScale", sizeScale);
        json.writeValue("sizeMin", sizeMin);
        json.writeValue("sizeMax", sizeMax);
        json.writeValue("speedMin", speedMin);
        json.writeValue("speedMax", speedMax);
        json.writeValue("angleMin", angleMin);
        json.writeValue("angleMax", angleMax);
        json.writeValue("velocityDamping", velocityDamping);
        json.writeValue("startDelayMax", startDelayMax);
        if (offsetX != 0f) json.writeValue("offsetX", offsetX);
        if (offsetY != 0f) json.writeValue("offsetY", offsetY);
    }

    @Override
    public void read(Json json, JsonValue data) {
        id = data.getString("id", null);
        type = data.getString("type", "Burst");
        duration = data.getFloat("duration", DEFAULT_DURATION);
        patterns = null;
        JsonValue patternsData = data.get("patterns");
        if (patternsData != null) {
            patterns = new Array<>();
            for (JsonValue child = patternsData.child; child != null; child = child.next) {
                ExplosionPatternDef sub = new ExplosionPatternDef();
                sub.read(json, child);
                patterns.add(sub);
            }
        }
        textures = null;
        JsonValue texturesData = data.get("textures");
        if (texturesData != null) {
            textures = new Array<>();
            for (JsonValue child = texturesData.child; child != null; child = child.next) {
                textures.add(child.asString());
            }
        }
        particleCount = data.getInt("particleCount", DEFAULT_PARTICLE_COUNT);
        frameCount = data.getInt("frameCount", DEFAULT_FRAME_COUNT);
        columns = data.getInt("columns", DEFAULT_COLUMNS);
        rows = data.getInt("rows", DEFAULT_ROWS);
        frameDuration = data.getFloat("frameDuration", DEFAULT_FRAME_DURATION);
        sizeScale = data.getFloat("sizeScale", DEFAULT_SIZE_SCALE);
        sizeMin = data.getFloat("sizeMin", DEFAULT_SIZE_MIN);
        sizeMax = data.getFloat("sizeMax", DEFAULT_SIZE_MAX);
        speedMin = data.getFloat("speedMin", DEFAULT_SPEED_MIN);
        speedMax = data.getFloat("speedMax", DEFAULT_SPEED_MAX);
        angleMin = data.getFloat("angleMin", DEFAULT_ANGLE_MIN);
        angleMax = data.getFloat("angleMax", DEFAULT_ANGLE_MAX);
        velocityDamping = data.getFloat("velocityDamping", DEFAULT_VELOCITY_DAMPING);
        startDelayMax = data.getFloat("startDelayMax", DEFAULT_START_DELAY_MAX);
        offsetX = data.getFloat("offsetX", 0f);
        offsetY = data.getFloat("offsetY", 0f);
    }
}
