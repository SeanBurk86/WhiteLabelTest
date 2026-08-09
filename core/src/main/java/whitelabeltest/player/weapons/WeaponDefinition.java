package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class WeaponDefinition {
    public String id;
    public String type; // e.g., "Direct", "Spline", "Orbit"
    public String texture;
    public int frameCount;
    public int columns = 0;
    public int rows = 1;
    public float frameDuration = 0.05f;
    public float size;

    // Indexed by level - 1. Each must have one entry per weapon level.
    public int[] damageByLevel;
    public float[] fireRateByLevel;
    public float[] speedByLevel;

    public float chainWindow = 2.0f;
    public float shootSpeedMultiplier = 0.75f;

    // Orbit specific
    public float radius;
    public float rotationSpeed;

    // BasicWeapon's Hyper Attack (see Player's halo-dash state machine) - the graze halo launches
    // forward haloDashDistance at haloDashSpeed. Basic's own re-press reattaches at
    // haloFastReturnSpeed, noticeably snappier than a fizzled recall (switching weapons away
    // mid-flight) or Thunderbolt's return leg, both of which glide back at haloReturnSpeed instead.
    public float haloDashDistance;
    public float haloDashSpeed;
    public float haloReturnSpeed;
    public float haloFastReturnSpeed;
    public int haloDashDamage;

    // Thunderbolt's Hyper Attack (see Player's thunderbolt charge/detonate state machine) - the
    // halo hovers thunderboltHaloFrontDistance in front of the ship, charging through one damage/
    // blast-radius tier every thunderboltChargeLevelTime seconds it's held (both arrays indexed by
    // tier, same length, capped at the last tier once fully charged).
    public float thunderboltHaloFrontDistance;
    public float thunderboltHaloMoveSpeed;
    public float thunderboltChargeLevelTime;
    public int[] thunderboltChargeDamageByTier;
    public float[] thunderboltBlastRadiusByTier;

    // Impact effect played wherever this weapon's bullet lands a hit - hitTexture null (the
    // default) means no hit effect. See AssetManager, which resolves hitTexture into
    // hitAnimation once at load time (not JSON-backed - never a key in weapons.json - so every
    // bullet spawned from this definition can share the one prebuilt Animation instead of each
    // rebuilding it from hitTexture/hitColumns/hitRows/hitFrameCount/hitFrameDuration).
    public String hitTexture;
    public float hitSize;
    public int hitFrameCount;
    public int hitColumns = 0;
    public int hitRows = 1;
    public float hitFrameDuration = 0.05f;
    public Animation<TextureRegion> hitAnimation;

    public WeaponDefinition() {}

    public int getDamage(int level) { return damageByLevel[levelIndex(level, damageByLevel.length)]; }
    public float getFireRate(int level) { return fireRateByLevel[levelIndex(level, fireRateByLevel.length)]; }
    public float getSpeed(int level) { return speedByLevel[levelIndex(level, speedByLevel.length)]; }

    private static int levelIndex(int level, int arrayLength) {
        int index = level - 1;
        if (index < 0) return 0;
        if (index >= arrayLength) return arrayLength - 1;
        return index;
    }
}
