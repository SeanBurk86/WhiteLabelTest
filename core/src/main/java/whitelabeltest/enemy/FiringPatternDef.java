package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

public class FiringPatternDef implements Json.Serializable {
    // A bullet's sprite-sheet layout and animation speed are purely per-pattern settings (the
    // bulletFrame*/bulletColumns/bulletRows fields below) — these are their fallbacks when a
    // pattern doesn't specify its own. Every pattern builds its own bullet animation (reusing the
    // enemy's default bulletTexture if the pattern doesn't set its own texture), so two patterns
    // sharing the same default texture must each repeat its layout if they both fire bullets.
    public static final float DEFAULT_BULLET_FRAME_DURATION = 0.1f;
    public static final int DEFAULT_BULLET_FRAME_COUNT = 1;
    public static final int DEFAULT_BULLET_COLUMNS = 0;
    public static final int DEFAULT_BULLET_ROWS = 1;

    public String type = "None";
    public float fireRate = 0;
    public float duration = 3.0f;
    public String spawnType;
    public float bulletSize = -1f;
    public float bulletSpeed = -1f;
    public String bulletTexture;
    public int bulletFrameCount = -1;
    public int bulletColumns = -1;
    public int bulletRows = -1;
    public float bulletFrameDuration = -1f;
    public float spreadDegrees = -1f;
    public int numBullets = -1;
    public float offsetX = 0f;
    public float offsetY = 0f;
    public float length = -1f;
    public float angularSpeed = 0f;
    public float fireAngle = Float.NaN;
    public float targetX = Float.NaN;
    public float targetY = Float.NaN;
    public float sweepDuration = -1f;
    public float sweepStartAngle = Float.NaN;
    public float sweepEndAngle = Float.NaN;
    public Array<FiringPatternDef> patterns;

    public FiringPatternDef() {}

    @Override
    public void write(Json json) {
        json.writeValue("type", type);
        json.writeValue("fireRate", fireRate);
        json.writeValue("duration", duration);
        if (spawnType != null) json.writeValue("spawnType", spawnType);
        if (bulletSize > 0) json.writeValue("bulletSize", bulletSize);
        if (bulletSpeed > 0) json.writeValue("bulletSpeed", bulletSpeed);
        if (bulletTexture != null) json.writeValue("bulletTexture", bulletTexture);
        if (bulletFrameCount > 0) json.writeValue("bulletFrameCount", bulletFrameCount);
        if (bulletColumns >= 0) json.writeValue("bulletColumns", bulletColumns);
        if (bulletRows > 0) json.writeValue("bulletRows", bulletRows);
        if (bulletFrameDuration > 0) json.writeValue("bulletFrameDuration", bulletFrameDuration);
        if (spreadDegrees > 0) json.writeValue("spreadDegrees", spreadDegrees);
        if (numBullets > 0) json.writeValue("numBullets", numBullets);
        if (offsetX != 0f) json.writeValue("offsetX", offsetX);
        if (offsetY != 0f) json.writeValue("offsetY", offsetY);
        if (length > 0) json.writeValue("length", length);
        if (angularSpeed != 0f) json.writeValue("angularSpeed", angularSpeed);
        if (!Float.isNaN(fireAngle)) json.writeValue("fireAngle", fireAngle);
        if (!Float.isNaN(targetX)) json.writeValue("targetX", targetX);
        if (!Float.isNaN(targetY)) json.writeValue("targetY", targetY);
        if (sweepDuration > 0) json.writeValue("sweepDuration", sweepDuration);
        if (!Float.isNaN(sweepStartAngle)) json.writeValue("sweepStartAngle", sweepStartAngle);
        if (!Float.isNaN(sweepEndAngle)) json.writeValue("sweepEndAngle", sweepEndAngle);
        if (patterns != null) json.writeValue("patterns", patterns);
    }

    @Override
    public void read(Json json, JsonValue data) {
        type = data.getString("type", "None");
        fireRate = data.getFloat("fireRate", 0);
        duration = data.getFloat("duration", 3.0f);
        spawnType = data.getString("spawnType", null);
        bulletSize = data.getFloat("bulletSize", -1f);
        bulletSpeed = data.getFloat("bulletSpeed", -1f);
        bulletTexture = data.getString("bulletTexture", null);
        bulletFrameCount = data.getInt("bulletFrameCount", -1);
        bulletColumns = data.getInt("bulletColumns", -1);
        bulletRows = data.getInt("bulletRows", -1);
        bulletFrameDuration = data.getFloat("bulletFrameDuration", -1f);
        spreadDegrees = data.getFloat("spreadDegrees", -1f);
        numBullets = data.getInt("numBullets", -1);
        offsetX = data.getFloat("offsetX", 0f);
        offsetY = data.getFloat("offsetY", 0f);
        length = data.getFloat("length", -1f);
        angularSpeed = data.getFloat("angularSpeed", 0f);
        fireAngle = data.getFloat("fireAngle", Float.NaN);
        targetX = data.getFloat("targetX", Float.NaN);
        targetY = data.getFloat("targetY", Float.NaN);
        sweepDuration = data.getFloat("sweepDuration", -1f);
        sweepStartAngle = data.getFloat("sweepStartAngle", Float.NaN);
        sweepEndAngle = data.getFloat("sweepEndAngle", Float.NaN);
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
