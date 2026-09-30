package whitelabeltest.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** A reusable bullet definition from data/bullets.json (size, speed profile, hitbox, texture,
 *  damage), referenced by FiringPatternDef.bulletId. A pattern's own fields override these. */
public class BulletDef implements Json.Serializable {
    public static final int DEFAULT_DAMAGE = 1;

    public String id;
    public float bulletSize = -1f;
    public float bulletSpeed = -1f;
    // Acceleration in world units/sec^2 (negative decelerates; 0 = constant speed), clamped to
    // [min, max] speed; -1 = no clamp (min resolves to 0, max to unbounded). Only for bullets that
    // move along a direction, not orbiting or laser bullets.
    public float bulletAcceleration = 0f;
    public float bulletMinSpeed = -1f;
    public float bulletMaxSpeed = -1f;
    // Optional speed-up/slow-down phases (optionally looping); replaces bulletAcceleration when set.
    public Array<BulletSpeedPhase> bulletSpeedPhases;
    public boolean bulletSpeedPhasesLoop = true;
    // Hitbox independent of the sprite: "Circle"/"Rectangle" (null = the bullet class's default),
    // size multiplier (1 = fits the sprite) and center offset in world units.
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