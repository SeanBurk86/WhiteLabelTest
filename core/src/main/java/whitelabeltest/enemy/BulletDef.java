package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** A reusable bullet appearance bundle — size, speed, texture, sprite-sheet layout and frame
 *  duration — referenced by id from FiringPatternDef.bulletId so multiple firing patterns can
 *  share one definition instead of repeating these fields inline. Any field a pattern sets on
 *  itself still takes priority over the value here. */
public class BulletDef implements Json.Serializable {
    public static final int DEFAULT_DAMAGE = 1;

    public String id;
    public float bulletSize = -1f;
    public float bulletSpeed = -1f;
    // World units/sec^2 added to bulletSpeed every frame (negative decelerates) - 0 keeps the
    // classic constant-speed behavior. bulletMinSpeed/bulletMaxSpeed clamp the ramp - -1 (the
    // default) means "no explicit clamp", which resolves to 0 (can decelerate to a stop but not
    // reverse) and unbounded, respectively - see PatternFactory's bulletAcceleration/
    // bulletMinSpeed/bulletMaxSpeed resolvers. Only applies to bullet types whose motion is a
    // simple speed-along-a-direction (aimed/drifting/sine-wave/exploding-radial bullets) - not
    // orbiting or laser bullets, whose "speed" means something else.
    public float bulletAcceleration = 0f;
    public float bulletMinSpeed = -1f;
    public float bulletMaxSpeed = -1f;
    // A sequence of speed-up/slow-down phases a bullet cycles through over its flight, in place of
    // a single constant bulletAcceleration - e.g. accelerate for 1s then decelerate for 1s,
    // optionally looping. When set (non-empty), this wholly overrides bulletAcceleration - see
    // PatternFactory.speedProfile for the resolution order against a firing pattern's own fields.
    public Array<BulletSpeedPhase> bulletSpeedPhases;
    public boolean bulletSpeedPhasesLoop = true;
    // Collision hitbox, independent of the visual sprite - "Circle" or "Rectangle" (null keeps
    // whichever shape this bullet type already defaults to - see HitboxSpec). hitboxScale
    // multiplies the hitbox's auto-derived size (1 = exactly fits the sprite, same as before this
    // field existed); hitboxOffsetX/Y shift the hitbox's center away from the sprite's center, in
    // world units. See PatternFactory.hitboxSpec for the resolution order against a firing
    // pattern's own fields.
    public String hitboxShape;
    public float hitboxScale = -1f;
    public float hitboxOffsetX = 0f;
    public float hitboxOffsetY = 0f;
    public String bulletTexture;
    public int bulletFrameCount = -1;
    public int bulletColumns = -1;
    public int bulletRows = -1;
    public float bulletFrameDuration = -1f;
    public int damage = DEFAULT_DAMAGE;

    public BulletDef() {}

    @Override
    public void write(Json json) {
        json.writeValue("id", id);
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
        if (bulletTexture != null) json.writeValue("bulletTexture", bulletTexture);
        if (bulletFrameCount > 0) json.writeValue("bulletFrameCount", bulletFrameCount);
        if (bulletColumns >= 0) json.writeValue("bulletColumns", bulletColumns);
        if (bulletRows > 0) json.writeValue("bulletRows", bulletRows);
        if (bulletFrameDuration > 0) json.writeValue("bulletFrameDuration", bulletFrameDuration);
        json.writeValue("damage", damage);
    }

    @Override
    public void read(Json json, JsonValue data) {
        id = data.getString("id", null);
        bulletSize = data.getFloat("bulletSize", -1f);
        bulletSpeed = data.getFloat("bulletSpeed", -1f);
        bulletAcceleration = data.getFloat("bulletAcceleration", 0f);
        bulletMinSpeed = data.getFloat("bulletMinSpeed", -1f);
        bulletMaxSpeed = data.getFloat("bulletMaxSpeed", -1f);
        JsonValue phasesData = data.get("bulletSpeedPhases");
        if (phasesData != null) {
            bulletSpeedPhases = new Array<>();
            for (JsonValue child = phasesData.child; child != null; child = child.next) {
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
        bulletTexture = data.getString("bulletTexture", null);
        bulletFrameCount = data.getInt("bulletFrameCount", -1);
        bulletColumns = data.getInt("bulletColumns", -1);
        bulletRows = data.getInt("bulletRows", -1);
        bulletFrameDuration = data.getFloat("bulletFrameDuration", -1f);
        damage = data.getInt("damage", DEFAULT_DAMAGE);
    }
}