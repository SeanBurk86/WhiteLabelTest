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

    // --- WaypointPath fields (see WaypointPathMovement/PatternFactory's "WaypointPath" case) ---

    // Per-waypoint (set on a "MoveToPoint" leg inside a "WaypointPath"): Cardinal-spline tension
    // at this control point - 0 = full smooth curve, 1 = straight corner (tangent magnitude scales
    // by (1 - tension); see WaypointSpline).
    public float tension = 0.5f;
    // Per-waypoint: seconds to pause here once reached, before continuing to the next point.
    public float waitSeconds = 0f;
    // Per-waypoint: "path" (face travel direction, the default/only behavior every other movement
    // type already has), "player" (turn toward the player at aimSpeed deg/sec), or "fixed" (hold
    // fixedAngle deg) - see WaypointPathMovement.
    public String orientation = "path";
    public float aimSpeed = 180f;
    public float fixedAngle = 0f;
    // Per-waypoint: a one-shot sound cue fired the moment this point is reached - null (the
    // default) fires nothing. See AudioManager.playCueSound(path, volume, pitch).
    public String soundName;
    public float soundVolume = 0.7f;
    public float soundPitch = 1f;
    public float soundPitchVariation = 0f;
    // Per-waypoint: swaps the spawning enemy's active firing pattern the moment this point is
    // reached, to whichever firing-pattern id EnemyDefinition.weaponSets maps `weaponSet` to - see
    // BaseEnemy's WaypointCue handling. changeWeaponSet gates this the same way Trigger.sound/etc.
    // gate their own optional actions, rather than treating a blank weaponSet as "do nothing".
    public boolean changeWeaponSet = false;
    public String weaponSet;

    // Path-level (set on the top "WaypointPath" def itself, ignored on an individual leg):
    public boolean closePath = false;
    public float globalSpeed = 1f;
    public boolean flipX = false;
    public boolean flipY = false;

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
        if (tension != 0.5f) json.writeValue("tension", tension);
        if (waitSeconds != 0f) json.writeValue("waitSeconds", waitSeconds);
        if (!"path".equals(orientation)) json.writeValue("orientation", orientation);
        if (aimSpeed != 180f) json.writeValue("aimSpeed", aimSpeed);
        if (fixedAngle != 0f) json.writeValue("fixedAngle", fixedAngle);
        if (soundName != null) json.writeValue("soundName", soundName);
        if (soundVolume != 0.7f) json.writeValue("soundVolume", soundVolume);
        if (soundPitch != 1f) json.writeValue("soundPitch", soundPitch);
        if (soundPitchVariation != 0f) json.writeValue("soundPitchVariation", soundPitchVariation);
        if (changeWeaponSet) json.writeValue("changeWeaponSet", true);
        if (weaponSet != null) json.writeValue("weaponSet", weaponSet);
        if (closePath) json.writeValue("closePath", true);
        if (globalSpeed != 1f) json.writeValue("globalSpeed", globalSpeed);
        if (flipX) json.writeValue("flipX", true);
        if (flipY) json.writeValue("flipY", true);
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
        tension = data.getFloat("tension", 0.5f);
        waitSeconds = data.getFloat("waitSeconds", 0f);
        orientation = data.getString("orientation", "path");
        aimSpeed = data.getFloat("aimSpeed", 180f);
        fixedAngle = data.getFloat("fixedAngle", 0f);
        soundName = data.getString("soundName", null);
        soundVolume = data.getFloat("soundVolume", 0.7f);
        soundPitch = data.getFloat("soundPitch", 1f);
        soundPitchVariation = data.getFloat("soundPitchVariation", 0f);
        changeWeaponSet = data.getBoolean("changeWeaponSet", false);
        weaponSet = data.getString("weaponSet", null);
        closePath = data.getBoolean("closePath", false);
        globalSpeed = data.getFloat("globalSpeed", 1f);
        flipX = data.getBoolean("flipX", false);
        flipY = data.getBoolean("flipY", false);
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
