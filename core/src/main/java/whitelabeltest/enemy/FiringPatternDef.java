package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** A firing pattern from data/firing_patterns/<id>.json, or a nested sub-pattern. Most fields are
 *  "unset" at -1/NaN and fall back to the referenced BulletDef or the pattern's own default; a
 *  pattern's own value always wins. Custom serialization writes only set fields. See the README's
 *  "Firing patterns" table for which type reads which fields. */
public class FiringPatternDef implements Json.Serializable {
    // Bullet sheet layout fallbacks. Each pattern builds its own bullet animation, so patterns sharing
    // a texture must each repeat its layout.
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
    // Speed profile (see BulletDef). A pattern's phase list replaces the BulletDef's entirely.
    public float bulletAcceleration = 0f;
    public float bulletMinSpeed = -1f;
    public float bulletMaxSpeed = -1f;
    public Array<BulletSpeedPhase> bulletSpeedPhases;
    public boolean bulletSpeedPhasesLoop = true;
    // Hitbox overrides (see BulletDef), each resolved independently.
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
    // QuarterCircle's optional fixed aim. Separate from fireAngle because many existing QuarterCircle
    // entries carry a stray "fireAngle": 0 that would otherwise stop them tracking the player.
    public float quarterCircleFixedAngle = Float.NaN;
    public float targetX = Float.NaN;
    public float targetY = Float.NaN;
    public float targetOffsetX = 0f;
    public float targetOffsetY = 0f;
    public float sweepDuration = -1f;
    public float sweepStartAngle = Float.NaN;
    public float sweepEndAngle = Float.NaN;
    // SineWave / Feather wave shape.
    public float amplitude = -1f;
    public float frequency = -1f;
    // Orbiting: each bullet's spin radius and speed (rad/s) around its drifting center.
    public float orbitRadius = -1f;
    public float orbitSpeed = -1f;
    // Wall: a full-width curtain laid out in world X; the gap is gapLaneCount lanes starting at lane
    // index gapLaneStart.
    public float wallMarginX = -1f;
    public float wallSpacing = -1f;
    public int gapLaneStart = -1;
    public int gapLaneCount = -1;
    // Wall: optional, one volley per entry (fireRate apart), each entry that volley's gapLaneStart.
    public int[] gapLaneSequence;
    // RadialNearMiss: volleys of numBullets from the play-area edge whose paths pass
    // nearMissDistance from the player's hitbox; volleyCount volleys, fireRate apart.
    public float nearMissDistance = -1f;
    public int volleyCount = -1;
    // BurstAimed: phase offset (so paired emitters alternate) and seconds between shots in a burst.
    public float phaseOffset = 0f;
    public float burstInterval = -1f;
    // SpawnEnemy: movement pattern id for spawned enemies (null = they don't move).
    public String spawnMovementPattern;
    // Shape: shapePoints is a flat x,y list of dots (world units at scale 1, as seen travelling
    // down). numBullets shapes per volley fanned over spreadDegrees, centered on fireAngle (NaN =
    // aimed). The dots form the picture at shapeScale after shapeFormTime seconds (<= 0 = formed
    // from the start), then drift apart at shapeDriftRatio (1 = no acceleration). See ShapeBullet.
    public float[] shapePoints;
    public float shapeScale = -1f;
    public float shapeFormTime = 1f;
    public float shapeDriftRatio = 1f;
    public boolean shapeFlipX = false;
    public boolean shapeRotateWithDirection = true;
    public Array<FiringPatternDef> patterns;

    public FiringPatternDef() {}

    @Override
    public void write(Json json) {
        if (id != null) json.writeValue("id", id);
        json.writeValue("type", type);
        json.writeValue("fireRate", fireRate);
        json.writeValue("duration", duration);
        if (spawnType != null) json.writeValue("spawnType", spawnType);
        if (spawnMovementPattern != null) json.writeValue("spawnMovementPattern", spawnMovementPattern);
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
        if (!Float.isNaN(quarterCircleFixedAngle)) json.writeValue("quarterCircleFixedAngle", quarterCircleFixedAngle);
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
        if (wallMarginX > 0) json.writeValue("wallMarginX", wallMarginX);
        if (wallSpacing > 0) json.writeValue("wallSpacing", wallSpacing);
        if (gapLaneStart >= 0) json.writeValue("gapLaneStart", gapLaneStart);
        if (gapLaneCount >= 0) json.writeValue("gapLaneCount", gapLaneCount);
        if (gapLaneSequence != null && gapLaneSequence.length > 0) json.writeValue("gapLaneSequence", gapLaneSequence);
        if (nearMissDistance > 0) json.writeValue("nearMissDistance", nearMissDistance);
        if (volleyCount >= 0) json.writeValue("volleyCount", volleyCount);
        if (phaseOffset != 0f) json.writeValue("phaseOffset", phaseOffset);
        if (burstInterval > 0) json.writeValue("burstInterval", burstInterval);
        if (shapePoints != null && shapePoints.length > 0) json.writeValue("shapePoints", shapePoints);
        if (shapeScale > 0) json.writeValue("shapeScale", shapeScale);
        if (shapeFormTime != 1f) json.writeValue("shapeFormTime", shapeFormTime);
        if (shapeDriftRatio != 1f) json.writeValue("shapeDriftRatio", shapeDriftRatio);
        if (shapeFlipX) json.writeValue("shapeFlipX", shapeFlipX);
        if (!shapeRotateWithDirection) json.writeValue("shapeRotateWithDirection", shapeRotateWithDirection);
        if (patterns != null) json.writeValue("patterns", patterns, Array.class, FiringPatternDef.class);
    }

    @Override
    public void read(Json json, JsonValue data) {
        id = data.getString("id", null);
        type = data.getString("type", "None");
        fireRate = data.getFloat("fireRate", 0);
        duration = data.getFloat("duration", 3.0f);
        spawnType = data.getString("spawnType", null);
        spawnMovementPattern = data.getString("spawnMovementPattern", null);
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
        quarterCircleFixedAngle = data.getFloat("quarterCircleFixedAngle", Float.NaN);
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
        wallMarginX = data.getFloat("wallMarginX", -1f);
        wallSpacing = data.getFloat("wallSpacing", -1f);
        gapLaneStart = data.getInt("gapLaneStart", -1);
        gapLaneCount = data.getInt("gapLaneCount", -1);
        JsonValue gapSequenceData = data.get("gapLaneSequence");
        if (gapSequenceData != null) {
            gapLaneSequence = new int[gapSequenceData.size];
            int idx = 0;
            for (JsonValue child = gapSequenceData.child; child != null; child = child.next) {
                gapLaneSequence[idx++] = child.asInt();
            }
        } else {
            gapLaneSequence = null;
        }
        nearMissDistance = data.getFloat("nearMissDistance", -1f);
        volleyCount = data.getInt("volleyCount", -1);
        phaseOffset = data.getFloat("phaseOffset", 0f);
        burstInterval = data.getFloat("burstInterval", -1f);
        JsonValue shapePointsData = data.get("shapePoints");
        shapePoints = shapePointsData != null ? shapePointsData.asFloatArray() : null;
        shapeScale = data.getFloat("shapeScale", -1f);
        shapeFormTime = data.getFloat("shapeFormTime", 1f);
        shapeDriftRatio = data.getFloat("shapeDriftRatio", 1f);
        shapeFlipX = data.getBoolean("shapeFlipX", false);
        shapeRotateWithDirection = data.getBoolean("shapeRotateWithDirection", true);
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
