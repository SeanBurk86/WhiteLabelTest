package whitelabeltest.player.weapons;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** One weapons.json entry. */
public class WeaponDefinition {
    public String id;
    public String type;
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

    // Basic Hyper Attack. A re-press returns at haloFastReturnSpeed; other recalls use haloReturnSpeed.
    public float haloDashDistance;
    public float haloDashSpeed;
    public float haloReturnSpeed;
    public float haloFastReturnSpeed;
    public int haloDashDamage;
    // Dash-hit burst size as a multiple of the halo's diameter.
    public float haloCollisionScale = 0.75f;

    // Chain lightning: a hit also arcs to the arcTargets nearest other enemies within arcRange for
    // damage * arcDamageMultiplier. 0 = no arcs.
    public int arcTargets = 0;
    public float arcDamageMultiplier = 0.5f;
    public float arcRange = 3.5f;

    // Thunderbolt Hyper Attack. The tier arrays have the same length; one tier per
    // thunderboltChargeLevelTime held.
    public float thunderboltHaloFrontDistance;
    public float thunderboltHaloMoveSpeed;
    public float thunderboltChargeLevelTime;
    public int[] thunderboltChargeDamageByTier;
    public float[] thunderboltBlastRadiusByTier;

    // Optional impact effect. hitAnimation is built once by AssetManager (not from JSON).
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
