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

    public String id;
    public String type = "None";
    public float fireRate = 0;
    public float duration = 3.0f;
    public String spawnType;
    public String bulletId;
    public float bulletSize = -1f;
    public float bulletSpeed = -1f;
    // See BulletDef's matching fields for the full explanation - this pattern's own value always
    // wins over its referenced BulletDef's, same as bulletSpeed above.
    public float bulletAcceleration = 0f;
    public float bulletMinSpeed = -1f;
    public float bulletMaxSpeed = -1f;
    // See BulletDef.bulletSpeedPhases for the full explanation - this pattern's own list always
    // wins wholesale over its referenced BulletDef's (the two are never merged), same as
    // bulletAcceleration above.
    public Array<BulletSpeedPhase> bulletSpeedPhases;
    public boolean bulletSpeedPhasesLoop = true;
    // See BulletDef's matching fields for the full explanation - each resolved independently
    // against the referenced BulletDef's, same as bulletSize/bulletSpeed above.
    public String hitboxShape;
    public float hitboxScale = -1f;
    public float hitboxOffsetX = 0f;
    public float hitboxOffsetY = 0f;
    public int bulletDamage = -1;
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
    public float targetOffsetX = 0f;
    public float targetOffsetY = 0f;
    public float sweepDuration = -1f;
    public float sweepStartAngle = Float.NaN;
    public float sweepEndAngle = Float.NaN;
    // SineWave's wave shape - see SineWaveFiring.DEFAULT_AMPLITUDE/DEFAULT_FREQUENCY.
    public float amplitude = -1f;
    public float frequency = -1f;
    // Orbiting's per-bullet spin around its own drifting center - see
    // OrbitingFiring.DEFAULT_ORBIT_RADIUS/DEFAULT_ORBIT_SPEED (radians/second).
    public float orbitRadius = -1f;
    public float orbitSpeed = -1f;
    public Array<FiringPatternDef> patterns;

    public FiringPatternDef() {}

    @Override
    public void write(Json json) {
        if (id != null) json.writeValue("id", id);
        json.writeValue("type", type);
        json.writeValue("fireRate", fireRate);
        json.writeValue("duration", duration);
        if (spawnType != null) json.writeValue("spawnType", spawnType);
        if (bulletId != null) json.writeValue("bulletId", bulletId);
        if (bulletSize > 0) json.writeValue("bulletSize", bulletSize);
        if (bulletSpeed > 0) json.writeValue("bulletSpeed", bulletSpeed);
        if (bulletAcceleration != 0f) json.writeValue("bulletAcceleration", bulletAcceleration);
        if (bulletMinSpeed > 0) json.writeValue("bulletMinSpeed", bulletMinSpeed);
        if (bulletMaxSpeed > 0) json.writeValue("bulletMaxSpeed", bulletMaxSpeed);
        if (bulletSpeedPhases != null) json.writeValue("bulletSpeedPhases", bulletSpeedPhases, Array.class, BulletSpeedPhase.class);
        if (!bulletSpeedPhasesLoop) json.writeValue("bulletSpeedPhasesLoop", bulletSpeedPhasesLoop);
        if (hitboxShape != null) json.writeValue("hitboxShape", hitboxShape);
        if (hitboxScale > 0) json.writeValue("hitboxScale", hitboxScale);
        if (hitboxOffsetX != 0f) json.writeValue("hitboxOffsetX", hitboxOffsetX);
        if (hitboxOffsetY != 0f) json.writeValue("hitboxOffsetY", hitboxOffsetY);
        if (bulletDamage > 0) json.writeValue("bulletDamage", bulletDamage);
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
        if (targetOffsetX != 0f) json.writeValue("targetOffsetX", targetOffsetX);
        if (targetOffsetY != 0f) json.writeValue("targetOffsetY", targetOffsetY);
        if (sweepDuration > 0) json.writeValue("sweepDuration", sweepDuration);
        if (!Float.isNaN(sweepStartAngle)) json.writeValue("sweepStartAngle", sweepStartAngle);
        if (!Float.isNaN(sweepEndAngle)) json.writeValue("sweepEndAngle", sweepEndAngle);
        if (amplitude > 0) json.writeValue("amplitude", amplitude);
        if (frequency > 0) json.writeValue("frequency", frequency);
        if (orbitRadius > 0) json.writeValue("orbitRadius", orbitRadius);
        if (orbitSpeed > 0) json.writeValue("orbitSpeed", orbitSpeed);
        if (patterns != null) json.writeValue("patterns", patterns, Array.class, FiringPatternDef.class);
    }

    @Override
    public void read(Json json, JsonValue data) {
        id = data.getString("id", null);
        type = data.getString("type", "None");
        fireRate = data.getFloat("fireRate", 0);
        duration = data.getFloat("duration", 3.0f);
        spawnType = data.getString("spawnType", null);
        bulletId = data.getString("bulletId", null);
        bulletSize = data.getFloat("bulletSize", -1f);
        bulletSpeed = data.getFloat("bulletSpeed", -1f);
        bulletAcceleration = data.getFloat("bulletAcceleration", 0f);
        bulletMinSpeed = data.getFloat("bulletMinSpeed", -1f);
        bulletMaxSpeed = data.getFloat("bulletMaxSpeed", -1f);
        JsonValue speedPhasesData = data.get("bulletSpeedPhases");
        if (speedPhasesData != null) {
            bulletSpeedPhases = new Array<>();
            for (JsonValue child = speedPhasesData.child; child != null; child = child.next) {
                BulletSpeedPhase phase = new BulletSpeedPhase();
                phase.read(json, child);
                bulletSpeedPhases.add(phase);
            }
        }
        bulletSpeedPhasesLoop = data.getBoolean("bulletSpeedPhasesLoop", true);
        hitboxShape = data.getString("hitboxShape", null);
        hitboxScale = data.getFloat("hitboxScale", -1f);
        hitboxOffsetX = data.getFloat("hitboxOffsetX", 0f);
        hitboxOffsetY = data.getFloat("hitboxOffsetY", 0f);
        bulletDamage = data.getInt("bulletDamage", -1);
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
        targetOffsetX = data.getFloat("targetOffsetX", 0f);
        targetOffsetY = data.getFloat("targetOffsetY", 0f);
        sweepDuration = data.getFloat("sweepDuration", -1f);
        sweepStartAngle = data.getFloat("sweepStartAngle", Float.NaN);
        sweepEndAngle = data.getFloat("sweepEndAngle", Float.NaN);
        amplitude = data.getFloat("amplitude", -1f);
        frequency = data.getFloat("frequency", -1f);
        orbitRadius = data.getFloat("orbitRadius", -1f);
        orbitSpeed = data.getFloat("orbitSpeed", -1f);
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
