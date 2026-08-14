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
    // QuarterCircle's optional fixed aim override - see QuarterCircleFiring.fixedAimAngleDeg. A
    // separate field from fireAngle (Laser's own fixed angle) rather than reusing it: this data
    // file is machine-exported by an editor that dumps every field on every pattern regardless of
    // whether that pattern type reads it, so countless existing QuarterCircle entries already carry
    // a harmless-until-now "fireAngle": 0 left over from that dump - wiring QuarterCircle to read
    // fireAngle would have silently switched every one of them from tracking the player to firing
    // fixed at 0 degrees.
    public float quarterCircleFixedAngle = Float.NaN;
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
    // Wall's full-width bullet curtain - see WallFiring. wallMarginX/wallSpacing lay bullets out in
    // world-space X (not relative to the firing enemy); gapLaneStart/gapLaneCount carve the hole
    // out of that curtain by lane INDEX rather than a raw X-distance window, so the gap is always
    // exactly gapLaneCount lanes wide no matter what gapLaneStart is set to.
    public float wallMarginX = -1f;
    public float wallSpacing = -1f;
    public int gapLaneStart = -1;
    public int gapLaneCount = -1;
    // Optional - one Wall volley per entry, fireRate seconds apart, each entry becoming that
    // volley's gapLaneStart. Null/absent keeps Wall's original single-volley behavior. See
    // WallFiring's class doc for why this is how a smoothly weaving, vertically dense column of
    // walls is built instead of authoring several near-duplicate patterns/spawn events.
    public int[] gapLaneSequence;
    // RadialNearMiss's "surround but don't touch" volleys - see RadialNearMissFiring. numBullets is
    // per volley, fireRate is seconds between volleys, volleyCount caps how many volleys fire
    // before the pattern goes idle. Each bullet spawns projected onto the play area's edge (using
    // the worldWidth/worldHeight PatternFactory.createFiring is called with, not a config field
    // here) so it never spawns already past AimedEnemyBullet's own off-screen cull bounds.
    // nearMissDistance is how far each bullet's straight path passes from the player's hitbox -
    // larger than the hitbox radius but small enough to still read as "just barely missed" against
    // the player's much bigger sprite.
    public float nearMissDistance = -1f;
    public int volleyCount = -1;
    // BurstAimed's idle-vs-burst phase - see BurstAimedFiring's phaseOffset constructor param. 0
    // (the default) is the original single-emitter behavior; two BurstAimed patterns fired from
    // side-by-side emitters can set this to land in opposite phase instead of bursting in lockstep.
    public float phaseOffset = 0f;
    // BurstAimed's seconds-between-shots-within-a-burst - see BurstAimedFiring's burstInterval
    // constructor param. -1 (the default) falls back to BurstAimedFiring's own 0.15s default.
    public float burstInterval = -1f;
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
